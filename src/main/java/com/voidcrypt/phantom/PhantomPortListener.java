package com.voidcrypt.phantom;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.voidcrypt.VoidCryptPlugin;
import com.voidcrypt.shadow.SessionGuardian;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Módulo 5A: Listener de Puertos Fantasma (Honeypot)
 * Detecta escaneos de red y comportamiento malicioso
 */
public class PhantomPortListener extends PacketAdapter {

    private final VoidCryptPlugin plugin;
    private final FirewallExecutor firewallExecutor;
    private final SessionGuardian sessionGuardian;
    
    // Canales trampa configurados
    private final Set<String> honeypotChannels;
    
    // Registro de IPs sospechosas
    private final Map<String, SuspicionData> suspicionRegistry;
    
    // Patrones maliciosos conocidos
    private static final Pattern MALFORMED_BRAND = Pattern.compile(
        ".*[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f].*" // Caracteres de control
    );
    private static final Pattern EXPLOIT_PATTERN = Pattern.compile(
        ".*(\\$\\{|jndi:|ldap:|rmi:).*", Pattern.CASE_INSENSITIVE // Log4j y similares
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
        
        // Cargar canales trampa de la configuración
        List<String> channels = plugin.getConfig().getStringList("phantom-ports.honeypot-channels");
        honeypotChannels.addAll(channels);
        
        protocolManager.addPacketListener(this);
        
        plugin.getLogger().info("Honeypot activo con " + honeypotChannels.size() + " canales trampa");
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        if (event.isCancelled()) return;
        
        Player player = event.getPlayer();
        String ip = extractIP(player);
        
        try {
            String channel = event.getPacket().getStrings().readSafely(0);
            byte[] data = event.getPacket().getByteArrays().readSafely(0);
            
            if (channel == null) return;
            
            // Verificar si es un canal trampa
            if (isHoneypotChannel(channel)) {
                handleHoneypotTrigger(ip, player, channel);
                event.setCancelled(true);
                return;
            }
            
            // Analizar contenido del paquete
            if (data != null) {
                analyzePayload(ip, player, channel, data);
            }
            
            // Verificar canal MC|Brand
            if ("MC|Brand".equals(channel) || "minecraft:brand".equals(channel)) {
                validateBrandPacket(ip, player, data);
            }
            
        } catch (Exception e) {
            plugin.getLogger().fine("Error procesando paquete phantom: " + e.getMessage());
        }
    }

    private boolean isHoneypotChannel(String channel) {
        return honeypotChannels.contains(channel) || 
               channel.startsWith("voidcrypt:") && !channel.equals("voidcrypt:response");
    }

    private void handleHoneypotTrigger(String ip, Player player, String channel) {
        plugin.alert("¡HONEYPOT ACTIVADO! IP: " + ip + " | Canal: " + channel);
        
        // Incrementar sospecha
        SuspicionData data = suspicionRegistry.computeIfAbsent(ip, 
            k -> new SuspicionData(ip, 0, System.currentTimeMillis()));
        data.incrementLevel(5);
        
        // Marcar sesión
        if (player != null) {
            sessionGuardian.addSuspicion(player.getUniqueId(), 5);
        }
        
        // Ejecutar bloqueo si está habilitado
        if (data.level >= 10 && plugin.getConfig().getBoolean("phantom-ports.auto-firewall", false)) {
            firewallExecutor.executeBan(ip);
        }
        
        // Kick al jugador
        if (player != null && player.isOnline()) {
            player.kickPlayer("§cConexión terminada.");
        }
    }

    private void analyzePayload(String ip, Player player, String channel, byte[] data) {
        String content = new String(data, StandardCharsets.UTF_8);
        
        // Detectar patrones de exploit
        if (EXPLOIT_PATTERN.matcher(content).find()) {
            plugin.alert("¡INTENTO DE EXPLOIT DETECTADO! IP: " + ip + " | Patrón: " + content);
            
            SuspicionData suspData = suspicionRegistry.computeIfAbsent(ip,
                k -> new SuspicionData(ip, 0, System.currentTimeMillis()));
            suspData.incrementLevel(10);
            
            if (player != null) {
                sessionGuardian.addSuspicion(player.getUniqueId(), 10);
                player.kickPlayer("§cConexión terminada.");
            }
            
            if (plugin.getConfig().getBoolean("phantom-ports.auto-firewall", false)) {
                firewallExecutor.executeBan(ip);
            }
        }
    }

    private void validateBrandPacket(String ip, Player player, byte[] data) {
        if (data == null || data.length == 0) {
            addSuspicion(ip, player, 2, "Brand packet vacío");
            return;
        }
        
        String brand = new String(data, StandardCharsets.UTF_8);
        
        // Detectar caracteres malformados
        if (MALFORMED_BRAND.matcher(brand).matches()) {
            addSuspicion(ip, player, 3, "Brand malformado: " + sanitizeForLog(brand));
        }
        
        // Detectar clients conocidos por ser usados en ataques
        String brandLower = brand.toLowerCase();
        if (brandLower.contains("bot") || brandLower.contains("attack") || 
            brandLower.contains("flood") || brandLower.contains("stress")) {
            addSuspicion(ip, player, 5, "Brand sospechoso: " + brand);
        }
    }

    private void addSuspicion(String ip, Player player, int level, String reason) {
        SuspicionData data = suspicionRegistry.computeIfAbsent(ip,
            k -> new SuspicionData(ip, 0, System.currentTimeMillis()));
        data.incrementLevel(level);
        
        plugin.getLogger().warning("Sospecha añadida para " + ip + ": " + reason);
        
        if (player != null) {
            sessionGuardian.addSuspicion(player.getUniqueId(), level);
        }
    }

    private String extractIP(Player player) {
        if (player == null) return "unknown";
        InetSocketAddress address = player.getAddress();
        return address != null ? address.getAddress().getHostAddress() : "unknown";
    }

    private String sanitizeForLog(String input) {
        return input.replaceAll("[\\x00-\\x1f]", "?").substring(0, Math.min(50, input.length()));
    }

    public Map<String, SuspicionData> getSuspicionRegistry() {
        return Map.copyOf(suspicionRegistry);
    }

    // Clase para tracking de sospecha
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
