package rvn;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import static java.lang.String.join;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.toList;
import java.util.stream.Stream;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.script.Bindings;
import javax.script.ScriptException;
import static rvn.Ansi.ANSI_RESET;
import static rvn.Ansi.ANSI_WHITE;
import static rvn.Globals.baseConfig;
import static rvn.Globals.buildArtifact;
import static rvn.Globals.configFileNames;
import static rvn.Globals.locations;
import static rvn.PathWatcher.keys;
import rvn.graalson.JsonObjectBindings;

/**
 *
 * @author wozza
 */
public class ConfigFactory {

    private PathWatcher pathWatcher;

    private static Rvn rvn = Rvn.getInstance();
    private static ConfigFactory instance;

    static {
        try {
            instance = new ConfigFactory();
        } catch (IOException ex) {
            Logger.getLogger(ConfigFactory.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static ConfigFactory getInstance() {
        return instance;
    }

    private final Project project;

    public ConfigFactory() throws IOException {
        pathWatcher = new PathWatcher();
        project = Project.getInstance();
    }

    private Logger log = Logger.getLogger(this.getClass().getName());

    public List<String> optionalArray(Object v) {
        if (v instanceof List) {
            return (List<String>) v;
        }
        return Arrays.asList(new String[]{v.toString()});
    }

    /*
     * public List<String> asArray(Map v) { List<String> result = new
     * ArrayList<>(); if (v.isArray() && !v.isEmpty()) { for (int i = 0; i <
     * v.size(); i++) { result.add((String) v.getSlot(i)); } } return result; }
     */
    Path loadConfiguration(Path path) {
        try {
            NVV nvv = null;
            if (configFileNames.contains(path.getFileName().toString())) {
                Path pomPath = path.getParent().resolve("pom.xml");
                if (pomPath.toFile().exists()) {
                    nvv = project.nvvFrom(pomPath);
                    log.fine("module configuration found for " + nvv);
                }
            }
            this.loadConfiguration(path, nvv);
            log.info(String.format("watching %1$s for config changes", path.getParent()));
            pathWatcher.watch(path.getParent());
            return path;
        } catch (Exception ex) {
            Logger.getLogger(Rvn.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    Config loadDefaultConfiguration() throws IOException, ScriptException, URISyntaxException {
        String base = System.getProperty("rvn.config", Globals.userHome + File.separatorChar + ".m2");
        String name = base + File.separatorChar + ".rvn.json";
        ////System.out.println(System.getProperties().entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("\n")));
        if (base == null || base.trim().length() == 0) {
            log.info("system property ${rvn.config} not set defaulting to " + name);
        } else {
            log.info("system property ${rvn.config} set " + base + ", resolving " + name);
        }

        Globals.configs.add(0, Paths.get(name));

        Globals.configs.forEach(path -> {
            if (path.toFile().isDirectory()) {
                Globals.configFileNames.forEach(config -> {
                    try {
                        this.loadConfiguration(path.resolve(config), null);
                    } catch (Exception ex) {
                        Logger.getLogger(ConfigFactory.class.getName()).log(Level.SEVERE, null, ex);
                    }
                });
            } else {
                try {
                    this.loadConfiguration(path, null);
                } catch (Exception ex) {
                    Logger.getLogger(ConfigFactory.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });

        return Globals.config;
    }

    private Path loadConfiguration(Path config, NVV nvv) throws IOException, ScriptException, URISyntaxException {
        if (!config.startsWith("jar:") && !Files.exists(config)) {
            log.info(String.format(ANSI_WHITE + "%1$s" + ANSI_RESET + " doesn't exist, creating it", config));
        } else {
            log.info(String.format("%1$s exists", config));
        }
        pathWatcher.watch(config.getParent());
        log.info(String.format("loading configuration " + ANSI_WHITE + "%1$s" + ANSI_RESET, config));

        Reader scriptReader = Files.newBufferedReader(config);
        JsonReader reader = Json.createReader(scriptReader);
        JsonObject jsonObject = reader.readObject();
        javax.script.Bindings result = (Bindings) new JsonObjectBindings(jsonObject);

        if (nvv != null) {
            result.put("projectCoordinates", nvv);
        }
        buildConfiguration(result);
        return config;
    }

    private Config buildConfiguration(Map result) {
        Optional<NVV> oNvv = Optional.ofNullable((NVV) result.get("projectCoordinates"));

        Config config = new Config();

        //TODO merge
        if (oNvv.isPresent()) {
            config.mvnOptsMap.remove(oNvv.get());
            config.mvnArgsMap.remove(oNvv.get());
            config.timeoutMap.remove(oNvv.get());
            config.mvnCmdMap.remove(oNvv.get());
            config.javaHomeMap.remove(oNvv.get());
            config.interruptMap.remove(oNvv.get());
            config.settingsMap.remove(oNvv.get());
            config.batchWaitMap.remove(oNvv.get());
            config.reuseOutputMap.remove(oNvv.get());
            config.commands.remove(oNvv.get().toString());
        }

        String key = null;
        if (result.containsKey(key = "mvnCmd")) {

            if (oNvv.isPresent()) {
                config.mvnCmdMap.put(oNvv.get(), (String) result.get(key));
            } else {
                config.mvnCmd = (String) result.get(key);
            }

        } else {
            if (!oNvv.isPresent()) {
                log.fine(System.getProperty("os.name"));
                config.mvnCmd = System.getProperty("os.name").regionMatches(true, 0, "windows", 0, 7) ? "mvn.cmd" : "mvn";
            }
        }

        log.fine(key + " " + config.mvnCmd + " because os.name=" + System.getProperty("os.name")
                + " override with mvnCmd: 'mvn' in config file");

        if (result.containsKey(key = "showOutput")) {
            config.showOutput = (Boolean) result.get(key);
            log.fine(key + " " + config.showOutput);
        }
        if (result.containsKey(key = "locations")) {
            List<String> v = (List) result.get(key);
            locations.addAll(v.stream().filter(s -> !s.startsWith("!")).collect(toList()));
            log.fine(key + " " + locations.toString());
        }
        if (result.containsKey(key = "watchDirectories")) {
            Map v = (Map) result.get(key);
            if (v.containsKey("includes")) {
                config.matchDirIncludes.addAll((List) v.get("includes"));
                log.fine(key + " includes "
                        + config.matchDirIncludes.toString());
            }
            if (v.containsKey("excludes")) {
                config.matchDirExcludes.addAll((List) v.get("excludes"));
                log.fine(key + " excludes "
                        + config.matchDirExcludes.toString());
            }
        }

        if (result.containsKey(key = "activeFiles")) {
            Map v = (Map) result.get(key);
            if (v.containsKey("includes")) {
                config.matchFileIncludes.addAll((List) v.get("includes"));
                log.fine(key + " includes " + config.matchFileIncludes.toString());
            }
            if (v.containsKey("excludes")) {
                config.matchFileExcludes.addAll((List) v.get("excludes"));
                log.fine(key + " excludes " + config.matchFileExcludes.toString());
            }

        }

        if (result.containsKey(key = "activeArtifacts")) {
            Map v = (Map) result.get(key);
            if (v.containsKey("includes")) {
                config.matchArtifactIncludes.addAll((List) v.get("includes"));
                log.fine(key + " includes "
                        + config.matchArtifactIncludes.toString());
            }
            if (v.containsKey("excludes")) {
                config.matchArtifactExcludes.addAll((List) v.get("excludes"));
                log.fine(key + " excludes "
                        + config.matchArtifactExcludes.toString());
            }

        }

        if (result.containsKey(key = "buildCommands")) {
            Object v = result.get(key);
            if (v instanceof List) {
                List<String> commands = ((List<String>) v).stream().map(e
                        -> e.toString()).collect(Collectors.toList());
                if (oNvv.isPresent()) {
                    config.addCommand(oNvv.get().toString(),
                            commands);
                }
            } else if (v instanceof Map) {
                ((Map<String, Object>) v).entrySet().forEach(e
                        -> config.addCommand(e.getKey(), optionalArray(e.getValue())));
            }
            log.fine(v.toString());

            if (oNvv.isPresent()) {
                log.fine("commands " + oNvv.toString() + " " + config.commands.get(oNvv.get().toString()));
            }

        }

        if (result.containsKey(key = "timeout")) {
            Integer v = (Integer) result.get(key);

            if (oNvv.isPresent()) {
                config.timeoutMap.put(oNvv.get(), Duration.ofSeconds(v));
            } else {
                config.timeout = Duration.ofSeconds(v);
            }
            log.fine(key + " " + v);
        }

        if (result.containsKey(key = "mvnOpts")) {
            String v = (String) result.get(key);
            if (oNvv.isPresent()) {
                config.mvnOptsMap.compute(oNvv.get(), (k, o) -> join(o, v));
            } else {
                config.mvnOpts = v.toString();
            }
            log.fine(key + " " + v);
        }

        if (result.containsKey(key = "javaHome")) {
            String v = (String) result.get(key);
            if (oNvv.isPresent()) {
                config.javaHomeMap.put(oNvv.get(), v.toString());
            } else {
                config.javaHome = v.toString();
            }
            log.fine(key + " " + v);
        }

        if (result.containsKey(key = "mvnArgs")) {
            Object v = (Object) result.get(key);
            String v2 = "";

            if (v instanceof String) {
                v2 = (String) v;
            } else if (v instanceof List) {
                v2 = ((List<String>) v).stream().map(s -> s.toString()).filter(s -> !s.startsWith("!")).collect(Collectors.joining(" "));
            }

            String v3 = v2;
            if (oNvv.isPresent()) {
                config.mvnArgsMap.compute(oNvv.get(), (k, o) -> join(o, v3));
            } else {
                config.mvnArgs = v2.toString();
            }
            log.fine(key + " " + v);
        }

        if (result.containsKey(key = "plugins")) {
            Boolean v = (Boolean) result.get(key);
            if (oNvv.isPresent()) {
                config.processPluginMap.put(oNvv.get(), v);
            } else {
                config.processPlugin = v;
            }
            log.fine(key + " " + v);
        }

        if (result.containsKey(key = "daemon")) {
            Boolean v = (Boolean) result.get(key);
            if (oNvv.isPresent()) {
                config.daemonMap.put(oNvv.get(), v);
            } else {
                config.daemon = v;
            }
            log.fine(key + " " + v);
        }

        if (result.containsKey(key = "showOutput")) {
            Boolean v = (Boolean) result.get(key);
            if (oNvv.isPresent()) {
                config.showOutputMap.put(oNvv.get(), v);
            } else {
                config.showOutput = v;
            }
            log.fine(key + " " + v);
        }

        if (result.containsKey(key = "reuseOutput")) {
            Boolean v = (Boolean) result.get(key);
            if (oNvv.isPresent()) {
                config.reuseOutputMap.put(oNvv.get(), v);
            } else {
                config.reuseOutput = v;
            }
            log.fine(key + " " + v);
        }

        if (result.containsKey(key = "batchWait")) {
            Integer v = (Integer) result.get(key);
            if (oNvv.isPresent()) {
                config.batchWaitMap.put(oNvv.get(), Duration.ofSeconds(v));
            } else {
                config.batchWait = Duration.ofSeconds(v);
            }
            log.fine(key + " " + v);
        }

        if (result.containsKey(key = "interrupt")) {
            Boolean v = (Boolean) result.get(key);

            if (oNvv.isPresent()) {
                config.interruptMap.put(oNvv.get(), v);
            } else {
                config.interrupt = v;
            }
            log.fine(key + " " + v);
        }

        if (result.containsKey(key = "settings")) {
            String v = (String) result.get(key);

            if (oNvv.isPresent()) {
                config.settingsMap.put(oNvv.get(), v);
            } else {
                config.settings = v;
            }
            log.fine(key + " " + v);
        }

        if (oNvv != null) {
            log.fine("add project specific settings");

        }
        return config;
    }

    public void findConfiguration(String uri) {
        Path dir = Paths.get(uri);
        log.info(String.format(ANSI_WHITE + "searching %1$s for config" + ANSI_RESET, dir));
        findConfiguration(dir);
    }

    public void findConfiguration(Path path) {
        log.finest(path.toString());
        try {
            if (Files.isDirectory(path)) {
                try (final Stream<Path> stream = Files.list(path)) {
                    stream.filter((child) -> pathWatcher.matchSafe(child)).forEach(this::findConfiguration);
                }
            } else if (path.getFileName() != null && configFileNames.contains(path.getFileName().toString())) {
                this.loadConfiguration(path);
            }
        } catch (IOException ex) {
            log.info(String.format("register failed %1$s %2$s %3$s", path, ex.getClass().getName(), ex.getMessage()));
        }
    }

    boolean isConfigFile(Path path) throws IOException {
        return (Files.exists(path) && Files.isSameFile(path, getConfig(path).configPath))
                || configFileNames.stream().filter(s -> path.toAbsolutePath().toString().endsWith(s)).findFirst().isPresent();
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

    public Config getConfig(NVV nvv) {
        Path path = buildArtifact.get(nvv);
        return getConfig(path);
    }

    public Config getConfig(Path path) {
        Path configPath = findMaximumPathMatch(path);
        return baseConfig.get(configPath);
    }

    private Path findMaximumPathMatch(Path path) {
        int max = 0;
        Path result = null;
        for (Path key : baseConfig.keySet()) {
            if (key.getParent().startsWith(path.getParent()) && key.getParent().toString().length() > max) {
                result = key;
                max = key.getParent().toString().length();
            }
        };
        return result;
    }

    public void reloadConfiguration() throws Exception {
        keys.forEach(k -> k.cancel());
        this.init();
        this.loadDefaultConfiguration();
        this.scan();
    }

    private void init() {
    }

    private void scan() {
    }

    public void toggleCommand(NVV nvv, String cmd) {
        Config config = Config.of(nvv);

        config.commands.entrySet().stream().filter(e -> project.matchNVV(nvv, e.getKey())).map(e -> e.getValue()).forEach(list
                -> {
            int i = list.indexOf(cmd);
            if (i >= 0) {
                list.set(i, this.toggleCommand(cmd));
            }
        });
    }

    public String toggleCommand(String cmd) {
        if (cmd.startsWith("!")) {
            return cmd.substring(1);
        } else {
            return "!" + cmd;
        }
    }

}
