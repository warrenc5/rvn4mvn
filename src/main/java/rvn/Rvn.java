package rvn;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author wozza
 */
public class Rvn extends Thread {

    private static List<String> locations;
    private Set<WatchKey> keys;
    private Map<WatchKey, Path> keyPath;
    private Map<NVV, Path> buildSet;
    private final Map<NVV, Set<NVV>> projects;
    private Map<Path, Process> processMap;

    private Map<String, String> commands;

    List<String> matchFileIncludes;
    List<String> matchFileExcludes;
    List<String> matchDirIncludes;
    List<String> matchDirExcludes;
    List<String> matchArtifactIncludes;
    List<String> matchArtifactExcludes;

    private Logger logger = Logger.getLogger(Rvn.class.getName());

    private MessageDigest md = null;

    private Map<Path, String> hashes;
    private final WatchService watcher;

    public static void main(String[] args) throws Exception {
        Rvn rvn = new Rvn();
        rvn.locations.addAll(0, Arrays.asList(args));

        rvn.start();
        rvn.processStdIn();
        System.out.println(String.format("exited"));

    }

    public Rvn() throws Exception {
        locations = new ArrayList<>();
        keys = new HashSet<>(locations.size());
        keyPath = new HashMap<>();
        watcher = FileSystems.getDefault().newWatchService();
        projects = new HashMap<>();
        buildSet = new HashMap<>();
        processMap = new HashMap<>();
        commands = new LinkedHashMap<>();
        hashes = new HashMap<>();
        matchFileIncludes = new ArrayList<>();
        matchFileExcludes = new ArrayList<>();
        matchDirIncludes = new ArrayList<>();
        matchDirExcludes = new ArrayList<>();
        matchArtifactIncludes = new ArrayList<>();
        matchArtifactExcludes = new ArrayList<>();

        loadConfiguration();

        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            logger.warning(e.getMessage());
        }

    }

    private void processStdIn() {
        Scanner scanner = new Scanner(System.in);
        scanner.useDelimiter(System.getProperty("line.separator"));
        Spliterator<String> splt = Spliterators.spliterator(scanner, Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.NONNULL);

        while (this.isAlive()) {
            StreamSupport.stream(splt, false).onClose(scanner::close).forEach(this::processCommand);
        }

        logger.info(String.format("commandless"));
    }

    private void processCommand(String command) {
        try {
            logger.info(String.format("%1$s", LocalTime.now()));
            jdk.nashorn.api.scripting.ScriptObjectMirror result = (jdk.nashorn.api.scripting.ScriptObjectMirror) getEngine().eval("config=" + command);
            buildConfiguration(result);
        } catch (ScriptException ex) {
            logger.warning(String.format("%1$s", ex.getMessage()));
        }
    }

    public void registerPath(String uri) {
        Path dir = Paths.get(uri);
        logger.info(String.format("watching %1$s", dir));
        registerPath(dir);
    }

    public void registerPath(Path path) {

        try {

            if (Files.isDirectory(path)) {
                try (Stream<Path> stream = Files.list(path)) {
                    stream.filter(child -> matchDirectories(child) || matchFiles(child)).forEach(this::registerPath);
                }
            } else if (path.toFile().toString().endsWith(".pom")) {
                processPom(path);
            } else if (path.endsWith("pom.xml")) {
                processPom(path);

                Path parent = path.getParent();
                watchRecursively(parent);
            } else {

            }
        } catch (IOException | SAXException | XPathExpressionException | ParserConfigurationException ex) {
            logger.info(String.format("register failed %1$s %2$s", ex.getClass().getName(), ex.getMessage()));
        }
    }

    public void watchRecursively(Path dir) {
        watch(dir);
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(child -> Files.isDirectory(child) && matchDirectories(child)).forEach(this::watchRecursively);
        } catch (IOException ex) {
            logger.info(String.format("recurse failed %1$s %2$s", ex.getClass().getName(), ex.getMessage()));
        }
    }

    public void watch(Path dir) {
        try {
            WatchKey key = dir.register(watcher,
                    ENTRY_CREATE,
                    ENTRY_DELETE,
                    ENTRY_MODIFY);

            keys.add(key);
            keyPath.put(key, dir);
            logger.fine(String.format("watching %1$s %2$d", dir, key.hashCode()));
        } catch (IOException x) {
            System.err.println(x);
        }

    }

    @Override
    public void run() {
        Instant then = Instant.now();
        locations.stream().forEach(this::registerPath);
        Duration duration = Duration.between(then, Instant.now());

        logger.info(String.format("started %1$s", duration));
        logger.info(String.format("keys %1$s", this.keys.size()));

        while (this.isAlive()) {
            logger.info(String.format("waiting.."));
            WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException x) {
                return;
            }

            try {
                for (WatchEvent<?> event : key.pollEvents()) {
                    try {
                        WatchEvent.Kind<?> kind = event.kind();

                        if (kind == OVERFLOW) {
                            continue;
                        }

                        WatchEvent<Path> ev = (WatchEvent<Path>) event;
                        Path filename = ev.context();
                        Path child = keyPath.get(key).resolve(filename);
                        logger.fine(String.format("kind %1$s %2$s %3$d", ev.kind(), child, key.hashCode()));

                        if (kind == ENTRY_DELETE) {
                            Optional<WatchKey> cancelKey = keyPath.entrySet().stream().filter(e -> child.equals(e.getValue())).map(e -> e.getKey()).findFirst();
                            if (cancelKey.isPresent()) {
                                cancelKey.get().cancel();
                            }
                        } else if (kind == ENTRY_CREATE) {
                            this.registerPath(child);
                        } else if (kind == ENTRY_MODIFY) {
                            processPath(child);
                        }
                    } catch (Exception ex) {
                        Logger.getLogger(Rvn.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            } finally {

                boolean valid = key.reset();
                if (!valid) {
                    break;
                }
            }
        }
        logger.info(String.format("outrun"));
    }

    public Optional<String> getExtensionByStringHandling(String filename) {
        return Optional.ofNullable(filename)
                .filter(f -> f.contains("."))
                .map(f -> f.substring(filename.lastIndexOf(".") + 1));
    }

    private void processPathSafe(String uri) {
        try {
            processPath(uri);

        } catch (XPathExpressionException | SAXException | IOException | ParserConfigurationException ex) {
            Logger.getLogger(Rvn.class.getName()).log(Level.SEVERE, null, ex);
        } catch (Exception ex) {
            Logger.getLogger(Rvn.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void processPath(String uri) throws XPathExpressionException, SAXException, IOException, ParserConfigurationException, Exception {
        System.err.println("in ->" + uri);
        Path path = Paths.get(uri);
        processPath(path);
    }

    private void processPath(Path path) throws XPathExpressionException, SAXException, IOException, ParserConfigurationException, Exception {
        if (!Files.isRegularFile(path)) {
            return;
        }

        if (!matchFiles(path)) {
            return;
        }

        if (hashes.containsKey(path) && toSHA1(path).equals(hashes.get(path))) {
            return;
        }
        try {
            NVV nvv = findPom(path);
            if (matchNVV(nvv)) {
                processChange(nvv, buildSet.get(nvv));
            }
        } catch (Exception x) {
            logger.warning(String.format("process: %1$s - %2$s", path, x.getMessage()));
            x.printStackTrace();
        }

    }

    private Document loadPom(Path path) throws SAXException, XPathExpressionException, IOException, ParserConfigurationException {
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = builderFactory.newDocumentBuilder();
        Document xmlDocument = builder.parse(path.toFile());
        return xmlDocument;
    }

    private void processPom(Path path) throws SAXException, XPathExpressionException, IOException, ParserConfigurationException {
        Document xmlDocument = loadPom(path);
        XPath xPath = XPathFactory.newInstance().newXPath();
        Node project = (Node) xPath.compile("/project").evaluate(xmlDocument, XPathConstants.NODE);
        NVV nvv = nvvFrom(project);
        buildSet.put(nvv, path);
        hashes.put(path, this.toSHA1(path));

        NodeList nodeList = (NodeList) xPath.compile("//dependency").evaluate(xmlDocument, XPathConstants.NODESET);

        Spliterator<Node> splt = Spliterators.spliterator(new NodeListIterator(nodeList), nodeList.getLength(), Spliterator.ORDERED | Spliterator.NONNULL);
        Set<NVV> deps = StreamSupport.stream(splt, true).map(this::processDependency).filter(this::matchNVV).collect(Collectors.toSet());

        try {
            Node parent = (Node) xPath.compile("/project/parent").evaluate(xmlDocument, XPathConstants.NODE);
            NVV parentNvv = nvvFrom(parent);

            if (nvv.vendor.isEmpty()) {
                nvv.vendor = parentNvv.vendor;
            }
            if (nvv.version.isEmpty()) {
                nvv.version = parentNvv.version;
            }

            deps.add(parentNvv);
            logger.fine(String.format("with parent %1$s", nvv.toString()));
        } catch (Exception e) {

        }
        logger.fine(String.format("tracking %1$s %2$s", nvv.toString(), path));
        projects.put(nvv, deps);
    }

    private NVV processDependency(Node n) {
        try {
            NVV nvv = nvvFrom(n);
            logger.fine(String.format("depends on %1$s", nvv.toString()));
            return nvv;
        } catch (XPathExpressionException ex) {
            Logger.getLogger(Rvn.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    private NVV nvvFrom(Node context) throws XPathExpressionException {
        XPath xPath = XPathFactory.newInstance().newXPath();
        return new NVV(
                xPath.compile("artifactId").evaluate(context),
                xPath.compile("groupId").evaluate(context),
                xPath.compile("version").evaluate(context)
        );
    }

    private void processChange(NVV nvv, Path path) {
        logger.info(String.format("changed %1$s %2$s", nvv.toString(), path));

        hashes.remove(path);

        Path dir = buildSet.get(nvv);

        if (dir == null) {
            return;
        }

        if (dir.endsWith("pom.xml")) {
            if (!doBuild(nvv, path)) {
                throw new RuntimeException(String.format("%1$s failed", nvv));
            }

        }

        try {
            hashes.put(path, this.toSHA1(path));
        } catch (IOException ex) {
            Logger.getLogger(Rvn.class.getName()).log(Level.SEVERE, null, ex);
        }

        try {
            this.projects.entrySet().stream()
                    .filter(e -> e.getValue().stream()
                    .filter(nvv2 -> nvv2.equals(nvv)).findAny().isPresent())
                    .forEach(e -> processChange(e.getKey(), this.buildSet.get(e.getKey())));
        } catch (RuntimeException x) {
            logger.warning(String.format("%1$s %2$s %3$s", nvv.toString(), path, x.getMessage()));
        }
        //}
    }

    private boolean matchFiles(Path path) {
        return matchFileIncludes.stream().filter(s -> path.toAbsolutePath().toString().matches(s)).findFirst().isPresent() //FIXME: absolutely
                && !matchFileExcludes.stream().filter(s -> path.toAbsolutePath().toString().matches(s)).findFirst().isPresent(); //FIXME: absolutely
    }

    private boolean matchDirectories(Path path) {
        return matchDirIncludes.stream().filter(s -> path.toAbsolutePath().toString().matches(s)).findFirst().isPresent() //FIXME: absolutely
                && !matchDirExcludes.stream().filter(s -> path.toAbsolutePath().toString().matches(s)).findFirst().isPresent(); //FIXME: absolutely
    }

    private boolean matchNVV(NVV nvv) {
        return matchArtifactIncludes.stream().filter(s -> nvv.toString().matches(s)).findFirst().isPresent() //FIXME: absolutely
                && !matchArtifactExcludes.stream().filter(s -> nvv.toString().matches(s)).findFirst().isPresent(); //FIXME: absolutely
    }

    private boolean doBuild(NVV nvv, Path path) {
        if (processMap.containsKey(path)) {
            logger.info(String.format("already building %1$s", nvv));
            processMap.get(path).destroyForcibly();
        }

        String command = locateCommand(nvv, path);
        logger.info(String.format("build %1$s %2$s", nvv, command));

        if (command.isEmpty()) {
            return true;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder()
                    .command(command.split(" "))
                    .inheritIO();
            Process p = pb.start();
            processMap.put(path, p);
            p.waitFor(1, TimeUnit.MINUTES);
            processMap.remove(path);

            logger.info(String.format("exit %1$s %2$s", nvv, p.exitValue()));
            return p.exitValue() == 0;

        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(Rvn.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }

    private String locateCommand(NVV nvv, Path path) {
        return String.format(commands.entrySet().stream()
                .filter(e -> path.toString().matches(e.getKey()) || nvv.toString().matches(e.getKey())).map(e -> e.getValue()).findFirst().orElse(commands.get("::::")), path);
    }

    private boolean isPom(Path path) {
        return Arrays.asList(new String[]{".*pom.xml", ".*.pom$"})
                .stream().filter(s -> path.toAbsolutePath().toString().matches(s)).findFirst().isPresent(); //FIXME: absolutely
    }

    private NVV findPom(Path path) throws Exception {
        return buildSet.entrySet().stream().filter(e
                -> isBasePath(e.getValue(), path)).reduce(null, (e1, e2)
                -> (e1 != null && e1.getValue().getNameCount() > e2.getValue().getNameCount()) ? e1 : e2).getKey();
        //map(e -> e.getKey()).reducefindFirst().orElseThrow(() -> new Exception("not known " + path));
    }

    private boolean isBasePath(Path project, Path changed) {
        return (changed.startsWith(project.getParent()));
    }

    static {
        try {
            URI logging = Rvn.class.getResource("/logging.properties").toURI();
            //System.out.println(String.format("%1$s %2$s", logging, Files.exists(Paths.get(logging))));
            LogManager.getLogManager().readConfiguration(logging.toURL().openStream());
        } catch (URISyntaxException | IOException ex) {
        }
    }

    public String toSHA1(Path value) throws IOException {
        Files.lines(value).forEach(s -> md.update(s.getBytes()));
        return new String(md.digest());
    }

    private void loadConfiguration() throws IOException, ScriptException, URISyntaxException {
        URL configURL = Rvn.class.getResource("/rvn.json");
        Path config = null;

        logger.info(String.format("trying configuration %1$s", configURL));

        if (configURL == null || configURL.toExternalForm().startsWith("jar:")) {
            config = Paths.get(System.getProperty("user.home") + File.separator + ".m2" + File.separator + "rvn.json");
            if (!Files.exists(config)) {
                logger.info(String.format("creating new configuration from %1$s", configURL));
                try (
                        Reader reader = new InputStreamReader(configURL.openStream());
                        Writer writer = new FileWriter(config.toFile());) {
                    while (reader.ready()) {
                        writer.write(reader.read());
                    }
                    writer.flush();
                }
                logger.info(String.format("written new configuration to %1$s", config));
            }
        } else {
            config = Paths.get(configURL.toURI());
        }

        logger.info(String.format("loading configuration %1$s", config));

        Reader scriptReader = Files.newBufferedReader(config);
        jdk.nashorn.api.scripting.ScriptObjectMirror result = (jdk.nashorn.api.scripting.ScriptObjectMirror) getEngine().eval(scriptReader);
        buildConfiguration(result);

    }

    private void buildConfiguration(ScriptObjectMirror result) {

        String key = null;
        if (result.hasMember(key = "locations")) {
            ScriptObjectMirror v = (ScriptObjectMirror) result.get(key);
            locations.addAll(asArray(v));
            logger.info(key + " " + locations.toString());

        }
        if (result.hasMember(key = "watchDirectories")) {
            ScriptObjectMirror v = (ScriptObjectMirror) result.get(key);
            this.matchDirIncludes.addAll(asArray((ScriptObjectMirror) v.getMember("includes")));
            this.matchDirExcludes.addAll(asArray((ScriptObjectMirror) v.getMember("excludes")));
            logger.info(key + " " + matchDirIncludes.toString());
            logger.info(key + " " + matchDirExcludes.toString());
        }

        if (result.hasMember(key = "activeFiles")) {
            ScriptObjectMirror v = (ScriptObjectMirror) result.get(key);
            this.matchFileIncludes.addAll(asArray((ScriptObjectMirror) v.getMember("includes")));
            this.matchFileExcludes.addAll(asArray((ScriptObjectMirror) v.getMember("excludes")));

            logger.info(key + " " + matchFileIncludes.toString());
            logger.info(key + " " + matchFileExcludes.toString());
        }

        if (result.hasMember(key = "activeArtifacts")) {
            ScriptObjectMirror v = (ScriptObjectMirror) result.get(key);
            this.matchArtifactIncludes.addAll(asArray((ScriptObjectMirror) v.getMember("includes")));
            this.matchArtifactExcludes.addAll(asArray((ScriptObjectMirror) v.getMember("excludes")));

            logger.info(key + " " + matchArtifactIncludes.toString());
            logger.info(key + " " + matchArtifactExcludes.toString());
        }

        if (result.hasMember(key = "buildCommands")) {
            ScriptObjectMirror v = (ScriptObjectMirror) result.get(key);
            v.entrySet().forEach(e -> commands.put(e.getKey(), e.getValue().toString()));
            System.out.println(key + " " + commands.toString());
        }
    }

    private Collection<? extends String> asArray(ScriptObjectMirror v) {
        List result = new ArrayList();
        if (v.isArray() && !v.isEmpty()) {
            for (int i = 0; i < v.size(); i++) {
                result.add((String) v.getSlot(i));
            }
        }
        return result;
    }

    private ScriptEngine getEngine() {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("JavaScript");
        return engine;

    }
}

class NVV {

    public String name, vendor, version;

    public NVV(String name, String vendor, String version) {
        this.name = name;
        this.vendor = vendor;
        this.version = version;
    }

    @Override
    public String toString() {
        return String.format("%1$s::%2$s::%3$s", vendor, name, version);
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 67 * hash + Objects.hashCode(this.name);
        hash = 67 * hash + Objects.hashCode(this.vendor);
        hash = 67 * hash + Objects.hashCode(this.version);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final NVV other = (NVV) obj;
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        if (!Objects.equals(this.vendor, other.vendor)) {
            return false;
        }
        return true;

    }

}

/**
 * @SuppressWarnings("unchecked") static <T> WatchEvent<T>
 * cast(WatchEvent<?>
 * event) { return (WatchEvent<Path>) event; }
 *
 */
