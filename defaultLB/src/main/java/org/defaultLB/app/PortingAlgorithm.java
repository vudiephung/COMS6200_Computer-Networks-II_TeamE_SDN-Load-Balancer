package org.defaultLB.app;

import org.onosproject.net.PortNumber;

import java.util.Set;

public interface PortingAlgorithm {

    /**
     * Returns the selected out port from a list of out ports.
     * @param outPorts
     * @return
     */
    PortNumber out(Set<PortNumber> outPorts);

}
