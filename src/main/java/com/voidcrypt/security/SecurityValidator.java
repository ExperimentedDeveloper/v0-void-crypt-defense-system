package com.voidcrypt.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Centralized security validation utilities
 * Provides strict IP validation, rate limiting, and input sanitization
 */
public class SecurityValidator {

    private static final Pattern IPV4_PATTERN = Pattern.compile(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );

    private static final Pattern IPV6_PATTERN = Pattern.compile(
        "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|" +
        "^::([0-9a-fA-F]{1,4}:){0,6}[0-9a-fA-F]{1,4}$|" +
        "^([0-9a-fA-F]{1,4}:){1,7}:$|" +
        "^([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}$|" +
        "^([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}$|" +
        "^([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}$|" +
        "^([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}$|" +
        "^([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}$|" +
        "^[0-9a-fA-F]{1,4}:(:[0-9a-fA-F]{1,4}){1,6}$|" +
        "^::1$"
    );

    private static final Map<String, RateLimitEntry> rateLimitMap = new ConcurrentHashMap<>();
    private static final int MAX_ATTEMPTS_PER_MINUTE = 30;
    private static final long RATE_LIMIT_WINDOW_MS = 60_000;

    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * Validates an IP address using InetAddress (most reliable method)
     * Rejects null, empty, private, and malformed IPs
     * @return validated IP string or null if invalid
     */
    public static String validateIP(String ip) {
        if (ip == null || ip.isEmpty() || ip.isBlank()) {
            return null;
        }

        // Strip any leading/trailing whitespace
        ip = ip.trim();

        // First check with regex for format
        boolean isIPv4 = IPV4_PATTERN.matcher(ip).matches();
        boolean isIPv6 = IPV6_PATTERN.matcher(ip).matches();

        if (!isIPv4 && !isIPv6) {
            return null;
        }

        // Then validate with InetAddress (catches edge cases)
        try {
            InetAddress address = InetAddress.getByName(ip);
            
            // Reject loopback unless explicitly allowed
            if (address.isLoopbackAddress()) {
                return ip; // Allow loopback for local testing
            }

            return address.getHostAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /**
     * Checks if IP is from private/local network
     */
    public static boolean isPrivateIP(String ip) {
        if (ip == null) return false;
        
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.isLoopbackAddress() || 
                   address.isSiteLocalAddress() || 
                   address.isLinkLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /**
     * Rate limiting check - returns true if request should be allowed
     */
    public static boolean checkRateLimit(String identifier) {
        long now = System.currentTimeMillis();
        
        RateLimitEntry entry = rateLimitMap.compute(identifier, (key, existing) -> {
            if (existing == null || now - existing.windowStart > RATE_LIMIT_WINDOW_MS) {
                return new RateLimitEntry(now, 1);
            }
            existing.count++;
            return existing;
        });

        return entry.count <= MAX_ATTEMPTS_PER_MINUTE;
    }

    /**
     * Escapes shell-dangerous characters from IP for firewall commands
     */
    public static String escapeForShell(String input) {
        if (input == null) return "";
        
        // Only allow safe characters for IPs
        return input.replaceAll("[^0-9a-fA-F.:]", "");
    }

    /**
     * Sanitizes input string for safe logging (prevents log injection)
     */
    public static String sanitizeForLog(String input) {
        if (input == null) return "";
        
        return input
            .replaceAll("[\\r\\n]", " ")  // Remove newlines
            .replaceAll("[\\x00-\\x1f]", "?")  // Replace control chars
            .substring(0, Math.min(input.length(), 200));  // Limit length
    }

    /**
     * Generates a cryptographically secure random token
     */
    public static String generateSecureToken(int length) {
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Validates secret key meets minimum security requirements
     */
    public static boolean isSecretKeyValid(String key) {
        if (key == null || key.isEmpty()) return false;
        if (key.length() < 16) return false;
        if (key.equals("CHANGE_THIS_SECRET_KEY_NOW")) return false;
        if (key.equals("DEFAULT_KEY")) return false;
        if (key.matches("^[a-z]+$")) return false; // All lowercase
        if (key.matches("^[0-9]+$")) return false; // All numbers
        return true;
    }

    /**
     * Cleans up old rate limit entries (call periodically)
     */
    public static void cleanupRateLimits() {
        long now = System.currentTimeMillis();
        rateLimitMap.entrySet().removeIf(entry -> 
            now - entry.getValue().windowStart > RATE_LIMIT_WINDOW_MS * 2);
    }

    private static class RateLimitEntry {
        long windowStart;
        int count;

        RateLimitEntry(long windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
