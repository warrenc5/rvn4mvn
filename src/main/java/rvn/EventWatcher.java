package rvn;

import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import static rvn.Ansi.ANSI_CYAN;
import static rvn.Ansi.ANSI_GREEN;
import static rvn.Ansi.ANSI_PURPLE;
import static rvn.Ansi.ANSI_RED;
import static rvn.Ansi.ANSI_RESET;
import static rvn.Ansi.ANSI_YELLOW;
import static rvn.Globals.buildArtifact;
import static rvn.Globals.config;
import static rvn.Globals.hashConfig;
import static rvn.Rvn.lockFileName;

/**
 *
 * @author wozza
 */
public class EventWatcher extends Thread {

    private Logger log = Logger.getLogger(this.getClass().getName());

    private static EventWatcher instance;

    static {
        instance = new EventWatcher();
    }

    public static EventWatcher getInstance() {
        return instance;
    }
    private final BuildIt buildIt;
    private final Project project;

    public EventWatcher() {
        this.buildIt = BuildIt.getInstance();
        this.project = Project.getInstance();
    }

    @Override
    public void run() {

        Map<Path, WatchEvent.Kind> events = new HashMap<>();
        long lastEvent = System.currentTimeMillis();

        log.info("watching for changes");
        while (this.isAlive()) {
            Thread.yield();

            if (System.currentTimeMillis() - lastEvent >= 400 && events.size() > 0) {
                System.err.print('.');

                events.entrySet().forEach(e -> processEvent(e.getKey(), e.getValue()));
                events.clear();
            }
            Project.getInstance().updateIndex();
            //log.fine(String.format("waiting.."));

            WatchKey key;

            try {
                key = PathWatcher.getInstance().watcher.poll(500, TimeUnit.MILLISECONDS);
                if (key == null) {
                    Thread.yield();
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

    public void processChangeImmediatley(NVV nvv) {
        processChange(nvv, buildArtifact.get(nvv), true);
    }

    public NVV processChange(NVV nvv) {
        processChange(nvv, buildArtifact.get(nvv), false);
        return nvv;
    }

    public void processChange(NVV nvv, Path path, boolean immediate) {
        log.info(String.format(
                "changed " + ANSI_CYAN + "%1$s" + ANSI_PURPLE + " %2$s" + ANSI_YELLOW + " %3$s" + ANSI_RESET,
                nvv.toString(), path, LocalTime.now().toString()));

        boolean updated = false;
        if (path != null) {
            updated = Hasher.getInstance().update(path);
        }

        if (updated || immediate) {
            buildIt.scheduleFuture(nvv, immediate);
        }
    }

    private Path resolve(WatchEvent<?> event, WatchKey key) {
        WatchEvent.Kind<?> kind = event.kind();

        if (kind == OVERFLOW) {
            return null;
        }

        WatchEvent<Path> ev = (WatchEvent<Path>) event;
        Path filename = ev.context();

        if (!PathWatcher.getInstance().keyPath.containsKey(key)) {
            return null;
        }
        Path child = PathWatcher.getInstance().keyPath.get(key).resolve(filename);
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

            if (child.equals(hashConfig)) {
                return;
            }

            if (child.endsWith(lockFileName)) {
                return;
            }

            if (child.equals(config) || ConfigFactory.getInstance().isConfigFile(child)) {
                try {
                    log.info(ANSI_RED + "config changed " + ANSI_GREEN + child + ANSI_RESET);
                    ConfigFactory.getInstance().reloadConfiguration();
                } catch (Throwable ex) {
                    log.log(Level.SEVERE, ex.getMessage(), ex);
                }
                return;
            }

            if (kind == ENTRY_DELETE) {
                Optional<WatchKey> cancelKey = PathWatcher.getInstance().keyPath.entrySet().stream()
                        .filter(e -> child.equals(e.getValue())).map(e -> e.getKey()).findFirst();
                if (cancelKey.isPresent()) {
                    cancelKey.get().cancel();
                }
                // TODO remove from buildArtifacts
                NVV nvv = Globals.buildPaths.get(child);
                if (nvv != null) {
                    Globals.buildArtifact.remove(nvv);
                    Globals.buildPaths.remove(child);
                }

            } else if (kind == ENTRY_CREATE) {
                PathWatcher.getInstance().registerPath(child);
                Project.getInstance().updateIndex();
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
