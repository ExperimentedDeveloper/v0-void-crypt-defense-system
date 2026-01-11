package com.voidcrypt.gui;

import com.voidcrypt.VoidCryptPlugin;
import com.voidcrypt.radar.TrafficAnalyzer;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * HUD de seguridad en tiempo real para administradores
 * Muestra estado del servidor en ActionBar y BossBar
 */
public class SecurityHUD {

    private final VoidCryptPlugin plugin;
    private final Map<UUID, BossBar> playerBossBars;
    private final Set<UUID> hudEnabled;
    private BukkitTask updateTask;

    public SecurityHUD(VoidCryptPlugin plugin) {
        this.plugin = plugin;
        this.playerBossBars = new HashMap<>();
        this.hudEnabled = new HashSet<>();
        startUpdateTask();
    }

    private void startUpdateTask() {
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAllHUDs, 20L, 10L);
    }

    public void toggleHUD(Player player) {
        UUID uuid = player.getUniqueId();
        if (hudEnabled.contains(uuid)) {
            disableHUD(player);
        } else {
            enableHUD(player);
        }
    }

    public void enableHUD(Player player) {
        UUID uuid = player.getUniqueId();
        hudEnabled.add(uuid);
        
        // Crear BossBar
        BossBar bossBar = Bukkit.createBossBar(
            ChatColor.RED + "VoidCrypt Security Monitor",
            BarColor.RED,
            BarStyle.SEGMENTED_10
        );
        bossBar.addPlayer(player);
        playerBossBars.put(uuid, bossBar);
        
        player.sendMessage(ChatColor.GREEN + "HUD de seguridad activado.");
    }

    public void disableHUD(Player player) {
        UUID uuid = player.getUniqueId();
        hudEnabled.remove(uuid);
        
        BossBar bossBar = playerBossBars.remove(uuid);
        if (bossBar != null) {
            bossBar.removePlayer(player);
        }
        
        player.sendMessage(ChatColor.YELLOW + "HUD de seguridad desactivado.");
    }

    private void updateAllHUDs() {
        TrafficAnalyzer traffic = plugin.getTrafficAnalyzer();
        double threat = traffic.getThreatLevel();
        double tps = traffic.getSystemLoad();
        int packets = traffic.getPacketVolume();
        int sessions = plugin.getSessionGuardian().getActiveSessionCount();
        var networkStatus = traffic.getNetworkStatus();
        
        // Determinar colores según estado
        ChatColor tpsColor = tps >= 18 ? ChatColor.GREEN : (tps >= 15 ? ChatColor.YELLOW : ChatColor.RED);
        ChatColor threatColor = threat < 0.3 ? ChatColor.GREEN : (threat < 0.6 ? ChatColor.YELLOW : ChatColor.RED);
        ChatColor netColor = switch (networkStatus) {
            case NORMAL -> ChatColor.GREEN;
            case ELEVATED -> ChatColor.YELLOW;
            case WARNING -> ChatColor.GOLD;
            case CRITICAL -> ChatColor.RED;
        };
        
        // ActionBar message
        String actionBarMsg = ChatColor.DARK_GRAY + "« " +
            ChatColor.GRAY + "TPS: " + tpsColor + String.format("%.1f", tps) + 
            ChatColor.DARK_GRAY + " | " +
            ChatColor.GRAY + "Amenaza: " + threatColor + (int)(threat * 100) + "%" +
            ChatColor.DARK_GRAY + " | " +
            ChatColor.GRAY + "Red: " + netColor + packets + "pkt/s" +
            ChatColor.DARK_GRAY + " | " +
            ChatColor.GRAY + "Sesiones: " + ChatColor.WHITE + sessions +
            ChatColor.DARK_GRAY + " »";
        
        // BossBar color
        BarColor barColor = threat < 0.3 ? BarColor.GREEN : 
                           (threat < 0.6 ? BarColor.YELLOW : BarColor.RED);
        
        for (UUID uuid : hudEnabled) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                hudEnabled.remove(uuid);
                BossBar bar = playerBossBars.remove(uuid);
                if (bar != null) bar.removeAll();
                continue;
            }
            
            // Update ActionBar
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, 
                TextComponent.fromLegacyText(actionBarMsg));
            
            // Update BossBar
            BossBar bossBar = playerBossBars.get(uuid);
            if (bossBar != null) {
                bossBar.setTitle(ChatColor.RED + "⚡ VoidCrypt " + 
                    ChatColor.GRAY + "| Threat: " + threatColor + (int)(threat * 100) + "%" +
                    ChatColor.GRAY + " | TPS: " + tpsColor + String.format("%.1f", tps) +
                    ChatColor.GRAY + " | " + netColor + networkStatus.name());
                bossBar.setProgress(Math.min(1.0, threat));
                bossBar.setColor(barColor);
            }
        }
    }

    public boolean isHUDEnabled(Player player) {
        return hudEnabled.contains(player.getUniqueId());
    }

    public void cleanup() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        for (BossBar bar : playerBossBars.values()) {
            bar.removeAll();
        }
        playerBossBars.clear();
        hudEnabled.clear();
    }

    public void removePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        hudEnabled.remove(uuid);
        BossBar bar = playerBossBars.remove(uuid);
        if (bar != null) {
            bar.removePlayer(player);
        }
    }
}
