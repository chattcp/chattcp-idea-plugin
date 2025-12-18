package com.chattcp.ui;

import com.chattcp.model.ConnectionInfo;
import com.chattcp.model.PacketInfo;
import com.chattcp.service.PacketCaptureService;
import com.chattcp.service.PermissionManager;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class ChatTCPPanel extends JPanel {
    
    private final PacketCaptureService captureService;
    
    private final JComboBox<String> interfaceComboBox;
    private final JTextField portField;
    private final JButton startButton;
    private final JButton stopButton;
    
    private final DefaultListModel<ConnectionInfo> connectionListModel;
    private final JList<ConnectionInfo> connectionList;
    
    private final JPanel packetPanel;
    private final JBScrollPane packetScrollPane;
    
    private final JButton uploadButton;
    private final JButton openWithChatTCPButton;
    private final JLabel filePathLabel;
    
    private volatile boolean updateScheduled = false;

    // Deep blue-gray background color matching the reference UI
    private static final Color PANEL_BACKGROUND = new Color(32, 39, 49);
    
    /**
     * Create a styled button with rounded corners and no blue background
     */
    private JButton createStyledButton(String text) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Draw rounded rectangle background
                if (getModel().isPressed()) {
                    g2.setColor(new Color(50, 60, 70));
                } else if (getModel().isRollover() && isEnabled()) {
                    g2.setColor(new Color(70, 80, 90));
                } else {
                    g2.setColor(getBackground());
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                
                // Draw border
                g2.setColor(new Color(80, 90, 100));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                
                g2.dispose();
                super.paintComponent(g);
            }
        };
        
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setBackground(new Color(60, 70, 80));
        button.setForeground(new Color(220, 220, 220));
        button.setBorder(JBUI.Borders.empty(4, 12));
        
        // Set height but let width auto-size based on text
        Dimension preferredSize = button.getPreferredSize();
        button.setPreferredSize(new Dimension(preferredSize.width, 28));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        
        return button;
    }
    
    /**
     * Check if ChatTCP app is installed on macOS
     */
    private boolean isChatTCPInstalled() {
        try {
            // Try multiple methods to detect ChatTCP
            
            // Method 1: Try to find ChatTCP.app in common locations
            String[] commonPaths = {
                "/Applications/ChatTCP.app",
                System.getProperty("user.home") + "/Applications/ChatTCP.app"
            };
            
            for (String path : commonPaths) {
                if (new java.io.File(path).exists()) {
                    System.out.println("Found ChatTCP at: " + path);
                    return true;
                }
            }
            
            // Method 2: Use mdfind to search for ChatTCP app
            Process process = Runtime.getRuntime().exec(new String[]{"mdfind", "kMDItemKind == 'Application' && kMDItemDisplayName == 'ChatTCP'"});
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            if (line != null && !line.isEmpty()) {
                System.out.println("Found ChatTCP via mdfind: " + line);
                return true;
            }
            
            // Method 3: Try to check if 'open -a ChatTCP' would work
            Process testProcess = Runtime.getRuntime().exec(new String[]{"sh", "-c", "which -s open && echo found"});
            java.io.BufferedReader testReader = new java.io.BufferedReader(new java.io.InputStreamReader(testProcess.getInputStream()));
            String testResult = testReader.readLine();
            
            System.out.println("ChatTCP not found in common locations");
            return false;
        } catch (Exception e) {
            System.err.println("Error checking for ChatTCP: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Open file with ChatTCP app on macOS
     */
    private void openWithChatTCP(String filePath) {
        try {
            Runtime.getRuntime().exec(new String[]{"open", "-a", "ChatTCP", filePath});
            System.out.println("Opened file with ChatTCP: " + filePath);
        } catch (Exception e) {
            System.err.println("Failed to open with ChatTCP: " + e.getMessage());
            JOptionPane.showMessageDialog(this, 
                "Failed to open with ChatTCP:\n" + e.getMessage(), 
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    public ChatTCPPanel(PacketCaptureService captureService) {
        super(new BorderLayout());
        this.captureService = captureService;
        
        // Set panel background
        setBackground(PANEL_BACKGROUND);
        setOpaque(true);
        
        this.interfaceComboBox = new JComboBox<>();
        this.portField = new JTextField("8080", 10);
        this.startButton = createStyledButton("Start");
        this.stopButton = createStyledButton("Stop");
        
        this.connectionListModel = new DefaultListModel<>();
        this.connectionList = new JList<>(connectionListModel);
        
        this.packetPanel = new JPanel();
        this.packetScrollPane = new JBScrollPane(packetPanel);
        
        this.uploadButton = createStyledButton("Upload Analysis");
        this.openWithChatTCPButton = createStyledButton("Open with ChatTCP");
        this.filePathLabel = new JLabel("Capture file: Not started");
        this.filePathLabel.setForeground(new Color(180, 180, 180));
        this.filePathLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        
        // Check if ChatTCP is available on macOS
        String osName = System.getProperty("os.name").toLowerCase();
        boolean isMac = osName.contains("mac");
        System.out.println("Operating System: " + osName);
        System.out.println("Is macOS: " + isMac);
        
        boolean hasChatTCP = false;
        if (isMac) {
            hasChatTCP = isChatTCPInstalled();
            System.out.println("ChatTCP installed: " + hasChatTCP);
        }
        
        // If ChatTCP is available, show "Open with ChatTCP" button, otherwise show "Upload Analysis"
        boolean showChatTCPButton = isMac && hasChatTCP;
        openWithChatTCPButton.setVisible(showChatTCPButton);
        uploadButton.setVisible(!showChatTCPButton);
        
        // Initially disable buttons until capture starts
        openWithChatTCPButton.setEnabled(false);
        uploadButton.setEnabled(false);
        
        System.out.println("Open with ChatTCP button visible: " + openWithChatTCPButton.isVisible());
        System.out.println("Upload Analysis button visible: " + uploadButton.isVisible());
        
        // 设置最小宽度
        setMinimumSize(new Dimension(400, 300));
        setPreferredSize(new Dimension(500, 600));
        
        setupUI();
        setupListeners();
        
        // 延迟加载网络接口，避免阻塞 UI 初始化
        SwingUtilities.invokeLater(this::loadNetworkInterfaces);
    }

    private void setupUI() {
        // 顶部控制面板 - 使用 BoxLayout 水平布局
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.X_AXIS));
        topPanel.setBorder(JBUI.Borders.empty(5, 10, 5, 10));
        topPanel.setBackground(PANEL_BACKGROUND);
        
        topPanel.add(new JLabel("Interface:"));
        topPanel.add(Box.createHorizontalStrut(5));
        interfaceComboBox.setMaximumSize(new Dimension(180, 30));
        topPanel.add(interfaceComboBox);
        
        topPanel.add(Box.createHorizontalStrut(10));
        topPanel.add(new JLabel("Port:"));
        topPanel.add(Box.createHorizontalStrut(5));
        portField.setMaximumSize(new Dimension(70, 30));
        topPanel.add(portField);
        
        topPanel.add(Box.createHorizontalStrut(10));
        topPanel.add(startButton);
        topPanel.add(Box.createHorizontalStrut(5));
        topPanel.add(stopButton);
        
        topPanel.add(Box.createHorizontalGlue()); // 填充剩余空间
        
        stopButton.setEnabled(false);
        
        // 中间连接列表 - 深色主题
        connectionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        connectionList.setCellRenderer(new ConnectionListCellRenderer());
        connectionList.setBackground(PANEL_BACKGROUND);
        connectionList.setForeground(new Color(220, 220, 220));
        connectionList.setSelectionBackground(new Color(130, 80, 190));
        connectionList.setSelectionForeground(Color.WHITE);
        connectionList.setFocusable(false); // Remove blue focus border
        
        JBScrollPane connectionScrollPane = new JBScrollPane(connectionList);
        connectionScrollPane.setBorder(BorderFactory.createTitledBorder("TCP Connections"));
        connectionScrollPane.getViewport().setBackground(PANEL_BACKGROUND);
        
        // 数据包显示面板 - 深色背景
        packetPanel.setLayout(new BoxLayout(packetPanel, BoxLayout.Y_AXIS));
        packetPanel.setBorder(JBUI.Borders.empty(0));
        packetPanel.setBackground(PANEL_BACKGROUND);
        packetScrollPane.setBorder(BorderFactory.createTitledBorder("Packets"));
        packetScrollPane.getViewport().setBackground(PANEL_BACKGROUND);
        
        // 底部面板 - 使用 BorderLayout
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(JBUI.Borders.empty(10));
        bottomPanel.setBackground(PANEL_BACKGROUND);
        
        // 文件路径显示
        JPanel fileInfoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        fileInfoPanel.setBackground(PANEL_BACKGROUND);
        fileInfoPanel.add(filePathLabel);
        
        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(PANEL_BACKGROUND);
        buttonPanel.add(openWithChatTCPButton);
        buttonPanel.add(uploadButton);
        
        bottomPanel.add(fileInfoPanel, BorderLayout.WEST);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);
        
        // 中间分割面板
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setTopComponent(connectionScrollPane);
        splitPane.setBottomComponent(packetScrollPane);
        splitPane.setDividerLocation(200);
        splitPane.setResizeWeight(0.3);
        
        // 自定义分割线颜色以匹配深色主题
        splitPane.setBackground(PANEL_BACKGROUND);
        splitPane.setBorder(null);
        splitPane.setDividerSize(5);
        
        // 设置分割线颜色
        splitPane.setUI(new javax.swing.plaf.basic.BasicSplitPaneUI() {
            @Override
            public javax.swing.plaf.basic.BasicSplitPaneDivider createDefaultDivider() {
                return new javax.swing.plaf.basic.BasicSplitPaneDivider(this) {
                    @Override
                    public void paint(Graphics g) {
                        g.setColor(new Color(50, 58, 68)); // 稍微亮一点的深色
                        g.fillRect(0, 0, getWidth(), getHeight());
                    }
                };
            }
        });
        
        // 组装主面板
        add(topPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void setupListeners() {
        startButton.addActionListener(e -> {
            System.out.println("Start button clicked");
            
            // Check permissions first
            System.out.println("Checking permissions...");
            boolean hasPerms = PermissionManager.hasPermissions();
            System.out.println("Permission check result: " + hasPerms);
            
            if (!hasPerms) {
                System.out.println("Permissions not granted, requesting...");
                // Request permissions (this will show the dialog)
                boolean granted = PermissionManager.requestPermissions(this);
                System.out.println("Permission grant result: " + granted);
                
                if (!granted) {
                    // User cancelled or permission grant failed
                    return;
                }
            }
            
            String selectedInterface = (String) interfaceComboBox.getSelectedItem();
            String portText = portField.getText();
            
            System.out.println("Selected interface: " + selectedInterface);
            System.out.println("Port: " + portText);
            
            try {
                int port = Integer.parseInt(portText);
                if (selectedInterface != null) {
                    System.out.println("Starting capture on " + selectedInterface + ":" + port);
                    
                    // Clear previous data when starting new capture
                    connectionListModel.clear();
                    packetPanel.removeAll();
                    packetPanel.revalidate();
                    packetPanel.repaint();
                    
                    captureService.startCapture(selectedInterface, port, (connection, packet) -> {
                        // Batch updates to reduce UI thread load
                        SwingUtilities.invokeLater(() -> {
                            addOrUpdateConnection(connection);
                            if (connectionList.getSelectedValue() != null && 
                                connectionList.getSelectedValue().getId().equals(connection.getId())) {
                                addPacketToDisplay(packet);
                            }
                        });
                    });
                    
                    System.out.println("Capture started successfully");
                    startButton.setEnabled(false);
                    stopButton.setEnabled(true);
                    interfaceComboBox.setEnabled(false);
                    portField.setEnabled(false);
                    
                    // Enable file operation buttons
                    openWithChatTCPButton.setEnabled(true);
                    uploadButton.setEnabled(true);
                    
                    // Update file path label
                    String pcapFile = captureService.getCurrentPcapFile();
                    if (pcapFile != null) {
                        filePathLabel.setText("Capture file: " + pcapFile);
                    }
                }
            } catch (NumberFormatException ex) {
                System.err.println("Invalid port number: " + portText);
                JOptionPane.showMessageDialog(this, "Please enter a valid port number", "Error", JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                System.err.println("Failed to start capture: " + ex.getMessage());
                ex.printStackTrace();
                
                // Show detailed error message
                String errorMsg = "Failed to start packet capture.\n\n";
                errorMsg += "Error: " + ex.getMessage() + "\n\n";
                
                if (ex.getMessage() != null && ex.getMessage().contains("Permission denied")) {
                    errorMsg += "This usually means:\n";
                    errorMsg += "• You need administrator/root permissions\n";
                    errorMsg += "• Try granting permissions again\n";
                    errorMsg += "• On macOS: Run 'sudo chmod o+r /dev/bpf*' in Terminal\n";
                } else if (ex.getMessage() != null && ex.getMessage().contains("No such device")) {
                    errorMsg += "The selected network interface may not exist.\n";
                    errorMsg += "Please select a different interface.";
                } else {
                    errorMsg += "Please check:\n";
                    errorMsg += "• libpcap is installed (macOS/Linux)\n";
                    errorMsg += "• Npcap is installed (Windows)\n";
                    errorMsg += "• You have proper permissions\n";
                }
                
                JOptionPane.showMessageDialog(this, errorMsg, "Capture Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        
        stopButton.addActionListener(e -> {
            captureService.stopCapture();
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            interfaceComboBox.setEnabled(true);
            portField.setEnabled(true);
            // Don't clear data when stopping - keep it for review
        });
        
        connectionList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                ConnectionInfo selected = connectionList.getSelectedValue();
                if (selected != null) {
                    displayPacketsForConnection(selected);
                }
            }
        });
        
        openWithChatTCPButton.addActionListener(e -> {
            // Check if capture is still running
            if (stopButton.isEnabled()) {
                JOptionPane.showMessageDialog(this, 
                    "Please stop capturing first before opening the file.", 
                    "Capture In Progress", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            String pcapFile = captureService.getCurrentPcapFile();
            if (pcapFile != null && new java.io.File(pcapFile).exists()) {
                openWithChatTCP(pcapFile);
            } else {
                JOptionPane.showMessageDialog(this, 
                    "No capture file available.", 
                    "Info", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        
        uploadButton.addActionListener(e -> {
            // Check if capture is still running
            if (stopButton.isEnabled()) {
                JOptionPane.showMessageDialog(this, 
                    "Please stop capturing first before uploading the file.", 
                    "Capture In Progress", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            String pcapFile = captureService.getCurrentPcapFile();
            if (pcapFile != null) {
                String message = "Captured packets saved to:\n" + pcapFile + 
                               "\n\nYou can upload this file to a server for further analysis.\n" +
                               "Upload feature coming soon...";
                JOptionPane.showMessageDialog(this, message, "Pcap File Info", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "No capture file available.", 
                                            "Info", JOptionPane.INFORMATION_MESSAGE);
            }
        });
    }

    private void loadNetworkInterfaces() {
        // 先添加加载提示
        interfaceComboBox.addItem("Loading...");
        
        // 在后台线程加载网络接口
        new Thread(() -> {
            try {
                System.out.println("Loading network interfaces...");
                List<String> interfaces = captureService.getNetworkInterfaces();
                System.out.println("Loaded " + interfaces.size() + " interfaces");
                
                SwingUtilities.invokeLater(() -> {
                    interfaceComboBox.removeAllItems();
                    int loopbackIndex = -1;
                    
                    for (int i = 0; i < interfaces.size(); i++) {
                        String iface = interfaces.get(i);
                        interfaceComboBox.addItem(iface);
                        System.out.println("Added interface: " + iface);
                        
                        // Find loopback interface (127.0.0.1)
                        if (loopbackIndex == -1 && iface.contains("127.0.0.1")) {
                            loopbackIndex = i;
                        }
                    }
                    
                    // Select loopback interface if found, otherwise select first
                    if (!interfaces.isEmpty()) {
                        if (loopbackIndex >= 0) {
                            interfaceComboBox.setSelectedIndex(loopbackIndex);
                            System.out.println("Selected loopback interface at index: " + loopbackIndex);
                        } else {
                            interfaceComboBox.setSelectedIndex(0);
                            System.out.println("Loopback not found, selected first interface");
                        }
                    }
                });
            } catch (Exception e) {
                System.err.println("Failed to load network interfaces: " + e.getMessage());
                e.printStackTrace();
                
                final String errorMsg = e.getMessage();
                SwingUtilities.invokeLater(() -> {
                    interfaceComboBox.removeAllItems();
                    interfaceComboBox.addItem("Error: " + errorMsg);
                    JOptionPane.showMessageDialog(
                        ChatTCPPanel.this,
                        "Failed to load network interfaces:\n" + errorMsg + 
                        "\n\nMake sure:\n" +
                        "1. libpcap is installed (macOS/Linux) or Npcap (Windows)\n" +
                        "2. You have proper permissions\n" +
                        "3. JNA library is properly loaded",
                        "Network Interface Error",
                        JOptionPane.ERROR_MESSAGE
                    );
                });
            }
        }, "NetworkInterfaceLoader").start();
    }

    private void addOrUpdateConnection(ConnectionInfo connection) {
        int index = -1;
        for (int i = 0; i < connectionListModel.size(); i++) {
            if (connectionListModel.get(i).getId().equals(connection.getId())) {
                index = i;
                break;
            }
        }
        
        if (index >= 0) {
            connectionListModel.set(index, connection);
        } else {
            connectionListModel.addElement(connection);
            if (connectionListModel.size() == 1) {
                connectionList.setSelectedIndex(0);
            }
        }
    }

    private void displayPacketsForConnection(ConnectionInfo connection) {
        packetPanel.removeAll();
        
        List<PacketInfo> packets = connection.getPackets();
        int maxPackets = 100;
        
        // Only show the most recent packets
        int startIndex = Math.max(0, packets.size() - maxPackets);
        for (int i = startIndex; i < packets.size(); i++) {
            PacketInfo packet = packets.get(i);
            PacketChatView packetView = new PacketChatView(packet);
            packetPanel.add(packetView);
        }
        
        packetPanel.revalidate();
        packetPanel.repaint();
        
        // Scroll to bottom
        SwingUtilities.invokeLater(() -> {
            JScrollBar scrollBar = packetScrollPane.getVerticalScrollBar();
            scrollBar.setValue(scrollBar.getMaximum());
        });
    }

    private void addPacketToDisplay(PacketInfo packet) {
        // Limit number of displayed packets to prevent performance issues
        int maxPackets = 100;
        if (packetPanel.getComponentCount() >= maxPackets) {
            // Remove oldest packet
            packetPanel.remove(0);
        }
        
        PacketChatView packetView = new PacketChatView(packet);
        packetPanel.add(packetView);
        
        // Batch UI updates to reduce repaints
        if (!updateScheduled) {
            updateScheduled = true;
            SwingUtilities.invokeLater(() -> {
                packetPanel.revalidate();
                packetPanel.repaint();
                
                JScrollBar scrollBar = packetScrollPane.getVerticalScrollBar();
                scrollBar.setValue(scrollBar.getMaximum());
                
                updateScheduled = false;
            });
        }
    }
}
