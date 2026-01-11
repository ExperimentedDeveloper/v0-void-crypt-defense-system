package com.voidcrypt.shadow;

import com.voidcrypt.VoidCryptPlugin;
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
import java.util.regex.Pattern;

/**
 * Módulo 2B: Guardián de Sesiones
 * Monitorea la integridad de las sesiones de jugadores
 */
public class SessionGuardian implements Listener {

    private final VoidCryptPlugin plugin;
    
    // Almacén thread-safe de huellas de sesión
    private final Map<UUID, SessionFingerprint> sessionStore;
    
    // Regex para validación IPv4
    private static final Pattern IPV4_PATTERN = Pattern.compile(
        "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$"
    );

    public SessionGuardian(VoidCryptPlugin plugin) {
        this.plugin = plugin;
        this.sessionStore = new ConcurrentHashMap<>();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        InetAddress address = event.getAddress();
        String ip = address != null ? address.getHostAddress() : "unknown";
        
        // Validar formato de IP
        if (!isValidIPv4(ip)) {
            plugin.getLogger().warning("IP inválida detectada: " + ip);
            ip = "0.0.0.0";
        }
        
        // Verificar si ya existe una sesión activa con diferente IP
        if (plugin.getConfig().getBoolean("shadow-session.enforce-ip-lock", true)) {
            SessionFingerprint existing = sessionStore.get(uuid);
            
            if (existing != null && !existing.validateIP(ip)) {
                handleSessionSwap(event, uuid, existing.getBoundIP(), ip);
                return;
            }
        }
        
        // Crear nueva huella de sesión
        // El protocolVersion se establecerá después en PlayerJoinEvent
        SessionFingerprint fingerprint = new SessionFingerprint(uuid, ip, -1);
        sessionStore.put(uuid, fingerprint);
        
        plugin.getLogger().fine("Sesión creada para " + event.getName() + " desde " + ip);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        SessionFingerprint fingerprint = sessionStore.get(player.getUniqueId());
        
        if (fingerprint == null) {
            // Crear huella si no existe (caso raro)
            String ip = player.getAddress() != null ? 
                player.getAddress().getAddress().getHostAddress() : "unknown";
            fingerprint = new SessionFingerprint(player.getUniqueId(), ip, player.getProtocolVersion());
            sessionStore.put(player.getUniqueId(), fingerprint);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Mantener la sesión por un tiempo para detectar reconexiones rápidas
        UUID uuid = event.getPlayer().getUniqueId();
        
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            // Limpiar después de 5 minutos si no reconecta
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                sessionStore.remove(uuid);
            }
        }, 6000L); // 5 minutos
    }

    private void handleSessionSwap(AsyncPlayerPreLoginEvent event, UUID uuid, 
                                    String originalIP, String newIP) {
        plugin.alert("¡Cambio de IP detectado! Jugador: " + event.getName() + 
                     " | Original: " + originalIP + " | Nueva: " + newIP);
        
        if (plugin.getConfig().getBoolean("shadow-session.ban-on-swap", true)) {
            int banMinutes = plugin.getConfig().getInt("shadow-session.ban-duration-minutes", 60);
            
            // Banear temporalmente
            Date expiry = Date.from(Instant.now().plus(Duration.ofMinutes(banMinutes)));
            Bukkit.getBanList(BanList.Type.IP).addBan(
                newIP,
                "VoidCrypt: Sesión corrupta detectada",
                expiry,
                "VoidCrypt System"
            );
            
            event.disallow(
                AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.kick-session-corrupt", 
                        "&cSesión inválida."))
            );
        }
        
        // Marcar sesión original como comprometida
        SessionFingerprint fingerprint = sessionStore.get(uuid);
        if (fingerprint != null) {
            fingerprint.incrementSuspicion(10);
        }
    }

    /**
     * Valida IP contra la huella almacenada
     * Llamado por otros módulos para verificar integridad
     */
    public boolean validateSession(Player player) {
        SessionFingerprint fingerprint = sessionStore.get(player.getUniqueId());
        if (fingerprint == null) return false;
        
        String currentIP = player.getAddress() != null ? 
            player.getAddress().getAddress().getHostAddress() : "unknown";
        
        return fingerprint.validateIP(currentIP);
    }

    /**
     * Obtiene la huella de sesión de un jugador
     */
    public SessionFingerprint getFingerprint(UUID uuid) {
        return sessionStore.get(uuid);
    }

    /**
     * Obtiene todas las sesiones activas
     */
    public Map<UUID, SessionFingerprint> getAllSessions() {
        return Map.copyOf(sessionStore);
    }

    /**
     * Marca una sesión para investigación
     */
    public void flagForInvestigation(UUID uuid) {
        SessionFingerprint fingerprint = sessionStore.get(uuid);
        if (fingerprint != null) {
            fingerprint.markInvestigated();
        }
    }

    /**
     * Incrementa sospecha de una sesión
     */
    public void addSuspicion(UUID uuid, int level) {
        SessionFingerprint fingerprint = sessionStore.get(uuid);
        if (fingerprint != null) {
            fingerprint.incrementSuspicion(level);
        }
    }

    private boolean isValidIPv4(String ip) {
        return ip != null && IPV4_PATTERN.matcher(ip).matches();
    }

    public int getActiveSessionCount() {
        return sessionStore.size();
    }
}
