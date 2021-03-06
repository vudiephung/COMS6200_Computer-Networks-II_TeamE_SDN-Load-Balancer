package org.defaultLB.app;

import java.nio.ByteBuffer;
import java.util.*;

import org.onlab.packet.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.PortStatistics;
import org.onosproject.net.flow.*;
import org.onosproject.net.packet.*;
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

    // Change packet processor subclass here to change the algorithm.
    PacketProcessor pktprocess = new PacketHash();

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

    // Override the packetProcessor class
    private class DefaultLB implements PacketProcessor {

        protected final Map<PortNumber, MacAddress> serverAddresses;
        protected final Map<IpAddress, MacAddress> clientMap;
        protected DeviceId switchId;
        protected IpPrefix srcIpPrefix;
        protected TpPort srcPort;

        public DefaultLB() {
            super();
            serverAddresses = new HashMap<>();
            clientMap = new HashMap<>();
        }

        @Override
        public void process(PacketContext pktIn) {

            InboundPacket pkt = pktIn.inPacket();

            DeviceId switchId = pkt.receivedFrom().deviceId();
            inPort = pkt.receivedFrom().port();

            Ethernet ethernetPacket = pkt.parsed();
            IPacket payload = pkt.parsed().getPayload();

            MacAddress sourceMacAddress = ethernetPacket.getSourceMAC();

            // the mac address we are going to tell "clients" the load balancer is
            // this is sort of a "fake" address that represents our load balancer
            MacAddress loadBalancerMAC = MacAddress.valueOf("be:ef:66:66:66:66");

            if(payload instanceof ARP) {

                // handle ARP requests
                ARP packet = (ARP) payload;

                boolean isFromServer = inPort.toLong() != 1;

                if(isFromServer) {
                    // this is a request from a server, so map port to MAC address
                    serverAddresses.put(inPort, sourceMacAddress);
                } else {
                    // this is a request from a client, so map IP to MAC address
                    clientMap.put(IpAddress.valueOf(IpAddress.Version.INET, packet.getSenderProtocolAddress()), sourceMacAddress);
                }

                // send it back to the requesting port as a reply
                TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                        .setOutput(inPort)
                        .build();

                // the IP address being queried, WHOIS targetIP?
                IpAddress targetIP = IpAddress.valueOf(IpAddress.Version.INET, packet.getTargetProtocolAddress());

                // build the ARP reply
                Ethernet reply = ARP.buildArpReply(
                        IpAddress.valueOf(IpAddress.Version.INET, packet.getTargetProtocolAddress()).getIp4Address(),
                        isFromServer ? clientMap.get(targetIP) : loadBalancerMAC,
                        // ^ if it is a request from the server, we give it the real address of the client
                        // if it is a client we give it the "fake" mac address of our load balancing switch
                        ARP.buildArpRequest(
                                packet.getSenderHardwareAddress(),
                                packet.getSenderProtocolAddress(),
                                packet.getTargetHardwareAddress(),
                                packet.getTargetProtocolAddress(),
                                packet.getSenderHardwareAddress(),
                                (short) 0
                        )
                );

                // turn this packet into a byte buffer
                ByteBuffer b = ByteBuffer.wrap(reply.serialize());

                // send it
                packetService.emit(new DefaultOutboundPacket(switchId, treatment, b));

                if (isFromServer) {
                    // drop the original packet
                    pktIn.treatmentBuilder().drop();
                } else {
                    // send client requests to servers, allowing controller ARP table to be populated.
                    pktIn.treatmentBuilder().setOutput(PortNumber.FLOOD);
                    pktIn.send();
                }

            } else if(payload instanceof IPv4) {

                // handle TCP packets (web requests etc)

                srcIpPrefix = IpPrefix.valueOf(((IPv4) payload).getSourceAddress(), 32);
                IpPrefix dstIpPrefix = IpPrefix.valueOf(((IPv4) payload).getDestinationAddress(), 32);

                IPacket innerPayload = payload.getPayload();

                // not a TCP or ICMP packet
                if(!(innerPayload instanceof TCP || innerPayload instanceof ICMP)) {
                    return;
                }

                if(!serverAddresses.containsValue(sourceMacAddress)) {
                    // This is a client request
                    PortNumber targetServerPort = out();

                    if (targetServerPort == null) {
                        return;
                    }

                    MacAddress targetServer = serverAddresses.get(targetServerPort);

                    if (targetServer == null) {
                        return;
                    }

                    log.info(targetServer.toString());

                    // Selectors for client to server and server to client
                    TrafficSelector.Builder c2s_selector = DefaultTrafficSelector.builder();
                    TrafficSelector.Builder s2c_selector = DefaultTrafficSelector.builder();

                    if(innerPayload instanceof TCP) {
                        TCP tcpPayload = (TCP)innerPayload;
                        srcPort = TpPort.tpPort(tcpPayload.getSourcePort());

                        c2s_selector
                                .matchEthType(Ethernet.TYPE_IPV4) // these match a "TCP packet"
                                .matchIPProtocol(IPv4.PROTOCOL_TCP) // you will get a PREREQ error if you try and use TCP matches without this
                                .matchEthDst(loadBalancerMAC) // the client sends to the "load balancer mac"
                                .matchEthSrc(sourceMacAddress) // the "source" client
                                .matchIPSrc(srcIpPrefix)
                                .matchIPDst(dstIpPrefix)
                                .matchTcpSrc(srcPort) // the clients source port
                                .matchTcpDst(TpPort.tpPort(tcpPayload.getDestinationPort())); // the port on the server

                        s2c_selector
                                .matchEthType(Ethernet.TYPE_IPV4)
                                .matchIPProtocol(IPv4.PROTOCOL_TCP)
                                .matchEthDst(sourceMacAddress) // the server sends to the clients MAC address
                                .matchInPort(targetServerPort) // this is the physical port the server is on (easier to use than MAC IMO, as there is one server per port)
                                .matchIPDst(srcIpPrefix)
                                .matchTcpDst(srcPort); // responding to the client request sent from this port

                    } else {

                        c2s_selector
                                .matchEthType(Ethernet.TYPE_IPV4)
                                .matchIPProtocol(IPv4.PROTOCOL_ICMP)
                                .matchEthDst(loadBalancerMAC)
                                .matchEthSrc(sourceMacAddress)
                                .matchIPSrc(srcIpPrefix)
                                .matchIPDst(dstIpPrefix);

                        s2c_selector
                                .matchEthType(Ethernet.TYPE_IPV4)
                                .matchIPProtocol(IPv4.PROTOCOL_ICMP)
                                .matchEthDst(sourceMacAddress)
                                .matchInPort(targetServerPort)
                                .matchIPDst(srcIpPrefix);
                    }

                    TrafficTreatment c2s_treatment = DefaultTrafficTreatment.builder()
                            .setEthDst(targetServer) // real MAC address
                            .setOutput(targetServerPort) // real (switch) port
                            .build();

                    FlowRule c2s_flowRule = DefaultFlowRule.builder()
                            .forDevice(switchId)
                            .withSelector(c2s_selector.build())
                            .withTreatment(c2s_treatment)
                            .withPriority(50000)
                            .fromApp(appId)
                            .withIdleTimeout(30)
                            .build();

                    TrafficTreatment s2c_treatment = DefaultTrafficTreatment.builder()
                            .setEthSrc(loadBalancerMAC) // load balancer MAC address
                            .setOutput(inPort) // real (switch) port
                            .build();

                    FlowRule s2c_flowRule = DefaultFlowRule.builder()
                            .forDevice(switchId)
                            .withSelector(s2c_selector.build())
                            .withTreatment(s2c_treatment)
                            .withPriority(50000)
                            .fromApp(appId)
                            .withIdleTimeout(30)
                            .build();

                    flowRuleService.applyFlowRules(c2s_flowRule);
                    flowRuleService.applyFlowRules(s2c_flowRule);

                    // drop the first packet, it will be re-sent anyway as a re-transmit as this is TCP
                    // if you want to avoid this, you need to do a custom packet emit like before
                    pktIn.treatmentBuilder().drop();
                }
            }

            // all other packets are dropped

        }

        protected PortNumber out() {
            return PortNumber.portNumber(2);
        }
    }

    private class RoundRobin extends DefaultLB {

        private Iterator<PortNumber> iterator;

        public RoundRobin() {
            super();
        }

        @Override
        protected PortNumber out() {
            Set<PortNumber> outPorts = serverAddresses.keySet();

            if (outPorts.size() == 0) {
                return null;
            } else if (iterator == null || !iterator.hasNext()) {
                iterator = List.copyOf(outPorts).iterator();
            }
            return iterator.next();
        }
    }

    private class RandomisedStatic extends DefaultLB {

        private Iterator<PortNumber> iterator;

        public RandomisedStatic() {
            super();
        }

        @Override
        protected PortNumber out() {
            Set<PortNumber> outPorts = serverAddresses.keySet();

            if (outPorts.size() == 0) {
                return null;
            } else if (iterator == null || !iterator.hasNext()) {
                List<PortNumber> outPortsList = new ArrayList<>(outPorts);
                Collections.shuffle(outPortsList);
                iterator = outPortsList.iterator();
            }
            return iterator.next();
        }
    }

    private class PacketHash extends DefaultLB {

        public PacketHash() {
            super();
        }

        @Override
        protected PortNumber out() {
            if (serverAddresses.size() == 0) {
                return PortNumber.portNumber(2);
            }
            int pkthash = srcIpPrefix.hashCode();
            if (srcPort != null) {
                pkthash += srcPort.hashCode();
            }
            return PortNumber.portNumber((pkthash % serverAddresses.size()) + 2);
        }
    }

    private class PacketBased extends DefaultLB {

        public PacketBased() {
            super();
        }

        @Override
        protected PortNumber out() {
            Set<PortNumber> outPorts = serverAddresses.keySet();

            if (outPorts.size() == 0) {
                return null;
            }
            Iterator<PortNumber> iterator = List.copyOf(outPorts).iterator();

            if (deviceService == null || deviceService.getPortStatistics(switchId).size() == 0) {
                return null;
            }
            Map<PortNumber, Integer> portThresholds = new HashMap<>();

            // count how many flow rules exist for each server, to update map of port thresholds
            for (PortStatistics stat : deviceService.getPortStatistics(switchId)) {

                for (PortNumber checkPort : outPorts) {
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