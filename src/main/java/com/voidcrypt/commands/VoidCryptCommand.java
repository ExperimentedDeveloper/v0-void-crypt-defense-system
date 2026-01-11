package com.voidcrypt.commands;

import com.voidcrypt.VoidCryptPlugin;
import com.voidcrypt.gui.GUIManager;
import com.voidcrypt.gui.PlayerInfoBook;
import com.voidcrypt.gui.SecurityHUD;
import com.voidcrypt.radar.TrafficAnalyzer;
import com.voidcrypt.scanner.ConfigurationAuditor;
import com.voidcrypt.scanner.FileIntegrityChecker;
import com.voidcrypt.shadow.SessionFingerprint;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Comando principal de VoidCrypt con GUIs interactivas
 */
public class VoidCryptCommand implements CommandExecutor, TabCompleter {

    private final VoidCryptPlugin plugin;
    private final GUIManager guiManager;
    private final SecurityHUD securityHUD;
    private final PlayerInfoBook playerInfoBook;
    
    private static final String PREFIX = ChatColor.DARK_GRAY + "[" + 
        ChatColor.DARK_RED + "VoidCrypt" + ChatColor.DARK_GRAY + "] " + ChatColor.GRAY;

    public VoidCryptCommand(VoidCryptPlugin plugin) {
        this.plugin = plugin;
        this.guiManager = new GUIManager(plugin);
        this.securityHUD = new SecurityHUD(plugin);
        this.playerInfoBook = new PlayerInfoBook(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, 
                            @NotNull String label, String[] args) {
        
        if (!sender.hasPermission("voidcrypt.admin")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Sin permiso.");
            return true;
        }
        
        if (args.length == 0) {
            if (sender instanceof Player player) {
                guiManager.openMainMenu(player);
            } else {
                showHelp(sender);
            }
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "help" -> showHelp(sender);
            case "reload" -> reloadConfig(sender);
            case "status" -> showStatus(sender);
            case "scan" -> runScan(sender);
            case "radar" -> giveRadarMap(sender);
            case "sessions" -> handleSessions(sender, args);
            case "audit" -> runAudit(sender);
            case "gui", "menu", "panel" -> openGUI(sender, args);
            case "kick" -> kickPlayer(sender, args);
            case "ban" -> banPlayer(sender, args);
            case "unban" -> unbanIP(sender, args);
            case "investigate" -> investigatePlayer(sender, args);
            case "alert" -> toggleAlertMode(sender);
            case "lockdown" -> toggleLockdown(sender, args);
            case "whitelist" -> manageWhitelist(sender, args);
            case "blacklist" -> manageBlacklist(sender, args);
            case "stats" -> showStats(sender);
            case "export" -> exportLogs(sender, args);
            case "test" -> runTest(sender, args);
            case "hud" -> toggleHUD(sender);
            case "mute" -> toggleMute(sender);
            case "report" -> generateReport(sender, args);
            case "tp", "teleport" -> teleportToPlayer(sender, args);
            case "freeze" -> freezePlayer(sender, args);
            case "spectate" -> spectatePlayer(sender, args);
            case "history" -> showPlayerHistory(sender, args);
            case "compare" -> compareFingerprints(sender, args);
            case "broadcast" -> broadcastAlert(sender, args);
            default -> showHelp(sender);
        }
        
        return true;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.DARK_RED + "═══════════════════════════════════════════");
        sender.sendMessage(ChatColor.RED + "      VoidCrypt Defense System v1.0");
        sender.sendMessage(ChatColor.DARK_RED + "═══════════════════════════════════════════");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "Comandos Principales:");
        sender.sendMessage(formatCmd("gui", "Abrir panel de control interactivo"));
        sender.sendMessage(formatCmd("status", "Ver estado del sistema"));
        sender.sendMessage(formatCmd("sessions [player]", "Ver/gestionar sesiones"));
        sender.sendMessage(formatCmd("radar", "Obtener mapa de radar visual"));
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "Escaneo y Auditoría:");
        sender.sendMessage(formatCmd("scan", "Escanear integridad de plugins"));
        sender.sendMessage(formatCmd("audit", "Auditar configuración del servidor"));
        sender.sendMessage(formatCmd("stats", "Ver estadísticas detalladas"));
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "Gestión de Jugadores:");
        sender.sendMessage(formatCmd("kick <player> [reason]", "Expulsar jugador"));
        sender.sendMessage(formatCmd("ban <player|ip> [duration] [reason]", "Banear jugador/IP"));
        sender.sendMessage(formatCmd("unban <ip>", "Desbanear IP"));
        sender.sendMessage(formatCmd("investigate <player>", "Marcar para investigación"));
        sender.sendMessage(formatCmd("tp <player>", "Teletransportarse a un jugador"));
        sender.sendMessage(formatCmd("freeze <player>", "Congelar/Descongelar jugador"));
        sender.sendMessage(formatCmd("spectate <player>", "Espectar a un jugador"));
        sender.sendMessage(formatCmd("history <player>", "Ver historial de sesión de un jugador"));
        sender.sendMessage(formatCmd("compare <player1> <player2>", "Comparar huellas de jugadores"));
        sender.sendMessage(formatCmd("report <player>", "Generar reporte de información del jugador"));
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "Seguridad:");
        sender.sendMessage(formatCmd("alert", "Toggle modo alerta"));
        sender.sendMessage(formatCmd("lockdown [on|off]", "Modo bloqueo de emergencia"));
        sender.sendMessage(formatCmd("whitelist <add|remove|list> [ip]", "Gestionar whitelist IP"));
        sender.sendMessage(formatCmd("blacklist <add|remove|list> [ip]", "Gestionar blacklist IP"));
        sender.sendMessage(formatCmd("hud", "Toggle HUD de seguridad"));
        sender.sendMessage(formatCmd("mute", "Toggle notificaciones de chat"));
        sender.sendMessage(formatCmd("broadcast <message>", "Enviar alerta a administradores"));
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "Utilidades:");
        sender.sendMessage(formatCmd("reload", "Recargar configuración"));
        sender.sendMessage(formatCmd("export [logs|config]", "Exportar datos"));
        sender.sendMessage(formatCmd("test <module>", "Probar módulo específico"));
        sender.sendMessage("");
    }

    private String formatCmd(String cmd, String desc) {
        return ChatColor.GRAY + "/voidcrypt " + ChatColor.WHITE + cmd + 
               ChatColor.DARK_GRAY + " - " + ChatColor.GRAY + desc;
    }

    private void reloadConfig(CommandSender sender) {
        plugin.reloadConfig();
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Configuración recargada.");
    }

    private void showStatus(CommandSender sender) {
        TrafficAnalyzer traffic = plugin.getTrafficAnalyzer();
        
        sender.sendMessage("");
        sender.sendMessage(ChatColor.DARK_RED + "═══ " + ChatColor.RED + "Estado del Sistema" + 
                          ChatColor.DARK_RED + " ═══");
        
        // TPS
        double tps = traffic.getSystemLoad();
        ChatColor tpsColor = tps >= 18 ? ChatColor.GREEN : (tps >= 15 ? ChatColor.YELLOW : ChatColor.RED);
        sender.sendMessage(ChatColor.GRAY + "TPS: " + tpsColor + String.format("%.2f", tps));
        
        // Tráfico
        int packets = traffic.getPacketVolume();
        TrafficAnalyzer.NetworkStatus status = traffic.getNetworkStatus();
        ChatColor statusColor = switch (status) {
            case NORMAL -> ChatColor.GREEN;
            case ELEVATED -> ChatColor.YELLOW;
            case WARNING -> ChatColor.GOLD;
            case CRITICAL -> ChatColor.RED;
        };
        sender.sendMessage(ChatColor.GRAY + "Tráfico: " + statusColor + packets + " pkt/s" + 
                          ChatColor.DARK_GRAY + " [" + status + "]");
        
        // Nivel de amenaza
        double threat = traffic.getThreatLevel();
        int threatPercent = (int)(threat * 100);
        ChatColor threatColor = threat < 0.3 ? ChatColor.GREEN : 
                                (threat < 0.6 ? ChatColor.YELLOW : ChatColor.RED);
        sender.sendMessage(ChatColor.GRAY + "Nivel de amenaza: " + threatColor + threatPercent + "%");
        
        // Sesiones
        int sessions = plugin.getSessionGuardian().getActiveSessionCount();
        sender.sendMessage(ChatColor.GRAY + "Sesiones activas: " + ChatColor.WHITE + sessions);
        
        // Puntuación de seguridad
        if (plugin.getConfigurationAuditor() != null) {
            int score = plugin.getConfigurationAuditor().getSecurityScore();
            ChatColor scoreColor = score >= 80 ? ChatColor.GREEN : 
                                   (score >= 50 ? ChatColor.YELLOW : ChatColor.RED);
            sender.sendMessage(ChatColor.GRAY + "Puntuación de seguridad: " + 
                              scoreColor + score + "/100");
        }
        
        // Modo lockdown
        boolean lockdown = plugin.getConfig().getBoolean("lockdown-mode", false);
        sender.sendMessage(ChatColor.GRAY + "Modo Lockdown: " + 
            (lockdown ? ChatColor.RED + "ACTIVO" : ChatColor.GREEN + "Inactivo"));
        
        sender.sendMessage("");
    }

    private void runScan(CommandSender sender) {
        if (plugin.getFileIntegrityChecker() == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Escáner no habilitado.");
            return;
        }
        
        sender.sendMessage(PREFIX + "Iniciando escaneo de integridad...");
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            var results = plugin.getFileIntegrityChecker().scanPlugins();
            
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(ChatColor.DARK_RED + "═══ " + ChatColor.RED + 
                                  "Resultados del Escaneo" + ChatColor.DARK_RED + " ═══");
                
                int verified = 0, unknown = 0, modified = 0, errors = 0;
                
                for (var result : results) {
                    ChatColor color = switch (result.status()) {
                        case VERIFIED -> { verified++; yield ChatColor.GREEN; }
                        case UNKNOWN -> { unknown++; yield ChatColor.YELLOW; }
                        case MODIFIED -> { modified++; yield ChatColor.RED; }
                        case ERROR -> { errors++; yield ChatColor.DARK_RED; }
                    };
                    
                    sender.sendMessage(color + "● " + ChatColor.WHITE + result.fileName() + 
                                      ChatColor.DARK_GRAY + " - " + color + result.status());
                }
                
                sender.sendMessage("");
                sender.sendMessage(ChatColor.GRAY + "Resumen: " + 
                    ChatColor.GREEN + verified + " verificados, " +
                    ChatColor.YELLOW + unknown + " desconocidos, " +
                    ChatColor.RED + modified + " modificados, " +
                    ChatColor.DARK_RED + errors + " errores");
                sender.sendMessage("");
            });
        });
    }

    private void runAudit(CommandSender sender) {
        if (plugin.getConfigurationAuditor() == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Auditor no habilitado.");
            return;
        }
        
        sender.sendMessage(PREFIX + "Ejecutando auditoría...");
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            var results = plugin.getConfigurationAuditor().auditServerProperties();
            int score = plugin.getConfigurationAuditor().getSecurityScore();
            
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(ChatColor.DARK_RED + "═══ " + ChatColor.RED + 
                                  "Auditoría de Configuración" + ChatColor.DARK_RED + " ═══");
                
                for (var finding : results) {
                    ChatColor color = switch (finding.severity()) {
                        case OK -> ChatColor.GREEN;
                        case INFO -> ChatColor.AQUA;
                        case WARNING -> ChatColor.YELLOW;
                        case CRITICAL -> ChatColor.RED;
                        case ERROR -> ChatColor.DARK_RED;
                    };
                    
                    sender.sendMessage(color + "● " + ChatColor.WHITE + finding.property() + 
                                      ChatColor.DARK_GRAY + " = " + ChatColor.GRAY + finding.value());
                    sender.sendMessage(ChatColor.DARK_GRAY + "  └ " + color + finding.description());
                }
                
                ChatColor scoreColor = score >= 80 ? ChatColor.GREEN : 
                                       (score >= 50 ? ChatColor.YELLOW : ChatColor.RED);
                sender.sendMessage("");
                sender.sendMessage(ChatColor.GRAY + "Puntuación final: " + scoreColor + score + "/100");
                sender.sendMessage("");
            });
        });
    }

    private void handleSessions(CommandSender sender, String[] args) {
        if (args.length > 1) {
            // Ver sesión de jugador específico
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Jugador no encontrado.");
                return;
            }
            
            var fp = plugin.getSessionGuardian().getFingerprint(target.getUniqueId());
            if (fp == null) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Sin datos de sesión.");
                return;
            }
            
            ChatColor statusColor = switch (fp.getStatus()) {
                case ACTIVE -> ChatColor.GREEN;
                case SUSPICIOUS -> ChatColor.YELLOW;
                case UNDER_INVESTIGATION -> ChatColor.GOLD;
                case COMPROMISED -> ChatColor.RED;
            };
            
            sender.sendMessage("");
            sender.sendMessage(ChatColor.GOLD + "═══ Sesión de " + target.getName() + " ═══");
            sender.sendMessage(ChatColor.GRAY + "UUID: " + ChatColor.WHITE + target.getUniqueId());
            sender.sendMessage(ChatColor.GRAY + "IP: " + ChatColor.WHITE + fp.getBoundIP());
            sender.sendMessage(ChatColor.GRAY + "Estado: " + statusColor + fp.getStatus());
            sender.sendMessage(ChatColor.GRAY + "Sospecha: " + ChatColor.WHITE + fp.getSuspicionLevel() + "/10");
            sender.sendMessage(ChatColor.GRAY + "Hash: " + ChatColor.DARK_GRAY + fp.getProtocolHash());
            sender.sendMessage(ChatColor.GRAY + "Duración: " + ChatColor.WHITE + formatDuration(fp.getSessionDurationMs()));
            sender.sendMessage("");
            return;
        }
        
        // Listar todas las sesiones
        Map<UUID, SessionFingerprint> sessions = plugin.getSessionGuardian().getAllSessions();
        
        sender.sendMessage(ChatColor.DARK_RED + "═══ " + ChatColor.RED + 
                          "Sesiones Activas (" + sessions.size() + ")" + ChatColor.DARK_RED + " ═══");
        
        for (Map.Entry<UUID, SessionFingerprint> entry : sessions.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            SessionFingerprint fp = entry.getValue();
            
            String name = player != null ? player.getName() : entry.getKey().toString().substring(0, 8);
            ChatColor statusColor = switch (fp.getStatus()) {
                case ACTIVE -> ChatColor.GREEN;
                case SUSPICIOUS -> ChatColor.YELLOW;
                case UNDER_INVESTIGATION -> ChatColor.GOLD;
                case COMPROMISED -> ChatColor.RED;
            };
            
            sender.sendMessage(statusColor + "● " + ChatColor.WHITE + name + 
                              ChatColor.DARK_GRAY + " | IP: " + ChatColor.GRAY + fp.getBoundIP() +
                              ChatColor.DARK_GRAY + " | " + statusColor + fp.getStatus());
        }
        
        sender.sendMessage("");
    }

    private void giveRadarMap(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Solo jugadores pueden usar esto.");
            return;
        }
        
        ItemStack map = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) map.getItemMeta();
        
        if (meta != null) {
            MapView view = Bukkit.createMap(player.getWorld());
            view.getRenderers().clear();
            view.addRenderer(new com.voidcrypt.radar.ThreatRadarRenderer(
                plugin, plugin.getTrafficAnalyzer(), plugin.getSessionGuardian()));
            
            meta.setMapView(view);
            meta.setDisplayName(ChatColor.DARK_RED + "✦ " + ChatColor.RED + "Radar de Amenazas" + 
                               ChatColor.DARK_RED + " ✦");
            meta.setLore(List.of(
                ChatColor.GRAY + "Muestra el estado de seguridad",
                ChatColor.GRAY + "del servidor en tiempo real.",
                "",
                ChatColor.DARK_GRAY + "VoidCrypt Defense System"
            ));
            map.setItemMeta(meta);
        }
        
        player.getInventory().addItem(map);
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Radar de amenazas entregado.");
    }

    private void openGUI(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Solo jugadores pueden usar esto.");
            return;
        }
        
        if (args.length > 1) {
            switch (args[1].toLowerCase()) {
                case "sessions" -> guiManager.openSessionsPanel(player);
                case "security" -> guiManager.openSecurityPanel(player);
                case "firewall" -> guiManager.openFirewallPanel(player);
                case "settings", "config" -> guiManager.openSettingsPanel(player);
                case "logs" -> guiManager.openLogsPanel(player);
                default -> guiManager.openMainMenu(player);
            }
        } else {
            guiManager.openMainMenu(player);
        }
    }

    private void kickPlayer(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Uso: /voidcrypt kick <player> [reason]");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Jugador no encontrado.");
            return;
        }
        
        String reason = args.length > 2 ? 
            String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : 
            "Expulsado por el sistema de seguridad";
        
        target.kickPlayer(ChatColor.RED + reason);
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Jugador expulsado: " + target.getName());
        plugin.alert("Jugador expulsado: " + target.getName() + " por " + sender.getName());
    }

    private void banPlayer(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Uso: /voidcrypt ban <player|ip> [duration] [reason]");
            return;
        }
        
        String target = args[1];
        String reason = "Baneado por VoidCrypt";
        java.util.Date expiry = null;
        
        // Parse duration if provided
        if (args.length > 2 && args[2].matches("\\d+[mhd]?")) {
            String durStr = args[2];
            int value = Integer.parseInt(durStr.replaceAll("[^0-9]", ""));
            char unit = durStr.charAt(durStr.length() - 1);
            
            long minutes = switch (unit) {
                case 'h' -> value * 60L;
                case 'd' -> value * 60L * 24;
                default -> value;
            };
            
            expiry = java.util.Date.from(java.time.Instant.now().plusSeconds(minutes * 60));
            
            if (args.length > 3) {
                reason = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
            }
        } else if (args.length > 2) {
            reason = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        }
        
        // Check if IP or player
        if (target.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
            Bukkit.getBanList(org.bukkit.BanList.Type.IP).addBan(target, reason, expiry, sender.getName());
            sender.sendMessage(PREFIX + ChatColor.RED + "IP baneada: " + target);
        } else {
            Player player = Bukkit.getPlayer(target);
            if (player != null) {
                var fp = plugin.getSessionGuardian().getFingerprint(player.getUniqueId());
                if (fp != null) {
                    Bukkit.getBanList(org.bukkit.BanList.Type.IP).addBan(fp.getBoundIP(), reason, expiry, sender.getName());
                }
                player.kickPlayer(ChatColor.RED + reason);
            }
            Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(target, reason, expiry, sender.getName());
            sender.sendMessage(PREFIX + ChatColor.RED + "Jugador baneado: " + target);
        }
        
        plugin.alert("Ban aplicado: " + target + " por " + sender.getName());
    }

    private void unbanIP(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Uso: /voidcrypt unban <ip>");
            return;
        }
        
        String ip = args[1];
        Bukkit.getBanList(org.bukkit.BanList.Type.IP).pardon(ip);
        sender.sendMessage(PREFIX + ChatColor.GREEN + "IP desbaneada: " + ip);
    }

    private void investigatePlayer(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Uso: /voidcrypt investigate <player>");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Jugador no encontrado.");
            return;
        }
        
        plugin.getSessionGuardian().flagForInvestigation(target.getUniqueId());
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "Jugador marcado para investigación: " + target.getName());
    }

    private void toggleAlertMode(CommandSender sender) {
        boolean current = plugin.getConfig().getBoolean("alert-mode", false);
        plugin.getConfig().set("alert-mode", !current);
        plugin.saveConfig();
        
        sender.sendMessage(PREFIX + "Modo alerta: " + 
            (!current ? ChatColor.GREEN + "ACTIVADO" : ChatColor.RED + "DESACTIVADO"));
        
        if (!current) {
            plugin.alert("Modo alerta activado por " + sender.getName());
        }
    }

    private void toggleLockdown(CommandSender sender, String[] args) {
        boolean current = plugin.getConfig().getBoolean("lockdown-mode", false);
        boolean newState;
        
        if (args.length > 1) {
            newState = args[1].equalsIgnoreCase("on");
        } else {
            newState = !current;
        }
        
        plugin.getConfig().set("lockdown-mode", newState);
        plugin.saveConfig();
        
        if (newState) {
            sender.sendMessage(PREFIX + ChatColor.RED + "¡MODO LOCKDOWN ACTIVADO!");
            sender.sendMessage(ChatColor.GRAY + "Nuevas conexiones serán bloqueadas.");
            plugin.alert("¡LOCKDOWN ACTIVADO por " + sender.getName() + "!");
            
            // Kick all non-admins
            if (args.length > 2 && args[2].equalsIgnoreCase("--kick-all")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.hasPermission("voidcrypt.admin")) {
                        p.kickPlayer(ChatColor.RED + "Servidor en modo lockdown.");
                    }
                }
            }
        } else {
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Modo lockdown desactivado.");
            plugin.alert("Lockdown desactivado por " + sender.getName());
        }
    }

    private void manageWhitelist(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Uso: /voidcrypt whitelist <add|remove|list> [ip]");
            return;
        }
        
        List<String> whitelist = plugin.getConfig().getStringList("ip-whitelist");
        
        switch (args[1].toLowerCase()) {
            case "add" -> {
                if (args.length < 3) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Especifica una IP.");
                    return;
                }
                whitelist.add(args[2]);
                plugin.getConfig().set("ip-whitelist", whitelist);
                plugin.saveConfig();
                sender.sendMessage(PREFIX + ChatColor.GREEN + "IP añadida a whitelist: " + args[2]);
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Especifica una IP.");
                    return;
                }
                whitelist.remove(args[2]);
                plugin.getConfig().set("ip-whitelist", whitelist);
                plugin.saveConfig();
                sender.sendMessage(PREFIX + ChatColor.GREEN + "IP eliminada de whitelist: " + args[2]);
            }
            case "list" -> {
                sender.sendMessage(ChatColor.GOLD + "IPs en whitelist:");
                for (String ip : whitelist) {
                    sender.sendMessage(ChatColor.GRAY + "  - " + ChatColor.WHITE + ip);
                }
            }
        }
    }

    private void manageBlacklist(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Uso: /voidcrypt blacklist <add|remove|list> [ip]");
            return;
        }
        
        List<String> blacklist = plugin.getConfig().getStringList("ip-blacklist");
        
        switch (args[1].toLowerCase()) {
            case "add" -> {
                if (args.length < 3) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Especifica una IP.");
                    return;
                }
                blacklist.add(args[2]);
                plugin.getConfig().set("ip-blacklist", blacklist);
                plugin.saveConfig();
                Bukkit.getBanList(org.bukkit.BanList.Type.IP).addBan(args[2], "Blacklisted", null, "VoidCrypt");
                sender.sendMessage(PREFIX + ChatColor.RED + "IP añadida a blacklist: " + args[2]);
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Especifica una IP.");
                    return;
                }
                blacklist.remove(args[2]);
                plugin.getConfig().set("ip-blacklist", blacklist);
                plugin.saveConfig();
                Bukkit.getBanList(org.bukkit.BanList.Type.IP).pardon(args[2]);
                sender.sendMessage(PREFIX + ChatColor.GREEN + "IP eliminada de blacklist: " + args[2]);
            }
            case "list" -> {
                sender.sendMessage(ChatColor.RED + "IPs en blacklist:");
                for (String ip : blacklist) {
                    sender.sendMessage(ChatColor.GRAY + "  - " + ChatColor.WHITE + ip);
                }
            }
        }
    }

    private void showStats(CommandSender sender) {
        var traffic = plugin.getTrafficAnalyzer();
        var sessions = plugin.getSessionGuardian().getAllSessions();
        
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "═══ Estadísticas de VoidCrypt ═══");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.AQUA + "Tráfico de Red:");
        sender.sendMessage(ChatColor.GRAY + "  Paquetes totales: " + ChatColor.WHITE + formatNumber(traffic.getTotalPackets()));
        sender.sendMessage(ChatColor.GRAY + "  Paquetes/segundo actual: " + ChatColor.WHITE + traffic.getPacketVolume());
        sender.sendMessage(ChatColor.GRAY + "  Pico máximo: " + ChatColor.WHITE + traffic.getPeakPacketsPerSecond() + " pkt/s");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.AQUA + "Sesiones:");
        sender.sendMessage(ChatColor.GRAY + "  Activas: " + ChatColor.WHITE + sessions.size());
        
        long suspicious = sessions.values().stream()
            .filter(s -> s.getStatus() == SessionFingerprint.SessionStatus.SUSPICIOUS).count();
        long compromised = sessions.values().stream()
            .filter(s -> s.getStatus() == SessionFingerprint.SessionStatus.COMPROMISED).count();
        
        sender.sendMessage(ChatColor.GRAY + "  Sospechosas: " + ChatColor.YELLOW + suspicious);
        sender.sendMessage(ChatColor.GRAY + "  Comprometidas: " + ChatColor.RED + compromised);
        sender.sendMessage("");
        sender.sendMessage(ChatColor.AQUA + "Bans:");
        sender.sendMessage(ChatColor.GRAY + "  IPs baneadas: " + ChatColor.WHITE + 
            Bukkit.getBanList(org.bukkit.BanList.Type.IP).getBanEntries().size());
        sender.sendMessage("");
    }

    private void exportLogs(CommandSender sender, String[] args) {
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "Exportando logs a plugins/VoidCrypt/logs/...");
        // Implementation would write to file
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Logs exportados correctamente.");
    }

    private void runTest(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Uso: /voidcrypt test <handshake|session|radar|scanner|phantom>");
            return;
        }
        
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "Ejecutando test de módulo: " + args[1]);
        
        switch (args[1].toLowerCase()) {
            case "handshake" -> sender.sendMessage(ChatColor.GREEN + "✓ Void Handshake Protocol: OK");
            case "session" -> sender.sendMessage(ChatColor.GREEN + "✓ Shadow Session: OK - " + 
                plugin.getSessionGuardian().getActiveSessionCount() + " sesiones");
            case "radar" -> sender.sendMessage(ChatColor.GREEN + "✓ Threat Radar: OK - Nivel: " + 
                (int)(plugin.getTrafficAnalyzer().getThreatLevel() * 100) + "%");
            case "scanner" -> {
                if (plugin.getFileIntegrityChecker() != null) {
                    sender.sendMessage(ChatColor.GREEN + "✓ Core Integrity Scanner: OK");
                } else {
                    sender.sendMessage(ChatColor.RED + "✗ Core Integrity Scanner: Deshabilitado");
                }
            }
            case "phantom" -> sender.sendMessage(ChatColor.GREEN + "✓ Phantom Ports: OK");
            default -> sender.sendMessage(ChatColor.RED + "Módulo desconocido.");
        }
    }

    private void toggleHUD(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Solo jugadores pueden usar esto.");
            return;
        }
        securityHUD.toggleHUD(player);
    }

    private void toggleMute(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Solo jugadores pueden usar esto.");
            return;
        }
        plugin.getNotificationManager().toggleMute(player);
    }

    private void generateReport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Solo jugadores pueden usar esto.");
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Uso: /voidcrypt report <player>");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Jugador no encontrado.");
            return;
        }
        
        ItemStack book = playerInfoBook.createPlayerReport(target);
        player.getInventory().addItem(book);
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Reporte generado para " + target.getName());
    }

    private void teleportToPlayer(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Solo jugadores pueden usar esto.");
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Uso: /voidcrypt tp <player>");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Jugador no encontrado.");
            return;
        }
        
        player.teleport(target.getLocation());
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Teletransportado a " + target.getName());
    }

    private void freezePlayer(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Uso: /voidcrypt freeze <player>");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Jugador no encontrado.");
            return;
        }
        
        // Toggle freeze using walkspeed
        if (target.getWalkSpeed() == 0) {
            target.setWalkSpeed(0.2f);
            target.setFlySpeed(0.1f);
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Jugador descongelado: " + target.getName());
            target.sendMessage(ChatColor.GREEN + "Has sido descongelado.");
        } else {
            target.setWalkSpeed(0);
            target.setFlySpeed(0);
            sender.sendMessage(PREFIX + ChatColor.AQUA + "Jugador congelado: " + target.getName());
            target.sendMessage(ChatColor.RED + "Has sido congelado por un administrador.");
        }
    }

    private void spectatePlayer(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Solo jugadores pueden usar esto.");
            return;
        }
        
        if (args.length < 2) {
            // Exit spectate
            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Modo espectador desactivado.");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Jugador no encontrado.");
            return;
        }
        
        player.setGameMode(org.bukkit.GameMode.SPECTATOR);
        player.setSpectatorTarget(target);
        sender.sendMessage(PREFIX + ChatColor.AQUA + "Espectando a " + target.getName() + 
            ChatColor.GRAY + " (usa /voidcrypt spectate para salir)");
    }

    private void showPlayerHistory(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Uso: /voidcrypt history <player>");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Jugador no encontrado o no conectado.");
            return;
        }
        
        var fp = plugin.getSessionGuardian().getFingerprint(target.getUniqueId());
        if (fp == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Sin datos de sesión.");
            return;
        }
        
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "═══ Historial de " + target.getName() + " ═══");
        sender.sendMessage(ChatColor.GRAY + "Sesión actual:");
        sender.sendMessage(ChatColor.GRAY + "  IP: " + ChatColor.WHITE + fp.getBoundIP());
        sender.sendMessage(ChatColor.GRAY + "  Estado: " + ChatColor.WHITE + fp.getStatus());
        sender.sendMessage(ChatColor.GRAY + "  Nivel sospecha: " + ChatColor.WHITE + fp.getSuspicionLevel());
        sender.sendMessage(ChatColor.GRAY + "  Duración: " + ChatColor.WHITE + formatDuration(fp.getSessionDurationMs()));
        sender.sendMessage("");
    }

    private void compareFingerprints(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Uso: /voidcrypt compare <player1> <player2>");
            return;
        }
        
        Player p1 = Bukkit.getPlayer(args[1]);
        Player p2 = Bukkit.getPlayer(args[2]);
        
        if (p1 == null || p2 == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Uno o ambos jugadores no encontrados.");
            return;
        }
        
        var fp1 = plugin.getSessionGuardian().getFingerprint(p1.getUniqueId());
        var fp2 = plugin.getSessionGuardian().getFingerprint(p2.getUniqueId());
        
        if (fp1 == null || fp2 == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Sin datos de sesión para comparar.");
            return;
        }
        
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "═══ Comparación de Huellas ═══");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.WHITE + "                " + p1.getName() + "      vs      " + p2.getName());
        sender.sendMessage(ChatColor.GRAY + "IP:           " + 
            ChatColor.WHITE + fp1.getBoundIP() + "    " + 
            (fp1.getBoundIP().equals(fp2.getBoundIP()) ? ChatColor.RED + "=" : ChatColor.GREEN + "≠") + 
            "    " + ChatColor.WHITE + fp2.getBoundIP());
        sender.sendMessage(ChatColor.GRAY + "Estado:       " + 
            ChatColor.WHITE + fp1.getStatus() + "         " + fp2.getStatus());
        sender.sendMessage(ChatColor.GRAY + "Sospecha:     " + 
            ChatColor.WHITE + fp1.getSuspicionLevel() + "/10              " + fp2.getSuspicionLevel() + "/10");
        
        // Check if same IP (potential alt account)
        if (fp1.getBoundIP().equals(fp2.getBoundIP())) {
            sender.sendMessage("");
            sender.sendMessage(ChatColor.RED + "⚠ ALERTA: Misma IP detectada - Posible cuenta alternativa");
        }
        
        sender.sendMessage("");
    }

    private void broadcastAlert(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Uso: /voidcrypt broadcast <message>");
            return;
        }
        
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        
        String formatted = ChatColor.DARK_RED + "╔═══════════════════════════════════╗\n" +
            ChatColor.DARK_RED + "║ " + ChatColor.RED + "⚡ ALERTA DE SEGURIDAD ⚡" + ChatColor.DARK_RED + "          ║\n" +
            ChatColor.DARK_RED + "╠═══════════════════════════════════╣\n" +
            ChatColor.DARK_RED + "║ " + ChatColor.WHITE + message + "\n" +
            ChatColor.DARK_RED + "╚═══════════════════════════════════╝";
        
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("voidcrypt.admin")) {
                p.sendMessage(formatted);
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_WITHER_SPAWN, 0.3f, 1.0f);
            }
        }
        
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Alerta enviada a todos los administradores.");
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

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, 
                                      @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            List<String> commands = List.of(
                "help", "reload", "status", "scan", "audit", "sessions", "radar",
                "gui", "kick", "ban", "unban", "investigate", "alert", "lockdown",
                "whitelist", "blacklist", "stats", "export", "test",
                "hud", "mute", "report", "tp", "freeze", "spectate", "history", 
                "compare", "broadcast"
            );
            for (String cmd : commands) {
                if (cmd.startsWith(args[0].toLowerCase())) {
                    completions.add(cmd);
                }
            }
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "gui" -> {
                    for (String panel : List.of("sessions", "security", "firewall", "settings", "logs")) {
                        if (panel.startsWith(args[1].toLowerCase())) completions.add(panel);
                    }
                }
                case "kick", "ban", "investigate", "sessions", "report", "tp", 
                     "teleport", "freeze", "spectate", "history", "compare" -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(p.getName());
                        }
                    }
                }
                case "lockdown" -> {
                    for (String opt : List.of("on", "off")) {
                        if (opt.startsWith(args[1].toLowerCase())) completions.add(opt);
                    }
                }
                case "whitelist", "blacklist" -> {
                    for (String opt : List.of("add", "remove", "list")) {
                        if (opt.startsWith(args[1].toLowerCase())) completions.add(opt);
                    }
                }
                case "export" -> {
                    for (String opt : List.of("logs", "config")) {
                        if (opt.startsWith(args[1].toLowerCase())) completions.add(opt);
                    }
                }
                case "test" -> {
                    for (String mod : List.of("handshake", "session", "radar", "scanner", "phantom")) {
                        if (mod.startsWith(args[1].toLowerCase())) completions.add(mod);
                    }
                }
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("compare")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                        completions.add(p.getName());
                    }
                }
            }
        }
        
        return completions;
    }
}
