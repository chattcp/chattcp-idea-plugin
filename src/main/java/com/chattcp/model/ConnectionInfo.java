package com.chattcp.model;

import java.util.ArrayList;
import java.util.List;

public class ConnectionInfo {
    private final String id;
    private final String sourceIp;
    private final int sourcePort;
    private final String destIp;
    private final int destPort;
    private final List<PacketInfo> packets;

    public ConnectionInfo(String id, String sourceIp, int sourcePort, String destIp, int destPort) {
        this.id = id;
        this.sourceIp = sourceIp;
        this.sourcePort = sourcePort;
        this.destIp = destIp;
        this.destPort = destPort;
        this.packets = new ArrayList<>();
    }

    public void addPacket(PacketInfo packet) {
        packets.add(packet);
    }

    public String getId() {
        return id;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public int getSourcePort() {
        return sourcePort;
    }

    public String getDestIp() {
        return destIp;
    }

    public int getDestPort() {
        return destPort;
    }

    public List<PacketInfo> getPackets() {
        return packets;
    }

    @Override
    public String toString() {
        return sourceIp + ":" + sourcePort + " → " + destIp + ":" + destPort;
    }
}
