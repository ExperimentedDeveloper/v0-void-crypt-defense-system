package com.voidcrypt.shadow;

import com.voidcrypt.security.SecurityValidator;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Module 2A: Session Digital Fingerprint
 * Stores immutable session data for verification
 */
public class SessionFingerprint {

    private final UUID playerUUID;
    private final String boundIP;
    private final String protocolHash;
    private final long creationTime;
    private final int protocolVersion;
    
    private final String entropyToken;
    
    // Session status
    private SessionStatus status;
    private int suspicionLevel;

    public SessionFingerprint(UUID playerUUID, String boundIP, int protocolVersion) {
        this.playerUUID = playerUUID;
        this.boundIP = validateAndSanitizeIP(boundIP);
        this.protocolVersion = protocolVersion;
        this.entropyToken = SecurityValidator.generateSecureToken(8);
        this.protocolHash = generateProtocolHash();
        this.creationTime = System.currentTimeMillis();
        this.status = SessionStatus.ACTIVE;
        this.suspicionLevel = 0;
    }

    /**
     * Generates a unique hash based on UUID + IP + Protocol + Entropy
     */
    private String generateProtocolHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = playerUUID.toString() + boundIP + protocolVersion + creationTime + entropyToken;
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) { // Only first 8 bytes for brevity
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "error";
        }
    }

    /**
     * Properly validates IP using SecurityValidator
     * Returns null for invalid IPs instead of 0.0.0.0
     */
    private String validateAndSanitizeIP(String ip) {
        String validated = SecurityValidator.validateIP(ip);
        if (validated == null) {
            // For fingerprinting purposes, we need SOME value
            // But mark it as suspicious
            this.suspicionLevel = 1;
            return "INVALID";
        }
        return validated;
    }

    /**
     * Verifies if an IP matches the bound IP
     * Uses proper IP validation
     */
    public boolean validateIP(String currentIP) {
        String validatedCurrent = SecurityValidator.validateIP(currentIP);
        if (validatedCurrent == null || "INVALID".equals(boundIP)) {
            return false;
        }
        return boundIP.equals(validatedCurrent);
    }

    /**
     * Increments suspicion level
     */
    public void incrementSuspicion(int amount) {
        this.suspicionLevel += amount;
        if (suspicionLevel >= 10) {
            this.status = SessionStatus.COMPROMISED;
        } else if (suspicionLevel >= 5) {
            this.status = SessionStatus.SUSPICIOUS;
        }
    }

    public void markInvestigated() {
        this.status = SessionStatus.UNDER_INVESTIGATION;
    }

    public void clearSuspicion() {
        this.suspicionLevel = 0;
        this.status = SessionStatus.ACTIVE;
    }

    // Getters
    public UUID getPlayerUUID() { return playerUUID; }
    public String getBoundIP() { return boundIP; }
    public String getProtocolHash() { return protocolHash; }
    public long getCreationTime() { return creationTime; }
    public int getProtocolVersion() { return protocolVersion; }
    public SessionStatus getStatus() { return status; }
    public int getSuspicionLevel() { return suspicionLevel; }

    public long getSessionDurationMs() {
        return System.currentTimeMillis() - creationTime;
    }

    public enum SessionStatus {
        ACTIVE,              // Normal
        SUSPICIOUS,          // Anomalous behavior detected
        UNDER_INVESTIGATION, // Being monitored
        COMPROMISED          // Session compromised
    }
}
