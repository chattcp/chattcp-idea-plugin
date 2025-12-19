/*
 * Copyright (c) 2025 ChatTCP. All rights reserved.
 * Licensed under AGPL-3.0 or Commercial License.
 * See LICENSE file for details.
 */
package com.chattcp.service;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;

/**
 * Advanced macOS permission helper that creates a temporary helper app
 * This allows showing "ChatTCP" instead of "osascript" in the auth dialog
 */
public class MacPermissionHelper {
    
    /**
     * Grant permissions using a temporary helper app
     * This shows "ChatTCP Helper" in the authorization dialog
     */
    public static boolean grantPermissionsWithHelper() {
        try {
            // Create temporary helper app
            File tempDir = Files.createTempDirectory("chattcp-helper").toFile();
            File appDir = new File(tempDir, "ChatTCP Helper.app");
            File contentsDir = new File(appDir, "Contents");
            File macosDir = new File(contentsDir, "MacOS");
            File resourcesDir = new File(contentsDir, "Resources");
            
            macosDir.mkdirs();
            resourcesDir.mkdirs();
            
            // Create Info.plist
            File infoPlist = new File(contentsDir, "Info.plist");
            try (FileWriter writer = new FileWriter(infoPlist)) {
                writer.write(
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n" +
                    "<plist version=\"1.0\">\n" +
                    "<dict>\n" +
                    "    <key>CFBundleExecutable</key>\n" +
                    "    <string>helper</string>\n" +
                    "    <key>CFBundleIdentifier</key>\n" +
                    "    <string>com.chattcp.helper</string>\n" +
                    "    <key>CFBundleName</key>\n" +
                    "    <string>ChatTCP Helper</string>\n" +
                    "    <key>CFBundleVersion</key>\n" +
                    "    <string>1.0</string>\n" +
                    "</dict>\n" +
                    "</plist>"
                );
            }
            
            // Create helper script
            File helperScript = new File(macosDir, "helper");
            try (FileWriter writer = new FileWriter(helperScript)) {
                writer.write(
                    "#!/bin/bash\n" +
                    "osascript -e 'do shell script \"chmod o+r /dev/bpf*\" with administrator privileges'\n"
                );
            }
            helperScript.setExecutable(true);
            
            // Execute helper app
            ProcessBuilder pb = new ProcessBuilder("open", appDir.getAbsolutePath());
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            // Cleanup
            deleteDirectory(tempDir);
            
            return exitCode == 0;
            
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    private static void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        dir.delete();
    }
}
