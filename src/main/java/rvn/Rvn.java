//TODO
// create aggregate pom for known projects to resolve deps
package rvn;

import java.io.Closeable;
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
import java.io.PrintStream;
import java.io.Reader;
import java.io.Writer;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.toList;
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
import jdk.internal.org.jline.reader.LineReader;
import jdk.internal.org.jline.reader.LineReaderBuilder;
import jdk.internal.org.jline.terminal.Terminal;
import jdk.internal.org.jline.terminal.TerminalBuilder;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.codehaus.plexus.classworlds.launcher.Launcher;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;

/**
 *
 * @author wozza
 */
public class Rvn extends Thread {

    /**
     * *
     * are commands for the project merge ` and `` command handlers. *
     * autoResume -rf module projects
     */
    private static Set<String> locations;
    private Set<WatchKey> keys;
    private Map<WatchKey, Path> keyPath;
    private Map<NVV, Path> buildArtifact;
    private Map<NVV, Path> repoArtifact;
    private List<NVV> buildIndex;
    private List<NVV> index;
    private List<NVV> toBuild;
    private Map<Path, NVV> buildPaths;
    private Map<Path, NVV> repoPaths;
    private Map<NVV, FileTime> lastBuild;
    private Map<NVV, FileTime> lastUpdate;
    private Map<NVV, Set<NVV>> projects;
    private Map<NVV, NVV> parent;
    private Map<NVV, Path> failMap; //TODO store fail for each command
    private List<Path> logs;
    private List<Path> paths;
    private Map<Path, Process> processMap;
    private Map<Integer, String> previousCmdIdx;

    private Map<String, List<String>> commands;
    private Map<NVV, Map<String, String>> properties;

    private Set<NVV> agProjects;
    private List<String> matchFileIncludes;
    private List<String> matchFileExcludes;
    private List<String> matchDirIncludes;
    private List<String> matchDirExcludes;
    private List<String> matchArtifactIncludes;
    private List<String> matchArtifactExcludes;
    private List<String> configFileNames;
    private List<String> pomFileNames;
    private List<CommandHandler> commandHandlers;

    private ImportFinder iFinder;
    private Logger log = Logger.getLogger(Rvn.class.getName());
    private static Logger slog = Logger.getLogger(Rvn.class.getName());

    private MessageDigest md = null;

    private Map<String, String> hashes;
    private final WatchService watcher;
    private Path config;
    private String mvnCmd;
    boolean ee = false;
    Boolean showOutput = true;

    private Path lastFile = null;
    private Boolean interrupt;
    private Boolean reuseOutput;
    private Boolean daemon = false;
    private String mvnOpts;
    private String javaHome;
    private String mvnArgs;
    private Duration batchWait;
    private Map<NVV, String> lastCommand;
    private Map<NVV, String> mvnCmdMap;
    private Map<NVV, Duration> batchWaitMap;
    private Map<NVV, Duration> timeoutMap;
    private Map<NVV, Boolean> interruptMap;
    private Map<NVV, String> settingsMap;
    private Map<NVV, String> mvnOptsMap;
    private Map<NVV, String> javaHomeMap;
    private Map<NVV, String> mvnArgsMap;
    private Map<NVV, Boolean> reuseOutputMap;
    private Map<NVV, Boolean> showOutputMap;
    private Map<NVV, Boolean> daemonMap;

    private Map<NVV, Future> futureMap = new ConcurrentHashMap<>();

    private PrintStream out = System.out;
    private PrintStream err = System.err;

    public static void main(String[] args) throws Exception {
        Logger.getAnonymousLogger().warning(ANSI_BOLD + ANSI_GREEN + "Raven 4 Maven" + ANSI_RESET);
        Rvn rvn = new Rvn();
        rvn.locations.addAll(Arrays.asList(args).stream().filter(s -> !s.startsWith("!")).collect(toList()));
        rvn.start();
        rvn.processStdInOld();
        System.out.println(String.format("************** Exited ************************"));
    }

    private Duration timeout = Duration.ofSeconds(60);

    private NVV lastNvv;
    private Instant thenFinished = null;
    private Instant thenStarted = null;
    private Path lastChangeFile;
    private final BuildIt buildIt;
    private Path hashConfig;

    private String userHome = System.getProperty("user.home");

    ScheduledThreadPoolExecutor executor;
    private String settings;

    public Rvn() throws Exception {
        ThreadFactory tFactory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {

                Thread t;

                t = new Thread(r);
                t.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread t, Throwable e) {
                        log.log(Level.SEVERE, "+++++++" + e.getMessage(), e);
                    }
                });
                return t;
            }
        };
        executor = new ScheduledThreadPoolExecutor(10, tFactory);
        this.setName("Rvn_Main");

        this.setDefaultUncaughtExceptionHandler((e, t) -> {
            log.log(Level.WARNING, e.getName() + " " + t.getMessage(), t);
        });

        System.out.print(ANSI_RESET + ANSI_RESET + ANSI_WHITE);
        watcher = FileSystems.getDefault().newWatchService();
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            log.warning(e.getMessage());
        }

        init();

        buildIt = new BuildIt();
        buildIt.start();
    }

    public void init() throws Exception {

        log.info(System.getProperties().toString());
        hashConfig = Paths.get(userHome + File.separator + ".m2" + File.separator + "rvn.hashes");
        locations = new ConcurrentSkipListSet<>();
        keys = new HashSet<>(locations.size());
        keyPath = new HashMap<>();
        projects = new HashMap<>();
        parent = new HashMap<>();
        buildArtifact = new LinkedHashMap<>();
        repoArtifact = new LinkedHashMap<>();
        buildIndex = new ArrayList<>();
        agProjects = new HashSet<>();
        buildPaths = new LinkedHashMap<>();
        toBuild = new CopyOnWriteArrayList<>();
        lastBuild = new HashMap<>();
        lastUpdate = new HashMap<>();
        processMap = new LinkedHashMap<>();
        previousCmdIdx = new HashMap<>();
        failMap = new LinkedHashMap<>();
        commands = new LinkedHashMap<>();
        properties = new HashMap<>();
        hashes = new HashMap<>();
        configFileNames = new ArrayList<>(Arrays.asList(new String[]{".rvn", ".rvn.json"}));
        pomFileNames = new ArrayList<>(Arrays.asList(new String[]{"pom.xml", "pom.yml", ".*.pom$"}));
        matchFileIncludes = new ArrayList<>(configFileNames);
        matchFileExcludes = new ArrayList<>();
        matchDirIncludes = new ArrayList<>();
        matchDirExcludes = new ArrayList<>();
        matchArtifactIncludes = new ArrayList<>();
        matchArtifactExcludes = new ArrayList<>();
        thenFinished = Instant.now();
        thenStarted = Instant.now();
        batchWait = Duration.ofSeconds(0);
        interrupt = Boolean.FALSE;
        lastCommand = new HashMap<>();
        mvnCmdMap = new HashMap<>();
        batchWaitMap = new HashMap<>();
        timeoutMap = new HashMap<>();
        interruptMap = new HashMap<>();
        settingsMap = new HashMap<>();
        mvnOptsMap = new HashMap<>();
        javaHomeMap = new HashMap<>();
        mvnArgsMap = new HashMap<>();
        reuseOutputMap = new HashMap<>();
        showOutputMap = new HashMap<>();
        daemonMap = new HashMap<>();
        logs = new ArrayList<>();
        paths = new ArrayList<>();
        reuseOutput = FALSE;
        mvnOpts = "";
        javaHome = "";
        mvnArgs = "";

        this.readHashes();
        loadDefaultConfiguration();

        URL location = Rvn.class.getProtectionDomain().getCodeSource().getLocation();
        System.out.println("Code in  " + location.getFile());
        System.out.println("Running in " + Paths.get(".").toAbsolutePath().normalize().toString());
        this.locations.add(Paths.get(".").toAbsolutePath().normalize().toString());

        commandHandlers = new ArrayList<>();
        createCommandHandlers();
        updateIndex();

    }

    private void processStdIn() throws IOException {
        final Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .build();
        final LineReader lineReader
                = LineReaderBuilder.builder()
                        .terminal(terminal)
                        //.completer(new MyCompleter())
                        //.highlighter(new MyHighlighter())
                        //.parser(new MyParser())
                        .build();

        CloseableIterator<String> iterator = new CloseableIterator<String>() {
            String line;

            public boolean hasNext() {
                return (line = lineReader.readLine()) != null;
            }

            public String next() {
                log.info(line);
                return line;
            }

            @Override
            public void close() throws IOException {
                terminal.close();
            }
        };

        this.processStdIn(iterator);
    }

    private void processStdInOld() throws IOException {
        Scanner scanner = new Scanner(System.in);

        scanner.useDelimiter(System.getProperty("line.separator"));
        this.processStdIn(scanner);
    }

    private void processStdIn(Iterator<String> iterator) throws IOException {
        Spliterator<String> splt = Spliterators.spliterator(iterator, Long.MAX_VALUE,
                Spliterator.ORDERED | Spliterator.NONNULL);

        while (this.isAlive()) {
            Thread.yield();
            try {
                StreamSupport.stream(splt, false).onClose(() -> {
                    try {
                        ((Closeable) iterator).close();
                    } catch (IOException ex) {
                        Logger.getLogger(Rvn.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }).forEach(this::processCommand);
            } catch (Exception x) {
                log.info(x.getMessage() + " in cmd handler");
                log.log(Level.WARNING, x.getMessage(), x);
            }
        }

        log.info(String.format("commandless"));
    }

    private void processCommand(final String command2) {
        final String command = command2.trim();
        log.info(String.format(ANSI_YELLOW + "%1$s" + ANSI_RESET + " last build finished " + ANSI_YELLOW + "%2$s" + ANSI_RESET + " ago. last build started " + ANSI_YELLOW + "%3$s" + ANSI_RESET + " ago.",
                LocalTime.now(), Duration.between(thenFinished, Instant.now()).toString(), Duration.between(thenStarted, Instant.now()).toString()));

        Optional<Boolean> handled = commandHandlers.stream().map(c -> c.apply(command))
                .filter(b -> Boolean.TRUE.equals(b)).findFirst();
        if (handled.isPresent()) {
            if (handled.get().booleanValue()) {
                log.info("" + (char) (new Random().nextInt(5) + 1));
            }
        }
    }

    public String removeDebug(String s) {
        String s2 = s.replaceFirst("-Xrunjdwp:[0-9A-Za-z_,=]+", "");
        return s2;
    }

    private void toggleCommand(NVV nvv, String cmd) {
        this.commands.entrySet().stream().filter(e -> this.matchNVV(nvv, e.getKey())).map(e -> e.getValue()).forEach(list
                -> {
            int i = list.indexOf(cmd);
            if (i >= 0) {
                list.set(i, this.toggleCommand(cmd));
            }
        });
    }

    private String toggleCommand(String cmd) {
        if (cmd.startsWith("!")) {
            return cmd.substring(1);
        } else {
            return "!" + cmd;
        }
    }

    private boolean compareHashes(Path bPath, Path rPath) {

        if (bPath == null || rPath == null) {
            return false;
        }

        String bHash = this.hashes.get(bPath.toString());
        String rHash = this.hashes.get(rPath.toString());

        if (bHash == null || rHash == null) {
            log.info("no hash");
            return false;
        }
        return bHash.equals(rHash);
    }

    private String expandNVVMatch(String s) {

        StringBuilder bob = new StringBuilder();
        if (!s.startsWith("^")) {
            bob.append("^");
        }
        if (s.startsWith(":")) {
            bob.append("^.*");
        }
        bob.append(s);
        if (s.endsWith(":")) {
            bob.append(".*");
        }

        if (!s.endsWith("$")) {
            bob.append("$");
        }

        log.finest(s + "->" + bob.toString());
        return bob.toString();
    }

    private boolean needsBuild(NVV nvv) {
        Optional<Path> bPath = this.buildArtifact.entrySet().stream().filter(e -> e.getKey().equalsExact(nvv)).map(e -> e.getValue()).findAny();
        Optional<Path> rPath = this.repoArtifact.entrySet().stream().filter(e -> e.getKey().equals(nvv)).map(e -> e.getValue()).findAny();
        if (bPath.isEmpty() || rPath.isEmpty()) {
            log.fine(nvv.toString() + " " + bPath + " " + rPath);
            return true;
        }
        return !compareHashes(bPath.get(), rPath.get());
    }

    private void resolveVersion() {
        Stream.concat(
                Stream.concat(
                        Stream.concat(
                                this.projects.keySet().stream(),
                                Stream.concat(
                                        this.buildIndex.stream(),
                                        this.index.stream())
                        ), this.toBuild.stream()),
                this.projects.values().stream().flatMap(s -> s.stream()))
                .forEach(nvv -> {
                    resolveVersion(nvv);
                });
    }

    private void resolveVersion(NVV nvv) {
        NVV parentNvv = this.parent.get(nvv);

        if (parentNvv == null) {
            return;
        }

        this.resolveVersion(parentNvv);

        if (nvv.vendor.isBlank()) {
            nvv.vendor = parentNvv.vendor;
        }
        if (nvv.version.isBlank()) {
            nvv.version = parentNvv.version;
        } else if (nvv.version.equalsIgnoreCase("${pom.version}") || nvv.version.equalsIgnoreCase("${project.version}")) {
            nvv.version = parentNvv.version;
        } else if (nvv.version.startsWith("${")) {
            nvv.version = interpolate(nvv, nvv.version);
        }

        nvv.resolveVersion(nvv.version);

    }

    private String interpolate(NVV nvv, String value) {
        Pattern p = Pattern.compile("\\$\\{(.*)\\}");
        Map<String, String> props = null;
        props = propertiesFrom(nvv);
        Matcher matcher = p.matcher(nvv.version);
        if (matcher.matches()) {
            String name = matcher.group(1);
            String newValue = null;
            if (props.containsKey(name)) {
                newValue = props.get(name);
                return newValue;
            } else {
                NVV parentNvv = this.parent.get(nvv);

                if (parentNvv != null) {
                    newValue = this.interpolate(parentNvv, value);
                    if (newValue != null) {
                        return newValue;
                    }
                }
            }
        }
        if (nvv.path != null && !nvv.path.toString().endsWith(".pom")) {
            log.warning("not resolved " + nvv + " " + value);
        }
        return value;
    }

    private boolean matchNVV(NVV nvv, Path path) {
        try {
            return matchNVV(nvv) && matchFiles(path);
        } catch (IOException ex) {
            log.warning(ex.getMessage());
            return false;
        }
    }

    private Stream<NVV> projectDepends(NVV nvv) {
        return projects.entrySet().stream()
                .filter(e
                        -> e.getValue().contains(nvv)
                ).map(e -> e.getKey());
    }

    private void rehash() {

        this.buildArtifact = new LinkedHashMap<>(this.buildArtifact);
        this.repoArtifact = new LinkedHashMap<>(this.repoArtifact);
        this.parent = new LinkedHashMap<>(this.parent);
    }

    private static abstract class CloseableIterator<T> implements Iterator<T>, Closeable {
    }

    class SafeConsumer<T> implements Consumer<T> {

        protected Consumer<T> f;

        public SafeConsumer(Consumer<T> f) {
            this.f = f;
        }

        @Override
        public void accept(T t) {
            try {
                f.accept(t);
            } catch (Throwable ex) {
                log.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
    }

    class Safe<T, R> {

        protected Function<T, R> f;

        public Safe(Function<T, R> f) {
            this.f = f;
        }
    }

    /**
     * class SafeFunction<T, R> extends Safe<T, R> implements Function<T, R> {
     *
     * @Override public R apply(T t) { try { return f.apply(t); } catch
     * (Throwable ex) { log.log(Level.SEVERE, ex.getMessage(), ex); return null;
     * } } }*
     */
    public void findConfiguration(String uri) {
        Path dir = Paths.get(uri);
        log.fine(String.format(ANSI_WHITE + "searching %1$s for config" + ANSI_RESET, dir));
        findConfiguration(dir);
    }

    public void findConfiguration(Path path) {
        try {
            if (Files.isDirectory(path)) {
                try (Stream<Path> stream = Files.list(path)) {
                    stream.filter(child -> matchSafe(child)).forEach(this::findConfiguration);
                }
            } else if (path.getFileName() != null && this.configFileNames.contains(path.getFileName().toString())) {
                this.loadConfiguration(path);
            }
        } catch (IOException ex) {
            log.info(
                    String.format("register failed %1$s %2$s %3$s", path, ex.getClass().getName(), ex.getMessage()));
        }
    }

    public void registerPath(String uri) {
        Path dir = Paths.get(uri);
        log.info(String.format(ANSI_WHITE + "watching %1$s" + ANSI_RESET, dir));
        registerPath(dir);
    }

    public void registerPath(Path path) {
        try {
            if (Files.isDirectory(path)) {
                try (Stream<Path> stream = Files.list(path)) {
                    stream.sorted().filter(child -> matchSafe(child)).forEach(this::registerPath);
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
            } else {
                this.paths.add(path);
            }
        } catch (IOException | SAXException | XPathExpressionException | ParserConfigurationException ex) {
            log.info(
                    String.format("register failed %1$s %2$s %3$s", path, ex.getClass().getName(), ex.getMessage()));
        }
    }

    int depth = 0;
    int maxDepth = 10;

    public Optional<FileTime> watchRecursively(Path dir) {
        depth++;
        if (depth > maxDepth) {
            log.warning(dir + " is " + depth + " deep");
        }
        watch(dir);
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(child -> Files.isDirectory(child) && matchDirectories(child)).forEach(this::watchRecursively);
        } catch (IOException ex) {
            log.info(String.format("recurse failed %1$s %2$s", ex.getClass().getName(), ex.getMessage()));
        } finally {
            depth--;
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
            log.info(String.format("recurse failed %1$s %2$s", ex.getClass().getName(), ex.getMessage()));
        }

        return null;
    }

    public void watch(Path dir) {
        try {
            WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);

            keys.add(key);
            keyPath.put(key, dir);
            log.fine(String.format("watching %1$s %2$d", dir, key.hashCode()));
        } catch (IOException x) {
            System.err.println("watch " + x.getMessage());
        }
    }

    public void scan() {
        locations.stream().forEach(this::findConfiguration);
        log.fine("locations :" + locations.toString().replace(',', '\n'));
        locations.stream().forEach(this::registerPath);

        log.fine("buildSet :" + buildPaths.toString().replace(',', '\n'));
        ArrayList watchSet;
        watchSet = new ArrayList(this.keyPath.values());
        Collections.sort(watchSet);
        log.fine("watchSet :" + watchSet.toString().replace(',', '\n'));

        this.buildIndex();

        try {
            this.writeHashes();
        } catch (IOException ex) {
            log.severe(ex.getMessage());
        }
        this.resolveVersion();

        rehash();
        this.calculateToBuild();

        this.iFinder = new ImportFinder(this.paths);

        watchSummary();
    }

    public void watchSummary() {
        Duration duration = Duration.between(thenFinished, Instant.now());
        log.info(String.format(ANSI_WHITE + "watching %1$s projects, %2$s builds, %3$s are out of date,  %4$s keys - all in %5$s" + ANSI_RESET,
                projects.size(), buildPaths.size(), toBuild.size(), keys.size(), duration.toString()));
    }

    @Override
    public void run() {
        scan();

        Map<Path, WatchEvent.Kind> events = new HashMap<>();
        long lastEvent = System.currentTimeMillis();

        while (this.isAlive()) {
            Thread.yield();

            if (System.currentTimeMillis() - lastEvent >= 400 && events.size() > 0) {
                System.err.print('.');

                events.entrySet().forEach(e -> processEvent(e.getKey(), e.getValue()));
                events.clear();
            }
            //log.fine(String.format("waiting.."));

            WatchKey key;

            try {
                key = watcher.poll(500, TimeUnit.MILLISECONDS);
                if (key == null) {
                    continue;
                }
                lastEvent = System.currentTimeMillis();
            } catch (InterruptedException x) {
                continue;
            }

            try {
                for (WatchEvent<?> event : key.pollEvents()) {
                    events.put(this.resolve(event, key), event.kind());
                }

            } catch (Throwable ex) {
                Logger.getLogger(Rvn.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            } finally {

                boolean valid = key.reset();
                if (!valid) {
                }
            }
        }

        log.info(String.format("outrun"));
        Thread.dumpStack();
    }

    public Optional<String> getExtensionByStringHandling(String filename) {
        return Optional.ofNullable(filename).filter(f -> f.contains("."))
                .map(f -> f.substring(filename.lastIndexOf(".") + 1));
    }

    private void processPathSafe(String uri) {
        try {
            processPath(uri);

        } catch (XPathExpressionException | SAXException | IOException | ParserConfigurationException ex) {
            log.log(Level.SEVERE, null, ex);
        } catch (Exception ex) {
            log.log(Level.SEVERE, null, ex);
        }
    }

    private void processPath(String uri)
            throws XPathExpressionException, SAXException, IOException, ParserConfigurationException, Exception {
        this.processPath(uri, false);
    }

    private void processPath(String uri, boolean immediate) throws XPathExpressionException, SAXException, IOException, ParserConfigurationException, Exception {
        log.fine("in ->" + uri);
        Path path = Paths.get(uri);
        processPath(path, immediate);
    }

    private void processPath(Path path)
            throws XPathExpressionException, SAXException, IOException, ParserConfigurationException, Exception {
        this.processPath(path, false);
    }

    private void processPath(Path path, boolean immediate)
            throws XPathExpressionException, SAXException, IOException, ParserConfigurationException, Exception {
        if (!Files.isRegularFile(path)) {
            return;
        }

        if (!matchFiles(path)) {
            return;
        }

        boolean skipHash = !path.endsWith("pom.xml") || isConfigFile(path);

        if (!skipHash && hashes.containsKey(path.toString()) && toSHA1(path).equals(hashes.get(path.toString()))) {
            log.info("no hash change detected " + path);
            return;
        }

        if (path.toString().endsWith(".pom")) {
            log.fine(String.format("no nvv: %1$s", path));
            Document pom = this.loadPom(path);
            NVV nvv = this.nvvFrom(pom).with(path);
            this.nvvParent(nvv, pom);

            log.info(String.format("nvv: %1$s %2$s", path, nvv));
            if (matchNVV(nvv, path)) {
                //this.buildDeps(nvv);
            }
            return;
        } else if (this.configFileNames.contains(path.getFileName().toString())) {
            NVV nvv = findPom(path);

            if (nvv == null) {
                log.fine(String.format("no nvv: %1$s", path));
                return;
            } else {
                log.info(String.format("config nvv: %1$s %2$s", path, nvv));
            }
            this.loadConfiguration(path);
            return;
        }

        try {
            NVV nvv = findPom(path);
            if (nvv == null) {
                log.fine(String.format("no nvv: %1$s", path));
                return;
            } else {
                log.info(String.format("nvv: %1$s %2$s", path, nvv));
            }

            nvv.with(path);
            if (matchNVV(nvv)) {
                lastNvv = nvv;
                if (this.lastChangeFile == null) {
                    this.lastChangeFile = path;
                }
                processChange(nvv, path, immediate);
                log.info(String.format("change triggered by config: %1$s %2$s", path, nvv));
            } else {
                log.info(String.format("change excluded by config: %1$s %2$s", path, nvv));
            }

        } catch (Exception x) {
            log.warning(String.format("process: %1$s - %2$s", path, x.getMessage()));
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
        NVV parentNvv = nvvParent(nvv, xmlDocument);

        this.resolveVersion(nvv);

        if (!matchNVV(nvv, path)) {
            log.finest("ignoring " + nvv.toString() + "  " + path.toString());
            return Optional.empty();
        }
        log.finest("selected " + nvv.toString() + "  " + path.toString());

        if (path.endsWith("pom.xml")) {
            Long workingTime = Files.getLastModifiedTime(path).to(TimeUnit.SECONDS);

            Path oldPath = buildArtifact.get(nvv);
            if (oldPath != null && !Files.isSameFile(path, oldPath)) {
                Long otherTime = Files.getLastModifiedTime(oldPath).to(TimeUnit.SECONDS);
                if (workingTime > otherTime) {
                    log.warning(
                            String.format(
                                    ANSI_PURPLE + "%1$s " + ANSI_YELLOW + "newer than" + ANSI_PURPLE + " %2$s"
                                    + ANSI_CYAN + " %3$s" + ANSI_RED + ", replacing" + ANSI_RESET,
                                    path, oldPath, nvv.toString()));
                } else {
                    log.warning(
                            String.format(
                                    ANSI_PURPLE + "%1$s " + ANSI_YELLOW + "older than" + ANSI_PURPLE + " %2$s"
                                    + ANSI_CYAN + " %3$s" + ANSI_RESET + ", ignoring",
                                    path, oldPath, nvv.toString()));
                    return Optional.empty();
                }
            }

            log.fine("found build " + nvv.toString() + " " + path.toString());
            this.buildArtifact.put(nvv, path);
            this.buildPaths.put(path, nvv);
        } else if (path.toString().endsWith(".pom")) {
            log.finest(nvv.toString() + " ++++++++ " + path.toString());
            this.repoArtifact.put(nvv, path);
        } else {
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

        Set<NVV> deps = StreamSupport.stream(splt, true)
                .map(n -> this.processDependency(n, nvv)).filter(t -> t != null)
                .filter(nvv2 -> this.matchNVV(nvv2, path))
                .collect(Collectors.toSet());

        if (!Objects.isNull(parentNvv)) {
            parent.put(nvv, parentNvv);
            deps.add(parentNvv);
        }

        NodeList modules = (NodeList) xPath.compile("//modules/module").evaluate(xmlDocument, XPathConstants.NODESET);
        if (modules.getLength() > 0) {
            log.fine(String.format("aggregator project %1$s", nvv.toString()));
            agProjects.add(nvv);
        }

        Map<String, String> props = this.propertiesFrom(xmlDocument);
        this.properties.put(nvv, props);

        log.fine(String.format("tracking %1$s %2$s", nvv.toString(), path));
        projects.put(nvv, deps);
        return Optional.of(nvv);
    }

    private NVV processDependency(Node n, NVV proj) {

        /**
         * //TODO make configurable on project by project basis if
         * (n.getParentNode().getParentNode().getNodeName().equals("plugin")) {
         * return null; }
         *
         */
        try {
            NVV nvv = nvvFrom(n);
            //log.info(String.format(proj.toString() + " depends on %1$s", nvv.toString()));
            //log.info(n.getParentNode().getParentNode().getNodeName());
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

        } catch (Exception e) {
        }
        log.fine(String.format("%1$s with parent %2$s", nvv.toString(), parentNvv));
        return parentNvv;
    }

    private NVV nvvFrom(Path path)
            throws XPathExpressionException, SAXException, IOException, ParserConfigurationException {
        Document xmlDocument = loadPom(path);
        return nvvFrom(xmlDocument).with(path);
    }

    private NVV nvvFrom(Document xmlDocument) throws XPathExpressionException {

        Node project = (Node) xPath.compile("/project").evaluate(xmlDocument, XPathConstants.NODE);

        NVV nvv = nvvFrom(project);

        return nvv;
    }

    private Node projectFrom(Path path)
            throws XPathExpressionException, SAXException, IOException, ParserConfigurationException {
        Document xmlDocument = loadPom(path);
        return projectFrom(xmlDocument);
    }

    private Node projectFrom(Document xmlDocument) throws XPathExpressionException {
        Node project = (Node) xPath.compile("/project").evaluate(xmlDocument, XPathConstants.NODE);
        return project;
    }

    //TODO load NODE From
    private Map<String, String> propertiesFrom(NVV nvv) {

        if (this.properties.containsKey(nvv)) {
            return this.properties.get(nvv);
        }

        //TODO get properties from repoArtifacts too
        Path pom = this.buildArtifact.getOrDefault(nvv, this.repoArtifact.get(nvv));

        if (pom != null) {

            try {
                return propertiesFrom(pom); //TODO move to resolve
            } catch (Exception ex) {
                Logger.getLogger(Rvn.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        return Collections.EMPTY_MAP;
    }

    private Map<String, String> propertiesFrom(Path path)
            throws XPathExpressionException, SAXException, IOException, ParserConfigurationException {
        return propertiesFrom(projectFrom(path));
    }

    private Map<String, String> propertiesFrom(Node context) throws XPathExpressionException {
        Map<String, String> result = new HashMap<>();
        NodeList propSet = (NodeList) xPath.compile("//properties/node()").evaluate(context, XPathConstants.NODESET);

        for (int i = 0; i < propSet.getLength(); i++) {
            if (Node.ELEMENT_NODE == propSet.item(i).getNodeType()) {
                String name = propSet.item(i).getNodeName();
                String value = ((Element) propSet.item(i)).getTextContent();
                result.put(name, value);
            }
        }

        //log.info(result.toString());
        return result;
    }

    XPath xPath = XPathFactory.newInstance().newXPath();

    private NVV nvvFrom(Node context) throws XPathExpressionException {
        return new NVV(xPath.compile("artifactId").evaluate(context).trim(), xPath.compile("groupId").evaluate(context).trim(),
                xPath.compile("version").evaluate(context).trim());
    }

    private void processChangeImmediatley(NVV nvv) {
        processChange(nvv, buildArtifact.get(nvv), true);
    }

    private NVV processChange(NVV nvv) {
        processChange(nvv, buildArtifact.get(nvv), false);
        return nvv;
    }

    private void processChange(NVV nvv, Path path, boolean immediate) {
        log.info(String.format(
                "changed " + ANSI_CYAN + "%1$s" + ANSI_PURPLE + " %2$s" + ANSI_YELLOW + " %3$s" + ANSI_RESET,
                nvv.toString(), path, LocalTime.now().toString()));

        if (path != null) {
            try {
                hashes.put(path.toString(), this.toSHA1(path));
                writeHashes();
            } catch (java.nio.charset.MalformedInputException ex) {
                Logger.getLogger(Rvn.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            } catch (IOException ex) {
                Logger.getLogger(Rvn.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            }
        }

        this.scheduleFuture(nvv, immediate);
    }

    private synchronized void scheduleFuture(NVV nvv, boolean immediate) {
        Duration batchWait = immediate ? Duration.ZERO : batchWaitMap.getOrDefault(nvv, this.batchWait);

        futureMap.computeIfPresent(nvv, (nvv1, future) -> {
            Boolean interrupt = interruptMap.getOrDefault(nvv1, this.interrupt);
            if (future.isDone()) {
                return null;
            } else if (interrupt) {
                if (future instanceof ScheduledFuture) {
                    log.fine("already scheduled " + nvv1.toString());
                    return null;
                } else {
                    synchronized (futureMap) {
                        Rvn.this.stopBuild(nvv);
                    }
                    return null;
                }
            } else {
                log.fine("already queued/running " + nvv1.toString());
                return future;
            }
        });

        futureMap.computeIfAbsent(nvv, nvv1 -> {
            log.fine(String.format("submitting %1$s with batchWait %2$s ms", nvv.toString(), batchWait.toMillis()));
            ScheduledFuture<?> future = executor.schedule(() -> {
                log.fine(String.format("executing %1$s with batchWait %2$s ms", nvv.toString(), batchWait.toMillis()));
                try {
                    Rvn.this.buildDeps(nvv);
                } catch (Exception e) {
                    log.severe("error " + e);
                } finally {
                    futureMap.remove(nvv);
                }
                return null;
            }, batchWait.toMillis(), TimeUnit.MILLISECONDS);
            log.fine(String.format("waiting for future %1$s in %2$s", nvv.toString(), batchWait.toString()));
            return future;
        });
    }

    private synchronized void buildDeps(NVV nvv) {
        try {
            NVV pNvv = parent.get(nvv);
            if (pNvv != null) {
                buildDeps(pNvv);
            }
            List<NVV> deps = this.projects.entrySet().stream()
                    //.filter(e -> pNvv == null || (pNvv != null && !e.getKey().equals(pNvv)))
                    .flatMap(e -> projectDepends(nvv))
                    .filter(nvv3 -> this.buildArtifact.containsKey(nvv3))
                    .filter(nvv4 -> this.needsBuild(nvv4))
                    .distinct().collect(toList());
            if (deps.isEmpty()) {
                qBuild(nvv, nvv);
            } else {

                deps.forEach(
                        nvv2 -> {
                            qBuild(nvv2, nvv);
                        }
                );
            }

            /**
             * ).filter(e -> e.getValue().stream().filter( nvv2 -> pNvv == null
             * || (pNvv != null && !nvv2.equals(pNvv)) ).filter( nvv2 ->
             * nvv2.equals(nvv) ).findAny().isPresent() ).map(e -> e.getKey())
             * .distinct() .forEach( nvv2 -> { qBuild(nvv, nvv2); } ); *
             */
        } catch (RuntimeException x) {
            log.log(Level.WARNING, String.format("%1$s %2$s", nvv.toString(), x.getMessage()), x);

        }
    }

    private boolean matchFiles(Path path) throws IOException {
        return this.isConfigFile(path)
                || matchFileIncludes.isEmpty() || (matchFileIncludes.stream().filter(s -> this.match(path, s)).findFirst().isPresent() // FIXME: absolutely
                || !matchFileExcludes.stream().filter(s -> this.match(path, s)).findFirst().isPresent()); // FIXME: absolutely
    }

    private boolean matchDirectories(Path path) {
        return matchDirIncludes.stream().filter(s -> this.match(path, s)).findFirst().isPresent() // FIXME: absolutely
                && !matchDirExcludes.stream().filter(s -> this.match(path, s)).findFirst().isPresent(); // FIXME: absolutely
    }

    private boolean matchNVV(NVV nvv) {
        return matchArtifactIncludes.isEmpty() || (matchArtifactIncludes.stream().filter(s -> this.match(nvv, s)).findFirst().isPresent() // FIXME:
                );
        // absolutely
        //&& !matchArtifactExcludes.stream().filter(s -> this.match(nvv, s)).findFirst().isPresent()); // FIXME:
        // absolutely
    }

    private boolean match(Path path, String s) {

        try {
            return this.matchSafe(path, s);
        } catch (PatternSyntaxException pse) {
            log.warning(pse.getMessage() + " " + s);
        }

        return false;
    }

    private boolean matchSafe(Path path, String s) throws PatternSyntaxException {
        boolean matches = path.toAbsolutePath().toString().matches(s);
        if (matches) {
            log.finest("matches path " + path.toString() + " " + s);
        }
        return matches;
    }

    private boolean match(NVV nvv, String s) {

        try {
            return this.matchSafe(nvv, s);
        } catch (PatternSyntaxException pse) {
            log.warning(pse.getMessage() + " " + s);
        }

        return false;
    }

    private boolean matchSafe(NVV nvv, String s) throws PatternSyntaxException {
        boolean matches = nvv.toString().matches(s = expandNVVMatch(s));

        if (matches) {
            log.fine("matches artifact " + s + " " + nvv.toString());
        } else {
            log.finest("doesn't match artifact " + s + " " + nvv.toString());
        }
        return matches;
    }

    private void calculateToBuild() {

        this.buildPaths.entrySet().stream().forEach(e -> {
            NVV nvv = e.getValue();
            if (needsBuild(nvv)) {
                if (!toBuild.contains(nvv)) {
                    toBuild.add(nvv);
                }
            } else {
                log.info(String.format("skipping build - hashes match " + ANSI_GREEN + "%1$d " + ANSI_CYAN + " %2$s " + ANSI_RESET, buildIndex.indexOf(nvv), nvv.toString()));
            }
        });

        Collections.sort(this.toBuild, new Comparator<NVV>() {
            @Override
            public int compare(NVV o1, NVV o2) {
                return Long.compare(Rvn.this.buildIndex.indexOf(o1), Rvn.this.buildIndex.indexOf(o2));
            }
        });

        this.toBuild.forEach(nvv -> {
            log.info(String.format("consider building " + ANSI_GREEN + "%1$d " + ANSI_CYAN + " %2$s " + ANSI_RESET, buildIndex.indexOf(nvv), nvv.toString()));
        });
    }

    private void qBuild(NVV nvv, NVV next) {

        Path dir = buildArtifact.get(nvv);

        if (dir == null) {
            log.warning("no build path " + nvv);
            return;
        } else if (!isPom(dir)) {
            return;
        } else {
            try {
                processPom(dir);
            } catch (SAXException | XPathExpressionException | IOException | ParserConfigurationException ex) {
                log.warning("process " + ex.getMessage());
            }
        }

        Edge edge = new Edge(nvv, next);

        if (!q.contains(edge)) {
            q.insert(edge);
        }
        log.finest(nvv + "=>" + next + " q->" + q.toString().replace(',', '\n'));
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

        for (int i = 0; i < match.length(); i++) {
            Character c = match.charAt(i);
            bob.append(c);
            if (':' == c) {
                if (i == 0) {
                    bob.insert(i, ".*");

                } else if (i == match.length() - 1) {
                    bob.append(".*");
                } else {
                }
                if (match.length() > i + 1 && c.equals(match.charAt(i + 1))) {
                    bob.append(".*");
                }
            }
        }

        log.finest("matching " + match + " " + bob.toString());
        return bob.toString();
    }

    public boolean matchNVV(String project, String match) {
        return project.matches(this.expandNVVRegex(match));
    }

    private boolean matchNVV(NVV project, String match) {
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
                log.info(String.format("%1$s\t\t %2$s \t\t\t %3$s\n", "Command", "Example", "Description"));
                commandHandlers.stream().forEach(c -> {
                    log.info(String.format("%1$s\t\t %2$s \t\t\t - %3$s\n", c.verb, c.format, c.description));

                });
                return TRUE;
            }
            return FALSE;
        }));

        commandHandlers.add(
                new CommandHandler("!", "!", "Stop the current build. Leave the build queue in place", (command) -> {
                    if (command.equals("!")) {
                        stopAllBuilds();
                        return TRUE;
                    }
                    return FALSE;
                }));

        commandHandlers.add(new CommandHandler("- {buildIndex}", "- 1", "Hide the output.",
                (command) -> new SimpleCommand("^-\\s?([0-9]?)$") {

                    public Boolean configure(Iterator<String> i) throws Exception {
                        if (!i.hasNext()) {
                            return FALSE;
                        }

                        i.next();
                        if (i.hasNext()) {
                            NVV nvv;
                            Rvn.this.showOutputMap.put(nvv = this.forProjectIndex(i.next()), FALSE);
                            log.warning(String.format("hiding output for %1$s", nvv.toString()));
                            return TRUE;
                        } else {
                            Rvn.this.showOutput = false;
                            log.info((Rvn.this.showOutput) ? "showing output" : "hiding output");
                            return TRUE;
                        }
                    }
                }.apply(command)));

        commandHandlers.add(new CommandHandler("+ {buildIndex}", "+ 1", "Show the output.", (command) -> new SimpleCommand("^+\\s([0-9]?)$") {

            public Boolean configure(Iterator<String> i) throws Exception {
                if (!i.hasNext()) {
                    return FALSE;
                }

                i.next();

                if (i.hasNext()) {
                    NVV nvv;
                    Rvn.this.showOutputMap.put(nvv = this.forProjectIndex(i.next()), TRUE);
                    log.warning(String.format("showing output for %1$s", nvv.toString()));
                    return TRUE;
                } else {
                    Rvn.this.showOutput = true;
                    log.info((Rvn.this.showOutput) ? "showing output" : "hiding output");
                    return TRUE;
                }
            }
        }.apply(command)));

        commandHandlers.add(new CommandHandler("_", "_", "Show the last output passed or failed.", (command) -> {
            if (command.equals("_")) {
                Iterator<Path> it = logs.iterator();
                Path last = null;
                try {
                    if (it.hasNext()) {
                        last = it.next();
                        writeFileToStdout(last);
                    }
                } catch (IOException ex) {
                    log.info("show " + ex.getMessage());
                } finally {
                    if (last != null) {
                        it.remove();
                    }
                }
                return TRUE;
            }
            return FALSE;
        }));

        commandHandlers.add(new CommandHandler("|", "|", "Show the last failed output.", (command) -> {
            if (command.equals("|")) {
                try {
                    writeFileToStdout(Rvn.this.lastFile);
                } catch (IOException ex) {
                    log.info("show last " + ex.getMessage());
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
                        stopAllBuilds();
                        List<NVV> l = new ArrayList<>();
                        this.q.oq.drainTo(l);
                        if (l.isEmpty()) {
                        } else {
                            log.info("cancelled " + ANSI_CYAN + l.toString() + ANSI_RESET);
                        }
                        return TRUE;
                    }
                    return FALSE;
                }));

        commandHandlers.add(new CommandHandler(">", ">", "Show the fail map.", (command) -> {
            if (command.equals(">")) {
                index.stream().filter(nvv -> failMap.containsKey(nvv)).filter(nvv -> failMap.get(nvv) != null)
                        .forEach(nvv -> {
                            log.info(String.format(ANSI_GREEN + "%1$s " + ANSI_CYAN + "%2$s " + ANSI_PURPLE + "%3$s" + ANSI_RESET, buildIndex.indexOf(nvv), nvv, failMap.get(nvv)));
                        });
                return TRUE;
            }
            return FALSE;
        }));

        commandHandlers.add(new CommandHandler(">[0-9]", ">1", "Show the fail map entry.", (command) -> {
            if (command.matches(">[0-9]+")) {
                Integer i = Integer.valueOf(command.substring(1));
                NVV nvv = buildIndex.get(i);
                Path fail = failMap.get(nvv);
                try {
                    writeFileToStdout(fail);
                } catch (IOException ex) {
                    log.warning("show fail " + ex.getMessage());
                }
                return TRUE;
            }
            return FALSE;
        }));
        commandHandlers
                .add(new CommandHandler("@", "@", "Reload the configuration file and rescan filesystem.", (command) -> {
                    if (command.equals("@")) {
                        try {
                            this.commands.clear();
                            this.reloadConfiguration();
                        } catch (Exception ex) {
                            log.warning("reload " + ex.getMessage());
                        }
                        return TRUE;
                    }
                    return FALSE;
                }));

        commandHandlers.add(new CommandHandler("\\", "\\", "List yet to build list", (command) -> {
            if (command.trim().equals("\\")) {
                log.info(this.toBuild.stream()
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
            if (command.trim().equals("\\\\")) {
                this.toBuild.stream().forEach(nvv -> this.buildDeps(nvv));
                return TRUE;
            }
            return FALSE;
        }));

        commandHandlers.add(new CommandHandler("#", "#", "Build all projects outdated by hashes", (command) -> {
            if (command.trim().equals("#")) {

                this.hashes.forEach((k, v) -> {
                    try {
                        this.processPath(k, true);
                    } catch (Exception ex) {
                        log.severe("build " + ex.getMessage());
                    }
                });
                return TRUE;
            }
            return FALSE;
        }));

        commandHandlers.add(new CommandHandler("=", "=:test:", "List build commands for project", (command) -> {
            if (command.startsWith("=")) {

                log.info(this.commands.keySet().stream()
                        .filter(i -> matchNVV(i, command.length() == 1 ? ".*" : command.substring(1)))
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
                        this.buildArtifact.keySet().stream().filter(n -> matchNVV(n, command)).forEach(n -> {
                            lastNvv = this.processChange(n);
                        });
                        return TRUE;
                    }
                    return FALSE;
                }));

        commandHandlers.add(new CommandHandler("path", "/path/to/pom.xml",
                "Builds the project(s) for the given coordinate(s). Supports regexp.", (command) -> {
                    return this.paths.stream().filter(p -> p != null && this.match(p, command)).map(p -> {
                        this.hashes.remove(p.toString());
                        try {
                            this.lastChangeFile = p;
                            this.processPath(p, true);
                        } catch (Exception ex) {
                            log.warning("path " + ex.getMessage());
                        }
                        return p;
                    }).iterator().hasNext();
                }));

        commandHandlers.add(new CommandHandler("path", "/tmp/to/fail.out", "Dump the file to stdout.", (command) -> {
            if (command.endsWith(".out")) {
                if (Files.exists(Paths.get(command))) {
                    try {
                        Rvn.this.writeFileToStdout(Paths.get(command).toFile());
                    } catch (Exception ex) {
                        log.warning("path out " + ex.getMessage());

                    }
                }
                return TRUE;
            }
            return FALSE;
        }));
        commandHandlers.add(new CommandHandler(">>", ">>", "Dump the first entry in the fail map.", (command) -> {
            if (command.equals(">>")) {
                failMap.entrySet().stream().findFirst().ifPresent(e -> {
                    if (Files.exists(e.getValue())) {
                        try {
                            Rvn.this.writeFileToStdout(e.getValue());
                        } catch (Exception ex) {
                            log.warning("dump first " + ex.getMessage());
                        }
                    }
                });
                return TRUE;
            }
            return FALSE;
        }));

        commandHandlers.add(new CommandHandler("timeout {number} {buildIndex}", "timeout 60 1",
                "Sets the maximum build timeout to 1 minute.", (command) -> new SimpleCommand("^timeout\\s([0-9]+)\\s([0-9]?)$") {

                    public Boolean configure(Iterator<String> i) throws Exception {
                        if (!i.hasNext()) {
                            return FALSE;
                        }

                        i.next();

                        Duration timeout = Duration.ofSeconds(Integer.parseInt(i.next()));
                        if (i.hasNext()) {
                            NVV nvv;
                            Rvn.this.timeoutMap.put(nvv = this.forProjectIndex(i.next()), timeout);
                            log.warning(String.format("timeout for %1$s is %2$s second", nvv.toString(), timeout.toString()));
                            return TRUE;
                        } else {
                            Rvn.this.timeout = timeout;
                            log.warning(String.format("timeout is %1$s second", timeout.toString()));
                            return TRUE;
                        }
                    }
                }.apply(command)));

        commandHandlers.add(new CommandHandler("/", "/", "Rebuild all projects in fail map.", (command) -> {
            if (command.trim().equals("/")) {
                failMap.entrySet().stream().filter(e -> e.getValue() != null).forEach(e -> {
                    Rvn.this.buildDeps(e.getKey());
                });
                return TRUE;
            }
            return FALSE;
        }));

        commandHandlers.add(new CommandHandler("q", "", "Stop all builds and exit.", (command) -> {
            if (command.trim().equalsIgnoreCase("q")) {
                log.info("blitzkreik");

                stopAllBuilds();

                executor.schedule(() -> {
                    System.exit(0);
                }, 1, TimeUnit.SECONDS);
                return TRUE;
            }
            return FALSE;
        }));

        commandHandlers.add(new CommandHandler("[enter]", "", "Proceed with all builds waiting.", (command) -> {
            if (command.trim().length() == 0) {
                log.info("resubmitting all scheduled builds");

                futureMap.forEach((nvv, future) -> {
                    this.executor.submit(() -> {
                        future.cancel(true);
                        futureMap.remove(nvv);
                        qBuild(nvv, nvv);
                        return null;
                    });
                });
                return TRUE;
            }
            return FALSE;
        }));

        commandHandlers.add(new CommandHandler("`", "`[:test:|#]",
                "List known project(s) matching coordinate or path expression.", (command) -> {
                    if (command.startsWith("`") && !command.equals("``")) {
                        updateIndex();
                        String nvvMatch = command.substring(1);
                        if (nvvMatch.isBlank()) {
                            nvvMatch = ".*";
                        }
                        final String match = nvvMatch;

                        List<NVV> selected = buildArtifact.entrySet().stream()
                                .filter(e -> matchNVV(e.getKey(), match) || match(e.getValue(), match)).map(e -> e.getKey()).collect(Collectors.toList());

                        log.info(selected.stream()
                                .sorted((nvv1, nvv2) -> Integer.compare(this.buildIndex.indexOf(nvv1), this.buildIndex.indexOf(nvv2)))
                                .map(i -> String.format(((this.toBuild.indexOf(i)) >= 0 ? (ANSI_RED + "*") : " ")
                                + ANSI_GREEN + "%1$d " + ANSI_CYAN + "%2$s " + ANSI_PURPLE + "%3$s" + ANSI_RESET,
                                buildIndex.indexOf(i), i, buildArtifact.get(i)))
                                .collect(Collectors.joining("," + System.lineSeparator(), "", "")));
                        watchSummary();
                        return TRUE;
                    }
                    return FALSE;
                }));

        commandHandlers.add(new CommandHandler("[:num:]+[!`]?[`:num:,\\-]+", "100,3-5", "Builds the given project with the commands. To rebuild last use ``,  To list commands omit the second argument.", (command) -> {
            Pattern re = Pattern.compile("([0-9]+)([`!])([0-9,\\-`]*)");

            Matcher matcher = re.matcher(command);
            if (matcher.matches()) {
                if (matcher.group(1).isBlank()) {
                    return Boolean.FALSE;
                }
                Integer index = Integer.parseInt(matcher.group(1));
                if (this.buildIndex.size() <= index) {
                    log.info("not enough commands" + index);
                    return TRUE;
                }
                NVV nvv = this.buildIndex.get(index);
                Integer cmdIndex = null;
                List<String> commands = this.locateCommand(nvv, null);

                if (matcher.groupCount() == 3 && !matcher.group(3).isBlank()) {

                    String cmd = null;

                    if ("`".equals(matcher.group(3))) {
                        cmd = this.previousCmdIdx.get(index);
                    } else {
                        cmd = matcher.group(3);
                        this.previousCmdIdx.put(index, cmd);
                    }

                    List<Integer> rangeIdx = rangeToIndex(cmd);
                    for (Integer cmdIdx : rangeIdx) {
                        cmd = commands.get(cmdIdx);
                        log.info(cmd);
                        if ("!".equals(matcher.group(2))) {
                            this.toggleCommand(nvv, cmd);
                        } else {
                            this.buildACommand(nvv, cmd);
                        }
                    }
                } else {

                    AtomicInteger i = new AtomicInteger();
                    commands.stream()
                            .forEach(s -> this.log.info(i.getAndIncrement() + " " + s));
                }

                return TRUE;
            }
            return FALSE;
        }));
        commandHandlers.add(new CommandHandler("[:num: ]+", "100 101 102", "Builds the project(s) for the given project number.", (command) -> {

            Iterator<? extends Object> it = Arrays.stream(command.split(" ")).filter(s -> s.trim().length() > 0).map(s -> s.trim()).map(s -> {
                try {
                    return Integer.valueOf(s);
                } catch (Exception x) {
                }
                return s;
            }).iterator();

            Integer i = null;
            Object o = null;
            StringBuilder cmd = new StringBuilder();

            OUTER:
            while (it.hasNext()) {
                o = it.next();

                if (o instanceof Integer) {
                    if (i != null && cmd.length() == 0) {
                        this.buildAllCommands(i);
                    }
                    i = (Integer) o;
                    o = null;
                } else if (o instanceof String) {
                    if (i == null) {
                        return FALSE;
                    }
                    cmd.append(o.toString());
                    INNER:
                    while (it.hasNext()) {
                        o = it.next();
                        if (o instanceof String) {
                            cmd.append(' ').append(o.toString());
                        } else if (o instanceof Integer) {
                            i = (Integer) o;
                            break OUTER;
                        }
                    }

                    if (i != null && cmd.length() > 0) {

                        this.buildACommand(i, cmd.toString());
                        cmd = new StringBuilder();
                        i = null;
                    }

                }
            }

            if (i != null && cmd.length() == 0) {
                this.buildAllCommands(i);
            }

            log.fine("swallowing command");
            return TRUE;
        }));
    }

    private void writeHashes() throws IOException {

        try {
            FileOutputStream fos = new FileOutputStream(hashConfig.toFile());
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(this.hashes);
            fos.flush();
        } catch (IOException x) {
            log.info("write hashes " + x.getMessage());
        }
    }

    private void readHashes() throws IOException {

        if (Files.exists(hashConfig)) {
            try {
                FileInputStream fis = new FileInputStream(hashConfig.toFile());
                ObjectInputStream ois = new ObjectInputStream(fis);
                this.hashes = (Map<String, String>) ois.readObject();
            } catch (IOException x) {
                log.warning("read hashes " + x.getMessage());
            } catch (ClassNotFoundException x) {
                log.warning("cnf " + x.getMessage());
            }
        } else {
            log.info("no hashes found " + hashConfig.toAbsolutePath());
        }
    }

    private void stopBuild(NVV nvv) {
        log.info("stopping " + nvv.toString());

        if (this.processMap.containsKey(nvv)) {
            log.info("stopping process " + nvv.toString());
            try {
                stopProcess(this.processMap.get(nvv));

            } catch (Exception x) {
                log.log(Level.SEVERE, x.getMessage(), x);
            } finally {
                this.processMap.remove(nvv);
            }
        }

        if (this.futureMap.containsKey(nvv)) {
            log.info("cancelling future " + nvv.toString());
            try {
                Future future = this.futureMap.get(nvv);
                if (future == null) {
                    log.info("future gone" + nvv.toString());
                } else {
                    future.cancel(true);
                }
            } catch (CancellationException x) {
                log.info("cancelled future " + nvv.toString());
                //log.log(Level.SEVERE, x.getMessage(), x);
            } catch (RuntimeException x) {
                log.log(Level.INFO, "rtx - cancelled future " + x.getMessage(), x);
            } finally {
                log.info("removed " + nvv);
                this.futureMap.remove(nvv);
            }
        }

        Rvn.this.resetOut();
        log.info("stopped " + nvv.toString());
    }

    private void stopProcess(Process p) {
        //FIXME: java9  
        boolean java9 = true;
        if (p == null) {
            log.severe("process was null");
            return;
        }

        p.destroy();
        if (java9) {
            p.descendants().forEach(ph -> ph.destroy());
        }

        p.destroyForcibly();
        if (java9) {
            p.descendants().forEach(ph -> ph.destroyForcibly());
        }
        log.info("destroyed " + p.info());
    }

    private void buildIndex() {
        List<NVV> index = this.buildArtifact.keySet().stream().collect(Collectors.toList());

        Collections.sort(index, (NVV o1, NVV o2) -> o1.compareTo(o2));

        index.stream()
                .filter(nvv -> !buildIndex.contains(nvv))
                .forEach(nvv -> buildIndex.add(nvv));
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
                    log.fine("module configuration found for " + nvv);
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
            log.info("system property ${rvn.config} not set defaulting to " + name);
        } else {
            log.info("system property ${rvn.config} set " + base + ", resolving " + name);
        }
        URL configURL = null;
        if (Files.exists(FileSystems.getDefault().getPath(name))) {
            configURL = FileSystems.getDefault().getPath(name).toUri().toURL();
        } else {
            log.info("loading from classpath " + name);
            configURL = Rvn.class.getResource(name);
        }
        this.config = this.loadConfiguration(configURL);
    }

    private void addCommand(String projectKey, List<String> newCommandList) {
        if (log.isLoggable(Level.FINEST)) {
            log.finest("==" + projectKey + " " + newCommandList.toString() + " " + newCommandList.toString());
        }

        commands.compute(projectKey, (key, oldValue) -> {

            List<String> newList = new ArrayList<>();
            if (oldValue != null) {
                newList.addAll(
                        oldValue.stream()
                                .filter(v -> oldValue.contains(v))
                                .collect(Collectors.toList()));

                List<String> newCommands = newCommandList.stream().filter(v -> !oldValue.contains(v))
                        .collect(Collectors.toList());
                newList.addAll(newCommands);
            } else {
                newList.addAll(newCommandList);
            }
            return newList;
        });

        log.fine(projectKey + "  " + commands.get(projectKey).toString());
    }

    private boolean matchSafe(Path child) {
        try {
            return (Files.isDirectory(child) && matchDirectories(child)) || matchFiles(child);
        } catch (IOException ex) {
            log.log(Level.SEVERE, ex.getMessage(), ex);
            return false;
        }
    }

    private String join(String o, String v) {
        StringBuilder bob = new StringBuilder();
        if (o != null) {
            bob.append(o);
            bob.append(" ");
        }
        bob.append(v);
        return bob.toString();
    }

    private void buildACommand(Integer i, String command) {

        if (buildIndex.size() > i) {
            this.buildACommand(buildIndex.get(i), command);
        }
    }

    private void buildACommand(NVV nvv, String command) {
        this.lastChangeFile = null;

        if (command.equals("-")) {
            command = lastCommand.get(nvv);
            if (command == null) {
                command = locateCommand(nvv, null).iterator().next();
            }
        } else {
            lastCommand.put(nvv, command);
        }

        if (command.startsWith("!")) {
            command = command.substring(1);
        }

        String cmd = command;

        executor.submit(() -> {
            buildIt.doBuildTimeout(nvv, (nvv2, path) -> Arrays.asList(cmd));
        });
    }

    private void buildAllCommands(Integer i) {

        this.lastChangeFile = null;

        if (buildIndex.size() > i) {
            this.processChangeImmediatley(buildIndex.get(i));
        }
    }

    private Path resolve(WatchEvent<?> event, WatchKey key) {
        WatchEvent.Kind<?> kind = event.kind();

        if (kind == OVERFLOW) {
            return null;
        }

        WatchEvent<Path> ev = (WatchEvent<Path>) event;
        Path filename = ev.context();

        if (!keyPath.containsKey(key)) {
            return null;
        }
        Path child = keyPath.get(key).resolve(filename);
        return child;
    }

    private synchronized void processEvent(Path child, WatchEvent.Kind<?> kind) {
        try {

            if (child.equals(config)) {
                try {
                    log.info("config changed " + child);
                    this.reloadConfiguration();
                } catch (Throwable ex) {
                    log.log(Level.SEVERE, ex.getMessage(), ex);
                }
                return;
            }

            if (log.isLoggable(Level.INFO)) {
                log.info(String.format("kind %1$s %2$s ", kind, child));
            }

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
        } catch (AccessDeniedException ex) {
            log.log(Level.INFO, ex.getMessage());
        } catch (NoSuchFileException ex) {
            log.log(Level.INFO, ex.getMessage());
        } catch (Exception ex) {
            log.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    private void stopAllBuilds() {
        if (futureMap.isEmpty()) {
            log.warning("no future");
        } else {
            this.futureMap.keySet().forEach(nvv -> this.stopBuild(nvv));
        }

        q.truncate();
        //FIXME: executor.shutdownNow();
        //executor = new ScheduledThreadPoolExecutor(1);
    }

    private boolean isConfigFile(Path path) throws IOException {
        return Files.isSameFile(path, this.config)
                || this.configFileNames.stream().filter(s -> path.toAbsolutePath().toString().endsWith(s)).findFirst().isPresent();
    }

    private void createConfiguration(Path config) throws IOException {
        URL configURL = Thread.currentThread().getContextClassLoader().getResource("rvn.json");
        try (Reader reader = new InputStreamReader(configURL.openStream()); Writer writer = new FileWriter(config.toFile());) {
            while (reader.ready()) {
                writer.write(reader.read());
            }
            writer.flush();
        }
        log.info(String.format("written new configuration to " + ANSI_WHITE + "%1$s" + ANSI_RESET, config));
    }

    class BuildIt extends Thread {

        public BuildIt() {
            this.setName("BuildIt");
            this.setDefaultUncaughtExceptionHandler((e, t) -> {
                log.log(Level.WARNING, e.getName() + " " + t.getMessage(), t);
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
                        doBuildTimeout(nvv);
                    });
                }

            }
            log.info("builder exited - no more builds - restart");
            Thread.dumpStack();
        }

        private Lock lock = new ReentrantLock();

        public synchronized CompletableFuture<Boolean> doBuild(NVV nvv, BiFunction<NVV, Path, List<String>> commandLocator) {
            CompletableFuture<Boolean> result = null;

            Path dir = buildArtifact.get(nvv);
            if (dir == null) {
                log.info(String.format("no pom " + ANSI_CYAN + "%1$s" + ANSI_RESET, nvv));
                (result = new CompletableFuture<>()).complete(FALSE);
                return result;
            }

            //lock.lock();
            if (processMap.containsKey(dir)) {
                log.warning(String.format("already building " + ANSI_CYAN + "%1$s" + ANSI_RESET, nvv));
                (result = new CompletableFuture<>()).complete(FALSE);
                return result;
            }

            List<String> commandList = commandLocator.apply(nvv, dir);

            log.fine(nvv + " all commands " + commandList.toString());

            for (String command : commandList) {

                if (command.startsWith("!")) {
                    continue;
                } else if ("exit".equals(command)) {
                    System.exit(0);
                }

                boolean block = true;

                if (command.startsWith("-")) {
                    block = false;
                    command = command.substring(1);
                }

                CompletableFuture<Boolean> future = doBuild(nvv, command, dir);

                if (result == null) {
                    result = future;
                    Rvn.this.futureMap.put(nvv, result);
                }

                if (block) {
                    result.join();
                } else {
                    result.thenCombineAsync(future, (b, v) -> b && v);
                }
                result.exceptionally(x -> {
                    synchronized (q) {
                        q.truncate();
                    }
                    return true;
                });

            }

            if (result == null) {
                log.info("no commands to build");
            }

            if (result != null && result.getNow(Boolean.FALSE)) {
                dir.toFile().setLastModified(Instant.now().toEpochMilli());
            }

            return result;
        }

        private CompletableFuture<Boolean> doBuild(NVV nvv, String command, Path projectPath) {

            CompletableFuture<Boolean> result = new CompletableFuture<>();
            String mvnCmd = mvnCmdMap.getOrDefault(nvv, Rvn.this.mvnCmd);
            String mvnOpts = mvnOptsMap.getOrDefault(nvv, Rvn.this.mvnOpts);
            String javaHome = javaHomeMap.getOrDefault(nvv, Rvn.this.javaHome);
            String mvn = "mvn ";
            int commandIndex = calculateCommandIndex(nvv, command, projectPath);

            if (command.indexOf(mvn) >= 0 && command.indexOf("-f") == -1) {
                command = new StringBuilder(command).insert(command.indexOf(mvn) + mvn.length(), " -f %1$s ")
                        .toString();
            }

            if (agProjects.contains(nvv)) {
                command = new StringBuilder(command).insert(command.indexOf(mvn) + mvn.length(), " -N ")
                        .toString();
            }

            String cmd = String.format(command, ANSI_PURPLE + projectPath + ANSI_WHITE) + ANSI_RESET;

            if (command.isEmpty()) {
                log.info(String.format(
                        "already running " + ANSI_CYAN + "%1$s " + ANSI_WHITE + "%2$s" + ANSI_RESET, nvv, command));
                result.completeExceptionally(new Exception(String.format("already running " + ANSI_CYAN + "%1$s " + ANSI_WHITE + "%2$s" + ANSI_RESET, nvv, command)));
                return result;
            } else {
                log.fine(String.format("building " + ANSI_CYAN + "%1$s " + ANSI_WHITE + "%2$s" + ANSI_RESET, nvv,
                        cmd));
            }

            if (command.contains(mvn)) {
                command = command + " " + mvnArgsMap.getOrDefault(nvv, mvnArgs) + " ";
            }

            boolean daemon = daemonMap.getOrDefault(nvv, Rvn.this.daemon) && command.contains(mvn);

            if (daemon) {
                command = command.replace("mvn ", "-Drvn.mvn");
            } else {
                command = command.replace("mvn ", mvnCmd + " ");
            }

            final String commandFinal = String.format(command, projectPath);
            String[] args = commandFinal.split(" ");
            final List<String> filtered = Arrays.stream(args).filter(s -> s.trim().length() > 0).collect(Collectors.toList());

            String settings = settingsMap.getOrDefault(nvv, Rvn.this.settings);

            log.info(settings);
            if (settings != null) {
                if (Files.exists(Paths.get(settings))) {
                    filtered.add(1, "-s");
                    filtered.add(2, settings);

                } else {
                    log.warning("settings " + settings + " does not exist");
                }
            }

            final String[] filteredArgs = filtered.toArray(new String[filtered.size()]);

            Supplier<Boolean> task;
            task = new Supplier<Boolean>() {
                @Override
                public Boolean get() {
                    int exit = 0;
                    Callable<Path> archive = null;

                    Instant then = Instant.now();
                    Process p = null;
                    boolean timedOut = false;
                    Path lockFile = projectPath.getParent().resolve(".lock");
                    try {
                        lockFile = Files.createFile(lockFile);
                    } catch (IOException ex) {
                        Logger.getLogger(Rvn.class.getName()).log(Level.SEVERE, ex.getMessage());
                    }
                    try {
                        Rvn.this.thenStarted = Instant.now();
                        if (daemon) {
                            log.info("running in process " + filtered.toString());
                            archive = redirectOutput(nvv, commandIndex, null);
                            exit = Launcher.mainWithExitCode(filteredArgs);
                        } else {

                            log.info("spawning new process " + filtered.toString());
                            ProcessBuilder pb = new ProcessBuilder()
                                    .directory(projectPath.getParent().toFile())
                                    .command(filteredArgs);

                            archive = redirectOutput(nvv, commandIndex, pb);

                            pb.environment().putAll(System.getenv());

                            if (mvnOpts != null && !mvnOpts.trim().isEmpty()) {
                                pb.environment().put("MAVEN_OPTS", mvnOpts);
                            } else {
                                pb.environment().put("MAVEN_OPTS", removeDebug(pb.environment().getOrDefault("MAVEN_OPTS", "")));
                            }

                            if (log.isLoggable(Level.FINEST)) {
                                log.finest(pb.environment().entrySet().stream().map(e -> e.toString()).collect(Collectors.joining("\r\n", ",", "\r\n")));
                            }

                            if (javaHome != null && !javaHome.trim().isEmpty()) {
                                if (Files.exists(Paths.get(javaHome))) {
                                    pb.environment().put("JAVA_HOME", javaHome);
                                    String path = "Path";
                                    pb.environment().put(path, new StringBuilder(javaHome).append(File.separatorChar).append("bin").append(File.pathSeparatorChar).append(pb.environment().getOrDefault(path, "")).toString()); //FIXME:  maybe microsoft specific
                                    if (log.isLoggable(Level.FINE)) {
                                        log.fine(pb.environment().entrySet().stream().filter(e -> e.getKey().equals(path)).map(e -> e.toString()).collect(Collectors.joining(",", ",", ",")));
                                    }
                                } else {
                                    log.warning(String.format("JAVA_HOME %1$s does not exist, defaulting", javaHome));
                                }
                            }

                            p = pb.start();

                            processMap.put(projectPath, p);
                            CompletableFuture<Process> processFuture = p.onExit();

                            //lock.unlock();
                            if (!p.waitFor(timeoutMap.getOrDefault(nvv, timeout).toMillis(), TimeUnit.MILLISECONDS)) {
                                timedOut = true;
                                stopBuild(nvv);
                            }
                        }
                    } catch (InterruptedException ex) {
                        stopProcess(p);
                        throw new RuntimeException("build failed " + ex.getMessage(), ex);
                    } catch (Exception ex) {
                        log.log(Level.SEVERE, "build failed " + ex.getMessage(), ex);
                        throw new RuntimeException("build failed " + ex.getMessage(), ex);
                        //result.completeExceptionally(ex);
                    } finally {
                        Path output = archiveOutput(nvv, archive);
                        try {
                            boolean deleted = Files.deleteIfExists(lockFile);

                            log.info("lock file deleted " + deleted);
                        } catch (IOException ex) {
                            Logger.getLogger(Rvn.class.getName()).log(Level.SEVERE, null, ex);
                        }

                        if (daemon) {
                            resetOut();
                        } else {
                            processMap.remove(projectPath);
                            exit = p.exitValue();
                        }

                        log.info(String.format(
                                ANSI_GREEN + "%6$d " + ANSI_CYAN + "%1$s " + ANSI_RESET + ((exit == 0) ? ANSI_GREEN : ANSI_RED)
                                + (timedOut ? "TIMEDOUT" : (exit == 0 ? "PASSED" : "FAILED")) + " (%2$s)" + ANSI_RESET
                                + " with command " + ANSI_WHITE + "%3$s" + ANSI_YELLOW + " %4$s" + ANSI_RESET + "\n%5$s",
                                nvv, exit, commandFinal, Duration.between(then, Instant.now()), output != null ? output : "", buildIndex.indexOf(nvv)));

                        if (exit != 0) {
                            if (output != null) {
                                try {
                                    Rvn.this.writeFileToStdout(output);
                                } catch (IOException ex) {
                                    Logger.getLogger(Rvn.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            }
                            throw new RuntimeException("exit code " + exit);//Boolean.FALSE);

                        } else {
                            result.complete(Boolean.TRUE);
                            toBuild.remove(nvv);
                            failMap.remove(nvv);
                        }

                        Rvn.this.thenFinished = Instant.now();
                    }
                    throw new RuntimeException("not performed");
                }
            };

            return result.completeAsync(task, executor);
        }

        private void doBuildTimeout(NVV nvv) {
            this.doBuildTimeout(nvv, Rvn.this::locateCommand);
        }

        private void doBuildTimeout(NVV nvv, BiFunction<NVV, Path, List<String>> commandLocator) {
            Future future = null;
            try {
                //lock.unlock();
                future = doBuild(nvv, commandLocator);
                if (future == null) {
                    throw new RuntimeException("no result, add some buildCommands to .rvn.json config file");
                }

                future.get(timeoutMap.getOrDefault(nvv, timeout).toMillis(), TimeUnit.MILLISECONDS);// {
                log.finest("builder next");
                Thread.yield();

            } catch (CancellationException x) {
                log.warning(ANSI_GREEN + " " + Rvn.this.buildIndex.indexOf(nvv) + " " + ANSI_RED + "Cancelled" + ANSI_RESET + " " + nvv.toString()
                );
            } catch (CompletionException ex) {
                if (ex.getCause() instanceof InterruptedException) {
                    log.warning(ANSI_GREEN + " " + Rvn.this.buildIndex.indexOf(nvv) + " " + ANSI_RED + "ERROR" + ANSI_RESET + " build " + nvv.toString() + " interrupted");
                } else if (ex.getCause() instanceof TimeoutException) {
                    log.warning(ANSI_GREEN + " " + Rvn.this.buildIndex.indexOf(nvv) + " " + ANSI_RED + "ERROR" + ANSI_RESET + " build " + nvv.toString() + " timedout"
                    );
                } else {
                    log.warning(ANSI_GREEN + " " + Rvn.this.buildIndex.indexOf(nvv) + " " + ANSI_RED + "ERROR" + ANSI_RESET + " build " + nvv.toString() + " completed with " + ex.getClass().getSimpleName()
                            + " " + ex.getMessage() + Arrays.asList(ex.getStackTrace())
                            .subList(0, ex.getStackTrace().length).toString());
                    log.log(Level.SEVERE, ex.getMessage(), ex);
                }
            } catch (TimeoutException | RuntimeException | InterruptedException | ExecutionException ex) {
                log.warning(ANSI_GREEN + " " + Rvn.this.buildIndex.indexOf(nvv) + " " + ANSI_RED + "ERROR" + ANSI_RESET + " waiting for build " + nvv.toString() + " because " + ex.getClass().getSimpleName()
                        + " " + ex.getMessage() + Arrays.asList(ex.getStackTrace())
                        .subList(0, ex.getStackTrace().length).toString());
                log.log(Level.SEVERE, ex.getMessage(), ex);

            } finally {
                resetOut();
            }
        }

        private Callable<Path> redirectOutput(NVV nvv, int commandIndex, ProcessBuilder pb) throws IOException {
            File nf = null;
            File tf = null;
            boolean showOutput = Rvn.this.showOutputMap.getOrDefault(nvv, Rvn.this.showOutput);

            if (showOutput) {
                tf = null;
                if (pb != null) {
                    pb.inheritIO();
                }
                return () -> null;
            } else {
                tf = File.createTempFile("rvn-", formalizeFileName("-" + nvv.toString()) + ".out");

                if (reuseOutputMap.getOrDefault(nvv, reuseOutput)) {
                    nf = new File(tf.getParentFile(), formalizeFileName("rvn-" + nvv.toString() + "-" + commandIndex) + ".out");
                    if (!tf.renameTo(nf)) {
                        if (!nf.exists()) {
                            log.warning("rename file failed " + nf.getAbsolutePath());
                        }
                    }
                } else {
                    nf = tf;
                }

                if (pb == null) {
                    System.setOut(new PrintStream(new FileOutputStream(nf)));
                    System.setErr(new PrintStream(new FileOutputStream(nf)));

                } else {
                    pb.redirectOutput(nf);
                    pb.redirectError(nf);
                }

                log.info("redirecting to " + ANSI_WHITE + nf + ANSI_RESET);

                Path tp = Path.of(tf.toURI());
                Path np = Path.of(nf.toURI());
                return () -> {
                    logs.add(tp);
                    if (Files.exists(np) && Files.exists(tp) && !Files.isSameFile(tp, np)) {
                        Files.copy(np, tp, StandardCopyOption.REPLACE_EXISTING);
                    }

                    if (!showOutput) { // TODO make configurable
                        Rvn.this.failMap.put(nvv, tp);
                    }
                    return tp;
                };
            }

        }

        private Path archiveOutput(NVV nvv, Callable<Path> archive) {
            if (archive != null) {
                try {
                    Path call = archive.call();
                    log.finest("finally " + nvv.toString() + " " + call);
                    return call;
                } catch (Exception ex) {
                    log.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }
            return null;
        }

        private Map<NVV, List<String>> commandIndex = new HashMap<>();

        private int calculateCommandIndex(NVV nvv, String command, Path dir) {

            List<String> list = commandIndex.computeIfAbsent(nvv, nvv1 -> new ArrayList<>());

            if (!list.contains(command)) {
                list.add(command);
            }

            return list.indexOf(command);

        }

        private String formalizeFileName(String string) {
            return sanitizeOutName(string.replace(':', '-').replace('.', '_'));
        }

        private String sanitizeOutName(String nvvName) {
            return nvvName.replaceAll("[\\{\\}\\.\\$]", "");
        }

    }

    private void resetOut() {
        System.setOut(out);
        System.setErr(err);
    }

    private void writeFileToStdout(Path tp) throws FileNotFoundException, IOException {
        this.writeFileToStdout(tp.toFile());
    }

    private void writeFileToStdout(File tf) throws FileNotFoundException, IOException {
        if (tf != null) {
            try (FileReader reader = new FileReader(tf)) {
                char c[] = new char[1024];
                while (reader.ready()) {
                    int l = reader.read(c);
                    log.info(new String(c, 0, l));
                }
            }
            log.info(ANSI_WHITE + tf + ANSI_RESET);
        }
    }

    private final static Pattern testRe = Pattern.compile("^.*src.test.java.(.*Test).java$");

    private List<String> locateCommand(NVV nvv, Path path) {
        log.info("locating commands " + nvv.toString());
        List<String> commandList = new ArrayList<>();
        if (lastChangeFile != null && iFinder.isJava(lastChangeFile)) {
            String test = null;

            if (iFinder.isJavaTest(lastChangeFile)) {
                Matcher matcher = testRe.matcher(lastChangeFile.toAbsolutePath().toString());
                if (matcher.matches()) {
                    test = matcher.group(1).replaceAll("/".equals(File.separator) ? "/" : "\\\\", ".");
                } else {
                    try {
                        test = iFinder
                                .findImports(lastChangeFile).stream()
                                .filter(p -> iFinder.isJavaTest(p))
                                .map(p -> p.getFileName())
                                .map(p -> p.toString().substring(0, p.toString().length() - 5))
                                .collect(Collectors.joining(","));
                    } catch (IOException ex) {
                        Logger.getLogger(Rvn.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
                    }
                }
                //Pattern testRe = Pattern.compile("^.*src[:punct:]test[:punct:]java[:punct:](.*Test).java$");

                //if (command.indexOf(mvn) >= 0 && command.endsWith("-Dtest=")) {
                commandList.add(String.format("mvn test -Dtest=" + "%1$s", test));
                return commandList;
            }
        }

        commandList = commands.entrySet().stream().
                filter(e -> !e.getKey().equals("::"))
                .filter(e -> commandMatch(e.getKey(), nvv, path))
                .flatMap(e -> e.getValue().stream())
                .collect(Collectors.toList());

        if (commandList.isEmpty()) {
            if (commands.containsKey("::")) {
                commandList.addAll(commands.get("::"));
            } else {
                log.warning("No project commands or default commands, check your config has buildCommands for ::");
            }
        }

        return commandList;
    }

    private boolean commandMatch(String key, NVV nvv, Path path) {
        return matchNVV(nvv, key) || (path != null && path.toString().matches(key)) || nvv.toString().matches(key);
    }

    private boolean isPom(Path path) {
        return this.pomFileNames.stream().filter(s -> path.toAbsolutePath().toString().matches(s) || path.toAbsolutePath().toString().endsWith(s)).findFirst().isPresent();
    }

    private NVV findPom(Path path) throws Exception {
        // return buildPaths.get(path);
        List<Map.Entry<Path, NVV>> base = buildPaths.entrySet().stream().filter(e -> isBasePath(e.getKey(), path))
                .collect(Collectors.toList());

        log.fine("base: " + base.toString());

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
        log.fine(ANSI_PURPLE + "changed " + ANSI_CYAN + parent + " " + base + ANSI_RESET);
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

    public synchronized String toSHA1(Path value) throws IOException {
        md.update(Long.toString(Files.size(value)).getBytes());
        Files.lines(value).forEach(s -> md.update(s.getBytes()));
        return new String(md.digest());
    }

    private Path loadConfiguration(URL configURL) throws IOException, ScriptException, URISyntaxException {
        return this.loadConfiguration(configURL, null);
    }

    private Path loadConfiguration(URL configURL, NVV nvv) throws IOException, ScriptException, URISyntaxException {
        Path config = null;

        log.fine(String.format("trying configuration %1$s", configURL));

        if (configURL == null || configURL.toExternalForm().startsWith("jar:")) {
            config = Paths.get(userHome + File.separator + ".m2" + File.separator + "rvn.json");
            if (!Files.exists(config)) {
                log.info(String.format("%1$s doesn't exist, creating it from " + ANSI_WHITE + "%2$s" + ANSI_RESET,
                        config, configURL));
                createConfiguration(config);

            } else {
                log.info(String.format("%1$s exists", config));
            }

        } else {
            config = Paths.get(configURL.toURI());
            log.fine(String.format("trying configuration %1$s", configURL));
        }

        this.watch(config.getParent());
        log.info(String.format("loading configuration " + ANSI_WHITE + "%1$s" + ANSI_RESET, config));

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
        if (oNvv.isPresent()) {
            this.mvnOptsMap.remove(oNvv.get());
            this.mvnArgsMap.remove(oNvv.get());
            this.timeoutMap.remove(oNvv.get());
            this.mvnCmdMap.remove(oNvv.get());
            this.javaHomeMap.remove(oNvv.get());
            this.interruptMap.remove(oNvv.get());
            this.settingsMap.remove(oNvv.get());
            this.batchWaitMap.remove(oNvv.get());
            this.reuseOutputMap.remove(oNvv.get());
            this.commands.remove(oNvv.get().toString());
        }

        String key = null;
        if (result.hasMember(key = "mvnCmd")) {

            if (oNvv.isPresent()) {
                mvnCmdMap.put(oNvv.get(), (String) result.get(key));
            } else {
                this.mvnCmd = (String) result.get(key);
            }

        } else {
            if (!oNvv.isPresent()) {
                log.fine(System.getProperty("os.name"));
                this.mvnCmd = System.getProperty("os.name").regionMatches(true, 0, "windows", 0, 7) ? "mvn.cmd" : "mvn";
            }
        }

        log.fine(key + " " + mvnCmd + " because os.name=" + System.getProperty("os.name")
                + " override with mvnCmd: 'mvn' in config file");

        if (result.hasMember(key = "showOutput")) {
            this.showOutput = (Boolean) result.get(key);
            log.fine(key + " " + this.showOutput);
        }
        if (result.hasMember(key = "locations")) {
            ScriptObjectMirror v = (ScriptObjectMirror) result.get(key);
            locations.addAll(asArray(v).stream().filter(s -> !s.startsWith("!")).collect(toList()));
            log.fine(key + " " + locations.toString());

        }
        if (result.hasMember(key = "watchDirectories")) {
            ScriptObjectMirror v = (ScriptObjectMirror) result.get(key);
            if (v.hasMember("includes")) {
                this.matchDirIncludes.addAll(asArray((ScriptObjectMirror) v.getMember("includes")));
                log.fine(key + " includes " + matchDirIncludes.toString());
            }
            if (v.hasMember("excludes")) {
                this.matchDirExcludes.addAll(asArray((ScriptObjectMirror) v.getMember("excludes")));
                log.fine(key + " excludes " + matchDirExcludes.toString());
            }
        }

        if (result.hasMember(key = "activeFiles")) {
            ScriptObjectMirror v = (ScriptObjectMirror) result.get(key);
            if (v.hasMember("includes")) {
                this.matchFileIncludes.addAll(asArray((ScriptObjectMirror) v.getMember("includes")));
                log.fine(key + " includes " + matchFileIncludes.toString());
            }
            if (v.hasMember("excludes")) {
                this.matchFileExcludes.addAll(asArray((ScriptObjectMirror) v.getMember("excludes")));
                log.fine(key + " excludes " + matchFileExcludes.toString());
            }

        }

        if (result.hasMember(key = "activeArtifacts")) {
            ScriptObjectMirror v = (ScriptObjectMirror) result.get(key);
            if (v.hasMember("includes")) {
                this.matchArtifactIncludes.addAll(asArray((ScriptObjectMirror) v.getMember("includes")));
                log.fine(key + " includes " + matchArtifactIncludes.toString());
            }
            if (v.hasMember("excludes")) {
                this.matchArtifactExcludes.addAll(asArray((ScriptObjectMirror) v.getMember("excludes")));
                log.fine(key + " excludes " + matchArtifactExcludes.toString());
            }

        }

        if (result.hasMember(key = "buildCommands")) {
            ScriptObjectMirror v = (ScriptObjectMirror) result.get(key);
            if (v.isArray()) {
                List<String> commands = v.values().stream().map(e -> e.toString()).collect(Collectors.toList());
                if (oNvv.isPresent()) {
                    this.addCommand(oNvv.get().toString(), commands);
                }
            } else {
                v.entrySet().forEach(e -> this.addCommand(e.getKey(), optionalArray(e.getValue())));
            }

            if (oNvv.isPresent()) {
                log.fine("commands " + oNvv.toString() + " " + commands.get(oNvv.get().toString()));
            }

            log.fine(v.toString());
        }

        if (result.hasMember(key = "timeout")) {
            Integer v = (Integer) result.get(key);

            if (oNvv.isPresent()) {
                timeoutMap.put(oNvv.get(), Duration.ofSeconds(v));
            } else {
                timeout = Duration.ofSeconds(v);
            }
            log.fine(key + " " + v);
        }

        if (result.hasMember(key = "mvnOpts")) {
            String v = (String) result.get(key);
            if (oNvv.isPresent()) {
                mvnOptsMap.compute(oNvv.get(), (k, o) -> join(o, v));
            } else {
                mvnOpts = v.toString();
            }
            log.fine(key + " " + v);
        }

        if (result.hasMember(key = "javaHome")) {
            String v = (String) result.get(key);
            if (oNvv.isPresent()) {
                javaHomeMap.put(oNvv.get(), v.toString());
            } else {
                javaHome = v.toString();
            }
            log.fine(key + " " + v);
        }

        if (result.hasMember(key = "mvnArgs")) {
            Object v = (Object) result.get(key);
            String v2 = "";

            if (v instanceof String) {
                v2 = (String) v;
            } else if (v instanceof ScriptObjectMirror && ((ScriptObjectMirror) v).isArray()) {
                v2 = ((ScriptObjectMirror) v).values().stream().map(s -> s.toString()).filter(s -> !s.startsWith("!")).collect(Collectors.joining(" "));
            }

            String v3 = v2;
            if (oNvv.isPresent()) {
                mvnArgsMap.compute(oNvv.get(), (k, o) -> join(o, v3));
            } else {
                mvnArgs = v2.toString();
            }
            log.fine(key + " " + v);
        }

        if (result.hasMember(key = "daemon")) {
            Boolean v = (Boolean) result.get(key);
            if (oNvv.isPresent()) {
                daemonMap.put(oNvv.get(), v);
            } else {
                daemon = v;
            }
            log.fine(key + " " + v);
        }

        if (result.hasMember(key = "showOutput")) {
            Boolean v = (Boolean) result.get(key);
            if (oNvv.isPresent()) {
                showOutputMap.put(oNvv.get(), v);
            } else {
                showOutput = v;
            }
            log.fine(key + " " + v);
        }

        if (result.hasMember(key = "reuseOutput")) {
            Boolean v = (Boolean) result.get(key);
            if (oNvv.isPresent()) {
                reuseOutputMap.put(oNvv.get(), v);
            } else {
                reuseOutput = v;
            }
            log.fine(key + " " + v);
        }

        if (result.hasMember(key = "batchWait")) {
            Integer v = (Integer) result.get(key);
            if (oNvv.isPresent()) {
                batchWaitMap.put(oNvv.get(), Duration.ofSeconds(v));
            } else {
                batchWait = Duration.ofSeconds(v);
            }
            log.fine(key + " " + v);
        }

        if (result.hasMember(key = "interrupt")) {
            Boolean v = (Boolean) result.get(key);

            if (oNvv.isPresent()) {
                interruptMap.put(oNvv.get(), v);
            } else {
                interrupt = v;
            }
            log.fine(key + " " + v);
        }

        if (result.hasMember(key = "settings")) {
            String v = (String) result.get(key);

            if (oNvv.isPresent()) {
                settingsMap.put(oNvv.get(), v);
            } else {
                settings = v;
            }
            log.fine(key + " " + v);
        }

        if (oNvv != null) {
            log.fine("add project specific settings");

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

    public static List<Integer> rangeToIndex(String range) {
        slog.info(range);
        List<Integer> list = new ArrayList();

        String[] split = range.split(",");

        for (String s : split) {

            int b = 0;
            int e = 0;
            if (s.indexOf('-') > 0) {
                String[] r = s.split("-");
                switch (r.length) {
                    case 0:
                        list.add(-1);
                        break;
                    case 1:
                        b = Integer.parseInt(r[0]);
                        e = list.size();
                        break;
                    case 2:
                        b = Integer.parseInt(r[0]);
                        e = Integer.parseInt(r[1]);
                        break;
                    default:

                }
                for (int i = b; i <= e; i++) {
                    list.add(i);
                }

            } else {
                b = Integer.parseInt(s);
                list.add(b);
            }
        }
        return list;
    }

    private ScriptEngine getEngine() {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("JavaScript");
        return engine;

    }

    private void easterEgg() {
        log.info(new Scanner(Rvn.class.getResourceAsStream("/rvn.txt")).useDelimiter("\r").next());
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
            boolean applied = this.fun.apply(t);
            if (applied) {
                log.info("handled by " + verb);
            }
            return applied;
        }

    }

    public abstract class SimpleCommand implements Function< String, Boolean> {

        private final Pattern pattern;

        public SimpleCommand(String pattern) {
            this(Pattern.compile(pattern));
        }

        public SimpleCommand(Pattern pattern) {
            this.pattern = pattern;
        }

        @Override
        public Boolean apply(String command) {
            Matcher matcher = pattern.matcher(command);

            if (matcher.matches()) {
                List<String> matches = new ArrayList<>(matcher.groupCount());

                for (int i = 0; i <= matcher.groupCount(); i++) {
                    if (!matcher.group(i).isBlank()) {
                        matches.add(matcher.group(i));
                    }
                }

                log.finest(matcher.groupCount() + " " + matches.toString());

                try {
                    return this.configure(matches.iterator());
                } catch (Exception ex) {
                    log.severe(matcher.groupCount() + " " + matches.toString() + " " + ex.getClass().getName() + " " + ex.getMessage());
                    return FALSE;
                }
            }
            return FALSE;
        }

        public abstract Boolean configure(Iterator<String> i) throws Exception;

        public NVV forProjectIndex(String s) throws Exception {

            int buildIndex = Integer.parseInt(s);
            if (between(buildIndex, 0, Rvn.this.buildIndex.size())) {
                NVV nvv = Rvn.this.buildIndex.get(buildIndex);
                return nvv;
            } else {
                throw new Exception(buildIndex + " out of bounds ");
            }
        }

        private boolean between(int i, int min, int max) {
            return i >= min && i <= max;
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
