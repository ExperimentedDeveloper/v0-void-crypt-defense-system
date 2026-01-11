package com.voidcrypt.phantom;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.voidcrypt.VoidCryptPlugin;
import com.voidcrypt.security.SecurityValidator;
import com.voidcrypt.shadow.SessionGuardian;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Module 5A: Phantom Port Listener (Honeypot)
 * Detects network scanning and malicious behavior
 */
public class PhantomPortListener extends PacketAdapter {

    private final VoidCryptPlugin plugin;
    private final FirewallExecutor firewallExecutor;
    private final SessionGuardian sessionGuardian;
    
    // Configured honeypot channels
    private final Set<String> honeypotChannels;
    
    // Suspicious IP registry
    private final Map<String, SuspicionData> suspicionRegistry;
    
    // Known malicious patterns
    private static final Pattern MALFORMED_BRAND = Pattern.compile(
        ".*[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f].*" // Control characters
    );
    private static final Pattern EXPLOIT_PATTERN = Pattern.compile(
        ".*(\\$\\{|jndi:|ldap:|rmi:).*", Pattern.CASE_INSENSITIVE // Log4j and similar
    );

    public PhantomPortListener(VoidCryptPlugin plugin, ProtocolManager protocolManager,
                                FirewallExecutor firewallExecutor, SessionGuardian sessionGuardian) {
        super(plugin, ListenerPriority.LOWEST,
            PacketType.Play.Client.CUSTOM_PAYLOAD,
            PacketType.Login.Client.CUSTOM_PAYLOAD);
        
        this.plugin = plugin;
        this.firewallExecutor = firewallExecutor;
        this.sessionGuardian = sessionGuardian;
        this.honeypotChannels = ConcurrentHashMap.newKeySet();
        this.suspicionRegistry = new ConcurrentHashMap<>();
        
        // Load honeypot channels from config
        List<String> channels = plugin.getConfig().getStringList("phantom-ports.honeypot-channels");
        honeypotChannels.addAll(channels);
        
        protocolManager.addPacketListener(this);
        
        plugin.getLogger().info("Honeypot active with " + honeypotChannels.size() + " trap channels");
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        if (event.isCancelled()) return;
        
        Player player = event.getPlayer();
        String ip = extractIP(player);
        
        String validatedIP = SecurityValidator.validateIP(ip);
        if (validatedIP == null) return;
        
        try {
            String channel = event.getPacket().getStrings().readSafely(0);
            byte[] data = event.getPacket().getByteArrays().readSafely(0);
            
            if (channel == null) return;
            
            // Check if it's a honeypot channel
            if (isHoneypotChannel(channel)) {
                handleHoneypotTrigger(validatedIP, player, channel);
                event.setCancelled(true);
                return;
            }
            
            // Analyze packet content
            if (data != null) {
                analyzePayload(validatedIP, player, channel, data);
            }
            
            // Verify MC|Brand channel
            if ("MC|Brand".equals(channel) || "minecraft:brand".equals(channel)) {
                validateBrandPacket(validatedIP, player, data);
            }
            
        } catch (Exception e) {
            plugin.getLogger().fine("Error processing phantom packet: " + e.getMessage());
        }
    }

    private boolean isHoneypotChannel(String channel) {
        return honeypotChannels.contains(channel) || 
               channel.startsWith("voidcrypt:") && !channel.equals("voidcrypt:response");
    }

    private void handleHoneypotTrigger(String ip, Player player, String channel) {
        if (!SecurityValidator.checkRateLimit("honeypot:" + ip)) {
            return;
        }
        
        plugin.alert("HONEYPOT TRIGGERED! IP: " + ip + " | Channel: " + channel);
        plugin.auditLog(Level.WARNING, "HONEYPOT_TRIGGERED", 
            "IP: " + ip + " Channel: " + SecurityValidator.sanitizeForLog(channel));
        
        // Increment suspicion
        SuspicionData data = suspicionRegistry.computeIfAbsent(ip, 
            k -> new SuspicionData(ip, 0, System.currentTimeMillis()));
        data.incrementLevel(5);
        
        // Mark session
        if (player != null) {
            sessionGuardian.addSuspicion(player.getUniqueId(), 5);
        }
        
        // Execute block if enabled
        if (data.level >= 10 && plugin.getConfig().getBoolean("phantom-ports.auto-firewall", false)) {
            firewallExecutor.executeBan(ip);
        }
        
        // Kick player
        if (player != null && player.isOnline()) {
            player.kickPlayer("§cConnection terminated.");
        }
    }

    private void analyzePayload(String ip, Player player, String channel, byte[] data) {
        String content = new String(data, StandardCharsets.UTF_8);
        
        // Detect exploit patterns
        if (EXPLOIT_PATTERN.matcher(content).find()) {
            plugin.alert("EXPLOIT ATTEMPT DETECTED! IP: " + ip + " | Pattern found");
            plugin.auditLog(Level.SEVERE, "EXPLOIT_ATTEMPT", 
                "IP: " + ip + " Content: " + SecurityValidator.sanitizeForLog(content));
            
            SuspicionData suspData = suspicionRegistry.computeIfAbsent(ip,
                k -> new SuspicionData(ip, 0, System.currentTimeMillis()));
            suspData.incrementLevel(10);
            
            if (player != null) {
                sessionGuardian.addSuspicion(player.getUniqueId(), 10);
                player.kickPlayer("§cConnection terminated.");
            }
            
            if (plugin.getConfig().getBoolean("phantom-ports.auto-firewall", false)) {
                firewallExecutor.executeBan(ip);
            }
        }
    }

    private void validateBrandPacket(String ip, Player player, byte[] data) {
        if (data == null || data.length == 0) {
            addSuspicion(ip, player, 2, "Empty brand packet");
            return;
        }
        
        String brand = new String(data, StandardCharsets.UTF_8);
        
        // Detect malformed characters
        if (MALFORMED_BRAND.matcher(brand).matches()) {
            addSuspicion(ip, player, 3, "Malformed brand: " + SecurityValidator.sanitizeForLog(brand));
        }
        
        // Detect clients known to be used in attacks
        String brandLower = brand.toLowerCase();
        if (brandLower.contains("bot") || brandLower.contains("attack") || 
            brandLower.contains("flood") || brandLower.contains("stress")) {
            addSuspicion(ip, player, 5, "Suspicious brand: " + brand);
        }
    }

    private void addSuspicion(String ip, Player player, int level, String reason) {
        SuspicionData data = suspicionRegistry.computeIfAbsent(ip,
            k -> new SuspicionData(ip, 0, System.currentTimeMillis()));
        data.incrementLevel(level);
        
        plugin.getLogger().warning("Suspicion added for " + ip + ": " + reason);
        
        if (player != null) {
            sessionGuardian.addSuspicion(player.getUniqueId(), level);
        }
    }

    private String extractIP(Player player) {
        if (player == null) return null;
        InetSocketAddress address = player.getAddress();
        return address != null ? address.getAddress().getHostAddress() : null;
    }

    public Map<String, SuspicionData> getSuspicionRegistry() {
        return Map.copyOf(suspicionRegistry);
    }

    // Suspicion tracking class
    public static class SuspicionData {
        private final String ip;
        private int level;
        private long firstSeen;
        private long lastSeen;

        public SuspicionData(String ip, int level, long timestamp) {
            this.ip = ip;
            this.level = level;
            this.firstSeen = timestamp;
            this.lastSeen = timestamp;
        }

        public void incrementLevel(int amount) {
            this.level += amount;
            this.lastSeen = System.currentTimeMillis();
        }

        public String getIp() { return ip; }
        public int getLevel() { return level; }
        public long getFirstSeen() { return firstSeen; }
        public long getLastSeen() { return lastSeen; }
    }
}
