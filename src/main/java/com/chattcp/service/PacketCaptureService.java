package com.chattcp.service;

import com.chattcp.model.ConnectionInfo;
import com.chattcp.model.PacketInfo;
import org.pcap4j.core.*;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.IpV6Packet;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.TcpPacket;
import org.pcap4j.packet.namednumber.TcpPort;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Packet capture service using pcap4j library
 */
public class PacketCaptureService {
    
    private volatile boolean isCapturing = false;
    private final ConcurrentHashMap<String, ConnectionInfo> connections = new ConcurrentHashMap<>();
    private Thread captureThread;
    private PcapHandle handle;
    private PcapDumper dumper;
    private String currentPcapFile;
    
    private static final int SNAPLEN = 64 * 1024;
    private static final int TIMEOUT = 10;
    
    // Static initializer to configure JNA before any usage
    static {
        try {
            JnaHelper.initialize();
        } catch (Exception e) {
            System.err.println("Warning: JNA initialization failed in static block: " + e.getMessage());
        }
    }
    
    /**
     * Get all available network interfaces with IP addresses
     */
    public List<String> getNetworkInterfaces() throws PcapNativeException {
        // Ensure JNA is initialized
        JnaHelper.initialize();
        
        List<String> interfaceNames = new ArrayList<>();
        
        List<PcapNetworkInterface> allDevs = Pcaps.findAllDevs();
        
        if (allDevs == null || allDevs.isEmpty()) {
            throw new PcapNativeException("No network interfaces found. Make sure libpcap is installed and you have proper permissions.");
        }
        
        for (PcapNetworkInterface device : allDevs) {
            String name = device.getName();
            String description = device.getDescription();
            
            // Get IPv4 addresses only (for display)
            StringBuilder ipv4Addresses = new StringBuilder();
            List<org.pcap4j.core.PcapAddress> addresses = device.getAddresses();
            if (addresses != null && !addresses.isEmpty()) {
                for (org.pcap4j.core.PcapAddress addr : addresses) {
                    if (addr.getAddress() != null) {
                        String ipStr = addr.getAddress().getHostAddress();
                        // Only include IPv4 addresses (no colons, which indicate IPv6)
                        if (ipStr != null && !ipStr.contains(":")) {
                            if (ipv4Addresses.length() > 0) {
                                ipv4Addresses.append(", ");
                            }
                            ipv4Addresses.append(ipStr);
                        }
                    }
                }
            }
            
            // Skip interfaces without IPv4 addresses
            if (ipv4Addresses.length() == 0) {
                continue;
            }
            
            // Build display string
            StringBuilder displayName = new StringBuilder(name);
            displayName.append(" [").append(ipv4Addresses).append("]");
            
            if (description != null && !description.isEmpty()) {
                displayName.append(" (").append(description).append(")");
            }
            
            interfaceNames.add(displayName.toString());
        }
        
        System.out.println("Found " + interfaceNames.size() + " network interfaces (including 'any')");
        return interfaceNames;
    }

    /**
     * Start capturing packets
     * Throws exception if capture cannot be started
     */
    public void startCapture(String interfaceName, int port, 
                            BiConsumer<ConnectionInfo, PacketInfo> onPacketReceived) throws Exception {
        if (isCapturing) {
            System.out.println("Already capturing, ignoring start request");
            return;
        }
        
        System.out.println("Starting packet capture...");
        System.out.println("Interface: " + interfaceName);
        System.out.println("Port: " + port);
        
        // Test if we can open the interface before starting thread
        // This will throw exception immediately if there are permission issues
        testInterfaceAccess(interfaceName);
        
        isCapturing = true;
        connections.clear();
        
        // Create pcapng file for this capture session
        currentPcapFile = createPcapFileName(port);
        System.out.println("Saving packets to: " + currentPcapFile);
        
        captureThread = new Thread(() -> {
            try {
                System.out.println("Capture thread started");
                realCapture(interfaceName, port, onPacketReceived);
            } catch (Exception e) {
                System.err.println("Capture failed: " + e.getMessage());
                e.printStackTrace();
                isCapturing = false;
                // Show error in UI
                javax.swing.SwingUtilities.invokeLater(() -> {
                    javax.swing.JOptionPane.showMessageDialog(
                        null,
                        "Packet capture stopped due to error:\n" + e.getMessage(),
                        "Capture Error",
                        javax.swing.JOptionPane.ERROR_MESSAGE
                    );
                });
            } finally {
                System.out.println("Capture thread ended");
            }
        }, "PacketCaptureThread");
        captureThread.setDaemon(true);
        captureThread.start();
        System.out.println("Capture thread launched");
    }
    
    /**
     * Test if we can access the network interface
     * Throws exception immediately if there are permission issues
     */
    private void testInterfaceAccess(String interfaceName) throws Exception {
        String deviceName = interfaceName.split(" ")[0];
        
        PcapNetworkInterface nif = null;
        for (PcapNetworkInterface dev : Pcaps.findAllDevs()) {
            if (dev.getName().equals(deviceName)) {
                nif = dev;
                break;
            }
        }
        
        if (nif == null) {
            throw new Exception("Network interface not found: " + deviceName);
        }
        
        // Try to open the interface briefly to test permissions
        PcapHandle testHandle = null;
        try {
            testHandle = nif.openLive(SNAPLEN, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, TIMEOUT);
            System.out.println("Successfully opened interface for testing: " + deviceName);
        } catch (PcapNativeException e) {
            String errorMsg = e.getMessage();
            System.err.println("Failed to open interface: " + errorMsg);
            
            // Provide helpful error messages
            if (errorMsg != null && (errorMsg.contains("Permission denied") || errorMsg.contains("Operation not permitted"))) {
                throw new Exception("Permission denied. Please grant packet capture permissions.\n" +
                    "On macOS, run: sudo chmod o+r /dev/bpf*");
            } else if (errorMsg != null && errorMsg.contains("No such device")) {
                throw new Exception("Network interface not available: " + deviceName);
            } else {
                throw new Exception("Cannot open network interface: " + (errorMsg != null ? errorMsg : "Unknown error"));
            }
        } finally {
            if (testHandle != null && testHandle.isOpen()) {
                testHandle.close();
            }
        }
    }
    
    /**
     * Create pcap file name with timestamp
     */
    private String createPcapFileName(int port) {
        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
        String userHome = System.getProperty("user.home");
        // Use .pcap extension (pcap4j's dumpOpen creates pcap format, not pcapng)
        return userHome + "/chattcp_" + port + "_" + timestamp + ".pcap";
    }
    
    /**
     * Get current pcap file path
     */
    public String getCurrentPcapFile() {
        return currentPcapFile;
    }

    /**
     * Stop capturing
     */
    public void stopCapture() {
        isCapturing = false;
        
        // Close dumper first
        if (dumper != null) {
            try {
                dumper.close();
                System.out.println("Pcap file saved: " + currentPcapFile);
            } catch (Exception e) {
                System.err.println("Failed to close dumper: " + e.getMessage());
            }
            dumper = null;
        }
        
        if (handle != null && handle.isOpen()) {
            try {
                handle.breakLoop();
                handle.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            handle = null;
        }
        
        if (captureThread != null) {
            captureThread.interrupt();
            captureThread = null;
        }
        // Don't clear connections - keep data for review
    }

    /**
     * Real packet capture implementation using pcap4j
     */
    private void realCapture(String interfaceName, int port, 
                            BiConsumer<ConnectionInfo, PacketInfo> onPacketReceived) throws Exception {
        // Extract interface name (remove description)
        String deviceName = interfaceName.split(" ")[0];
        
        // Find the network interface
        PcapNetworkInterface nif = null;
        for (PcapNetworkInterface dev : Pcaps.findAllDevs()) {
            if (dev.getName().equals(deviceName)) {
                nif = dev;
                break;
            }
        }
        
        if (nif == null) {
            throw new Exception("Network interface not found: " + deviceName);
        }
        
        // Open the interface
        handle = nif.openLive(SNAPLEN, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, TIMEOUT);
        
        // Set BPF filter for TCP packets on specified port
        String filter = "tcp port " + port;
        System.out.println("Setting BPF filter: " + filter);
        handle.setFilter(filter, BpfProgram.BpfCompileMode.OPTIMIZE);
        System.out.println("BPF filter set successfully");
        
        // Open dumper to save packets to file
        try {
            dumper = handle.dumpOpen(currentPcapFile);
            System.out.println("Dumper opened for: " + currentPcapFile);
        } catch (Exception e) {
            System.err.println("Failed to open dumper: " + e.getMessage());
            e.printStackTrace();
            dumper = null;
        }
        
        System.out.println("Starting packet capture loop on " + deviceName + " port " + port);
        
        // Packet listener with dumper
        PacketListener listener = new PacketListener() {
            private int packetCount = 0;
            
            @Override
            public void gotPacket(Packet packet) {
                packetCount++;
                if (packetCount == 1) {
                    System.out.println("First packet received!");
                }
                
                if (!isCapturing) {
                    return;
                }
                
                // Save raw packet to file using handle's timestamp
                if (dumper != null && handle != null) {
                    try {
                        // Use dumpPacket which includes timestamp
                        dumper.dump(packet, handle.getTimestamp());
                        packetCount++;
                        
                        // Flush every 10 packets to ensure data is written to disk
                        if (packetCount % 10 == 0) {
                            dumper.flush();
                            System.out.println("Dumper flushed after " + packetCount + " packets");
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to dump packet: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                
                processPacket(packet, port, onPacketReceived);
            }
        };
        
        // Start capturing loop
        try {
            handle.loop(-1, listener);
        } catch (InterruptedException e) {
            // Normal interruption
        } catch (NotOpenException e) {
            // Handle closed
        } finally {
            // Flush dumper
            if (dumper != null) {
                try {
                    dumper.flush();
                    System.out.println("Dumper flushed");
                } catch (Exception e) {
                    System.err.println("Failed to flush dumper: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Process captured packet
     */
    private void processPacket(Packet packet, int monitorPort,
                              BiConsumer<ConnectionInfo, PacketInfo> onPacketReceived) {
        try {
            if (packet == null) {
                return;
            }
            
            // If it's UnknownPacket, parse raw data directly
            if (packet.getClass().getSimpleName().equals("UnknownPacket")) {
                byte[] rawData = packet.getRawData();
                if (rawData != null && rawData.length > 0) {
                    parseRawPacket(rawData, monitorPort, onPacketReceived);
                }
                return;
            }
            
            // Try to get IP layer (IPv4 or IPv6)
            IpV4Packet ipV4Packet = packet.get(IpV4Packet.class);
            IpV6Packet ipV6Packet = packet.get(IpV6Packet.class);
            
            final String sourceIp;
            final String destIp;
            
            // Try IPv4 first
            if (ipV4Packet != null) {
                sourceIp = ipV4Packet.getHeader().getSrcAddr().getHostAddress();
                destIp = ipV4Packet.getHeader().getDstAddr().getHostAddress();
            } 
            // Try IPv6
            else if (ipV6Packet != null) {
                sourceIp = ipV6Packet.getHeader().getSrcAddr().getHostAddress();
                destIp = ipV6Packet.getHeader().getDstAddr().getHostAddress();
            }
            // If no IP layer, try other methods
            else {
                // On loopback, packets might have different encapsulation
                // Try to get TCP directly or parse raw bytes
                TcpPacket tcpPacket = packet.get(TcpPacket.class);
                if (tcpPacket != null) {
                    processLoopbackTcpPacket(tcpPacket, monitorPort, onPacketReceived);
                    return;
                }
                
                // Try parsing raw data
                byte[] rawData = packet.getRawData();
                if (rawData != null && rawData.length > 0) {
                    parseRawPacket(rawData, monitorPort, onPacketReceived);
                }
                return;
            }
            
            // Get TCP layer
            TcpPacket tcpPacket = packet.get(TcpPacket.class);
            if (tcpPacket == null) {
                return;
            }
            
            // Extract ports
            final int sourcePort = tcpPacket.getHeader().getSrcPort().valueAsInt();
            final int destPort = tcpPacket.getHeader().getDstPort().valueAsInt();
            
            // Determine packet direction
            boolean isFromClient = destPort == monitorPort;
            
            // Create normalized connection ID (always use client->server order)
            String connectionId = createConnectionId(sourceIp, sourcePort, destIp, destPort, monitorPort);
            
            // Get or create connection
            ConnectionInfo connection = connections.computeIfAbsent(connectionId, 
                k -> new ConnectionInfo(connectionId, sourceIp, sourcePort, destIp, destPort)
            );
            
            // Get TCP flags
            String flags = getTcpFlags(tcpPacket);
            
            // Get sequence number
            long seq = tcpPacket.getHeader().getSequenceNumberAsLong();
            
            // Get payload
            byte[] payloadBytes = tcpPacket.getPayload() != null ? 
                tcpPacket.getPayload().getRawData() : new byte[0];
            
            String payload = "";
            int payloadSize = 0;
            String websocketData = null;
            
            if (payloadBytes.length > 0) {
                payloadSize = payloadBytes.length;
                payload = bytesToReadableString(payloadBytes, Math.min(payloadBytes.length, 1024));
                websocketData = parseWebSocket(payloadBytes);
            }
            
            // Create packet info
            PacketInfo packetInfo = new PacketInfo(
                seq,
                flags,
                isFromClient,
                payload,
                payloadSize,
                websocketData,
                sourceIp,
                sourcePort
            );
            
            connection.addPacket(packetInfo);
            onPacketReceived.accept(connection, packetInfo);
            
        } catch (Exception e) {
            // Ignore parsing errors
        }
    }

    /**
     * Get TCP flags as string
     */
    private String getTcpFlags(TcpPacket tcpPacket) {
        List<String> flags = new ArrayList<>();
        TcpPacket.TcpHeader header = tcpPacket.getHeader();
        
        if (header.getSyn()) flags.add("SYN");
        if (header.getAck()) flags.add("ACK");
        if (header.getPsh()) flags.add("PSH");
        if (header.getFin()) flags.add("FIN");
        if (header.getRst()) flags.add("RST");
        if (header.getUrg()) flags.add("URG");
        
        return String.join(",", flags);
    }

    /**
     * Convert bytes to readable string
     * Try UTF-8 first, fall back to hex if not printable
     */
    private String bytesToReadableString(byte[] bytes, int maxLength) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        
        try {
            // Try to decode as UTF-8
            String text = new String(bytes, 0, Math.min(bytes.length, maxLength), "UTF-8");
            
            // Check if the string is mostly printable
            if (isPrintable(text)) {
                if (bytes.length > maxLength) {
                    return text + "\n... (truncated)";
                }
                return text;
            }
        } catch (Exception e) {
            // Fall through to hex
        }
        
        // Fall back to hex for binary data
        return bytesToHexString(bytes, maxLength);
    }
    
    /**
     * Check if string is mostly printable (for display as text)
     */
    private boolean isPrintable(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        
        int printableCount = 0;
        int totalCount = 0;
        
        for (char c : text.toCharArray()) {
            totalCount++;
            // Consider printable: regular ASCII, newlines, tabs, and common Unicode
            if (c >= 32 && c < 127 || c == '\n' || c == '\r' || c == '\t' || c > 127) {
                printableCount++;
            }
        }
        
        // If more than 80% is printable, treat as text
        return totalCount > 0 && (printableCount * 100 / totalCount) > 80;
    }
    
    /**
     * Convert bytes to hex string (for binary data)
     */
    private String bytesToHexString(byte[] bytes, int maxLength) {
        StringBuilder sb = new StringBuilder();
        int length = Math.min(bytes.length, maxLength);
        for (int i = 0; i < length; i++) {
            sb.append(String.format("%02X ", bytes[i]));
            if ((i + 1) % 16 == 0) {
                sb.append("\n");
            }
        }
        if (bytes.length > maxLength) {
            sb.append("\n... (truncated)");
        }
        return sb.toString();
    }

    /**
     * Try to parse WebSocket frame
     */
    private String parseWebSocket(byte[] payload) {
        if (payload == null || payload.length < 2) {
            return null;
        }
        
        try {
            // Check if this looks like HTTP first (common false positive)
            if (payload.length > 4) {
                String start = new String(payload, 0, Math.min(4, payload.length), "UTF-8");
                if (start.startsWith("GET ") || start.startsWith("POST") || 
                    start.startsWith("HTTP") || start.startsWith("PUT ") ||
                    start.startsWith("HEAD") || start.startsWith("DELE")) {
                    return null; // This is HTTP, not WebSocket
                }
            }
            
            int firstByte = payload[0] & 0xFF;
            int secondByte = payload[1] & 0xFF;
            
            boolean fin = (firstByte & 0x80) != 0;
            int opcode = firstByte & 0x0F;
            boolean masked = (secondByte & 0x80) != 0;
            int payloadLen = secondByte & 0x7F;
            
            // WebSocket frames must have valid opcode (0-10)
            // and reasonable payload length
            if (opcode < 0 || opcode > 10 || payloadLen > 125) {
                return null;
            }
            
            // Additional validation: check reserved bits
            int rsv = (firstByte >> 4) & 0x07;
            if (rsv != 0) {
                return null; // Reserved bits should be 0
            }
            
            if (opcode >= 0 && opcode <= 10) {
                StringBuilder result = new StringBuilder();
                result.append("WebSocket Frame:\n");
                result.append("  FIN: ").append(fin).append("\n");
                result.append("  Opcode: ").append(getOpcodeDescription(opcode)).append("\n");
                result.append("  Masked: ").append(masked).append("\n");
                result.append("  Payload Length: ").append(payloadLen).append("\n");
                
                if (opcode == 1 && payloadLen > 0 && payloadLen < 126) {
                    int offset = 2;
                    byte[] maskingKey = null;
                    
                    if (masked) {
                        if (payload.length < offset + 4) {
                            return result.toString();
                        }
                        maskingKey = new byte[4];
                        System.arraycopy(payload, offset, maskingKey, 0, 4);
                        offset += 4;
                    }
                    
                    if (payload.length >= offset + payloadLen) {
                        byte[] data = new byte[payloadLen];
                        for (int i = 0; i < payloadLen; i++) {
                            if (masked && maskingKey != null) {
                                data[i] = (byte) (payload[offset + i] ^ maskingKey[i % 4]);
                            } else {
                                data[i] = payload[offset + i];
                            }
                        }
                        result.append("  Text: ").append(new String(data, "UTF-8"));
                    }
                }
                
                return result.toString();
            }
        } catch (Exception e) {
            // Not a WebSocket packet
        }
        
        return null;
    }

    /**
     * Get WebSocket opcode description
     */
    private String getOpcodeDescription(int opcode) {
        switch (opcode) {
            case 0: return "0 (Continuation)";
            case 1: return "1 (Text)";
            case 2: return "2 (Binary)";
            case 8: return "8 (Close)";
            case 9: return "9 (Ping)";
            case 10: return "10 (Pong)";
            default: return String.valueOf(opcode);
        }
    }
    
    /**
     * Process TCP packet from loopback without IP layer
     */
    private void processLoopbackTcpPacket(TcpPacket tcpPacket, int monitorPort,
                                         BiConsumer<ConnectionInfo, PacketInfo> onPacketReceived) {
        try {
            // For loopback, assume 127.0.0.1
            String sourceIp = "127.0.0.1";
            String destIp = "127.0.0.1";
            
            int sourcePort = tcpPacket.getHeader().getSrcPort().valueAsInt();
            int destPort = tcpPacket.getHeader().getDstPort().valueAsInt();
            
            boolean isFromClient = destPort == monitorPort;
            String connectionId = createConnectionId(sourceIp, sourcePort, destIp, destPort, monitorPort);
            
            ConnectionInfo connection = getOrCreateConnection(
                connectionId, sourceIp, sourcePort, destIp, destPort, monitorPort
            );
            
            String flags = getTcpFlags(tcpPacket);
            long seq = tcpPacket.getHeader().getSequenceNumberAsLong();
            
            byte[] payloadBytes = tcpPacket.getPayload() != null ? 
                tcpPacket.getPayload().getRawData() : new byte[0];
            
            String payload = "";
            int payloadSize = 0;
            String websocketData = null;
            
            if (payloadBytes.length > 0) {
                payloadSize = payloadBytes.length;
                payload = bytesToReadableString(payloadBytes, Math.min(payloadBytes.length, 1024));
                websocketData = parseWebSocket(payloadBytes);
            }
            
            PacketInfo packetInfo = new PacketInfo(
                seq, flags, isFromClient, payload, payloadSize, websocketData, sourceIp, sourcePort
            );
            
            connection.addPacket(packetInfo);
            onPacketReceived.accept(connection, packetInfo);
            
        } catch (Exception e) {
            System.err.println("Error processing loopback TCP packet: " + e.getMessage());
        }
    }
    
    /**
     * Parse raw packet data (fallback for loopback)
     */
    private void parseRawPacket(byte[] rawData, int monitorPort,
                               BiConsumer<ConnectionInfo, PacketInfo> onPacketReceived) {
        try {
            // On macOS loopback, packets start with a 4-byte header
            // Skip it and try to parse as IP packet
            if (rawData.length < 24) {
                return; // Too short
            }
            
            int offset = 0;
            
            // Try to detect and skip link layer headers
            // Different systems use different link layer formats:
            // - Ethernet (Windows/Linux/macOS): 14 bytes
            // - macOS loopback: 4 bytes
            // - Linux loopback: no header (starts with IP)
            // - Windows loopback (Npcap): varies
            
            if (rawData.length > 14) {
                // Check EtherType field (bytes 12-13) for Ethernet frame
                int etherType = ((rawData[12] & 0xFF) << 8) | (rawData[13] & 0xFF);
                
                if (etherType == 0x0800 || etherType == 0x86DD) { // IPv4 or IPv6
                    offset = 14;
                }
            }
            
            // If not Ethernet, check for loopback headers
            if (offset == 0 && rawData.length > 4) {
                // macOS loopback: 4-byte header (0x02000000 for IPv4, 0x1e000000 for IPv6)
                if (rawData[0] == 0x02 || rawData[0] == 0x1e || rawData[0] == 0x00) {
                    offset = 4;
                }
                // Linux loopback: no header, starts directly with IP
                // Windows loopback: varies, but usually starts with IP
                // Try to detect IP version at offset 0
                else {
                    int firstByte = rawData[0] & 0xFF;
                    int possibleVersion = (firstByte >> 4) & 0x0F;
                    if (possibleVersion == 4 || possibleVersion == 6) {
                        offset = 0;
                    }
                }
            }
            
            // Check IP version
            if (offset + 20 > rawData.length) {
                return;
            }
            
            int versionAndHeaderLen = rawData[offset] & 0xFF;
            int version = (versionAndHeaderLen >> 4) & 0x0F;
            
            final String sourceIp;
            final String destIp;
            final int tcpOffset;
            
            if (version == 4) {
                // Parse IPv4 header
                int ipHeaderLen = (versionAndHeaderLen & 0x0F) * 4;
                int protocol = rawData[offset + 9] & 0xFF;
                
                if (protocol != 6) { // TCP = 6
                    return;
                }
                
                // Extract IPv4 addresses
                sourceIp = String.format("%d.%d.%d.%d",
                    rawData[offset + 12] & 0xFF,
                    rawData[offset + 13] & 0xFF,
                    rawData[offset + 14] & 0xFF,
                    rawData[offset + 15] & 0xFF
                );
                
                destIp = String.format("%d.%d.%d.%d",
                    rawData[offset + 16] & 0xFF,
                    rawData[offset + 17] & 0xFF,
                    rawData[offset + 18] & 0xFF,
                    rawData[offset + 19] & 0xFF
                );
                
                tcpOffset = offset + ipHeaderLen;
            } 
            else if (version == 6) {
                // Parse IPv6 header (40 bytes fixed)
                if (offset + 40 > rawData.length) {
                    return;
                }
                
                int nextHeader = rawData[offset + 6] & 0xFF;
                
                if (nextHeader != 6) { // TCP = 6
                    return;
                }
                
                // Extract IPv6 addresses (16 bytes each)
                sourceIp = formatIPv6Address(rawData, offset + 8);
                destIp = formatIPv6Address(rawData, offset + 24);
                
                tcpOffset = offset + 40;
            } 
            else {
                // Unknown IP version
                return;
            }
            
            // Parse TCP header
            if (tcpOffset + 20 > rawData.length) {
                return;
            }
            
            final int sourcePort = ((rawData[tcpOffset] & 0xFF) << 8) | (rawData[tcpOffset + 1] & 0xFF);
            final int destPort = ((rawData[tcpOffset + 2] & 0xFF) << 8) | (rawData[tcpOffset + 3] & 0xFF);
            
            long seq = ((long)(rawData[tcpOffset + 4] & 0xFF) << 24) |
                      ((long)(rawData[tcpOffset + 5] & 0xFF) << 16) |
                      ((long)(rawData[tcpOffset + 6] & 0xFF) << 8) |
                      (long)(rawData[tcpOffset + 7] & 0xFF);
            
            int tcpFlags = rawData[tcpOffset + 13] & 0xFF;
            String flags = parseTcpFlags(tcpFlags);
            
            boolean isFromClient = destPort == monitorPort;
            String connectionId = createConnectionId(sourceIp, sourcePort, destIp, destPort, monitorPort);
            
            ConnectionInfo connection = getOrCreateConnection(
                connectionId, sourceIp, sourcePort, destIp, destPort, monitorPort
            );
            
            int tcpHeaderLen = ((rawData[tcpOffset + 12] & 0xFF) >> 4) * 4;
            int payloadOffset = tcpOffset + tcpHeaderLen;
            int payloadSize = rawData.length - payloadOffset;
            
            String payload = "";
            String websocketData = null;
            
            if (payloadSize > 0 && payloadOffset < rawData.length) {
                byte[] payloadBytes = new byte[payloadSize];
                System.arraycopy(rawData, payloadOffset, payloadBytes, 0, payloadSize);
                payload = bytesToReadableString(payloadBytes, Math.min(payloadSize, 1024));
                websocketData = parseWebSocket(payloadBytes);
            }
            
            PacketInfo packetInfo = new PacketInfo(
                seq, flags, isFromClient, payload, payloadSize, websocketData, sourceIp, sourcePort
            );
            
            connection.addPacket(packetInfo);
            onPacketReceived.accept(connection, packetInfo);
            
        } catch (Exception e) {
            // Silently ignore parsing errors
        }
    }
    
    /**
     * Parse TCP flags from byte
     */
    private String parseTcpFlags(int flagsByte) {
        List<String> flags = new ArrayList<>();
        if ((flagsByte & 0x02) != 0) flags.add("SYN");
        if ((flagsByte & 0x10) != 0) flags.add("ACK");
        if ((flagsByte & 0x08) != 0) flags.add("PSH");
        if ((flagsByte & 0x01) != 0) flags.add("FIN");
        if ((flagsByte & 0x04) != 0) flags.add("RST");
        if ((flagsByte & 0x20) != 0) flags.add("URG");
        return String.join(",", flags);
    }
    
    /**
     * Format IPv6 address from raw bytes
     */
    private String formatIPv6Address(byte[] data, int offset) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i += 2) {
            if (i > 0) {
                sb.append(":");
            }
            int value = ((data[offset + i] & 0xFF) << 8) | (data[offset + i + 1] & 0xFF);
            sb.append(String.format("%x", value));
        }
        // Simplify consecutive zeros (basic implementation)
        String addr = sb.toString();
        // Replace longest sequence of :0:0:0: with ::
        addr = addr.replaceFirst("(^|:)(0:)+", "::");
        addr = addr.replaceFirst("^::", "::");
        return addr;
    }

    
    /**
     * Create normalized connection ID
     * Always uses client->server order regardless of packet direction
     */
    private String createConnectionId(String ip1, int port1, String ip2, int port2, int monitorPort) {
        // Determine which side is client and which is server
        boolean ip1IsClient = port2 == monitorPort;
        
        if (ip1IsClient) {
            // ip1:port1 is client, ip2:port2 is server
            return ip1 + ":" + port1 + "-" + ip2 + ":" + port2;
        } else {
            // ip2:port2 is client, ip1:port1 is server
            return ip2 + ":" + port2 + "-" + ip1 + ":" + port1;
        }
    }
    
    /**
     * Get connection info with correct client/server order
     */
    private ConnectionInfo getOrCreateConnection(String connectionId, 
                                                 String ip1, int port1, 
                                                 String ip2, int port2, 
                                                 int monitorPort) {
        return connections.computeIfAbsent(connectionId, k -> {
            // Determine which side is client and which is server
            boolean ip1IsClient = port2 == monitorPort;
            
            if (ip1IsClient) {
                return new ConnectionInfo(connectionId, ip1, port1, ip2, port2);
            } else {
                return new ConnectionInfo(connectionId, ip2, port2, ip1, port1);
            }
        });
    }
}
