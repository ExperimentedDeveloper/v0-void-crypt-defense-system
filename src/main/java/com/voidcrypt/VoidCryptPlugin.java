package com.voidcrypt;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.voidcrypt.commands.VoidCryptCommand;
import com.voidcrypt.gui.LogManager;
import com.voidcrypt.gui.NotificationManager;
import com.voidcrypt.phantom.FirewallExecutor;
import com.voidcrypt.phantom.PhantomPortListener;
import com.voidcrypt.radar.ThreatRadarRenderer;
import com.voidcrypt.radar.TrafficAnalyzer;
import com.voidcrypt.scanner.ConfigurationAuditor;
import com.voidcrypt.scanner.FileIntegrityChecker;
import com.voidcrypt.shadow.SessionGuardian;
import com.voidcrypt.zte.CryptographicChallenge;
import com.voidcrypt.zte.HandshakeInterceptor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Level;

public class VoidCryptPlugin extends JavaPlugin {

    private static VoidCryptPlugin instance;
    private ProtocolManager protocolManager;
    
    // Módulos
    private CryptographicChallenge cryptographicChallenge;
    private HandshakeInterceptor handshakeInterceptor;
    private SessionGuardian sessionGuardian;
    private TrafficAnalyzer trafficAnalyzer;
    private ThreatRadarRenderer threatRadarRenderer;
    private FileIntegrityChecker fileIntegrityChecker;
    private ConfigurationAuditor configurationAuditor;
    private PhantomPortListener phantomPortListener;
    private FirewallExecutor firewallExecutor;
    private LogManager logManager;
    private NotificationManager notificationManager;

    @Override
    public void onEnable() {
        instance = this;
        
        // Guardar configuración por defecto
        saveDefaultConfig();
        
        // Verificar dependencia ProtocolLib
        if (!checkProtocolLib()) {
            getLogger().severe("ProtocolLib no encontrado! VoidCrypt requiere ProtocolLib.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        
        protocolManager = ProtocolLibrary.getProtocolManager();
        
        logManager = new LogManager(this);
        notificationManager = new NotificationManager(this);
        
        // Inicializar módulos
        initializeModules();
        
        // Registrar comandos
        getCommand("voidcrypt").setExecutor(new VoidCryptCommand(this));
        
        // Escaneo inicial si está habilitado
        if (getConfig().getBoolean("integrity-scanner.scan-on-startup", true)) {
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                fileIntegrityChecker.scanPlugins();
                configurationAuditor.auditServerProperties();
            });
        }
        
        logStartup();
        logManager.info("Sistema iniciado correctamente", "Core");
    }

    @Override
    public void onDisable() {
        if (protocolManager != null) {
            protocolManager.removePacketListeners(this);
        }
        getLogger().info("VoidCrypt Defense System desactivado.");
    }

    private boolean checkProtocolLib() {
        return Bukkit.getPluginManager().getPlugin("ProtocolLib") != null;
    }

    private void initializeModules() {
        // Módulo 1: Void Handshake Protocol
        if (getConfig().getBoolean("void-handshake.enabled", true)) {
            cryptographicChallenge = new CryptographicChallenge(this);
            handshakeInterceptor = new HandshakeInterceptor(this, protocolManager, cryptographicChallenge);
            getLogger().info("✓ Módulo Void Handshake Protocol activado");
        }
        
        // Módulo 2: Shadow Session
        sessionGuardian = new SessionGuardian(this);
        Bukkit.getPluginManager().registerEvents(sessionGuardian, this);
        getLogger().info("✓ Módulo Shadow Session activado");
        
        // Módulo 3: Threat Radar
        trafficAnalyzer = new TrafficAnalyzer(this, protocolManager);
        threatRadarRenderer = new ThreatRadarRenderer(this, trafficAnalyzer, sessionGuardian);
        getLogger().info("✓ Módulo Threat Radar activado");
        
        // Módulo 4: Core Integrity Scanner
        if (getConfig().getBoolean("integrity-scanner.enabled", true)) {
            fileIntegrityChecker = new FileIntegrityChecker(this);
            configurationAuditor = new ConfigurationAuditor(this);
            getLogger().info("✓ Módulo Core Integrity Scanner activado");
        }
        
        // Módulo 5: Phantom Ports
        if (getConfig().getBoolean("phantom-ports.active", true)) {
            firewallExecutor = new FirewallExecutor(this);
            phantomPortListener = new PhantomPortListener(this, protocolManager, firewallExecutor, sessionGuardian);
            getLogger().info("✓ Módulo Phantom Ports activado");
        }
    }

    private void logStartup() {
        getLogger().info("╔══════════════════════════════════════╗");
        getLogger().info("║     VoidCrypt Defense System v1.0    ║");
        getLogger().info("║      Sistema de Defensa Activo       ║");
        getLogger().info("╚══════════════════════════════════════╝");
    }

    public static VoidCryptPlugin getInstance() {
        return instance;
    }

    public ProtocolManager getProtocolManager() {
        return protocolManager;
    }

    public SessionGuardian getSessionGuardian() {
        return sessionGuardian;
    }

    public TrafficAnalyzer getTrafficAnalyzer() {
        return trafficAnalyzer;
    }

    public FileIntegrityChecker getFileIntegrityChecker() {
        return fileIntegrityChecker;
    }

    public ConfigurationAuditor getConfigurationAuditor() {
        return configurationAuditor;
    }

    public LogManager getLogManager() {
        return logManager;
    }

    public NotificationManager getNotificationManager() {
        return notificationManager;
    }

    public void alert(String message) {
        String prefix = ChatColor.translateAlternateColorCodes('&', 
            getConfig().getString("messages.alert-prefix", "&8[&4VoidCrypt&8] &c"));
        Bukkit.getOnlinePlayers().stream()
            .filter(p -> p.hasPermission("voidcrypt.admin"))
            .forEach(p -> p.sendMessage(prefix + message));
        getLogger().warning("[ALERTA] " + message);
        if (logManager != null) {
            logManager.alert(message, "Alert");
        }
        if (notificationManager != null) {
            notificationManager.sendNotification(message);
        }
    }
}
