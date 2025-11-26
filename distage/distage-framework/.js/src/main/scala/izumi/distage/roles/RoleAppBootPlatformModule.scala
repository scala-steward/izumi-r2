package izumi.distage.roles

import izumi.distage.model.definition.ModuleDef
import izumi.distage.roles.launcher.RoleProvider

class RoleAppBootPlatformModule[F[_]]() extends ModuleDef {
  include(new RoleAppBootConfigModule[F]())
  include(new RoleAppBootLoggerModule[F]())

  make[RoleProvider].from[RoleProvider.NonReflectiveImpl]
}
