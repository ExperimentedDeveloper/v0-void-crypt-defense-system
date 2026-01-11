package com.voidcrypt.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SecurityValidator
 */
class SecurityValidatorTest {

    @Test
    @DisplayName("Valid IPv4 addresses should pass validation")
    void testValidIPv4() {
        assertEquals("192.168.1.1", SecurityValidator.validateIP("192.168.1.1"));
        assertEquals("10.0.0.1", SecurityValidator.validateIP("10.0.0.1"));
        assertEquals("255.255.255.255", SecurityValidator.validateIP("255.255.255.255"));
        assertEquals("0.0.0.0", SecurityValidator.validateIP("0.0.0.0"));
    }

    @Test
    @DisplayName("Invalid IPv4 addresses should be rejected")
    void testInvalidIPv4() {
        assertNull(SecurityValidator.validateIP("999.999.999.999"));
        assertNull(SecurityValidator.validateIP("256.1.1.1"));
        assertNull(SecurityValidator.validateIP("192.168.1"));
        assertNull(SecurityValidator.validateIP("192.168.1.1.1"));
        assertNull(SecurityValidator.validateIP("abc.def.ghi.jkl"));
    }

    @Test
    @DisplayName("Null and empty values should be rejected")
    void testNullAndEmpty() {
        assertNull(SecurityValidator.validateIP(null));
        assertNull(SecurityValidator.validateIP(""));
        assertNull(SecurityValidator.validateIP("   "));
    }

    @Test
    @DisplayName("Loopback addresses should be allowed")
    void testLoopback() {
        assertNotNull(SecurityValidator.validateIP("127.0.0.1"));
    }

    @Test
    @DisplayName("Private IP detection should work")
    void testPrivateIPDetection() {
        assertTrue(SecurityValidator.isPrivateIP("192.168.1.1"));
        assertTrue(SecurityValidator.isPrivateIP("10.0.0.1"));
        assertTrue(SecurityValidator.isPrivateIP("172.16.0.1"));
        assertTrue(SecurityValidator.isPrivateIP("127.0.0.1"));
        assertFalse(SecurityValidator.isPrivateIP("8.8.8.8"));
    }

    @Test
    @DisplayName("Secret key validation should enforce security requirements")
    void testSecretKeyValidation() {
        assertFalse(SecurityValidator.isSecretKeyValid(null));
        assertFalse(SecurityValidator.isSecretKeyValid(""));
        assertFalse(SecurityValidator.isSecretKeyValid("short"));
        assertFalse(SecurityValidator.isSecretKeyValid("CHANGE_THIS_SECRET_KEY_NOW"));
        assertFalse(SecurityValidator.isSecretKeyValid("DEFAULT_KEY"));
        assertFalse(SecurityValidator.isSecretKeyValid("abcdefghijklmnop")); // All lowercase
        assertFalse(SecurityValidator.isSecretKeyValid("1234567890123456")); // All numbers
        assertTrue(SecurityValidator.isSecretKeyValid("MySecureKey12345!")); // Complex enough
    }

    @Test
    @DisplayName("Shell escaping should remove dangerous characters")
    void testShellEscaping() {
        assertEquals("192.168.1.1", SecurityValidator.escapeForShell("192.168.1.1"));
        assertEquals("192.168.1.1", SecurityValidator.escapeForShell("192.168.1.1; rm -rf /"));
        assertEquals("192.168.1.1", SecurityValidator.escapeForShell("192.168.1.1 && cat /etc/passwd"));
    }

    @Test
    @DisplayName("Log sanitization should remove control characters")
    void testLogSanitization() {
        assertEquals("test message", SecurityValidator.sanitizeForLog("test message"));
        assertEquals("test message", SecurityValidator.sanitizeForLog("test\nmessage"));
        assertEquals("test message", SecurityValidator.sanitizeForLog("test\rmessage"));
    }

    @Test
    @DisplayName("Rate limiting should work correctly")
    void testRateLimiting() {
        String testId = "test-" + System.currentTimeMillis();
        
        // First requests should be allowed
        for (int i = 0; i < 30; i++) {
            assertTrue(SecurityValidator.checkRateLimit(testId));
        }
        
        // 31st request should be blocked
        assertFalse(SecurityValidator.checkRateLimit(testId));
    }

    @Test
    @DisplayName("Secure token generation should produce valid tokens")
    void testSecureTokenGeneration() {
        String token1 = SecurityValidator.generateSecureToken(16);
        String token2 = SecurityValidator.generateSecureToken(16);
        
        assertEquals(32, token1.length()); // 16 bytes = 32 hex chars
        assertNotEquals(token1, token2); // Should be random
        assertTrue(token1.matches("^[0-9a-f]+$")); // Should be hex
    }
}
