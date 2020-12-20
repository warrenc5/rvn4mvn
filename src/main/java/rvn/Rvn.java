//TODO
// create aggregate pom for known projects to resolve deps
package rvn;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Scanner;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.regex.PatternSyntaxException;
import static java.util.stream.Collectors.toList;
import java.util.stream.StreamSupport;
import jdk.internal.org.jline.reader.LineReader;
import jdk.internal.org.jline.reader.LineReaderBuilder;
import jdk.internal.org.jline.terminal.Terminal;
import jdk.internal.org.jline.terminal.TerminalBuilder;
import static rvn.Ansi.ANSI_BOLD;
import static rvn.Ansi.ANSI_GREEN;
import static rvn.Ansi.ANSI_RESET;
import static rvn.Ansi.ANSI_WHITE;
import static rvn.Ansi.ANSI_YELLOW;
import static rvn.Globals.buildArtifact;
import static rvn.Globals.buildIndex;
import static rvn.Globals.repoArtifact;
import static rvn.Util.between;

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
    //TODO make these paths
    List<CommandHandler> commandHandlers;

    private ImportFinder iFinder;
    private Logger log = Logger.getLogger(Rvn.class.getName());
    private static Logger slog = Logger.getLogger(Rvn.class.getName());

    private Map<String, String> hashes;
    private Path config;
    boolean ee = false;

    public static final String lockFileName = ".lock.rvn";
    private Map<NVV, String> lastCommand;

    private PrintStream out = System.out;
    private PrintStream err = System.err;

    private static Rvn rvn = null;

    static {
        rvn = Rvn.getInstance();
    }

    public synchronized static Rvn getInstance() {
        return rvn;
    }

    public static void main(String[] args) throws Exception {
        Logger.getAnonymousLogger().warning(ANSI_BOLD + ANSI_GREEN + "Raven 4 Maven" + ANSI_RESET);
        Rvn rvn = new Rvn();
        Globals.locations.addAll(Arrays.asList(args).stream().filter(s -> !s.startsWith("!")).collect(toList()));
        rvn.start();
        rvn.processStdInOld();
        System.out.println(String.format("************** Exited ************************"));
    }

    private Duration timeout = Duration.ofSeconds(60);

    private Instant thenFinished = null;
    private Instant thenStarted = null;
    private final BuildIt buildIt;

    ScheduledThreadPoolExecutor executor;
    private final Project project;

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

        init();

        buildIt = new BuildIt();
        buildIt.start();
        project = Project.getInstance();
    }

    public void init() throws Exception {

        log.info(System.getProperties().toString());
        thenFinished = Instant.now();
        thenStarted = Instant.now();

        lastCommand = new HashMap<>();

        Hasher.getInstance().readHashes();

        URL location = Rvn.class.getProtectionDomain().getCodeSource().getLocation();
        System.out.println("Code in  " + location.getFile());
        System.out.println("Running in " + Paths.get(".").toAbsolutePath().normalize().toString());
        Globals.locations.add(Paths.get(".").toAbsolutePath().normalize().toString());

        ConfigFactory.getInstance().loadDefaultConfiguration();

        commandHandlers = new ArrayList<>();
        //commandHandlers.addAll(new Commands().createCommandHandlers(this));
        project.updateIndex();

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

    private boolean needsBuild(NVV nvv) {
        Optional<Path> bPath = buildArtifact.entrySet().stream().filter(e -> e.getKey().equalsExact(nvv)).map(e -> e.getValue()).findAny();
        Optional<Path> rPath = repoArtifact.entrySet().stream().filter(e -> e.getKey().equalsExact(nvv)).map(e -> e.getValue()).findAny();

        if (bPath.isPresent() && rPath.isEmpty()) {
            log.finest("missing " + nvv.toString() + " " + bPath + " " + rPath);
            return true;
        } else if (bPath.isPresent() && rPath.isPresent()) {
            return !compareHashes(bPath.get(), rPath.get());
        } else {
            return false;
        }
    }

    public Optional<String> getExtensionByStringHandling(String filename) {
        return Optional.ofNullable(filename).filter(f -> f.contains("."))
                .map(f -> f.substring(filename.lastIndexOf(".") + 1));
    }

    private boolean isConfigFile(Path path) throws IOException {
        return (Files.exists(path) && Files.isSameFile(path, this.config))
                || Globals.configFileNames.stream().filter(s -> path.toAbsolutePath().toString().endsWith(s)).findFirst().isPresent();
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

    private void resetOut() {
        System.setOut(out);
        System.setErr(err);
    }

    void writeFileToStdout(Path tp) throws FileNotFoundException, IOException {
        this.writeFileToStdout(tp.toFile());
    }

    void writeFileToStdout(File tf) throws FileNotFoundException, IOException {
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

    private void easterEgg() {
        log.info(new Scanner(Rvn.class.getResourceAsStream("/rvn.txt")).useDelimiter("\r").next());
    }

    public NVV forProjectIndex(String s) throws Exception {
        int buildIndex = Integer.parseInt(s);
        return forProjectIndex(buildIndex);
    }

    public NVV forProjectIndex(Integer s) throws Exception {

        if (between(s, 0, buildIndex.size())) {
            NVV nvv = buildIndex.get(s);
            return nvv;
        } else {
            throw new Exception(s + " out of bounds ");
        }
    }
}
