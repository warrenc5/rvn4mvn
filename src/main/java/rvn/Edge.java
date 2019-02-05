package rvn;

import java.nio.file.Path;
import java.util.Objects;

public class Edge {
    
    public NVV nvv1;
    public NVV nvv2;
    public Path path;

    public Edge(NVV nvv1, NVV nvv2) {
        this.nvv1 = nvv1;
        this.nvv2 = nvv2;
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
        return nvv1 + ">" + nvv2;
    }
    
}
