package com.voidcrypt.radar;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.voidcrypt.VoidCryptPlugin;
import org.bukkit.Bukkit;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Módulo 3B: Analizador de Tráfico
 * Monitorea el volumen de paquetes y la carga del sistema
 */
public class TrafficAnalyzer extends PacketAdapter {

    private final VoidCryptPlugin plugin;
    
    // Contadores atómicos para thread-safety
    private final AtomicInteger packetsThisSecond;
    private final AtomicInteger packetsLastSecond;
    private final AtomicLong totalPackets;
    private final AtomicInteger peakPacketsPerSecond;
    
    // Umbrales
    private static final int WARNING_THRESHOLD = 1000;  // Paquetes/segundo
    private static final int CRITICAL_THRESHOLD = 5000;

    public TrafficAnalyzer(VoidCryptPlugin plugin, ProtocolManager protocolManager) {
        super(plugin, ListenerPriority.MONITOR,
            PacketType.Play.Client.POSITION,
            PacketType.Play.Client.POSITION_LOOK,
            PacketType.Play.Client.LOOK,
            PacketType.Play.Client.FLYING,
            PacketType.Play.Client.CUSTOM_PAYLOAD);
        
        this.plugin = plugin;
        this.packetsThisSecond = new AtomicInteger(0);
        this.packetsLastSecond = new AtomicInteger(0);
        this.totalPackets = new AtomicLong(0);
        this.peakPacketsPerSecond = new AtomicInteger(0);
        
        protocolManager.addPacketListener(this);
        
        // Actualizar contador cada segundo
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::updateCounters, 20L, 20L);
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        if (event.isCancelled()) return;
        
        packetsThisSecond.incrementAndGet();
        totalPackets.incrementAndGet();
    }

    private void updateCounters() {
        int current = packetsThisSecond.getAndSet(0);
        packetsLastSecond.set(current);
        
        // Actualizar pico
        if (current > peakPacketsPerSecond.get()) {
            peakPacketsPerSecond.set(current);
        }
        
        // Alertas de tráfico
        if (current >= CRITICAL_THRESHOLD) {
            plugin.alert("⚠ CRÍTICO: Tráfico de red extremo detectado: " + current + " pkt/s");
        } else if (current >= WARNING_THRESHOLD) {
            plugin.getLogger().warning("Tráfico elevado: " + current + " pkt/s");
        }
    }

    /**
     * Obtiene el volumen de paquetes del último segundo
     */
    public int getPacketVolume() {
        return packetsLastSecond.get();
    }

    /**
     * Obtiene el TPS actual del servidor
     */
    public double getSystemLoad() {
        try {
            // Paper/Spigot TPS
            double[] tps = Bukkit.getTPS();
            return tps.length > 0 ? tps[0] : 20.0;
        } catch (Exception e) {
            return 20.0; // Asumir TPS normal si no está disponible
        }
    }

    /**
     * Calcula el nivel de amenaza basado en métricas
     * @return 0.0 (normal) a 1.0 (crítico)
     */
    public double getThreatLevel() {
        int packets = getPacketVolume();
        double tps = getSystemLoad();
        
        double packetFactor = Math.min(1.0, packets / (double) CRITICAL_THRESHOLD);
        double tpsFactor = Math.max(0.0, (20.0 - tps) / 20.0);
        
        return Math.min(1.0, (packetFactor * 0.6) + (tpsFactor * 0.4));
    }

    /**
     * Determina el estado actual de la red
     */
    public NetworkStatus getNetworkStatus() {
        int packets = getPacketVolume();
        double tps = getSystemLoad();
        
        if (packets >= CRITICAL_THRESHOLD || tps < 10) {
            return NetworkStatus.CRITICAL;
        } else if (packets >= WARNING_THRESHOLD || tps < 15) {
            return NetworkStatus.WARNING;
        } else if (packets >= WARNING_THRESHOLD / 2 || tps < 18) {
            return NetworkStatus.ELEVATED;
        }
        return NetworkStatus.NORMAL;
    }

    public long getTotalPackets() {
        return totalPackets.get();
    }

    public int getPeakPacketsPerSecond() {
        return peakPacketsPerSecond.get();
    }

    public enum NetworkStatus {
        NORMAL,    // Verde
        ELEVATED,  // Amarillo
        WARNING,   // Naranja
        CRITICAL   // Rojo
    }
}
