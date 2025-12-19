/*
 * Copyright (c) 2025 ChatTCP. All rights reserved.
 * Licensed under AGPL-3.0 or Commercial License.
 * See LICENSE file for details.
 */
package com.chattcp.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class PacketInfo {
    private final long seq;
    private final String flags;
    private final String timestamp;
    private final boolean isFromClient;
    private final String payload;
    private final int payloadSize;
    private final String websocketData;
    private final String sourceIp;
    private final int sourcePort;

    public PacketInfo(long seq, String flags, boolean isFromClient, String payload, 
                      int payloadSize, String websocketData, String sourceIp, int sourcePort) {
        this.seq = seq;
        this.flags = flags;
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
        this.isFromClient = isFromClient;
        this.payload = payload;
        this.payloadSize = payloadSize;
        this.websocketData = websocketData;
        this.sourceIp = sourceIp;
        this.sourcePort = sourcePort;
    }
    
    // Backward compatibility constructor
    public PacketInfo(long seq, String flags, boolean isFromClient, String payload, 
                      int payloadSize, String websocketData) {
        this(seq, flags, isFromClient, payload, payloadSize, websocketData, "", 0);
    }

    public long getSeq() {
        return seq;
    }

    public String getFlags() {
        return flags;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public boolean isFromClient() {
        return isFromClient;
    }

    public String getPayload() {
        return payload;
    }

    public int getPayloadSize() {
        return payloadSize;
    }

    public String getWebsocketData() {
        return websocketData;
    }
    
    public String getSourceIp() {
        return sourceIp;
    }
    
    public int getSourcePort() {
        return sourcePort;
    }
}
