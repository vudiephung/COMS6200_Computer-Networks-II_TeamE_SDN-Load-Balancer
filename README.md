# COMS6200 Team E Load Balancer
This load balancer implements several load balancing algorithms all of which determine which server is to be assigned to handle a given HTTP request.

## Architecture

### Example Network Architecture

![Example Network Architecture](/img/architecture.png)

Where:
* h1 is a client connecting to the setup. There may be several clients connecting to the load balancer concurrently.
* s1 is the switch / load balancer.
* v1, v2 and v3 are the servers being load balanced. There can be any number of these servers being load balanced.

### Load Balancer Class Diagram

![Architecture of Load Balancer](/img/coms6200_class_diagram.png)

## Processes

### ARP

When handling ARP packets, the following process takes place:
1. A client sends an ARP request containing its IP and MAC addresses to a given IP (all the servers share this IP).
2. The switch / load balancer receives this request and stores a IP-MAC address mapping.
3. The load balancer responds to the client with a "fake" MAC address that represents the load balancer.
4. The load balancer sends the request to each of the connected servers.
5. Each client replies to the load balancer with ARP packets containing their MAC addresses. The load balancer then uses this to map each port to each MAC address.
6. The client replies are dropped, as this would otherwise overwrite the reply sent by the load balancer to the client.

This process is important for the following reasons:
* Clients will store an entry in their ARP table that maps the IP of the load balancer setup (which is known) with a MAC address provided by the load balancer, rather than ones provided by a server. This abstracts the load balancer setup to the clients, making it appear as though the load balancer setup is one server. This also prevents several ARP replies from all of the servers being sent to the client and overwriting one another.
* The process populates a table mapping SDN switch ports (which are known to the load balancer) with MAC addresses (which are not known), which is important for further handling of packets and scales with varying numbers of servers.

### TCP/ICMP

When a packet is sent to the load balancer, the following process take place:
1. If the packet is IPv4, the payload of the packet is retrieved. Otherwise it gets dropped.
2. If the payload is not a TCP or ICMP packet, the packet is dropped. Otherwise the load balancer determines which server the packet should be sent to using one of the algorithms.
3. Flow rules are installed into the switch which match the protocol, destination and source MAC addresses, destination and source IP addresses, SDN Switch in ports, and TCP source and destination ports to Traffic treatments which 'bind' the client and chosen servers.
4. The packet is dropped. This is not a problem for TCP as the packet will be retransmitted but for ICMP, this results in the first few packets being dropped.
5. Subsequent requests are sorted according to the flow table rules installed in Step 3.

## Algorithms

### Default Implementation

By default, the load balancer will always select port 2 to send packets to. As a result, only the server connected to port 2 can handle requests. 

### Round Robin

In Round Robin (RR), the load balancer repeatedly iterates through a list of ports to select a port.

### Randomised Static

In Randomised Static, the load balancer simply selects a port randomly.

### PacketHash

To select a chosen port, the source IP address and TCP port of the packet will be hashed using the Java hashCode() and modulo. This is then used to select a port from a list of ports. As a result, packets with the same source IP address and TCP port will be assigned to the same server (or just IP address if packet is ICMP).

### PacketBased

To select a chosen port, the threshold values must be checked against by first counting how many flow entries exists for each port.  If there are less than 300 flow entries that applies to a port from a list of port, then that port is selected.  The list of ports iterates from port 2 to port 3 to port 4 to port 2 etc.  If all ports are full, then port 2 will be chosen.

![Flow diagram of PacketBased](/img/packetbased_flowchart.png)
