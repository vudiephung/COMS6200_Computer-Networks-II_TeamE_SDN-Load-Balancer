package org.defaultLB.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.onlab.packet.MacAddress;
import org.onosproject.net.PortNumber;

public class RoundRobin implements PortingAlgorithm {
    // Get set of visited PortNumber(s) corresponding to src MAC address
    Map<MacAddress,ArrayList<PortNumber>> visitedPortsTbl = new HashMap<MacAddress,ArrayList<PortNumber>>();
    PortNumber outPort;

    public PortNumber out(ArrayList<PortNumber> outPorts, MacAddress srcMac, MacAddress dstMac) {
        //  If the visited port table doesn't contain the visited ports corresponding to given srcMac
        if (!visitedPortsTbl.containsKey(srcMac)) {
            visitedPortsTbl.put(srcMac, new ArrayList<PortNumber>());
        }

        // Get the visited ports from the visited ports table
        ArrayList<PortNumber> visitedPorts = visitedPortsTbl.get(srcMac);

        // If the visited list is empty
        if (visitedPorts.size() == 0) {
            // Get first port in the outPorts list
            outPort = outPorts.get(0);
            // Add to visited list
            visitedPorts.add(outPort);
        } else {
            for (PortNumber p : outPorts) {
                // Get the port hasn't been visited yet
                if (!visitedPorts.contains(p)) {
                    outPort = p;
                    visitedPorts.add(p);
                    // If all ports are visited
                    if (visitedPorts.equals(outPorts)) {
                        // Reset the visited list
                        visitedPorts.clear();
                    }
                    break;
                }
            }
        }

        return outPort;
    }
}
