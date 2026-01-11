package com.voidcrypt.zte;

import com.voidcrypt.VoidCryptPlugin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Módulo 1A: Generación de desafíos criptográficos
 * Implementa Prueba de Trabajo (PoW) ligera para verificar clientes legítimos
 */
public class CryptographicChallenge {

    private final VoidCryptPlugin plugin;
    private final SecureRandom secureRandom;
    private final String secretKey;
    private final int difficulty;
    
    // Almacén de desafíos pendientes: IP -> ChallengeData
    private final Map<String, ChallengeData> pendingChallenges;
    
    // Regex para validar respuesta hash hexadecimal de 64 caracteres
    private static final Pattern HASH_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

    public CryptographicChallenge(VoidCryptPlugin plugin) {
        this.plugin = plugin;
        this.secureRandom = new SecureRandom();
        this.secretKey = plugin.getConfig().getString("void-handshake.secret-key", "DEFAULT_KEY");
        this.difficulty = plugin.getConfig().getInt("void-handshake.difficulty", 2);
        this.pendingChallenges = new ConcurrentHashMap<>();
        
        if ("CHANGE_THIS_SECRET_KEY_NOW".equals(secretKey) || "DEFAULT_KEY".equals(secretKey)) {
            plugin.getLogger().warning("⚠ ADVERTENCIA: Cambia la clave secreta en config.yml!");
        }
    }

    /**
     * Genera un nonce aleatorio seguro
     */
    public long generateNonce() {
        return secureRandom.nextLong();
    }

    /**
     * Calcula la respuesta esperada usando SHA-256
     * Algoritmo: SHA-256(Nonce + SecretKey + Difficulty)
     */
    public String calculateExpectedAnswer(long nonce) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = nonce + secretKey + difficulty;
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            plugin.getLogger().severe("Error crítico: SHA-256 no disponible!");
            return null;
        }
    }

    /**
     * Crea un nuevo desafío para una IP
     */
    public ChallengeData createChallenge(String ip) {
        long nonce = generateNonce();
        String expectedAnswer = calculateExpectedAnswer(nonce);
        long timestamp = System.currentTimeMillis();
        
        ChallengeData challenge = new ChallengeData(nonce, expectedAnswer, timestamp, difficulty);
        pendingChallenges.put(ip, challenge);
        
        return challenge;
    }

    /**
     * Verifica la respuesta de un cliente
     */
    public VerificationResult verifyResponse(String ip, String response) {
        ChallengeData challenge = pendingChallenges.remove(ip);
        
        if (challenge == null) {
            return VerificationResult.NO_CHALLENGE;
        }
        
        // Verificar timeout (60 ticks = 3 segundos por defecto)
        int timeoutTicks = plugin.getConfig().getInt("void-handshake.timeout-ticks", 60);
        long timeoutMs = timeoutTicks * 50L; // 1 tick = 50ms
        
        if (System.currentTimeMillis() - challenge.timestamp() > timeoutMs) {
            return VerificationResult.TIMEOUT;
        }
        
        // Validar formato de respuesta
        if (response == null || !HASH_PATTERN.matcher(response.toLowerCase()).matches()) {
            return VerificationResult.INVALID_FORMAT;
        }
        
        // Comparar respuesta
        if (challenge.expectedAnswer().equalsIgnoreCase(response)) {
            return VerificationResult.SUCCESS;
        }
        
        return VerificationResult.WRONG_ANSWER;
    }

    /**
     * Limpia desafíos expirados
     */
    public void cleanupExpiredChallenges() {
        long now = System.currentTimeMillis();
        int timeoutTicks = plugin.getConfig().getInt("void-handshake.timeout-ticks", 60);
        long timeoutMs = timeoutTicks * 50L;
        
        pendingChallenges.entrySet().removeIf(entry -> 
            now - entry.getValue().timestamp() > timeoutMs * 2);
    }

    public boolean hasPendingChallenge(String ip) {
        return pendingChallenges.containsKey(ip);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // Record para datos del desafío
    public record ChallengeData(long nonce, String expectedAnswer, long timestamp, int difficulty) {
        public String toPayload() {
            return nonce + ":" + difficulty;
        }
    }

    public enum VerificationResult {
        SUCCESS,
        NO_CHALLENGE,
        TIMEOUT,
        INVALID_FORMAT,
        WRONG_ANSWER
    }
}
