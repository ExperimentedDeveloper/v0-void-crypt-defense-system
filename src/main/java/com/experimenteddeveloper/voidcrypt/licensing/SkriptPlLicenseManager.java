package com.experimenteddeveloper.voidcrypt.licensing;

import java.io.IOException;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Skript.pl License Manager Implementation for VoidCrypt Defense System
 * 
 * Provides license validation with:
 * - 3-month time-limited licensing
 * - Hardware-locked validation
 * - Secure license key generation and verification
 * - License expiration tracking
 * 
 * @author ExperimentedDeveloper
 * @version 1.0.0
 * @since 2026-01-11
 */
public class SkriptPlLicenseManager implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(SkriptPlLicenseManager.class.getName());
    
    // License constants
    private static final int LICENSE_VALIDITY_MONTHS = 3;
    private static final int LICENSE_KEY_LENGTH = 32;
    private static final String LICENSE_ALGORITHM = "SHA-256";
    private static final String LICENSE_PREFIX = "VOIDCRYPT-SKRIPTPL";
    private static final long HARDWARE_LOCK_TIMEOUT_HOURS = 24;
    
    private final Map<String, LicenseData> activeLicenses;
    private final HardwareIdentifier hardwareIdentifier;
    private final SecureRandom secureRandom;
    
    /**
     * Initializes the Skript.pl License Manager
     */
    public SkriptPlLicenseManager() {
        this.activeLicenses = new HashMap<>();
        this.hardwareIdentifier = new HardwareIdentifier();
        this.secureRandom = new SecureRandom();
        LOGGER.info("SkriptPlLicenseManager initialized successfully");
    }
    
    /**
     * Generates a new 3-month time-limited license key
     * 
     * @param licenseeId The unique identifier for the licensee
     * @param licenseeEmail The email of the licensee
     * @return The generated license key
     */
    public String generateLicenseKey(String licenseeId, String licenseeEmail) {
        try {
            LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
            LocalDateTime expirationDate = now.plusMonths(LICENSE_VALIDITY_MONTHS);
            
            String hardwareId = hardwareIdentifier.getHardwareFingerprint();
            
            // Create license data
            LicenseData licenseData = new LicenseData(
                licenseeId,
                licenseeEmail,
                hardwareId,
                now,
                expirationDate,
                generateRandomSalt()
            );
            
            // Generate the license key
            String licenseKey = generateSecureKey(licenseData);
            
            // Store the license
            activeLicenses.put(licenseKey, licenseData);
            
            LOGGER.info("License generated for: " + licenseeId + 
                       " (Expires: " + expirationDate + ")");
            
            return licenseKey;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to generate license key", e);
            throw new RuntimeException("License generation failed", e);
        }
    }
    
    /**
     * Validates a license key with hardware and time verification
     * 
     * @param licenseKey The license key to validate
     * @return true if the license is valid, false otherwise
     */
    public boolean validateLicense(String licenseKey) {
        try {
            if (licenseKey == null || licenseKey.isEmpty()) {
                LOGGER.warning("License validation failed: null or empty key");
                return false;
            }
            
            LicenseData licenseData = activeLicenses.get(licenseKey);
            if (licenseData == null) {
                LOGGER.warning("License validation failed: key not found");
                return false;
            }
            
            // Check expiration
            if (isLicenseExpired(licenseData)) {
                LOGGER.warning("License validation failed: license expired for " + 
                             licenseData.getLicenseeId());
                return false;
            }
            
            // Check hardware lock
            if (!validateHardwareLock(licenseData)) {
                LOGGER.warning("License validation failed: hardware lock mismatch for " + 
                             licenseData.getLicenseeId());
                return false;
            }
            
            // Verify license key signature
            if (!verifyLicenseSignature(licenseKey, licenseData)) {
                LOGGER.warning("License validation failed: signature verification failed");
                return false;
            }
            
            LOGGER.info("License validation successful for: " + 
                       licenseData.getLicenseeId());
            return true;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "License validation error", e);
            return false;
        }
    }
    
    /**
     * Checks if a license is expired
     * 
     * @param licenseData The license data to check
     * @return true if expired, false otherwise
     */
    private boolean isLicenseExpired(LicenseData licenseData) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        return now.isAfter(licenseData.getExpirationDate());
    }
    
    /**
     * Validates hardware lock for the license
     * 
     * @param licenseData The license data containing hardware info
     * @return true if hardware matches, false otherwise
     */
    private boolean validateHardwareLock(LicenseData licenseData) {
        try {
            String currentHardwareId = hardwareIdentifier.getHardwareFingerprint();
            String licenseHardwareId = licenseData.getHardwareId();
            
            return currentHardwareId.equals(licenseHardwareId);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Hardware validation error", e);
            return false;
        }
    }
    
    /**
     * Verifies the license key signature
     * 
     * @param licenseKey The license key to verify
     * @param licenseData The associated license data
     * @return true if signature is valid, false otherwise
     */
    private boolean verifyLicenseSignature(String licenseKey, LicenseData licenseData) {
        try {
            String expectedSignature = generateSecureKey(licenseData);
            return licenseKey.equals(expectedSignature);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Signature verification error", e);
            return false;
        }
    }
    
    /**
     * Generates a secure license key based on license data
     * 
     * @param licenseData The license data to sign
     * @return The generated secure key
     */
    private String generateSecureKey(LicenseData licenseData) {
        try {
            MessageDigest digest = MessageDigest.getInstance(LICENSE_ALGORITHM);
            
            String dataToSign = String.format(
                "%s-%s-%s-%s-%s-%s",
                LICENSE_PREFIX,
                licenseData.getLicenseeId(),
                licenseData.getLicenseeEmail(),
                licenseData.getHardwareId(),
                licenseData.getExpirationDate(),
                licenseData.getSalt()
            );
            
            byte[] hashBytes = digest.digest(dataToSign.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    /**
     * Generates a random salt for license data
     * 
     * @return The generated salt as Base64 string
     */
    private String generateRandomSalt() {
        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }
    
    /**
     * Gets license information
     * 
     * @param licenseKey The license key
     * @return The license data if valid, null otherwise
     */
    public LicenseData getLicenseInfo(String licenseKey) {
        return activeLicenses.get(licenseKey);
    }
    
    /**
     * Revokes a license
     * 
     * @param licenseKey The license key to revoke
     * @return true if revocation was successful, false otherwise
     */
    public boolean revokeLicense(String licenseKey) {
        LicenseData removed = activeLicenses.remove(licenseKey);
        if (removed != null) {
            LOGGER.info("License revoked for: " + removed.getLicenseeId());
            return true;
        }
        return false;
    }
    
    /**
     * Gets the number of active licenses
     * 
     * @return The count of active licenses
     */
    public int getActiveLicenseCount() {
        return activeLicenses.size();
    }
    
    /**
     * Checks remaining validity of a license in days
     * 
     * @param licenseKey The license key to check
     * @return The number of days remaining, or -1 if invalid
     */
    public long getRemainingDays(String licenseKey) {
        LicenseData licenseData = activeLicenses.get(licenseKey);
        if (licenseData == null) {
            return -1;
        }
        
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        return ChronoUnit.DAYS.between(now, licenseData.getExpirationDate());
    }
    
    /**
     * License Data class containing license information
     */
    public static class LicenseData implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private final String licenseeId;
        private final String licenseeEmail;
        private final String hardwareId;
        private final LocalDateTime issueDate;
        private final LocalDateTime expirationDate;
        private final String salt;
        
        /**
         * Constructs LicenseData
         */
        public LicenseData(String licenseeId, String licenseeEmail, String hardwareId,
                          LocalDateTime issueDate, LocalDateTime expirationDate, String salt) {
            this.licenseeId = licenseeId;
            this.licenseeEmail = licenseeEmail;
            this.hardwareId = hardwareId;
            this.issueDate = issueDate;
            this.expirationDate = expirationDate;
            this.salt = salt;
        }
        
        public String getLicenseeId() {
            return licenseeId;
        }
        
        public String getLicenseeEmail() {
            return licenseeEmail;
        }
        
        public String getHardwareId() {
            return hardwareId;
        }
        
        public LocalDateTime getIssueDate() {
            return issueDate;
        }
        
        public LocalDateTime getExpirationDate() {
            return expirationDate;
        }
        
        public String getSalt() {
            return salt;
        }
        
        @Override
        public String toString() {
            return "LicenseData{" +
                    "licenseeId='" + licenseeId + '\'' +
                    ", licenseeEmail='" + licenseeEmail + '\'' +
                    ", issueDate=" + issueDate +
                    ", expirationDate=" + expirationDate +
                    '}';
        }
    }
    
    /**
     * Hardware Identifier for device fingerprinting
     */
    public static class HardwareIdentifier {
        private static final Logger LOGGER = Logger.getLogger(HardwareIdentifier.class.getName());
        
        /**
         * Generates a unique hardware fingerprint
         * 
         * @return The hardware fingerprint hash
         */
        public String getHardwareFingerprint() {
            try {
                StringBuilder hardwareInfo = new StringBuilder();
                
                // Get processor information
                hardwareInfo.append(ManagementFactory.getRuntimeMXBean().getName());
                
                // Get MAC address
                hardwareInfo.append(getMacAddress());
                
                // Get hostname
                hardwareInfo.append(java.net.InetAddress.getLocalHost().getHostName());
                
                // Hash the combined information
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hashBytes = digest.digest(hardwareInfo.toString().getBytes(StandardCharsets.UTF_8));
                
                return Base64.getEncoder().encodeToString(hashBytes);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to generate hardware fingerprint", e);
                throw new RuntimeException("Hardware identification failed", e);
            }
        }
        
        /**
         * Gets the MAC address of the primary network interface
         * 
         * @return The MAC address as a string
         */
        private String getMacAddress() {
            try {
                Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
                while (networkInterfaces.hasMoreElements()) {
                    NetworkInterface networkInterface = networkInterfaces.nextElement();
                    byte[] macAddress = networkInterface.getHardwareAddress();
                    
                    if (macAddress != null && macAddress.length > 0) {
                        StringBuilder sb = new StringBuilder();
                        for (byte b : macAddress) {
                            sb.append(String.format("%02X", b));
                        }
                        return sb.toString();
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to retrieve MAC address", e);
            }
            return "UNKNOWN";
        }
    }
}
