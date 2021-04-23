
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;
import rvn.Rvn;

/**
 *
 * @author wozza
 */
public class RvnTest {

    @Test
    public void test() throws URISyntaxException, Exception {
        URL config = RvnTest.class.getResource("/test/rvn.json");
        assertNotNull(config);
        File file = new File(config.toURI());
        Rvn.main(new String[]{file.getAbsolutePath()});
    }

}
