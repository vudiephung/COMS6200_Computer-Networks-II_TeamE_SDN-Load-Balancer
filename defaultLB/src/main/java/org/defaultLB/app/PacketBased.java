package org.defaultLB.app;

import org.onlab.packet.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.flow.*;
import org.onosproject.net.flow.criteria.*;
import org.onosproject.net.flow.criteria.Criterion.*;
import org.onosproject.net.PortNumber;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PacketBased implements PortingAlgorithm {
    Iterator<PortNumber> iterator;
    ArrayList<FlowEntry> flowEntryList;
    Map<PortNumber, Integer> portThresholds;

    public PacketBased() {
        iterator = null;
    }

    @Override
    public PortNumber out(Set<PortNumber> outPorts, FlowRuleService flowRuleService, ApplicationId appId) {
        if (outPorts == null || outPorts.size() == 0) {
            return null;
        }
        iterator = List.copyOf(outPorts).iterator();
        
        if (flowRuleService == null || flowRuleService.getFlowRuleCount() == 0) {
            return null;
        }
        portThresholds = new HashMap<>();

        // count how many flow rules exist for each server, to update map of port thresholds
        for (FlowRule rule : flowRuleService.getFlowEntriesById(appId)) {
            TrafficSelector selector = rule.selector();
            PortCriterion criterion = (PortCriterion) selector.getCriterion(Criterion.Type.TCP_DST);

            if (criterion != null) {
                PortNumber criteria = criterion.port();

                if (criteria != null) {
                    if (!portThresholds.containsKey(criteria)) {
                        portThresholds.put(criteria, 1);
                    } else {
                        portThresholds.replace(criteria, portThresholds.get(criteria) + 1);
                    }
                }
            }
        }

        while (iterator.hasNext()) {
            PortNumber port = iterator.next();

            if (!portThresholds.containsKey(port)) {
                portThresholds.put(port, 1);
                return port;
            } else if (portThresholds.get(port) < 3) {  // change number accordingly to set port threshold
                portThresholds.replace(port, portThresholds.get(port)+1);
                return port;
            }
        }
        return iterator.next();
    }
}
