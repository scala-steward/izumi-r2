# Motivation

There's much written on the benefit of Dependency Injection.

Motivation for Dependency Injection frameworks in OOP:

- [Zenject: is this overkill?](https://github.com/svermeulen/Zenject#theory)

- [IoC in 2003](https://paulhammant.com/files/JDJ_2003_12_IoC_Rocks.pdf) - 
  nowadays what the article calls. `distage`
  
- Example: Code sharing

Motivation for Dependency Injection frameworks in functional programming:

- [Haskell registry: modules as records of functions](https://github.com/etorreborre/registry/blob/master/doc/motivation.md)

Note: despite being a Haskell library, the `registry` DI framework above is *less* principled than `distage` due to not offering
a first-class representation for its operations. As such it fails at being extensible or debuggable, which is what `distage` excels at.
