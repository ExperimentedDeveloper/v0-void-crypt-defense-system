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
 * Module 3B: Traffic Analyzer
 * Monitors packet volume and system load
 * Translated to English
 */
public class TrafficAnalyzer extends PacketAdapter {

    private final VoidCryptPlugin plugin;
    
    // Atomic counters for thread-safety
    private final AtomicInteger packetsThisSecond;
    private final AtomicInteger packetsLastSecond;
    private final AtomicLong totalPackets;
    private final AtomicInteger peakPacketsPerSecond;
    
    // Thresholds
    private static final int WARNING_THRESHOLD = 1000;  // Packets/second
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
        
        // Update counter every second
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
        
        // Update peak
        if (current > peakPacketsPerSecond.get()) {
            peakPacketsPerSecond.set(current);
        }
        
        // Traffic alerts
        if (current >= CRITICAL_THRESHOLD) {
            plugin.alert("CRITICAL: Extreme network traffic detected: " + current + " pkt/s");
        } else if (current >= WARNING_THRESHOLD) {
            plugin.getLogger().warning("High traffic: " + current + " pkt/s");
        }
    }

    /**
     * Gets packet volume from last second
     */
    public int getPacketVolume() {
        return packetsLastSecond.get();
    }

    /**
     * Gets current server TPS
     */
    public double getSystemLoad() {
        try {
            // Paper/Spigot TPS
            double[] tps = Bukkit.getTPS();
            return tps.length > 0 ? tps[0] : 20.0;
        } catch (Exception e) {
            return 20.0; // Assume normal TPS if not available
        }
    }

    /**
     * Calculates threat level based on metrics
     * @return 0.0 (normal) to 1.0 (critical)
     */
    public double getThreatLevel() {
        int packets = getPacketVolume();
        double tps = getSystemLoad();
        
        double packetFactor = Math.min(1.0, packets / (double) CRITICAL_THRESHOLD);
        double tpsFactor = Math.max(0.0, (20.0 - tps) / 20.0);
        
        return Math.min(1.0, (packetFactor * 0.6) + (tpsFactor * 0.4));
    }

    /**
     * Determines current network status
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
        NORMAL,    // Green
        ELEVATED,  // Yellow
        WARNING,   // Orange
        CRITICAL   // Red
    }
}
