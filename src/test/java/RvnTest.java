
import au.com.devnull.graalson.JsonObjectBindings;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonWriter;
import javax.script.Bindings;
import static org.junit.Assert.assertNotNull;
import org.junit.Ignore;
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

    @Test
    @Ignore
    public void testGraalson() throws URISyntaxException, IOException {
        URL config = RvnTest.class.getResource("/test/rvn.json");
        Reader scriptReader = Files.newBufferedReader(Paths.get(config.toURI()));
        JsonReader reader = Json.createReader(scriptReader);
        JsonObject jsonObject = reader.readObject();
        javax.script.Bindings result = (Bindings) new JsonObjectBindings(jsonObject);
        System.out.println(result.toString());

        Writer writer = new OutputStreamWriter(System.out);

        JsonWriter jwriter = Json.createWriter(writer);
        jwriter.write(jsonObject);

    }

}
