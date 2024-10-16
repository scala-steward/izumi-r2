package izumi.distage.framework.services

import com.typesafe.config.{Config, ConfigFactory}
import izumi.distage.config.model.*
import izumi.distage.model.definition.Id
import izumi.distage.model.exceptions.DIException
import izumi.functional.IzEither.*
import izumi.fundamentals.platform.exceptions.IzThrowable.*
import izumi.fundamentals.platform.resources.IzResources
import izumi.fundamentals.platform.resources.IzResources.{LoadablePathReference, UnloadablePathReference}
import izumi.fundamentals.platform.strings.IzString.*
import izumi.logstage.api.IzLogger

import java.io.{File, FileNotFoundException}
import scala.annotation.nowarn
import scala.util.{Failure, Success, Try}

/**
  * Default config resources:
  *   - `\${roleName}.conf`
  *   - `\${roleName}-reference.conf`
  *   - `\${roleName}-reference-dev.conf`
  *   - `application.conf`
  *   - `application-reference.conf`
  *   - `application-reference-dev.conf`
  *   - `common.conf`
  *   - `common-reference.conf`
  *   - `common-reference-dev.conf`
  *
  * NOTE: You can change default config locations by overriding `make[ConfigLocationProvider]`
  * binding in [[izumi.distage.roles.RoleAppMain#roleAppBootOverrides]] (defaults defined in [[izumi.distage.roles.RoleAppBootModule]])
  *
  * When explicit configs are passed to the role launcher on the command-line using the `-c` option, they have higher priority than all the reference configs.
  * Role-specific configs on the command-line (`-c` option after `:role` argument) override global command-line configs (`-c` option given before the first `:role` argument).
  *
  * Example:
  *
  * {{{
  *   ./launcher -c global.conf :role1 -c role1.conf :role2 -c role2.conf
  * }}}
  *
  * Here configs will be loaded in the following order, with higher priority earlier:
  *
  *   - explicits: `role1.conf`, `role2.conf`, `global.conf`,
  *   - resources: `role1[-reference,-dev].conf`, `role2[-reference,-dev].conf`, ,`application[-reference,-dev].conf`, `common[-reference,-dev].conf`
  *
  * @see [[ConfigLocationProvider]]
  * @see [[ConfigLoader.LocalFSImpl]]
  */
trait ConfigLoader extends AbstractConfigLoader

@nowarn("msg=Unused import")
object ConfigLoader {
  def empty: ConfigLoader = (_: String) => AppConfig(ConfigFactory.empty(), List.empty, List.empty)

  import scala.collection.compat.*

  final case class Args(
    global: Option[File],
    configs: List[RoleConfig],
  )
  final class ConfigLoaderException(message: String, val failures: List[Throwable]) extends DIException(message)

  open class LocalFSImpl(
    logger: IzLogger @Id("early"),
    merger: ConfigMerger,
    configLocation: ConfigLocationProvider,
    args: ConfigArgsProvider,
  ) extends ConfigLoader {
    protected def resourceClassLoader: ClassLoader = getClass.getClassLoader

    def loadConfig(clue: String): AppConfig = {
      val configArgs = args.args()

      val maybeLoadedRoleConfigs = configArgs.configs.map {
        roleConfig =>
          val references = configLocation.forRole(roleConfig.role).map(loadConfigSource(false, _))
          val loaded = roleConfig.configSource match {
            case GenericConfigSource.ConfigFile(file) =>
              val explicit = Seq(loadConfigSource(true, ConfigSource.File(file)))
              explicit ++ references
            case GenericConfigSource.ConfigDefault =>
              references
          }
          (roleConfig, loaded)
      }

      val loadedCommonExplicitConfigs = configArgs.global.map(ConfigSource.File(_)).map(loadConfigSource(true, _))
      val loadedCommonReferenceConfigs = configLocation.commonReferenceConfigs.map(loadConfigSource(false, _))
      val loaded = for {
        loadedCommonConfigs <- (loadedCommonExplicitConfigs ++ loadedCommonReferenceConfigs).map(_.toEither).toList.biSequenceScalar
        loadedRoleConfigs <- maybeLoadedRoleConfigs.map {
          case (roleConfig, loaded) =>
            loaded.map(_.toEither).biSequenceScalar match {
              case Left(failures) =>
                Left(failures)
              case Right(configLoadResults) =>
                Right(LoadedRoleConfigs(roleConfig, configLoadResults))
            }
        }.biSequence
      } yield (loadedCommonConfigs, loadedRoleConfigs)

      loaded match {
        case Left(errs) =>
          val failures = errs.map(f => s"Failed to load ${f.src} ${f.clue}: ${f.failure.stacktraceString}")
          logger.error(s"Cannot load configuration: ${failures.toList.niceList() -> "failures"}")
          throw new ConfigLoaderException(s"Cannot load configuration: failures=${failures.toList.niceList()}", errs.map(_.failure).toList)
        case Right((shared, role)) =>
          val merged = merger.addSystemProps(merger.merge(shared, role, clue))
          AppConfig(merged, shared, role)
      }
    }

    protected def loadConfigSource(isExplicit: Boolean, configSource: ConfigSource): ConfigLoadResult = {
      configSource match {
        case r: ConfigSource.Resource =>
          def tryLoadResource(): Try[Config] = {
            Try(ConfigFactory.parseResources(resourceClassLoader, r.name)).flatMap {
              cfg =>
                if (cfg.origin().resource() eq null) {
                  Failure(new FileNotFoundException(s"Couldn't find config file $r"))
                } else Success(cfg)
            }
          }

          IzResources(resourceClassLoader).getPath(r.name) match {
            case Some(LoadablePathReference(path, _)) =>
              doLoad(s"$r (available: $path)", configSource, isExplicit, tryLoadResource())
            case Some(UnloadablePathReference(path)) =>
              doLoad(s"$r (exists: $path)", configSource, isExplicit, tryLoadResource())
            case None =>
              doLoad(s"$r (missing)", configSource, isExplicit, Success(ConfigFactory.empty()))
          }

        case f: ConfigSource.File =>
          if (f.file.exists()) {
            doLoad(
              s"$f (exists: ${f.file.getCanonicalPath})",
              configSource,
              isExplicit,
              Try(ConfigFactory.parseFile(f.file)).flatMap {
                cfg => if (cfg.origin().filename() ne null) Success(cfg) else Failure(new FileNotFoundException(s"Couldn't find config file $f"))
              },
            )
          } else {
            doLoad(s"$f (missing)", configSource, isExplicit, Failure(new FileNotFoundException(f.file.getCanonicalPath)))
          }
      }
    }

    private def doLoad(clue: String, source: ConfigSource, isExplicit: Boolean, loader: => Try[Config]): ConfigLoadResult = {
      loader match {
        case Failure(exception) => ConfigLoadResult.Failure(clue, source, isExplicit, exception)
        case Success(value) => ConfigLoadResult.Success(clue, source, isExplicit, value)
      }
    }

  }

}
