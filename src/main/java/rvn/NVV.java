package rvn;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.maven.artifact.versioning.ComparableVersion;

public class NVV implements Comparable<NVV> {

    public String name;
    public String vendor;
    public String version;
    public String classifier;
    public ComparableVersion cVersion;
    public Path path;
    public boolean isParent;
    public Map<String, String> properties = new HashMap<>();
    public NVV parent;
    public NVV project;
    boolean resolved;
    public Set<NVV> deps = new HashSet<>();

    public NVV(String name, String vendor, String version) {
        this(name, vendor, version, null);
    }

    private NVV(String name, String vendor, String version, Path path) {
        this.name = name;
        this.vendor = vendor;
        this.version = version;
        this.path = path;
        this.cVersion = new ComparableVersion(version);
    }

    private NVV(String name, String vendor) {
        this.name = name;
        this.vendor = vendor;
    }

    //TESTING
    public NVV(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        StringBuilder bob = new StringBuilder();
        if (vendor != null) {
            bob.append(vendor).append(":");
        }
        if (name != null) {
            bob.append(name).append(":");
        }

        if (version != null) {
            bob.append(version);
        }

        if (classifier != null) {
            if (version != null) {
                bob.append(":");
            }

            bob.append(classifier);
        }

        return bob.toString();
    }

    public boolean equalsExact(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final NVV other = (NVV) obj;
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        if (!Objects.equals(this.vendor, other.vendor)) {
            return false;
        }
        if (!Objects.equals(this.version, other.version)) {
            return false;
        }
        return true;
    }

    private String getLongName() {
        return vendor + name;
    }

    @Override
    public int compareTo(NVV other) {
        return Comparator.comparing(NVV::getLongName)
                .thenComparing((NVV nvv1, NVV nvv2)
                        -> (nvv1.cVersion == nvv2.cVersion) ? 0 : (nvv1.cVersion == null ? -1 : (nvv2.cVersion == null ? 1 : nvv1.cVersion.compareTo(nvv2.cVersion))))
                .compare(this, other);
    }

    void resolveVersion(String version) {
        this.version = version;
        this.cVersion = new ComparableVersion(version);
    }

    NVV with(Path path) {
        this.path = path;
        return this;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 79 * hash + Objects.hashCode(this.name);
        hash = 79 * hash + Objects.hashCode(this.vendor);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final NVV other = (NVV) obj;
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        return Objects.equals(this.vendor, other.vendor);
    }

    void setProperties(Map<String, String> props) {
        this.properties = props;
    }

    String getRepositoryPath() {
        return "^.*/"+vendor+"/"+name +"/.*$";
    }
}