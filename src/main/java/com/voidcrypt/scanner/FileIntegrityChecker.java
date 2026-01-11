package com.voidcrypt.scanner;

import com.voidcrypt.VoidCryptPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Módulo 4A: Verificador de Integridad de Archivos
 * Escanea plugins en busca de modificaciones no autorizadas
 */
public class FileIntegrityChecker {

    private final VoidCryptPlugin plugin;
    private final Map<String, FileChecksum> knownChecksums;
    private final List<ScanResult> lastScanResults;

    public FileIntegrityChecker(VoidCryptPlugin plugin) {
        this.plugin = plugin;
        this.knownChecksums = new HashMap<>();
        this.lastScanResults = new ArrayList<>();
        
        loadKnownChecksums();
    }

    private void loadKnownChecksums() {
        List<String> checksumList = plugin.getConfig().getStringList("integrity-scanner.known-good-checksums");
        
        for (String entry : checksumList) {
            String[] parts = entry.split(":");
            if (parts.length == 2) {
                knownChecksums.put(parts[0], new FileChecksum(parts[0], parts[1], -1));
            }
        }
        
        plugin.getLogger().info("Cargados " + knownChecksums.size() + " checksums conocidos");
    }

    /**
     * Escanea la carpeta de plugins
     */
    public List<ScanResult> scanPlugins() {
        lastScanResults.clear();
        File pluginsFolder = plugin.getDataFolder().getParentFile();
        
        if (!pluginsFolder.exists() || !pluginsFolder.isDirectory()) {
            plugin.getLogger().warning("Carpeta de plugins no encontrada");
            return lastScanResults;
        }
        
        File[] jarFiles = pluginsFolder.listFiles((dir, name) -> name.endsWith(".jar"));
        
        if (jarFiles == null) {
            return lastScanResults;
        }
        
        plugin.getLogger().info("Escaneando " + jarFiles.length + " archivos JAR...");
        
        for (File jar : jarFiles) {
            ScanResult result = analyzeFile(jar);
            lastScanResults.add(result);
            
            if (result.status() == FileStatus.MODIFIED || result.status() == FileStatus.UNKNOWN) {
                plugin.alert("Archivo sospechoso detectado: " + jar.getName() + 
                            " [" + result.status() + "]");
            }
        }
        
        // Resumen
        long safe = lastScanResults.stream().filter(r -> r.status() == FileStatus.VERIFIED).count();
        long modified = lastScanResults.stream().filter(r -> r.status() == FileStatus.MODIFIED).count();
        long unknown = lastScanResults.stream().filter(r -> r.status() == FileStatus.UNKNOWN).count();
        
        plugin.getLogger().info(String.format(
            "Escaneo completado: %d verificados, %d modificados, %d desconocidos",
            safe, modified, unknown));
        
        return lastScanResults;
    }

    private ScanResult analyzeFile(File file) {
        try {
            String checksum = calculateSHA256(file);
            long size = file.length();
            String fileName = file.getName();
            
            FileChecksum known = knownChecksums.get(fileName);
            
            if (known == null) {
                return new ScanResult(fileName, checksum, size, FileStatus.UNKNOWN,
                    "Archivo no está en la lista blanca");
            }
            
            if (known.sha256().equalsIgnoreCase(checksum)) {
                return new ScanResult(fileName, checksum, size, FileStatus.VERIFIED,
                    "Checksum coincide");
            } else {
                return new ScanResult(fileName, checksum, size, FileStatus.MODIFIED,
                    "Checksum NO coincide! Esperado: " + known.sha256().substring(0, 16) + "...");
            }
            
        } catch (IOException | NoSuchAlgorithmException e) {
            return new ScanResult(file.getName(), "ERROR", 0, FileStatus.ERROR,
                "Error al calcular checksum: " + e.getMessage());
        }
    }

    /**
     * Calcula SHA-256 de un archivo
     */
    public String calculateSHA256(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        
        byte[] hash = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Agrega un archivo a la lista blanca
     */
    public void addToWhitelist(String fileName, String checksum) {
        knownChecksums.put(fileName, new FileChecksum(fileName, checksum, -1));
        
        List<String> list = plugin.getConfig().getStringList("integrity-scanner.known-good-checksums");
        list.add(fileName + ":" + checksum);
        plugin.getConfig().set("integrity-scanner.known-good-checksums", list);
        plugin.saveConfig();
    }

    public List<ScanResult> getLastScanResults() {
        return Collections.unmodifiableList(lastScanResults);
    }

    // Records para datos
    public record FileChecksum(String fileName, String sha256, long size) {}
    
    public record ScanResult(String fileName, String checksum, long size, 
                             FileStatus status, String message) {}
    
    public enum FileStatus {
        VERIFIED,   // Checksum coincide
        MODIFIED,   // Checksum diferente
        UNKNOWN,    // No está en lista blanca
        ERROR       // Error al procesar
    }
}
