package izumi.distage.provisioning

import java.util.concurrent.atomic.AtomicReference

import distage.Id
import izumi.distage.LocatorDefaultImpl
import izumi.distage.model.Locator
import izumi.distage.model.Locator.LocatorMeta
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.effect.QuasiIO.syntax._
import izumi.distage.model.exceptions.{IncompatibleEffectTypesException, ProvisionerIssue}
import izumi.distage.model.plan.ExecutableOp.{MonadicOp, _}
import izumi.distage.model.plan.{ExecutableOp, OrderedPlan}
import izumi.distage.model.provisioning.PlanInterpreter.{FailedProvision, FailedProvisionMeta, Finalizer, FinalizerFilter}
import izumi.distage.model.provisioning.Provision.ProvisionMutable
import izumi.distage.model.provisioning._
import izumi.distage.model.provisioning.strategies._
import izumi.distage.model.recursive.LocatorRef
import izumi.distage.model.reflection._
import izumi.functional.IzEither._
import izumi.reflect.TagK

import scala.annotation.nowarn
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

// TODO: add introspection capabilities
class PlanInterpreterDefaultRuntimeImpl(
  setStrategy: SetStrategy,
  proxyStrategy: ProxyStrategy,
  providerStrategy: ProviderStrategy,
  importStrategy: ImportStrategy,
  instanceStrategy: InstanceStrategy,
  effectStrategy: EffectStrategy,
  resourceStrategy: ResourceStrategy,
  failureHandler: ProvisioningFailureInterceptor,
  verifier: ProvisionOperationVerifier,
  fullStackTraces: Boolean @Id("izumi.distage.interpreter.full-stacktraces"),
) extends PlanInterpreter
  with OperationExecutor {

  type OperationMetadata = Long

  override def instantiate[F[_]: TagK](
    plan: OrderedPlan,
    parentLocator: Locator,
    filterFinalizers: FinalizerFilter[F],
  )(implicit F: QuasiIO[F]
  ): Lifecycle[F, Either[FailedProvision[F], Locator]] = {
    Lifecycle.make(
      acquire = instantiateImpl(plan, parentLocator)
    )(release = {
      resource =>
        val finalizers = resource match {
          case Left(failedProvision) => failedProvision.failed.finalizers
          case Right(locator) => locator.finalizers
        }
        filterFinalizers.filter(finalizers).foldLeft(F.unit) {
          case (acc, f) => acc.guarantee(F.suspendF(f.effect()))
        }
    })
  }

  private[this] def instantiateImpl[F[_]: TagK](
    plan: OrderedPlan,
    parentContext: Locator,
  )(implicit F: QuasiIO[F]
  ): F[Either[FailedProvision[F], LocatorDefaultImpl[F]]] = {
    val mutProvisioningContext = ProvisionMutable[F]()
    val temporaryLocator = new LocatorDefaultImpl(plan, Option(parentContext), LocatorMeta.empty, mutProvisioningContext)
    val locatorRef = new LocatorRef(new AtomicReference(Left(temporaryLocator)))
    mutProvisioningContext.instances.put(DIKey.get[LocatorRef], locatorRef)

    val mutExcluded = mutable.Set.empty[DIKey]
    val mutFailures = mutable.ArrayBuffer.empty[ProvisioningFailure]
    val meta = mutable.HashMap.empty[DIKey, OperationMetadata]

    def currentContext() = {
      LocatorContext(mutProvisioningContext.toImmutable, parentContext)
    }

    def failureContext(step: NonImportOp): ProvisioningFailureContext = {
      ProvisioningFailureContext(parentContext, mutProvisioningContext, step)
    }
    def doStep(step: NonImportOp): F[Either[Throwable, Seq[NewObjectOp]]] = {
      for {
        maybeResult <- F.definitelyRecover[Try[Seq[NewObjectOp]]](
          action = execute(currentContext(), step).map(Success(_))
        )(recover =
          exception =>
            F.maybeSuspend {
              failureHandler
                .onExecutionFailed(failureContext(step))
                .applyOrElse(exception, Failure(_: Throwable))
            }
        )
      } yield {
        maybeResult.toEither
      }
    }

    def processStep[T <: ExecutableOp, E](h: T => F[Either[E, Seq[NewObjectOp]]])(step: T): F[Either[E, Seq[NewObjectOp]]] = {
      for {
        before <- F.maybeSuspend(System.nanoTime())
        excludeOp <- F.maybeSuspend(mutExcluded.contains(step.target))
        r <- F.ifThenElse(excludeOp)(F.pure(Right(Seq.empty[NewObjectOp]): Either[E, Seq[NewObjectOp]]), h(step))
        after <- F.maybeSuspend(System.nanoTime())
      } yield {
        val time = after - before
        meta.put(step.target, time)
        r
      }
    }

    def doImport(step: ImportDependency): F[Either[ProvisionerIssue, Seq[NewObjectOp]]] = {
      F.maybeSuspend(importStrategy.importDependency(currentContext(), this, step))
    }

    val imports = new ArrayBuffer[ImportDependency]()
    val otherSteps = new ArrayBuffer[NonImportOp]()
    plan.steps.foreach {
      case i: ImportDependency =>
        imports.append(i)
      case o: NonImportOp =>
        otherSteps.append(o)
    }

    @nowarn("msg=Unused import")
    def makeMeta(): LocatorMeta = {
      import scala.collection.compat._
      LocatorMeta(meta.view.mapValues(Duration.fromNanos).toMap)
    }

    def doFail(immutable: Provision.ProvisionImmutable[F]): Either[FailedProvision[F], LocatorDefaultImpl[F]] = {
      Left(FailedProvision[F](immutable, plan, parentContext, mutFailures.toVector, FailedProvisionMeta(makeMeta().timings), fullStackTraces))
    }

    def runSteps(otherSteps: ArrayBuffer[ExecutableOp.NonImportOp]): F[Unit] = {
      F.traverse_(otherSteps) {
        step =>
          processStep(doStep)(step).flatMap {
            case Right(newObjectOps) =>
              F.maybeSuspend {
                newObjectOps.foreach {
                  newObject =>
                    val maybeSuccess = Try {
                      interpretResult(mutProvisioningContext, newObject)
                    }.recoverWith(failureHandler.onBadResult(failureContext(step)))

                    maybeSuccess match {
                      case Success(_) =>
                      case Failure(failure) =>
                        mutExcluded ++= plan.topology.transitiveDependees(step.target)
                        mutFailures += StepProvisioningFailure(step, failure)
                    }
                }
              }

            case Left(failure) =>
              F.maybeSuspend {
                mutExcluded ++= plan.topology.transitiveDependees(step.target)
                mutFailures += StepProvisioningFailure(step, failure)
                ()
              }
          }
      }
    }

    for {
      // do imports first before everything
      importResults <- F.traverse(imports)(processStep(doImport)).map(_.biAggregateScalar)
      // verify effect type for everything else first before everything
      effectIssues <- verifyEffectType[F](otherSteps.toSeq)

      allIssues = importResults.left.getOrElse(List.empty) ++ effectIssues.left.getOrElse(List.empty)

      _ <- F.ifThenElse(allIssues.isEmpty)(
        F.unit,
        F.maybeSuspend {
          mutFailures += AggregateFailure(allIssues)
          ()
        },
      )

      _ <- F.maybeSuspend(importResults.toSeq.flatten.flatten.foreach(r => interpretResult(mutProvisioningContext, r)))

      out <- F.ifThenElse(mutFailures.nonEmpty)(
        F.maybeSuspend(doFail(mutProvisioningContext.toImmutable)),
        runSteps(otherSteps).flatMap {
          _ =>
            F.ifThenElse(mutFailures.nonEmpty)(
              F.maybeSuspend(doFail(mutProvisioningContext.toImmutable)),
              F.maybeSuspend {
                val finalLocator = new LocatorDefaultImpl(plan, Option(parentContext), makeMeta(), mutProvisioningContext.toImmutable)
                locatorRef.ref.set(Right(finalLocator))
                Right(finalLocator): Either[FailedProvision[F], LocatorDefaultImpl[F]]
              },
            )
        },
      )

    } yield {
      out
    }
  }

  override def execute[F[_]: TagK](context: ProvisioningKeyProvider, step: NonImportOp)(implicit F: QuasiIO[F]): F[Seq[NewObjectOp]] = {
    step match {
      case op: CreateSet =>
        F pure setStrategy.makeSet(context, this, op)

      case op: WiringOp =>
        F pure execute(context, op)

      case op: ProxyOp.MakeProxy =>
        F pure proxyStrategy.makeProxy(context, this, op)

      case op: ProxyOp.InitProxy =>
        proxyStrategy.initProxy(context, this, op)

      case op: MonadicOp.ExecuteEffect =>
        F widen effectStrategy.executeEffect[F](context, this, op)

      case op: MonadicOp.AllocateResource =>
        F widen resourceStrategy.allocateResource[F](context, this, op)
    }
  }

  override def execute(context: ProvisioningKeyProvider, step: WiringOp): Seq[NewObjectOp] = {
    step match {
      case op: WiringOp.UseInstance =>
        instanceStrategy.getInstance(context, this, op)

      case op: WiringOp.ReferenceKey =>
        instanceStrategy.getInstance(context, this, op)

      case op: WiringOp.CallProvider =>
        providerStrategy.callProvider(context, this, op)
    }
  }

  private[this] def interpretResult[F[_]: TagK](active: ProvisionMutable[F], result: NewObjectOp): Unit = {
    result match {
      case NewObjectOp.NewImport(target, instance) =>
        verifier.verify(target, active.imports.keySet, instance, s"import")
        active.imports += (target -> instance)

      case NewObjectOp.NewInstance(target, instance) =>
        verifier.verify(target, active.instances.keySet, instance, "instance")
        active.instances += (target -> instance)

      case r @ NewObjectOp.NewResource(target, instance, _) =>
        verifier.verify(target, active.instances.keySet, instance, "resource")
        active.instances += (target -> instance)
        val finalizer = r.asInstanceOf[NewObjectOp.NewResource[F]].finalizer
        active.finalizers prepend Finalizer[F](target, finalizer)

      case r @ NewObjectOp.NewFinalizer(target, _) =>
        val finalizer = r.asInstanceOf[NewObjectOp.NewFinalizer[F]].finalizer
        active.finalizers prepend Finalizer[F](target, finalizer)

      case NewObjectOp.UpdatedSet(target, instance) =>
        verifier.verify(target, active.instances.keySet, instance, "set")
        active.instances += (target -> instance)
    }
  }

  private[this] def verifyEffectType[F[_]: TagK](
    ops: Seq[NonImportOp]
  )(implicit F: QuasiIO[F]
  ): F[Either[Seq[IncompatibleEffectTypesException], Unit]] = {
    val monadicOps = ops.collect { case m: MonadicOp => m }
    val badOps = monadicOps
      .filter(_.isIncompatibleEffectType[F])
      .map {
        op =>
          IncompatibleEffectTypesException(op, op.provisionerEffectType[F], op.actionEffectType)
      }

    F.ifThenElse(badOps.isEmpty)(F.pure(Right(())), F.pure(Left(badOps)))
  }

}
