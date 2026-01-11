package com.experimenteddeveloper.voidcrypt;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main VoidCryptDefenseSystem class that integrates SkriptPlLicenseManager
 * with 3-month licensing and hardware-locked validation.
 * 
 * This system provides:
 * - License management with 3-month validity periods
 * - Hardware-locked validation using MAC addresses
 * - License activation and expiration tracking
 * - Secure license verification
 * 
 * @author ExperimentedDeveloper
 * @version 1.0.0
 * @since 2026-01-11
 */
public class VoidCryptDefenseSystem implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(VoidCryptDefenseSystem.class.getName());
    
    // License constants
    private static final long LICENSE_VALIDITY_DAYS = 90; // 3 months
    private static final String LICENSE_VERSION = "1.0.0";
    private static final String SYSTEM_NAME = "VoidCryptDefenseSystem";
    
    // Instance variables
    private String licenseKey;
    private String hardwareFingerprint;
    private LocalDateTime licenseActivationDate;
    private LocalDateTime licenseExpirationDate;
    private boolean isLicenseValid;
    private LicenseStatus licenseStatus;
    private SkriptPlLicenseManager licenseManager;
    
    // Enumeration for license status
    public enum LicenseStatus {
        NOT_ACTIVATED("License not yet activated"),
        ACTIVE("License is currently active"),
        EXPIRED("License has expired"),
        INVALID_HARDWARE("License is locked to different hardware"),
        REVOKED("License has been revoked"),
        MAINTENANCE_MODE("System running in maintenance mode");
        
        private final String description;
        
        LicenseStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Default constructor - Initializes the VoidCryptDefenseSystem
     */
    public VoidCryptDefenseSystem() {
        this.isLicenseValid = false;
        this.licenseStatus = LicenseStatus.NOT_ACTIVATED;
        this.licenseManager = new SkriptPlLicenseManager();
        this.hardwareFingerprint = generateHardwareFingerprint();
        LOGGER.info("VoidCryptDefenseSystem initialized with hardware fingerprint: " + maskedFingerprint());
    }
    
    /**
     * Constructor with license key
     * 
     * @param licenseKey The license key to activate
     */
    public VoidCryptDefenseSystem(String licenseKey) {
        this();
        this.licenseKey = licenseKey;
        validateAndActivateLicense(licenseKey);
    }
    
    /**
     * Generates a hardware fingerprint based on MAC addresses
     * Uses SHA-256 for secure hashing
     * 
     * @return Hardware fingerprint hash
     */
    private String generateHardwareFingerprint() {
        try {
            StringBuilder macAddress = new StringBuilder();
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                byte[] hardwareAddress = networkInterface.getHardwareAddress();
                
                if (hardwareAddress != null && networkInterface.isUp()) {
                    for (byte b : hardwareAddress) {
                        macAddress.append(String.format("%02X", b));
                    }
                    break; // Use first available MAC address
                }
            }
            
            if (macAddress.length() == 0) {
                // Fallback if no MAC address available
                macAddress.append(InetAddress.getLocalHost().getHostAddress());
            }
            
            // Hash with SHA-256
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(macAddress.toString().getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (NoSuchAlgorithmException | SocketException e) {
            LOGGER.log(Level.WARNING, "Error generating hardware fingerprint", e);
            return "UNKNOWN_HARDWARE";
        }
    }
    
    /**
     * Returns a masked version of the hardware fingerprint for logging
     * 
     * @return Masked fingerprint (first 8 and last 8 characters visible)
     */
    private String maskedFingerprint() {
        if (hardwareFingerprint.length() < 16) {
            return "****" + hardwareFingerprint.substring(Math.max(0, hardwareFingerprint.length() - 4));
        }
        return hardwareFingerprint.substring(0, 8) + "****" + hardwareFingerprint.substring(hardwareFingerprint.length() - 8);
    }
    
    /**
     * Validates and activates the license
     * Integrates with SkriptPlLicenseManager for validation
     * 
     * @param licenseKey The license key to validate
     * @return true if license is valid and activated, false otherwise
     */
    public synchronized boolean validateAndActivateLicense(String licenseKey) {
        try {
            // Validate with SkriptPlLicenseManager
            if (!licenseManager.isValidLicense(licenseKey)) {
                licenseStatus = LicenseStatus.INVALID_HARDWARE;
                LOGGER.warning("Invalid license key provided");
                return false;
            }
            
            // Check hardware lock
            if (!licenseManager.validateHardwareLock(licenseKey, hardwareFingerprint)) {
                licenseStatus = LicenseStatus.INVALID_HARDWARE;
                LOGGER.warning("License is locked to different hardware");
                return false;
            }
            
            // Activate license
            this.licenseKey = licenseKey;
            this.licenseActivationDate = LocalDateTime.now(ZoneId.of("UTC"));
            this.licenseExpirationDate = licenseActivationDate.plusDays(LICENSE_VALIDITY_DAYS);
            this.isLicenseValid = true;
            this.licenseStatus = LicenseStatus.ACTIVE;
            
            LOGGER.info("License activated successfully. Expiration date: " + licenseExpirationDate);
            
            return true;
        } catch (Exception e) {
            licenseStatus = LicenseStatus.REVOKED;
            LOGGER.log(Level.SEVERE, "Error during license validation", e);
            return false;
        }
    }
    
    /**
     * Checks if the license is currently valid
     * Validates expiration date and hardware lock
     * 
     * @return true if license is valid, false otherwise
     */
    public synchronized boolean isLicenseValid() {
        if (!isLicenseValid || licenseKey == null) {
            licenseStatus = LicenseStatus.NOT_ACTIVATED;
            return false;
        }
        
        // Check expiration
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        if (now.isAfter(licenseExpirationDate)) {
            licenseStatus = LicenseStatus.EXPIRED;
            isLicenseValid = false;
            LOGGER.warning("License has expired");
            return false;
        }
        
        // Re-verify hardware lock
        if (!licenseManager.validateHardwareLock(licenseKey, hardwareFingerprint)) {
            licenseStatus = LicenseStatus.INVALID_HARDWARE;
            isLicenseValid = false;
            LOGGER.warning("Hardware validation failed");
            return false;
        }
        
        licenseStatus = LicenseStatus.ACTIVE;
        return true;
    }
    
    /**
     * Gets the remaining days until license expiration
     * 
     * @return Number of days remaining, or -1 if license is not active
     */
    public long getRemainingDays() {
        if (!isLicenseValid || licenseExpirationDate == null) {
            return -1;
        }
        
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        return ChronoUnit.DAYS.between(now, licenseExpirationDate);
    }
    
    /**
     * Renews the license for another 3-month period
     * 
     * @param renewalKey The renewal key from SkriptPlLicenseManager
     * @return true if renewal was successful, false otherwise
     */
    public synchronized boolean renewLicense(String renewalKey) {
        if (!isLicenseValid()){
            LOGGER.warning("Cannot renew an invalid license");
            return false;
        }
        
        try {
            if (!licenseManager.validateRenewalKey(licenseKey, renewalKey, hardwareFingerprint)) {
                LOGGER.warning("Invalid renewal key");
                return false;
            }
            
            this.licenseActivationDate = LocalDateTime.now(ZoneId.of("UTC"));
            this.licenseExpirationDate = licenseActivationDate.plusDays(LICENSE_VALIDITY_DAYS);
            
            LOGGER.info("License renewed successfully. New expiration date: " + licenseExpirationDate);
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during license renewal", e);
            return false;
        }
    }
    
    /**
     * Gets the system status including license information
     * 
     * @return Status string with system and license information
     */
    public String getSystemStatus() {
        StringBuilder status = new StringBuilder();
        status.append("=== VoidCryptDefenseSystem Status ===\n");
        status.append("System Name: ").append(SYSTEM_NAME).append("\n");
        status.append("Version: ").append(LICENSE_VERSION).append("\n");
        status.append("Hardware ID: ").append(maskedFingerprint()).append("\n");
        status.append("License Status: ").append(licenseStatus.getDescription()).append("\n");
        
        if (isLicenseValid) {
            status.append("License Valid: YES\n");
            status.append("Activation Date: ").append(licenseActivationDate).append("\n");
            status.append("Expiration Date: ").append(licenseExpirationDate).append("\n");
            status.append("Days Remaining: ").append(getRemainingDays()).append("\n");
        } else {
            status.append("License Valid: NO\n");
        }
        
        return status.toString();
    }
    
    /**
     * Performs a full system diagnostic
     * 
     * @return Diagnostic report
     */
    public String performDiagnostics() {
        StringBuilder diagnostics = new StringBuilder();
        diagnostics.append("=== VoidCryptDefenseSystem Diagnostics ===\n");
        diagnostics.append(getSystemStatus());
        diagnostics.append("\nDiagnostics Results:\n");
        
        try {
            // Check hardware fingerprint
            String newFingerprint = generateHardwareFingerprint();
            boolean hardwareConsistent = newFingerprint.equals(hardwareFingerprint);
            diagnostics.append("Hardware Consistency: ").append(hardwareConsistent ? "PASS" : "FAIL").append("\n");
            
            // Check license manager
            boolean licenseManagerReady = licenseManager != null;
            diagnostics.append("License Manager Ready: ").append(licenseManagerReady ? "PASS" : "FAIL").append("\n");
            
            // Check license validity
            boolean licenseValid = isLicenseValid();
            diagnostics.append("License Valid: ").append(licenseValid ? "PASS" : "FAIL").append("\n");
            
        } catch (Exception e) {
            diagnostics.append("Diagnostic Error: ").append(e.getMessage()).append("\n");
        }
        
        return diagnostics.toString();
    }
    
    // Getters and Setters
    
    public String getLicenseKey() {
        return licenseKey;
    }
    
    public String getHardwareFingerprint() {
        return hardwareFingerprint;
    }
    
    public LocalDateTime getLicenseActivationDate() {
        return licenseActivationDate;
    }
    
    public LocalDateTime getLicenseExpirationDate() {
        return licenseExpirationDate;
    }
    
    public LicenseStatus getLicenseStatus() {
        return licenseStatus;
    }
    
    public SkriptPlLicenseManager getLicenseManager() {
        return licenseManager;
    }
    
    /**
     * Main method for testing purposes
     */
    public static void main(String[] args) {
        LOGGER.info("Starting VoidCryptDefenseSystem...");
        
        try {
            VoidCryptDefenseSystem system = new VoidCryptDefenseSystem();
            System.out.println(system.getSystemStatus());
            System.out.println(system.performDiagnostics());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error starting VoidCryptDefenseSystem", e);
        }
    }
}
