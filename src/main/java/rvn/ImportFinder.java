package rvn;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ImportFinder {

    private static final String ID_PATTERN = "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*";
    private static final Pattern FQCN = Pattern.compile(ID_PATTERN + "(\\." + ID_PATTERN + ")*");
    private static final FileSystem fSystem = FileSystems.getDefault();
    private static final PathMatcher pathMatcher = fSystem.getPathMatcher("glob:*.java");
    private static final PathMatcher testMatcher = fSystem.getPathMatcher("glob:*Test.java");
    private static ImportFinder instance;
    private final List<Path> paths;

    static {
        instance = new ImportFinder();
    }

    static ImportFinder getInstance() {
        return instance;
    }

    public ImportFinder(List<Path> paths) {
        this.paths = paths;

    }

    private ImportFinder() {
        this.paths = new ArrayList<>();
    }

    private Set<PathMatcher> read(File file) throws IOException {
        Set<PathMatcher> found = new HashSet<>();
        try (Scanner scan = new Scanner(new FileReader(file))) {
            while (scan.hasNext()) {
                String fqcn = scan.findInLine(FQCN);
                if (fqcn != null) {
                    System.err.println("f  " + fqcn.toString());
                    found.add(fSystem.getPathMatcher("regex:" + fqcn + ".java"));
                }
                scan.next();
            }
        }
        System.err.println("found " + found.toString());
        return found;
    }

    public List<Path> findImports(Path path) throws IOException {

        Set<PathMatcher> read = read(path.toFile());

        if (read.isEmpty()) {
            return paths.stream().filter(p -> isJava(p)).filter(p -> {
                return read.stream().filter(s -> s.matches(p)).findFirst().isPresent();
            }).collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    public boolean isJava(Path p) {
        if (p == null) {
            return false;
        }
        return pathMatcher.matches(p);
    }

    public boolean isJavaTest(Path p) {

        if (p == null) {
            return false;
        }
        return testMatcher.matches(p);
    }
}
