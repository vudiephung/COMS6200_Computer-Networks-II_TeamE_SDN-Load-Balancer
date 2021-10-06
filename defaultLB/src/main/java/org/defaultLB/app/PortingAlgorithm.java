package org.defaultLB.app;

import org.onlab.packet.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.flow.*;
import org.onosproject.net.PortNumber;

import java.util.Set;

public interface PortingAlgorithm {

    /**
     * Returns the selected out port from a list of out ports.
     * @param outPorts
     * @param flowRuleService
     * @param appId
     * @return
     */
    // PortNumber out(Set<PortNumber> outPorts);
    PortNumber out(Set<PortNumber> outPorts, FlowRuleService flowRuleService, ApplicationId appId);
}
