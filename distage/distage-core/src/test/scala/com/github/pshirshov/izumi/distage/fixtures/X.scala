package com.github.pshirshov.izumi.distage.fixtures

trait Greeter {
  def hello(name: String): Unit
}

final class PrintGreeter extends Greeter {
  override def hello(name: String) = println(s"Hello $name!")
}

class HelloByeApp(greeter: Greeter, byer: Byer) {
  def run(): Unit = {
    println("What's your name?")
    val name = readLine()

    greeter.hello(name)
    byer.bye(name)
  }
}

trait Byer {
  def bye(name: String): Unit
}

class PrintByer extends Byer {
  override def bye(name: String) = println(s"Bye $name!")
}

import distage.{ModuleDef, Injector}

object HelloByeModule extends ModuleDef {
  make[Greeter].from[PrintGreeter]
  make[Byer].from[PrintByer]
  make[HelloByeApp]
}

object Main extends App {
  val injector = Injector()
  val plan = injector.plan(HelloByeModule)

  println(plan.render)

  val objects = injector.produce(plan)

  val app = objects.get[HelloByeApp]
  app.run()
}

