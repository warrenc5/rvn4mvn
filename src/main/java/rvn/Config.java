/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package rvn;

import static java.lang.Boolean.FALSE;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 *
 * @author wozza
 */
public class Config {

    private Logger log = Logger.getLogger(this.getClass().getName());

    public static Config of(NVV nvv) {
        return ConfigFactory.getInstance().getConfig(nvv);
    }

    public String mvnCmd;
    public Boolean showOutput = true;
    public Boolean interrupt;
    public String settings;
    public Boolean reuseOutput;
    public Boolean daemon = false;
    public Boolean processPlugin = false;
    public String mvnOpts;
    public String javaHome;
    public String mvnArgs;
    public Duration batchWait;
    public Duration timeout;

    public Map<String, List<String>> commands;
    public Map<NVV, String> lastCommand;

    public Map<NVV, String> mvnCmdMap;
    public Map<NVV, Duration> batchWaitMap;
    public Map<NVV, Duration> timeoutMap;
    public Map<NVV, Boolean> interruptMap;
    public Map<NVV, String> settingsMap;
    public Map<NVV, String> mvnOptsMap;
    public Map<NVV, String> javaHomeMap;
    public Map<NVV, String> mvnArgsMap;
    public Map<NVV, Boolean> reuseOutputMap;
    public Map<NVV, Boolean> showOutputMap;
    public Map<NVV, Boolean> daemonMap;
    public Map<NVV, Boolean> processPluginMap;

    public List<String> matchFileIncludes;
    public List<String> matchFileExcludes;
    public List<String> matchDirIncludes;
    public List<String> matchDirExcludes;
    public List<String> matchArtifactIncludes;
    public List<String> matchArtifactExcludes;
    public Path configPath;

    public Config(Path configPath) {
        this.configPath = configPath;
        init();
    }

    public void init() {
        reuseOutput = FALSE;
        mvnOpts = "";
        javaHome = "";
        mvnArgs = "";

        batchWait = Duration.ofSeconds(0);
        interrupt = Boolean.FALSE;
        lastCommand = new HashMap<>();
        mvnCmdMap = new HashMap<>();
        batchWaitMap = new HashMap<>();
        timeout = Duration.ofMinutes(2);
        timeoutMap = new HashMap<>();
        interruptMap = new HashMap<>();
        settingsMap = new HashMap<>();
        mvnOptsMap = new HashMap<>();
        javaHomeMap = new HashMap<>();
        mvnArgsMap = new HashMap<>();
        reuseOutputMap = new HashMap<>();
        showOutputMap = new HashMap<>();
        daemonMap = new HashMap<>();
        processPluginMap = new HashMap<>();

        matchFileIncludes = new ArrayList<>(Globals.configFileNames);
        matchFileExcludes = new ArrayList<>();
        matchDirIncludes = new ArrayList<>();
        matchDirExcludes = new ArrayList<>();
        matchArtifactIncludes = new ArrayList<>();
        matchArtifactExcludes = new ArrayList<>();

        commands = new LinkedHashMap<>();
    }

    public void addCommand(String projectKey, List<String> newCommandList) {
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

}