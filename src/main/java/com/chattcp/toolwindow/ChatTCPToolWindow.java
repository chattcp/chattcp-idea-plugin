/*
 * Copyright (c) 2025 ChatTCP. All rights reserved.
 * Licensed under AGPL-3.0 or Commercial License.
 * See LICENSE file for details.
 */
package com.chattcp.toolwindow;

import com.chattcp.service.PacketCaptureService;
import com.chattcp.ui.ChatTCPPanel;
import com.intellij.openapi.project.Project;

import javax.swing.*;

public class ChatTCPToolWindow {
    private final Project project;
    private final PacketCaptureService captureService;
    private final ChatTCPPanel panel;

    public ChatTCPToolWindow(Project project) {
        this.project = project;
        this.captureService = new PacketCaptureService();
        this.panel = new ChatTCPPanel(captureService);
    }

    public JComponent getContent() {
        return panel;
    }
}
