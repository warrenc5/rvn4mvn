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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
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
import java.util.stream.Stream;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import org.codehaus.plexus.classworlds.launcher.Launcher;
import org.xml.sax.SAXException;
import static rvn.Ansi.ANSI_CYAN;
import static rvn.Ansi.ANSI_GREEN;
import static rvn.Ansi.ANSI_PURPLE;
import static rvn.Ansi.ANSI_RED;
import static rvn.Ansi.ANSI_RESET;
import static rvn.Ansi.ANSI_WHITE;
import static rvn.Ansi.ANSI_YELLOW;
import static rvn.Globals.buildArtifact;
import static rvn.Globals.buildIndex;
import static rvn.Globals.buildPaths;
import static rvn.Globals.futureMap;
import static rvn.Globals.logs;
import static rvn.Globals.parent;
import static rvn.Globals.processMap;
import static rvn.Globals.toBuild;

/**
 *
 * @author wozza
 */
public class BuildIt extends Thread {

    private Logger log = Logger.getLogger(this.getClass().getName());
    private static Logger slog = Logger.getLogger(Rvn.class.getName());
    private static BuildIt instance;

    static {
        instance = new BuildIt();
    }

    public static BuildIt getInstance() {
        return instance;
    }

    public BuildIt() {
        this.setName("BuildIt");
        this.setDefaultUncaughtExceptionHandler((e, t) -> {
            log.log(Level.WARNING, e.getName() + " " + t.getMessage(), t);
        });
    }

    void calculateToBuild() {

        buildPaths.entrySet().stream().forEach(e -> {
            NVV nvv = e.getValue();
            if (needsBuild(nvv)) {
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

    void buildAllCommands(Integer i) {

        lastChangeFile = null;

        if (buildIndex.size() > i) {
            this.processChangeImmediatley(buildIndex.get(i));
        }
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

    void stopAllBuilds() {
        if (futureMap.isEmpty()) {
            log.warning("no future");
        } else {
            this.futureMap.keySet().forEach(nvv -> this.stopBuild(nvv));
        }

        q.truncate();
        //FIXME: executor.shutdownNow();
        //executor = new ScheduledThreadPoolExecutor(1);
    }

    private void qBuild(NVV nvv) {
        qBuild(nvv, nvv);
    }

    void qBuild(NVV nvv, NVV next) {
        log.warning("qbuild " + nvv);

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
            log.info("build " + edge.toString());
            q.insert(edge);
        }
        log.finest(nvv + "=>" + next + " q->" + q.toString().replace(',', '\n'));
    }

    Graph<NVV> q = new Graph<>();

    public synchronized void scheduleFuture(NVV nvv, boolean immediate) {
        Duration batchWait = immediate ? Duration.ZERO : ConfigFactory.getInstance().getConfig(nvv).batchWaitMap.getOrDefault(nvv, this.batchWait);

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
                    Rvn.this.build(nvv);
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

    synchronized void build(NVV nvv) {
        NVV pNvv = parent.get(nvv);

        if (pNvv != null && this.needsBuild(pNvv)) {
            build(pNvv);
        }
        qBuild(nvv);
    }

    public synchronized void buildDeps(NVV nvv) {
        log.info("requested to build " + nvv.toString());
        try {
            NVV pNvv = Globals.parent.get(nvv);

            /**
             * if (pNvv != null) { //AND parent is in buildArtifacts
             * buildDeps(pNvv); }*
             */
            List<NVV> deps = this.projects.entrySet().stream()
                    //k.filter(e -> pNvv == null || (pNvv != null && !e.getKey().equals(pNvv)))
                    .flatMap(e -> Project.getInstance().projectDepends(nvv))
                    .filter(nvv3 -> Globals.buildArtifact.containsKey(nvv3))
                    .filter(nvv4 -> this.needsBuild(nvv4))
                    .distinct().collect(toList());

            if (deps.isEmpty()) {
                qBuild(nvv, nvv);
            } else {
                deps.forEach(
                        nvv2 -> {
                            log.info("dep build " + nvv2.toString() + " " + nvv.toString());
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

    public void run() {
        while (this.isAlive()) {
            try {
                Thread.currentThread().sleep(500l);
            } catch (InterruptedException ex) {
            }
            if (Rvn.q.isEmpty()) {
                continue;
            } else if (Rvn.q.size() >= 5) {
                if (!Rvn.ee) {
                    Rvn.ee = !Rvn.ee;
                    Rvn.this.easterEgg();
                }
            }
            try (final Stream<NVV> path = Rvn.q.paths2()) {
                path.forEach((nvv) -> {
                    doBuildTimeout(nvv);
                    Rvn.q.remove(nvv);
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
                futureMap.put(nvv, result);
            }
            if (block) {
                result.join();
            } else {
                result.thenCombineAsync(future, (b, v) -> b && v);
            }
            result.exceptionally((x) -> {
                synchronized (Rvn.q) {
                    Rvn.q.truncate();
                }
                return true;
            });
        }
        result.whenComplete((r, t) -> {
            if (r == true && t != null) {
                buildDeps(nvv);
            }
        });
        result.exceptionally((x) -> {
            synchronized (Rvn.q) {
                Rvn.q.truncate();
            }
            return true;
        });
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
        int commandIndex = calculateCommandIndex(nvv, command, projectPath);
        if (command.indexOf(mvn) >= 0 && command.indexOf("-f") == -1) {
            command = new StringBuilder(command).insert(command.indexOf(mvn) + mvn.length(), " -f %1$s ").toString();
        }
        if (Rvn.agProjects.contains(nvv)) {
            command = new StringBuilder(command).insert(command.indexOf(mvn) + mvn.length(), " -N ").toString();
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
            command = command + " " + Rvn.mvnArgsMap.getOrDefault(nvv, Rvn.mvnArgs) + " ";
        }
        boolean daemon = Rvn.daemonMap.getOrDefault(nvv, Rvn.this.daemon) && command.contains(mvn);
        if (daemon) {
            command = command.replace("mvn ", "-Drvn.mvn");
        } else {
            command = command.replace("mvn ", mvnCmd + " ");
        }
        final String commandFinal = String.format(command, projectPath);
        String[] args = commandFinal.split(" ");
        final List<String> filtered = Arrays.stream(args).filter((s) -> s.trim().length() > 0).collect(Collectors.toList());
        String settings = config.settingsMap.getOrDefault(nvv, Globals.config.settings);
        log.info(settings);
        if (settings != null) {
            Path settingsPath = projectPath.getParent().resolve(Paths.get(settings));
            if (Files.exists(settingsPath)) {
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
                try {
                    lockFile = Files.createFile(lockFile);
                } catch (IOException ex) {
                    Logger.getLogger(Rvn.class.getName()).log(Level.SEVERE, ex.getMessage());
                }
                try {
                    this.thenStarted = Instant.now();
                    if (daemon) {
                        log.info("running in process " + filtered.toString());
                        archive = redirectOutput(nvv, commandIndex, null);
                        exit = Launcher.mainWithExitCode(filteredArgs);
                    } else {
                        log.info("spawning new process " + filtered.toString());
                        ProcessBuilder pb = new ProcessBuilder().directory(projectPath.getParent().toFile()).command(filteredArgs);
                        archive = redirectOutput(nvv, commandIndex, pb);
                        pb.environment().putAll(System.getenv());
                        if (mvnOpts != null && !mvnOpts.trim().isEmpty()) {
                            pb.environment().put("MAVEN_OPTS", mvnOpts);
                        } else {
                            pb.environment().put("MAVEN_OPTS", Rvn.removeDebug(pb.environment().getOrDefault("MAVEN_OPTS", "")));
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
                        Rvn.processMap.put(projectPath, p);
                        CompletableFuture<Process> processFuture = p.onExit();
                        //lock.unlock();
                        if (!p.waitFor(Rvn.timeoutMap.getOrDefault(nvv, Rvn.timeout).toMillis(), TimeUnit.MILLISECONDS)) {
                            timedOut = true;
                            stopBuild(nvv);
                        }
                    }
                } catch (InterruptedException ex) {
                    Rvn.stopProcess(p);
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
                        Rvn.resetOut();
                    } else {
                        Rvn.processMap.remove(projectPath);
                        exit = p.exitValue();
                    }
                    log.info(String.format(ANSI_GREEN + "%6$d " + ANSI_CYAN + "%1$s " + ANSI_RESET + ((exit == 0) ? ANSI_GREEN : ANSI_RED) + (timedOut ? "TIMEDOUT" : (exit == 0 ? "PASSED" : "FAILED")) + " (%2$s)" + ANSI_RESET + " with command " + ANSI_WHITE + "%3$s" + ANSI_YELLOW + " %4$s" + ANSI_RESET + "\n%5$s", nvv, exit, commandFinal, Duration.between(then, Instant.now()), output != null ? output : "", Rvn.buildIndex.indexOf(nvv)));
                    if (exit != 0) {
                        if (output != null) {
                            try {
                                Rvn.this.writeFileToStdout(output);
                            } catch (IOException ex) {
                                Logger.getLogger(Rvn.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                        throw new RuntimeException("exit code " + exit); //Boolean.FALSE);
                    } else {
                        result.complete(Boolean.TRUE);
                        Rvn.toBuild.remove(nvv);
                        Rvn.failMap.remove(nvv);
                    }
                    Rvn.this.thenFinished = Instant.now();
                }
                throw new RuntimeException("not performed");
            }
        };

        return result.completeAsync(task, Rvn.executor);
    }

    private void doBuildTimeout(NVV nvv) {
        this.doBuildTimeout(nvv, Rvn.this::locateCommand);
    }

    void doBuildTimeout(NVV nvv, BiFunction<NVV, Path, List<String>> commandLocator) {
        Future future = null;
        try {
            //lock.unlock();
            future = doBuild(nvv, commandLocator);
            if (future == null) {
                throw new RuntimeException("no result, add some buildCommands to .rvn.json config file");
            }
            future.get(Rvn.timeoutMap.getOrDefault(nvv, Rvn.timeout).toMillis(), TimeUnit.MILLISECONDS); // {
            log.finest("builder next");
            Thread.yield();
        } catch (CancellationException x) {
            log.warning(ANSI_GREEN + " " + Rvn.this.buildIndex.indexOf(nvv) + " " + ANSI_RED + "Cancelled" + ANSI_RESET + " " + nvv.toString());
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof InterruptedException) {
                log.warning(ANSI_GREEN + " " + Rvn.this.buildIndex.indexOf(nvv) + " " + ANSI_RED + "ERROR" + ANSI_RESET + " build " + nvv.toString() + " interrupted");
            } else if (ex.getCause() instanceof TimeoutException) {
                log.warning(ANSI_GREEN + " " + Rvn.this.buildIndex.indexOf(nvv) + " " + ANSI_RED + "ERROR" + ANSI_RESET + " build " + nvv.toString() + " timedout");
            } else {
                log.warning(ANSI_GREEN + " " + Rvn.this.buildIndex.indexOf(nvv) + " " + ANSI_RED + "ERROR" + ANSI_RESET + " build " + nvv.toString() + " completed with " + ex.getClass().getSimpleName() + " " + ex.getMessage() + Arrays.asList(ex.getStackTrace()).subList(0, ex.getStackTrace().length).toString());
                log.log(Level.SEVERE, ex.getMessage(), ex);
            }
        } catch (TimeoutException | RuntimeException | InterruptedException | ExecutionException ex) {
            log.warning(ANSI_GREEN + " " + Rvn.this.buildIndex.indexOf(nvv) + " " + ANSI_RED + "ERROR" + ANSI_RESET + " waiting for build " + nvv.toString() + " because " + ex.getClass().getSimpleName() + " " + ex.getMessage() + Arrays.asList(ex.getStackTrace()).subList(0, ex.getStackTrace().length).toString());
            log.log(Level.SEVERE, ex.getMessage(), ex);
        } finally {
            Rvn.resetOut();
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
            if (Rvn.reuseOutputMap.getOrDefault(nvv, Rvn.reuseOutput)) {
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
                    Rvn.this.failMap.put(nvv, tp);
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

}
