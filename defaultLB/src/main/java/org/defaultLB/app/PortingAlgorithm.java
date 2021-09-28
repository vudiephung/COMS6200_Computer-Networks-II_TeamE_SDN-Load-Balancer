package org.defaultLB.app;

import org.onlab.packet.MacAddress;
import org.onosproject.net.PortNumber;

import java.util.ArrayList;

public interface PortingAlgorithm {

    /**
     * Returns the selected out port from a list of out ports.
     * @param outPorts
     * @return
     */
    public PortNumber out(ArrayList<PortNumber> outPorts, MacAddress srcMac, MacAddress dstMac);

}
