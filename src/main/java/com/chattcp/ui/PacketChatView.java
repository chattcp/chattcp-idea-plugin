package com.chattcp.ui;

import com.chattcp.model.PacketInfo;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class PacketChatView extends JPanel {
    private final PacketInfo packet;
    private JPanel bubblePanel;
    private JTextArea payloadArea;

    // Color scheme matching the reference UI
    private static final Color BACKGROUND_COLOR = new Color(32, 39, 49);
    private static final Color CLIENT_BUBBLE_COLOR = new Color(55, 65, 75);
    private static final Color SERVER_BUBBLE_COLOR = new Color(130, 80, 190);
    private static final Color TEXT_COLOR = new Color(220, 220, 220);
    private static final Color ICON_BG_COLOR = new Color(60, 70, 80);

    public PacketChatView(PacketInfo packet) {
        super(new BorderLayout());
        this.packet = packet;
        setBackground(BACKGROUND_COLOR);
        setOpaque(true);
        setBorder(JBUI.Borders.empty(8, 10));
        setupUI();
        
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateBubbleWidth();
            }
        });
    }
    
    @Override
    public Dimension getMaximumSize() {
        Dimension pref = getPreferredSize();
        return new Dimension(Integer.MAX_VALUE, pref.height);
    }

    private void setupUI() {
        boolean isClient = packet.isFromClient();
        
        // Timestamp label above bubble with IP:port
        String ipPort = packet.getSourceIp() + ":" + packet.getSourcePort();
        String timestampText = ipPort + "  " + packet.getTimestamp();
        JLabel timestampLabel = new JLabel(timestampText);
        timestampLabel.setForeground(new Color(140, 140, 140));
        timestampLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        
        // Align timestamp based on direction
        if (isClient) {
            timestampLabel.setHorizontalAlignment(SwingConstants.LEFT);
            timestampLabel.setBorder(JBUI.Borders.empty(0, 46, 4, 0)); // Left align with bubble
        } else {
            timestampLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            timestampLabel.setBorder(JBUI.Borders.empty(0, 0, 4, 46)); // Right align with bubble
        }
        
        // Main container with icon and bubble
        JPanel mainContainer = new JPanel(new BorderLayout(10, 0));
        mainContainer.setOpaque(false);
        
        // Icon panel (left for client, right for server)
        JPanel iconPanel = createIconPanel(isClient);
        
        // Bubble panel with rounded corners
        Color bubbleColor = isClient ? CLIENT_BUBBLE_COLOR : SERVER_BUBBLE_COLOR;
        bubblePanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Draw rounded rectangle background
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                
                g2.dispose();
            }
        };
        bubblePanel.setLayout(new BoxLayout(bubblePanel, BoxLayout.Y_AXIS));
        bubblePanel.setBorder(JBUI.Borders.empty(12, 16, 12, 16)); // Increased padding
        bubblePanel.setBackground(bubbleColor);
        bubblePanel.setOpaque(false);
        
        // Create header with seq, ack, flags
        JPanel headerPanel = createHeaderPanel(isClient);
        bubblePanel.add(headerPanel);
        
        // WebSocket data content (prioritize WebSocket over raw payload)
        String displayContent = null;
        if (packet.getWebsocketData() != null && !packet.getWebsocketData().isEmpty()) {
            displayContent = packet.getWebsocketData();
        } else if (packet.getPayload() != null && !packet.getPayload().isEmpty()) {
            displayContent = packet.getPayload();
        }
        
        if (displayContent != null) {
            if (displayContent.length() > 1000) {
                displayContent = displayContent.substring(0, 1000) + "\n... (truncated, total: " + displayContent.length() + " chars)";
            }
            
            // Add separator
            JSeparator separator = new JSeparator();
            separator.setForeground(new Color(255, 255, 255, 30));
            separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
            bubblePanel.add(Box.createVerticalStrut(8));
            bubblePanel.add(separator);
            bubblePanel.add(Box.createVerticalStrut(8));
            
            payloadArea = new JTextArea(displayContent);
            payloadArea.setEditable(false);
            payloadArea.setLineWrap(true);
            payloadArea.setWrapStyleWord(false);
            payloadArea.setBackground(bubblePanel.getBackground());
            payloadArea.setForeground(TEXT_COLOR);
            payloadArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
            payloadArea.setBorder(JBUI.Borders.empty(4, 0, 0, 0));
            
            // Don't set fixed rows - let it grow naturally
            // Calculate preferred size based on content
            payloadArea.setRows(0); // Remove row limit
            
            bubblePanel.add(payloadArea);
        }
        
        // Wrapper to control bubble width
        JPanel bubbleWrapper = new JPanel(new FlowLayout(isClient ? FlowLayout.LEFT : FlowLayout.RIGHT, 0, 0));
        bubbleWrapper.setOpaque(false);
        bubbleWrapper.add(bubblePanel);
        
        // Layout based on direction
        if (isClient) {
            mainContainer.add(iconPanel, BorderLayout.WEST);
            mainContainer.add(bubbleWrapper, BorderLayout.CENTER);
        } else {
            mainContainer.add(bubbleWrapper, BorderLayout.CENTER);
            mainContainer.add(iconPanel, BorderLayout.EAST);
        }
        
        // Add timestamp at top, main container at center
        add(timestampLabel, BorderLayout.NORTH);
        add(mainContainer, BorderLayout.CENTER);
    }
    
    private JPanel createIconPanel(boolean isClient) {
        JPanel iconPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Draw circle
                g2.setColor(isClient ? ICON_BG_COLOR : SERVER_BUBBLE_COLOR);
                g2.fillOval(0, 0, 36, 36);
                
                // Draw icon (C for client, S for server)
                g2.setColor(TEXT_COLOR);
                g2.setFont(new Font("SansSerif", Font.BOLD, 16));
                FontMetrics fm = g2.getFontMetrics();
                String letter = isClient ? "C" : "S";
                int x = (36 - fm.stringWidth(letter)) / 2;
                int y = (36 + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(letter, x, y);
                
                g2.dispose();
            }
        };
        iconPanel.setPreferredSize(new Dimension(36, 36));
        iconPanel.setOpaque(false);
        return iconPanel;
    }
    
    private JPanel createHeaderPanel(boolean isClient) {
        // Use a container that allows wrapping
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.X_AXIS));
        headerPanel.setOpaque(false);
        headerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        // seq
        JLabel seqLabel = new JLabel("seq: " + packet.getSeq());
        seqLabel.setForeground(TEXT_COLOR);
        seqLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        headerPanel.add(seqLabel);
        headerPanel.add(Box.createHorizontalStrut(8));
        
        // ack (if present in flags)
        if (packet.getFlags().contains("ACK")) {
            JLabel ackLabel = new JLabel("ack: 0");
            ackLabel.setForeground(TEXT_COLOR);
            ackLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
            headerPanel.add(ackLabel);
            headerPanel.add(Box.createHorizontalStrut(8));
        }
        
        // flag label
        JLabel flagLabel = new JLabel("flag:");
        flagLabel.setForeground(TEXT_COLOR);
        flagLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        headerPanel.add(flagLabel);
        headerPanel.add(Box.createHorizontalStrut(8));
        
        // Flag badges
        String[] flags = packet.getFlags().split(",");
        for (String flag : flags) {
            flag = flag.trim();
            if (!flag.isEmpty()) {
                JLabel flagBadge = createFlagBadge(flag);
                flagBadge.setAlignmentY(Component.CENTER_ALIGNMENT);
                headerPanel.add(flagBadge);
                headerPanel.add(Box.createHorizontalStrut(6));
            }
        }
        
        // Add glue to push everything to the left
        headerPanel.add(Box.createHorizontalGlue());
        
        return headerPanel;
    }
    
    private JLabel createFlagBadge(String flag) {
        // Color based on flag type
        Color badgeColor;
        switch (flag) {
            case "SYN":
                badgeColor = new Color(180, 80, 200);
                break;
            case "ACK":
                badgeColor = new Color(100, 120, 180);
                break;
            case "PSH":
                badgeColor = new Color(140, 80, 160);
                break;
            case "FIN":
                badgeColor = new Color(200, 80, 80);
                break;
            case "RST":
                badgeColor = new Color(220, 60, 60);
                break;
            default:
                badgeColor = new Color(100, 100, 120);
        }
        
        JLabel badge = new JLabel(flag) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Draw rounded rectangle background
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                
                g2.dispose();
                super.paintComponent(g);
            }
        };
        badge.setFont(new Font("SansSerif", Font.BOLD, 11));
        badge.setForeground(Color.WHITE);
        badge.setOpaque(false);
        badge.setBorder(JBUI.Borders.empty(2, 8));
        badge.setBackground(badgeColor);
        
        return badge;
    }
    
    private void updateBubbleWidth() {
        int containerWidth = getWidth();
        if (containerWidth > 0) {
            // 80% width minus icon and margins
            int maxBubbleWidth = (int) (containerWidth * 0.8) - 36 - 30;
            
            // Only set max width, let bubble size based on content
            bubblePanel.setMaximumSize(new Dimension(maxBubbleWidth, Integer.MAX_VALUE));
            bubblePanel.setPreferredSize(null); // Remove fixed width
            
            if (payloadArea != null) {
                // Set columns for text wrapping
                int columns = Math.max(30, maxBubbleWidth / 7);
                payloadArea.setColumns(columns);
            }
            
            revalidate();
            repaint();
        }
    }
}
