package izumi.fundamentals.graphs

import izumi.fundamentals.graphs.GraphImpl.{DirectedGraphPred, DirectedGraphSucc}
import izumi.fundamentals.graphs.GraphProperty.DirectedAcyclicGraph
import izumi.fundamentals.graphs.struct.AdjacencyList
import izumi.fundamentals.graphs.tools.cycles.{CycleEraser, LoopBreaker}

final case class DAG[N, M] private (
  successors: AdjacencyList[N],
  predecessors: AdjacencyList[N],
  meta: GraphMeta[N, M],
) extends AbstractGraph[N, M]
  with DirectedAcyclicGraph[N, M]
  with DirectedGraphSucc[N, M]
  with DirectedGraphPred[N, M] {
  override def transposed: GraphProperty.DirectedGraph[N, M] = {
    DAG(predecessors, successors, meta)
  }
}

object DAG extends GraphSyntax[DAG] {

  def fromSucc[N, M](successors: AdjacencyList[N], meta: GraphMeta[N, M], breaker: LoopBreaker[N] = LoopBreaker.terminating[N]): Either[DAGError[N], DAG[N, M]] = {
    fromPred(successors.transposed, meta, breaker)
  }

  def fromPred[N, M](predecessors: AdjacencyList[N], meta: GraphMeta[N, M], breaker: LoopBreaker[N] = LoopBreaker.terminating[N]): Either[DAGError[N], DAG[N, M]] = {
    new CycleEraser[N](predecessors, breaker)
      .run().map(unsafeFactory(_, meta))
  }

  override protected def unsafeFactory[N, M](predecessors: AdjacencyList[N], meta: GraphMeta[N, M]): DAG[N, M] = {
    new DAG(predecessors.transposed, predecessors, meta.only(predecessors.links.keySet))
  }

}
