package org.defaultLB.app;

import org.onosproject.net.PortNumber;

import java.util.Set;

public interface PortingAlgorithm {

    public PortNumber out(Set<PortNumber> outPorts);

}
