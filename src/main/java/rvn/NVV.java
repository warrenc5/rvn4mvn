package rvn;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import org.apache.maven.artifact.versioning.ComparableVersion;

public class NVV implements Comparable<NVV> {

    public String name;
    public String vendor;
    public String version;
    public ComparableVersion cVersion;
    public Path path;

    public NVV(String name, String vendor, String version) {
        this(name, vendor, version, null);
    }

    public NVV(String name, String vendor, String version, Path path) {
        this.name = name;
        this.vendor = vendor;
        this.version = version;
        this.path = path;
        this.cVersion = new ComparableVersion(version);
    }

    public NVV(String name, String vendor) {
        this.name = name;
        this.vendor = vendor;
    }

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

        return bob.toString();
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 61 * hash + Objects.hashCode(this.name);
        hash = 61 * hash + Objects.hashCode(this.vendor);
        return hash;
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
        if (!Objects.equals(this.vendor, other.vendor)) {
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
                .thenComparing((NVV nvv1, NVV nvv2) -> nvv1.cVersion.compareTo(nvv2.cVersion))
                .compare(this, other);
    }

    void resolveVersion(String version) {
        this.version = version;
        this.cVersion = new ComparableVersion(version);
    }

}
