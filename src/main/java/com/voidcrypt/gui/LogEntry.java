package com.voidcrypt.gui;

import org.bukkit.ChatColor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Entrada de log para el sistema de eventos
 */
public class LogEntry {
    
    private final LocalDateTime timestamp;
    private final LogLevel level;
    private final String message;
    private final String source;
    
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public LogEntry(LogLevel level, String message, String source) {
        this.timestamp = LocalDateTime.now();
        this.level = level;
        this.message = message;
        this.source = source;
    }

    public String getFormattedTime() {
        return timestamp.format(TIME_FORMAT);
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public LogLevel getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    public String getSource() {
        return source;
    }

    public String getDisplayName() {
        return level.getColor() + "[" + getFormattedTime() + "] " + message;
    }

    public String[] getLore() {
        return new String[] {
            ChatColor.GRAY + "Fuente: " + ChatColor.WHITE + source,
            ChatColor.GRAY + "Nivel: " + level.getColor() + level.name(),
            ChatColor.GRAY + "Hora: " + ChatColor.WHITE + getFormattedTime()
        };
    }

    public enum LogLevel {
        INFO(ChatColor.GREEN),
        WARNING(ChatColor.YELLOW),
        ALERT(ChatColor.RED),
        CRITICAL(ChatColor.DARK_RED);

        private final ChatColor color;

        LogLevel(ChatColor color) {
            this.color = color;
        }

        public ChatColor getColor() {
            return color;
        }
    }
}
