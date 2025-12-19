/*
 * Copyright (c) 2025 ChatTCP. All rights reserved.
 * Licensed under AGPL-3.0 or Commercial License.
 * See LICENSE file for details.
 */
package com.chattcp.ui;

import com.chattcp.model.ConnectionInfo;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;

public class ConnectionListCellRenderer extends JPanel implements ListCellRenderer<ConnectionInfo> {
    
    private final JLabel textLabel;
    private boolean isSelected;
    private Color backgroundColor;
    
    public ConnectionListCellRenderer() {
        setLayout(new BorderLayout());
        setBorder(JBUI.Borders.empty(6, 10));
        setOpaque(false);
        
        textLabel = new JLabel();
        textLabel.setOpaque(false);
        textLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        add(textLabel, BorderLayout.CENTER);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends ConnectionInfo> list, 
                                                 ConnectionInfo value, 
                                                 int index, 
                                                 boolean isSelected, 
                                                 boolean cellHasFocus) {
        this.isSelected = isSelected;
        
        if (value != null) {
            // Use normal font size without HTML font-size styling
            String text = "<html>" +
                         "<b>" + value.getSourceIp() + ":" + value.getSourcePort() + "</b> → " +
                         "<b>" + value.getDestIp() + ":" + value.getDestPort() + "</b><br>" +
                         "<span style='color: #aaaaaa;'><small>Packets: " + value.getPackets().size() + "</small></span>" +
                         "</html>";
            textLabel.setText(text);
        }
        
        if (isSelected) {
            backgroundColor = list.getSelectionBackground();
            textLabel.setForeground(list.getSelectionForeground());
        } else {
            backgroundColor = list.getBackground();
            textLabel.setForeground(list.getForeground());
        }
        
        return this;
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        if (isSelected) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // Draw rounded rectangle background for selected item
            g2.setColor(backgroundColor);
            g2.fillRoundRect(5, 2, getWidth() - 10, getHeight() - 4, 8, 8);
            
            g2.dispose();
        }
        super.paintComponent(g);
    }
}
