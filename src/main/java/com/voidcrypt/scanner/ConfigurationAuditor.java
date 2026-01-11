package com.voidcrypt.scanner;

import com.voidcrypt.VoidCryptPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Módulo 4B: Auditor de Configuración
 * Analiza server.properties en busca de configuraciones inseguras
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
     * Audita el archivo server.properties
     */
    public List<AuditFinding> auditServerProperties() {
        lastAuditResults.clear();
        securityScore = 100;
        
        File serverProperties = new File("server.properties");
        
        if (!serverProperties.exists()) {
            lastAuditResults.add(new AuditFinding(
                "server.properties",
                "Archivo no encontrado",
                Severity.WARNING,
                "No se puede auditar la configuración del servidor"
            ));
            return lastAuditResults;
        }
        
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(serverProperties)) {
            props.load(fis);
        } catch (IOException e) {
            lastAuditResults.add(new AuditFinding(
                "server.properties",
                "Error de lectura",
                Severity.ERROR,
                e.getMessage()
            ));
            return lastAuditResults;
        }
        
        // Verificaciones de seguridad
        checkOnlineMode(props);
        checkServerPort(props);
        checkQueryEnabled(props);
        checkRconEnabled(props);
        checkWhitelist(props);
        checkMaxPlayers(props);
        checkNetworkCompression(props);
        checkSpawnProtection(props);
        
        // Log resumen
        long critical = lastAuditResults.stream()
            .filter(f -> f.severity() == Severity.CRITICAL).count();
        long warnings = lastAuditResults.stream()
            .filter(f -> f.severity() == Severity.WARNING).count();
        
        plugin.getLogger().info(String.format(
            "Auditoría completada - Puntuación: %d/100 | Críticos: %d | Advertencias: %d",
            securityScore, critical, warnings));
        
        if (critical > 0) {
            plugin.alert("¡Configuración crítica detectada! Revisa /voidcrypt status");
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
                "¡CRÍTICO! Servidor en modo offline. Cualquiera puede conectarse con cualquier nombre."
            ));
            securityScore -= 40;
        } else {
            lastAuditResults.add(new AuditFinding(
                "online-mode",
                "true",
                Severity.OK,
                "Autenticación de Mojang habilitada"
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
                "Puerto por defecto. Considerar cambiar para reducir escaneos automáticos."
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
                "Query habilitado. Puede ser usado para ataques DoS y enumeración."
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
                    "habilitado con contraseña débil",
                    Severity.CRITICAL,
                    "¡CRÍTICO! RCON habilitado con contraseña débil o vacía."
                ));
                securityScore -= 30;
            } else {
                lastAuditResults.add(new AuditFinding(
                    "rcon",
                    "habilitado",
                    Severity.WARNING,
                    "RCON habilitado. Asegúrate de que el puerto esté protegido por firewall."
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
                "Whitelist deshabilitada. Cualquiera puede intentar conectarse."
            ));
        } else {
            lastAuditResults.add(new AuditFinding(
                "white-list",
                "true",
                Severity.OK,
                "Whitelist habilitada. Solo jugadores aprobados pueden conectarse."
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
                    "Límite alto de jugadores. Asegúrate de tener recursos suficientes."
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
                    "Compresión de red deshabilitada. Mayor uso de ancho de banda."
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
                    "Sin protección de spawn. Jugadores pueden modificar el área de spawn."
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
        OK,       // Configuración segura
        INFO,     // Información
        WARNING,  // Advertencia
        CRITICAL, // Crítico - debe corregirse
        ERROR     // Error al verificar
    }
}
