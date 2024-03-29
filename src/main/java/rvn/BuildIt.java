package rvn;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.toList;
import org.codehaus.plexus.classworlds.launcher.Launcher;
import org.jgrapht.alg.TransitiveClosure;
import org.jgrapht.traverse.DepthFirstIterator;
import static rvn.Ansi.ANSI_BOLD;
import static rvn.Ansi.ANSI_CYAN;
import static rvn.Ansi.ANSI_GREEN;
import static rvn.Ansi.ANSI_PURPLE;
import static rvn.Ansi.ANSI_RED;
import static rvn.Ansi.ANSI_RESET;
import static rvn.Ansi.ANSI_WHITE;
import static rvn.Ansi.ANSI_YELLOW;
import static rvn.Globals.agProjects;
import static rvn.Globals.buildArtifact;
import static rvn.Globals.buildIndex;
import static rvn.Globals.buildPaths;
import static rvn.Globals.config;
import static rvn.Globals.failMap;
import static rvn.Globals.futureMap;
import static rvn.Globals.lastChangeFile;
import static rvn.Globals.lastCommand;
import static rvn.Globals.logs;
import static rvn.Globals.parent;
import static rvn.Globals.processMap;
import static rvn.Globals.projects;
import static rvn.Globals.toBuild;

/**
 *
 * @author wozza
 */
public class BuildIt extends Thread {

    private Logger log = Logger.getLogger(this.getClass().getName());
    private static Logger slog = Logger.getLogger(Rvn.class.getName());
    private static BuildIt instance;
    private Deque<NVV> q = new ConcurrentLinkedDeque<>();
    public ScheduledThreadPoolExecutor executor;

    static {
        instance = new BuildIt();
    }

    public static BuildIt getInstance() {
        return instance;
    }

    private final ImportFinder iFinder;

    private Graph g;

    private boolean haveMvnD;

    public BuildIt() {
        this.setName("BuildIt");
        executor = new ScheduledThreadPoolExecutor(10, Rvn.tFactory);
        this.iFinder = ImportFinder.getInstance();

        this.setDefaultUncaughtExceptionHandler((e, t) -> {
            log.log(Level.WARNING, e.getName() + " " + t.getMessage(), t);
        });

        detectMavenDaemon();
    }

    void calculateToBuild() {

        buildPaths.entrySet().stream().forEach(e -> {
            NVV nvv = e.getValue();
            if (Project.getInstance().needsBuild(nvv)) {
                if (!toBuild.contains(nvv)) {
                    toBuild.add(nvv);
                }
            } else {
                log.info(String.format("skipping build - hashes match " + ANSI_GREEN + "%1$d " + ANSI_CYAN + " %2$s " + ANSI_RESET, buildIndex.indexOf(nvv), nvv.toString()));
            }
        });

        Collections.sort(toBuild, new Comparator<NVV>() {
            @Override
            public int compare(NVV o1, NVV o2) {
                return Long.compare(buildIndex.indexOf(o1), buildIndex.indexOf(o2));
            }
        });

        toBuild.forEach(nvv -> {
            log.info(String.format("consider building " + ANSI_GREEN + "%1$d " + ANSI_CYAN + " %2$s " + ANSI_RESET, buildIndex.indexOf(nvv), nvv.toString()));
        });
    }

    void buildACommand(Integer i, String command) {

        if (buildIndex.size() > i) {
            this.buildACommand(buildIndex.get(i), command);
        }
    }

    void buildACommand(NVV nvv, String command) {
        lastChangeFile = null;

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
            doBuildTimeout(nvv, (nvv2, path) -> Arrays.asList(cmd));
        });
    }

    void buildAllCommands(NVV nvv) {
    }

    void buildAllCommands(Integer i) {

        lastChangeFile = null;

        /*if (buildIndex.size() > i) {
            EventWatcher.getInstance().processChangeImmediatley(buildIndex.get(i));
        }*/
    }

    void buildACommand(NVV nvv, Integer i) {
        var config = Config.of(nvv);
        var commands = config.commands.get(nvv.toString());
        String cmd = commands.get(i);
        if (cmd.startsWith(Globals.INVERSE)) {
            cmd = cmd.substring(1);
        }
        buildACommand(nvv, cmd);
    }

    private final static Pattern testRe = Pattern.compile("^.*src.test.java.(.*Test).java$");

    List<String> locateCommand(NVV nvv, Path path) {
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
                        log.log(Level.SEVERE, ex.getMessage(), ex);
                    }
                }
                //Pattern testRe = Pattern.compile("^.*src[:punct:]test[:punct:]java[:punct:](.*Test).java$");

                //if (command.indexOf(mvn) >= 0 && command.endsWith("-Dtest=")) {
                commandList.add(String.format("mvn test -Dtest=" + "%1$s", test));
                return commandList;
            }
        }
        Map<String, List<String>> commands = ConfigFactory.getInstance().getConfig(nvv).commands;
        commandList = commands.entrySet().stream().
                filter(e -> !e.getKey().equals("::"))
                .filter(e -> commandMatch(e.getKey(), nvv, path))
                .flatMap(e -> e.getValue().stream())
                .collect(Collectors.toList());
        log.warning("commands " + commandList);

        if (commandList.isEmpty()) {
            if (Globals.config.commands.containsKey("::")) {
                commandList.addAll(Globals.config.commands.get("::"));
            } else {
                log.warning("No project commands or default commands, check your config has buildCommands for ::");
            }
        }

        return commandList;
    }

    private boolean commandMatch(String key, NVV nvv, Path path) {
        return Project.getInstance().matchNVV(nvv, key) || (path != null && path.toString().matches(key)) || nvv.toString().matches(key);
    }

    void stopAllBuilds() {
        if (futureMap.isEmpty()) {
            log.warning("no future");
        } else {
            futureMap.keySet().forEach(nvv -> this.stopBuild(nvv));
        }
        q.clear();
        g.clear();
    }

    public synchronized void scheduleFuture(NVV nvv, boolean immediate) {
        Duration batchWait = immediate ? Duration.ZERO : ConfigFactory.getInstance().getConfig(nvv).batchWaitMap.getOrDefault(nvv, config.batchWait);

        futureMap.computeIfPresent(nvv, (nvv1, future) -> {
            Boolean interrupt = config.interruptMap.getOrDefault(nvv1, config.interrupt);
            if (future.isDone()) {
                return null;
            } else if (interrupt) {
                if (future instanceof ScheduledFuture) {
                    log.fine("already scheduled " + nvv1.toString());
                    return null;
                } else {
                    synchronized (futureMap) {
                        stopBuild(nvv);
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
                    build(nvv);
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

    synchronized void build(NVV... nvvs) {
        initGraph();
        for (NVV nvv : nvvs) {
            NVV pNvv = parent.get(nvv);

            if (pNvv != null && Project.getInstance().needsBuild(pNvv)) {
                //build(pNvv);
            }
            buildDeps(nvv, true);
        }
        q = g.reduceG2Q();
    }

    public synchronized void buildDeps(NVV nvv, boolean root) {
        log.info("requested to build " + nvv.toString());
        if (g == null) {
            initGraph();
        }
        synchronized (g) {
            try {
                if (!root && g.containsVertex(nvv)) {
                    return;
                }

                NVV pNvv = Globals.parent.get(nvv);
                if (pNvv != null) { //AND parent is in buildArtifacts
                    //buildDeps(pNvv);
                }

                List<NVV> dependants = getDependants(nvv);

                g.addVertex(nvv);
                dependants.stream().filter(n -> Project.getInstance().matchNVV(n))
                        .forEach(nvv2 -> {
                            if (!nvv2.equals(nvv)) {
                                //log.info("dep build " + nvv2.toString() + " " + nvv.toString());
                                if (!g.containsVertex(nvv2)) {
                                    g.addVertex(nvv2);
                                }
                                g.addEdge(nvv, nvv2);
                                buildDeps(nvv2, false);
                            }
                        });

            } catch (RuntimeException x) {
                log.log(Level.WARNING, String.format("%1$s %2$s", nvv.toString(), x.getMessage()), x);

            }
        }
    }

    public synchronized List<NVV> getDependants(NVV nvv) {
        return projects.entrySet().stream()
                //k.filter(e -> pNvv == null || (pNvv != null && !e.getKey().equals(pNvv)))
                .flatMap(e -> Project.getInstance().projectDepends(nvv))
                .filter(nvv3 -> Globals.buildArtifact.containsKey(nvv3))
                //.filter(nvv4 -> Project.getInstance().needsBuild(nvv4))
                .distinct().collect(toList());
    }

    public void run() {
        log.info("waiting for builds");
        while (this.isAlive()) {
            Thread.currentThread().yield();
            try {
                Thread.currentThread().sleep(200l);

            } catch (InterruptedException ex) {
                Logger.getLogger(BuildIt.class
                        .getName()).log(Level.SEVERE, null, ex);
            }

            synchronized (q) {
                if (!q.isEmpty()) {
                    try {
                        NVV nvv = q.peek();
                        doBuildTimeout(nvv);
                    } finally {

                        try {
                            q.pop();
                            log.info(q.toString());
                        } catch (java.util.NoSuchElementException xNSE) {
                            log.warning("q gone");
                        }
                    }
                }
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
            (result = new CompletableFuture<>()).complete(Boolean.FALSE);
            return result;
        }
        //lock.lock();
        if (processMap.containsKey(dir)) {
            log.warning(String.format("already building " + ANSI_CYAN + "%1$s" + ANSI_RESET, nvv));
            (result = new CompletableFuture<>()).complete(Boolean.FALSE);
            return result;
        }
        List<String> commandList = commandLocator.apply(nvv, dir);
        log.fine(nvv + " all commands " + commandList.toString());
        for (String command : commandList) {
            if (command.startsWith("!")) {
                continue;
            } else if ("exit".equals(command)) { //FIXME move this to commands
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
                futureMap.put(nvv, result);
            }
            if (block) {
                result.join();
            } else {
                result.thenCombineAsync(future, (b, v) -> b && v);
            }
            result.exceptionally((x) -> { //FIXME user handleAsync from doBuild
                g.clear();
                return true;
            });
        }
        if (result != null) { //WTF? this is the chained result 
            result.whenComplete((r, t) -> { //FIXME user handleAsync from doBuild
                if (r == true && t != null) {
                    buildDeps(nvv, true);
                }
            });

            result.exceptionally((x) -> { //FIXME user handleAsync from doBuild
                g.clear();
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
        Config config = ConfigFactory.getInstance().getConfig(nvv);
        String mvnCmd = config.mvnCmdMap.getOrDefault(nvv, Globals.config.mvnCmd);
        String mvnOpts = config.mvnOptsMap.getOrDefault(nvv, Globals.config.mvnOpts);
        String javaHome = config.javaHomeMap.getOrDefault(nvv, Globals.config.javaHome);
        String mvn = "mvn ";
        boolean directMvn = false;
        int commandIndex = calculateCommandIndex(nvv, command, projectPath);
        if (command.indexOf(mvn) >= 0 && command.indexOf("-f") == -1) {
            command = new StringBuilder(command).insert(command.indexOf(mvn) + mvn.length(), " -f %1$s ").toString();
        }

        String cmd = String.format(command, ANSI_PURPLE + projectPath + ANSI_WHITE) + ANSI_RESET;
        if (command.isEmpty()) {
            log.info(String.format("already running " + ANSI_CYAN + "%1$s " + ANSI_WHITE + "%2$s" + ANSI_RESET, nvv, command));
            result.completeExceptionally(new Exception(String.format("already running " + ANSI_CYAN + "%1$s " + ANSI_WHITE + "%2$s" + ANSI_RESET, nvv, command)));
            return result;
        } else {
            log.fine(String.format("building " + ANSI_CYAN + "%1$s " + ANSI_WHITE + "%2$s" + ANSI_RESET, nvv, cmd));
        }
        if (command.contains(mvn)) {
            directMvn = true;
            command = command + " " + config.mvnArgsMap.getOrDefault(nvv, config.mvnArgs) + " ";
        }

        boolean daemon = config.daemonMap.getOrDefault(nvv, config.daemon) && command.contains(mvn);
        if (directMvn) {
            if (directMvn && agProjects.contains(nvv)) {
                command = new StringBuilder(command).insert(command.indexOf(mvn) + mvn.length(), " -N ").toString();
            }

            if (daemon) {
                command = command.replace("mvn ", "-Drvn.mvn");
            } else {

                if (haveMvnD) {
                    command = command.replace("mvn ", "mvnd "); //FIXME: for windows
                } else {
                    command = command.replace("mvn ", mvnCmd + " ");
                }
            }
        }
        final String commandFinal = String.format(command, projectPath);
        String[] args = commandFinal.split(" ");
        final List<String> filtered = Arrays.stream(args).filter((s) -> s.trim().length() > 0).collect(Collectors.toList());
        String settings = config.settingsMap.getOrDefault(nvv, Globals.config.settings);

        if (settings == null) {
            Path settingsPath = projectPath.getParent().resolve("settings.xml");
            if (Files.exists(settingsPath)) {
                log.info("auto detected settings " + settingsPath.toString());
                settings = "settings.xml";
            }
        }

        if (settings != null) {
            log.info(settings);
            Path settingsPath = projectPath.getParent().resolve(Paths.get(settings));
            if (command.startsWith(mvn) && Files.exists(settingsPath)) {
                filtered.add(1, "-s");
                filtered.add(2, settingsPath.toAbsolutePath().toString());
            } else {
                log.warning("settings " + settings + " does not exist");
            }
        }
        if (nvv.isParent) {
            filtered.add("-N");
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
                Path lockFile = projectPath.getParent().resolve(Rvn.lockFileName);
                var timeout = config.timeoutMap.getOrDefault(nvv, config.timeout);

                try {
                    lockFile = Files.createFile(lockFile);
                } catch (IOException ex) {
                    log.log(Level.SEVERE, ex.getMessage());
                }
                try {
                    Globals.thenStarted = Instant.now();
                    if (daemon) {
                        log.info(String.format("running in process " + ANSI_WHITE + " %1$s" + ANSI_YELLOW + " %2$s" + ANSI_RESET, filtered.toString(), timeout.toString()));
                        archive = redirectOutput(nvv, commandIndex, null);
                        exit = Launcher.mainWithExitCode(filteredArgs);
                    } else {
                        log.info(String.format("spawning new process " + ANSI_BOLD + ANSI_WHITE + " %1$s" + ANSI_RESET + ANSI_YELLOW + " %2$s" + ANSI_RESET, filtered.toString(), timeout.toString()));
                        ProcessBuilder pb = new ProcessBuilder().directory(projectPath.getParent().toFile()).command(filteredArgs);
                        archive = redirectOutput(nvv, commandIndex, pb);
                        pb.environment().putAll(System.getenv());

                        if (!config.env.isEmpty()) {
                            log.info("env overrides " + config.env.toString());
                            pb.environment().putAll(config.env);
                            log.finest("env " + pb.environment().toString());
                        }

                        if (mvnOpts != null && !mvnOpts.trim().isEmpty()) {
                            pb.environment().put("MAVEN_OPTS", mvnOpts);
                        } else {
                            pb.environment().put("MAVEN_OPTS", removeDebug(pb.environment().getOrDefault("MAVEN_OPTS", "")));
                        }
                        if (log.isLoggable(Level.FINEST)) {
                            log.finest(pb.environment().entrySet().stream().map((e) -> e.toString()).collect(Collectors.joining("\r\n", ",", "\r\n")));
                        }
                        if (javaHome != null && !javaHome.trim().isEmpty()) {
                            if (Files.exists(Paths.get(javaHome))) {
                                pb.environment().put("JAVA_HOME", javaHome);
                                String path = "Path";
                                pb.environment().put(path, new StringBuilder(javaHome).append(File.separatorChar).append("bin").append(File.pathSeparatorChar).append(pb.environment().getOrDefault(path, "")).toString()); //FIXME:  maybe microsoft specific
                                if (log.isLoggable(Level.FINE)) {
                                    log.fine(pb.environment().entrySet().stream().filter((e) -> e.getKey().equals(path)).map((e) -> e.toString()).collect(Collectors.joining(",", ",", ",")));
                                }
                            } else {
                                log.warning(String.format("JAVA_HOME %1$s does not exist, defaulting", javaHome));
                            }
                        }
                        p = pb.start();
                        Globals.processMap.put(projectPath, p);
                        CompletableFuture<Process> processFuture = p.onExit();
                        //lock.unlock();
                        if (!p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
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
                        log.log(Level.SEVERE, null, ex);
                    }
                    if (daemon) {
                        Rvn.resetOut();
                    } else {
                        processMap.remove(projectPath);
                        exit = p.exitValue();
                    }
                    log.info(String.format((exit == 0 ? ANSI_GREEN : ANSI_RED) + "%6$d " + ANSI_CYAN + "%1$s " + ANSI_RESET + ((exit == 0) ? ANSI_GREEN : ANSI_RED) + (timedOut ? "TIMEDOUT" : (exit == 0 ? "PASSED" : "FAILED")) + " (%2$s)" + ANSI_RESET + " with command " + ANSI_BOLD + ANSI_WHITE + "%3$s" + ANSI_RESET + ANSI_YELLOW + " %4$s" + ANSI_RESET + (exit == 0 ? ANSI_GREEN : ANSI_RED) + "\n%5$s " + ANSI_PURPLE + "\n%6$s %7$s %8$s" + ANSI_RESET, nvv, exit, commandFinal, Duration.between(then, Instant.now()), output != null ? output : "", buildIndex.indexOf(nvv), nvv.path, (nvv.parent != null ? nvv.parent.path : "")));
                    if (exit != 0) {
                        if (output != null) {
                            try {
                                Rvn.writeFileToStdout(output);
                            } catch (IOException ex) {
                                log.log(Level.SEVERE, null, ex);
                            }
                        }
                        throw new RuntimeException("exit code " + exit); //Boolean.FALSE);
                    } else {
                        result.complete(Boolean.TRUE);
                        toBuild.remove(nvv);
                        failMap.remove(nvv);
                    }
                    Globals.thenFinished = Instant.now();
                }
                throw new RuntimeException("not performed");
            }
        };

        return result.completeAsync(task, executor); //FIXME use handle with handler
    }

    private void doBuildTimeout(NVV nvv) {
        doBuildTimeout(nvv, BuildIt.this::locateCommand);
    }

    void doBuildTimeout(NVV nvv, BiFunction<NVV, Path, List<String>> commandLocator) {
        Future future = null;
        try {
            //lock.unlock();
            future = doBuild(nvv, commandLocator);
            if (future == null) {
                throw new RuntimeException("no result, add some buildCommands to .rvn.json config file");
            }
            future.get(Globals.config.timeoutMap.getOrDefault(nvv, Globals.config.timeout).toMillis(), TimeUnit.MILLISECONDS); // {
            log.finest("builder next");
            Thread.yield();
        } catch (CancellationException x) {
            log.warning(ANSI_GREEN + " " + buildIndex.indexOf(nvv) + " " + ANSI_RED + "Cancelled" + ANSI_RESET + " " + nvv.toString());
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof InterruptedException) {
                log.warning(ANSI_GREEN + " " + buildIndex.indexOf(nvv) + " " + ANSI_RED + "ERROR" + ANSI_RESET + " build " + nvv.toString() + " interrupted");
            } else if (ex.getCause() instanceof TimeoutException) {
                log.warning(ANSI_GREEN + " " + buildIndex.indexOf(nvv) + " " + ANSI_RED + "ERROR" + ANSI_RESET + " build " + nvv.toString() + " timedout");
            } else {
                log.warning(ANSI_GREEN + " " + buildIndex.indexOf(nvv) + " " + ANSI_RED + "ERROR" + ANSI_RESET + " build " + nvv.toString() + " completed with " + ex.getClass().getSimpleName() + " " + ex.getMessage() + Arrays.asList(ex.getStackTrace()).subList(0, ex.getStackTrace().length).toString());
                log.log(Level.SEVERE, ex.getMessage());
                //log.log(Level.SEVERE, ex.getMessage(), ex);
            }
        } catch (TimeoutException | RuntimeException | InterruptedException | ExecutionException ex) {
            log.warning(ANSI_GREEN + " " + buildIndex.indexOf(nvv) + " " + ANSI_RED + "ERROR" + ANSI_RESET + " waiting for build " + nvv.toString() + " because " + ex.getClass().getSimpleName() + " " + ex.getMessage() + Arrays.asList(ex.getStackTrace()).subList(0, ex.getStackTrace().length).toString());
            log.log(Level.SEVERE, ex.getMessage(), ex);
        } finally {
            Rvn.resetOut();
        }
    }

    private Callable<Path> redirectOutput(NVV nvv, int commandIndex, ProcessBuilder pb) throws IOException {
        File nf = null;
        File tf = null;
        boolean showOutput = Globals.config.showOutputMap.getOrDefault(nvv, Globals.config.showOutput);
        if (showOutput) {
            tf = null;
            if (pb != null) {
                pb.inheritIO();
            }
            return () -> null;
        } else {
            tf = File.createTempFile("rvn-", formalizeFileName("-" + nvv.toString()) + ".out");
            if (Globals.config.reuseOutputMap.getOrDefault(nvv, Globals.config.reuseOutput)) {
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
                if (!showOutput) {
                    // TODO make configurable
                    failMap.put(nvv, tp);
                }
                return tp;
            };
        }
    }

    private void stopBuild(NVV nvv) {
        log.info("stopping " + nvv.toString());

        if (processMap.containsKey(nvv)) {
            log.info("stopping process " + nvv.toString());
            try {
                stopProcess(processMap.get(nvv));

            } catch (Exception x) {
                log.log(Level.SEVERE, x.getMessage(), x);
            } finally {
                processMap.remove(nvv);
            }
        }

        if (futureMap.containsKey(nvv)) {
            log.info("cancelling future " + nvv.toString());
            try {
                Future future = futureMap.get(nvv);
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
                futureMap.remove(nvv);
            }
        }

        Rvn.resetOut();
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
        List<String> list = commandIndex.computeIfAbsent(nvv, (nvv1) -> new ArrayList<>());
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

    public String removeDebug(String s) {
        String s2 = s.replaceFirst("-Xrunjdwp:[0-9A-Za-z_,=]+", "");
        return s2;
    }

    private void detectMavenDaemon() {
        try {
            new ProcessBuilder("mvnd", "--status").start().onExit().handle((p, t) -> {
                if (p.exitValue() == 0) {
                    log.info(ANSI_BOLD + ANSI_YELLOW + "maven daemon mvnd detected, using" + ANSI_RESET);
                    haveMvnD = true;
                    return true;

                }
                return false;
            });
        } catch (IOException ex) {
            Logger.getLogger(BuildIt.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private void initGraph() {
        g = new Graph();
    }
}
