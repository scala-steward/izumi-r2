package izumi.distage.reflection.macros.universe.basicuniverse

import izumi.distage.reflection.macros.universe.basicuniverse
import izumi.distage.reflection.macros.universe.basicuniverse.exceptions.BadIdAnnotationException

class BaseReflectionProvider[U <: scala.reflect.api.Universe & Singleton](val u: U, idAnnotationFqn: String) {
  def typeToParameter(t: u.Type, transformName: String => String): CompactParameter = {
    parameterToAssociation2(MacroSymbolInfoCompactImpl.syntheticFromType(u)(transformName)(t))
  }

  def symbolToParameter(s: u.Symbol): CompactParameter = {
    parameterToAssociation2(MacroSymbolInfoCompactImpl.fromSymbol(u)(s))
  }

  private def parameterToAssociation2(parameterSymbol: MacroSymbolInfoCompact): CompactParameter = {
    val key = keyFromSymbol(parameterSymbol)
    basicuniverse.CompactParameter(parameterSymbol, key)
  }

  def tpeFromSymbol(parameterSymbol: MacroSymbolInfoCompact): MacroSafeType = {
    val tpe = parameterSymbol.finalResultTypeIn(u)
    val paramType = if (parameterSymbol.isByName) { // this will never be true for a method symbol
      tpe.typeArgs.head.finalResultType
    } else {
      tpe
    }
    MacroSafeType.create(u)(paramType)
  }

  def keyFromSymbol(parameterSymbol: MacroSymbolInfoCompact): MacroDIKey.BasicKey = {
    val tpe = tpeFromSymbol(parameterSymbol)
    val typeKey = MacroDIKey.TypeKey(tpe)
    withIdKeyFromAnnotation(parameterSymbol, typeKey)
  }

  private def withIdKeyFromAnnotation(parameterSymbol: MacroSymbolInfoCompact, typeKey: MacroDIKey.TypeKey): MacroDIKey.BasicKey = {
    val maybeDistageName = parameterSymbol.findUniqueFriendlyAnno(a => a.fqn == idAnnotationFqn).map {
      value =>
        value.params match {
          case FriendlyAnnoParams.Full(values) =>
            values.toMap.get("name") match {
              case Some(value: FriendlyAnnotationValue.StringValue) =>
                value.value
              case _ =>
                throw new BadIdAnnotationException(value.toString, value)
            }

          case FriendlyAnnoParams.Values(_) =>
            throw new BadIdAnnotationException(value.toString, value)
        }

    }

    lazy val maybeJSRName = parameterSymbol.findUniqueFriendlyAnno(a => a.fqn.endsWith(".Named")).flatMap {
      value =>
        value.params.values match {
          case FriendlyAnnotationValue.StringValue(head) :: Nil =>
            Some(head)
          case _ =>
            None
        }
    }

    maybeDistageName.orElse(maybeJSRName) match {
      case Some(value) => typeKey.named(value)
      case None => typeKey
    }
  }
}
