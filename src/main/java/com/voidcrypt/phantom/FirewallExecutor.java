package com.voidcrypt.phantom;

import com.voidcrypt.VoidCryptPlugin;
import com.voidcrypt.security.SecurityValidator;
import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Module 5B: Firewall Executor
 * Interacts with OS to block malicious IPs
 */
public class FirewallExecutor {

    private final VoidCryptPlugin plugin;
    private final OperatingSystem os;
    private final Set<String> bannedIPs;

    public FirewallExecutor(VoidCryptPlugin plugin) {
        this.plugin = plugin;
        this.os = detectOS();
        this.bannedIPs = ConcurrentHashMap.newKeySet();
        
        plugin.getLogger().info("Firewall Executor initialized for: " + os);
    }

    private OperatingSystem detectOS() {
        String osName = System.getProperty("os.name").toLowerCase();
        
        if (osName.contains("linux")) {
            return OperatingSystem.LINUX;
        } else if (osName.contains("windows")) {
            return OperatingSystem.WINDOWS;
        } else if (osName.contains("mac")) {
            return OperatingSystem.MACOS;
        }
        return OperatingSystem.UNKNOWN;
    }

    /**
     * Executes IP ban at OS firewall level
     * WARNING: Requires elevated permissions
     */
    public boolean executeBan(String ip) {
        String validatedIP = SecurityValidator.validateIP(ip);
        
        if (validatedIP == null) {
            plugin.getLogger().warning("Invalid IP rejected for firewall ban: " + 
                SecurityValidator.sanitizeForLog(ip));
            return false;
        }
        
        if (!SecurityValidator.checkRateLimit("firewall:" + validatedIP)) {
            plugin.getLogger().warning("Rate limit exceeded for firewall operations on: " + validatedIP);
            return false;
        }
        
        // Avoid duplicates
        if (bannedIPs.contains(validatedIP)) {
            return true;
        }
        
        // Check if auto-firewall is enabled
        if (!plugin.getConfig().getBoolean("phantom-ports.auto-firewall", false)) {
            plugin.getLogger().info("Auto-firewall disabled. IP marked: " + validatedIP);
            bannedIPs.add(validatedIP);
            return false;
        }
        
        String escapedIP = SecurityValidator.escapeForShell(validatedIP);
        String command = buildCommand(escapedIP);
        if (command == null) {
            plugin.getLogger().warning("Unsupported operating system for firewall");
            return false;
        }
        
        // Execute command asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                executeCommand(command, validatedIP);
            } catch (Exception e) {
                plugin.getLogger().severe("Error executing firewall command: " + e.getMessage());
            }
        });
        
        bannedIPs.add(validatedIP);
        plugin.auditLog(Level.WARNING, "FIREWALL_BAN", "IP: " + validatedIP);
        return true;
    }

    private String buildCommand(String ip) {
        return switch (os) {
            case LINUX -> "iptables -A INPUT -s " + ip + " -j DROP";
            case WINDOWS -> "netsh advfirewall firewall add rule name=\"VoidCrypt_" + 
                           ip.replace(".", "_") + "\" dir=in action=block remoteip=" + ip;
            case MACOS -> "pfctl -t voidcrypt -T add " + ip;
            default -> null;
        };
    }

    private void executeCommand(String command, String ip) {
        try {
            ProcessBuilder pb;
            
            if (os == OperatingSystem.WINDOWS) {
                pb = new ProcessBuilder("cmd.exe", "/c", command);
            } else {
                pb = new ProcessBuilder("sh", "-c", command);
            }
            
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                plugin.getLogger().info("[OK] IP blocked in firewall: " + ip);
                plugin.alert("IP blocked at OS level: " + ip);
            } else {
                plugin.getLogger().warning("Error blocking IP (code " + exitCode + "): " + output);
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("Exception executing firewall: " + e.getMessage());
        }
    }

    /**
     * Unblocks an IP from firewall
     */
    public boolean executeUnban(String ip) {
        String validatedIP = SecurityValidator.validateIP(ip);
        
        if (validatedIP == null) {
            return false;
        }
        
        String escapedIP = SecurityValidator.escapeForShell(validatedIP);
        String command = buildUnbanCommand(escapedIP);
        if (command == null) return false;
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                executeCommand(command, validatedIP);
                bannedIPs.remove(validatedIP);
            } catch (Exception e) {
                plugin.getLogger().severe("Error unbanning IP: " + e.getMessage());
            }
        });
        
        plugin.auditLog(Level.INFO, "FIREWALL_UNBAN", "IP: " + validatedIP);
        return true;
    }

    private String buildUnbanCommand(String ip) {
        return switch (os) {
            case LINUX -> "iptables -D INPUT -s " + ip + " -j DROP";
            case WINDOWS -> "netsh advfirewall firewall delete rule name=\"VoidCrypt_" + 
                           ip.replace(".", "_") + "\"";
            case MACOS -> "pfctl -t voidcrypt -T delete " + ip;
            default -> null;
        };
    }

    public Set<String> getBannedIPs() {
        return Set.copyOf(bannedIPs);
    }

    public OperatingSystem getOperatingSystem() {
        return os;
    }

    public enum OperatingSystem {
        LINUX,
        WINDOWS,
        MACOS,
        UNKNOWN
    }
}
