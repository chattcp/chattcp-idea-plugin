package com.chattcp.service;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/**
 * Manages system permissions for packet capture
 * Requests permissions dynamically through GUI dialogs
 */
public class PermissionManager {
    
    private static final String OS = System.getProperty("os.name").toLowerCase();
    
    /**
     * Check if we have packet capture permissions
     */
    public static boolean hasPermissions() {
        try {
            if (isMac()) {
                return checkMacPermissions();
            } else if (isLinux()) {
                return checkLinuxPermissions();
            } else if (isWindows()) {
                return checkWindowsPermissions();
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }
    
    /**
     * Request permissions with GUI dialog
     * Returns true if permissions were granted
     */
    public static boolean requestPermissions(JComponent parent) {
        if (hasPermissions()) {
            return true;
        }
        
        String message = buildPermissionMessage();
        String title = "ChatTCP Requires Permissions";
        
        int result = JOptionPane.showConfirmDialog(
            parent,
            message,
            title,
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        
        if (result != JOptionPane.OK_OPTION) {
            return false;
        }
        
        // Show password dialog and execute permission command
        return executePermissionGrant(parent);
    }
    
    private static String buildPermissionMessage() {
        StringBuilder msg = new StringBuilder();
        msg.append("ChatTCP needs system permissions to capture network packets.\n\n");
        
        if (isMac()) {
            msg.append("This will:\n");
            msg.append("• Grant read access to /dev/bpf* devices\n");
            msg.append("• Require your administrator password\n");
            msg.append("• Only needs to be done once\n\n");
            msg.append("Click OK to grant permissions.");
        } else if (isLinux()) {
            msg.append("This will:\n");
            msg.append("• Grant network capture capabilities to Java\n");
            msg.append("• Require your sudo password\n");
            msg.append("• Only needs to be done once per Java update\n\n");
            msg.append("Click OK to grant permissions.");
        } else if (isWindows()) {
            msg.append("Please install Npcap:\n");
            msg.append("1. Download from: https://npcap.com/\n");
            msg.append("2. Run installer as Administrator\n");
            msg.append("3. Check 'WinPcap API-compatible Mode'\n\n");
            msg.append("After installation, restart the plugin.");
        }
        
        return msg.toString();
    }
    
    private static boolean executePermissionGrant(JComponent parent) {
        try {
            if (isMac()) {
                return grantMacPermissions(parent);
            } else if (isLinux()) {
                return grantLinuxPermissions(parent);
            } else if (isWindows()) {
                // Windows requires manual Npcap installation
                openUrl("https://npcap.com/");
                JOptionPane.showMessageDialog(
                    parent,
                    "Please download and install Npcap, then restart the plugin.",
                    "Manual Installation Required",
                    JOptionPane.INFORMATION_MESSAGE
                );
                return false;
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(
                parent,
                "Failed to grant permissions: " + e.getMessage(),
                "Permission Error",
                JOptionPane.ERROR_MESSAGE
            );
            return false;
        }
        return false;
    }
    
    private static boolean grantMacPermissions(JComponent parent) {
        try {
            // Create AppleScript with custom prompt message
            String script = String.format(
                "do shell script \"chmod o+r /dev/bpf*\" " +
                "with prompt \"ChatTCP needs to access network devices for packet capture.\" " +
                "with administrator privileges"
            );
            
            ProcessBuilder pb = new ProcessBuilder(
                "osascript",
                "-e", script
            );
            
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                JOptionPane.showMessageDialog(
                    parent,
                    "Permissions granted successfully!\nYou can now capture packets.",
                    "Success",
                    JOptionPane.INFORMATION_MESSAGE
                );
                return true;
            } else {
                // User cancelled or error occurred
                return false;
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    private static boolean grantLinuxPermissions(JComponent parent) {
        try {
            // Find Java binary
            String javaPath = System.getProperty("java.home") + "/bin/java";
            File javaFile = new File(javaPath);
            if (!javaFile.exists()) {
                javaPath = findJavaPath();
            }
            
            if (javaPath == null) {
                JOptionPane.showMessageDialog(
                    parent,
                    "Could not find Java binary path.",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
                );
                return false;
            }
            
            // Try to use pkexec (GUI sudo) or gksudo
            String[] commands = {
                "pkexec", "setcap", "cap_net_raw,cap_net_admin=eip", javaPath
            };
            
            ProcessBuilder pb = new ProcessBuilder(commands);
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                JOptionPane.showMessageDialog(
                    parent,
                    "Permissions granted successfully!\nYou can now capture packets.",
                    "Success",
                    JOptionPane.INFORMATION_MESSAGE
                );
                return true;
            } else {
                // Try with terminal-based sudo
                return grantLinuxPermissionsTerminal(parent, javaPath);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    private static boolean grantLinuxPermissionsTerminal(JComponent parent, String javaPath) {
        try {
            // Show instructions for terminal
            String command = "sudo setcap cap_net_raw,cap_net_admin=eip " + javaPath;
            
            JTextArea textArea = new JTextArea(
                "Please run this command in a terminal:\n\n" +
                command + "\n\n" +
                "Then click OK to continue."
            );
            textArea.setEditable(false);
            textArea.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));
            
            JOptionPane.showMessageDialog(
                parent,
                new JScrollPane(textArea),
                "Terminal Command Required",
                JOptionPane.INFORMATION_MESSAGE
            );
            
            // Check if permissions were granted
            return checkLinuxPermissions();
            
        } catch (Exception e) {
            return false;
        }
    }
    
    private static boolean checkMacPermissions() {
        try {
            // Try to actually open a BPF device to test real permissions
            // File.canRead() is not reliable for device files
            // macOS can have up to 256 BPF devices (0-255)
            boolean foundDevice = false;
            boolean hasPermission = false;
            
            for (int i = 0; i < 256; i++) {
                File bpf = new File("/dev/bpf" + i);
                if (bpf.exists()) {
                    foundDevice = true;
                    try {
                        // Try to open for reading
                        java.io.FileInputStream fis = new java.io.FileInputStream(bpf);
                        fis.close();
                        System.out.println("Successfully opened " + bpf.getPath());
                        hasPermission = true;
                        break; // Found one we can access
                    } catch (Exception e) {
                        // Permission denied or device busy
                        String msg = e.getMessage();
                        if (msg != null && msg.contains("Permission denied")) {
                            // Definitely no permission
                            System.out.println("Permission denied for " + bpf.getPath());
                            return false;
                        }
                        // Device might be busy, try next one
                        continue;
                    }
                }
            }
            
            if (!foundDevice) {
                System.out.println("No BPF devices found");
                return false;
            }
            
            if (!hasPermission) {
                System.out.println("All BPF devices are busy, assuming no permission");
                return false;
            }
            
            return true;
        } catch (Exception e) {
            System.err.println("Error checking Mac permissions: " + e.getMessage());
            return false;
        }
    }
    
    private static boolean checkLinuxPermissions() {
        try {
            String javaPath = System.getProperty("java.home") + "/bin/java";
            ProcessBuilder pb = new ProcessBuilder("getcap", javaPath);
            Process process = pb.start();
            
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );
            String line = reader.readLine();
            
            return line != null && 
                   line.contains("cap_net_raw") && 
                   line.contains("cap_net_admin");
                   
        } catch (Exception e) {
            return false;
        }
    }
    
    private static boolean checkWindowsPermissions() {
        // Check if Npcap is installed
        File npcapDir = new File("C:\\Windows\\System32\\Npcap");
        if (npcapDir.exists()) {
            return true;
        }
        
        // Check for WinPcap
        File winpcapDir = new File("C:\\Windows\\System32\\wpcap.dll");
        return winpcapDir.exists();
    }
    
    private static String findJavaPath() {
        try {
            ProcessBuilder pb = new ProcessBuilder("which", "java");
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );
            String path = reader.readLine();
            
            if (path != null) {
                // Resolve symlinks
                pb = new ProcessBuilder("readlink", "-f", path);
                process = pb.start();
                reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
                );
                String realPath = reader.readLine();
                return realPath != null ? realPath : path;
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
    
    private static void openUrl(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            }
        } catch (Exception e) {
            // Ignore
        }
    }
    
    private static boolean isMac() {
        return OS.contains("mac");
    }
    
    private static boolean isLinux() {
        return OS.contains("linux");
    }
    
    private static boolean isWindows() {
        return OS.contains("win");
    }
}
