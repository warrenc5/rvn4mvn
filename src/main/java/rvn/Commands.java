/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package rvn;

import java.io.IOException;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import static rvn.Ansi.ANSI_CYAN;
import static rvn.Ansi.ANSI_GREEN;
import static rvn.Ansi.ANSI_PURPLE;
import static rvn.Ansi.ANSI_RED;
import static rvn.Ansi.ANSI_RESET;
import static rvn.Ansi.ANSI_WHITE;
import static rvn.Globals.buildArtifact;
import static rvn.Globals.buildIndex;
import static rvn.Globals.buildPaths;
import static rvn.Globals.config;
import static rvn.Globals.failMap;
import static rvn.Globals.futureMap;
import static rvn.Globals.lastBuild;
import static rvn.Globals.lastFile;
import static rvn.Globals.logs;
import static rvn.Globals.paths;
import static rvn.Globals.previousCmdIdx;
import static rvn.Globals.toBuild;
import static rvn.Util.prettyDuration;
import static rvn.Util.rangeToIndex;

/**
 *
 * @author wozza
 */
public class Commands {

    //private Logger log = Logger.getLogger(Commands.class.getName());
    private static final Logger log = Logger.getLogger(Commands.class.getName());
    private final Project project;
    private final EventWatcher eventWatcher;
    private final ConfigFactory configFactory;
    private final Hasher hasher;
    private final BuildIt buildIt;
    private PathWatcher pathWatcher;

    public Commands() {
        this.eventWatcher = EventWatcher.getInstance();
        this.project = Project.getInstance();
        this.hasher = Hasher.getInstance();
        this.configFactory = ConfigFactory.getInstance();
        this.buildIt = BuildIt.getInstance();
        pathWatcher = PathWatcher.getInstance();
    }

    public List<CommandHandler> createCommandHandlers(final Rvn rvn, CommandProcessor processor) {

        List<CommandHandler> commandHandlers = new ArrayList<>();

        commandHandlers.add(new CommandHandler("?", "?", "Prints the help.", (command) -> {
            if (command.equals("?")) {
                log.info(String.format("%1$s\t\t\t\t %2$s \t\t\t\t %3$s\n", "Command", "Example", "Description"));
                processor.commandHandlers.stream().forEach(c -> {
                    log.info(String.format("%1$s\t\t\t\t %2$s \t\t\t\t - %3$s\n", c.verb, c.format, c.description));

                });
                return TRUE;
            }
            return FALSE;
        }));

        commandHandlers.add(
                new CommandHandler("!", "!", "Stop the current build. Leave the build queue in place", (command) -> {
                    if (command.equals("!")) {
                        buildIt.stopAllBuilds();
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
                            NVV nvv = project.forProjectIndex(i.next());
                            Config.of(nvv).showOutputMap.put(nvv, FALSE);
                            log.warning(String.format("hiding output for %1$s", nvv.toString()));
                            return TRUE;
                        } else {
                            Globals.config.showOutput = false;
                            log.info((Globals.config.showOutput) ? "showing output" : "hiding output");
                            return TRUE;
                        }
                    }
                }.apply(command)));

        commandHandlers.add(new CommandHandler("+ {buildIndex}", "+ 1", "Show the output.", (command) -> new SimpleCommand("^\\+\\s?([0-9]?)$") {

            public Boolean configure(Iterator<String> i) throws Exception {
                if (!i.hasNext()) {
                    return FALSE;
                }

                i.next();

                if (i.hasNext()) {
                    NVV nvv;
                    Globals.config.showOutputMap.put(nvv = project.forProjectIndex(i.next()), TRUE);
                    log.warning(String.format("showing output for %1$s", nvv.toString()));
                    return TRUE;
                } else {
                    Globals.config.showOutput = true;
                    log.info((Globals.config.showOutput) ? "showing output" : "hiding output");
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
                        rvn.writeFileToStdout(last);
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
                    rvn.writeFileToStdout(lastFile);
                } catch (IOException ex) {
                    log.info("show last " + ex.getMessage());
                }
                return TRUE;
            }
            return FALSE;
        }));

        commandHandlers.add(new CommandHandler(".", ".", "Repeat last change that triggered a build.", (command) -> {
            if (command.equals(".")) {
                NVV lastNvv = Project.getInstance().lastNvv;
                if (lastNvv != null) {
                    Path path = Globals.buildArtifact.get(lastNvv);
                    String remove = hasher.hashes.remove(path.toString());
                    eventWatcher.processChange(lastNvv, true);
                }
                return TRUE;
            }
            return FALSE;
        }));

        commandHandlers
                .add(new CommandHandler("!!", "!!", "Stop the current build. Drain out the build queue.", (command) -> {
                    if (command.equals("!!")) {
                        BuildIt.getInstance().stopAllBuilds();
                        return TRUE;
                    }
                    return FALSE;
                }));

        commandHandlers.add(new CommandHandler(">", ">", "Show the fail map.", (command) -> {
            if (command.equals(">")) {
                Globals.index.stream().filter(nvv -> failMap.containsKey(nvv)).filter(nvv -> failMap.get(nvv) != null)
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
                NVV nvv = Globals.buildIndex.get(i);
                Path fail = Globals.failMap.get(nvv);
                try {
                    rvn.writeFileToStdout(fail);
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
                            Globals.config.init();
                            ConfigFactory.getInstance().reloadConfiguration();
                            PathWatcher.getInstance().run();
                        } catch (Exception ex) {
                            log.warning("reload " + ex.getMessage());
                        }
                        return TRUE;
                    }
                    return FALSE;
                }));

        commandHandlers
                .add(new CommandHandler("@@", "@@", "Show the configuration files.", (command) -> {
                    if (command.equals("@@")) {
                        try {
                            log.info(Globals.config.toString());
                            Globals.baseConfig.forEach((k, v) -> log.info(k.toString() + "=" + v.toString()));
                        } catch (Exception ex) {
                            log.warning("show " + ex.getMessage());
                        }
                        return TRUE;
                    }
                    return FALSE;
                }));

        commandHandlers.add(new CommandHandler("\\", "\\", "List yet to build list", (command) -> {
            if (command.trim().equals("\\")) {
                log.info(toBuild.stream()
                        .map(i -> String.format(
                        ANSI_GREEN + "%1$d " + ANSI_CYAN + "%2$s " + ANSI_PURPLE + "%3$s" + ANSI_RESET
                        + ANSI_WHITE + " %4$s",
                        buildIndex.indexOf(i), i.toString(), buildArtifact.get(i),
                        prettyDuration(Duration.between(
                                lastBuild.getOrDefault(i, FileTime.from(Instant.now())).toInstant(),
                                Instant.now()))))
                        .collect(Collectors.joining("," + System.lineSeparator(), "", "")));
                if(toBuild.isEmpty()){
                    log.info("all builds good");
                }
                return TRUE;
            }
            return FALSE;
        }));

        commandHandlers.add(new CommandHandler("\\\\", "\\\\", "Build all yet to build list", (command) -> {
            if (command.trim().equals("\\\\")) {
                Globals.toBuild.stream().forEach(nvv -> BuildIt.getInstance().build(nvv));
                return TRUE;
            }
            return FALSE;
        }));

        commandHandlers.add(new CommandHandler("##", "##", "Clear all hashes", (command) -> {
            if (command.trim().equals("##")) {

                hasher.hashes.clear();
                return TRUE;
            }
            return FALSE;
        }));

        commandHandlers.add(new CommandHandler("#", "#", "Build all projects outdated by hashes", (command) -> {
            if (command.trim().equals("#")) {

                hasher.hashes.forEach((k, v) -> {
                    try {
                        Project.getInstance().processPath(k, true);
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
                //TODO: make config based somehow.

                log.info(config.commands.keySet().stream()
                        .filter(i -> project.matchNVV(i, command.length() == 1 ? ".*" : command.substring(1)))
                        .map(i -> String.format(ANSI_CYAN + "%1$s " + ANSI_RESET + "%2$s" + ANSI_RESET, i,
                        config.commands.get(i).stream()
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
                    if (Project.isNVV(command)) {
                        buildArtifact.keySet().stream().filter(n -> project.matchNVV(n, command)).forEach(n -> {
                            Globals.lastNvv = eventWatcher.processChange(n);
                        });
                        return TRUE;
                    }
                    return FALSE;
                }));

        commandHandlers.add(new CommandHandler("path", "/path/to/pom.xml",
                "Builds the project(s) for the given coordinate(s). Supports regexp.", (command) -> {
                    return paths.stream()
                            .filter(p -> buildPaths.containsKey(p))
                            .filter(p -> p != null && pathWatcher.match(p, command)).map(p -> {
                        Hasher.getInstance().hashes.remove(p.toString());
                        try {
                            Globals.lastChangeFile = p;
                            Project.getInstance().processPath(p, true);
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
                        rvn.writeFileToStdout(Paths.get(command).toFile());
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
                            rvn.writeFileToStdout(e.getValue());
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
                            Globals.config.timeoutMap.put(nvv = project.forProjectIndex(i.next()), timeout);
                            log.warning(String.format("timeout for %1$s is %2$s second", nvv.toString(), timeout.toString()));
                            return TRUE;
                        } else {
                            Globals.config.timeout = timeout;
                            log.warning(String.format("timeout is %1$s second", timeout.toString()));
                            return TRUE;
                        }
                    }
                }.apply(command)));

        commandHandlers.add(new CommandHandler("`", "`", "Rebuild all projects in fail map.", (command) -> {
            if (command.trim().equals("`")) {
                Globals.failMap.entrySet().stream().filter(e -> e.getValue() != null).forEach(e -> {
                    BuildIt.getInstance().build(e.getKey());
                });
                return TRUE;
            }
            return FALSE;
        }));

        commandHandlers.add(new CommandHandler("q", "", "Stop all builds and exit.", (command) -> {
            if (command.trim().equalsIgnoreCase("q")) {
                log.info("blitzkreik");

                buildIt.stopAllBuilds();

                buildIt.executor.schedule(() -> {
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
                    buildIt.executor.submit(() -> {
                        future.cancel(true);
                        futureMap.remove(nvv);
                        buildIt.qBuild(nvv, nvv);
                        return null;
                    });
                });
                return TRUE;
            }
            return FALSE;
        }));

        commandHandlers.add(new CommandHandler("/", "/[:test:|#]",
                "List known project(s) matching coordinate or path expression.", (command) -> {
            if (command.startsWith("/") && !command.equals("//")) {
                        project.updateIndex();
                        String nvvMatch = command.substring(1);
                        if (nvvMatch.isBlank()) {
                            nvvMatch = ".*";
                        }
                        final String match = nvvMatch;

                        List<NVV> selected = buildArtifact.entrySet().stream()
                                .filter(e -> project.matchNVV(e.getKey(), match) || pathWatcher.match(e.getValue(), match)).map(e -> e.getKey()).collect(Collectors.toList());

                        log.info(selected.stream()
                                .sorted((nvv1, nvv2) -> Integer.compare(buildIndex.indexOf(nvv1), buildIndex.indexOf(nvv2)))
                                .map(i -> String.format(((toBuild.indexOf(i)) >= 0 ? (ANSI_RED + "*") : " ")
                                + ANSI_GREEN + "%1$d " + ANSI_CYAN + "%2$s " + ANSI_PURPLE + "%3$s" + ANSI_RESET,
                                buildIndex.indexOf(i), i, buildArtifact.get(i)))
                                .collect(Collectors.joining("," + System.lineSeparator(), "", "")));
                        this.pathWatcher.watchSummary();
                        return TRUE;
                    }
                    return FALSE;
                }));

        commandHandlers.add(new CommandHandler("[:num:]+[!`]?[`:num:,\\-]+", "100,3-5", "Builds the given project with the commands. To rebuild last use `,  To list commands omit the second argument.", (command) -> {
            Pattern re = Pattern.compile("([0-9]+)([`!])([0-9,\\-`]*)");

            Matcher matcher = re.matcher(command);
            if (matcher.matches()) {
                if (matcher.group(1).isBlank()) {
                    return Boolean.FALSE;
                }
                Integer index = Integer.parseInt(matcher.group(1));
                if (Globals.buildIndex.size() <= index) {
                    log.info("not enough commands" + index);
                    return TRUE;
                }
                NVV nvv = buildIndex.get(index);
                Integer cmdIndex = null;
                List<String> commands = buildIt.locateCommand(nvv, null);

                if (matcher.groupCount() == 3 && !matcher.group(3).isBlank()) {

                    String cmd = null;

                    if ("`".equals(matcher.group(3))) {
                        cmd = previousCmdIdx.get(index);
                    } else {
                        cmd = matcher.group(3);
                        previousCmdIdx.put(index, cmd);
                    }

                    List<Integer> rangeIdx = rangeToIndex(cmd);
                    for (Integer cmdIdx : rangeIdx) {
                        cmd = commands.get(cmdIdx);
                        log.info(cmd);
                        if ("!".equals(matcher.group(2))) {
                            configFactory.toggleCommand(nvv, cmd);
                        } else {
                            buildIt.buildACommand(nvv, cmd);
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
                        buildIt.buildAllCommands(i);
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

                        buildIt.buildACommand(i, cmd.toString());
                        cmd = new StringBuilder();
                        i = null;
                    }

                }
            }

            if (i != null && cmd.length() == 0) {
                buildIt.buildAllCommands(i);
            }

            log.fine("swallowing command");
            return TRUE;
        }));
        return commandHandlers;
    }

}
