
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import rvn.Edge;
import rvn.Graph;
import rvn.NVV;

/**
 *
 * @author wozza
 */
public class BuildGraphTest {

    NVV a = new NVV("a"), b = new NVV("b"), c = new NVV("c"), d = new NVV("d"), e = new NVV("e"), f = new NVV("f"), k = new NVV("k"), x = new NVV("x"), y = new NVV("y"), z = new NVV("z");

    List<NVV> ordered = new ArrayList<>();

    Graph graph = new Graph();

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

        System.out.println("edges->" + edges.toString());
        edges.forEach(edge -> {
            graph.insert(edge);
        });

        System.out.println("graph->" + graph.toString());

        List<NVV> roots = null;//graph.roots();
        System.out.println("roots->" + roots);

        Assert.assertArrayEquals(new NVV[]{a, k}, roots.toArray(new NVV[roots.size()]));

        //ordered = graph.paths2().collect(Collectors.toList());
        System.out.println("o->" + ordered.toString());
        NVV[] ex = new NVV[]{a, e, f, d, c, b, k, z};
        System.out.println("expected->" + Arrays.asList(ex).toString());
        Assert.assertTrue(ordered.containsAll(Arrays.asList(ex)));
        System.out.println("contains all!");
        //Assert.assertThat(ordered.toArray(new NVV[ordered.size()]));
        Assert.assertArrayEquals(new NVV[]{a, e, f, d, c, b, k, z}, ordered.toArray(new NVV[ordered.size()]));

        graph
                .insert(new Edge(x, z))
                .insert(new Edge(y, z))
                .insert(new Edge(y, z))
                .insert(new Edge(y, x));

        System.out.println("g->" + graph.toString());
        //ordered = graph.paths2().collect(Collectors.toList());
        //System.out.println("o->" + ordered.toString());
        //Assert.assertArrayEquals(new NVV[]{x, y, z}, ordered.toArray(new NVV[ordered.size()]));

    }

}
