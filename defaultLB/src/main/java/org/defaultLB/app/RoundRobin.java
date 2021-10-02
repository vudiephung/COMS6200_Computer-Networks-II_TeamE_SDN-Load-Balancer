package org.defaultLB.app;

import org.onosproject.net.PortNumber;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class RoundRobin implements PortingAlgorithm {
    Iterator<PortNumber> iterator;

    public RoundRobin() {
        iterator = null;
    }

    @Override
    public PortNumber out(Set<PortNumber> outPorts) {
        if (outPorts == null || outPorts.size() == 0) {
            return null;
        } else if (iterator == null || !iterator.hasNext()) {
            iterator = List.copyOf(outPorts).iterator();
        }
        return iterator.next();
    }
}
