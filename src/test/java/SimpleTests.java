
import static java.lang.Boolean.TRUE;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import rvn.Globals;
import rvn.NVV;
import rvn.Rvn;
import rvn.SimpleCommand;
import rvn.Util;

/**
 *
 * @author wcrossing
 */
public class SimpleTests {

    private Logger log = Logger.getLogger(SimpleTests.class.getName());

    @Test
    public void testCommand() throws Exception {
        String regex = "^a ([0-9])$";
        Pattern pattern = Pattern.compile(regex);
        String input = "a 3";
        Matcher matcher = pattern.matcher(input);
        assertTrue(matcher.matches());
        assertTrue(matcher.groupCount() > 0);
        assertEquals(input, matcher.group(0));
        assertEquals("3", matcher.group(1));

        boolean result = new SimpleCommand(regex) {
            public Boolean configure(Iterator<String> i) throws Exception {
                assertTrue(i.hasNext());
                assertEquals(input, i.next());
                assertTrue(i.hasNext());
                assertEquals("3", i.next());
                return TRUE;
            }
        }.apply(input);

        assertTrue(result);
    }

    @Test
    public void indexRange() {
        String range = "10,11,1-3,8,6-9,9";

        List<Integer> list = Util.rangeToIndex(range);
        List<Integer> expected = Arrays.asList(new Integer[]{10, 11, 1, 2, 3, 8, 6, 7, 8, 9, 9});
        assertEquals(expected, list);

    }

    @Test
    public void testCommandMatching() throws Exception {
        Rvn rvn = new Rvn();
        String project = "group:art:ver";
        String match = "::";
        boolean selected = rvn.getProject().matchNVV(project, match);
        assertTrue(selected);

    }

    @Test
    public void testMavenOpts() throws Exception {

        Rvn rvn = new Rvn();

        String s = "-Xms32m -Xmx256m -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5009 -Xdebug";
        s = rvn.getBuildIt().removeDebug(s);
        assertEquals("-Xms32m -Xmx256m  -Xdebug", s);
    }

    @Test
    public void testMatches() throws Exception {
        Rvn rvn = new Rvn();

        Globals.config.matchDirIncludes.add(".*");
        Globals.config.matchDirExcludes.add(".*system32.*");
        Globals.config.matchDirExcludes.add("system32");
        Path p = Path.of("C:", "windows", "system32");
        log.info(p.toString() + " " + Files.exists(p));
        boolean m = rvn.getPathWatcher().matchSafe(p);
        assertFalse(m);
    }

    @Test
    public void testNvvHashcode() {
        Map<NVV, String> projects = new HashMap<>();

        NVV nvv = new NVV("group", "mine", "1.9");
        NVV nvv2 = new NVV("group", "mine", "2.9");

        projects.put(nvv, "yes");
        assertEquals(true, projects.containsKey(nvv2));
        assertEquals("yes", projects.get(nvv2));
    }
}
