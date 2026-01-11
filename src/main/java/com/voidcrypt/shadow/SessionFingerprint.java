package com.voidcrypt.shadow;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Módulo 2A: Huella digital de sesión
 * Almacena datos inmutables de la sesión para verificación
 */
public class SessionFingerprint {

    private final UUID playerUUID;
    private final String boundIP;
    private final String protocolHash;
    private final long creationTime;
    private final int protocolVersion;
    
    // Estado de la sesión
    private SessionStatus status;
    private int suspicionLevel;

    public SessionFingerprint(UUID playerUUID, String boundIP, int protocolVersion) {
        this.playerUUID = playerUUID;
        this.boundIP = sanitizeIP(boundIP);
        this.protocolVersion = protocolVersion;
        this.protocolHash = generateProtocolHash();
        this.creationTime = System.currentTimeMillis();
        this.status = SessionStatus.ACTIVE;
        this.suspicionLevel = 0;
    }

    /**
     * Genera un hash único basado en UUID + IP + Protocolo
     */
    private String generateProtocolHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = playerUUID.toString() + boundIP + protocolVersion + creationTime;
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) { // Solo primeros 8 bytes para brevedad
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "error";
        }
    }

    /**
     * Sanitiza la IP eliminando caracteres peligrosos
     * Regex: Solo permite dígitos y puntos para IPv4
     */
    private String sanitizeIP(String ip) {
        if (ip == null) return "0.0.0.0";
        // IPv4 validation: ^((25[0-5]|(2[0-4]|1\d|[1-9]|)\d)\.?\b){4}$
        String sanitized = ip.replaceAll("[^0-9.:]", "");
        return sanitized.isEmpty() ? "0.0.0.0" : sanitized;
    }

    /**
     * Verifica si una IP coincide con la IP vinculada
     */
    public boolean validateIP(String currentIP) {
        String sanitizedCurrent = sanitizeIP(currentIP);
        return boundIP.equals(sanitizedCurrent);
    }

    /**
     * Incrementa el nivel de sospecha
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
        ACTIVE,           // Normal
        SUSPICIOUS,       // Comportamiento anómalo detectado
        UNDER_INVESTIGATION, // Siendo monitoreado
        COMPROMISED       // Sesión comprometida
    }
}
