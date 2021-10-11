package org.defaultLB.app;

import org.onlab.packet.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.flow.*;
import org.onosproject.net.PortNumber;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceService;

import java.util.*;

public class RandomisedStatic implements PortingAlgorithm {
    Iterator<PortNumber> iterator;

    public RandomisedStatic() {
        iterator = null;
    }

    @Override
    public PortNumber out(Map<PortNumber, MacAddress> serverAddresses, DeviceService deviceService, DeviceId switchId) {
        Set<PortNumber> outPorts = serverAddresses.keySet();

        if (outPorts == null || outPorts.size() == 0) {
            return null;
        } else if (iterator == null || !iterator.hasNext()) {
            List<PortNumber> outPortsList = new ArrayList<PortNumber>();
            outPortsList.addAll(outPorts);
            Collections.shuffle(outPortsList);
            iterator = outPortsList.iterator();
        }
        return iterator.next();
    }
}
