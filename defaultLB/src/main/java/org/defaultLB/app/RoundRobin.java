package org.defaultLB.app;

import org.onosproject.net.PortNumber;

import java.util.HashSet;
import java.util.Set;

public class RoundRobin implements PortingAlgorithm {
    Set<PortNumber> visited;
    PortNumber outPort;

    public RoundRobin() {
        this.visited = new HashSet<PortNumber>();
    }

    public PortNumber out(Set<PortNumber> outPorts) {
        if (visited.size() == 0) {
            outPort = outPorts.iterator().next();
            visited.add(outPort);
        } else {
            for (PortNumber p : outPorts) {
                if (!visited.contains(p)) {
                    outPort = p;
                    visited.add(p);
                    if (visited.equals(outPorts)) {
                        visited.clear();
                    }
                    break;
                }
            }
        }
        return outPort;
    }
}
