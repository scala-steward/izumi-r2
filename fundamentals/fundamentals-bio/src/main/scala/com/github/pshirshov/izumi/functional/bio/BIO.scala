package com.github.pshirshov.izumi.functional.bio

import com.github.pshirshov.izumi.functional.bio.impl.{BIOAsyncZio, BIOZio}
import scalaz.zio.ZIO

import scala.util.Try

trait BIOFunctor[F[_, +_]] {
  def map[E, A, B](r: F[E, A])(f: A => B): F[E, B]
  def void[E, A](r: F[E, A]): F[E, Unit] = map(r)(_ => ())
}

object BIOFunctor {
  def apply[F[_, +_]: BIOFunctor]: BIOFunctor[F] = implicitly

  // place ZIO instances at the root of hierarchy, so that they're visible for summons of any class in hierarchy
  @inline implicit final def BIOZIO[R]: BIOZio[R] = BIOZio.asInstanceOf[BIOZio[R]]
  @inline implicit final def BIOAsyncZio[R](implicit clockService: scalaz.zio.clock.Clock): BIOAsync[ZIO[R, +?, +?]] = new BIOAsyncZio[R](clockService)
}

trait BIOBifunctor[F[+_, +_]] extends BIOFunctor[F] {
  def bimap[E, A, E2, A2](r: F[E, A])(f: E => E2, g: A => A2): F[E2, A2]
  @inline def leftMap[E, A, E2](r: F[E, A])(f: E => E2): F[E2, A] = bimap(r)(f, identity)
}

object BIOBifunctor {
  def apply[F[+_, +_]: BIOBifunctor]: BIOBifunctor[F] = implicitly
}

trait BIOApplicative[F[+_, +_]] extends BIOBifunctor[F] {
  def now[A](a: A): F[Nothing, A]

  /** execute two operations in order, map their results */
  def map2[E, A, E2 >: E, B, C](firstOp: F[E, A], secondOp: => F[E2, B])(f: (A, B) => C): F[E2, C]

  /** execute two operations in order, return result of second operation */
  def *>[E, A, E2 >: E, B](firstOp: F[E, A], secondOp: => F[E2, B]): F[E2, B]

  /** execute two operations in order, same as `*>`, but return result of first operation */
  def <*[E, A, E2 >: E, B](firstOp: F[E, A], secondOp: => F[E2, B]): F[E2, A]

  @inline final val unit: F[Nothing, Unit] = now(())
  @inline final def when[E](p: Boolean)(r: F[E, Unit]): F[E, Unit] = if (p) r else unit
}

object BIOApplicative {
  def apply[F[+_, +_]: BIOApplicative]: BIOApplicative[F] = implicitly
}

trait BIOGuarantee[F[+_, +_]] extends BIOApplicative[F]  {
  def guarantee[E, A](f: F[E, A])(cleanup: F[Nothing, Unit]): F[E, A]
}

object BIOGuarantee {
  def apply[F[+_, +_]: BIOGuarantee]: BIOGuarantee[F] = implicitly
}

trait BIOError[F[+_ ,+_]] extends BIOGuarantee[F] {
  def fail[E](v: => E): F[E, Nothing]
  def redeem[E, A, E2, B](r: F[E, A])(err: E => F[E2, B], succ: A => F[E2, B]): F[E2, B]

  @inline def redeemPure[E, A, B](r: F[E, A])(err: E => B, succ: A => B): F[Nothing, B] = redeem(r)(err.andThen(now), succ.andThen(now))
  @inline def attempt[E, A](r: F[E, A]): F[Nothing, Either[E, A]] = redeemPure(r)(Left(_), Right(_))
  @inline def catchAll[E, A, E2, A2 >: A](r: F[E, A])(f: E => F[E2, A2]): F[E2, A2] = redeem(r)(f, now)
  @inline def flip[E, A](r: F[E, A]) : F[A, E] = redeem(r)(now, fail(_))
  @inline final def fromOption[A](effect: => Option[A]): F[Unit, A] = fromOption(())(effect)

  def fromEither[E, V](effect: => Either[E, V]): F[E, V]
  def fromOption[E, A](errorOnNone: E)(effect: => Option[A]): F[E, A]
  def fromTry[A](effect: => Try[A]): F[Throwable, A]

  // defaults
  @inline override def bimap[E, A, E2, B](r: F[E, A])(f: E => E2, g: A => B): F[E2, B] = redeem(r)(e => fail(f(e)), a => now(g(a)))
}

object BIOError {
  def apply[F[+_, +_]: BIOError]: BIOError[F] = implicitly
}

trait BIOMonad[F[+_, +_]] extends BIOApplicative[F] {
  def flatMap[E, A, E2 >: E, B](r: F[E, A])(f: A => F[E2, B]): F[E2, B]
  def flatten[E, A](r: F[E, F[E, A]]): F[E, A] = flatMap(r)(identity)
  def traverse[E, A, B](l: Iterable[A])(f: A => F[E, B]): F[E, List[B]]

  @inline final def forever[E, A](r: F[E, A]): F[E, Nothing] = flatMap(r)(_ => forever(r))

  @inline final def traverse_[E, A, B](l: Iterable[A])(f: A => F[E, B]): F[E, Unit] = void(traverse(l)(f))
  @inline final def sequence[E, A, B](l: Iterable[F[E, A]]): F[E, List[A]] = traverse(l)(identity)
  @inline final def sequence_[E](l: Iterable[F[E, Unit]]): F[E, Unit] = void(traverse(l)(identity))

  // defaults
  @inline override def map[E, A, B](r: F[E, A])(f: A => B): F[E, B] = {
    flatMap(r)(a => now(f(a)))
  }

  /** execute two operations in order, return result of second operation */
  @inline override def *>[E, A, E2 >: E, B](f: F[E, A], next: => F[E2, B]): F[E2, B] = {
    flatMap[E, A, E2, B](f)(_ => next)
  }

  /** execute two operations in order, same as `*>`, but return result of first operation */
  @inline override def <*[E, A, E2 >: E, B](f: F[E, A], next: => F[E2, B]): F[E2, A] = {
    flatMap[E, A, E2, A](f)(a => map(next)(_ => a))
  }

  /** execute two operations in order, map their results */
  @inline override def map2[E, A, E2 >: E, B, C](r1: F[E, A], r2: => F[E2, B])(f: (A, B) => C): F[E2, C] = {
    flatMap[E, A, E2, C](r1)(a => map(r2)(b => f(a, b)))
  }
}

object BIOMonad {
  def apply[F[+_, +_]: BIOMonad]: BIOMonad[F] = implicitly
}

trait BIOBracket[F[+_, +_]] extends BIOError[F] with BIOMonad[F] {
  def bracketCase[E, A, B](acquire: F[E, A])(release: (A, BIOExit[E, B]) => F[Nothing, Unit])(use: A => F[E, B]): F[E, B]

  @inline def bracket[E, A, B](acquire: F[E, A])(release: A => F[Nothing, Unit])(use: A => F[E, B]): F[E, B] = {
    bracketCase[E, A, B](acquire)((a, _) => release(a))(use)
  }

  @inline def leftFlatMap[E, A, E2](r: F[E, A])(f: E => F[Nothing, E2]): F[E2, A] = {
    redeem(r)(e => flatMap(f(e))(fail(_)), now)
  }

  // defaults
  @inline override def guarantee[E, A](f: F[E, A])(cleanup: F[Nothing, Unit]): F[E, A] = {
    bracket(unit)(_ => cleanup)(_ => f)
  }
}

object BIOBracket {
  def apply[F[+_, +_]: BIOBracket]: BIOBracket[F] = implicitly
}

trait BIOPanic[F[+_, +_]] extends BIOBracket[F] {
  def terminate(v: => Throwable): F[Nothing, Nothing]

  @inline final def orTerminate[E <: Throwable, A](r: F[E, A]): F[Nothing, A] = catchAll(r)(terminate(_))

  def sandbox[E, A](r: F[E, A]): F[BIOExit.Failure[E], A]
  def sandboxWith[E, A, E2, B](r: F[E, A])(f: F[BIOExit.Failure[E], A] => F[BIOExit.Failure[E2], B]): F[E2, B]
}

object BIOPanic {
  def apply[F[+_, +_]: BIOPanic]: BIOPanic[F] = implicitly
}

trait BIO[F[+_, +_]] extends BIOPanic[F] {
  type Or[+E, +A] = F[E, A]
  type Just[+A] = F[Nothing, A]

  @inline def syncThrowable[A](effect: => A): F[Throwable, A]
  @inline def sync[A](effect: => A): F[Nothing, A]
  @inline final def apply[A](effect: => A): F[Throwable, A] = syncThrowable(effect)

  @deprecated("use .now for pure values, .sync for effects or delayed computations", "29.04.2019")
  @inline def point[A](v: => A): F[Nothing, A]

  @inline override def fromEither[E, A](effect: => Either[E, A]): F[E, A] = flatMap(sync(effect)) {
    case Left(e) => fail(e): F[E, A]
    case Right(v) => now(v): F[E, A]
  }

  @inline override def fromOption[E, A](errorOnNone: E)(effect: => Option[A]): F[E, A] = {
    flatMap(sync(effect))(e => fromEither(e.toRight(errorOnNone)))
  }

  @inline override def fromTry[A](effect: => Try[A]): F[Throwable, A] = {
    syncThrowable(effect.get)
  }
}

object BIO
  extends BIOSyntax {
  @inline def apply[F[+_, +_] : BIO]: BIO[F] = implicitly

  @inline def apply[F[+_, +_], A](effect: => A)(implicit BIO: BIO[F]): F[Throwable, A] = BIO.syncThrowable(effect)
}

