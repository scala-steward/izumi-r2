package distage

import izumi.distage.plugins.load

package object plugins extends DistagePlugins {

  override type PluginBase = izumi.distage.plugins.PluginBase
  override val PluginBase: izumi.distage.plugins.PluginBase.type = izumi.distage.plugins.PluginBase

  // Because of Scala 3 bug https://github.com/scala/scala3/issues/19745
  // we can't use a type alias or export to alias PluginDef name anymore,
  // use the longer package izumi.distage.plugins.PluginDef instead.
//  override type PluginDef[T] = izumi.distage.plugins.PluginDef[T]

  override type BootstrapPlugin = izumi.distage.plugins.BootstrapPlugin
  override val BootstrapPlugin: izumi.distage.plugins.BootstrapPlugin.type = izumi.distage.plugins.BootstrapPlugin

  // Because of Scala 3 bug https://github.com/scala/scala3/issues/19745
  // we can't use a type alias or export to alias BootstrapPluginDef name anymore,
  // use the longer package izumi.distage.plugins.BootstrapPluginDef instead.
//  override type BootstrapPluginDef[T] = izumi.distage.plugins.BootstrapPluginDef[T]

  override type PluginLoader = load.PluginLoader
  override val PluginLoader: load.PluginLoader.type = load.PluginLoader

  override val StaticPlugingLoader: izumi.distage.plugins.StaticPluginLoader.type = izumi.distage.plugins.StaticPluginLoader

  override type PluginLoaderDefaultImpl = load.PluginLoaderDefaultImpl
  override val PluginLoaderDefaultImpl: load.PluginLoaderDefaultImpl.type = load.PluginLoaderDefaultImpl

  override type PluginConfig = izumi.distage.plugins.PluginConfig
  override val PluginConfig: izumi.distage.plugins.PluginConfig.type = izumi.distage.plugins.PluginConfig

}
