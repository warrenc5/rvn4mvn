
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import static org.junit.Assert.assertFalse;
import org.junit.Test;
import rvn.ImportFinder;

/**
 *
 * @author wcrossing
 */
public class JavaImportDepsTest {

    final Logger log = Logger.getLogger(this.getClass().getName());

    @Test
    public void testFindImports() throws Exception {

        List<Path> paths = recurse(Paths.get("C:\\work\\rvn4mvn-master\\"));
        log.info(paths.toString());
        List<Path> deps = new ImportFinder(paths).findImports(Paths.get("C:\\work\\rvn4mvn-master\\src\\main\\java\\rvn", "Rvn.java"));
        assertFalse(deps.isEmpty());
    }

    private List<Path> recurse(Path path) {
        List<Path> paths = new ArrayList<>();
        if (Files.isDirectory(path)) {
            Stream<Path> list = null;
            try {
                list = Files.list(path);
                list.forEach(p -> {
                    if (Files.isDirectory(p)) {
                        recurse(p);
                    } else {
                        paths.add(p);
                    }
                });
            } catch (IOException ex) {
                Logger.getLogger(JavaImportDepsTest.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            paths.add(path);
        }
        return paths;
    }

}
