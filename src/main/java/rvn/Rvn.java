//TODO
// create aggregate pom for known projects to resolve deps
package rvn;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import static java.util.stream.Collectors.toList;
import static rvn.Ansi.ANSI_BOLD;
import static rvn.Ansi.ANSI_GREEN;
import static rvn.Ansi.ANSI_RESET;
import static rvn.Ansi.ANSI_WHITE;
import static rvn.Globals.thenFinished;
import static rvn.Globals.thenStarted;
import static rvn.Util.log;
import static rvn.Util.logX;

/**
 *
 * @author wozza
 */
public class Rvn extends Thread {

    /**
     * TODO: autoResume -rf module projects
     */
    private ImportFinder iFinder;
    private Logger log = Logger.getLogger(Rvn.class.getName());
    private static Logger slog = Logger.getLogger(Rvn.class.getName());

    private Map<String, String> hashes;
    private Path config;
    static boolean ee = false;

    public static final String lockFileName = ".lock.rvn";

    private static PrintStream out = System.out;
    private static PrintStream err = System.err;

    private static Rvn rvn = null;
    private final BuildIt buildIt;
    private final Project project;

    static {
        rvn = Rvn.getInstance();
        Globals.init();
    }

    public synchronized static Rvn getInstance() {
        return rvn;
    }

    public static void main(String[] args) throws Exception {
        Logger.getAnonymousLogger().warning(ANSI_BOLD + ANSI_GREEN + "Raven 4 Maven" + ANSI_RESET);
        Rvn rvn = new Rvn(args);
        rvn.init();
        //Globals.locations.addAll(Arrays.asList(args).stream().filter(s -> !s.startsWith("!")).collect(toList()));
        rvn.start();
        rvn.join();
        //Thread.sleep(5000);
        System.err.println(String.format("************** Exited ************************"));
    }

    private Duration timeout = Duration.ofSeconds(60);

    static ThreadFactory tFactory = new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {

            Thread t;

            t = new Thread(r);
            t.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread t, Throwable e) {
                    slog.log(Level.SEVERE, "+++++++" + e.getMessage(), e);
                }
            });
            return t;
        }
    };
    private CommandProcessor commandProcessor;

    public Rvn() throws Exception {

        this.setName("Rvn_Main");

        this.setDefaultUncaughtExceptionHandler((e, t) -> {
            log.log(Level.WARNING, e.getName() + " " + t.getMessage(), t);
        });

        System.out.print(ANSI_RESET + ANSI_RESET + ANSI_WHITE);
        project = Project.getInstance();
        buildIt = BuildIt.getInstance();
        this.setDaemon(true);
    }

    public Rvn(String[] args) throws Exception {
        this();
        Globals.configs.addAll(Arrays.asList(args).stream().map(arg -> Path.of(arg)).collect(toList()));
    }

    @Override
    public void run() {
        try {
            commandProcessor.processStdInOld();
        } catch (IOException ex) {
            log(log, ex);
        } catch (Exception ex) {
            logX(log, ex);
        }
        buildIt.start();
        getPathWatcher().start();
        EventWatcher.getInstance().start();

        while (this.isAlive()) {
            try {
                Thread.currentThread().sleep(500l);
            } catch (InterruptedException ex) {
            }
        }
    }

    public void init() throws Exception {
        this.commandProcessor = new CommandProcessor(this);

        log.info(System.getProperties().toString());
        thenFinished = Instant.now();
        thenStarted = Instant.now();

        Hasher.getInstance().readHashes();

        URL location = Rvn.class.getProtectionDomain().getCodeSource().getLocation();
        System.out.println("Code in  " + location.getFile());
        System.out.println("Running in " + Paths.get(".").toAbsolutePath().normalize().toString());
        Globals.locations.add(Paths.get(".").toAbsolutePath().normalize().toString());
        ConfigFactory.getInstance().loadDefaultConfiguration();

        project.updateIndex();


    }

    public Optional<String> getExtensionByStringHandling(String filename) {
        return Optional.ofNullable(filename).filter(f -> f.contains("."))
                .map(f -> f.substring(filename.lastIndexOf(".") + 1));
    }

    static void resetOut() {
        System.setOut(out);
        System.setErr(err);
    }

    static void writeFileToStdout(Path tp) throws FileNotFoundException, IOException {
        writeFileToStdout(tp.toFile());
    }

    static void writeFileToStdout(File tf) throws FileNotFoundException, IOException {
        if (tf != null) {
            try (FileReader reader = new FileReader(tf)) {
                char c[] = new char[1024];
                while (reader.ready()) {
                    int l = reader.read(c);
                    slog.info(new String(c, 0, l));
                }
            }
            slog.info(ANSI_WHITE + tf + ANSI_RESET);
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
                System.err.println("configured logging " + resource.toExternalForm());
            } else {
                System.err.println("no /logging.properties found in classpath");
            }
        } catch (URISyntaxException | IOException ex) {
        }
    }

    static void easterEgg() {
        slog.info(new Scanner(Rvn.class.getResourceAsStream("/rvn.txt")).useDelimiter("\r").next());
    }

    public static BuildIt getBuildIt() {
        return BuildIt.getInstance();
    }

    public static PathWatcher getPathWatcher() {
        return PathWatcher.getInstance();
    }

    public static Project getProject() {
        return Project.getInstance();
    }

    public static Config getGlobalConfig() {
        return Globals.config;
    }

    public CommandProcessor getCommandHandler() {
        return this.commandProcessor;
    }

}
