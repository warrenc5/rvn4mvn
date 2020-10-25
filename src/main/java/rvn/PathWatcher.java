package rvn;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import org.xml.sax.SAXException;
import static rvn.Ansi.ANSI_GREEN;
import static rvn.Ansi.ANSI_RED;
import static rvn.Ansi.ANSI_RESET;
import static rvn.Ansi.ANSI_WHITE;
import static rvn.Globals.buildArtifact;
import static rvn.Globals.buildIndex;
import static rvn.Globals.buildPaths;
import static rvn.Globals.lastBuild;
import static rvn.Globals.lastUpdate;
import static rvn.Globals.locations;
import static rvn.Globals.paths;
import static rvn.Globals.projects;
import static rvn.Globals.rehash;
import static rvn.Globals.toBuild;

/**
 *
 * @author wozza
 */
public class PathWatcher extends Thread {

    private Logger log = Logger.getLogger(this.getClass().getName());
    private static Logger slog = Logger.getLogger(Rvn.class.getName());

    int depth = 0;
    int maxDepth = 16;
    int lastDepth = 0;

    public static Set<WatchKey> keys;
    public Map<WatchKey, Path> keyPath;

    public final WatchService watcher;

    private static PathWatcher instance;
    private Instant thenFinished = null;
    private Instant thenStarted = null;

    static {
        instance = new PathWatcher();
    }

    public static PathWatcher getInstance() {
        return instance;
    }
    private final ConfigFactory configFactory;

    public PathWatcher() {
        watcher = FileSystems.getDefault().newWatchService();
        configFactory = ConfigFactory.getInstance();

        keyPath = new HashMap<>();
        keys = new HashSet<>(locations.size());
    }

    public Optional<FileTime> watchRecursively(Path dir) {
        try {
            Thread.currentThread().yield();
        } catch (Exception x) {
        }

        depth++;
        if (depth > maxDepth && depth > lastDepth) {
            log.warning(dir + " is " + depth + " deep");
        } else {
        }
        lastDepth = depth;
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

    public void run() {
        //scan();
    }

    public void scan(List<String> locations) {
        locations.stream().forEach(configFactory::findConfiguration);
        log.fine("locations :" + locations.toString().replace(',', '\n'));
        locations.stream().forEach(this::registerPath);

        log.fine("buildSet :" + buildPaths.toString().replace(',', '\n'));
        ArrayList watchSet;
        watchSet = new ArrayList(this.keyPath.values());
        Collections.sort(watchSet);
        log.fine("watchSet :" + watchSet.toString().replace(',', '\n'));

        this.rebuildIndex();

        Hasher.getInstance().writeHashes();

        Project.getInstance().resolveVersions();

        rehash();

        BuildIt.getInstance().calculateToBuild();

        this.iFinder = new ImportFinder(this.paths);

        watchSummary();
    }

    private void rebuildIndex() {
        List<NVV> index = buildArtifact.keySet().stream().collect(Collectors.toList());

        Collections.sort(index, (NVV o1, NVV o2) -> o1.compareTo(o2));

        index.stream()
                .filter(nvv -> !buildIndex.contains(nvv))
                .forEach(nvv -> buildIndex.add(nvv));
    }

    public void registerPath(String uri) {
        Path dir = Paths.get(uri);
        log.info(String.format(ANSI_WHITE + "watching %1$s" + ANSI_RESET, dir));
        registerPath(dir);
    }

    public void registerPath(Path path) {
        try {
            log.finest(path.toString());
            if (Files.isDirectory(path)) {
                try (Stream<Path> stream = Files.list(path)) {
                    stream.sorted().filter(child -> matchSafe(child)).forEach(this::registerPath);
                }
            } else if (path.toFile().toString().endsWith(".pom")) {
                Optional<FileTime> lastest = PathWatcher.getInstance().watchRecursively(path.getParent().getParent()); // watch all versions

                Optional<NVV> oNvv = Project.getInstance().processPom(path);
                if (oNvv.isPresent() && lastest.isPresent()) {
                    lastBuild.put(oNvv.get(), lastest.get());
                }
            } else if (path.endsWith("pom.xml")) {
                Optional<NVV> oNvv = Project.getInstance().processPom(path);
                if (oNvv.isPresent()) {
                    Path parent = path.getParent();
                    Optional<FileTime> lastest = watchRecursively(parent);
                    lastUpdate.put(oNvv.get(), lastest.get());
                } else {
                    // logger.warning(String.format(ANSI_WHITE + "failed %1$s" + ANSI_RESET, path));
                }
            } else {
                paths.add(path);
            }
        } catch (IOException | SAXException | XPathExpressionException | ParserConfigurationException ex) {
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
            log.finest(path.toString());
            if (Files.isDirectory(path)) {
                try (Stream<Path> stream = Files.list(path)) {
                    stream.sorted().filter(child -> matchSafe(child)).forEach(this::registerPath);
                }
            } else if (path.toFile().toString().endsWith(".pom")) {
                Optional<FileTime> lastest = watchRecursively(path.getParent().getParent()); // watch all versions

                Optional<NVV> oNvv = Project.getInstance().processPom(path);
                if (oNvv.isPresent() && lastest.isPresent()) {
                    lastBuild.put(oNvv.get(), lastest.get());
                }
            } else if (path.endsWith("pom.xml")) {
                Optional<NVV> oNvv = Project.getInstance().processPom(path);
                if (oNvv.isPresent()) {
                    Path parent = path.getParent();
                    Optional<FileTime> lastest = watchRecursively(parent);
                    lastUpdate.put(oNvv.get(), lastest.get());
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

    public void watchSummary() {
        Duration duration = Duration.between(thenFinished, Instant.now());
        log.info(String.format(ANSI_WHITE + "watching %1$s projects, %2$s builds, %3$s are out of date,  %4$s keys - all in %5$s" + ANSI_RESET,
                projects.size(), buildPaths.size(), toBuild.size(), keys.size(), duration.toString()));
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

            if (log.isLoggable(Level.INFO)) {
                log.info(String.format("kind %1$s %2$s ", kind, child));
            }
            if (child == null) {
                return;
            }

            if (child.equals(Config.hashConfig)) {
                return;
            }

            if (child.endsWith(Config.lockFileName)) {
                return;
            }

            if (child.equals(config) || this.isConfigFile(child)) {
                try {
                    log.info(ANSI_RED + "config changed " + ANSI_GREEN + child + ANSI_RESET);
                    this.reloadConfiguration();
                } catch (Throwable ex) {
                    log.log(Level.SEVERE, ex.getMessage(), ex);
                }
                return;
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
                watcher.registerPath(child);
                updateIndex();
            } else if (kind == ENTRY_MODIFY) {
                Project.getInstance().processPath(child);
            }

        } catch (AccessDeniedException ex) {
            log.log(Level.INFO, ex.getMessage());
        } catch (NoSuchFileException ex) {
            log.log(Level.INFO, ex.getMessage());
        } catch (Exception ex) {
            log.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

}
