package izumi.fundamentals.collections

import scala.annotation.nowarn
import scala.collection.compat.*
import scala.collection.{MapView, View, mutable}

@nowarn("msg=Unused import")
final class IzMultiMaps[A, B](private val mmap: scala.collection.Map[A, Set[B]]) extends AnyVal {
  def unwrap: List[(A, B)] = {
    mmap.view.flatMap { case (k, vs) => vs.map(v => (k, v)) }.toList
  }
}

@nowarn("msg=Unused import")
final class IzIterOnceMappings[A, B](private val list: IterableOnce[(A, B)]) extends AnyVal {
  import scala.collection.compat._

  @nowarn("msg=deprecated")
  def toMultimapMut: MutableMultiMap[A, B] = {
    list.iterator.foldLeft(new mutable.HashMap[A, mutable.Set[B]] with mutable.MultiMap[A, B]) {
      (acc, pair) =>
        acc.addBinding(pair._1, pair._2)
    }
  }

  def toMultimapView: MapView[A, View[B]] = {
    toMultimapMut.view.mapValues(_.view)
  }

  def toMultimap: ImmutableMultiMap[A, B] = {
    toMultimapMut.view.mapValues(_.toSet).toMap
  }
}

@nowarn("msg=Unused import")
final class IzIterMappings[A, B](private val list: Iterable[(A, B)]) extends AnyVal {

  import scala.collection.compat._

  def toUniqueMap[E](onConflict: Map[A, List[B]] => E): Either[E, Map[A, B]] = {
    val grouped = list.groupBy(_._1)
    val bad = grouped.filter(_._2.size > 1)

    if (bad.isEmpty) {
      Right(list.toMap)
    } else {
      Left(onConflict(bad.view.mapValues(_.map(_._2).toList).toMap))
    }

  }
}
