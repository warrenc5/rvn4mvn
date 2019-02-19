package rvn;

import java.nio.file.Path;
import java.util.Objects;

public class NVV {

    public String name;
    public String vendor;
    public String version;
    public Path path;

    public NVV(String name, String vendor, String version) {
        this(name, vendor, version, null);
    }

    public NVV(String name, String vendor, String version, Path path) {
        this.name = name;
        this.vendor = vendor;
        this.version = version;
        this.path = path;
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
            bob.append(vendor).append("::");
        }
        if (name != null) {
            bob.append(name).append("::");
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

    public boolean equalsVersion(Object obj) {

        final NVV other = (NVV) obj;

        if (!Objects.equals(this, other)) {
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

}
