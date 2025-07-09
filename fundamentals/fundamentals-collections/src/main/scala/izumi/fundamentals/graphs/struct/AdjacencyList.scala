package izumi.fundamentals.graphs.struct

import scala.collection.compat._
import scala.collection.mutable

final case class AdjacencyList[N] private (links: Map[N, Set[N]]) extends AnyVal {

  def transposed: AdjacencyList[N] = {
    val output = mutable.HashMap.empty[N, mutable.LinkedHashSet[N]]
    links.foreach {
      case (n, linked) =>
        output.getOrElseUpdate(n, mutable.LinkedHashSet.empty[N])

        linked.foreach {
          l =>
            output.getOrElseUpdate(l, mutable.LinkedHashSet.empty[N]) += n
        }
    }
    new AdjacencyList(output.view.mapValues(_.toSet).toMap)
  }

  def map[N1](f: N => N1): AdjacencyList[N1] = {
    AdjacencyList(links.map {
      case (n, deps) =>
        (f(n), deps.map(f))
    })
  }

  def without(nodes: Set[N]): AdjacencyList[N] = {
    AdjacencyList(links.view.filterKeys(k => !nodes.contains(k)).mapValues(_.diff(nodes)).toMap)
  }

  def rewriteLinked(mapping: Map[N, N]): AdjacencyList[N] = {
    AdjacencyList(links.view.mapValues(_.map(d => mapping.getOrElse(d, d))).toMap)
  }

  def rewriteAll(mapping: Map[N, N]): AdjacencyList[N] = {
    val rewritten = links.map {
      case (n, deps) =>
        val mappedKey = mapping.getOrElse(n, n)
        val mappedValue = deps.map(d => mapping.getOrElse(d, d))
        (mappedKey, mappedValue)
    }
    AdjacencyList(rewritten)
  }
}

object AdjacencyList {
  def empty[N]: AdjacencyList[N] = apply(Map.empty[N, Set[N]])
  def apply[N](links: (N, IterableOnce[N])*): AdjacencyList[N] = {
    apply(links.toMap.view.mapValues(_.iterator.toSet).toMap)
  }

  def apply[N](links: Map[N, Set[N]]): AdjacencyList[N] = {
    val missing = missingKeys(links)
    val normalized = links ++ missing.map(m => (m, Set.empty[N])).toMap
    new AdjacencyList(links ++ normalized)
  }

  def missingKeys[N](links: Map[N, Set[N]]): Set[N] = {
    val allKeys = links.keySet ++ links.values.flatten
    val missing = allKeys -- links.keySet
    missing
  }

  def linear[N](ordered: Seq[N]): AdjacencyList[N] = {
    AdjacencyList(
      ordered
        .sliding(2)
        .flatMap {
          s =>
            if (s.size > 1) {
              Seq(s.head -> Set(s.last))
            } else {
              Seq.empty
            }
        }.toMap
    )
  }
}
