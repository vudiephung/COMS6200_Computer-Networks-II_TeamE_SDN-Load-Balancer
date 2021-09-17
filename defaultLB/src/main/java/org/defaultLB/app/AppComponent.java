package org.defaultLB.app;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());

    // ################ Instantiates the relevant services ################
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private DeviceService deviceService;

    PacketProcessor pktprocess = new DefaultLB();
    private ApplicationId appId;
    private PortNumber inPort, outPort;

    @Activate
    protected void activate() {
        // Register component at the core
        appId = coreService.registerApplication("org.defaultLB.app");

        // Add listener for the pkt-in event with priority
        packetService.addProcessor(pktprocess, PacketProcessor.director(1));

        // Add matching rules on incoming packets
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();

        // Set ARP and IPv4 packets to be forwarded
        selector.matchEthType(Ethernet.TYPE_ARP);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId, Optional.empty());
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId, Optional.empty());

        log.info("Default LB started");
    }

    public class RoundRobin {
        Set<PortNumber> outPorts;
        Set<PortNumber> visited;
        PortNumber outPort;

        public RoundRobin(Set<PortNumber> outPorts) {
            this.outPorts = outPorts;
            this.visited = new HashSet<PortNumber>();
        }

        PortNumber out() {
            if (visited.size() == 0) {
                outPort = outPorts.iterator().next();
                visited.add(outPort);
            } else {
                for (PortNumber p : outPorts) {
                    if (!visited.contains(p)) {
                        outPort = p;
                        visited.add(p);
                        if (visited.equals(outPorts)) {
                            visited.clear();
                        }
                        break;
                    }
                }
            }
            return outPort;
        }
    }

    // Override the packetProcessor class
    private class DefaultLB implements PacketProcessor {
        @Override
        public void process(PacketContext pktIn) {

            InboundPacket pkt = pktIn.inPacket();

            // Get the following information from incoming pkt
            MacAddress dstMac = pkt.parsed().getDestinationMAC();
            MacAddress srcMac = pkt.parsed().getSourceMAC();
            DeviceId switchId = pkt.receivedFrom().deviceId();
            inPort = pkt.receivedFrom().port();

            // Get switch ports
            List<Port> switchPorts = deviceService.getPorts(switchId);

            // outPorts is the set of possible output PortNumber (excluding the incoming port and the LOCAL port)
            Set<PortNumber> outPorts = new HashSet<PortNumber>();
            for (Port p : switchPorts) {
                if (p.isEnabled() && !p.number().equals(inPort) && !p.number().equals(PortNumber.LOCAL)) {
                    outPorts.add(p.number());
                }
            }

            RoundRobin rr = new RoundRobin(outPorts);
            outPort = rr.out();

            pktIn.treatmentBuilder().setOutput(outPort);
            pktIn.send();
        }
    }

    // Cancel all selectors
    private void withdrawIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
        selector.matchEthType(Ethernet.TYPE_ARP);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

    @Deactivate
    protected void deactivate() {
        log.info("Default LB Stopped");
        packetService.removeProcessor(pktprocess);
        flowRuleService.removeFlowRulesById(appId);
        withdrawIntercepts();
    }
}