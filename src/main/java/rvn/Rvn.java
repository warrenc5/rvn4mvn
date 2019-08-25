package rvn;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.io.Writer;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

    // TODO:
    /**
     * *
     * autoResume -rf module projects
     */
    private static Set<String> locations;
    private Set<WatchKey> keys;
    private Map<WatchKey, Path> keyPath;
    private Map<NVV, Path> buildArtifact;
    private List<NVV> buildIndex;
    private List<NVV> index;
    private List<NVV> toBuild;
    private Map<Path, NVV> buildPaths;
    private Map<NVV, FileTime> lastBuild;
    private Map<NVV, FileTime> lastUpdate;
    private Map<NVV, Set<NVV>> projects;
    private Map<NVV, NVV> parent;
    private Map<NVV, File> failMap;
    private Map<Path, Process> processMap;

    private Map<String, List<String>> commands;

    private Set<NVV> agProjects;
    private List<String> matchFileIncludes;
    private List<String> matchFileExcludes;
    private List<String> matchDirIncludes;
    private List<String> matchDirExcludes;
    private List<String> matchArtifactIncludes;
    private List<String> matchArtifactExcludes;
    private List<String> configFileNames;
    private List<CommandHandler> commandHandlers;

    private Logger logger = Logger.getLogger(Rvn.class.getName());

    private MessageDigest md = null;

    private Map<String, String> hashes;
    private final WatchService watcher;
    private Path config;
    private String mvnCmd;
    boolean ee = false;
    boolean showOutput = true;

    private File tf = null;
    private Boolean interrupt;
    private String mvnOpts;
    private String javaHome;
    private String mvnArgs;
    private Duration batchWait;
    private Map<NVV, String> mvnCmdMap;
    private Map<NVV, Duration> batchWaitMap;
    private Map<NVV, Duration> timeoutMap;
    private Map<NVV, Boolean> interruptMap;
    private Map<NVV, String> mvnOptsMap;
    private Map<NVV, String> javaHomeMap;
    private Map<NVV, String> mvnArgsMap;

    private Map<NVV, ScheduledFuture> futureMap = new HashMap<>();

    public static void main(String[] args) throws Exception {
        Logger.getAnonymousLogger().warning(ANSI_BOLD + ANSI_GREEN + "Raven 4 Maven" + ANSI_RESET);
        Rvn rvn = new Rvn();
        rvn.locations.addAll(Arrays.asList(args));
        rvn.start();
        rvn.processStdIn();
        System.out.println(String.format("exited"));
    }

    private Duration timeout = Duration.ofSeconds(60);

    private NVV lastNvv;
    private Instant then = null;
    private Path lastChangeFile;

    public Rvn() throws Exception {
        this.setName("Rvn_Main");

        this.setDefaultUncaughtExceptionHandler((e, t) -> {
            logger.log(Level.WARNING, e.getName() + " " + t.getMessage(), t);
        });

        System.out.print(ANSI_RESET + ANSI_RESET + ANSI_WHITE);
        watcher = FileSystems.getDefault().newWatchService();
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            logger.warning(e.getMessage());
        }

        init();

        BuildIt buildIt = new BuildIt();
        buildIt.start();
    }

    public void init() throws Exception {
        locations = new ConcurrentSkipListSet<>();
        keys = new HashSet<>(locations.size());
        keyPath = new HashMap<>();
        projects = new HashMap<>();
        parent = new HashMap<>();
        buildArtifact = new LinkedHashMap<>();
        buildIndex = new ArrayList<>();
        agProjects = new HashSet<>();
        buildPaths = new LinkedHashMap<>();
        toBuild = new ArrayList<>();
        lastBuild = new HashMap<>();
        lastUpdate = new HashMap<>();
        processMap = new LinkedHashMap<>();
        failMap = new LinkedHashMap<>();
        commands = new LinkedHashMap<>();
        hashes = new HashMap<>();
        configFileNames = new ArrayList<>(Arrays.asList(new String[]{".rvn", ".rvn.json"}));
        matchFileIncludes = new ArrayList<>(configFileNames);
        matchFileExcludes = new ArrayList<>();
        matchDirIncludes = new ArrayList<>();
        matchDirExcludes = new ArrayList<>();
        matchArtifactIncludes = new ArrayList<>();
        matchArtifactExcludes = new ArrayList<>();
        then = Instant.now();
        batchWait = Duration.ofSeconds(0);
        interrupt = Boolean.FALSE;
        mvnCmdMap = new HashMap<>();
        batchWaitMap = new HashMap<>();
        timeoutMap = new HashMap<>();
        interruptMap = new HashMap<>();
        mvnOptsMap = new HashMap<>();
        javaHomeMap = new HashMap<>();
        mvnArgsMap = new HashMap<>();
        mvnOpts = "";
        javaHome = "";
        mvnArgs = "";

        this.readHashes();
        loadDefaultConfiguration();

        commandHandlers = new ArrayList<>();
        createCommandHandlers();
        updateIndex();
    }

    private void processStdIn() {
        Scanner scanner = new Scanner(System.in);
        scanner.useDelimiter(System.getProperty("line.separator"));
        Spliterator<String> splt = Spliterators.spliterator(scanner, Long.MAX_VALUE,
                Spliterator.ORDERED | Spliterator.NONNULL);

        while (this.isAlive()) {
            Thread.yield();
            StreamSupport.stream(splt, false).onClose(scanner::close).forEach(this::processCommand);
        }

        logger.info(String.format("commandless"));
    }

    private void processCommand(final String command2) {
        final String command = command2.trim();
        logger.info(String.format(
                ANSI_YELLOW + "%1$s" + ANSI_RESET + " last build " + ANSI_YELLOW + "%2$s" + ANSI_RESET + " ago.",
                LocalTime.now(), Duration.between(then, Instant.now()).toString()));

        Optional<Boolean> handled = commandHandlers.stream().map(c -> c.apply(command))
                .filter(b -> Boolean.TRUE.equals(b)).findAny();
        if (handled.isPresent()) {
            if (handled.get().booleanValue()) {
                logger.info("" + (char) (new Random().nextInt(5) + 1));
            }
        }

        /*
		 * //jdk.nashorn.api.scripting.ScriptObjectMirror result =
		 * (jdk.nashorn.api.scripting.ScriptObjectMirror) getEngine().eval("config=" +
		 * command); //buildConfiguration(result); } catch (ScriptException ex) {
		 * logger.warning(String.format("%1$s", ex.getMessage())); } catch (Exception
		 * ex) { logger.severe("command failed" + ex.getMessage()); }
         */
    }

    public void registerPath(String uri) {
        Path dir = Paths.get(uri);
        logger.info(String.format(ANSI_WHITE + "watching %1$s" + ANSI_RESET, dir));
        registerPath(dir);
    }

    public void registerPath(Path path) {
        try {
            if (Files.isDirectory(path)) {
                try (Stream<Path> stream = Files.list(path)) {
                    stream.filter(child -> matchSafe(child)).forEach(this::registerPath);
                }
            } else if (path.toFile().toString().endsWith(".pom")) {
                Optional<FileTime> lastest = watchRecursively(path.getParent().getParent()); // watch all versions
                Optional<NVV> oNvv = processPom(path);
                if (oNvv.isPresent() && lastest.isPresent()) {
                    this.lastBuild.put(oNvv.get(), lastest.get());
                }
            } else if (path.endsWith("pom.xml")) {
                Optional<NVV> oNvv = processPom(path);
                if (oNvv.isPresent()) {
                    Path parent = path.getParent();
                    Optional<FileTime> lastest = watchRecursively(parent);
                    this.lastUpdate.put(oNvv.get(), lastest.get());
                } else {
                    // logger.warning(String.format(ANSI_WHITE + "failed %1$s" + ANSI_RESET, path));
                }
            } else if (this.configFileNames.contains(path.getFileName().toString())) {
                this.loadConfiguration(path);
            } else {

            }
        } catch (IOException | SAXException | XPathExpressionException | ParserConfigurationException ex) {
            logger.info(
                    String.format("register failed %1$s %2$s %3$s", path, ex.getClass().getName(), ex.getMessage()));
        }
    }

    public Optional<FileTime> watchRecursively(Path dir) {
        watch(dir);
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(child -> Files.isDirectory(child) && matchDirectories(child)).forEach(this::watchRecursively);
        } catch (IOException ex) {
            logger.info(String.format("recurse failed %1$s %2$s", ex.getClass().getName(), ex.getMessage()));
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.map(child -> {
                try {
                    LinkOption option = LinkOption.NOFOLLOW_LINKS;
                    return Files.getLastModifiedTime(child, option);
                } catch (IOException e) {
                    return null;
                }
            }).max(FileTime::compareTo);
        } catch (IOException ex) {
            logger.info(String.format("recurse failed %1$s %2$s", ex.getClass().getName(), ex.getMessage()));
        }

        return null;
    }

    public void watch(Path dir) {
        try {
            WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);

            keys.add(key);
            keyPath.put(key, dir);
            logger.fine(String.format("watching %1$s %2$d", dir, key.hashCode()));
        } catch (IOException x) {
            System.err.println(x);
        }
    }

    public void scan() {
        locations.stream().forEach(this::registerPath);
        Duration duration = Duration.between(then, Instant.now());

        logger.fine("buildSet :" + buildPaths.toString().replace(',', '\n'));
        ArrayList watchSet;
        watchSet = new ArrayList(this.keyPath.values());
        Collections.sort(watchSet);
        logger.fine("watchSet :" + watchSet.toString().replace(',', '\n'));

        this.buildIndex();
        this.calculateToBuild();

        logger.info(String.format(ANSI_WHITE + "watching %1$s builds, %2$s projects, %3$s keys - all in %4$s" + ANSI_RESET,
                buildPaths.size(), projects.size(), keys.size(), duration.toString()));
    }

    @Override
    public void run() {
        scan();

        /**
         * logger.info(toBuild.toString()); toBuild.forEach(nvv -> {
         * this.buildDeps(nvv); });
         *
         */
        while (this.isAlive()) {
            Thread.yield();
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
                                logger.log(Level.SEVERE, ex.getMessage(), ex);
                            }
                            continue;
                        }

                        logger.fine(String.format("kind %1$s %2$s %3$d", ev.kind(), child, key.hashCode()));

                        if (kind == ENTRY_DELETE) {
                            Optional<WatchKey> cancelKey = keyPath.entrySet().stream()
                                    .filter(e -> child.equals(e.getValue())).map(e -> e.getKey()).findFirst();
                            if (cancelKey.isPresent()) {
                                cancelKey.get().cancel();
                            }
                            // TODO remove from buildArtifacts
                            updateIndex();
                        } else if (kind == ENTRY_CREATE) {
                            this.registerPath(child);
                            updateIndex();
                        } else if (kind == ENTRY_MODIFY) {
                            processPath(child);
                        }
                    } catch (Exception ex) {
                        logger.log(Level.SEVERE, ex.getMessage(), ex);
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
        return Optional.ofNullable(filename).filter(f -> f.contains("."))
                .map(f -> f.substring(filename.lastIndexOf(".") + 1));
    }

    private void processPathSafe(String uri) {
        try {
            processPath(uri);

        } catch (XPathExpressionException | SAXException | IOException | ParserConfigurationException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    private void processPath(String uri)
            throws XPathExpressionException, SAXException, IOException, ParserConfigurationException, Exception {
        System.err.println("in ->" + uri);
        Path path = Paths.get(uri);
        processPath(path);
    }

    private void processPath(Path path)
            throws XPathExpressionException, SAXException, IOException, ParserConfigurationException, Exception {
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

            // logger.info(String.format("nvv: %1$s %2$s", path, nvv));
            if (matchNVV(nvv)) {
                this.buildDeps(nvv);
            }
            return;
        } else if (this.configFileNames.contains(path.getFileName().toString())) {
            NVV nvv = findPom(path);

            if (nvv == null) {
                logger.fine(String.format("no nvv: %1$s", path));
                return;
            } else {
                logger.info(String.format("config nvv: %1$s %2$s", path, nvv));
            }
            this.loadConfiguration(path);
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
                lastNvv = nvv;
                this.lastChangeFile = path;
                processChange(nvv, path, false);
            } else {
                logger.info(String.format("change excluded by config: %1$s %2$s", path, nvv));
            }

        } catch (Exception x) {
            logger.warning(String.format("process: %1$s - %2$s", path, x.getMessage()));
            x.printStackTrace();
        }
    }

    private Document loadPom(Path path)
            throws SAXException, XPathExpressionException, IOException, ParserConfigurationException {
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = builderFactory.newDocumentBuilder();
        builder.setErrorHandler(new DefaultHandler2());
        Document xmlDocument = builder.parse(path.toFile());
        return xmlDocument;
    }

    private Optional<NVV> processPom(Path path)
            throws SAXException, XPathExpressionException, IOException, ParserConfigurationException {
        Document xmlDocument = loadPom(path);

        NVV nvv = nvvFrom(path);
        if (!matchNVV(nvv)) {
            return Optional.empty();
        }

        if (path.endsWith("pom.xml")) {
            Long workingTime = Files.getLastModifiedTime(path).to(TimeUnit.SECONDS);

            Path oldPath = buildArtifact.get(nvv);
            if (oldPath != null && !Files.isSameFile(path, oldPath)) {
                Long otherTime = Files.getLastModifiedTime(oldPath).to(TimeUnit.SECONDS);
                if (workingTime > otherTime) {
                    logger.warning(
                            String.format(
                                    ANSI_PURPLE + "%1$s " + ANSI_YELLOW + "newer than" + ANSI_PURPLE + " %2$s"
                                    + ANSI_CYAN + " %3$s" + ANSI_RED + ", replacing" + ANSI_RESET,
                                    path, oldPath, nvv.toString()));
                } else {
                    logger.warning(
                            String.format(
                                    ANSI_PURPLE + "%1$s " + ANSI_YELLOW + "older than" + ANSI_PURPLE + " %2$s"
                                    + ANSI_CYAN + " %3$s" + ANSI_RESET + ", ignoring",
                                    path, oldPath, nvv.toString()));
                    return Optional.empty();
                }
            }

            this.buildArtifact.put(nvv, path);

            buildPaths.put(path, nvv);
        }

        String newHash = null;

        String oldHash = hashes.put(path.toString(), newHash = this.toSHA1(path));
        if (oldHash != null && oldHash != newHash) {
            // logger.warning(String.format("%1$s already changed", nvv));
            // this.buildDeps(nvv);
        }

        NodeList nodeList = (NodeList) xPath.compile("//dependency").evaluate(xmlDocument, XPathConstants.NODESET);

        Spliterator<Node> splt = Spliterators.spliterator(new NodeListIterator(nodeList), nodeList.getLength(),
                Spliterator.ORDERED | Spliterator.NONNULL);
        Set<NVV> deps = StreamSupport.stream(splt, true).map(this::processDependency).filter(this::matchNVV)
                .collect(Collectors.toSet());

        NVV parentNvv = nvvParent(nvv, xmlDocument);

        if (!Objects.isNull(parentNvv)) {
            parent.put(nvv, parentNvv);
            deps.add(parentNvv);
        }

        NodeList modules = (NodeList) xPath.compile("//modules/module").evaluate(xmlDocument, XPathConstants.NODESET);
        if (modules.getLength() > 0) {
            logger.fine(String.format("aggregator project %1$s", nvv.toString()));
            agProjects.add(nvv);
        }

        logger.fine(String.format("tracking %1$s %2$s", nvv.toString(), path));
        projects.put(nvv, deps);
        return Optional.of(nvv);
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
        } catch (Exception e) {
        }
        logger.fine(String.format("%1$s with parent %2$s", nvv.toString(), parentNvv));
        return parentNvv;
    }

    private NVV nvvFrom(Path path)
            throws XPathExpressionException, SAXException, IOException, ParserConfigurationException {
        Document xmlDocument = loadPom(path);
        return nvvFrom(xmlDocument);
    }

    private NVV nvvFrom(Document xmlDocument) throws XPathExpressionException {

        Node project = (Node) xPath.compile("/project").evaluate(xmlDocument, XPathConstants.NODE);

        NVV nvv = nvvFrom(project);

        return nvv;
    }

    XPath xPath = XPathFactory.newInstance().newXPath();

    private NVV nvvFrom(Node context) throws XPathExpressionException {
        return new NVV(xPath.compile("artifactId").evaluate(context), xPath.compile("groupId").evaluate(context),
                xPath.compile("version").evaluate(context));
    }

    private void processChangeImmediatley(NVV nvv) {
        processChange(nvv, buildArtifact.get(nvv), true);
    }

    private NVV processChange(NVV nvv) {
        processChange(nvv, buildArtifact.get(nvv), false);
        return nvv;
    }

    private void processChange(NVV nvv, Path path, boolean immediate) {
        logger.info(String.format(
                "changed " + ANSI_CYAN + "%1$s" + ANSI_PURPLE + " %2$s" + ANSI_YELLOW + " %3$s" + ANSI_RESET,
                nvv.toString(), path, LocalTime.now().toString()));

        try {
            hashes.put(path.toString(), this.toSHA1(path));
            writeHashes();
        } catch (IOException ex) {
            Logger.getLogger(Rvn.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
        }

        Duration batchWait = immediate ? Duration.ZERO : batchWaitMap.getOrDefault(nvv, this.batchWait);

        synchronized (futureMap) {
            interruptProcess(nvv);
            futureMap.computeIfPresent(nvv, (nvv1, future) -> {
                future.cancel(interrupt);
                return null;
            });

            futureMap.computeIfAbsent(nvv, (nvv1) -> {
                logger.fine(
                        String.format("submitting %1$s with batchWait %2$s ms", nvv.toString(), batchWait.toMillis()));
                ScheduledFuture<?> future = executor.schedule(() -> {
                    logger.fine(String.format("executing %1$s with batchWait %2$s ms", nvv.toString(),
                            batchWait.toMillis()));
                    qBuild(nvv, nvv);
                    futureMap.remove(nvv);
                    return null;
                }, batchWait.toMillis(), TimeUnit.MILLISECONDS);
                return future;
            });
        }
    }

    private boolean interruptProcess(NVV nvv) {
        Boolean interrupt = interruptMap.getOrDefault(nvv, this.interrupt);
        if (interrupt) {

            Path dir = buildArtifact.get(nvv);

            Process value = this.processMap.computeIfPresent(dir, (key, p) -> {
                logger.info(String.format("destroying process %1$s", nvv));
                synchronized (processMap) {
                    stopProcess(p);
                }
                return null;
            });

            if (value != null) {
                return true;
            }
        }
        return false;
    }

    private void buildDeps(NVV nvv) {
        try {
            this.projects.entrySet().stream().filter(e -> !e.getKey().equals(parent.get(nvv)))
                    .filter(e -> e.getValue().stream().filter(nvv2 -> !nvv2.equals(parent.get(nvv)))
                    .filter(nvv2 -> nvv2.equalsVersion(nvv)).findAny().isPresent())
                    .forEach(e -> qBuild(nvv, e.getKey()));
        } catch (RuntimeException x) {
            logger.warning(String.format("%1$s %2$s", nvv.toString(), x.getMessage()));
        }
    }

    private boolean matchFiles(Path path) throws IOException {
        return Files.isSameFile(path, this.config)
                || this.configFileNames.stream().filter(s -> path.toAbsolutePath().toString().endsWith(s)).findFirst().isPresent()
                || matchFileIncludes.stream().filter(s -> this.match(path, s)).findFirst().isPresent() // FIXME: absolutely
                && !matchFileExcludes.stream().filter(s -> this.match(path, s)).findFirst().isPresent(); // FIXME: absolutely
    }

    private boolean matchDirectories(Path path) {
        return matchDirIncludes.stream().filter(s -> this.match(path, s)).findFirst().isPresent() // FIXME: absolutely
                && !matchDirExcludes.stream().filter(s -> this.match(path, s)).findFirst().isPresent(); // FIXME: absolutely
    }

    private boolean matchNVV(NVV nvv) {
        return matchArtifactIncludes.stream().filter(s -> this.match(nvv, s)).findFirst().isPresent() // FIXME:
                // absolutely
                && !matchArtifactExcludes.stream().filter(s -> this.match(nvv, s)).findFirst().isPresent(); // FIXME:
        // absolutely
    }

    private boolean match(Path path, String s) {
        boolean matches = path.toAbsolutePath().toString().matches(s);
        if (matches) {
            logger.fine("matches path " + s);
        }
        return matches;
    }

    private boolean match(NVV nvv, String s) {
        boolean matches = nvv.toString().matches(s);
        if (matches) {
            logger.fine("matches artifact " + s);
        }
        return matches;
    }

    private void calculateToBuild() {

        this.lastBuild.entrySet().forEach(e -> {
            NVV nvv = e.getKey();
            if (e.getValue().compareTo(this.lastUpdate.getOrDefault(nvv, FileTime.from(Instant.MIN))) < 0) {
                logger.info(String.format("consider building " + ANSI_CYAN + " %s " + ANSI_RESET, nvv.toString()));
                if (!toBuild.contains(nvv)) {
                    toBuild.add(nvv);
                }
            }
        });

        Collections.sort(this.toBuild, new Comparator<NVV>() {
            @Override
            public int compare(NVV o1, NVV o2) {
                return Long.compare(Rvn.this.buildIndex.indexOf(o1), Rvn.this.buildIndex.indexOf(o2));
            }
        });

    }

    ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);

    private void qBuild(NVV nvv, NVV next) {

        Path dir = buildArtifact.get(nvv);

        if (!dir.toString().endsWith("pom.xml")) {
            return;
        } else {
            try {
                processPom(dir);
            } catch (SAXException | XPathExpressionException | IOException | ParserConfigurationException ex) {
                logger.warning(ex.getMessage());
            }
        }

        Edge edge = new Edge(nvv, next);

        if (!q.contains(edge)) {
            q.insert(edge);

            // if (!next.equals(nvv)) {
            buildDeps(next);
            // }
        }
        logger.finest(nvv + "=>" + next + " q->" + q.toString().replace(',', '\n'));
    }

    Graph<NVV> q = new Graph<>();

    private boolean isNVV(String command) {
        return command.matches(".*:.*");
    }

    private void reloadConfiguration() throws Exception {
        keys.forEach(k -> k.cancel());
        this.init();
        this.loadDefaultConfiguration();
        this.scan();
    }

    private String expandNVVRegex(String match) {
        StringBuilder bob = new StringBuilder();
        if (match.length() == 0) {
            match = ":";
        }
        if (match.startsWith(":")) {
            bob.insert(0, ".*");
        }
        bob.append(match);
        if (match.endsWith(":")) {
            bob.append(".*");
        }
        // logger.finest("matching " + match + " "+ bob.toString());
        return bob.toString();
    }

    private boolean matchNVVCommand(String project, String match) {
        return project.matches(this.expandNVVRegex(match));
    }

    private boolean matchNVVCommand(NVV project, String match) {
        return project.toString().matches(this.expandNVVRegex(match));
    }

    private String prettyDuration(Duration d) {
        if (d.toHours() > 24) {
            return d.toDays() + " days";
        }
        return d.toString();
    }

    private void createCommandHandlers() {

        commandHandlers.add(new CommandHandler("?", "?", "Prints the help.", (command) -> {
            if (command.equals("?")) {
                logger.info(String.format("%1$s\t\t %2$s \t\t\t %3$s\n", "Command", "Example", "Description"));
                commandHandlers.stream().forEach(c -> {
                    logger.info(String.format("%1$s\t\t %2$s \t\t\t - %3$s\n", c.verb, c.format, c.description));

                });
                return TRUE;
            }
            return FALSE;
        }));

        commandHandlers.add(
                new CommandHandler("!", "!", "Stop the current build. Leave the build queue in place", (command) -> {
                    if (command.equals("!")) {
                        this.processMap.values().forEach(p -> stopProcess(p));
                        return TRUE;
                    }
                    return FALSE;
                }));

        commandHandlers.add(new CommandHandler("-", "-", "Hide the output.", (command) -> {
            if (command.equals("-")) {
                Rvn.this.showOutput = false;
                logger.info((Rvn.this.showOutput) ? "showing output" : "hiding output");
                return TRUE;
            }
            return FALSE;
        }));

        commandHandlers.add(new CommandHandler("+", "+", "Show the output.", (command) -> {
            if (command.equals("+")) {
                Rvn.this.showOutput = true;
                logger.info((Rvn.this.showOutput) ? "showing output" : "hiding output");
                return TRUE;
            }
            return FALSE;
        }));

        commandHandlers.add(new CommandHandler("|", "|", "Show the last failed output.", (command) -> {
            if (command.equals("|")) {
                try {
                    writeFileToStdout(Rvn.this.tf);
                } catch (IOException ex) {
                    logger.info(ex.getMessage());
                }
                return TRUE;
            }
            return FALSE;
        }));

        commandHandlers.add(new CommandHandler(".", ".", "Repeat last change that triggered a build.", (command) -> {
            if (command.equals(".")) {
                if (lastNvv != null) {
                    Path path = this.buildArtifact.get(lastNvv);
                    String remove = hashes.remove(path.toString());
                    processChange(lastNvv);
                }
                return TRUE;
            }
            return FALSE;
        }));

        commandHandlers
                .add(new CommandHandler("!!", "!!", "Stop the current build. Drain out the build queue.", (command) -> {
                    if (command.equals("!!")) {
                        this.processMap.values().forEach(p -> stopProcess(p));
                        List<NVV> l = new ArrayList<>();
                        this.q.oq.drainTo(l);
                        if (l.isEmpty()) {
                        } else {
                            logger.info("cancelled " + ANSI_CYAN + l.toString() + ANSI_RESET);
                        }
                        return TRUE;
                    }
                    return FALSE;
                }));

        commandHandlers.add(new CommandHandler(">", ">", "Show the fail map.", (command) -> {
            if (command.equals(">")) {
                index.stream().filter(nvv -> failMap.containsKey(nvv)).filter(nvv -> failMap.get(nvv) != null)
                        .forEach(nvv -> {
                            logger.info(String.format(
                                    ANSI_GREEN + "%1$s " + ANSI_CYAN + "%2$s " + ANSI_PURPLE + "%3$s" + ANSI_RESET,
                                    buildIndex.indexOf(nvv), nvv, failMap.get(nvv)));
                        });
                return TRUE;
            }
            return FALSE;
        }));

        commandHandlers.add(new CommandHandler(">[0-9]", ">1", "Show the fail map entry.", (command) -> {
            if (command.matches(">[0-9]+")) {
                Integer i = Integer.valueOf(command.substring(1));
                NVV nvv = buildIndex.get(i);
                File fail = failMap.get(nvv);
                try {
                    writeFileToStdout(fail);
                } catch (IOException ex) {
                    logger.warning(ex.getMessage());
                }
                return TRUE;
            }
            return FALSE;
        }));
        commandHandlers
                .add(new CommandHandler("@", "@", "Reload the configuration file and rescan filesystem.", (command) -> {
                    if (command.equals("@")) {
                        try {
                            this.reloadConfiguration();
                        } catch (Exception ex) {
                            logger.warning(ex.getMessage());
                        }
                        return TRUE;
                    }
                    return FALSE;
                }));

        commandHandlers.add(new CommandHandler("\\", "\\", "List yet to build list", (command) -> {
            if (command.startsWith("\\")) {
                logger.info(this.toBuild.stream()
                        .map(i -> String.format(
                        ANSI_GREEN + "%1$d " + ANSI_CYAN + "%2$s " + ANSI_PURPLE + "%3$s" + ANSI_RESET
                        + ANSI_WHITE + " %4$s",
                        this.buildIndex.indexOf(i), i.toString(), this.buildArtifact.get(i),
                        prettyDuration(Duration.between(
                                this.lastBuild.getOrDefault(i, FileTime.from(Instant.now())).toInstant(),
                                Instant.now()))))
                        .collect(Collectors.joining("," + System.lineSeparator(), "", "")));
                return TRUE;
            }
            return FALSE;
        }));

        commandHandlers.add(new CommandHandler("\\\\", "\\\\", "Build all yet to build list", (command) -> {
            if (command.startsWith("\\\\")) {
                this.toBuild.stream().forEach(nvv -> this.buildDeps(nvv));
                return TRUE;
            }
            return FALSE;
        }));

        commandHandlers.add(new CommandHandler("`", "`[:test:|#]",
                "List known project(s) matching coordinate or path expression.", (command) -> {
                    if (command.startsWith("`")) {
                        updateIndex();
                        logger.info(index.stream()
                                .filter(i -> matchNVVCommand(i, command.substring(1))
                                || this.buildArtifact.get(i).toString().matches(command.substring(1)))
                                .map(i -> String.format(
                                ANSI_GREEN + "%1$d " + ANSI_CYAN + "%2$s " + ANSI_PURPLE + "%3$s" + ANSI_RESET,
                                buildIndex.indexOf(i), i, buildArtifact.get(i)))
                                .collect(Collectors.joining("," + System.lineSeparator(), "", "")));
                        return TRUE;
                    }
                    return FALSE;
                }));

        commandHandlers.add(new CommandHandler("=", "=:test:", "List build commands for project", (command) -> {
            if (command.startsWith("=")) {

                logger.info(this.commands.keySet().stream()
                        .filter(i -> matchNVVCommand(i, command.length() == 1 ? ".*" : command.substring(1)))
                        .map(i -> String.format(ANSI_CYAN + "%1$s " + ANSI_RESET + "%2$s" + ANSI_RESET, i,
                        this.commands.get(i).stream()
                                .map(c -> String.format(ANSI_WHITE + "    %1$s" + ANSI_RESET, c))
                                .collect(Collectors.joining("," + System.lineSeparator(),
                                        System.lineSeparator(), ""))))
                        .collect(Collectors.joining("," + System.lineSeparator(), "", System.lineSeparator())));
                return TRUE;
            }
            return FALSE;
        }));
        commandHandlers.add(new CommandHandler("[groupId]:[artifactId]:[version]", ":test: mygroup:",
                "Builds the project(s) for the given coordinate(s). Supports regexp. e.g. .*:test:.* or :test: ",
                (command) -> {
                    if (isNVV(command)) {
                        this.buildArtifact.keySet().stream().filter(n -> matchNVVCommand(n, command)).forEach(n -> {
                            lastNvv = this.processChange(n);
                        });
                        return TRUE;
                    }
                    return FALSE;
                }));
        commandHandlers.add(
                new CommandHandler("0-100", "100", "Builds the project(s) for the given project number.", (command) -> {

                    try {
                        for (String c : command.split(" ")) {
                            int i = Integer.parseInt(c);

                            if (buildIndex.size() > i) {
                                this.processChangeImmediatley(buildIndex.get(i));
                            }
                        }
                        return TRUE;
                    } catch (NumberFormatException n) {
                    }
                    return FALSE;
                }));
        commandHandlers.add(new CommandHandler("path", "/path/to/pom.xml",
                "Builds the project(s) for the given coordinate(s). Supports regexp.", (command) -> {
                    try {
                        if (Files.exists(Paths.get(command))) {

                            this.hashes.remove(Paths.get(command).toString());
                            try {
                                this.processPath(Paths.get(command));
                            } catch (Exception ex) {
                                logger.warning(ex.getMessage());
                            }
                        }
                        return TRUE;
                    } catch (Exception x) {
                    }
                    return FALSE;
                }));
        commandHandlers.add(new CommandHandler("path", "/tmp/to/fail.out", "Dump the file to stdout.", (command) -> {
            if (command.endsWith(".out")) {
                if (Files.exists(Paths.get(command))) {
                    try {
                        Rvn.this.writeFileToStdout(Paths.get(command).toFile());
                    } catch (Exception ex) {
                        logger.warning(ex.getMessage());

                    }
                }
                return TRUE;
            }
            return FALSE;
        }));
        commandHandlers.add(new CommandHandler(">>", ">>", "Dump the first entry in the fail map.", (command) -> {
            if (command.equals(">>")) {
                failMap.entrySet().stream().findFirst().ifPresent(e -> {
                    if (e.getValue().exists()) {
                        try {
                            Rvn.this.writeFileToStdout(e.getValue());
                        } catch (Exception ex) {
                            logger.warning(ex.getMessage());
                        }
                    }
                });
                return TRUE;
            }
            return FALSE;
        }));

        commandHandlers.add(new CommandHandler("timeout {number}", "timeout 60",
                "Sets the maximum build timeout to 1 minute.", (command) -> {
                    Pattern pattern = Pattern.compile("^timeout\\s([0-9]+)$");
                    Matcher matcher = pattern.matcher(command);
                    if (matcher.matches()) {
                        timeout = Duration.ofSeconds(Integer.parseInt(matcher.group(1)));
                        logger.warning(String.format("timeout is %1$s second", timeout.toString()));
                        return TRUE;
                    }
                    return FALSE;
                }));

        commandHandlers.add(new CommandHandler("/", "/", "Rebuild all projects in fail map.", (command) -> {
            if (command.equals("/")) {
                failMap.entrySet().stream().filter(e -> e.getValue() != null).forEach(e -> {
                    Rvn.this.buildDeps(e.getKey());
                });
                return TRUE;
            }
            return FALSE;
        }));

        commandHandlers.add(new CommandHandler("q", "", "Proceed with all builds waiting.", (command) -> {
            if (command.equalsIgnoreCase("q")) {
                logger.info("blitzkreik");

                processMap.forEach((nvv, process) -> {
                    stopProcess(process);
                });

                executor.schedule(() -> {
                    System.exit(0);
                }, 1, TimeUnit.SECONDS);
                return TRUE;
            }
            return FALSE;
        }));

        commandHandlers.add(new CommandHandler("[enter]", "", "Proceed with all builds waiting.", (command) -> {
            if (command.trim().length() == 0) {
                logger.info("resubmitting all scheduled builds");

                futureMap.forEach((nvv, future) -> {
                    this.executor.submit(() -> {
                        future.cancel(true);

                        qBuild(nvv, nvv);
                        futureMap.remove(nvv);
                        return null;
                    });
                });
                return TRUE;
            }
            return FALSE;
        }));
    }

    private void writeHashes() throws IOException {

        Path config = Paths
                .get(System.getProperty("user.home") + File.separator + ".m2" + File.separator + "rvn.hashes");
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

        Path config = Paths
                .get(System.getProperty("user.home") + File.separator + ".m2" + File.separator + "rvn.hashes");
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
        //FIXME: java9  p.descendants().forEach(ph -> ph.destroyForcibly());
        p.destroyForcibly();
    }

    private void buildIndex() {
        List<NVV> index = this.buildArtifact.keySet().stream().collect(Collectors.toList());
        Collections.sort(index, (NVV o1, NVV o2) -> o1.toString().compareTo(o2.toString()));
        index.stream().filter(nvv -> !buildIndex.contains(nvv)).forEach(nvv -> buildIndex.add(nvv));
    }

    private void updateIndex() {
        index = this.buildArtifact.keySet().stream().collect(Collectors.toList());
        Collections.sort(index, (NVV o1, NVV o2) -> o1.toString().compareTo(o2.toString()));
    }

    private void loadConfiguration(Path path) {
        try {
            NVV nvv = null;
            if (this.configFileNames.contains(path.getFileName().toString())) {
                Path project = path.getParent().resolve("pom.xml");
                if (project.toFile().exists()) {
                    nvv = nvvFrom(project);
                    logger.info("module configuration found for " + nvv);
                }
            }
            URL configURL = path.toUri().toURL();
            this.loadConfiguration(configURL, nvv);
        } catch (Exception ex) {
            Logger.getLogger(Rvn.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void loadDefaultConfiguration() throws IOException, ScriptException, URISyntaxException {
        String base = System.getProperty("rvn.config");
        String name = base + File.separatorChar + "rvn.json";
        //System.out.println(System.getProperties().entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("\n")));
        if (base == null || base.trim().length() == 0) {
            logger.info("system property ${rvn.config} not set defaulting to " + name);
        } else {
            logger.info("system property ${rvn.config} set " + base + ", resolving " + name);
        }
        URL configURL = null;
        if (Files.exists(FileSystems.getDefault().getPath(name))) {
            configURL = FileSystems.getDefault().getPath(name).toUri().toURL();
        } else {
            logger.info("loading from classpath " + name);
            configURL = Rvn.class.getResource(name);
        }
        this.config = this.loadConfiguration(configURL);
    }

    private void addCommand(String projectKey, List<String> newCommandList) {
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest("==" + projectKey + " " + newCommandList.toString());
        }

        commands.compute(projectKey, (key, oldValue) -> {
            List<String> newList = new ArrayList<>(newCommandList);
            if (oldValue != null) {
                newList.addAll(oldValue);
            }
            return newList;
        });

        logger.fine(projectKey + "  " + commands.get(projectKey).toString());
    }

    private boolean matchSafe(Path child) {
        try {
            return matchDirectories(child) || matchFiles(child);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            return false;
        }
    }

    class BuildIt extends Thread {

        public BuildIt() {
            this.setName("BuildIt");
            this.setDefaultUncaughtExceptionHandler((e, t) -> {
                logger.log(Level.WARNING, e.getName() + " " + t.getMessage(), t);
            });
        }

        public void run() {
            while (this.isAlive()) {
                try {
                    Thread.currentThread().sleep(500l);
                } catch (InterruptedException ex) {
                }

                if (q.isEmpty()) {
                    continue;
                } else if (q.size() >= 5) {
                    if (!ee) {
                        ee = !ee;
                        Rvn.this.easterEgg();
                    }
                }

                try (Stream<NVV> path = q.paths()) {
                    if (path == null) {
                        continue;
                    }
                    path.forEach(nvv -> {
                        try {
                            if (!doBuild(nvv).get(timeoutMap.getOrDefault(nvv, timeout).toMillis(),
                                    TimeUnit.MILLISECONDS)) {
                                throw new RuntimeException(ANSI_CYAN + nvv + ANSI_RESET + " failed "
                                        + ((tf != null) ? (ANSI_WHITE + tf.getAbsolutePath() + ANSI_RESET) : ""));
                            }
                            logger.finest("builder next");
                            Thread.yield();

                        } catch (TimeoutException | RuntimeException | InterruptedException | ExecutionException ex) {
                            logger.warning(ANSI_RED + "ERROR" + ANSI_RESET + " build " + ex.getClass().getSimpleName()
                                    + " " + ex.getMessage() + Arrays.asList(ex.getStackTrace())
                                    .subList(0, ex.getStackTrace().length).toString());

                            q.clear();
                        }
                    });
                }

            }
            logger.info("builder exited - no more builds - restart");
        }

        public CompletableFuture<Boolean> doBuild(NVV nvv) {
            CompletableFuture<Boolean> result = new CompletableFuture<>();

            Path dir = buildArtifact.get(nvv);
            if (dir == null) {
                logger.info(String.format("no pom " + ANSI_CYAN + "%1$s" + ANSI_RESET, nvv));
                result.complete(Boolean.TRUE);
                return result;
            }

            if (processMap.containsKey(dir)) {
                logger.info(String.format("already building " + ANSI_CYAN + "%1$s" + ANSI_RESET, nvv));

                if (interruptProcess(nvv)) {
                    result.complete(Boolean.TRUE);
                    return result;
                }
                // processMap.get(path).destroyForcibly(); }
            }

            List<String> commandList = locateCommand(nvv, dir); // nice to have different commands for different paths

            logger.info(nvv + "=>" + commandList.toString());

            for (String command : commandList) {

                if (command.startsWith("!")) {
                    continue;
                }

                String mvnCmd = mvnCmdMap.getOrDefault(nvv, Rvn.this.mvnCmd);
                String mvnOpts = mvnOptsMap.getOrDefault(nvv, Rvn.this.mvnOpts);
                String javaHome = javaHomeMap.getOrDefault(nvv, Rvn.this.javaHome);

                command = command.replace("mvn ", mvnCmd + " " + mvnArgsMap.getOrDefault(nvv, mvnArgs) + " ");

                String cmd = String.format(command, ANSI_PURPLE + dir + ANSI_WHITE) + ANSI_RESET;

                if (command.isEmpty()) {
                    logger.info(String.format(
                            "already running " + ANSI_CYAN + "%1$s " + ANSI_WHITE + "%2$s" + ANSI_RESET, nvv, command));
                    result.complete(Boolean.TRUE);
                    return result;
                } else {
                    logger.info(String.format("building " + ANSI_CYAN + "%1$s " + ANSI_WHITE + "%2$s" + ANSI_RESET, nvv,
                            cmd));
                }

                Pattern testRe = Pattern.compile("^.*src/test/java/(.*Test).java$");

                if (lastChangeFile != null) {
                    Matcher matcher = testRe.matcher(lastChangeFile.toString());

                    if (command.indexOf(mvnCmd) >= 0 && command.endsWith("-Dtest=")) {
                        if (lastChangeFile != null && matcher != null && matcher.matches()) {

                            command = String.format(command + "%1$s", matcher.group(1).replaceAll(File.separator, "."));
                            logger.info(command);
                        } else {
                            continue;
                        }
                    }
                }

                if (command.indexOf(mvnCmd) >= 0 && command.indexOf("-f") == -1) {
                    command = new StringBuilder(command).insert(command.indexOf(mvnCmd) + mvnCmd.length(), " -f %1$s")
                            .toString();
                }

                if (agProjects.contains(nvv)) {
                    command = new StringBuilder(command).insert(command.indexOf(mvnCmd) + mvnCmd.length(), " -N")
                            .toString();
                }

                command = String.format(command, dir);
                String[] args = command.split(" ");
                List<String> filtered = Arrays.stream(args).filter(s -> s.trim().length() > 0).collect(Collectors.toList());

                ProcessBuilder pb = new ProcessBuilder()
                        .directory(dir.getParent().toFile())
                        .command(filtered.toArray(new String[filtered.size()]));

                Instant then = Instant.now();

                Process p = null;
                try {

                    if (Rvn.this.showOutput) {
                        tf = null;
                        pb.inheritIO();
                    } else {
                        tf = File.createTempFile("rvn-" + nvv.toString().replace(':', '-'), ".out");
                        pb.redirectOutput(tf);
                        pb.redirectError(tf);
                        logger.fine("redirecting to " + ANSI_WHITE + tf + ANSI_RESET);
                    }

                    pb.environment().putAll(System.getenv());

                    if (mvnOpts != null && !mvnOpts.trim().isEmpty()) {
                        pb.environment().put("maven.opts", mvnOpts);
                    }
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.finest(pb.environment().entrySet().stream().map(e -> e.toString()).collect(Collectors.joining("\r\n", ",", "\r\n")));
                    }
                    if (javaHome != null && !javaHome.trim().isEmpty()) {
                        pb.environment().put("JAVA_HOME", javaHome);
                        String path = "Path";
                        pb.environment().put(path, new StringBuilder(javaHome).append(File.separatorChar).append("bin").append(File.pathSeparatorChar).append(pb.environment().getOrDefault(path, "")).toString()); //FIXME:  maybe microsoft specific
                        if (logger.isLoggable(Level.FINE)) {
                            logger.fine(pb.environment().entrySet().stream().filter(e -> e.getKey().equals(path)).map(e -> e.toString()).collect(Collectors.joining(",", ",", ",")));
                        }
                    }

                    p = pb.start();

                    processMap.put(dir, p);

                    if (!p.waitFor(timeoutMap.getOrDefault(nvv, timeout).toMillis(), TimeUnit.MILLISECONDS)) {
                        stopProcess(p);
                    }

                    processMap.remove(dir);

                    logger.info(String.format(
                            ANSI_CYAN + "%1$s " + ANSI_RESET + ((p.exitValue() == 0) ? ANSI_GREEN : ANSI_RED)
                            + (p.exitValue() == 0 ? "PASSED" : "FAILED") + " (%2$s)" + ANSI_RESET
                            + " with command " + ANSI_WHITE + "%3$s" + ANSI_YELLOW + " %4$s" + ANSI_RESET,
                            nvv, +p.exitValue(), command, Duration.between(then, Instant.now())));

                } catch (Exception ex) {
                    logger.log(Level.SEVERE, "wasted process" + ex.getMessage(), ex);
                } finally {
                    if (p.exitValue() != 0) {
                        result.complete(Boolean.FALSE);
                        if (!Rvn.this.showOutput) { // TODO make configurable
                            failMap.put(nvv, tf);
                        }
                        break;
                    } else {
                        result.complete(Boolean.TRUE);
                        failMap.remove(nvv);
                    }

                    Rvn.this.then = Instant.now();
                }
            }

            if (result == null) {
                logger.info("no commands to build");
            }

            if (result != null && result.getNow(Boolean.FALSE)) {
                dir.toFile().setLastModified(Instant.now().toEpochMilli());
            }

            return result;
        }
    }

    private void writeFileToStdout(File tf) throws FileNotFoundException, IOException {
        if (tf != null) {
            try (FileReader reader = new FileReader(tf)) {
                char c[] = new char[1024];
                while (reader.ready()) {
                    int l = reader.read(c);
                    logger.info(new String(c, 0, l));
                }
            }
        }
    }

    private List<String> locateCommand(NVV nvv, Path path) {
        List<String> commandList = commands.entrySet().stream().filter(e -> commandMatch(e.getKey(), nvv, path))
                .flatMap(e -> e.getValue().stream()).collect(Collectors.toList());

        if (commandList.isEmpty()) {
            if (commands.containsKey("::")) {
                commandList.addAll(commands.get("::"));
            } else {
                logger.warning("No project commands or default commands, check your config has buildCommands for ::");
            }
        }

        return commandList;
    }

    private boolean commandMatch(String key, NVV nvv, Path path) {
        return matchNVVCommand(nvv, key) || path.toString().matches(key) || nvv.toString().matches(key);
    }

    private boolean isPom(Path path) {
        return Arrays.asList(new String[]{".*\\/pom.xml$", ".*.pom$"}).stream()
                .filter(s -> path.toAbsolutePath().toString().matches(s)).findFirst().isPresent(); // FIXME: absolutely
    }

    private NVV findPom(Path path) throws Exception {
        // return buildPaths.get(path);
        List<Map.Entry<Path, NVV>> base = buildPaths.entrySet().stream().filter(e -> isBasePath(e.getKey(), path))
                .collect(Collectors.toList());

        logger.fine("base: " + base.toString());

        Optional<Map.Entry<Path, NVV>> nvv = base.stream().reduce((e1, e2) -> {
            return (e1 != null && e1.getKey().getNameCount() >= e2.getKey().getNameCount()) ? e1 : e2;
        });

        if (nvv.isPresent()) {
            return nvv.get().getValue();
        } else {
            return null;
        }
        // map(e -> e.getKey()).reducefindFirst().orElseThrow(() -> new Exception("not
        // known " + path));
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
            URL resource = Rvn.class.getResource("/logging.properties");
            if (resource != null) {
                URI logging = resource.toURI();
                // System.out.println(String.format("%1$s %2$s", logging,
                // Files.exists(Paths.get(logging))));
                LogManager.getLogManager().readConfiguration(logging.toURL().openStream());
            } else {
                System.err.println("no /logging.properties found in classpath");
            }
        } catch (URISyntaxException | IOException ex) {
        }
    }

    public String toSHA1(Path value) throws IOException {
        md.update(Long.toString(Files.size(value)).getBytes());
        Files.lines(value).forEach(s -> md.update(s.getBytes()));
        return new String(md.digest());
    }

    private Path loadConfiguration(URL configURL) throws IOException, ScriptException, URISyntaxException {
        return this.loadConfiguration(configURL, null);
    }

    private Path loadConfiguration(URL configURL, NVV nvv) throws IOException, ScriptException, URISyntaxException {
        Path config = null;

        logger.fine(String.format("trying configuration %1$s", configURL));

        if (configURL == null || configURL.toExternalForm().startsWith("jar:")) {
            config = Paths.get(System.getProperty("user.home") + File.separator + ".m2" + File.separator + "rvn.json");
            if (!Files.exists(config)) {
                logger.info(String.format("%1$s doesn't exist, creating it from " + ANSI_WHITE + "%2$s" + ANSI_RESET,
                        config, configURL));
                try (Reader reader = new InputStreamReader(configURL.openStream());
                        Writer writer = new FileWriter(config.toFile());) {
                    while (reader.ready()) {
                        writer.write(reader.read());
                    }
                    writer.flush();
                }
                logger.info(String.format("written new configuration to " + ANSI_WHITE + "%1$s" + ANSI_RESET, config));
            } else {
                logger.info(String.format("%1$s exists", config));
            }

        } else {
            config = Paths.get(configURL.toURI());
            logger.info(String.format("trying configuration %1$s", configURL));
        }

        this.watch(config.getParent());
        logger.info(String.format("loading configuration " + ANSI_WHITE + "%1$s" + ANSI_RESET, config));

        Reader scriptReader = Files.newBufferedReader(config);
        jdk.nashorn.api.scripting.ScriptObjectMirror result = (jdk.nashorn.api.scripting.ScriptObjectMirror) getEngine()
                .eval(scriptReader);
        if (nvv != null) {
            result.put("projectCoordinates", nvv);
        }
        buildConfiguration(result);
        return config;
    }

    private void buildConfiguration(ScriptObjectMirror result) {
        Optional<NVV> oNvv = Optional.ofNullable((NVV) result.get("projectCoordinates"));

        String key = null;
        if (result.hasMember(key = "mvnCmd")) {

            if (oNvv.isPresent()) {
                mvnCmdMap.put(oNvv.get(), (String) result.get(key));
            } else {
                this.mvnCmd = (String) result.get(key);
            }

        } else {
            if (!oNvv.isPresent()) {
                logger.fine(System.getProperty("os.name"));
                this.mvnCmd = System.getProperty("os.name").regionMatches(true, 0, "windows", 0, 7) ? "mvn.cmd" : "mvn";
            }
        }

        logger.info(key + " " + mvnCmd + " because os.name=" + System.getProperty("os.name")
                + " override with mvnCmd: 'mvn' in config file");

        if (result.hasMember(key = "showOutput")) {
            this.showOutput = (Boolean) result.get(key);
            logger.fine(key + " " + this.showOutput);
        }
        if (result.hasMember(key = "locations")) {
            ScriptObjectMirror v = (ScriptObjectMirror) result.get(key);
            locations.addAll(asArray(v));
            logger.fine(key + " " + locations.toString());

        }
        if (result.hasMember(key = "watchDirectories")) {
            ScriptObjectMirror v = (ScriptObjectMirror) result.get(key);
            this.matchDirIncludes.addAll(asArray((ScriptObjectMirror) v.getMember("includes")));
            this.matchDirExcludes.addAll(asArray((ScriptObjectMirror) v.getMember("excludes")));
            logger.fine(key + " includes " + matchDirIncludes.toString());
            logger.fine(key + " excludes " + matchDirExcludes.toString());
        }

        if (result.hasMember(key = "activeFiles")) {
            ScriptObjectMirror v = (ScriptObjectMirror) result.get(key);
            this.matchFileIncludes.addAll(asArray((ScriptObjectMirror) v.getMember("includes")));
            this.matchFileExcludes.addAll(asArray((ScriptObjectMirror) v.getMember("excludes")));

            logger.fine(key + " includes " + matchFileIncludes.toString());
            logger.fine(key + " excludes " + matchFileExcludes.toString());
        }

        if (result.hasMember(key = "activeArtifacts")) {
            ScriptObjectMirror v = (ScriptObjectMirror) result.get(key);
            this.matchArtifactIncludes.addAll(asArray((ScriptObjectMirror) v.getMember("includes")));
            this.matchArtifactExcludes.addAll(asArray((ScriptObjectMirror) v.getMember("excludes")));

            logger.fine(key + " includes " + matchArtifactIncludes.toString());
            logger.fine(key + " excludes " + matchArtifactExcludes.toString());
        }

        if (result.hasMember(key = "buildCommands")) {
            ScriptObjectMirror v = (ScriptObjectMirror) result.get(key);
            if (v.isArray()) {
                v.values().stream().collect(Collectors.toCollection(LinkedList::new)).descendingIterator()
                        .forEachRemaining(e -> this.addCommand(oNvv.get().toString(), optionalArray(e)));
            } else {
                v.entrySet().forEach(e -> this.addCommand(e.getKey(), optionalArray(e.getValue())));
            }
            logger.fine(commands.toString());
        }

        if (result.hasMember(key = "timeout")) {
            Integer v = (Integer) result.get(key);

            if (oNvv.isPresent()) {
                timeoutMap.put(oNvv.get(), Duration.ofSeconds(v));
            } else {
                timeout = Duration.ofSeconds(v);
            }
            logger.fine(key + " " + timeout);
        }

        if (result.hasMember(key = "mvnOpts")) {
            String v = (String) result.get(key);
            if (oNvv.isPresent()) {
                mvnOptsMap.put(oNvv.get(), v.toString());
            } else {
                mvnOpts = v.toString();
            }
            logger.fine(key + " " + mvnOpts);
        }

        if (result.hasMember(key = "javaHome")) {
            String v = (String) result.get(key);
            if (oNvv.isPresent()) {
                javaHomeMap.put(oNvv.get(), v.toString());
            } else {
                javaHome = v.toString();
            }
            logger.fine(key + " " + javaHome);
        }

        if (result.hasMember(key = "mvnArgs")) {
            String v = (String) result.get(key);
            if (oNvv.isPresent()) {
                mvnArgsMap.put(oNvv.get(), v.toString());
            } else {
                mvnArgs = v.toString();
            }
            logger.fine(key + " " + mvnArgs);
        }

        if (result.hasMember(key = "batchWait")) {
            Integer v = (Integer) result.get(key);
            if (oNvv.isPresent()) {
                batchWaitMap.put(oNvv.get(), Duration.ofSeconds(v));
            } else {
                batchWait = Duration.ofSeconds(v);
            }
            logger.fine(key + " " + batchWait);
        }

        if (result.hasMember(key = "interrupt")) {
            Boolean v = (Boolean) result.get(key);

            if (oNvv.isPresent()) {
                interruptMap.put(oNvv.get(), v);
            } else {
                interrupt = v;
            }
            logger.fine(key + " " + interrupt);
        }

        if (oNvv != null) {
            logger.fine("add project specific settings");

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

    private void easterEgg() {
        logger.info(new Scanner(Rvn.class.getResourceAsStream("/rvn.txt")).useDelimiter("\r").next());
    }

    class CommandHandler implements Function<String, Boolean> {

        private final String verb;
        private final String format;
        private final String description;
        private final Function<String, Boolean> fun;

        public CommandHandler(String verb, String format, String description, Function<String, Boolean> fun) {
            this.verb = verb;
            this.format = format;
            this.description = description;
            this.fun = fun;
        }

        @Override
        public Boolean apply(String t) {
            return this.fun.apply(t);
        }

    }

    /*
	 * {System.out.println(System.getProperties().toString()); }
     */
    static final boolean isAnsi() {

        String term = System.getenv().get("TERM");
        boolean isAnsi = System.console() != null && term != null && (term.contains("color") || term.contains("xterm"));
        return isAnsi;
    }

    public static final Boolean IS_ANSI = isAnsi();
    public static final String ANSI_RESET = IS_ANSI ? "\u001B[0m" : "";
    public static final String ANSI_BOLD = IS_ANSI ? "\u001B[1m" : "";
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
