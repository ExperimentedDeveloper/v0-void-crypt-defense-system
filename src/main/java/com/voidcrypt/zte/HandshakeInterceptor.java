package com.voidcrypt.zte;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.voidcrypt.VoidCryptPlugin;
import io.netty.buffer.Unpooled;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Módulo 1B: Interceptor de Handshake Zero Trust
 * Intercepta conexiones entrantes y aplica verificación criptográfica
 */
public class HandshakeInterceptor extends PacketAdapter {

    private final VoidCryptPlugin plugin;
    private final CryptographicChallenge cryptoChallenge;
    
    // IPs verificadas exitosamente
    private final Set<String> verifiedIPs;
    
    // Contador de fallos por IP
    private final Map<String, Integer> failureCount;
    
    private static final String CHALLENGE_CHANNEL = "voidcrypt:challenge";
    private static final String RESPONSE_CHANNEL = "voidcrypt:response";

    public HandshakeInterceptor(VoidCryptPlugin plugin, ProtocolManager protocolManager, 
                                 CryptographicChallenge cryptoChallenge) {
        super(plugin, ListenerPriority.HIGHEST,
            PacketType.Login.Client.START,
            PacketType.Play.Client.CUSTOM_PAYLOAD);
        
        this.plugin = plugin;
        this.cryptoChallenge = cryptoChallenge;
        this.verifiedIPs = ConcurrentHashMap.newKeySet();
        this.failureCount = new ConcurrentHashMap<>();
        
        protocolManager.addPacketListener(this);
        
        // Limpieza periódica de desafíos expirados
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            cryptoChallenge.cleanupExpiredChallenges();
            // Limpiar IPs verificadas después de 5 minutos
            verifiedIPs.clear();
        }, 6000L, 6000L); // Cada 5 minutos
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
        
        if (ip == null) {
            event.setCancelled(true);
            return;
        }
        
        // Si ya está verificada, permitir
        if (verifiedIPs.contains(ip)) {
            return;
        }
        
        // Verificar si tiene demasiados fallos
        int failures = failureCount.getOrDefault(ip, 0);
        if (failures >= 3) {
            event.setCancelled(true);
            plugin.alert("IP bloqueada por múltiples fallos de handshake: " + ip);
            return;
        }
        
        // Crear nuevo desafío
        CryptographicChallenge.ChallengeData challenge = cryptoChallenge.createChallenge(ip);
        
        plugin.getLogger().fine("Desafío creado para " + ip + ": " + challenge.nonce());
    }

    private void handleCustomPayload(PacketEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        
        String ip = extractPlayerIP(player);
        
        try {
            // Leer el canal del paquete
            String channel = event.getPacket().getStrings().readSafely(0);
            
            if (RESPONSE_CHANNEL.equals(channel)) {
                // Leer la respuesta del cliente
                byte[] data = event.getPacket().getByteArrays().readSafely(0);
                if (data != null) {
                    String response = new String(data, StandardCharsets.UTF_8);
                    processResponse(ip, response, player);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().fine("Error procesando payload: " + e.getMessage());
        }
    }

    private void processResponse(String ip, String response, Player player) {
        CryptographicChallenge.VerificationResult result = cryptoChallenge.verifyResponse(ip, response);
        
        switch (result) {
            case SUCCESS -> {
                verifiedIPs.add(ip);
                failureCount.remove(ip);
                plugin.getLogger().fine("Verificación exitosa para " + ip);
            }
            case TIMEOUT -> {
                incrementFailure(ip);
                kickPlayer(player, "timeout");
            }
            case WRONG_ANSWER, INVALID_FORMAT -> {
                incrementFailure(ip);
                plugin.alert("Respuesta de handshake inválida desde: " + ip);
                kickPlayer(player, "invalid");
            }
            case NO_CHALLENGE -> {
                // Ignorar si no hay desafío pendiente
            }
        }
    }

    private void incrementFailure(String ip) {
        failureCount.merge(ip, 1, Integer::sum);
    }

    private void kickPlayer(Player player, String reason) {
        String message = ChatColor.translateAlternateColorCodes('&',
            plugin.getConfig().getString("messages.kick-handshake-failed", "&cConexión rechazada."));
        
        Bukkit.getScheduler().runTask(plugin, () -> player.kickPlayer(message));
    }

    private String extractIP(PacketEvent event) {
        try {
            InetSocketAddress address = event.getPlayer().getAddress();
            if (address != null) {
                return address.getAddress().getHostAddress();
            }
        } catch (Exception e) {
            // Fallback silencioso
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
        return verifiedIPs.contains(ip);
    }

    public int getFailureCount(String ip) {
        return failureCount.getOrDefault(ip, 0);
    }
}
