package izumi.logstage.api.logger

import izumi.fundamentals.platform.language.CodePosition
import izumi.logstage.api.Log
import izumi.logstage.api.Log.CustomContext

/** Logger that forwards entries to [[LogRouter]] */
trait RoutingLogger extends AbstractLogger {

  override type Self <: RoutingLogger

  def router: LogRouter
  def customContext: CustomContext

  override def acceptable(position: CodePosition, logLevel: Log.Level): Boolean = {
    router.acceptable(position, logLevel)
  }

  /** Log irrespective of minimum log level */
  @inline override final def unsafeLog(entry: Log.Entry): Unit = {
    val entryWithCtx = entry.addCustomContext(customContext)
    router.log(entryWithCtx)
  }

}
