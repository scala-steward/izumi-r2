package izumi.distage.roles

import izumi.distage.config.model.AppConfig
import izumi.distage.model.definition.ModuleDef

class RoleAppBootConfigModule[F[_]]() extends ModuleDef {
  make[AppConfig].fromValue(AppConfig.empty)
}
