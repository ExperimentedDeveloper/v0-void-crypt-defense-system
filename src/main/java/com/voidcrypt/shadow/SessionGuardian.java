package com.voidcrypt.shadow;

import com.voidcrypt.VoidCryptPlugin;
import com.voidcrypt.security.SecurityValidator;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Module 2B: Session Guardian
 * Monitors player session integrity
 */
public class SessionGuardian implements Listener {

    private final VoidCryptPlugin plugin;
    
    // Thread-safe session fingerprint storage
    private final Map<UUID, SessionFingerprint> sessionStore;

    public SessionGuardian(VoidCryptPlugin plugin) {
        this.plugin = plugin;
        this.sessionStore = new ConcurrentHashMap<>();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        InetAddress address = event.getAddress();
        String ip = address != null ? address.getHostAddress() : null;
        
        String validatedIP = SecurityValidator.validateIP(ip);
        if (validatedIP == null) {
            plugin.getLogger().warning("Invalid IP detected during login: " + 
                SecurityValidator.sanitizeForLog(ip));
            event.disallow(
                AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                ChatColor.RED + "Connection error."
            );
            return;
        }
        
        // Check for existing session with different IP
        if (plugin.getConfig().getBoolean("shadow-session.enforce-ip-lock", true)) {
            SessionFingerprint existing = sessionStore.get(uuid);
            
            if (existing != null && !existing.validateIP(validatedIP)) {
                handleSessionSwap(event, uuid, existing.getBoundIP(), validatedIP);
                return;
            }
        }
        
        // Create new session fingerprint
        SessionFingerprint fingerprint = new SessionFingerprint(uuid, validatedIP, -1);
        sessionStore.put(uuid, fingerprint);
        
        plugin.getLogger().fine("Session created for " + event.getName() + " from " + validatedIP);
        plugin.auditLog(Level.INFO, "SESSION_CREATED", 
            "Player: " + event.getName() + " IP: " + validatedIP);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        SessionFingerprint fingerprint = sessionStore.get(player.getUniqueId());
        
        if (fingerprint == null) {
            // Create fingerprint if doesn't exist (rare case)
            String ip = player.getAddress() != null ? 
                player.getAddress().getAddress().getHostAddress() : "unknown";
            String validatedIP = SecurityValidator.validateIP(ip);
            fingerprint = new SessionFingerprint(player.getUniqueId(), 
                validatedIP != null ? validatedIP : "unknown", 
                player.getProtocolVersion());
            sessionStore.put(player.getUniqueId(), fingerprint);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Keep session for a while to detect quick reconnections
        UUID uuid = event.getPlayer().getUniqueId();
        
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            // Clean up after 5 minutes if not reconnected
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                sessionStore.remove(uuid);
            }
        }, 6000L); // 5 minutes
    }

    private void handleSessionSwap(AsyncPlayerPreLoginEvent event, UUID uuid, 
                                    String originalIP, String newIP) {
        plugin.alert("IP change detected! Player: " + event.getName() + 
                     " | Original: " + originalIP + " | New: " + newIP);
        
        plugin.auditLog(Level.WARNING, "SESSION_SWAP_DETECTED",
            "Player: " + event.getName() + " Original: " + originalIP + " New: " + newIP);
        
        if (plugin.getConfig().getBoolean("shadow-session.ban-on-swap", true)) {
            int banMinutes = plugin.getConfig().getInt("shadow-session.ban-duration-minutes", 60);
            
            // Temporary ban
            Date expiry = Date.from(Instant.now().plus(Duration.ofMinutes(banMinutes)));
            Bukkit.getBanList(BanList.Type.IP).addBan(
                newIP,
                "VoidCrypt: Corrupted session detected",
                expiry,
                "VoidCrypt System"
            );
            
            event.disallow(
                AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.kick-session-corrupt", 
                        "&cInvalid session."))
            );
        }
        
        // Mark original session as compromised
        SessionFingerprint fingerprint = sessionStore.get(uuid);
        if (fingerprint != null) {
            fingerprint.incrementSuspicion(10);
        }
    }

    /**
     * Validates IP against stored fingerprint
     * Called by other modules to verify integrity
     */
    public boolean validateSession(Player player) {
        SessionFingerprint fingerprint = sessionStore.get(player.getUniqueId());
        if (fingerprint == null) return false;
        
        String currentIP = player.getAddress() != null ? 
            player.getAddress().getAddress().getHostAddress() : "unknown";
        
        return fingerprint.validateIP(currentIP);
    }

    /**
     * Gets a player's session fingerprint
     */
    public SessionFingerprint getFingerprint(UUID uuid) {
        return sessionStore.get(uuid);
    }

    /**
     * Gets all active sessions
     */
    public Map<UUID, SessionFingerprint> getAllSessions() {
        return Map.copyOf(sessionStore);
    }

    /**
     * Marks a session for investigation
     */
    public void flagForInvestigation(UUID uuid) {
        SessionFingerprint fingerprint = sessionStore.get(uuid);
        if (fingerprint != null) {
            fingerprint.markInvestigated();
        }
    }

    /**
     * Increments session suspicion
     */
    public void addSuspicion(UUID uuid, int level) {
        SessionFingerprint fingerprint = sessionStore.get(uuid);
        if (fingerprint != null) {
            fingerprint.incrementSuspicion(level);
        }
    }

    public int getActiveSessionCount() {
        return sessionStore.size();
    }
}
