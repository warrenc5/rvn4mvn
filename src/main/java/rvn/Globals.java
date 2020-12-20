package rvn;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;

/**
 *
 * @author wozza
 */
public class Globals {

    public static Set<String> locations;
    public static List<Path> paths;
    public static List<NVV> buildIndex;
    public static List<NVV> index;
    public static List<NVV> toBuild;
    public static Map<Path, NVV> buildPaths;
    public static List<String> configFileNames = new ArrayList<>(Arrays.asList(new String[]{".rvn", ".rvn.json"}));

    public static Map<NVV, FileTime> lastBuild;
    public static Map<NVV, FileTime> lastUpdate;

    public static Map<NVV, Set<NVV>> projects;
    public static Map<NVV, NVV> parent;

    public static Map<NVV, Map<String, String>> properties;

    public static Map<NVV, Path> buildArtifact;
    public static Map<NVV, Path> repoArtifact;
    public static Set<NVV> agProjects;
    public static Map<Path, Config> baseConfig;

    public static Map<Path, NVV> repoPaths;
    public static Map<NVV, Path> failMap; //TODO store fail for each command
    public static List<Path> logs;
    public static Map<Path, Process> processMap;
    public static Map<NVV, Future> futureMap;
    public static Map<Integer, String> previousCmdIdx;

    public static NVV lastNvv;
    public static Path lastFile;
    public static Path lastChangeFile;

    public static Path configPath;
    public static Config config;

    public static final String userHome = System.getProperty("user.home");
    public static final String lockFileName = ".lock.rvn";
    public static Path hashConfig = Paths.get(userHome + File.separator + ".m2" + File.separator + "rvn.hashes");

    public static void rehash() {

        buildArtifact = new LinkedHashMap<>(buildArtifact);
        repoArtifact = new LinkedHashMap<>(repoArtifact);
        parent = new LinkedHashMap<>(parent);

    }

    public static void init() {
        agProjects = new HashSet<>();
        properties = new HashMap<>();
        locations = new ConcurrentSkipListSet<>();
        projects = new HashMap<>();
        parent = new HashMap<>();
        buildArtifact = new LinkedHashMap<>();
        repoArtifact = new LinkedHashMap<>();
        buildIndex = new ArrayList<>();
        buildPaths = new LinkedHashMap<>();
        toBuild = new CopyOnWriteArrayList<>();
        lastBuild = new HashMap<>();
        lastUpdate = new HashMap<>();

        processMap = new LinkedHashMap<>();
        previousCmdIdx = new HashMap<>();
        failMap = new LinkedHashMap<>();
        futureMap = new ConcurrentHashMap<>();

    }

}
