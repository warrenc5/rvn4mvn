/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package rvn;

import java.io.File;
import static java.lang.Boolean.FALSE;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author wozza
 */
public class Config {

    public static final String userHome = System.getProperty("user.home");
    public static final String lockFileName = ".lock.rvn";
    public static Path hashConfig = Paths.get(Config.userHome + File.separator + ".m2" + File.separator + "rvn.hashes");

    public static Object of(NVV nvv) {
        return ConfigFactory.getInstance().getConfig(nvv);
    }

    public String mvnCmd;
    public Boolean showOutput = true;
    public Boolean interrupt;
    public Boolean reuseOutput;
    public Boolean daemon = false;
    public Boolean processPlugin = false;
    public String mvnOpts;
    public String javaHome;
    public String mvnArgs;
    public Duration batchWait;

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

    public Config() {
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

        matchFileIncludes = new ArrayList<>(ConfigFactory.configFileNames);
        matchFileExcludes = new ArrayList<>();
        matchDirIncludes = new ArrayList<>();
        matchDirExcludes = new ArrayList<>();
        matchArtifactIncludes = new ArrayList<>();
        matchArtifactExcludes = new ArrayList<>();

    }

}
