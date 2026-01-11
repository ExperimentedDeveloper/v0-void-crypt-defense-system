package com.voidcrypt.zte;

import com.voidcrypt.VoidCryptPlugin;
import com.voidcrypt.security.SecurityValidator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Module 1A: Cryptographic Challenge Generation
 * Implements lightweight Proof of Work (PoW) to verify legitimate clients
 */
public class CryptographicChallenge {

    private final VoidCryptPlugin plugin;
    private final SecureRandom secureRandom;
    private final String secretKey;
    private final int difficulty;
    
    // Pending challenge storage: IP -> ChallengeData
    private final Map<String, ChallengeData> pendingChallenges;
    
    // Regex to validate 64-char hex hash response
    private static final Pattern HASH_PATTERN = Pattern.compile("^[0-9a-f]{64}$");
    
    private static final long DEFAULT_CHALLENGE_TIMEOUT_MS = 60_000;

    public CryptographicChallenge(VoidCryptPlugin plugin) {
        this.plugin = plugin;
        this.secureRandom = new SecureRandom();
        this.secretKey = plugin.getConfig().getString("void-handshake.secret-key", "DEFAULT_KEY");
        this.difficulty = plugin.getConfig().getInt("void-handshake.difficulty", 2);
        this.pendingChallenges = new ConcurrentHashMap<>();
        
        if (!SecurityValidator.isSecretKeyValid(secretKey)) {
            plugin.getLogger().severe("Invalid secret key configuration detected!");
        }
    }

    /**
     * Generates a secure random nonce
     */
    public long generateNonce() {
        return secureRandom.nextLong();
    }

    /**
     * Calculates expected answer using SHA-256
     * Added random entropy token for unpredictability
     * Algorithm: SHA-256(Nonce + SecretKey + Difficulty + EntropyToken)
     */
    public String calculateExpectedAnswer(long nonce, String entropyToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = nonce + secretKey + difficulty + entropyToken;
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            plugin.getLogger().severe("Critical error: SHA-256 not available!");
            return null;
        }
    }

    /**
     * Creates a new challenge for an IP
     */
    public ChallengeData createChallenge(String ip) {
        String validatedIP = SecurityValidator.validateIP(ip);
        if (validatedIP == null) {
            plugin.getLogger().warning("Rejected invalid IP for challenge: " + 
                SecurityValidator.sanitizeForLog(ip));
            return null;
        }
        
        if (!SecurityValidator.checkRateLimit("challenge:" + validatedIP)) {
            plugin.getLogger().warning("Rate limit exceeded for challenge requests from: " + validatedIP);
            return null;
        }
        
        long nonce = generateNonce();
        String entropyToken = SecurityValidator.generateSecureToken(8);
        String expectedAnswer = calculateExpectedAnswer(nonce, entropyToken);
        long timestamp = System.currentTimeMillis();
        
        ChallengeData challenge = new ChallengeData(nonce, expectedAnswer, timestamp, difficulty, entropyToken);
        pendingChallenges.put(validatedIP, challenge);
        
        return challenge;
    }

    /**
     * Verifies a client's response
     */
    public VerificationResult verifyResponse(String ip, String response) {
        String validatedIP = SecurityValidator.validateIP(ip);
        if (validatedIP == null) {
            return VerificationResult.INVALID_FORMAT;
        }
        
        ChallengeData challenge = pendingChallenges.remove(validatedIP);
        
        if (challenge == null) {
            return VerificationResult.NO_CHALLENGE;
        }
        
        long timeoutMs = plugin.getConfig().getLong("void-handshake.timeout-ms", DEFAULT_CHALLENGE_TIMEOUT_MS);
        
        if (System.currentTimeMillis() - challenge.timestamp() > timeoutMs) {
            return VerificationResult.TIMEOUT;
        }
        
        // Validate response format
        if (response == null || !HASH_PATTERN.matcher(response.toLowerCase()).matches()) {
            return VerificationResult.INVALID_FORMAT;
        }
        
        if (MessageDigest.isEqual(
                challenge.expectedAnswer().toLowerCase().getBytes(StandardCharsets.UTF_8),
                response.toLowerCase().getBytes(StandardCharsets.UTF_8))) {
            return VerificationResult.SUCCESS;
        }
        
        return VerificationResult.WRONG_ANSWER;
    }

    /**
     * Cleans up expired challenges
     */
    public void cleanupExpiredChallenges() {
        long now = System.currentTimeMillis();
        long timeoutMs = plugin.getConfig().getLong("void-handshake.timeout-ms", DEFAULT_CHALLENGE_TIMEOUT_MS);
        
        pendingChallenges.entrySet().removeIf(entry -> 
            now - entry.getValue().timestamp() > timeoutMs * 2);
    }

    public boolean hasPendingChallenge(String ip) {
        String validatedIP = SecurityValidator.validateIP(ip);
        return validatedIP != null && pendingChallenges.containsKey(validatedIP);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public record ChallengeData(long nonce, String expectedAnswer, long timestamp, int difficulty, String entropyToken) {
        public String toPayload() {
            return nonce + ":" + difficulty + ":" + entropyToken;
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
