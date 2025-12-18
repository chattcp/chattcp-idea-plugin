package com.chattcp.service;

import java.io.File;

/**
 * Helper class to configure JNA for IntelliJ plugin environment
 */
public class JnaHelper {
    
    private static boolean initialized = false;
    
    /**
     * Initialize JNA configuration
     * Must be called before any JNA/pcap4j usage
     */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        
        try {
            // Set JNA library path to system temp directory
            String tmpDir = System.getProperty("java.io.tmpdir");
            String jnaDir = tmpDir + File.separator + "jna-" + System.currentTimeMillis();
            
            // Create JNA directory
            File jnaDirFile = new File(jnaDir);
            if (!jnaDirFile.exists()) {
                jnaDirFile.mkdirs();
            }
            
            // Configure JNA to extract native libraries to this directory
            System.setProperty("jna.tmpdir", jnaDir);
            System.setProperty("jna.nosys", "true"); // Don't use system-wide JNA
            System.setProperty("jna.noclasspath", "false"); // Use classpath
            
            // Enable JNA debug mode for troubleshooting
            // System.setProperty("jna.debug_load", "true");
            // System.setProperty("jna.debug_load.jna", "true");
            
            System.out.println("JNA initialized with tmpdir: " + jnaDir);
            
            // Try to load JNA Native class to trigger extraction
            Class.forName("com.sun.jna.Native");
            
            initialized = true;
            System.out.println("JNA successfully initialized");
            
        } catch (Exception e) {
            System.err.println("Failed to initialize JNA: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("JNA initialization failed", e);
        }
    }
    
    /**
     * Check if JNA is available and working
     */
    public static boolean isJnaAvailable() {
        try {
            initialize();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
