package org.defaultLB.app;

import org.onosproject.cfg.ComponentConfigService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.onlab.packet.MacAddress;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.TrafficTreatment;

import static org.onlab.util.Tools.get;
import java.util.Dictionary;
import java.util.Properties;

//#################### New Imports  #######################
// In order to use reference and deal with the core service
import org.onosproject.core.CoreService;
import org.onosproject.core.ApplicationId;

// In order to register for the pkt-in event
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketService;

// In order you install matching rules (traffic selector)
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.FlowRuleService;

// In order to get access to packet header
import org.onlab.packet.Ethernet;
import org.onosproject.net.packet.PacketPriority;
import java.util.Optional;

// In order to access Pkt-In header
import org.onosproject.net.PortNumber;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.DeviceId;
import java.util.Map;
import java.util.HashMap;

@Component(immediate = true)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Some configurable property. */
    private String someProperty;

    // ################ Instantiates the relevant services ################
    // Register component
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    // Add, delete matching rules of the selector
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    // Process incoming packets
    PacketProcessor pktprocess = new DefaultLB();

    private ApplicationId appId;
    private PortNumber inPort, outPort;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    Map<MacAddress, PortNumber> mytable = new HashMap<MacAddress, PortNumber>();

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
        @Override
        public void process(PacketContext pktIn) {

            InboundPacket pkt = pktIn.inPacket();

            // Get the following information from incoming pkt
            MacAddress dstMac = pkt.parsed().getDestinationMAC();
            MacAddress srcMac = pkt.parsed().getSourceMAC();
            DeviceId switchId = pkt.receivedFrom().deviceId();

            // Learn the MAC address of the sender
            inPort = pkt.receivedFrom().port();

            // Store this information to learn for next time
            mytable.put(srcMac, inPort);

            // Check if the MAC address exists in the table or not. If yes,
            // get the port and send the packet directly. If not, flood
            if (mytable.containsKey(dstMac)) {
                outPort = (PortNumber) mytable.get(dstMac);
            } else {
                // ********* outPort should be returned from specific LB algorithm instead of flooding *******
                outPort = PortNumber.FLOOD;
                pktIn.treatmentBuilder().setOutput(outPort);
                pktIn.send();

                // Install forwarding rules (based on src and dst address) to avoid Pkt-in next time

                // TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
                // selector.matchEthDst(dstMac).matchEthSrc(srcMac);
                // TrafficTreatment treatment =
                // DefaultTrafficTreatment.builder().setOutput(outPort).build();
                // FlowRule flowRule =
                // DefaultFlowRule.builder().forDevice(switchId).withSelector(selector.build())
                // .withTreatment(treatment).withPriority(50000).fromApp(appId).makePermanent().build();
                // flowRuleService.applyFlowRules(flowRule);
            }

            if (outPort != PortNumber.FLOOD) {
                pktIn.treatmentBuilder().setOutput(outPort);
                pktIn.send();
            }
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
