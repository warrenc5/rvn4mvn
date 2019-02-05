package rvn;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ArrayBlockingQueue;
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

        this.entrySet().stream().flatMap(e -> {
            return e.getValue().stream().flatMap(v -> {
                findPrevious2(e.getKey(), v);
                return q.stream();
            });
        }).distinct().forEach(e->oq.add(e));

        System.out.println("q->" + q.toString() + " " + q.size());
        this.clear();
        System.out.println("oq->" + oq.toString() + " " + oq.size());


        Spliterator<NVV> spliterator = new Spliterators.AbstractSpliterator<NVV>(oq.size(), 0) {
            @Override
            public boolean tryAdvance(Consumer<? super NVV> action) {
                NVV element = oq.poll();
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
        findPrevious2(a, c);
        return q;
    }

    private void findPrevious2(NVV n1, NVV n2) {
        //.filter(e -> !(e.nvv1.equals(n1) && e.nvv2.equals(n2)))
        //System.out.println(i + " " + n1 + " " + n2 + " " + e.toString() + "  " + q.toString());
        this.entrySet().stream().filter(n -> n.getValue().contains(n2))
                .map(n -> new Edge(n.getKey(), n2))
                .collect(Collectors.toSet()).stream()
               // .filter(e -> !(e.nvv1.equals(e.nvv2)))
                .forEach(e -> {
            System.out.println(n1 + " " + n2 + " " + e.toString() + "  " + q.toString());
            q.remove(e.nvv1);
            q.offerFirst(e.nvv1);

            if(e.nvv1.equals(e.nvv2))
                    return;
                findPrevious2(n2, e.nvv1);

            q.remove(e.nvv2);
            q.offerLast(e.nvv2);

        });
    }
}
