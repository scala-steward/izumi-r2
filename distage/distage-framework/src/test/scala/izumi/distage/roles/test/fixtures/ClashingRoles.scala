package izumi.distage.roles.test.fixtures

import izumi.distage.model.definition.Lifecycle
import izumi.distage.plugins.PluginDef
import izumi.distage.roles.model.definition.RoleModuleDef
import izumi.distage.roles.model.{RoleDescriptor, RoleService, RoleTask}
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.cli.model.EntrypointArgs
import izumi.logstage.api.IzLogger
import izumi.reflect.TagK

// Two distinct roles whose `RoleDescriptor.id` deliberately collide. Used to verify that the launcher rejects clashing
// role names. NOT included in `TestPluginBase`, so the rest of the suite is unaffected.
object ClashingRoles {
  final val clashingId = "clashing-role"

  class ClashingRoleService[F[_]: QuasiIO](logger: IzLogger) extends RoleService[F] {
    override def start(roleParameters: EntrypointArgs): Lifecycle[F, Unit] = Lifecycle.make(QuasiIO[F].maybeSuspend {
      logger.info(s"[ClashingRoleService] started: $roleParameters")
    })(_ => QuasiIO[F].unit)
  }

  object ClashingRoleService extends RoleDescriptor {
    override final val id = clashingId
  }

  class ClashingRoleTask[F[_]: QuasiIO](logger: IzLogger) extends RoleTask[F] {
    override def start(roleParameters: EntrypointArgs): F[Unit] = QuasiIO[F].maybeSuspend {
      logger.info(s"[ClashingRoleTask] started: $roleParameters")
    }
  }

  object ClashingRoleTask extends RoleDescriptor {
    override final val id = clashingId
  }
}

class ClashingRolesPlugin[F[_]: TagK] extends PluginDef with RoleModuleDef {
  makeRole[ClashingRoles.ClashingRoleService[F]]
  makeRole[ClashingRoles.ClashingRoleTask[F]]
}
