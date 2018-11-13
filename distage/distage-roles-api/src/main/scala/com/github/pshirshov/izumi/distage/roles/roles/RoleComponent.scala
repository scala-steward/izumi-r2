package com.github.pshirshov.izumi.distage.roles.roles

trait RoleComponent {
  /** Should be idempotent, i.e. [[start()]] on a running component should NOT spawn a second component, interfere with the current instance or throw */
  def start(): Unit

  /** Should be idempotent, i.e. [[stop()]] on an already stopped component should NOT throw */
  def stop(): Unit = {}
}
