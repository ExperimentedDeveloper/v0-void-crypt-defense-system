package com.voidcrypt.gui;

import com.voidcrypt.VoidCryptPlugin;
import com.voidcrypt.shadow.SessionFingerprint;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Genera un libro con información detallada de un jugador
 */
public class PlayerInfoBook {

    private final VoidCryptPlugin plugin;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");

    public PlayerInfoBook(VoidCryptPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack createPlayerReport(Player target) {
        SessionFingerprint fp = plugin.getSessionGuardian().getFingerprint(target.getUniqueId());
        
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        
        if (meta == null || fp == null) return book;
        
        meta.setTitle(ChatColor.RED + "Reporte: " + target.getName());
        meta.setAuthor("VoidCrypt System");
        
        // Página 1: Información básica
        List<BaseComponent[]> pages = new ArrayList<>();
        
        StringBuilder page1 = new StringBuilder();
        page1.append(ChatColor.DARK_RED).append("═══ REPORTE ═══\n\n");
        page1.append(ChatColor.BLACK).append("Jugador: ").append(ChatColor.DARK_BLUE).append(target.getName()).append("\n\n");
        page1.append(ChatColor.BLACK).append("UUID:\n").append(ChatColor.DARK_GRAY);
        page1.append(target.getUniqueId().toString().substring(0, 18)).append("\n");
        page1.append(target.getUniqueId().toString().substring(18)).append("\n\n");
        page1.append(ChatColor.BLACK).append("IP: ").append(ChatColor.DARK_GREEN).append(fp.getBoundIP()).append("\n\n");
        
        ChatColor statusColor = switch (fp.getStatus()) {
            case ACTIVE -> ChatColor.DARK_GREEN;
            case SUSPICIOUS -> ChatColor.GOLD;
            case UNDER_INVESTIGATION -> ChatColor.RED;
            case COMPROMISED -> ChatColor.DARK_RED;
        };
        page1.append(ChatColor.BLACK).append("Estado: ").append(statusColor).append(fp.getStatus().name());
        
        pages.add(TextComponent.fromLegacyText(page1.toString()));
        
        // Página 2: Detalles de sesión
        StringBuilder page2 = new StringBuilder();
        page2.append(ChatColor.DARK_RED).append("═══ SESIÓN ═══\n\n");
        page2.append(ChatColor.BLACK).append("Hash: \n").append(ChatColor.DARK_GRAY).append(fp.getProtocolHash()).append("\n\n");
        page2.append(ChatColor.BLACK).append("Protocolo: ").append(ChatColor.DARK_BLUE).append(fp.getProtocolVersion()).append("\n\n");
        page2.append(ChatColor.BLACK).append("Sospecha: ").append(ChatColor.DARK_RED).append(fp.getSuspicionLevel()).append("/10\n\n");
        
        String duration = formatDuration(fp.getSessionDurationMs());
        page2.append(ChatColor.BLACK).append("Duración: ").append(ChatColor.DARK_GREEN).append(duration).append("\n\n");
        
        String startTime = Instant.ofEpochMilli(fp.getCreationTime())
            .atZone(ZoneId.systemDefault())
            .format(TIME_FORMAT);
        page2.append(ChatColor.BLACK).append("Inicio:\n").append(ChatColor.DARK_GRAY).append(startTime);
        
        pages.add(TextComponent.fromLegacyText(page2.toString()));
        
        // Página 3: Ubicación y stats
        StringBuilder page3 = new StringBuilder();
        page3.append(ChatColor.DARK_RED).append("═══ UBICACIÓN ═══\n\n");
        page3.append(ChatColor.BLACK).append("Mundo: ").append(ChatColor.DARK_BLUE).append(target.getWorld().getName()).append("\n\n");
        page3.append(ChatColor.BLACK).append("Coords:\n");
        page3.append(ChatColor.DARK_GRAY).append(String.format("X: %.1f\n", target.getLocation().getX()));
        page3.append(ChatColor.DARK_GRAY).append(String.format("Y: %.1f\n", target.getLocation().getY()));
        page3.append(ChatColor.DARK_GRAY).append(String.format("Z: %.1f\n\n", target.getLocation().getZ()));
        page3.append(ChatColor.BLACK).append("Ping: ").append(ChatColor.DARK_GREEN).append(target.getPing()).append("ms\n\n");
        page3.append(ChatColor.BLACK).append("Modo: ").append(ChatColor.DARK_BLUE).append(target.getGameMode().name());
        
        pages.add(TextComponent.fromLegacyText(page3.toString()));
        
        // Página 4: Acciones
        StringBuilder page4 = new StringBuilder();
        page4.append(ChatColor.DARK_RED).append("═══ ACCIONES ═══\n\n");
        page4.append(ChatColor.BLACK).append("Comandos rápidos:\n\n");
        page4.append(ChatColor.DARK_BLUE).append("[Teleport]\n");
        page4.append(ChatColor.DARK_BLUE).append("[Investigar]\n");
        page4.append(ChatColor.DARK_BLUE).append("[Advertir]\n");
        page4.append(ChatColor.DARK_BLUE).append("[Kick]\n");
        page4.append(ChatColor.DARK_RED).append("[Ban]\n\n");
        page4.append(ChatColor.DARK_GRAY).append("Usa /voidcrypt gui\npara acciones interactivas");
        
        pages.add(TextComponent.fromLegacyText(page4.toString()));
        
        // Añadir páginas al libro
        for (BaseComponent[] page : pages) {
            meta.spigot().addPage(page);
        }
        
        book.setItemMeta(meta);
        return book;
    }

    private String formatDuration(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m";
        } else if (minutes > 0) {
            return minutes + "m " + (seconds % 60) + "s";
        } else {
            return seconds + "s";
        }
    }
}
