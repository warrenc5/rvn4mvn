
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Test;
import rvn.Edge;
import rvn.Graph;
import rvn.NVV;

/**
 *
 * @author wozza
 */
public class BuildGraph {

    NVV a = new NVV("a"), b = new NVV("b"), c = new NVV("c"), d = new NVV("d"), e = new NVV("e"), f = new NVV("f"), k = new NVV("k"), x = new NVV("x"), y = new NVV("y"), z = new NVV("z");

    List<NVV> ordered = new ArrayList<>();

    Graph<NVV> graph = new Graph<>();

    @Test
    public void testGraph() throws Exception {
        Set<Edge> edges = new HashSet<>();

        edges.addAll(Arrays.asList(
                new Edge(a, b),
                new Edge(a, e),
                new Edge(a, d),
                new Edge(d, c),
                new Edge(d, c),
                new Edge(d, c),
                new Edge(a, c),
                new Edge(e, f),
                new Edge(f, d),
                new Edge(d, b),
                new Edge(d, z),
                new Edge(k, k),
                new Edge(k, k)
        ));

        edges.forEach(edge -> {
            graph.insert(edge);
        });

        System.out.println("g->" + graph.toString());
        ordered = graph.paths().collect(Collectors.toList());
        System.out.println("o->" + ordered.toString());
        Assert.assertArrayEquals(new NVV[]{a, e, f, d, c, b, k, z}, ordered.toArray(new NVV[ordered.size()]));

        graph
                .insert(new Edge(x, z))
                .insert(new Edge(y, z))
                .insert(new Edge(y, z))
                .insert(new Edge(y, x));

        System.out.println("g->" + graph.toString());
        ordered = graph.paths().collect(Collectors.toList());
        System.out.println("o->" + ordered.toString());
        Assert.assertArrayEquals(new NVV[]{y, x, z}, ordered.toArray(new NVV[ordered.size()]));

    }

}
