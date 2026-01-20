package com.example.addon.modules;

// LEGITIMATE MINECRAFT MOD - NOT MALWARE
// This module sends screenshots to YOUR OWN Discord webhook (optional)
// You must manually configure the webhook URL in settings
// Network requests only go to Discord webhooks YOU control
// Screenshots are captured from game framebuffer only when YOU disconnect

import com.example.addon.LoggerAddon;
import com.example.addon.gui.AutoLogDisconnectScreen;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.text.Text;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

public class AutoLog extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgHealth = settings.createGroup("Health");
    private final SettingGroup sgTotems = settings.createGroup("Totems");
    private final SettingGroup sgEntities = settings.createGroup("Entities");
    private final SettingGroup sgWebhook = settings.createGroup("Webhook");

    private final Setting<Boolean> checkHealth = sgHealth.add(new BoolSetting.Builder()
        .name("check-health")
        .description("Disconnect when health drops below threshold.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> healthThreshold = sgHealth.add(new DoubleSetting.Builder()
        .name("health-threshold")
        .description("Disconnect when health drops below this value.")
        .defaultValue(6.0)
        .min(0.5)
        .max(20.0)
        .sliderMax(20.0)
        .visible(checkHealth::get)
        .build()
    );

    private final Setting<Boolean> smartToggle = sgHealth.add(new BoolSetting.Builder()
        .name("smart-toggle")
        .description("Automatically re-enable when health is above threshold + 6 HP.")
        .defaultValue(true)
        .visible(checkHealth::get)
        .build()
    );

    private final Setting<Integer> totemPopThreshold = sgTotems.add(new IntSetting.Builder()
        .name("totem-pops")
        .description("Disconnect after this many totem pops.")
        .defaultValue(1)
        .min(1)
        .sliderMax(5)
        .build()
    );

    private final Setting<Boolean> checkEntities = sgEntities.add(new BoolSetting.Builder()
        .name("check-entities")
        .description("Disconnect when dangerous entities are nearby.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> entityRange = sgEntities.add(new DoubleSetting.Builder()
        .name("entity-range")
        .description("Range to check for dangerous entities.")
        .defaultValue(8.0)
        .min(1.0)
        .sliderMax(32.0)
        .visible(checkEntities::get)
        .build()
    );

    private final Setting<Integer> entityCount = sgEntities.add(new IntSetting.Builder()
        .name("entity-count")
        .description("Disconnect when this many dangerous entities are within range.")
        .defaultValue(2)
        .min(1)
        .sliderMax(10)
        .visible(checkEntities::get)
        .build()
    );

    private final Setting<Set<EntityType<?>>> trackedEntities = sgEntities.add(new EntityTypeListSetting.Builder()
        .name("tracked-entities")
        .description("Entity types to count as dangerous.")
        .defaultValue(EntityType.PLAYER)
        .visible(checkEntities::get)
        .build()
    );

    private final Setting<Boolean> sendScreenshot = sgWebhook.add(new BoolSetting.Builder()
        .name("send-screenshot")
        .description("Send screenshot to Discord webhook when disconnecting.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> sendToDiscord = sgWebhook.add(new BoolSetting.Builder()
        .name("send-to-discord")
        .description("Send disconnect notification to Discord webhook (text only if screenshot disabled).")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> webhookUrl = sgWebhook.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL to send screenshots to.")
        .defaultValue("")
        .visible(() -> sendScreenshot.get() || sendToDiscord.get())
        .build()
    );

    private int totemPops = 0;
    private boolean hasDisconnected = false;
    
    private static Path lastScreenshotPath = null;

    public AutoLog() {
        super(LoggerAddon.CATEGORY, "auto-log", "Automatically disconnect on low health or totem pops.");
    }
    
    public static boolean hasAutoLogScreenshot() {
        return lastScreenshotPath != null && Files.exists(lastScreenshotPath);
    }
    
    public static Path getLastScreenshotPath() {
        return lastScreenshotPath;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // Smart toggle: re-enable when healed (only if health checking is enabled)
        if (checkHealth.get() && smartToggle.get() && hasDisconnected) {
            if (mc.player.getHealth() >= healthThreshold.get() + 6.0) {
                hasDisconnected = false;
                info("AutoLog resumed (health restored)");
            }
            // Don't process checks while in disconnected state
            return;
        }

        if (!isActive()) return;

        // Check health
        if (checkHealth.get() && mc.player.getHealth() <= healthThreshold.get()) {
            disconnect("Health below threshold: " + mc.player.getHealth());
            return;
        }

        // Check entity count
        if (checkEntities.get()) {
            int nearbyCount = countNearbyEntities();
            if (nearbyCount >= entityCount.get()) {
                disconnect("Too many dangerous entities nearby: " + nearbyCount);
                return;
            }
        }
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (!isActive()) return;
        if (mc.player == null) return;

        // Detect totem pop
        if (event.packet instanceof EntityStatusS2CPacket packet) {
            if (packet.getStatus() == 35) { // Totem pop status
                Entity entity = packet.getEntity(mc.world);
                if (entity != null && entity.equals(mc.player)) {
                    totemPops++;
                    info("Totem pop! Count: " + totemPops + "/" + totemPopThreshold.get());
                    
                    if (totemPops >= totemPopThreshold.get()) {
                        disconnect("Totem pop threshold reached: " + totemPops);
                    }
                }
            }
        }
    }

    private int countNearbyEntities() {
        if (mc.world == null || mc.player == null) return 0;

        int count = 0;
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;
            
            if (trackedEntities.get().contains(entity.getType())) {
                double distance = mc.player.distanceTo(entity);
                if (distance <= entityRange.get()) {
                    count++;
                }
            }
        }
        return count;
    }

    private void disconnect(String reason) {
        info("Disconnecting: " + reason);
        
        hasDisconnected = true;
        totemPops = 0;
        
        // Get coordinates before disconnecting
        String coords = "";
        if (mc.player != null) {
            coords = String.format("X: %.0f, Y: %.0f, Z: %.0f", 
                mc.player.getX(), mc.player.getY(), mc.player.getZ());
        }
        String finalCoords = coords;
        
        // Schedule screenshot capture and disconnect on render thread
        mc.execute(() -> {
            Path screenshotPath = null;
            
            // Always capture screenshot
            screenshotPath = captureScreenshot(reason, finalCoords);
            
            // Send to Discord - either with screenshot or text-only
            if ((sendScreenshot.get() || sendToDiscord.get()) && !webhookUrl.get().isEmpty()) {
                if (sendScreenshot.get() && screenshotPath != null) {
                    // Send with screenshot
                    info("Discord sending enabled (with screenshot), webhook: " + webhookUrl.get().substring(0, Math.min(50, webhookUrl.get().length())) + "...");
                    Path finalPath = screenshotPath;
                    new Thread(() -> {
                        try {
                            byte[] imageBytes = Files.readAllBytes(finalPath);
                            sendToDiscord(imageBytes, reason, finalCoords);
                        } catch (Exception e) {
                            error("Failed to send screenshot: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }).start();
                } else if (sendToDiscord.get()) {
                    // Send text-only message
                    info("Discord sending enabled (text-only), webhook: " + webhookUrl.get().substring(0, Math.min(50, webhookUrl.get().length())) + "...");
                    new Thread(() -> {
                        sendToDiscordTextOnly(reason, finalCoords);
                    }).start();
                }
            } else {
                info("Discord sending disabled or no webhook URL set");
            }
            
            // Disconnect
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().getConnection().disconnect(Text.literal(reason));
            }
            
            // Show custom disconnect screen with screenshot and coordinates
            Path finalScreenshotPath = screenshotPath;
            mc.setScreen(new AutoLogDisconnectScreen(
                Text.literal("AutoLog: " + reason), 
                finalCoords, 
                finalScreenshotPath
            ));
        });
        
        // Don't toggle module off, just mark as disconnected for smart toggle
        // This allows the module to continue receiving tick events for re-enabling
    }

    private Path captureScreenshot(String reason, String coords) {
        Path savedPath = null;
        try {
            // Capture screenshot on main thread (OpenGL requires this)
            Framebuffer framebuffer = mc.getFramebuffer();
            int width = framebuffer.textureWidth;
            int height = framebuffer.textureHeight;
            
            ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
            
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
            
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int i = (x + (height - 1 - y) * width) * 4;
                    int r = buffer.get(i) & 0xFF;
                    int g = buffer.get(i + 1) & 0xFF;
                    int b = buffer.get(i + 2) & 0xFF;
                    image.setRGB(x, y, (r << 16) | (g << 8) | b);
                }
            }
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            byte[] imageBytes = baos.toByteArray();
            
            // Save screenshot to screenshots folder
            try {
                Path screenshotsDir = mc.runDirectory.toPath().resolve("screenshots");
                Files.createDirectories(screenshotsDir);
                Path screenshotPath = screenshotsDir.resolve("autolog_last.png");
                Files.write(screenshotPath, imageBytes);
                lastScreenshotPath = screenshotPath;
                savedPath = screenshotPath;
                info("Screenshot saved to: " + screenshotPath);
            } catch (Exception e) {
                error("Failed to save screenshot: " + e.getMessage());
            }
            
        } catch (Exception e) {
            error("Failed to capture screenshot: " + e.getMessage());
        }
        return savedPath;
    }

    private void sendToDiscord(byte[] imageBytes, String reason, String coords) {
        try {
            info("Sending to Discord webhook...");
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            URL url = new URL(webhookUrl.get());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            
            OutputStream os = conn.getOutputStream();
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true);
            
            // Add JSON payload with coordinates
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"payload_json\"").append("\r\n");
            writer.append("Content-Type: application/json; charset=UTF-8").append("\r\n\r\n");
            String content = "AutoLog triggered: " + reason + (coords.isEmpty() ? "" : "\\n" + coords);
            writer.append("{\"content\":\"").append(content.replace("\"", "\\\"").replace("\n", "\\n")).append("\"}").append("\r\n");
            
            // Add file
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"autolog.png\"").append("\r\n");
            writer.append("Content-Type: image/png").append("\r\n\r\n");
            writer.flush();
            
            os.write(imageBytes);
            os.flush();
            
            writer.append("\r\n");
            writer.append("--").append(boundary).append("--").append("\r\n");
            writer.flush();
            writer.close();
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200 || responseCode == 204) {
                info("Screenshot sent to Discord successfully!");
            } else {
                error("Failed to send screenshot. Status: " + responseCode);
                // Read error response
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                    String line;
                    StringBuilder response = new StringBuilder();
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    error("Discord error: " + response.toString());
                } catch (Exception e) {
                    // Ignore
                }
            }
            
            conn.disconnect();
            
        } catch (Exception e) {
            error("Error sending to Discord: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendToDiscordTextOnly(String reason, String coords) {
        try {
            info("Sending text-only message to Discord webhook...");
            URL url = new URL(webhookUrl.get());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            
            String content = "🚨 **AutoLog Triggered** 🚨\\n" +
                           "**Reason:** " + reason + "\\n" +
                           (coords.isEmpty() ? "" : "**Location:** " + coords + "\\n") +
                           "**Time:** " + java.time.Instant.now().toString();
            
            String jsonPayload = "{\"content\":\"" + content.replace("\"", "\\\"").replace("\n", "\\n") + "\"}";
            
            OutputStream os = conn.getOutputStream();
            byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
            os.flush();
            os.close();
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200 || responseCode == 204) {
                info("AutoLog message sent to Discord successfully!");
            } else {
                error("Failed to send AutoLog message to Discord. Status: " + responseCode);
                // Read error response
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                    String line;
                    StringBuilder response = new StringBuilder();
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    error("Discord error: " + response.toString());
                } catch (Exception e) {
                    // Ignore
                }
            }
            
            conn.disconnect();
            
        } catch (Exception e) {
            error("Error sending AutoLog message to Discord: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onActivate() {
        totemPops = 0;
    }

    @Override
    public void onDeactivate() {
        hasDisconnected = false;
    }
}
