package org.defaultLB.app;

import org.onlab.packet.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.flow.*;
import org.onosproject.net.PortNumber;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceService;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RoundRobin implements PortingAlgorithm {
    Iterator<PortNumber> iterator;

    public RoundRobin() {
        iterator = null;
    }

    @Override
    public PortNumber out(Map<PortNumber, MacAddress> serverAddresses, DeviceService deviceService, DeviceId switchId) {
        Set<PortNumber> outPorts = serverAddresses.keySet();

        if (outPorts == null || outPorts.size() == 0) {
            return null;
        } else if (iterator == null || !iterator.hasNext()) {
            iterator = List.copyOf(outPorts).iterator();
        }
        return iterator.next();
    }
}
