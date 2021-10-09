package org.defaultLB.app;

import org.onlab.packet.*;
import org.onosproject.net.flow.*;
import org.onosproject.net.PortNumber;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.*;
import org.onosproject.net.device.DeviceService;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PacketBased implements PortingAlgorithm {
    Iterator<PortNumber> iterator;
    Map<PortNumber, Integer> portThresholds;

    public PacketBased() {
        iterator = null;
    }

    @Override
    public PortNumber out(Map<PortNumber, MacAddress> serverAddresses, DeviceService deviceService, DeviceId switchId) {
        Set<PortNumber> outPorts = serverAddresses.keySet();

        if (outPorts == null || outPorts.size() == 0) {
            return null;
        }
        iterator = List.copyOf(outPorts).iterator();
        
        if (deviceService == null || deviceService.getPortStatistics(switchId).size() == 0) {
            return null;
        }
        portThresholds = new HashMap<>();

        // count how many flow rules exist for each server, to update map of port thresholds
        for (PortStatistics stat : deviceService.getPortStatistics(switchId)) {
            Iterator<PortNumber> serverAddressesIterator = outPorts.iterator();

            while (serverAddressesIterator.hasNext()) {
                PortNumber checkPort = serverAddressesIterator.next();

                if (stat.portNumber() == checkPort) {
                    if (!portThresholds.containsKey(checkPort)) {
                        portThresholds.put(checkPort, 1);
                    } else {
                        portThresholds.replace(checkPort, portThresholds.get(checkPort) + 1);
                    }
                }                
            }
        }

        while (iterator.hasNext()) {
            PortNumber port = iterator.next();

            if (portThresholds.containsKey(port) && portThresholds.get(port) < 1) {
                return port;
            }
        }

        iterator = List.copyOf(outPorts).iterator();
        return iterator.next();
    }
}
