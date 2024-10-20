package izumi.distage.model.definition.dsl

import izumi.distage.model.definition.Lifecycle
import izumi.reflect.TagMacro

import scala.reflect.macros.blackbox
import scala.util.Try

object LifecycleTagMacro {
  def fakeResourceTagMacroIntellijWorkaroundImpl[R <: Lifecycle[Any, Any]: c.WeakTypeTag](c: blackbox.Context): c.Expr[Nothing] = {
    val tagMacro = new TagMacro(c)
    val _ = Try(tagMacro.makeWeakTag[R]) // run the macro AGAIN, to get a fresh error message
    val tagTrace = tagMacro.getImplicitError()

    c.abort(c.enclosingPosition, s"could not find implicit ResourceTag for ${c.universe.weakTypeOf[R]}!\n$tagTrace")
  }
}
