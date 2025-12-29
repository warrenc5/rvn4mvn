package rvn;

import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Supplier;
import java.util.logging.Logger;
import static java.util.stream.Collectors.toList;
import java.util.stream.StreamSupport;
import org.jgrapht.alg.TransitiveClosure;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.traverse.TopologicalOrderIterator;

/**
 *
 * @author wozza
 */
public class Graph<V extends NVV, E extends Edge<V>> extends org.jgrapht.graph.SimpleDirectedGraph<NVV, Edge<NVV>> {

    private Logger log = Logger.getLogger(Graph.class.getName());

    public Graph() {
        super(null, new Supplier<Edge<NVV>>() {
            @Override
            public Edge get() {
                return new Edge();
            }
        }, false);

    }

    public void insert(V nvv1, V nvv2) {
        insert((E) new Edge<V>(nvv1, nvv2));
    }

    public Graph insert(E edge) {
        synchronized (this) {
            Set<V> vertexSet = new HashSet(this.vertexSet());

            /**
            if (!vertexSet.contains(edge.nvv1)) {
                log.warning("edge not in vertex " + edge.nvv1);
            }**/
            if (!this.containsVertex(edge.nvv1)) {
                this.addVertex(edge.nvv1);
            }

            if (!this.containsVertex(edge.nvv2)) {
                this.addVertex(edge.nvv2);
            }

            this.addEdge(edge.nvv1, edge.nvv2);
            return this;
        }
    }

    public void clear() {
        synchronized (this) {
            Set<NVV> vertexSet = new HashSet(this.vertexSet());

            this.removeAllVertices(vertexSet);
        }
    }

    public Deque<NVV> reduceG2Q() {
        CycleDetector<V, E> cycleDetector = new CycleDetector(this);

        if (cycleDetector.detectCycles()) {
            Iterator<V> iterator;
            Set<V> subCycle;
            V cycle;

            Set<V> cycleVertices = cycleDetector.findCycles();

            while (!cycleVertices.isEmpty()) {
                iterator = cycleVertices.iterator();
                cycle = iterator.next();

                subCycle = cycleDetector.findCyclesContainingVertex(cycle);
                for (NVV sub : subCycle) {
                    System.out.println("   " + sub);
                    cycleVertices.remove(sub);
                }
            }
        }

        TransitiveClosure.INSTANCE.closeSimpleDirectedGraph(this);

        synchronized (this) {
            Spliterator<NVV> spliterator = Spliterators.spliteratorUnknownSize(new TopologicalOrderIterator<>(this), 0);
            List<NVV> list = StreamSupport.stream(spliterator, false).collect(toList());
            Collections.reverse(list);

            Deque<NVV> q = new ConcurrentLinkedDeque<>(list);
            log.info("result " + q.toString());
            return q;
        }
    }
}
