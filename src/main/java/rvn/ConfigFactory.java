package rvn;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import static java.lang.String.join;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.toList;
import java.util.stream.Stream;
import javax.script.ScriptException;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.file.FileSystems;
import java.util.logging.Level;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import static rvn.Ansi.ANSI_RESET;
import static rvn.Ansi.ANSI_WHITE;
import static rvn.Globals.baseConfig;
import static rvn.Globals.buildArtifact;
import static rvn.Globals.configFileNames;
import static rvn.Globals.locations;
import static rvn.Globals.userHome;
import static rvn.PathWatcher.keys;

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
        if (v instanceof ScriptObjectMirror) {
            ScriptObjectMirror s = (ScriptObjectMirror) v;
            if (s.isArray()) {
                return asArray(s);
            }
        }
        return Arrays.asList(new String[]{v.toString()});

    }

    public List<String> asArray(ScriptObjectMirror v) {
        List<String> result = new ArrayList<>();
        if (v.isArray() && !v.isEmpty()) {
            for (int i = 0; i < v.size(); i++) {
                result.add((String) v.getSlot(i));
            }
        }
        return result;
    }

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
            URL configURL = path.toUri().toURL();
            this.loadConfiguration(configURL, nvv);
            log.info(String.format("watching %1$s for config changes", path.getParent()));
            pathWatcher.watch(path.getParent());
            return path;
        } catch (Exception ex) {
            Logger.getLogger(Rvn.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    Config loadDefaultConfiguration() throws IOException, ScriptException, URISyntaxException {
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
        Path configPath = this.loadConfiguration(Path.of(configURL.toURI()));
        return this.getConfig(configPath);
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

        pathWatcher.watch(config.getParent());
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

    private Config buildConfiguration(ScriptObjectMirror result) {
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
        if (result.hasMember(key = "mvnCmd")) {

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

        if (result.hasMember(key = "showOutput")) {
            config.showOutput = (Boolean) result.get(key);
            log.fine(key + " " + config.showOutput);
        }
        if (result.hasMember(key = "locations")) {
            ScriptObjectMirror v = (ScriptObjectMirror) result.get(key);
            locations.addAll(asArray(v).stream().filter(s -> !s.startsWith("!")).collect(toList()));
            log.fine(key + " " + locations.toString());

        }
        if (result.hasMember(key = "watchDirectories")) {
            ScriptObjectMirror v = (ScriptObjectMirror) result.get(key);
            if (v.hasMember("includes")) {
                config.matchDirIncludes.addAll(asArray((ScriptObjectMirror) v.getMember("includes")));
                log.fine(key + " includes " + config.matchDirIncludes.toString());
            }
            if (v.hasMember("excludes")) {
                config.matchDirExcludes.addAll(asArray((ScriptObjectMirror) v.getMember("excludes")));
                log.fine(key + " excludes " + config.matchDirExcludes.toString());
            }
        }

        if (result.hasMember(key = "activeFiles")) {
            ScriptObjectMirror v = (ScriptObjectMirror) result.get(key);
            if (v.hasMember("includes")) {
                config.matchFileIncludes.addAll(asArray((ScriptObjectMirror) v.getMember("includes")));
                log.fine(key + " includes " + config.matchFileIncludes.toString());
            }
            if (v.hasMember("excludes")) {
                config.matchFileExcludes.addAll(asArray((ScriptObjectMirror) v.getMember("excludes")));
                log.fine(key + " excludes " + config.matchFileExcludes.toString());
            }

        }

        if (result.hasMember(key = "activeArtifacts")) {
            ScriptObjectMirror v = (ScriptObjectMirror) result.get(key);
            if (v.hasMember("includes")) {
                config.matchArtifactIncludes.addAll(asArray((ScriptObjectMirror) v.getMember("includes")));
                log.fine(key + " includes " + config.matchArtifactIncludes.toString());
            }
            if (v.hasMember("excludes")) {
                config.matchArtifactExcludes.addAll(asArray((ScriptObjectMirror) v.getMember("excludes")));
                log.fine(key + " excludes " + config.matchArtifactExcludes.toString());
            }

        }

        if (result.hasMember(key = "buildCommands")) {
            ScriptObjectMirror v = (ScriptObjectMirror) result.get(key);
            if (v.isArray()) {
                List<String> commands = v.values().stream().map(e -> e.toString()).collect(Collectors.toList());
                if (oNvv.isPresent()) {
                    config.addCommand(oNvv.get().toString(), commands);
                }
            } else {
                v.entrySet().forEach(e -> config.addCommand(e.getKey(), optionalArray(e.getValue())));
            }

            if (oNvv.isPresent()) {
                log.fine("commands " + oNvv.toString() + " " + config.commands.get(oNvv.get().toString()));
            }

            log.fine(v.toString());
        }

        if (result.hasMember(key = "timeout")) {
            Integer v = (Integer) result.get(key);

            if (oNvv.isPresent()) {
                config.timeoutMap.put(oNvv.get(), Duration.ofSeconds(v));
            } else {
                config.timeout = Duration.ofSeconds(v);
            }
            log.fine(key + " " + v);
        }

        if (result.hasMember(key = "mvnOpts")) {
            String v = (String) result.get(key);
            if (oNvv.isPresent()) {
                config.mvnOptsMap.compute(oNvv.get(), (k, o) -> join(o, v));
            } else {
                config.mvnOpts = v.toString();
            }
            log.fine(key + " " + v);
        }

        if (result.hasMember(key = "javaHome")) {
            String v = (String) result.get(key);
            if (oNvv.isPresent()) {
                config.javaHomeMap.put(oNvv.get(), v.toString());
            } else {
                config.javaHome = v.toString();
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
                config.mvnArgsMap.compute(oNvv.get(), (k, o) -> join(o, v3));
            } else {
                config.mvnArgs = v2.toString();
            }
            log.fine(key + " " + v);
        }

        if (result.hasMember(key = "plugins")) {
            Boolean v = (Boolean) result.get(key);
            if (oNvv.isPresent()) {
                config.processPluginMap.put(oNvv.get(), v);
            } else {
                config.processPlugin = v;
            }
            log.fine(key + " " + v);
        }

        if (result.hasMember(key = "daemon")) {
            Boolean v = (Boolean) result.get(key);
            if (oNvv.isPresent()) {
                config.daemonMap.put(oNvv.get(), v);
            } else {
                config.daemon = v;
            }
            log.fine(key + " " + v);
        }

        if (result.hasMember(key = "showOutput")) {
            Boolean v = (Boolean) result.get(key);
            if (oNvv.isPresent()) {
                config.showOutputMap.put(oNvv.get(), v);
            } else {
                config.showOutput = v;
            }
            log.fine(key + " " + v);
        }

        if (result.hasMember(key = "reuseOutput")) {
            Boolean v = (Boolean) result.get(key);
            if (oNvv.isPresent()) {
                config.reuseOutputMap.put(oNvv.get(), v);
            } else {
                config.reuseOutput = v;
            }
            log.fine(key + " " + v);
        }

        if (result.hasMember(key = "batchWait")) {
            Integer v = (Integer) result.get(key);
            if (oNvv.isPresent()) {
                config.batchWaitMap.put(oNvv.get(), Duration.ofSeconds(v));
            } else {
                config.batchWait = Duration.ofSeconds(v);
            }
            log.fine(key + " " + v);
        }

        if (result.hasMember(key = "interrupt")) {
            Boolean v = (Boolean) result.get(key);

            if (oNvv.isPresent()) {
                config.interruptMap.put(oNvv.get(), v);
            } else {
                config.interrupt = v;
            }
            log.fine(key + " " + v);
        }

        if (result.hasMember(key = "settings")) {
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

    private ScriptEngine getEngine() {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("JavaScript");
        return engine;

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
