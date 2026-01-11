package com.voidcrypt.radar;

import com.voidcrypt.VoidCryptPlugin;
import com.voidcrypt.shadow.SessionFingerprint;
import com.voidcrypt.shadow.SessionGuardian;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Map;
import java.util.UUID;

/**
 * Módulo 3A: Renderizador del Radar de Amenazas
 * Visualiza el estado de seguridad del servidor en un mapa
 */
public class ThreatRadarRenderer extends MapRenderer {

    private final VoidCryptPlugin plugin;
    private final TrafficAnalyzer trafficAnalyzer;
    private final SessionGuardian sessionGuardian;
    
    private long lastRender = 0;
    private int frameCounter = 0;
    
    // Colores del radar
    private static final byte COLOR_BLACK = MapPalette.matchColor(Color.BLACK);
    private static final byte COLOR_DARK_GREEN = MapPalette.matchColor(new Color(0, 40, 0));
    private static final byte COLOR_GREEN = MapPalette.matchColor(new Color(0, 255, 0));
    private static final byte COLOR_YELLOW = MapPalette.matchColor(Color.YELLOW);
    private static final byte COLOR_RED = MapPalette.matchColor(Color.RED);
    private static final byte COLOR_WHITE = MapPalette.matchColor(Color.WHITE);
    private static final byte COLOR_CYAN = MapPalette.matchColor(Color.CYAN);

    public ThreatRadarRenderer(VoidCryptPlugin plugin, TrafficAnalyzer trafficAnalyzer, 
                                SessionGuardian sessionGuardian) {
        super(true); // Contextual rendering
        this.plugin = plugin;
        this.trafficAnalyzer = trafficAnalyzer;
        this.sessionGuardian = sessionGuardian;
    }

    @Override
    public void render(@NotNull MapView view, @NotNull MapCanvas canvas, @NotNull Player player) {
        // Limitar tasa de actualización
        int refreshRate = plugin.getConfig().getInt("threat-radar.refresh-rate-ticks", 2);
        long now = System.currentTimeMillis();
        if (now - lastRender < (refreshRate * 50L)) {
            return;
        }
        lastRender = now;
        frameCounter++;
        
        // Limpiar canvas con fondo negro
        for (int x = 0; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                canvas.setPixel(x, y, COLOR_BLACK);
            }
        }
        
        // Dibujar grid estilo Matrix
        drawGrid(canvas);
        
        // Dibujar círculos de radar
        drawRadarCircles(canvas);
        
        // Dibujar línea de escaneo rotativa
        drawScanLine(canvas);
        
        // Dibujar núcleo central (servidor)
        drawCore(canvas);
        
        // Dibujar jugadores como puntos
        drawPlayers(canvas);
        
        // Dibujar indicadores de estado
        drawStatusIndicators(canvas);
        
        // Dibujar estadísticas
        drawStats(canvas);
    }

    private void drawGrid(MapCanvas canvas) {
        // Grid vertical y horizontal cada 16 píxeles
        for (int i = 0; i < 128; i += 16) {
            for (int j = 0; j < 128; j++) {
                canvas.setPixel(i, j, COLOR_DARK_GREEN);
                canvas.setPixel(j, i, COLOR_DARK_GREEN);
            }
        }
    }

    private void drawRadarCircles(MapCanvas canvas) {
        int centerX = 64, centerY = 64;
        int[] radii = {20, 40, 60};
        
        for (int radius : radii) {
            drawCircle(canvas, centerX, centerY, radius, COLOR_DARK_GREEN);
        }
    }

    private void drawCircle(MapCanvas canvas, int cx, int cy, int radius, byte color) {
        for (int angle = 0; angle < 360; angle += 2) {
            double rad = Math.toRadians(angle);
            int x = (int) (cx + radius * Math.cos(rad));
            int y = (int) (cy + radius * Math.sin(rad));
            if (x >= 0 && x < 128 && y >= 0 && y < 128) {
                canvas.setPixel(x, y, color);
            }
        }
    }

    private void drawScanLine(MapCanvas canvas) {
        int centerX = 64, centerY = 64;
        double angle = Math.toRadians((frameCounter * 6) % 360); // Rotación
        
        for (int r = 0; r < 60; r++) {
            int x = (int) (centerX + r * Math.cos(angle));
            int y = (int) (centerY + r * Math.sin(angle));
            if (x >= 0 && x < 128 && y >= 0 && y < 128) {
                canvas.setPixel(x, y, COLOR_GREEN);
            }
        }
    }

    private void drawCore(MapCanvas canvas) {
        // Núcleo central pulsante
        int centerX = 64, centerY = 64;
        byte coreColor = (frameCounter % 10 < 5) ? COLOR_CYAN : COLOR_WHITE;
        
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (Math.abs(dx) + Math.abs(dy) <= 3) {
                    canvas.setPixel(centerX + dx, centerY + dy, coreColor);
                }
            }
        }
    }

    private void drawPlayers(MapCanvas canvas) {
        Map<UUID, SessionFingerprint> sessions = sessionGuardian.getAllSessions();
        int playerIndex = 0;
        int totalPlayers = sessions.size();
        
        for (Map.Entry<UUID, SessionFingerprint> entry : sessions.entrySet()) {
            SessionFingerprint fp = entry.getValue();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;
            
            // Distribuir jugadores en círculo alrededor del centro
            double angle = (2 * Math.PI * playerIndex) / Math.max(1, totalPlayers);
            int distance = 30 + (fp.getSuspicionLevel() * 2); // Más sospecha = más lejos
            
            int x = (int) (64 + distance * Math.cos(angle));
            int y = (int) (64 + distance * Math.sin(angle));
            
            // Color según estado
            byte color = switch (fp.getStatus()) {
                case ACTIVE -> COLOR_GREEN;
                case SUSPICIOUS -> COLOR_YELLOW;
                case UNDER_INVESTIGATION -> (frameCounter % 4 < 2) ? COLOR_RED : COLOR_YELLOW;
                case COMPROMISED -> COLOR_RED;
            };
            
            // Dibujar punto del jugador
            if (x >= 1 && x < 127 && y >= 1 && y < 127) {
                canvas.setPixel(x, y, color);
                canvas.setPixel(x + 1, y, color);
                canvas.setPixel(x, y + 1, color);
                canvas.setPixel(x + 1, y + 1, color);
            }
            
            playerIndex++;
        }
    }

    private void drawStatusIndicators(MapCanvas canvas) {
        TrafficAnalyzer.NetworkStatus status = trafficAnalyzer.getNetworkStatus();
        
        // Indicador en esquina superior izquierda
        byte statusColor = switch (status) {
            case NORMAL -> COLOR_GREEN;
            case ELEVATED -> COLOR_YELLOW;
            case WARNING -> MapPalette.matchColor(Color.ORANGE);
            case CRITICAL -> (frameCounter % 4 < 2) ? COLOR_RED : COLOR_BLACK;
        };
        
        for (int x = 2; x < 10; x++) {
            for (int y = 2; y < 6; y++) {
                canvas.setPixel(x, y, statusColor);
            }
        }
    }

    private void drawStats(MapCanvas canvas) {
        // Dibujar TPS y paquetes en la parte inferior
        double tps = trafficAnalyzer.getSystemLoad();
        int packets = trafficAnalyzer.getPacketVolume();
        int players = sessionGuardian.getActiveSessionCount();
        
        // Mini barra de TPS (parte inferior)
        int tpsWidth = (int) ((tps / 20.0) * 50);
        byte tpsColor = tps >= 18 ? COLOR_GREEN : (tps >= 15 ? COLOR_YELLOW : COLOR_RED);
        
        for (int x = 2; x < 2 + tpsWidth; x++) {
            canvas.setPixel(x, 122, tpsColor);
            canvas.setPixel(x, 123, tpsColor);
        }
        
        // Indicador de jugadores (esquina inferior derecha)
        byte playerColor = players > 50 ? COLOR_YELLOW : COLOR_GREEN;
        for (int i = 0; i < Math.min(players, 10); i++) {
            canvas.setPixel(120 - (i * 3), 122, playerColor);
            canvas.setPixel(120 - (i * 3), 123, playerColor);
        }
    }
}
