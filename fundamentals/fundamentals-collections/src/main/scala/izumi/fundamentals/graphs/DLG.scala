package izumi.fundamentals.graphs

import izumi.fundamentals.graphs.GraphImpl.{DirectedGraphPred, DirectedGraphSucc}
import izumi.fundamentals.graphs.GraphProperty.DirectedGraph
import izumi.fundamentals.graphs.struct.AdjacencyList
import izumi.fundamentals.graphs.tools.{Toposort, ToposortLoopBreaker}

final case class DLG[N, +M](
  nodes: Seq[N],
  meta: GraphMeta[N, M],
) extends AbstractGraph[N, M]
  with DirectedGraph[N, M]
  with DirectedGraphSucc[N, M]
  with DirectedGraphPred[N, M] {
  override lazy val successors: AdjacencyList[N] = AdjacencyList.linear(nodes)

  override lazy val predecessors: AdjacencyList[N] = AdjacencyList.linear(nodes.reverse)

  override def transposed: DirectedGraph[N, M] = DLG(nodes.reverse, meta)
}

object DLG {
  def fromDag[N, M](dag: DAG[N, M]): DLG[N, M] = {
    Toposort.cycleBreaking(dag.predecessors, ToposortLoopBreaker.dontBreak) match {
      case Left(value) =>
        throw new IllegalStateException(s"Non-linearizable DAG, this can't be. Error: $value; $dag")
      case Right(value) =>
        DLG(value, dag.meta)
    }
  }

  def from[N, M](dg: DG[N, M], breaker: ToposortLoopBreaker[N]): Either[ToposortError[N], DLG[N, M]] = {
    for {
      sorted <- Toposort.cycleBreaking(dg.predecessors, breaker)
    } yield {
      DLG(sorted, dg.meta)
    }
  }
}
