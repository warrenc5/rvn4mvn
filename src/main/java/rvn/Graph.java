package rvn;

import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.jgrapht.alg.TransitiveClosure;
import org.jgrapht.traverse.TopologicalOrderIterator;

/**
 *
 * @author wozza
 */
public class Graph extends org.jgrapht.graph.SimpleDirectedGraph<NVV, Edge<NVV>> {

    private Logger log = Logger.getLogger(Graph.class.getName());

    public Graph() {
        super(null, new Supplier<Edge<NVV>>() {
            @Override
            public Edge get() {
                return new Edge();
            }
        }, false);

    }

    public Graph insert(Edge<NVV> edge) {
        synchronized (this) {

            this.addEdge(edge.nvv1, edge.nvv2);
            return this;
        }
    }

    public void clear() {
        synchronized (this) {
            Set<NVV> vertexSet = new HashSet<>(this.vertexSet());
            this.removeAllVertices(vertexSet);
        }
    }

    Deque<NVV> reduceG2Q() {
        TransitiveClosure.INSTANCE.closeSimpleDirectedGraph(this);
        log.info("result " + this.toString());

        synchronized (this) {
            Iterator iterator = new TopologicalOrderIterator(this);

            Deque<NVV> q = new ConcurrentLinkedDeque<>();
            while (iterator.hasNext()) {
                NVV nvv = (NVV) iterator.next();
                q.addLast(nvv);
                log.warning("BUILDING " + nvv.toString());
            }
            return q;
        }
    }
}
