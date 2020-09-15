package izumi.distage.model.planning

import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.plan.{OrderedPlan, SemiPlan}

trait PlanningHook {
  def hookDefinition(defn: ModuleBase): ModuleBase = defn

  def phase90AfterForwarding(plan: OrderedPlan): OrderedPlan = plan
}
