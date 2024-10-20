package izumi.distage.model.definition.dsl

import izumi.distage.constructors.MakeMacro

trait AbstractBindingDefDSLMacro[BindDSL[_]] {
  inline final protected def make[T]: BindDSL[T] = ${ MakeMacro.makeMethod[T, BindDSL[T]] }
}
