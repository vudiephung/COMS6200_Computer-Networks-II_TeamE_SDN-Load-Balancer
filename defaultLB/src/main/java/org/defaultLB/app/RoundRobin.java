package org.defaultLB.app;

import org.onlab.packet.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.flow.*;
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
    public PortNumber out(Set<PortNumber> outPorts, FlowRuleService flowRuleService, ApplicationId appId) {
        if (outPorts == null || outPorts.size() == 0) {
            return null;
        } else if (iterator == null || !iterator.hasNext()) {
            iterator = List.copyOf(outPorts).iterator();
        }
        return iterator.next();
    }
}
