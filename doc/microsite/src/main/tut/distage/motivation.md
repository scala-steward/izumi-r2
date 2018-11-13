# Motivation

There's much written on the benefit of Dependency Injection.

Motivation for Dependency Injection frameworks in OOP:

- [Zenject: is this overkill?](https://github.com/svermeulen/Zenject#theory)

- [IoC in 2003](https://paulhammant.com/files/JDJ_2003_12_IoC_Rocks.pdf) - 
  nowadays what the article calls. `distage`
  
- Example: Code sharing

 To extend the example, If your code spans over multiple repositories – for example, your company has `sdk` + 3 dependent apps, and the constructor has been changed in sdk, you will now have to update 4 repositories.
This is clearly _very_ unmodular – 4 applications were broken by an irrelevant implementation detail!
\item IMHO using DI as a concept – accepting modules as arguments, instead of using global objects – implicits included – is a #1 prerequisite for modularity,
while wiring implicitly is a #2 prerequisite – without those you simply can't share modules!

DI solves the problem of modularity in large systems. Not having problems solvable by DI is great. But when systems grow
to encompass several repos it becomes more important. For example, let's assume you have 4 separate repos - 3 apps, 1 shared sdk.
If in these 3 apps you instantiate an sdk class explicitly, and that class e.g. added a new argument, now you have to update
instantiations in all 3 apps. If we think of classes as first-class modules, this is clearly unmodular

Motivation for Dependency Injection frameworks in functional programming:

- [Haskell registry: modules as records of functions](https://github.com/etorreborre/registry/blob/master/doc/motivation.md)

Note: despite being a Haskell library, the `registry` DI framework above is *less* principled than `distage` due to not offering
a first-class representation for its operations. As such it fails at being extensible or debuggable, which is what `distage` excels at.

// Haskell-land is a nightmare for anyone concerned with modularity. importing B into A should ALWAYS succeed, A should not be impacted by internal implementation of B, even if B imports A – this can, and SHOULD always be resolved.
// Similarly, modules should not be dependent on application data flow, if a module changes signature as to require a new collaborator, it's users SHOULD not be affected by this change – they should NEVER be impacted by changes in a module's implementation
