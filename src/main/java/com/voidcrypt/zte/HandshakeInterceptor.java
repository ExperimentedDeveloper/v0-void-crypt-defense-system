package com.voidcrypt.zte;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.voidcrypt.VoidCryptPlugin;
import com.voidcrypt.security.SecurityValidator;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Module 1B: Zero Trust Handshake Interceptor
 * Intercepts incoming connections and applies cryptographic verification
 */
public class HandshakeInterceptor extends PacketAdapter {

    private final VoidCryptPlugin plugin;
    private final CryptographicChallenge cryptoChallenge;
    
    // Successfully verified IPs with expiration timestamp
    private final Map<String, Long> verifiedIPs;
    
    // Failure count per IP
    private final Map<String, Integer> failureCount;
    
    private final Map<String, Long> lockedOutIPs;
    
    private static final String CHALLENGE_CHANNEL = "voidcrypt:challenge";
    private static final String RESPONSE_CHANNEL = "voidcrypt:response";
    
    private static final int MAX_FAILURES = 3;
    private static final long LOCKOUT_DURATION_MS = 300_000; // 5 minutes
    private static final long VERIFICATION_EXPIRY_MS = 600_000; // 10 minutes

    public HandshakeInterceptor(VoidCryptPlugin plugin, ProtocolManager protocolManager, 
                                 CryptographicChallenge cryptoChallenge) {
        super(plugin, ListenerPriority.HIGHEST,
            PacketType.Login.Client.START,
            PacketType.Play.Client.CUSTOM_PAYLOAD);
        
        this.plugin = plugin;
        this.cryptoChallenge = cryptoChallenge;
        this.verifiedIPs = new ConcurrentHashMap<>();
        this.failureCount = new ConcurrentHashMap<>();
        this.lockedOutIPs = new ConcurrentHashMap<>();
        
        protocolManager.addPacketListener(this);
        
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            cryptoChallenge.cleanupExpiredChallenges();
            cleanupExpiredVerifications();
            cleanupExpiredLockouts();
        }, 6000L, 6000L); // Every 5 minutes
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        if (event.isCancelled()) return;
        
        if (event.getPacketType() == PacketType.Login.Client.START) {
            handleLoginStart(event);
        } else if (event.getPacketType() == PacketType.Play.Client.CUSTOM_PAYLOAD) {
            handleCustomPayload(event);
        }
    }

    private void handleLoginStart(PacketEvent event) {
        String ip = extractIP(event);
        
        String validatedIP = SecurityValidator.validateIP(ip);
        if (validatedIP == null) {
            event.setCancelled(true);
            plugin.auditLog(Level.WARNING, "INVALID_IP_LOGIN", "Rejected invalid IP format");
            return;
        }
        
        if (isLockedOut(validatedIP)) {
            event.setCancelled(true);
            plugin.alert("Blocked login attempt from locked out IP: " + validatedIP);
            return;
        }
        
        // If already verified and not expired, allow
        Long verifiedTime = verifiedIPs.get(validatedIP);
        if (verifiedTime != null && System.currentTimeMillis() - verifiedTime < VERIFICATION_EXPIRY_MS) {
            return;
        }
        
        // Check failure count
        int failures = failureCount.getOrDefault(validatedIP, 0);
        if (failures >= MAX_FAILURES) {
            lockoutIP(validatedIP);
            event.setCancelled(true);
            plugin.alert("IP locked out due to multiple handshake failures: " + validatedIP);
            return;
        }
        
        // Create new challenge
        CryptographicChallenge.ChallengeData challenge = cryptoChallenge.createChallenge(validatedIP);
        
        if (challenge != null) {
            plugin.getLogger().fine("Challenge created for " + validatedIP + ": " + challenge.nonce());
        }
    }

    private void handleCustomPayload(PacketEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        
        String ip = extractPlayerIP(player);
        String validatedIP = SecurityValidator.validateIP(ip);
        if (validatedIP == null) return;
        
        try {
            // Read channel from packet
            String channel = event.getPacket().getStrings().readSafely(0);
            
            if (RESPONSE_CHANNEL.equals(channel)) {
                // Read client response
                byte[] data = event.getPacket().getByteArrays().readSafely(0);
                if (data != null) {
                    String response = new String(data, StandardCharsets.UTF_8);
                    processResponse(validatedIP, response, player);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().fine("Error processing payload: " + e.getMessage());
        }
    }

    private void processResponse(String ip, String response, Player player) {
        CryptographicChallenge.VerificationResult result = cryptoChallenge.verifyResponse(ip, response);
        
        switch (result) {
            case SUCCESS -> {
                verifiedIPs.put(ip, System.currentTimeMillis());
                failureCount.remove(ip);
                plugin.getLogger().fine("Verification successful for " + ip);
                plugin.auditLog(Level.INFO, "HANDSHAKE_SUCCESS", "IP: " + ip);
            }
            case TIMEOUT -> {
                incrementFailure(ip);
                kickPlayer(player, "timeout");
                plugin.auditLog(Level.WARNING, "HANDSHAKE_TIMEOUT", "IP: " + ip);
            }
            case WRONG_ANSWER, INVALID_FORMAT -> {
                incrementFailure(ip);
                plugin.alert("Invalid handshake response from: " + ip);
                kickPlayer(player, "invalid");
                plugin.auditLog(Level.WARNING, "HANDSHAKE_INVALID", "IP: " + ip);
            }
            case NO_CHALLENGE -> {
                // Ignore if no pending challenge
            }
        }
    }

    private void incrementFailure(String ip) {
        failureCount.merge(ip, 1, Integer::sum);
    }

    private void lockoutIP(String ip) {
        lockedOutIPs.put(ip, System.currentTimeMillis());
        failureCount.remove(ip);
    }

    private boolean isLockedOut(String ip) {
        Long lockoutTime = lockedOutIPs.get(ip);
        if (lockoutTime == null) return false;
        
        if (System.currentTimeMillis() - lockoutTime > LOCKOUT_DURATION_MS) {
            lockedOutIPs.remove(ip);
            return false;
        }
        return true;
    }

    private void cleanupExpiredVerifications() {
        long now = System.currentTimeMillis();
        verifiedIPs.entrySet().removeIf(entry -> 
            now - entry.getValue() > VERIFICATION_EXPIRY_MS);
    }

    private void cleanupExpiredLockouts() {
        long now = System.currentTimeMillis();
        lockedOutIPs.entrySet().removeIf(entry -> 
            now - entry.getValue() > LOCKOUT_DURATION_MS);
    }

    private void kickPlayer(Player player, String reason) {
        String message = ChatColor.translateAlternateColorCodes('&',
            plugin.getConfig().getString("messages.kick-handshake-failed", "&cConnection rejected."));
        
        Bukkit.getScheduler().runTask(plugin, () -> player.kickPlayer(message));
    }

    private String extractIP(PacketEvent event) {
        try {
            InetSocketAddress address = event.getPlayer().getAddress();
            if (address != null) {
                return address.getAddress().getHostAddress();
            }
        } catch (Exception e) {
            // Silent fallback
        }
        return null;
    }

    private String extractPlayerIP(Player player) {
        InetSocketAddress address = player.getAddress();
        if (address != null) {
            return address.getAddress().getHostAddress();
        }
        return "unknown";
    }

    public boolean isVerified(String ip) {
        String validatedIP = SecurityValidator.validateIP(ip);
        if (validatedIP == null) return false;
        
        Long verifiedTime = verifiedIPs.get(validatedIP);
        return verifiedTime != null && System.currentTimeMillis() - verifiedTime < VERIFICATION_EXPIRY_MS;
    }

    public int getFailureCount(String ip) {
        return failureCount.getOrDefault(ip, 0);
    }
}
