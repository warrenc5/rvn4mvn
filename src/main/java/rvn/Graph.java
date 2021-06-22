package rvn;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Logger;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 *
 * @author wozza
 */
public class Graph<T> extends ConcurrentSkipListMap<T, Set<T>> {

    private Logger log = Logger.getLogger(Graph.class.getName());
    Deque<T> q = new ArrayDeque<>();

    public Graph() {
    }

    @Override
    public synchronized void clear() {
        super.clear();
        q.clear();
    }

    public void truncate() {
    }

    public Graph<T> insert(Edge<T> edge) {
        Set<T> n = super.getOrDefault(edge.nvv1, new LinkedHashSet<>());
        if (!edge.leaf) {
            n.add(edge.nvv2);
        }
        super.putIfAbsent(edge.nvv1, n);
        return this;
    }

    public Stream<T> paths2() {
        return paths2(null);
    }

    public Stream<T> paths2(T nvv) {
        GraphIterator<T> g = new GraphIterator<T>(this.roots());
        Spliterator<T> supplier = Spliterators.spliterator(g, this.size(), 0);
        return StreamSupport.stream(supplier, false);
    }

    public boolean contains(Edge edge) {
        if (!this.containsKey(edge.nvv1)) {
            return false;
        }
        if (edge.nvv2 == null) {
            return true;
        }
        if (edge.nvv2.equals(edge.nvv1)) {
            return true;
        }
        return this.getOrDefault(edge.nvv1, new HashSet<>()).contains(edge.nvv2);
    }

    public List<T> roots() {
        return this.keySet().stream().filter(k -> {
            return this.values().stream().allMatch(
                    v -> !v.contains(k)
            );
        }).collect(toList());
    }

    private boolean isLeaf(T current) {
        if (current == null) {
            return true;
        }
        Set<T> v = this.get(current);
        return v == null || v.isEmpty();
    }
    static int depth = 0;

    class GraphIterator<K> implements Iterator<K> {

        private Set<K> visited = new HashSet<>();
        K current;
        private Deque<Iterator<K>> it = new ArrayDeque<>();
        private Graph<K> g = (Graph<K>) Graph.this;
        private Iterator<K> lit;

        public GraphIterator() {
        }

        public GraphIterator(Collection<K> c) {
            log("init " + c.toString() + " " + current + " " + it.size());
            lit = c.iterator();
        }

        public GraphIterator(Deque<Iterator<K>> it, Set<K> visited, Collection<K> c) {
            this.it = it;
            this.visited = visited;
            log("init " + c.toString() + " " + current + " " + it.size());
            lit = c.iterator();
        }

        @Override
        public boolean hasNext() {
            //log("?" + current + " " + it.size() + " " + g.isLeaf(current) + " " + (it.isEmpty() ? false : it.peek().hasNext()));

            boolean hasNext = false;

            if (current != null && !g.isLeaf(current)) {
                lit = pushNew(current);
            }

            hasNext = lit.hasNext();

            if (!hasNext) {
                if (!it.isEmpty()) {
                    pop();
                    if (lit != null) {
                        hasNext = lit.hasNext(); //recurse
                    } else {
                        //end
                    }

                } else {
                    //end
                }

            }

            return hasNext;
        }

        @Override
        public K next() {
            log(">" + current + " " + visited.toString());
            current = lit.next();
            // it.peek().remove();
            visited.add(current);
            log("<" + current + " " + visited.toString());
            return current;
        }

        private Iterator<K> pushNew(K c) {
            log("-?" + c);
            Set<K> v = Graph.this.getOrDefault(c, Collections.EMPTY_SET);
            Set<K> unvisited = v.stream().filter(k -> !visited.contains(k)).collect(toSet());
            if (!unvisited.isEmpty()) {
                log("->" + c + " " + unvisited.toString());
                it.push(new GraphIterator<K>(it, visited, unvisited));
                depth++;
            }
            log("push " + it.size() + " " + depth);
            return lit;
            //}
        }

        private void log(String s) {
            System.out.println(s() + s);
        }

        private String s() {
            StringBuilder bob = new StringBuilder();
            for (int i = 0; i < depth; i++) {
                bob.append("  ");
            }

            return bob.toString();

        }

        private void pop() {
            lit = this.it.pop();
            depth--;
            log("pop " + it.size() + " " + depth);
        }
    }
}
