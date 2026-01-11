package com.voidcrypt.gui;

import com.voidcrypt.VoidCryptPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Gestor de logs del sistema para la GUI
 */
public class LogManager {
    
    private final VoidCryptPlugin plugin;
    private final ConcurrentLinkedDeque<LogEntry> logs;
    private static final int MAX_LOGS = 100;

    public LogManager(VoidCryptPlugin plugin) {
        this.plugin = plugin;
        this.logs = new ConcurrentLinkedDeque<>();
    }

    public void log(LogEntry.LogLevel level, String message, String source) {
        LogEntry entry = new LogEntry(level, message, source);
        logs.addFirst(entry);
        
        // Mantener límite de logs
        while (logs.size() > MAX_LOGS) {
            logs.removeLast();
        }
    }

    public void info(String message, String source) {
        log(LogEntry.LogLevel.INFO, message, source);
    }

    public void warning(String message, String source) {
        log(LogEntry.LogLevel.WARNING, message, source);
    }

    public void alert(String message, String source) {
        log(LogEntry.LogLevel.ALERT, message, source);
    }

    public void critical(String message, String source) {
        log(LogEntry.LogLevel.CRITICAL, message, source);
    }

    public List<LogEntry> getRecentLogs(int count) {
        List<LogEntry> recent = new ArrayList<>();
        int i = 0;
        for (LogEntry entry : logs) {
            if (i++ >= count) break;
            recent.add(entry);
        }
        return recent;
    }

    public List<LogEntry> getLogsByLevel(LogEntry.LogLevel level, int count) {
        List<LogEntry> filtered = new ArrayList<>();
        for (LogEntry entry : logs) {
            if (entry.getLevel() == level) {
                filtered.add(entry);
                if (filtered.size() >= count) break;
            }
        }
        return filtered;
    }

    public void clear() {
        logs.clear();
    }

    public int getLogCount() {
        return logs.size();
    }
}
