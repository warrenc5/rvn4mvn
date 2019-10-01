
import static java.lang.Boolean.TRUE;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import rvn.Rvn;

/**
 *
 * @author wcrossing
 */
public class SimpleTests {

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

        boolean result = new Rvn().new SimpleCommand(regex) {
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

        List<Integer> list = Rvn.rangeToIndex(range);
        List<Integer> expected = Arrays.asList(new Integer[]{10, 11, 1, 2, 3, 8, 6, 7, 8, 9, 9});
        assertEquals(expected, list);

    }
}
