package com.voidcrypt.phantom;

import com.voidcrypt.VoidCryptPlugin;
import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Módulo 5B: Ejecutor de Firewall
 * Interactúa con el sistema operativo para bloquear IPs maliciosas
 */
public class FirewallExecutor {

    private final VoidCryptPlugin plugin;
    private final OperatingSystem os;
    private final Set<String> bannedIPs;
    
    // Regex para sanitización de IP - Solo permite dígitos y puntos
    private static final Pattern IP_SANITIZE = Pattern.compile("[^0-9.]");
    private static final Pattern IP_VALIDATE = Pattern.compile(
        "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.){3}(25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)$"
    );

    public FirewallExecutor(VoidCryptPlugin plugin) {
        this.plugin = plugin;
        this.os = detectOS();
        this.bannedIPs = ConcurrentHashMap.newKeySet();
        
        plugin.getLogger().info("Firewall Executor inicializado para: " + os);
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
     * Ejecuta un bloqueo de IP a nivel de firewall del SO
     * ADVERTENCIA: Requiere permisos elevados
     */
    public boolean executeBan(String ip) {
        // Sanitizar IP - eliminar todo excepto dígitos y puntos
        String sanitizedIP = IP_SANITIZE.matcher(ip).replaceAll("");
        
        // Validar formato de IP
        if (!IP_VALIDATE.matcher(sanitizedIP).matches()) {
            plugin.getLogger().warning("IP inválida rechazada: " + ip);
            return false;
        }
        
        // Evitar duplicados
        if (bannedIPs.contains(sanitizedIP)) {
            return true;
        }
        
        // Verificar si el auto-firewall está habilitado
        if (!plugin.getConfig().getBoolean("phantom-ports.auto-firewall", false)) {
            plugin.getLogger().info("Auto-firewall deshabilitado. IP marcada: " + sanitizedIP);
            bannedIPs.add(sanitizedIP);
            return false;
        }
        
        String command = buildCommand(sanitizedIP);
        if (command == null) {
            plugin.getLogger().warning("Sistema operativo no soportado para firewall");
            return false;
        }
        
        // Ejecutar comando de forma asíncrona
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                executeCommand(command, sanitizedIP);
            } catch (Exception e) {
                plugin.getLogger().severe("Error ejecutando comando de firewall: " + e.getMessage());
            }
        });
        
        bannedIPs.add(sanitizedIP);
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
            
            // Leer salida
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
                plugin.getLogger().info("✓ IP bloqueada en firewall: " + ip);
                plugin.alert("IP bloqueada a nivel de SO: " + ip);
            } else {
                plugin.getLogger().warning("Error al bloquear IP (código " + exitCode + "): " + output);
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("Excepción ejecutando firewall: " + e.getMessage());
        }
    }

    /**
     * Desbloquea una IP del firewall
     */
    public boolean executeUnban(String ip) {
        String sanitizedIP = IP_SANITIZE.matcher(ip).replaceAll("");
        
        if (!IP_VALIDATE.matcher(sanitizedIP).matches()) {
            return false;
        }
        
        String command = buildUnbanCommand(sanitizedIP);
        if (command == null) return false;
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                executeCommand(command, sanitizedIP);
                bannedIPs.remove(sanitizedIP);
            } catch (Exception e) {
                plugin.getLogger().severe("Error desbloqueando IP: " + e.getMessage());
            }
        });
        
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
