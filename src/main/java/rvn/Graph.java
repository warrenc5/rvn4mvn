package rvn;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 *
 * @author wozza
 */
public class Graph<T> extends LinkedHashMap<NVV, Set<NVV>> {

    private Logger logger = Logger.getLogger(Graph.class.getName());
    Deque<NVV> q = new ArrayDeque<>();
    ArrayBlockingQueue<NVV> oq;

    public Graph() {
        this.oq = new ArrayBlockingQueue<>(10000);
    }

    @Override
    public void clear() {
        super.clear();
        q.clear();
    }

    public Graph<T> insert(Edge edge) {
        Set<NVV> n = super.getOrDefault(edge.nvv1, new LinkedHashSet<>());
        n.add(edge.nvv2);
        super.putIfAbsent(edge.nvv1, n);
        return this;
    }

    public Stream<NVV> paths() {
        return paths(null);
    }

    public Stream<NVV> paths(NVV nvv) {

        this.entrySet().stream().flatMap(e -> {
            return e.getValue().stream().filter(n -> (e.getKey().equals(nvv) | nvv == null))
                    .flatMap(v -> {
                        findAncestor(e.getKey(), v);
                        return q.stream();
                    });
        }).distinct().forEach(e -> oq.add(e));
        logger.fine("q->" + q.toString() + " " + q.size());
        this.clear();

        Spliterator<NVV> spliterator = new Spliterators.AbstractSpliterator<NVV>(oq.size(), 0) {
            @Override
            public boolean tryAdvance(Consumer<? super NVV> action) {
                logger.info("bq->" + oq.toString().replace(',', '\n') + " " + oq.size() + " remaining");
                NVV element = null;
                try {
                    Thread.yield();
                    element = oq.poll(10, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ex) {
                }
                if (element == null) {
                    return false;
                } else {
                    action.accept(element);
                    return true;
                }
            }

        };
        return StreamSupport.stream(spliterator, false);
    }

    private Collection<NVV> order(NVV a, NVV c) {
        findAncestor(a, c);
        return q;
    }

    private void findAncestor(NVV n1, NVV n2) {
        this.entrySet().stream().filter(n -> n.getValue().contains(n2))
                .map(n -> new Edge(n.getKey(), n2))
                .collect(Collectors.toSet()).stream()
                .forEach(e -> {
            logger.fine(n1 + " " + n2 + " " + e.toString() + "  " + q.toString());
                    q.remove(e.nvv1);
                    q.offerFirst(e.nvv1);

                    if (e.nvv1.equals(e.nvv2)) {
                        return;
                    }

                    if (!(n1.equals(e.nvv1) && n2.equals(e.nvv2))) {
                        findAncestor(n2, e.nvv1);
                    }

                    q.remove(e.nvv2);
                    q.offerLast(e.nvv2);

                });
    }

    public boolean contains(Edge edge) {
        return this.containsKey(edge.nvv1) && this.getOrDefault(edge.nvv1, new HashSet<>()).contains(edge.nvv2);
    }
}
