package rvn;

import java.nio.file.Path;
import java.util.Objects;

public class Edge<T> {

    public T nvv1;
    public T nvv2;
    public Path path;
    public boolean leaf;

    public Edge(T nvv1) {
        this.nvv1 = nvv1;
        this.leaf = true;
    }

    public Edge(T nvv1, T nvv2) {
        this.nvv1 = nvv1;
        this.nvv2 = nvv2;
        if (nvv1 == nvv2) {
            this.leaf = true;
        }
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 29 * hash + Objects.hashCode(this.nvv1);
        hash = 29 * hash + Objects.hashCode(this.nvv2);
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
        final Edge other = (Edge) obj;
        if (!Objects.equals(this.nvv1, other.nvv1)) {
            return false;
        }
        if (!Objects.equals(this.nvv2, other.nvv2)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        if (leaf) {
            return nvv1 + "&";
        }
        return nvv1 + ">" + nvv2;
    }

}
