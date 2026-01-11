package com.voidcrypt.scanner;

import com.voidcrypt.VoidCryptPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Module 4B: Configuration Auditor
 * Analyzes server.properties for insecure configurations
 * Translated to English
 */
public class ConfigurationAuditor {

    private final VoidCryptPlugin plugin;
    private final List<AuditFinding> lastAuditResults;
    private int securityScore;

    public ConfigurationAuditor(VoidCryptPlugin plugin) {
        this.plugin = plugin;
        this.lastAuditResults = new ArrayList<>();
        this.securityScore = 100;
    }

    /**
     * Audits the server.properties file
     */
    public List<AuditFinding> auditServerProperties() {
        lastAuditResults.clear();
        securityScore = 100;
        
        File serverProperties = new File("server.properties");
        
        if (!serverProperties.exists()) {
            lastAuditResults.add(new AuditFinding(
                "server.properties",
                "File not found",
                Severity.WARNING,
                "Cannot audit server configuration"
            ));
            return lastAuditResults;
        }
        
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(serverProperties)) {
            props.load(fis);
        } catch (IOException e) {
            lastAuditResults.add(new AuditFinding(
                "server.properties",
                "Read error",
                Severity.ERROR,
                e.getMessage()
            ));
            return lastAuditResults;
        }
        
        // Security checks
        checkOnlineMode(props);
        checkServerPort(props);
        checkQueryEnabled(props);
        checkRconEnabled(props);
        checkWhitelist(props);
        checkMaxPlayers(props);
        checkNetworkCompression(props);
        checkSpawnProtection(props);
        
        // Log summary
        long critical = lastAuditResults.stream()
            .filter(f -> f.severity() == Severity.CRITICAL).count();
        long warnings = lastAuditResults.stream()
            .filter(f -> f.severity() == Severity.WARNING).count();
        
        plugin.getLogger().info(String.format(
            "Audit completed - Score: %d/100 | Critical: %d | Warnings: %d",
            securityScore, critical, warnings));
        
        if (critical > 0) {
            plugin.alert("Critical configuration issue detected! Check /voidcrypt status");
        }
        
        return lastAuditResults;
    }

    private void checkOnlineMode(Properties props) {
        String onlineMode = props.getProperty("online-mode", "true");
        
        if ("false".equalsIgnoreCase(onlineMode)) {
            lastAuditResults.add(new AuditFinding(
                "online-mode",
                "false",
                Severity.CRITICAL,
                "CRITICAL! Server in offline mode. Anyone can connect with any username."
            ));
            securityScore -= 40;
        } else {
            lastAuditResults.add(new AuditFinding(
                "online-mode",
                "true",
                Severity.OK,
                "Mojang authentication enabled"
            ));
        }
    }

    private void checkServerPort(Properties props) {
        String port = props.getProperty("server-port", "25565");
        
        if ("25565".equals(port)) {
            lastAuditResults.add(new AuditFinding(
                "server-port",
                port,
                Severity.INFO,
                "Default port. Consider changing to reduce automated scans."
            ));
            securityScore -= 5;
        }
    }

    private void checkQueryEnabled(Properties props) {
        String queryEnabled = props.getProperty("enable-query", "false");
        
        if ("true".equalsIgnoreCase(queryEnabled)) {
            lastAuditResults.add(new AuditFinding(
                "enable-query",
                "true",
                Severity.WARNING,
                "Query enabled. Can be used for DoS attacks and enumeration."
            ));
            securityScore -= 15;
        }
    }

    private void checkRconEnabled(Properties props) {
        String rconEnabled = props.getProperty("enable-rcon", "false");
        String rconPassword = props.getProperty("rcon.password", "");
        
        if ("true".equalsIgnoreCase(rconEnabled)) {
            if (rconPassword.isEmpty() || rconPassword.length() < 12) {
                lastAuditResults.add(new AuditFinding(
                    "rcon",
                    "enabled with weak password",
                    Severity.CRITICAL,
                    "CRITICAL! RCON enabled with weak or empty password."
                ));
                securityScore -= 30;
            } else {
                lastAuditResults.add(new AuditFinding(
                    "rcon",
                    "enabled",
                    Severity.WARNING,
                    "RCON enabled. Ensure port is protected by firewall."
                ));
                securityScore -= 10;
            }
        }
    }

    private void checkWhitelist(Properties props) {
        String whitelist = props.getProperty("white-list", "false");
        
        if ("false".equalsIgnoreCase(whitelist)) {
            lastAuditResults.add(new AuditFinding(
                "white-list",
                "false",
                Severity.INFO,
                "Whitelist disabled. Anyone can attempt to connect."
            ));
        } else {
            lastAuditResults.add(new AuditFinding(
                "white-list",
                "true",
                Severity.OK,
                "Whitelist enabled. Only approved players can connect."
            ));
            securityScore += 5; // Bonus
        }
    }

    private void checkMaxPlayers(Properties props) {
        String maxPlayers = props.getProperty("max-players", "20");
        
        try {
            int max = Integer.parseInt(maxPlayers);
            if (max > 100) {
                lastAuditResults.add(new AuditFinding(
                    "max-players",
                    maxPlayers,
                    Severity.INFO,
                    "High player limit. Ensure you have sufficient resources."
                ));
            }
        } catch (NumberFormatException ignored) {}
    }

    private void checkNetworkCompression(Properties props) {
        String threshold = props.getProperty("network-compression-threshold", "256");
        
        try {
            int value = Integer.parseInt(threshold);
            if (value < 0) {
                lastAuditResults.add(new AuditFinding(
                    "network-compression-threshold",
                    threshold,
                    Severity.WARNING,
                    "Network compression disabled. Higher bandwidth usage."
                ));
            }
        } catch (NumberFormatException ignored) {}
    }

    private void checkSpawnProtection(Properties props) {
        String protection = props.getProperty("spawn-protection", "16");
        
        try {
            int value = Integer.parseInt(protection);
            if (value == 0) {
                lastAuditResults.add(new AuditFinding(
                    "spawn-protection",
                    "0",
                    Severity.INFO,
                    "No spawn protection. Players can modify spawn area."
                ));
            }
        } catch (NumberFormatException ignored) {}
    }

    public int getSecurityScore() {
        return Math.max(0, Math.min(100, securityScore));
    }

    public List<AuditFinding> getLastAuditResults() {
        return List.copyOf(lastAuditResults);
    }

    // Records
    public record AuditFinding(String property, String value, Severity severity, String description) {}
    
    public enum Severity {
        OK,       // Secure configuration
        INFO,     // Information
        WARNING,  // Warning
        CRITICAL, // Critical - must be fixed
        ERROR     // Error checking
    }
}
