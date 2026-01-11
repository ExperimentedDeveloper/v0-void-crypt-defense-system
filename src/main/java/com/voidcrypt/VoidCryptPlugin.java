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
import com.voidcrypt.security.SecurityValidator;
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
    
    // Modules
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
        
        // Save default configuration
        saveDefaultConfig();
        
        String secretKey = getConfig().getString("void-handshake.secret-key", "DEFAULT_KEY");
        if (!SecurityValidator.isSecretKeyValid(secretKey)) {
            getLogger().severe("==============================================");
            getLogger().severe("CRITICAL SECURITY ERROR!");
            getLogger().severe("You MUST change the secret-key in config.yml");
            getLogger().severe("The key must be at least 16 characters and complex.");
            getLogger().severe("VoidCrypt will NOT enable until this is fixed.");
            getLogger().severe("==============================================");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        
        // Check ProtocolLib dependency
        if (!checkProtocolLib()) {
            getLogger().severe("ProtocolLib not found! VoidCrypt requires ProtocolLib.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        
        protocolManager = ProtocolLibrary.getProtocolManager();
        
        logManager = new LogManager(this);
        notificationManager = new NotificationManager(this);
        
        // Initialize modules
        initializeModules();
        
        // Register commands
        getCommand("voidcrypt").setExecutor(new VoidCryptCommand(this));
        
        if (getConfig().getBoolean("integrity-scanner.scan-on-startup", true) && fileIntegrityChecker != null) {
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                fileIntegrityChecker.scanPlugins();
                if (configurationAuditor != null) {
                    configurationAuditor.auditServerProperties();
                }
            });
        }
        
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, 
            SecurityValidator::cleanupRateLimits, 6000L, 6000L);
        
        logStartup();
        logManager.info("System started successfully", "Core");
    }

    @Override
    public void onDisable() {
        if (protocolManager != null) {
            protocolManager.removePacketListeners(this);
        }
        getLogger().info("VoidCrypt Defense System disabled.");
    }

    private boolean checkProtocolLib() {
        return Bukkit.getPluginManager().getPlugin("ProtocolLib") != null;
    }

    private void initializeModules() {
        // Module 1: Void Handshake Protocol
        if (getConfig().getBoolean("void-handshake.enabled", true)) {
            cryptographicChallenge = new CryptographicChallenge(this);
            handshakeInterceptor = new HandshakeInterceptor(this, protocolManager, cryptographicChallenge);
            getLogger().info("[OK] Void Handshake Protocol module enabled");
        }
        
        // Module 2: Shadow Session
        sessionGuardian = new SessionGuardian(this);
        Bukkit.getPluginManager().registerEvents(sessionGuardian, this);
        getLogger().info("[OK] Shadow Session module enabled");
        
        // Module 3: Threat Radar
        trafficAnalyzer = new TrafficAnalyzer(this, protocolManager);
        threatRadarRenderer = new ThreatRadarRenderer(this, trafficAnalyzer, sessionGuardian);
        getLogger().info("[OK] Threat Radar module enabled");
        
        // Module 4: Core Integrity Scanner
        if (getConfig().getBoolean("integrity-scanner.enabled", true)) {
            fileIntegrityChecker = new FileIntegrityChecker(this);
            configurationAuditor = new ConfigurationAuditor(this);
            getLogger().info("[OK] Core Integrity Scanner module enabled");
        }
        
        // Module 5: Phantom Ports
        if (getConfig().getBoolean("phantom-ports.active", true)) {
            firewallExecutor = new FirewallExecutor(this);
            phantomPortListener = new PhantomPortListener(this, protocolManager, firewallExecutor, sessionGuardian);
            getLogger().info("[OK] Phantom Ports module enabled");
        }
    }

    private void logStartup() {
        getLogger().info("╔══════════════════════════════════════╗");
        getLogger().info("║     VoidCrypt Defense System v1.0    ║");
        getLogger().info("║        Defense System Active         ║");
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

    public FirewallExecutor getFirewallExecutor() {
        return firewallExecutor;
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
        getLogger().warning("[ALERT] " + message);
        if (logManager != null) {
            logManager.alert(message, "Alert");
        }
        if (notificationManager != null) {
            notificationManager.sendNotification(message);
        }
    }

    public void auditLog(Level level, String event, String details) {
        String logMessage = String.format("AUDIT [%s]: %s | %s", 
            level.getName(), event, details);
        getLogger().log(level, logMessage);
        if (logManager != null) {
            logManager.info(logMessage, "Audit");
        }
    }
}
