package com.voidcrypt.gui;

import com.voidcrypt.VoidCryptPlugin;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Interactive notification system for admins
 * Translated to English
 */
public class NotificationManager {

    private final VoidCryptPlugin plugin;
    private final Map<UUID, List<Notification>> pendingNotifications;
    private final Set<UUID> mutedPlayers;

    public NotificationManager(VoidCryptPlugin plugin) {
        this.plugin = plugin;
        this.pendingNotifications = new HashMap<>();
        this.mutedPlayers = new HashSet<>();
    }

    /**
     * Sends a notification to all admins
     */
    public void notifyAdmins(NotificationType type, String message, String... actions) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("voidcrypt.notify") && !mutedPlayers.contains(player.getUniqueId())) {
                sendNotification(player, type, message, actions);
            }
        }
    }

    /**
     * Sends a notification to a specific player
     */
    public void sendNotification(Player player, NotificationType type, String message, String... actions) {
        // Prefix based on type
        String prefix = switch (type) {
            case INFO -> ChatColor.DARK_GRAY + "[" + ChatColor.GREEN + "i" + ChatColor.DARK_GRAY + "] ";
            case WARNING -> ChatColor.DARK_GRAY + "[" + ChatColor.YELLOW + "!" + ChatColor.DARK_GRAY + "] ";
            case ALERT -> ChatColor.DARK_GRAY + "[" + ChatColor.RED + "!!" + ChatColor.DARK_GRAY + "] ";
            case CRITICAL -> ChatColor.DARK_GRAY + "[" + ChatColor.DARK_RED + "!!!" + ChatColor.DARK_GRAY + "] ";
            case SUCCESS -> ChatColor.DARK_GRAY + "[" + ChatColor.GREEN + "+" + ChatColor.DARK_GRAY + "] ";
        };
        
        ChatColor messageColor = switch (type) {
            case INFO -> ChatColor.GRAY;
            case WARNING -> ChatColor.YELLOW;
            case ALERT -> ChatColor.RED;
            case CRITICAL -> ChatColor.DARK_RED;
            case SUCCESS -> ChatColor.GREEN;
        };
        
        // Main message
        TextComponent mainMessage = new TextComponent(prefix + messageColor + message);
        
        // Add clickable actions
        if (actions.length > 0) {
            mainMessage.addExtra("\n");
            for (int i = 0; i < actions.length; i += 2) {
                if (i + 1 < actions.length) {
                    String label = actions[i];
                    String command = actions[i + 1];
                    
                    TextComponent actionBtn = new TextComponent(
                        ChatColor.DARK_GRAY + "[" + ChatColor.AQUA + label + ChatColor.DARK_GRAY + "] "
                    );
                    actionBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
                    actionBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                        new Text(ChatColor.GRAY + "Click to: " + ChatColor.WHITE + command)));
                    mainMessage.addExtra(actionBtn);
                }
            }
        }
        
        player.spigot().sendMessage(mainMessage);
        
        // Sound based on type
        Sound sound = switch (type) {
            case INFO -> Sound.BLOCK_NOTE_BLOCK_PLING;
            case WARNING -> Sound.BLOCK_NOTE_BLOCK_BASS;
            case ALERT -> Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            case CRITICAL -> Sound.ENTITY_WITHER_SPAWN;
            case SUCCESS -> Sound.ENTITY_PLAYER_LEVELUP;
        };
        
        player.playSound(player.getLocation(), sound, 0.5f, 1.0f);
        
        // Save to pending
        Notification notif = new Notification(type, message, System.currentTimeMillis());
        pendingNotifications.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(notif);
        
        // Clean old notifications (older than 5 minutes)
        cleanOldNotifications(player.getUniqueId());
    }

    /**
     * Simple notification method for alerts
     */
    public void sendNotification(String message) {
        notifyAdmins(NotificationType.ALERT, message);
    }

    /**
     * Sends suspicious session alert
     */
    public void alertSuspiciousSession(String playerName, String ip, String reason) {
        notifyAdmins(NotificationType.ALERT, 
            "Suspicious session: " + ChatColor.WHITE + playerName + ChatColor.RED + " (" + ip + ")",
            "Investigate", "/voidcrypt investigate " + playerName,
            "Kick", "/voidcrypt kick " + playerName + " Suspicious activity",
            "Ban", "/voidcrypt ban " + playerName);
    }

    /**
     * Sends high traffic alert
     */
    public void alertHighTraffic(int packetsPerSecond) {
        notifyAdmins(NotificationType.WARNING,
            "High traffic detected: " + ChatColor.WHITE + packetsPerSecond + " pkt/s",
            "View Status", "/voidcrypt status",
            "Panel", "/voidcrypt gui security");
    }

    /**
     * Sends integrity breach alert
     */
    public void alertIntegrityBreach(String pluginName) {
        notifyAdmins(NotificationType.CRITICAL,
            "Integrity compromised: " + ChatColor.WHITE + pluginName,
            "Scan", "/voidcrypt scan",
            "Lockdown", "/voidcrypt lockdown on");
    }

    /**
     * Sends honeypot triggered alert
     */
    public void alertHoneypotTriggered(String ip, int port) {
        notifyAdmins(NotificationType.ALERT,
            "Honeypot triggered from " + ChatColor.WHITE + ip + ":" + port,
            "Ban IP", "/voidcrypt ban " + ip,
            "View Firewall", "/voidcrypt gui firewall");
    }

    public void toggleMute(Player player) {
        UUID uuid = player.getUniqueId();
        if (mutedPlayers.contains(uuid)) {
            mutedPlayers.remove(uuid);
            player.sendMessage(ChatColor.GREEN + "Notifications enabled.");
        } else {
            mutedPlayers.add(uuid);
            player.sendMessage(ChatColor.YELLOW + "Notifications muted.");
        }
    }

    public boolean isMuted(Player player) {
        return mutedPlayers.contains(player.getUniqueId());
    }

    public List<Notification> getPendingNotifications(UUID uuid) {
        return pendingNotifications.getOrDefault(uuid, new ArrayList<>());
    }

    public void clearNotifications(UUID uuid) {
        pendingNotifications.remove(uuid);
    }

    private void cleanOldNotifications(UUID uuid) {
        List<Notification> notifications = pendingNotifications.get(uuid);
        if (notifications != null) {
            long fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000);
            notifications.removeIf(n -> n.timestamp() < fiveMinutesAgo);
        }
    }

    public enum NotificationType {
        INFO, WARNING, ALERT, CRITICAL, SUCCESS
    }

    public record Notification(NotificationType type, String message, long timestamp) {}
}
