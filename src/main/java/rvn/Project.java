package rvn;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.toList;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;
import static rvn.Ansi.ANSI_CYAN;
import static rvn.Ansi.ANSI_PURPLE;
import static rvn.Ansi.ANSI_RED;
import static rvn.Ansi.ANSI_RESET;
import static rvn.Ansi.ANSI_YELLOW;
import static rvn.DefaultExceptionMessage.ofX;
import static rvn.Globals.agProjects;
import static rvn.Globals.buildArtifact;
import static rvn.Globals.buildIndex;
import static rvn.Globals.buildPaths;
import static rvn.Globals.configFileNames;
import static rvn.Globals.index;
import static rvn.Globals.parent;
import static rvn.Globals.projects;
import static rvn.Globals.properties;
import static rvn.Globals.repoArtifact;
import static rvn.Globals.toBuild;
import static rvn.Util.between;
import static rvn.Util.toStream;

/**
 *
 * @author wozza
 */
public class Project {

    private Logger log = Logger.getLogger(this.getClass().getName());
    private static Logger slog = Logger.getLogger(Rvn.class.getName());

    public static Project instance;

    static {
        instance = new Project();
    }

    public static synchronized Project getInstance() {
        return instance;
    }
    private final PathWatcher pathWatcher;
    private final ConfigFactory configFactory;
    private EventWatcher eventWatcher;
    private List<String> pomFileNames;
    NVV lastNvv;
    private Path lastChangeFile;
    private final BuildIt buildIt;
    private final Hasher hasher;

    public Project() {
        this.pathWatcher = PathWatcher.getInstance();
        this.configFactory = ConfigFactory.getInstance();
        this.eventWatcher = EventWatcher.getInstance();
        this.buildIt = BuildIt.getInstance();
        this.hasher = Hasher.getInstance();

        pomFileNames = new ArrayList<>(Arrays.asList(new String[]{"pom.xml", "pom.yml", ".*.pom$"}));
    }

    private void processPathSafe(String uri) {
        try {
            processPath(uri);

        } catch (XPathExpressionException | SAXException | IOException | ParserConfigurationException ex) {
            log.log(Level.SEVERE, null, ex);
        } catch (Exception ex) {
            log.log(Level.SEVERE, null, ex);
        }
    }

    private void processPath(String uri)
            throws XPathExpressionException, SAXException, IOException, ParserConfigurationException, Exception {
        this.processPath(uri, false);;
    }

    void processPath(String uri, boolean immediate) throws XPathExpressionException, SAXException, IOException, ParserConfigurationException, Exception {
        log.fine("in ->" + uri);;
        Path path = Paths.get(uri);
        processPath(path, immediate);
    }

    void processPath(Path path)
            throws XPathExpressionException, SAXException, IOException, ParserConfigurationException, Exception {
        this.processPath(path, false);
    }

    void processPath(Path path, boolean immediate)
            throws XPathExpressionException, SAXException, IOException, ParserConfigurationException, Exception {
        if (!Files.isRegularFile(path)) {
            return;
        }

        if (!pathWatcher.matchFiles(path)) {
            return;
        }

        boolean checkHash = path.endsWith("pom.xml") || !configFactory.isConfigFile(path);

        if (checkHash
                && !Hasher.getInstance().hashChange(path)) {
            log.info("no hash change detected " + path);
            return;
        }

        if (path.toString().endsWith(".pom")) {
            log.fine(String.format("no nvv: %1$s", path));
            Document pom = this.loadPom(path);
            NVV nvv = this.nvvFrom(pom).with(path);
            this.nvvParent(nvv, pom);

            log.info(String.format("nvv: %1$s %2$s", path, nvv));
            if (matchNVV(nvv, path)) {
                buildIt.buildDeps(nvv);
            }
            return;
        } else if (configFileNames.contains(path.getFileName().toString())) {
            NVV nvv = findPom(path);

            if (nvv == null) {
                log.fine(String.format("no nvv: %1$s", path));
                return;
            } else {
                log.info(String.format("config nvv: %1$s %2$s", path, nvv));
            }
            ConfigFactory.getInstance().loadConfiguration(path);
            PathWatcher.getInstance().watch(path.getParent());
            return;
        }

        try {
            NVV nvv = findPom(path);
            if (nvv == null) {
                log.fine(String.format("no nvv: %1$s", path));
                return;
            } else {
                log.info(String.format("nvv: %1$s %2$s", path, nvv));
            }

            nvv.with(path);
            if (matchNVV(nvv)) {
                lastNvv = nvv;
                if (this.lastChangeFile == null) {
                    this.lastChangeFile = path;
                }
                eventWatcher.processChange(nvv, path, immediate);
                log.info(String.format("change triggered by config: %1$s %2$s immediately: %3$s", path, nvv, immediate));
            } else {
                log.info(String.format("change excluded by config: %1$s %2$s", path, nvv));
            }

        } catch (Exception x) {
            log.warning(String.format("process: %1$s - %2$s", path, x.getMessage()));
            x.printStackTrace();
        }
    }

    private Document loadPom(Path path)
            throws SAXException, XPathExpressionException, IOException, ParserConfigurationException {
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = builderFactory.newDocumentBuilder();
        builder.setErrorHandler(new DefaultHandler2());
        Document xmlDocument = builder.parse(path.toFile());
        return xmlDocument;
    }

    public Optional<NVV> processPom(Path path)
            throws SAXException, XPathExpressionException, IOException, ParserConfigurationException {
        Document xmlDocument = loadPom(path);

        NVV nvv = nvvFrom(path);
        NVV parentNvv = nvvParent(nvv, xmlDocument);

        if (!matchNVV(nvv, path)) {
            log.finest("ignoring " + nvv.toString() + "  " + path.toString());
            return Optional.empty();
        }
        log.finest("selected " + nvv.toString() + "  " + path.toString());

        boolean updated = Hasher.getInstance().update(path);
        if (updated) {
            log.warning(String.format("%1$s already changed", nvv));
            buildIt.buildDeps(nvv);
        }

        NodeList nodeList = (NodeList) xPath.compile("//dependency").evaluate(xmlDocument, XPathConstants.NODESET);

        Stream<Node> stream = toStream(nodeList);

        Config config = configFactory.getConfig(path);

        if (config.processPluginMap.getOrDefault(nvv, Globals.config.processPlugin)) {
            nodeList = (NodeList) xPath.compile("//plugin").evaluate(xmlDocument, XPathConstants.NODESET);
            stream = Stream.concat(stream, toStream(nodeList));
        }

        Set<NVV> deps = stream.map(n -> this.processDependency(n, nvv)).filter(t -> t != null)
                .filter(nvv2 -> this.matchNVV(nvv2, path))
                .collect(Collectors.toSet());

        if (!Objects.isNull(parentNvv)) {
            parent.put(nvv, parentNvv);
            deps.add(parentNvv);
        }

        nvv.deps = deps;

        NodeList modules = (NodeList) xPath.compile("//modules/module").evaluate(xmlDocument, XPathConstants.NODESET);

        if (modules.getLength() > 0) {
            log.fine(String.format("aggregator project %1$s", nvv.toString()));
            agProjects.add(nvv);
        }

        Map<String, String> props = this.propertiesFrom(xmlDocument);
        properties.put(nvv, props);
        nvv.properties = props;

        log.fine(String.format("tracking %1$s %2$s", nvv.toString(), path));
        projects.put(nvv, deps);

        if (path.endsWith("pom.xml")) {
            Long workingTime = Files.getLastModifiedTime(path).to(TimeUnit.SECONDS);

            Path oldPath = buildArtifact.get(nvv);
            if (oldPath != null && !Files.isSameFile(path, oldPath)) {
                Long otherTime = Files.getLastModifiedTime(oldPath).to(TimeUnit.SECONDS);
                if (workingTime > otherTime) {
                    log.warning(
                            String.format(
                                    ANSI_PURPLE + "%1$s " + ANSI_YELLOW + "newer than" + ANSI_PURPLE + " %2$s"
                                    + ANSI_CYAN + " %3$s" + ANSI_RED + ", replacing" + ANSI_RESET,
                                    path, oldPath, nvv.toString()));
                } else {
                    log.warning(
                            String.format(
                                    ANSI_PURPLE + "%1$s " + ANSI_YELLOW + "older than" + ANSI_PURPLE + " %2$s"
                                    + ANSI_CYAN + " %3$s" + ANSI_RESET + ", ignoring",
                                    path, oldPath, nvv.toString()));
                    return Optional.empty();
                }
            }

            log.fine("found build " + nvv.toString() + " " + path.toString());
            buildArtifact.put(nvv, path);
            buildPaths.put(path, nvv);
        } else if (path.toString().endsWith(".pom")) {
            log.finest(nvv.toString() + " ++++++++ " + path.toString());
            repoArtifact.put(nvv, path);
        } else {
        }

        return Optional.of(nvv);
    }

    private NVV processDependency(Node n, NVV proj) {
        Config config = configFactory.getConfig(proj);

        if (!config.processPluginMap.getOrDefault(proj, Globals.config.processPlugin) && n.getParentNode().getParentNode().getNodeName().equals("plugin")) {
            return null;
        }

        try {
            NVV nvv = nvvFrom(n);
            //log.info(String.format(proj.toString() + " depends on %1$s", nvv.toString()));
            //log.info(n.getParentNode().getParentNode().getNodeName());
            return nvv;
        } catch (XPathExpressionException ex) {
            log.severe(ofX(ex));
        }
        return null;
    }

    private NVV nvvParent(NVV nvv, Document xmlDocument) {
        NVV parentNvv = null;
        try {
            Node parent = (Node) xPath.compile("/project/parent").evaluate(xmlDocument, XPathConstants.NODE);
            if (parent != null) {
                parentNvv = nvvFrom(parent);
                nvv.parent = parentNvv;
                parentNvv.isParent = true;
            }

        } catch (Exception e) {
            log.warning("parent fail " + nvv + " " + e.getMessage());
        }
        log.fine(String.format("%1$s with parent %2$s", nvv.toString(), parentNvv));
        return parentNvv;
    }

    public NVV nvvFrom(Path path)
            throws XPathExpressionException, SAXException, IOException, ParserConfigurationException {
        Document xmlDocument = loadPom(path);
        return nvvFrom(xmlDocument).with(path);
    }

    private NVV nvvFrom(Document xmlDocument) throws XPathExpressionException {

        Node project = (Node) xPath.compile("/project").evaluate(xmlDocument, XPathConstants.NODE);

        NVV nvv = nvvFrom(project);

        return nvv;
    }

    private Node projectFrom(Path path)
            throws XPathExpressionException, SAXException, IOException, ParserConfigurationException {
        Document xmlDocument = loadPom(path);
        return projectFrom(xmlDocument);
    }

    private Node projectFrom(Document xmlDocument) throws XPathExpressionException {
        Node project = (Node) xPath.compile("/project").evaluate(xmlDocument, XPathConstants.NODE);
        return project;
    }

    //TODO load NODE From
    private Map<String, String> propertiesFrom(NVV nvv) {

        if (Globals.properties.containsKey(nvv)) {
            return Globals.properties.get(nvv);
        }

        //TODO get properties from repoArtifacts too
        Path pom = Globals.buildArtifact.getOrDefault(nvv, Globals.repoArtifact.get(nvv));

        if (pom != null) {

            try {
                return propertiesFrom(pom); //TODO move to resolve
            } catch (Exception ex) {
                log.severe(ofX(ex));
            }
        }

        return Collections.EMPTY_MAP;
    }

    private Map<String, String> propertiesFrom(Path path)
            throws XPathExpressionException, SAXException, IOException, ParserConfigurationException {
        return propertiesFrom(projectFrom(path));
    }

    private Map<String, String> propertiesFrom(Node context) throws XPathExpressionException {
        Map<String, String> result = new HashMap<>();
        NodeList propSet = (NodeList) xPath.compile("//properties/node()").evaluate(context, XPathConstants.NODESET);

        for (int i = 0; i < propSet.getLength(); i++) {
            if (Node.ELEMENT_NODE == propSet.item(i).getNodeType()) {
                String name = propSet.item(i).getNodeName();
                String value = ((Element) propSet.item(i)).getTextContent();
                result.put(name, value);
            }
        }

        //log.info(result.toString());
        return result;
    }

    XPath xPath = XPathFactory.newInstance().newXPath();

    private NVV nvvFrom(Node context) throws XPathExpressionException {
        return new NVV(xPath.compile("artifactId").evaluate(context).trim(), xPath.compile("groupId").evaluate(context).trim(),
                xPath.compile("version").evaluate(context).trim());
    }

    public void resolveGAV() {
        Stream<NVV> nvvs = Stream.of(
                projects.keySet().stream(),
                buildIndex.stream(),
                index.stream(),
                toBuild.stream(),
                buildArtifact.keySet().stream(),
                projects.values().stream().flatMap(s -> s.stream())).flatMap(s -> s);

        Project.this.resolveGAV(nvvs);
    }

    public void resolveGAV(Stream<NVV> nvvs) {
        nvvs.collect(toList()).forEach(nvv -> {
            NVV nvvR = resolveGAV(nvv);
            log.fine("resolved " + nvv.toString() + " " + nvvR.toString() + " " + nvvR.parent + " " + nvv.path);
        });
    }

    public NVV resolveGAV(NVV nvv) {
        NVV parentNvv = nvv.parent;

        if (parentNvv != null) {
            this.resolveGAV(parentNvv);
        }

        if (nvv.name.contains("\\${")) {
            nvv.name = interpolate(nvv, nvv.name);
        }

        if (nvv.vendor == null || nvv.vendor.isBlank()) {
            if (parentNvv != null) {
                nvv.vendor = parentNvv.vendor;
            }
        } else if (nvv.vendor.equalsIgnoreCase("${pom.groupId}")
                || nvv.vendor.equalsIgnoreCase("${project.groupId}")
                || nvv.vendor.equalsIgnoreCase("${project.parent.groupId}")) {
            if (parentNvv != null) {
                nvv.vendor = parentNvv.vendor;
            }
        } else if (nvv.vendor.contains("\\${")) {
            nvv.vendor = interpolate(nvv, nvv.vendor);
        }

        if (nvv.version == null || nvv.version.isBlank()) {
            if (parentNvv != null) {
                nvv.version = parentNvv.version;
            }
        } else if (nvv.version.equalsIgnoreCase("${pom.version}")
                || nvv.version.equalsIgnoreCase("${project.version}")
                || nvv.version.equalsIgnoreCase("${project.parent.version}")) {
            if (parentNvv != null) {
                nvv.version = parentNvv.version;
            }
        } else if (nvv.version.contains("\\${")) {
            nvv.version = interpolate(nvv, nvv.version);
        }

        nvv.resolveVersion(nvv.version);

        nvv.resolved = true;
        return nvv;
    }

    private String interpolate(NVV nvv, String value) {
        log.info("int " + nvv.toString() + " " + value);
        Pattern p = Pattern.compile("\\$\\{(.*)\\}");
        Matcher matcher = p.matcher(value);
        if (matcher.matches()) {
            String name = matcher.group(1);
            String newValue = null;
            if (nvv.properties.containsKey(name)) {
                newValue = nvv.properties.get(name);
                return newValue;
            } else {
                NVV parentNvv = nvv.parent;

                if (parentNvv != null) {
                    newValue = this.interpolate(parentNvv, value);
                    if (newValue != null) {
                        return newValue;
                    }
                }
            }
        }
        if (nvv.path != null && !nvv.path.toString().endsWith(".pom")) {
            log.warning("not resolved " + nvv + " " + value);
        }
        return value;
    }

    private String expandNVVRegex(String match) {
        StringBuilder bob = new StringBuilder();

        if (match.length() == 0) {
            match = ":";
        }

        for (int i = 0; i < match.length(); i++) {
            Character c = match.charAt(i);
            bob.append(c);
            if (':' == c) {
                if (i == 0) {
                    bob.insert(i, ".*");

                } else if (i == match.length() - 1) {
                    bob.append(".*");
                } else {
                }
                if (match.length() > i + 1 && c.equals(match.charAt(i + 1))) {
                    bob.append(".*");
                }
            }
        }

        if (bob.toString().equals(match)) {
            bob = new StringBuilder(".*" + match + ".*");
        }

        log.finest("matching " + match + " " + bob.toString());
        return bob.toString();
    }

    public boolean matchNVV(String project, String match) {
        if (project.equals("::")) {
            return true;
        }
        return project.matches(this.expandNVVRegex(match));
    }

    public boolean matchNVV(NVV project, String match) {
        return project.toString().matches(this.expandNVVRegex(match));
    }

    private boolean matchNVV(NVV nvv, Path path) {
        try {
            return matchNVV(nvv) && pathWatcher.matchFiles(path);
        } catch (IOException ex) {
            log.warning(ex.getMessage());
            return false;
        }
    }

    public boolean matchNVV(NVV nvv) {
        Config config = configFactory.getConfig(nvv);
        return config.matchArtifactIncludes.isEmpty() || (config.matchArtifactIncludes.stream().filter(s -> this.matchSafe(nvv, s)).findFirst().isPresent());// FIXME:
        // absolutely
        //&& !matchArtifactExcludes.stream().filter(s -> this.match(nvv, s)).findFirst().isPresent()); // FIXME:
        // absolutely
    }

    public boolean matchSafe(NVV nvv, String s) {

        try {
            return this.match(nvv, s);
        } catch (PatternSyntaxException pse) {
            log.warning(pse.getMessage() + " " + s);
        }

        return false;
    }

    public boolean match(NVV nvv, String s) throws PatternSyntaxException {
        boolean matches = nvv.toString().matches(s = expandNVVMatch(s));

        if (matches) {
            log.fine("matches artifact " + s + " " + nvv.toString());
        } else {
            log.finest("doesn't match artifact " + s + " " + nvv.toString());
        }
        return matches;
    }

    public static boolean isNVV(String command) {
        return command.matches(".*:.*");
    }

    Stream<NVV> projectDepends(NVV nvv) {
        return projects.entrySet().stream()
                .filter(e -> e.getValue().contains(nvv)
                ).map(e -> e.getKey());
    }

    boolean isPom(Path path) {
        return this.pomFileNames.stream().filter(s -> path.toAbsolutePath().toString().matches(s) || path.toAbsolutePath().toString().endsWith(s)).findFirst().isPresent();
    }

    private NVV findPom(Path path) throws Exception {
        // return buildPaths.get(path);
        List<Map.Entry<Path, NVV>> base = buildPaths.entrySet().stream().filter(e -> isBasePath(e.getKey(), path))
                .collect(Collectors.toList());

        Optional<Map.Entry<Path, NVV>> nvv = base.stream().reduce((e1, e2) -> {
            return (e1 != null && e1.getKey().getNameCount() >= e2.getKey().getNameCount()) ? e1 : e2;
        });

        if (nvv.isPresent()) {
            log.info("base: " + nvv.get().toString());
            return nvv.get().getValue();
        } else {
            return null;
        }
        // map(e -> e.getKey()).reducefindFirst().orElseThrow(() -> new Exception("not
        // known " + path));
    }

    private Path findPath(String name, String vendor, String version) {
        Path path = buildArtifact.get(new NVV(name, vendor, version));
        return path;
    }

    private boolean isBasePath(Path project, Path changed) {
        Path parent = project.getParent();
        boolean base = false;
        try {

            if (changed.toRealPath().startsWith(parent.toRealPath())) {
                base = true;
            } else if (changed.toRealPath().endsWith(parent.toRealPath())) {
                base = true;
            }

            if (base) {
                log.finest(ANSI_PURPLE + "changed " + changed.toRealPath() + " " + ANSI_CYAN + parent.toRealPath() + " " + base + ANSI_RESET + " " + project.toRealPath());
            }
            return base;
        } catch (Exception x) {
            return false;
        }
    }

    public static String expandNVVMatch(String s) {

        StringBuilder bob = new StringBuilder();
        if (!s.startsWith("^")) {
            bob.append("^");
        }
        if (s.startsWith(":")) {
            bob.append("^.*");
        }
        bob.append(s);
        if (s.endsWith(":")) {
            bob.append(".*");
        }

        if (!s.endsWith("$")) {
            bob.append("$");
        }

        slog.finest(s + "->" + bob.toString());
        return bob.toString();
    }

    public NVV forProjectIndex(String s) throws Exception {
        int buildIndex = Integer.parseInt(s);
        return forProjectIndex(buildIndex);
    }

    public NVV forProjectIndex(Integer s) throws Exception {

        if (between(s, 0, buildIndex.size())) {
            NVV nvv = buildIndex.get(s);
            return nvv;
        } else {
            throw new Exception(s + " out of bounds ");
        }
    }

    public void updateIndex() {
        index = buildArtifact.keySet().stream().collect(Collectors.toList());
        Collections.sort(index, (NVV o1, NVV o2) -> o1.toString().compareTo(o2.toString()));
    }

    boolean needsBuild(NVV nvv) {
        synchronized (buildArtifact) {
            Optional<Path> bPath = buildArtifact.entrySet().stream().filter(e -> e.getKey().equalsExact(nvv)).map(e -> e.getValue()).findAny();
            Optional<Path> rPath = repoArtifact.entrySet().stream().filter(e -> e.getKey().equalsExact(nvv)).map(e -> e.getValue()).findAny();

            if (bPath.isPresent() && rPath.isEmpty()) {
                log.finest("missing " + nvv.toString() + " " + bPath + " " + rPath);
                return true;
            } else if (bPath.isPresent() && rPath.isPresent()) {
                return !hasher.compareHashes(bPath.get(), rPath.get());
            } else {
                return false;
            }
        }
    }

}
