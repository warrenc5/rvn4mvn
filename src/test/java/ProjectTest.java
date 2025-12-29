
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import static org.junit.Assert.assertTrue;
import org.junit.jupiter.api.Test;
import rvn.NVV;
import rvn.Project;

/**
 *
 * @author wcrossing
 */
public class ProjectTest {

    private Logger log = Logger.getLogger(SimpleTests.class.getName());

    @Test
    public void testNvvHashcode() {
        Map<NVV, String> projects = new HashMap<>();

        NVV nvv = new NVV("me", "mine", "1.9");
        String v = "${project.groupId}.alt";
        NVV nvv2 = new NVV("meme", v, "2.9");

        Pattern p = Pattern.compile("\\$\\{([a-zA-Z0-9_\\.]+)}");
        assertTrue(p.matcher(v).find());
        //v = p.matcher(v).replaceAll("mine");
        //assertEquals("mine.alt",v);

        nvv2.project = nvv;
        nvv.deps.add(nvv2);

        Project.getInstance().resolveGAV(nvv);

        log.info(nvv + " " + nvv.deps);

    }
}
