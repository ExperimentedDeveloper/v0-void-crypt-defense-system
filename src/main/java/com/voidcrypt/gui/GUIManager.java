package com.voidcrypt.gui;

import com.voidcrypt.VoidCryptPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

/**
 * Gestor central de todas las GUIs del plugin
 */
public class GUIManager implements Listener {

    private final VoidCryptPlugin plugin;
    private final Set<UUID> openGUIs;
    
    // Identificadores de GUI
    public static final String MAIN_MENU = "voidcrypt_main";
    public static final String SESSIONS_MENU = "voidcrypt_sessions";
    public static final String PLAYER_DETAIL = "voidcrypt_player_";
    public static final String SECURITY_PANEL = "voidcrypt_security";
    public static final String FIREWALL_PANEL = "voidcrypt_firewall";
    public static final String LOGS_PANEL = "voidcrypt_logs";
    public static final String SETTINGS_PANEL = "voidcrypt_settings";

    public GUIManager(VoidCryptPlugin plugin) {
        this.plugin = plugin;
        this.openGUIs = new HashSet<>();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Abre el menú principal de VoidCrypt
     */
    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, 
            ChatColor.DARK_RED + "✦ " + ChatColor.RED + "VoidCrypt Panel" + ChatColor.DARK_RED + " ✦");
        
        // Decoración superior
        fillRow(inv, 0, createGlass(Material.BLACK_STAINED_GLASS_PANE));
        
        // Estado del sistema (centro superior)
        inv.setItem(4, createStatusItem());
        
        // Módulos principales
        inv.setItem(19, createItem(Material.SHIELD, ChatColor.GREEN + "Estado de Seguridad",
            ChatColor.GRAY + "Ver métricas de seguridad",
            ChatColor.GRAY + "y nivel de amenaza actual",
            "",
            ChatColor.YELLOW + "Click para abrir"));
        
        inv.setItem(21, createItem(Material.PLAYER_HEAD, ChatColor.AQUA + "Sesiones Activas",
            ChatColor.GRAY + "Gestionar sesiones de jugadores",
            ChatColor.GRAY + "Ver IPs y estados",
            "",
            ChatColor.YELLOW + "Click para abrir"));
        
        inv.setItem(23, createItem(Material.ENDER_EYE, ChatColor.LIGHT_PURPLE + "Radar de Amenazas",
            ChatColor.GRAY + "Monitor visual en tiempo real",
            ChatColor.GRAY + "del tráfico de red",
            "",
            ChatColor.YELLOW + "Click para obtener mapa"));
        
        inv.setItem(25, createItem(Material.COMPARATOR, ChatColor.GOLD + "Configuración",
            ChatColor.GRAY + "Ajustar parámetros del",
            ChatColor.GRAY + "sistema de defensa",
            "",
            ChatColor.YELLOW + "Click para abrir"));
        
        // Segunda fila de módulos
        inv.setItem(29, createItem(Material.BOOK, ChatColor.WHITE + "Escanear Plugins",
            ChatColor.GRAY + "Verificar integridad de",
            ChatColor.GRAY + "los archivos del servidor",
            "",
            ChatColor.YELLOW + "Click para escanear"));
        
        inv.setItem(31, createItem(Material.PAPER, ChatColor.WHITE + "Auditar Configuración",
            ChatColor.GRAY + "Revisar server.properties",
            ChatColor.GRAY + "y detectar vulnerabilidades",
            "",
            ChatColor.YELLOW + "Click para auditar"));
        
        inv.setItem(33, createItem(Material.BARRIER, ChatColor.RED + "Firewall & Bans",
            ChatColor.GRAY + "Gestionar IPs bloqueadas",
            ChatColor.GRAY + "y reglas de firewall",
            "",
            ChatColor.YELLOW + "Click para abrir"));
        
        // Fila de acciones rápidas
        inv.setItem(39, createItem(Material.EXPERIENCE_BOTTLE, ChatColor.GREEN + "Logs en Vivo",
            ChatColor.GRAY + "Ver eventos de seguridad",
            ChatColor.GRAY + "en tiempo real",
            "",
            ChatColor.YELLOW + "Click para ver"));
        
        inv.setItem(41, createItem(Material.REDSTONE, ChatColor.RED + "Modo Alerta",
            isAlertModeEnabled() ? ChatColor.GREEN + "ACTIVO" : ChatColor.GRAY + "INACTIVO",
            "",
            ChatColor.YELLOW + "Click para toggle"));
        
        // Decoración inferior
        fillRow(inv, 5, createGlass(Material.BLACK_STAINED_GLASS_PANE));
        
        // Botón cerrar
        inv.setItem(49, createItem(Material.ARROW, ChatColor.RED + "Cerrar"));
        
        player.openInventory(inv);
        openGUIs.add(player.getUniqueId());
    }

    /**
     * Abre el panel de sesiones activas
     */
    public void openSessionsPanel(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54,
            ChatColor.AQUA + "✦ Sesiones Activas ✦");
        
        // Decoración
        fillRow(inv, 0, createGlass(Material.CYAN_STAINED_GLASS_PANE));
        fillRow(inv, 5, createGlass(Material.CYAN_STAINED_GLASS_PANE));
        
        // Poblar con jugadores
        var sessions = plugin.getSessionGuardian().getAllSessions();
        int slot = 10;
        
        for (var entry : sessions.entrySet()) {
            if (slot >= 44) break;
            if (slot % 9 == 0) slot++; // Skip bordes
            if (slot % 9 == 8) slot += 2;
            
            Player target = Bukkit.getPlayer(entry.getKey());
            var fingerprint = entry.getValue();
            
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            
            if (meta != null && target != null) {
                meta.setOwningPlayer(target);
                
                ChatColor statusColor = switch (fingerprint.getStatus()) {
                    case ACTIVE -> ChatColor.GREEN;
                    case SUSPICIOUS -> ChatColor.YELLOW;
                    case UNDER_INVESTIGATION -> ChatColor.GOLD;
                    case COMPROMISED -> ChatColor.RED;
                };
                
                meta.setDisplayName(statusColor + target.getName());
                meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "IP: " + ChatColor.WHITE + fingerprint.getBoundIP(),
                    ChatColor.GRAY + "Estado: " + statusColor + fingerprint.getStatus().name(),
                    ChatColor.GRAY + "Sospecha: " + ChatColor.WHITE + fingerprint.getSuspicionLevel() + "/10",
                    ChatColor.GRAY + "Hash: " + ChatColor.DARK_GRAY + fingerprint.getProtocolHash(),
                    "",
                    ChatColor.YELLOW + "Click izquierdo: " + ChatColor.WHITE + "Ver detalles",
                    ChatColor.YELLOW + "Click derecho: " + ChatColor.WHITE + "Acciones"
                ));
                skull.setItemMeta(meta);
            }
            
            inv.setItem(slot, skull);
            slot++;
        }
        
        // Botones de navegación
        inv.setItem(45, createItem(Material.ARROW, ChatColor.GRAY + "Volver"));
        inv.setItem(49, createItem(Material.COMPASS, ChatColor.WHITE + "Refrescar"));
        
        player.openInventory(inv);
        openGUIs.add(player.getUniqueId());
    }

    /**
     * Abre el panel de detalles de un jugador
     */
    public void openPlayerDetail(Player admin, Player target) {
        var fingerprint = plugin.getSessionGuardian().getFingerprint(target.getUniqueId());
        if (fingerprint == null) return;
        
        Inventory inv = Bukkit.createInventory(null, 45,
            ChatColor.GOLD + "✦ " + target.getName() + " ✦");
        
        // Decoración
        fillRow(inv, 0, createGlass(Material.ORANGE_STAINED_GLASS_PANE));
        fillRow(inv, 4, createGlass(Material.ORANGE_STAINED_GLASS_PANE));
        
        ChatColor statusColor = switch (fingerprint.getStatus()) {
            case ACTIVE -> ChatColor.GREEN;
            case SUSPICIOUS -> ChatColor.YELLOW;
            case UNDER_INVESTIGATION -> ChatColor.GOLD;
            case COMPROMISED -> ChatColor.RED;
        };
        
        // Información del jugador
        inv.setItem(13, createPlayerHead(target, statusColor));
        
        // Detalles de sesión
        inv.setItem(20, createItem(Material.NAME_TAG, ChatColor.WHITE + "UUID",
            ChatColor.GRAY + target.getUniqueId().toString()));
        
        inv.setItem(21, createItem(Material.COMPASS, ChatColor.WHITE + "IP Vinculada",
            ChatColor.GRAY + fingerprint.getBoundIP()));
        
        inv.setItem(22, createItem(Material.CLOCK, ChatColor.WHITE + "Duración Sesión",
            ChatColor.GRAY + formatDuration(fingerprint.getSessionDurationMs())));
        
        inv.setItem(23, createItem(Material.PAPER, ChatColor.WHITE + "Hash Protocolo",
            ChatColor.GRAY + fingerprint.getProtocolHash()));
        
        inv.setItem(24, createItem(Material.REDSTONE, ChatColor.WHITE + "Nivel Sospecha",
            ChatColor.GRAY + fingerprint.getSuspicionLevel() + "/10",
            getProgressBar(fingerprint.getSuspicionLevel(), 10)));
        
        // Acciones
        inv.setItem(29, createItem(Material.ENDER_PEARL, ChatColor.AQUA + "Teletransportar",
            ChatColor.GRAY + "Ir a la ubicación del jugador"));
        
        inv.setItem(30, createItem(Material.SPYGLASS, ChatColor.LIGHT_PURPLE + "Investigar",
            ChatColor.GRAY + "Marcar para monitoreo activo"));
        
        inv.setItem(31, createItem(Material.GOLDEN_SWORD, ChatColor.YELLOW + "Advertir",
            ChatColor.GRAY + "Enviar advertencia al jugador"));
        
        inv.setItem(32, createItem(Material.IRON_DOOR, ChatColor.GOLD + "Expulsar",
            ChatColor.GRAY + "Desconectar del servidor"));
        
        inv.setItem(33, createItem(Material.BARRIER, ChatColor.RED + "Banear",
            ChatColor.GRAY + "Banear IP y cuenta"));
        
        // Navegación
        inv.setItem(36, createItem(Material.ARROW, ChatColor.GRAY + "Volver"));
        
        admin.openInventory(inv);
        openGUIs.add(admin.getUniqueId());
    }

    /**
     * Abre el panel de seguridad con métricas
     */
    public void openSecurityPanel(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54,
            ChatColor.GREEN + "✦ Estado de Seguridad ✦");
        
        // Decoración
        fillRow(inv, 0, createGlass(Material.LIME_STAINED_GLASS_PANE));
        fillRow(inv, 5, createGlass(Material.LIME_STAINED_GLASS_PANE));
        
        var traffic = plugin.getTrafficAnalyzer();
        
        // TPS
        double tps = traffic.getSystemLoad();
        ChatColor tpsColor = tps >= 18 ? ChatColor.GREEN : (tps >= 15 ? ChatColor.YELLOW : ChatColor.RED);
        inv.setItem(10, createItem(Material.CLOCK, tpsColor + "TPS: " + String.format("%.2f", tps),
            getProgressBar((int) tps, 20),
            "",
            ChatColor.GRAY + "20 TPS = rendimiento óptimo"));
        
        // Tráfico de red
        int packets = traffic.getPacketVolume();
        var status = traffic.getNetworkStatus();
        ChatColor netColor = switch (status) {
            case NORMAL -> ChatColor.GREEN;
            case ELEVATED -> ChatColor.YELLOW;
            case WARNING -> ChatColor.GOLD;
            case CRITICAL -> ChatColor.RED;
        };
        inv.setItem(12, createItem(Material.HOPPER, netColor + "Tráfico: " + packets + " pkt/s",
            ChatColor.GRAY + "Estado: " + netColor + status.name(),
            ChatColor.GRAY + "Pico: " + traffic.getPeakPacketsPerSecond() + " pkt/s",
            ChatColor.GRAY + "Total: " + formatNumber(traffic.getTotalPackets())));
        
        // Nivel de amenaza
        double threat = traffic.getThreatLevel();
        int threatPercent = (int) (threat * 100);
        ChatColor threatColor = threat < 0.3 ? ChatColor.GREEN : 
                                (threat < 0.6 ? ChatColor.YELLOW : ChatColor.RED);
        inv.setItem(14, createItem(Material.TNT, threatColor + "Nivel Amenaza: " + threatPercent + "%",
            getProgressBar(threatPercent, 100),
            "",
            ChatColor.GRAY + "Basado en tráfico y TPS"));
        
        // Sesiones
        int sessions = plugin.getSessionGuardian().getActiveSessionCount();
        long suspicious = plugin.getSessionGuardian().getAllSessions().values().stream()
            .filter(s -> s.getSuspicionLevel() > 0).count();
        inv.setItem(16, createItem(Material.PLAYER_HEAD, ChatColor.AQUA + "Sesiones: " + sessions,
            ChatColor.GRAY + "Sospechosas: " + ChatColor.YELLOW + suspicious,
            "",
            ChatColor.GRAY + "Jugadores conectados actualmente"));
        
        // Puntuación de seguridad
        if (plugin.getConfigurationAuditor() != null) {
            int score = plugin.getConfigurationAuditor().getSecurityScore();
            ChatColor scoreColor = score >= 80 ? ChatColor.GREEN : 
                                   (score >= 50 ? ChatColor.YELLOW : ChatColor.RED);
            inv.setItem(22, createItem(Material.SHIELD, scoreColor + "Puntuación: " + score + "/100",
                getProgressBar(score, 100),
                "",
                ChatColor.GRAY + "Basado en configuración del servidor"));
        }
        
        // Módulos activos
        inv.setItem(28, createModuleStatus("Void Handshake", 
            plugin.getConfig().getBoolean("void-handshake.enabled", true)));
        inv.setItem(29, createModuleStatus("Shadow Session", true));
        inv.setItem(30, createModuleStatus("Threat Radar", true));
        inv.setItem(31, createModuleStatus("Integrity Scanner", 
            plugin.getConfig().getBoolean("integrity-scanner.enabled", true)));
        inv.setItem(32, createModuleStatus("Phantom Ports", 
            plugin.getConfig().getBoolean("phantom-ports.active", true)));
        
        // Navegación
        inv.setItem(45, createItem(Material.ARROW, ChatColor.GRAY + "Volver"));
        inv.setItem(49, createItem(Material.COMPASS, ChatColor.WHITE + "Refrescar"));
        
        player.openInventory(inv);
        openGUIs.add(player.getUniqueId());
    }

    /**
     * Abre el panel de firewall
     */
    public void openFirewallPanel(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54,
            ChatColor.RED + "✦ Firewall & Bans ✦");
        
        fillRow(inv, 0, createGlass(Material.RED_STAINED_GLASS_PANE));
        fillRow(inv, 5, createGlass(Material.RED_STAINED_GLASS_PANE));
        
        // IPs baneadas
        var bannedIPs = Bukkit.getBanList(org.bukkit.BanList.Type.IP).getBanEntries();
        int slot = 10;
        
        for (var ban : bannedIPs) {
            if (slot >= 44) break;
            if (slot % 9 == 0) slot++;
            if (slot % 9 == 8) slot += 2;
            
            inv.setItem(slot, createItem(Material.BARRIER, ChatColor.RED + ban.getTarget(),
                ChatColor.GRAY + "Razón: " + ChatColor.WHITE + ban.getReason(),
                ChatColor.GRAY + "Por: " + ChatColor.WHITE + ban.getSource(),
                ChatColor.GRAY + "Expira: " + ChatColor.WHITE + 
                    (ban.getExpiration() != null ? ban.getExpiration().toString() : "Permanente"),
                "",
                ChatColor.YELLOW + "Click para desbanear"));
            slot++;
        }
        
        if (bannedIPs.isEmpty()) {
            inv.setItem(22, createItem(Material.EMERALD, ChatColor.GREEN + "Sin IPs baneadas",
                ChatColor.GRAY + "No hay IPs bloqueadas actualmente"));
        }
        
        // Acciones
        inv.setItem(48, createItem(Material.ANVIL, ChatColor.GOLD + "Banear IP Manual",
            ChatColor.GRAY + "Añadir una IP a la lista negra"));
        inv.setItem(50, createItem(Material.WATER_BUCKET, ChatColor.AQUA + "Limpiar Bans Expirados",
            ChatColor.GRAY + "Eliminar bans caducados"));
        
        inv.setItem(45, createItem(Material.ARROW, ChatColor.GRAY + "Volver"));
        
        player.openInventory(inv);
        openGUIs.add(player.getUniqueId());
    }

    /**
     * Abre el panel de configuración
     */
    public void openSettingsPanel(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54,
            ChatColor.GOLD + "✦ Configuración ✦");
        
        fillRow(inv, 0, createGlass(Material.YELLOW_STAINED_GLASS_PANE));
        fillRow(inv, 5, createGlass(Material.YELLOW_STAINED_GLASS_PANE));
        
        // Void Handshake
        boolean vhEnabled = plugin.getConfig().getBoolean("void-handshake.enabled", true);
        inv.setItem(10, createToggleItem("Void Handshake", vhEnabled,
            "Verificación criptográfica",
            "al conectar jugadores"));
        
        int difficulty = plugin.getConfig().getInt("void-handshake.difficulty", 4);
        inv.setItem(11, createItem(Material.EXPERIENCE_BOTTLE, 
            ChatColor.WHITE + "Dificultad PoW: " + ChatColor.GOLD + difficulty,
            ChatColor.GRAY + "Nivel de prueba de trabajo",
            "",
            ChatColor.YELLOW + "Click izq: +1 | Click der: -1"));
        
        // Shadow Session
        boolean ipLock = plugin.getConfig().getBoolean("shadow-session.enforce-ip-lock", true);
        inv.setItem(13, createToggleItem("Bloqueo IP", ipLock,
            "Vincular sesión a IP",
            "Detecta cambios de red"));
        
        boolean banOnSwap = plugin.getConfig().getBoolean("shadow-session.ban-on-swap", true);
        inv.setItem(14, createToggleItem("Ban en Cambio IP", banOnSwap,
            "Banear automáticamente al",
            "detectar cambio de IP"));
        
        int banDuration = plugin.getConfig().getInt("shadow-session.ban-duration-minutes", 60);
        inv.setItem(15, createItem(Material.CLOCK, 
            ChatColor.WHITE + "Duración Ban: " + ChatColor.GOLD + banDuration + " min",
            ChatColor.GRAY + "Tiempo de ban temporal",
            "",
            ChatColor.YELLOW + "Click izq: +10 | Click der: -10"));
        
        // Integrity Scanner
        boolean scanEnabled = plugin.getConfig().getBoolean("integrity-scanner.enabled", true);
        inv.setItem(19, createToggleItem("Scanner Integridad", scanEnabled,
            "Verificar checksums de",
            "plugins del servidor"));
        
        boolean scanOnStartup = plugin.getConfig().getBoolean("integrity-scanner.scan-on-startup", true);
        inv.setItem(20, createToggleItem("Escanear al Iniciar", scanOnStartup,
            "Escanear plugins al",
            "iniciar el servidor"));
        
        // Phantom Ports
        boolean phantomActive = plugin.getConfig().getBoolean("phantom-ports.active", true);
        inv.setItem(22, createToggleItem("Phantom Ports", phantomActive,
            "Honeypots para detectar",
            "escaneos de puertos"));
        
        boolean autoFirewall = plugin.getConfig().getBoolean("phantom-ports.auto-firewall", false);
        inv.setItem(23, createToggleItem("Firewall Auto", autoFirewall,
            "Bloquear IPs automáticamente",
            "con iptables/netsh"));
        
        // Acciones
        inv.setItem(31, createItem(Material.EMERALD, ChatColor.GREEN + "Guardar Cambios",
            ChatColor.GRAY + "Aplicar configuración"));
        
        inv.setItem(40, createItem(Material.REDSTONE_BLOCK, ChatColor.RED + "Restablecer",
            ChatColor.GRAY + "Volver a valores por defecto"));
        
        inv.setItem(45, createItem(Material.ARROW, ChatColor.GRAY + "Volver"));
        
        player.openInventory(inv);
        openGUIs.add(player.getUniqueId());
    }

    /**
     * Abre el panel de logs en vivo
     */
    public void openLogsPanel(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54,
            ChatColor.WHITE + "✦ Logs en Vivo ✦");
        
        fillRow(inv, 0, createGlass(Material.WHITE_STAINED_GLASS_PANE));
        fillRow(inv, 5, createGlass(Material.WHITE_STAINED_GLASS_PANE));
        
        // Los logs se actualizan dinámicamente
        inv.setItem(4, createItem(Material.BOOK, ChatColor.WHITE + "Eventos Recientes",
            ChatColor.GRAY + "Últimos 35 eventos de seguridad"));
        
        // Placeholder para logs (se llenarán dinámicamente)
        for (int i = 10; i < 44; i++) {
            if (i % 9 == 0 || i % 9 == 8) continue;
            inv.setItem(i, createItem(Material.GRAY_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + "Sin eventos"));
        }
        
        // Filtros
        inv.setItem(46, createItem(Material.LIME_DYE, ChatColor.GREEN + "Info"));
        inv.setItem(47, createItem(Material.YELLOW_DYE, ChatColor.YELLOW + "Advertencias"));
        inv.setItem(48, createItem(Material.RED_DYE, ChatColor.RED + "Alertas"));
        inv.setItem(50, createItem(Material.PURPLE_DYE, ChatColor.LIGHT_PURPLE + "Todos"));
        
        inv.setItem(45, createItem(Material.ARROW, ChatColor.GRAY + "Volver"));
        inv.setItem(53, createItem(Material.COMPASS, ChatColor.WHITE + "Refrescar"));
        
        player.openInventory(inv);
        openGUIs.add(player.getUniqueId());
    }

    // ==================== EVENT HANDLERS ====================
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        String title = event.getView().getTitle();
        if (!title.contains("VoidCrypt") && !title.contains("✦")) return;
        
        event.setCancelled(true);
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        // Main Menu
        if (title.contains("VoidCrypt Panel")) {
            handleMainMenuClick(player, event.getSlot(), clicked);
        }
        // Sessions Panel
        else if (title.contains("Sesiones Activas")) {
            handleSessionsClick(player, event.getSlot(), clicked, event.isRightClick());
        }
        // Security Panel
        else if (title.contains("Estado de Seguridad")) {
            handleSecurityClick(player, event.getSlot());
        }
        // Firewall Panel
        else if (title.contains("Firewall")) {
            handleFirewallClick(player, event.getSlot(), clicked);
        }
        // Settings Panel
        else if (title.contains("Configuración")) {
            handleSettingsClick(player, event.getSlot(), event.isRightClick());
        }
        // Player Detail
        else if (title.contains("✦") && !title.contains("Panel") && !title.contains("Activas")) {
            handlePlayerDetailClick(player, event.getSlot(), title);
        }
        // Logs Panel
        else if (title.contains("Logs")) {
            handleLogsClick(player, event.getSlot());
        }
    }

    private void handleMainMenuClick(Player player, int slot, ItemStack clicked) {
        switch (slot) {
            case 19 -> openSecurityPanel(player);
            case 21 -> openSessionsPanel(player);
            case 23 -> giveRadarMap(player);
            case 25 -> openSettingsPanel(player);
            case 29 -> {
                player.closeInventory();
                Bukkit.dispatchCommand(player, "voidcrypt scan");
            }
            case 31 -> {
                player.closeInventory();
                Bukkit.dispatchCommand(player, "voidcrypt audit");
            }
            case 33 -> openFirewallPanel(player);
            case 39 -> openLogsPanel(player);
            case 41 -> toggleAlertMode(player);
            case 49 -> player.closeInventory();
        }
    }

    private void handleSessionsClick(Player player, int slot, ItemStack clicked, boolean rightClick) {
        if (slot == 45) {
            openMainMenu(player);
            return;
        }
        if (slot == 49) {
            openSessionsPanel(player);
            return;
        }
        
        if (clicked.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) clicked.getItemMeta();
            if (meta != null && meta.getOwningPlayer() != null) {
                Player target = meta.getOwningPlayer().getPlayer();
                if (target != null) {
                    if (rightClick) {
                        openPlayerDetail(player, target);
                    } else {
                        openPlayerDetail(player, target);
                    }
                }
            }
        }
    }

    private void handleSecurityClick(Player player, int slot) {
        if (slot == 45) {
            openMainMenu(player);
        } else if (slot == 49) {
            openSecurityPanel(player);
        }
    }

    private void handleFirewallClick(Player player, int slot, ItemStack clicked) {
        if (slot == 45) {
            openMainMenu(player);
            return;
        }
        
        if (clicked.getType() == Material.BARRIER && clicked.hasItemMeta()) {
            String ip = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
            Bukkit.getBanList(org.bukkit.BanList.Type.IP).pardon(ip);
            player.sendMessage(ChatColor.GREEN + "IP desbaneada: " + ip);
            openFirewallPanel(player);
        }
        
        if (slot == 50) {
            // Limpiar bans expirados
            var bans = Bukkit.getBanList(org.bukkit.BanList.Type.IP).getBanEntries();
            int removed = 0;
            for (var ban : bans) {
                if (ban.getExpiration() != null && ban.getExpiration().before(new java.util.Date())) {
                    Bukkit.getBanList(org.bukkit.BanList.Type.IP).pardon(ban.getTarget());
                    removed++;
                }
            }
            player.sendMessage(ChatColor.GREEN + "Bans expirados eliminados: " + removed);
            openFirewallPanel(player);
        }
    }

    private void handleSettingsClick(Player player, int slot, boolean rightClick) {
        if (slot == 45) {
            openMainMenu(player);
            return;
        }
        
        // Toggle settings
        switch (slot) {
            case 10 -> toggleConfig(player, "void-handshake.enabled");
            case 11 -> adjustConfig(player, "void-handshake.difficulty", rightClick ? -1 : 1, 1, 8);
            case 13 -> toggleConfig(player, "shadow-session.enforce-ip-lock");
            case 14 -> toggleConfig(player, "shadow-session.ban-on-swap");
            case 15 -> adjustConfig(player, "shadow-session.ban-duration-minutes", rightClick ? -10 : 10, 10, 1440);
            case 19 -> toggleConfig(player, "integrity-scanner.enabled");
            case 20 -> toggleConfig(player, "integrity-scanner.scan-on-startup");
            case 22 -> toggleConfig(player, "phantom-ports.active");
            case 23 -> toggleConfig(player, "phantom-ports.auto-firewall");
            case 31 -> {
                plugin.saveConfig();
                player.sendMessage(ChatColor.GREEN + "Configuración guardada.");
            }
            case 40 -> {
                plugin.saveDefaultConfig();
                plugin.reloadConfig();
                player.sendMessage(ChatColor.YELLOW + "Configuración restablecida.");
                openSettingsPanel(player);
            }
        }
        
        if (slot >= 10 && slot <= 23) {
            openSettingsPanel(player);
        }
    }

    private void handlePlayerDetailClick(Player player, int slot, String title) {
        String targetName = ChatColor.stripColor(title).replace("✦ ", "").replace(" ✦", "");
        Player target = Bukkit.getPlayer(targetName);
        
        if (slot == 36) {
            openSessionsPanel(player);
            return;
        }
        
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Jugador no encontrado.");
            return;
        }
        
        switch (slot) {
            case 29 -> { // Teleport
                player.closeInventory();
                player.teleport(target.getLocation());
                player.sendMessage(ChatColor.GREEN + "Teletransportado a " + target.getName());
            }
            case 30 -> { // Investigate
                plugin.getSessionGuardian().flagForInvestigation(target.getUniqueId());
                player.sendMessage(ChatColor.YELLOW + "Jugador marcado para investigación.");
                openPlayerDetail(player, target);
            }
            case 31 -> { // Warn
                target.sendMessage(ChatColor.RED + "⚠ Has recibido una advertencia del sistema de seguridad.");
                player.sendMessage(ChatColor.YELLOW + "Advertencia enviada a " + target.getName());
            }
            case 32 -> { // Kick
                player.closeInventory();
                target.kickPlayer(ChatColor.RED + "Expulsado por el sistema de seguridad.");
                player.sendMessage(ChatColor.GOLD + "Jugador expulsado: " + target.getName());
            }
            case 33 -> { // Ban
                player.closeInventory();
                var fp = plugin.getSessionGuardian().getFingerprint(target.getUniqueId());
                if (fp != null) {
                    Bukkit.getBanList(org.bukkit.BanList.Type.IP).addBan(
                        fp.getBoundIP(), "Baneado por VoidCrypt", null, player.getName());
                }
                target.kickPlayer(ChatColor.RED + "Has sido baneado por el sistema de seguridad.");
                player.sendMessage(ChatColor.RED + "Jugador baneado: " + target.getName());
            }
        }
    }

    private void handleLogsClick(Player player, int slot) {
        if (slot == 45) {
            openMainMenu(player);
        } else if (slot == 53) {
            openLogsPanel(player);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        openGUIs.remove(event.getPlayer().getUniqueId());
    }

    // ==================== HELPER METHODS ====================

    private void giveRadarMap(Player player) {
        player.closeInventory();
        Bukkit.dispatchCommand(player, "voidcrypt radar");
    }

    private void toggleAlertMode(Player player) {
        boolean current = plugin.getConfig().getBoolean("alert-mode", false);
        plugin.getConfig().set("alert-mode", !current);
        player.sendMessage(ChatColor.YELLOW + "Modo alerta: " + 
            (!current ? ChatColor.GREEN + "ACTIVADO" : ChatColor.RED + "DESACTIVADO"));
        openMainMenu(player);
    }

    private boolean isAlertModeEnabled() {
        return plugin.getConfig().getBoolean("alert-mode", false);
    }

    private void toggleConfig(Player player, String path) {
        boolean current = plugin.getConfig().getBoolean(path, true);
        plugin.getConfig().set(path, !current);
    }

    private void adjustConfig(Player player, String path, int delta, int min, int max) {
        int current = plugin.getConfig().getInt(path, min);
        int newValue = Math.max(min, Math.min(max, current + delta));
        plugin.getConfig().set(path, newValue);
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createGlass(Material material) {
        ItemStack glass = new ItemStack(material);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            glass.setItemMeta(meta);
        }
        return glass;
    }

    private ItemStack createStatusItem() {
        var traffic = plugin.getTrafficAnalyzer();
        double threat = traffic.getThreatLevel();
        
        Material mat = threat < 0.3 ? Material.EMERALD : 
                       (threat < 0.6 ? Material.GOLD_INGOT : Material.REDSTONE);
        ChatColor color = threat < 0.3 ? ChatColor.GREEN : 
                          (threat < 0.6 ? ChatColor.YELLOW : ChatColor.RED);
        
        return createItem(mat, color + "Estado del Sistema",
            ChatColor.GRAY + "Nivel de amenaza: " + color + (int)(threat * 100) + "%",
            ChatColor.GRAY + "TPS: " + String.format("%.1f", traffic.getSystemLoad()),
            ChatColor.GRAY + "Sesiones: " + plugin.getSessionGuardian().getActiveSessionCount());
    }

    private ItemStack createPlayerHead(Player target, ChatColor color) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(target);
            meta.setDisplayName(color + target.getName());
            skull.setItemMeta(meta);
        }
        return skull;
    }

    private ItemStack createModuleStatus(String name, boolean enabled) {
        return createItem(enabled ? Material.LIME_DYE : Material.GRAY_DYE,
            (enabled ? ChatColor.GREEN : ChatColor.GRAY) + name,
            ChatColor.GRAY + "Estado: " + (enabled ? ChatColor.GREEN + "Activo" : ChatColor.RED + "Inactivo"));
    }

    private ItemStack createToggleItem(String name, boolean enabled, String... description) {
        List<String> lore = new ArrayList<>();
        for (String line : description) {
            lore.add(ChatColor.GRAY + line);
        }
        lore.add("");
        lore.add(enabled ? ChatColor.GREEN + "✔ Habilitado" : ChatColor.RED + "✘ Deshabilitado");
        lore.add(ChatColor.YELLOW + "Click para cambiar");
        
        ItemStack item = new ItemStack(enabled ? Material.LIME_DYE : Material.RED_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName((enabled ? ChatColor.GREEN : ChatColor.RED) + name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fillRow(Inventory inv, int row, ItemStack item) {
        for (int i = row * 9; i < (row + 1) * 9; i++) {
            inv.setItem(i, item);
        }
    }

    private String getProgressBar(int value, int max) {
        int filled = (int) ((value / (double) max) * 20);
        StringBuilder bar = new StringBuilder(ChatColor.GRAY + "[");
        
        ChatColor color = value < max * 0.3 ? ChatColor.RED :
                         (value < max * 0.6 ? ChatColor.YELLOW : ChatColor.GREEN);
        
        for (int i = 0; i < 20; i++) {
            if (i < filled) {
                bar.append(color).append("█");
            } else {
                bar.append(ChatColor.DARK_GRAY).append("░");
            }
        }
        bar.append(ChatColor.GRAY).append("]");
        return bar.toString();
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

    private String formatNumber(long num) {
        if (num >= 1_000_000) {
            return String.format("%.1fM", num / 1_000_000.0);
        } else if (num >= 1_000) {
            return String.format("%.1fK", num / 1_000.0);
        }
        return String.valueOf(num);
    }
}
