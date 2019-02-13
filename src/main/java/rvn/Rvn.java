package rvn;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.xml.sax.ext.DefaultHandler2;

/**
 *
 * @author wozza
 */
public class Rvn extends Thread {

    private static Set<String> locations;
    private Set<WatchKey> keys;
    private Map<WatchKey, Path> keyPath;
    private Map<NVV, Path> buildArtifact;
    private Map<Path, NVV> buildPaths;
    private Map<NVV, Set<NVV>> projects;
    private Map<Path, Process> processMap;

    private Map<String, List<String>> commands;

    private List<String> matchFileIncludes;
    private List<String> matchFileExcludes;
    private List<String> matchDirIncludes;
    private List<String> matchDirExcludes;
    private List<String> matchArtifactIncludes;
    private List<String> matchArtifactExcludes;
    private List<CommandHandler> commandHandlers;

    private Logger logger = Logger.getLogger(Rvn.class.getName());

    private MessageDigest md = null;

    private Map<String, String> hashes;
    private final WatchService watcher;
    private Path config;

    public static void main(String[] args) throws Exception {
        Logger.getAnonymousLogger().warning(ANSI_GREEN + "Raven 4 Maven" + ANSI_RESET);
        Rvn rvn = new Rvn();
        rvn.locations.addAll(Arrays.asList(args));
        rvn.start();
        rvn.processStdIn();
        System.out.println(String.format("exited"));
    }

    private Integer timeout = 60;

    public Rvn() throws Exception {
        watcher = FileSystems.getDefault().newWatchService();
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            logger.warning(e.getMessage());
        }

        init();
        new BuildIt().start();
    }

    public void init() throws Exception {
        locations = new LinkedHashSet<>();
        keys = new HashSet<>(locations.size());
        keyPath = new HashMap<>();
        projects = new HashMap<>();
        buildArtifact = new LinkedHashMap<>();
        buildPaths = new LinkedHashMap<>();
        processMap = new LinkedHashMap<>();
        commands = new LinkedHashMap<>();
        hashes = new HashMap<>();
        matchFileIncludes = new ArrayList<>();
        matchFileExcludes = new ArrayList<>();
        matchDirIncludes = new ArrayList<>();
        matchDirExcludes = new ArrayList<>();
        matchArtifactIncludes = new ArrayList<>();
        matchArtifactExcludes = new ArrayList<>();

        this.readHashes();
        loadConfiguration();

        commandHandlers = new ArrayList<>();
        createCommandHandlers();
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

    private void processCommand(final String command2) {
        final String command = command2.trim();
        logger.info(String.format("%1$s", LocalTime.now()));
        commandHandlers.stream().forEach(c -> c.apply(command));

        /*
            //jdk.nashorn.api.scripting.ScriptObjectMirror result = (jdk.nashorn.api.scripting.ScriptObjectMirror) getEngine().eval("config=" + command);
            //buildConfiguration(result);
        } catch (ScriptException ex) {
            logger.warning(String.format("%1$s", ex.getMessage()));
        } catch (Exception ex) {
            logger.severe("command failed" + ex.getMessage());
        }*/
    }

    public void registerPath(String uri) {
        Path dir = Paths.get(uri);
        logger.info(String.format(ANSI_WHITE + "watching %1$s" +ANSI_RESET, dir));
        registerPath(dir);
    }

    public void registerPath(Path path) {
        try {
            if (Files.isDirectory(path)) {
                try (Stream<Path> stream = Files.list(path)) {
                    stream.filter(child -> matchDirectories(child) || matchFiles(child)).forEach(this::registerPath);
                }
            } else if (path.toFile().toString().endsWith(".pom")) {
                watchRecursively(path.getParent().getParent());
                processPom(path);
            } else if (path.endsWith("pom.xml")) {
                boolean skipped = !processPom(path);
                if (!skipped) {
                    Path parent = path.getParent();
                    watchRecursively(parent);
                }
            } else {

            }
        } catch (IOException | SAXException | XPathExpressionException | ParserConfigurationException ex) {
            logger.info(String.format("register failed %1$s %2$s %3$s", path, ex.getClass().getName(), ex.getMessage()));
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

    public void scan() {
        Instant then = Instant.now();
        locations.stream().forEach(this::registerPath);
        Duration duration = Duration.between(then, Instant.now());

        logger.fine("buildSet :" + buildPaths.toString().replace(',', '\n'));
        ArrayList watchSet;
        watchSet = new ArrayList(this.keyPath.values());
        Collections.sort(watchSet);
        logger.fine("watchSet :" + watchSet.toString().replace(',', '\n'));
        logger.info(String.format(ANSI_WHITE + "%1$s builds, %2$s projects, %3$s keys" + ANSI_RESET, buildPaths.size(), projects.size(), keys.size()));
    }

    @Override
    public void run() {
        scan();
        while (this.isAlive()) {
            logger.fine(String.format("waiting.."));
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

                        if (!keyPath.containsKey(key)) {
                            continue;
                        }
                        Path child = keyPath.get(key).resolve(filename);

                        if (child.equals(config)) {
                            try {
                                logger.info("config changed " + filename);
                                this.reloadConfiguration();
                            } catch (Throwable ex) {
                                Logger.getLogger(Rvn.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
                            }
                            continue;
                        }

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
                        Logger.getLogger(Rvn.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
                    }
                }

            } catch (Throwable ex) {
                Logger.getLogger(Rvn.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            } finally {

                boolean valid = key.reset();
                if (!valid) {
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

        boolean skipHash = !path.endsWith("pom.xml");

        if (!skipHash && hashes.containsKey(path.toString()) && toSHA1(path).equals(hashes.get(path.toString()))) {
            logger.info("no change detected" + path);
            return;
        }

        if (path.toString().endsWith(".pom")) {
            Document pom = this.loadPom(path);
            NVV nvv = this.nvvFrom(pom);
            this.nvvParent(nvv, pom);

            //logger.info(String.format("nvv: %1$s %2$s", path, nvv));
            if (matchNVV(nvv)) {
                this.buildDeps(nvv);
            }
            return;
        }

        try {
            NVV nvv = findPom(path);
            if (nvv == null) {
                logger.fine(String.format("no nvv: %1$s", path));
                return;
            } else {
                logger.info(String.format("nvv: %1$s %2$s", path, nvv));
            }

            if (matchNVV(nvv)) {
                processChange(nvv);
            }
        } catch (Exception x) {
            logger.warning(String.format("process: %1$s - %2$s", path, x.getMessage()));
            x.printStackTrace();
        }
    }

    private Document loadPom(Path path) throws SAXException, XPathExpressionException, IOException, ParserConfigurationException {
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = builderFactory.newDocumentBuilder();
        builder.setErrorHandler(new DefaultHandler2());
        Document xmlDocument = builder.parse(path.toFile());
        return xmlDocument;
    }

    private boolean processPom(Path path) throws SAXException, XPathExpressionException, IOException, ParserConfigurationException {
        Document xmlDocument = loadPom(path);

        NVV nvv = nvvFrom(xmlDocument);
        if (!matchNVV(nvv)) {
            return false;
        }

        if (path.endsWith("pom.xml")) {
            Path oldPath = buildArtifact.put(nvv, path);
            if (oldPath != null) {
                logger.warning(String.format(ANSI_PURPLE+"%1$s "+ ANSI_YELLOW +"replaces" +ANSI_PURPLE +" %2$s" +ANSI_RESET, path, oldPath));
            }

            buildPaths.put(path, nvv);
        }

        String newHash = null;

        String oldHash = hashes.put(path.toString(), newHash = this.toSHA1(path));
        if(oldHash != null && oldHash !=newHash) {
            //logger.warning(String.format("%1$s already changed", nvv));
            //this.buildDeps(nvv);
        }

        NodeList nodeList = (NodeList) xPath.compile("//dependency").evaluate(xmlDocument, XPathConstants.NODESET);

        Spliterator<Node> splt = Spliterators.spliterator(new NodeListIterator(nodeList), nodeList.getLength(), Spliterator.ORDERED | Spliterator.NONNULL);
        Set<NVV> deps = StreamSupport.stream(splt, true).map(this::processDependency).filter(this::matchNVV).collect(Collectors.toSet());

        NVV parentNvv = nvvParent(nvv, xmlDocument);

        if (!Objects.isNull(parentNvv)) {
            deps.add(parentNvv);
        }

        logger.fine(String.format("tracking %1$s %2$s", nvv.toString(), path));
        projects.put(nvv, deps);
        return true;
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

    private NVV nvvParent(NVV nvv, Document xmlDocument) {
        NVV parentNvv = null;
        try {
            Node parent = (Node) xPath.compile("/project/parent").evaluate(xmlDocument, XPathConstants.NODE);
            parentNvv = nvvFrom(parent);

            if (nvv.vendor.isEmpty()) {
                nvv.vendor = parentNvv.vendor;
            }
            if (nvv.version.isEmpty()) {
                nvv.version = parentNvv.version;
            }
            logger.fine(String.format("with parent %1$s", nvv.toString()));
        } catch (Exception e) {
        }
        return parentNvv;
    }

    private NVV nvvFrom(Document xmlDocument) throws XPathExpressionException {

        Node project = (Node) xPath.compile("/project").evaluate(xmlDocument, XPathConstants.NODE);

        NVV nvv = nvvFrom(project);

        return nvv;
    }

    XPath xPath = XPathFactory.newInstance().newXPath();

    private NVV nvvFrom(Node context) throws XPathExpressionException {
        return new NVV(
                xPath.compile("artifactId").evaluate(context),
                xPath.compile("groupId").evaluate(context),
                xPath.compile("version").evaluate(context)
        );
    }

    private void processChange(NVV nvv) {
        processChange(nvv, buildArtifact.get(nvv));
    }

    private void processChange(NVV nvv, Path path) {
        logger.info(String.format("changed " + ANSI_CYAN + "%1$s" +ANSI_PURPLE+" %2$s" +ANSI_RESET, nvv.toString(), path));

        try {
            hashes.put(path.toString(), this.toSHA1(path));
            writeHashes();
        } catch (IOException ex) {
            Logger.getLogger(Rvn.class.getName()).log(Level.SEVERE, null, ex);
        }

        qBuild(nvv, nvv);
    }

    private void buildDeps(NVV nvv) {
        try {
            this.projects.entrySet().stream()
                    .filter(e -> e.getValue().stream()
                    .filter(nvv2 -> nvv2.equals(nvv)).findAny().isPresent())
                    .forEach(e -> qBuild(nvv, e.getKey()));
        } catch (RuntimeException x) {
            logger.warning(String.format("%1$s %2$s", nvv.toString(), x.getMessage()));
        }
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

    ExecutorService executor = new ScheduledThreadPoolExecutor(5);

    private void qBuild(NVV nvv, NVV next) {
        Path dir = buildArtifact.get(nvv);

        if (!dir.endsWith("pom.xml")) {
            return;
        }

        Edge edge = new Edge(nvv, next);

        if (!q.contains(edge)) {
            q.insert(edge);

            //if (!next.equals(nvv)) {
            buildDeps(next);
            //}
        }
        logger.finest(nvv + "=>" + next + " q->" + q.toString().replace(',', '\n'));
    }

    Graph<NVV> q = new Graph<>();

    private boolean isNVV(String command) {
        return command.matches(".*::.*");
    }

    private void reloadConfiguration() throws Exception {
        keys.forEach(k -> k.cancel());
        this.init();
        this.loadConfiguration();
        this.scan();
    }

    private boolean matchNVVCommand(NVV i, String match) {
        StringBuilder bob = new StringBuilder();
        if (match.length() == 0) {
            match = "::";
        }
        if (match.startsWith("::")) {
            bob.append(".*");
        }
        bob.append(match);
        if (match.endsWith("::")) {
            bob.append(".*");
        }

        return i.toString().matches(bob.toString());
    }

    private void createCommandHandlers() {

        commandHandlers.add(new CommandHandler("?", "?", "Prints the help.", (command) -> {
            if (command.equals("?")) {
                logger.info(String.format("%1$s\t\t %2$s \t\t\t %3$s\n", "Command", "Example", "Description"));
                commandHandlers.stream().forEach(c
                        -> {
                    logger.info(String.format("%1$s\t\t %2$s \t\t\t - %3$s\n", c.verb, c.format, c.description));

                });
            }
            return null;
        }));

        commandHandlers.add(new CommandHandler("!", "!", "Stop the current build. Leave the build queue in place", (command) -> {
            if (command.equals("!")) {
                this.processMap.values().forEach(p -> stopProcess(p));
            }
            return null;
        }));
        commandHandlers.add(new CommandHandler("!!", "!!", "Stop the current build. Drain out the build queue", (command) -> {
            if (command.equals("!!")) {
                this.processMap.values().forEach(p -> stopProcess(p));
                List<NVV> l = new ArrayList<>();
                this.q.oq.drainTo(l);
                if (l.isEmpty()) {
                } else {
                    logger.info("cancelled " + ANSI_CYAN + l.toString() + ANSI_RESET);
                }
            }
            return null;
        }));
        commandHandlers.add(new CommandHandler("@", "@", "Reload the configuration file and rescan filesystem.", (command) -> {
            if (command.equals("@")) {
                try {
                    this.reloadConfiguration();
                } catch (Exception ex) {
                    logger.warning(ex.getMessage());
                }
            }
            return null;
        }));
        commandHandlers.add(new CommandHandler("`", "`::test::", "List know project(s) matching coordinate or path expression.", (command) -> {
            if (command.startsWith("`")) {
                List<NVV> index = this.buildArtifact.keySet().stream().collect(Collectors.toList());
                Collections.sort(index, (NVV o1, NVV o2) -> o1.toString().compareTo(o2.toString()));

                logger.info(index.stream()
                        .filter(i -> matchNVVCommand(i, command.substring(1)) || this.buildArtifact.get(i).toString().matches(command.substring(1)))
                        .map(i -> String.format(ANSI_CYAN + "%1$s " + ANSI_PURPLE + "%2$s" + ANSI_RESET, i, buildArtifact.get(i)))
                        .collect(Collectors.joining("," + System.lineSeparator(), "", ""))
                );
            }
            return null;
        }));
        commandHandlers.add(new CommandHandler("[groupId]::[artifactId]::[version]", "::test:: mygroup::", "Builds the project(s) for the given coordinate(s). Supports regexp. e.g. .*::test::.* or ::test:: ", (command) -> {
            if (isNVV(command)) {
                this.buildArtifact.keySet().stream()
                        .filter(n -> matchNVVCommand(n, command))
                        .forEach(n -> this.processChange(n));
            }
            return null;
        }));
        commandHandlers.add(new CommandHandler("path", "/path/to/pom.xml", "Builds the project(s) for the given coordinate(s). Supports regexp.", (command) -> {
            if (Files.exists(Paths.get(command))) {
                this.hashes.remove(Paths.get(command).toString());
                try {
                    this.processPath(Paths.get(command));
                } catch (Exception ex) {
                    logger.warning(ex.getMessage());
                }
            }
            return null;
        }
        ));
        commandHandlers.add(new CommandHandler("timeout {number}", "timeout 60", "Sets the maximum build timeout to 1 minute.", (command) -> {
            Pattern pattern = Pattern.compile("^timeout\\s([0-9]+)$");
            Matcher matcher = pattern.matcher(command);
            if (matcher.matches()) {
                timeout = Integer.parseInt(matcher.group(1));
                logger.warning(String.format("timeout is %1$d second", timeout));
            }
            return null;
        }));
    }

    private void writeHashes() throws IOException {

        Path config = Paths.get(System.getProperty("user.home") + File.separator + ".m2" + File.separator + "rvn.hashes");
        try {
            FileOutputStream fos = new FileOutputStream(config.toFile());
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(this.hashes);
            fos.flush();
        } catch (IOException x) {
            logger.info(x.getMessage());
        }
    }

    private void readHashes() throws IOException {

        Path config = Paths.get(System.getProperty("user.home") + File.separator + ".m2" + File.separator + "rvn.hashes");
        if (Files.exists(config)) {
            try {
                FileInputStream fis = new FileInputStream(config.toFile());
                ObjectInputStream ois = new ObjectInputStream(fis);
                this.hashes = (Map<String, String>) ois.readObject();
            } catch (IOException x) {
                logger.warning(x.getMessage());
            } catch (ClassNotFoundException x) {
                logger.warning(x.getMessage());
            }
        }
    }

    private void stopProcess(Process p) {
        p.destroy();
    }

    class BuildIt extends Thread {

        public void run() {
            while (this.isAlive()) {
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException ex) {
                    Logger.getLogger(Rvn.class.getName()).log(Level.SEVERE, null, ex);
                }

                if (q.isEmpty()) {
                    continue;
                }

                try (Stream<NVV> path = q.paths()) {
                    path.forEach(nvv -> {
                        try {
                            if (!doBuild(nvv).get()) {
                                throw new RuntimeException(nvv + " failed");
                            }
                        } catch (InterruptedException | ExecutionException ex) {
                            Logger.getLogger(Rvn.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    });
                } catch (RuntimeException x) {
                    logger.info(x.getMessage());
                }
                q.clear();
            }
            logger.info("builder exited - no more builds - restart");
        }

        public CompletableFuture<Boolean> doBuild(NVV nvv) {
            CompletableFuture<Boolean> result = new CompletableFuture<>();

            Path dir = buildArtifact.get(nvv);
            if (dir == null) {
                result.complete(Boolean.FALSE);
                return result;
            }

            if (processMap.containsKey(dir)) {
                logger.info(String.format("already building % 1$s ", nvv));
                result.complete(Boolean.FALSE);
                return result;
                //processMap.get(path).destroyForcibly(); }
            }

            String command = locateCommand(nvv, dir); //nice to have different commands for different paths
            logger.info(String.format("building " + ANSI_CYAN + "%1$s " + ANSI_WHITE + "%2$s"  + ANSI_RESET, nvv, command));

            if (command.isEmpty()) {
                logger.info(String.format("already running " + ANSI_CYAN+ "%1$s "+ ANSI_WHITE+"%2$s" + ANSI_RESET, nvv, command));
                result.complete(Boolean.FALSE);
                return result;
            }

            ProcessBuilder pb = new ProcessBuilder().command(command.split(" "))
                    .inheritIO();

            pb.environment().putAll(System.getenv());

            try {
                Process p = pb.start();
                processMap.put(dir, p);
                if (!p.waitFor(timeout, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                }

                processMap.remove(dir);

                logger.info(String.format(ANSI_CYAN + "%1$s " + ANSI_RESET + "returned " + ANSI_RED + "%2$s" + ANSI_RESET + " with command " + ANSI_WHITE + "%3$s" + ANSI_RESET, nvv, p.exitValue(), command));

                result.complete(p.exitValue() == 0);
            } catch (IOException | InterruptedException ex) {
                Logger.getLogger(Rvn.class.getName()).log(Level.SEVERE, null, ex);
                result.complete(Boolean.FALSE);
            }

            return result;
        }
    }

    private String locateCommand(NVV nvv, Path path) {
        return String.format(commands.entrySet().stream()
                .filter(e -> path.toString().matches(e.getKey()) || nvv.toString().matches(e.getKey())).map(e -> e.getValue()).map(a
                -> a == null ? null : a.get(0)).findFirst().orElse(commands.get("::::").get(0)), path);
    }

    private boolean isPom(Path path) {
        return Arrays.asList(new String[]{".*\\/pom.xml$", ".*.pom$"})
                .stream().filter(s -> path.toAbsolutePath().toString().matches(s)).findFirst().isPresent(); //FIXME: absolutely
    }

    private synchronized NVV findPom(Path path) throws Exception {
        //    return buildPaths.get(path);
        List<Map.Entry<Path, NVV>> base = buildPaths.entrySet().stream()
                .filter(e -> isBasePath(e.getKey(), path)
                ).collect(Collectors.toList());

        logger.fine("base: " + base.toString());

        Optional<Map.Entry<Path, NVV>> nvv = base.stream()
                .reduce((e1, e2) -> {
                    return (e1 != null && e1.getKey().getNameCount() >= e2.getKey().getNameCount()) ? e1 : e2;
                });

        if (nvv.isPresent()) {
            return nvv.get().getValue();
        } else {
            return null;
        }
        //map(e -> e.getKey()).reducefindFirst().orElseThrow(() -> new Exception("not known " + path));
    }

    private Path findPath(String name, String vendor, String version) {
        Path path = buildArtifact.get(new NVV(name, vendor, version));
        return path;
    }

    private boolean isBasePath(Path project, Path changed) {
        Path parent = project.getParent();
        boolean base = false;

        if (changed.endsWith(project)) {
            base = true;
        } else if (changed.startsWith(parent)) {
            base = true;
        }
        logger.fine(ANSI_PURPLE + "changed " + ANSI_CYAN + parent + " " + base + ANSI_RESET);
        return base;
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
        md.update(Long.toString(Files.size(value)).getBytes());
        Files.lines(value).forEach(s -> md.update(s.getBytes()));
        return new String(md.digest());
    }

    private void loadConfiguration() throws IOException, ScriptException, URISyntaxException {
        URL configURL = Rvn.class.getResource("/rvn.json");
        config = null;

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

        this.watch(config.getParent());
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
            logger.info(key + " includes " + matchDirIncludes.toString());
            logger.info(key + " excludes " + matchDirExcludes.toString());
        }

        if (result.hasMember(key = "activeFiles")) {
            ScriptObjectMirror v = (ScriptObjectMirror) result.get(key);
            this.matchFileIncludes.addAll(asArray((ScriptObjectMirror) v.getMember("includes")));
            this.matchFileExcludes.addAll(asArray((ScriptObjectMirror) v.getMember("excludes")));

            logger.info(key + " includes " + matchFileIncludes.toString());
            logger.info(key + " excludes " + matchFileExcludes.toString());
        }

        if (result.hasMember(key = "activeArtifacts")) {
            ScriptObjectMirror v = (ScriptObjectMirror) result.get(key);
            this.matchArtifactIncludes.addAll(asArray((ScriptObjectMirror) v.getMember("includes")));
            this.matchArtifactExcludes.addAll(asArray((ScriptObjectMirror) v.getMember("excludes")));

            logger.info(key + " includes " + matchArtifactIncludes.toString());
            logger.info(key + " excludes " + matchArtifactExcludes.toString());
        }

        if (result.hasMember(key = "buildCommands")) {
            ScriptObjectMirror v = (ScriptObjectMirror) result.get(key);
            v.entrySet().forEach(e -> commands.put(e.getKey(), optionalArray(e.getValue())));
            System.out.println(key + " " + commands.toString());
        }
    }

    private List<String> optionalArray(Object v) {
        if (v instanceof ScriptObjectMirror) {
            ScriptObjectMirror s = (ScriptObjectMirror) v;
            if (s.isArray()) {
                return asArray(s);
            }
        }
        return Arrays.asList(new String[]{v.toString()});

    }

    private List<String> asArray(ScriptObjectMirror v) {
        List<String> result = new ArrayList<>();
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

    class CommandHandler implements Function<String, Void> {

        private final String verb;
        private final String format;
        private final String description;
        private final Function<String, Void> fun;

        public CommandHandler(String verb, String format, String description, Function<String, Void> fun) {
            this.verb = verb;
            this.format = format;
            this.description = description;
            this.fun = fun;
        }

        @Override
        public Void apply(String t) {
            return this.fun.apply(t);
        }

    }
    /*
    {System.out.println(System.getProperties().toString());
    }
     */
    public static final Boolean IS_ANSI = System.console() != null && System.getenv().get("TERM") != null && System.getenv().get("TERM").contains("color");
    public static final String ANSI_RESET = IS_ANSI ? "\u001B[0m" : "";
    public static final String ANSI_BLACK = IS_ANSI ? "\u001B[30m" : "";
    public static final String ANSI_RED = IS_ANSI ? "\u001B[31m" : "";
    public static final String ANSI_GREEN = IS_ANSI ? "\u001B[32m" : "";
    public static final String ANSI_YELLOW = IS_ANSI ? "\u001B[33m" : "";
    public static final String ANSI_BLUE = IS_ANSI ? "\u001B[34m" : "";
    public static final String ANSI_PURPLE = IS_ANSI ? "\u001B[35m" : "";
    public static final String ANSI_CYAN = IS_ANSI ? "\u001B[36m" : "";
    public static final String ANSI_WHITE = IS_ANSI ? "\u001B[37m" : "";

    /**
     * @SuppressWarnings("unchecked") static <T> WatchEvent<T>
     * cast(WatchEvent<?>
     * event) { return (WatchEvent<Path>) event; }
     *
     */
}
