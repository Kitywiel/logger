package com.example.addon.modules;

// LEGITIMATE MINECRAFT MOD - NOT MALWARE
// This module sends game statistics to YOUR OWN Discord webhook (optional)
// You must manually configure the webhook URL in settings
// Network requests only go to Discord webhooks YOU control
// No personal data or files are accessed outside Minecraft

import com.example.addon.LoggerAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

public class Logger extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWebhook = settings.createGroup("Webhook");
    private final SettingGroup sgItems = settings.createGroup("Items");

    private final Setting<String> webhookUrl = sgWebhook.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL to send logs to.")
        .defaultValue("")
        .build()
    );

    private final Setting<Boolean> sendScreenshot = sgWebhook.add(new BoolSetting.Builder()
        .name("send-screenshot")
        .description("Send screenshot with log data to Discord webhook.")
        .defaultValue(false)
        .visible(() -> !webhookUrl.get().isEmpty())
        .build()
    );

    private final Setting<Double> logInterval = sgGeneral.add(new DoubleSetting.Builder()
        .name("log-interval")
        .description("How often to log information (in minutes).")
        .defaultValue(1.0)
        .min(0.1)
        .sliderMax(10.0)
        .build()
    );

    private final Setting<Boolean> logCoords = sgGeneral.add(new BoolSetting.Builder()
        .name("log-coordinates")
        .description("Logs your coordinates.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> logDistance = sgGeneral.add(new BoolSetting.Builder()
        .name("log-distance")
        .description("Logs Distance from Start since last update.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> logDistanceMoved = sgGeneral.add(new BoolSetting.Builder()
        .name("log-distance-moved")
        .description("Logs distance moved since last log interval.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> logTotalDistance = sgGeneral.add(new BoolSetting.Builder()
        .name("log-total-distance")
        .description("Logs total distance traveled since enabled.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> logFood = sgGeneral.add(new BoolSetting.Builder()
        .name("log-food")
        .description("Logs your food level.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> logHealth = sgGeneral.add(new BoolSetting.Builder()
        .name("log-health")
        .description("Logs your health.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> logGearHealth = sgGeneral.add(new BoolSetting.Builder()
        .name("log-gear-health")
        .description("Logs the durability of your armor and held item.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> logTPS = sgGeneral.add(new BoolSetting.Builder()
        .name("log-tps")
        .description("Logs server TPS.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> logPing = sgGeneral.add(new BoolSetting.Builder()
        .name("log-ping")
        .description("Logs your ping.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<Item>> trackedItems = sgItems.add(new ItemListSetting.Builder()
        .name("tracked-items")
        .description("Items to track the total amount of.")
        .build()
    );

    private final Setting<Boolean> logItemCount = sgItems.add(new BoolSetting.Builder()
        .name("log-item-count")
        .description("Logs the total amount of the selected items.")
        .defaultValue(false)
        .build()
    );

    private int tickCounter = 0;
    private int ticksPerInterval = 1200;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    
    private double lastX = 0;
    private double lastY = 0;
    private double lastZ = 0;
    private boolean hasLastPosition = false;
    private double startX = 0;
    private double startY = 0;
    private double startZ = 0;
    private boolean hasStartPosition = false;

    public Logger() {
        super(LoggerAddon.CATEGORY, "logger", "Logs various game information at set intervals.");
    }

    // Public getters for HUD
    public int getTickCounter() {
        return tickCounter;
    }

    public double getLogInterval() {
        return logInterval.get();
    }

    public double getTotalDistanceTraveled() {
        if (!hasStartPosition || mc.player == null) return 0;
        
        double currentX = mc.player.getX();
        double currentY = mc.player.getY();
        double currentZ = mc.player.getZ();
        
        return Math.sqrt(
            Math.pow(currentX - startX, 2) + 
            Math.pow(currentY - startY, 2) + 
            Math.pow(currentZ - startZ, 2)
        );
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        // Update last position for other calculations
        if (mc.player != null) {
            lastX = mc.player.getX();
            lastY = mc.player.getY();
            lastZ = mc.player.getZ();
            hasLastPosition = true;
        }

        tickCounter++;

        if (tickCounter >= ticksPerInterval) {
            tickCounter = 0;
            ticksPerInterval = (int) (logInterval.get() * 1200);
            logInformation();
        }
    }

    private void logInformation() {
        if (webhookUrl.get().isEmpty()) {
            error("Discord webhook URL is not set!");
            return;
        }

        StringBuilder fields = new StringBuilder();
        boolean first = true;

        double currentX = mc.player.getX();
        double currentY = mc.player.getY();
        double currentZ = mc.player.getZ();

        if (logCoords.get()) {
            if (!first) fields.append(",");
            fields.append(String.format("{\"name\":\"📍 Coordinates\",\"value\":\"X: %s\\nY: %s\\nZ: %s\",\"inline\":true}", 
                formatCoordinate(currentX), 
                formatCoordinate(currentY), 
                formatCoordinate(currentZ)
            ));
            first = false;
        }

        if (logDistance.get() && hasStartPosition) {
            double distanceFromStart = Math.sqrt(
                Math.pow(currentX - startX, 2) + 
                Math.pow(currentY - startY, 2) + 
                Math.pow(currentZ - startZ, 2)
            );
            if (!first) fields.append(",");
            fields.append(String.format("{\"name\":\"🚶 Distance from Start\",\"value\":\"%.1f blocks\",\"inline\":true}", distanceFromStart));
            first = false;
        }

        if (logDistanceMoved.get() && hasLastPosition) {
            double distanceMoved = Math.sqrt(
                Math.pow(currentX - lastX, 2) + 
                Math.pow(currentY - lastY, 2) + 
                Math.pow(currentZ - lastZ, 2)
            );
            if (!first) fields.append(",");
            fields.append(String.format("{\"name\":\"📏 Distance Moved\",\"value\":\"%.1f blocks\",\"inline\":true}", distanceMoved));
            first = false;
        }

        if (logTotalDistance.get()) {
            if (!first) fields.append(",");
            fields.append(String.format("{\"name\":\"🗺️ Total Distance\",\"value\":\"%.1f blocks (%.1f km)\",\"inline\":true}", 
                getTotalDistanceTraveled(), 
                getTotalDistanceTraveled() / 1000.0));
            first = false;
        }

        lastX = currentX;
        lastY = currentY;
        lastZ = currentZ;
        hasLastPosition = true;

        if (logHealth.get()) {
            if (!first) fields.append(",");
            fields.append(String.format("{\"name\":\"❤️ Health\",\"value\":\"%.1f/%.1f\",\"inline\":true}", 
                mc.player.getHealth(), 
                mc.player.getMaxHealth()
            ));
            first = false;
        }

        if (logFood.get()) {
            if (!first) fields.append(",");
            fields.append(String.format("{\"name\":\"🍖 Food\",\"value\":\"%d/20\",\"inline\":true}", 
                mc.player.getHungerManager().getFoodLevel()
            ));
            first = false;
        }

        if (logTPS.get() && mc.world != null) {
            float tps = 20.0f;
            if (!first) fields.append(",");
            fields.append(String.format("{\"name\":\"⚙️ TPS\",\"value\":\"%.1f\",\"inline\":true}", tps));
            first = false;
        }

        if (logPing.get() && mc.getNetworkHandler() != null) {
            int ping = 0;
            try {
                var playerEntry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
                if (playerEntry != null) {
                    ping = playerEntry.getLatency();
                }
            } catch (Exception ignored) {}
            
            if (!first) fields.append(",");
            fields.append(String.format("{\"name\":\"📡 Ping\",\"value\":\"%d ms\",\"inline\":true}", ping));
            first = false;
        }

        if (logGearHealth.get()) {
            String gearInfo = getGearHealthForEmbed();
            if (!gearInfo.isEmpty()) {
                if (!first) fields.append(",");
                fields.append(String.format("{\"name\":\"🛡️ Gear Durability\",\"value\":\"%s\",\"inline\":false}", 
                    gearInfo.replace("\"", "\\\"")));
                first = false;
            }
        }

        if (logItemCount.get() && !trackedItems.get().isEmpty()) {
            StringBuilder itemCounts = new StringBuilder();
            for (Item item : trackedItems.get()) {
                int count = countItem(item);
                if (itemCounts.length() > 0) itemCounts.append("\\n");
                itemCounts.append(String.format("%s: %d", 
                    item.getName().getString(), 
                    count
                ));
            }
            if (itemCounts.length() > 0) {
                if (!first) fields.append(",");
                fields.append(String.format("{\"name\":\"📦 Item Counts\",\"value\":\"%s\",\"inline\":false}", 
                    itemCounts.toString().replace("\"", "\\\"")));
            }
        }

        sendToDiscord(fields.toString());
    }

    private Path captureScreenshot(String reason) {
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
            
            // Save to temp directory
            Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"));
            savedPath = tempDir.resolve("logger_screenshot_" + System.currentTimeMillis() + ".png");
            Files.write(savedPath, baos.toByteArray());
            
            info("Screenshot saved: " + savedPath);
        } catch (Exception e) {
            error("Failed to capture screenshot: " + e.getMessage());
            e.printStackTrace();
        }
        return savedPath;
    }

    private void sendToDiscord(String fields) {
        new Thread(() -> {
            try {
                double currentX = mc.player.getX();
                double currentY = mc.player.getY();
                double currentZ = mc.player.getZ();
                
                // Create title with coordinates
                String title = logCoords.get() 
                    ? String.format("⚡ Status Update - [%s, %s, %s]", 
                        formatCoordinate(currentX), 
                        formatCoordinate(currentY), 
                        formatCoordinate(currentZ))
                    : "⚡ Status Update";
                
                if (sendScreenshot.get()) {
                    // Capture screenshot on main thread then send
                    mc.execute(() -> {
                        Path screenshotPath = captureScreenshot("Logger Update");
                        if (screenshotPath != null) {
                            new Thread(() -> {
                                try {
                                    byte[] imageBytes = Files.readAllBytes(screenshotPath);
                                    sendToDiscordWithScreenshot(imageBytes, fields, title);
                                    // Clean up temp file
                                    try {
                                        Files.deleteIfExists(screenshotPath);
                                    } catch (Exception cleanupEx) {
                                        // Ignore cleanup errors
                                    }
                                } catch (Exception e) {
                                    error("Failed to send screenshot: " + e.getMessage());
                                    // Fallback to text-only
                                    sendToDiscordTextOnly(fields, title);
                                }
                            }).start();
                        } else {
                            // Fallback to text-only
                            sendToDiscordTextOnly(fields, title);
                        }
                    });
                } else {
                    // Send text-only
                    sendToDiscordTextOnly(fields, title);
                }
            } catch (Exception e) {
                error("Error preparing Discord message: " + e.getMessage());
            }
        }).start();
    }

    private void sendToDiscordTextOnly(String fields, String title) {
        try {
            String jsonPayload = String.format(
                "{\"embeds\":[{\"title\":\"%s\",\"color\":5814783,\"fields\":[%s],\"timestamp\":\"%s\",\"footer\":{\"text\":\"Kitywiel's Addon\"}}]}", 
                title,
                fields,
                java.time.Instant.now().toString()
            );

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl.get()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 204 || response.statusCode() == 200) {
                info("Status sent to Discord successfully!");
            } else {
                error("Failed to send to Discord. Status code: " + response.statusCode());
            }
        } catch (Exception e) {
            error("Error sending to Discord: " + e.getMessage());
        }
    }

    private void sendToDiscordWithScreenshot(byte[] imageBytes, String fields, String title) {
        try {
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            
            // Create JSON payload
            String jsonPayload = String.format(
                "{\"embeds\":[{\"title\":\"%s\",\"color\":5814783,\"fields\":[%s],\"timestamp\":\"%s\",\"footer\":{\"text\":\"Kitywiel's Addon\"},\"image\":{\"url\":\"attachment://screenshot.png\"}}]}", 
                title,
                fields,
                java.time.Instant.now().toString()
            );
            
            // Create multipart body
            ByteArrayOutputStream bodyStream = new ByteArrayOutputStream();
            bodyStream.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            bodyStream.write("Content-Disposition: form-data; name=\"payload_json\"\r\n".getBytes(StandardCharsets.UTF_8));
            bodyStream.write("Content-Type: application/json\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            bodyStream.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            bodyStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
            
            bodyStream.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            bodyStream.write("Content-Disposition: form-data; name=\"file\"; filename=\"screenshot.png\"\r\n".getBytes(StandardCharsets.UTF_8));
            bodyStream.write("Content-Type: image/png\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            bodyStream.write(imageBytes);
            bodyStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
            bodyStream.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            
            byte[] body = bodyStream.toByteArray();
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl.get()))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 204 || response.statusCode() == 200) {
                info("Status with screenshot sent to Discord successfully!");
            } else {
                error("Failed to send screenshot to Discord. Status code: " + response.statusCode());
                // Fallback to text-only
                sendToDiscordTextOnly(fields, title);
            }
        } catch (Exception e) {
            error("Error sending screenshot to Discord: " + e.getMessage());
            // Fallback to text-only
            sendToDiscordTextOnly(fields, title);
        }
    }

    private int countItem(Item item) {
        if (mc.player == null) return 0;
        
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private String getGearHealthForEmbed() {
        StringBuilder gear = new StringBuilder();
        
        ItemStack helmet = mc.player.getInventory().getArmorStack(3);
        if (!helmet.isEmpty() && helmet.isDamageable()) {
            int durability = helmet.getMaxDamage() - helmet.getDamage();
            int maxDurability = helmet.getMaxDamage();
            if (gear.length() > 0) gear.append("\\n");
            gear.append(String.format("Helmet: %d/%d (%.0f%%)", 
                durability, maxDurability, (durability * 100.0 / maxDurability)));
        }
        
        ItemStack chestplate = mc.player.getInventory().getArmorStack(2);
        if (!chestplate.isEmpty() && chestplate.isDamageable()) {
            int durability = chestplate.getMaxDamage() - chestplate.getDamage();
            int maxDurability = chestplate.getMaxDamage();
            if (gear.length() > 0) gear.append("\\n");
            gear.append(String.format("Chestplate: %d/%d (%.0f%%)", 
                durability, maxDurability, (durability * 100.0 / maxDurability)));
        }
        
        ItemStack leggings = mc.player.getInventory().getArmorStack(1);
        if (!leggings.isEmpty() && leggings.isDamageable()) {
            int durability = leggings.getMaxDamage() - leggings.getDamage();
            int maxDurability = leggings.getMaxDamage();
            if (gear.length() > 0) gear.append("\\n");
            gear.append(String.format("Leggings: %d/%d (%.0f%%)", 
                durability, maxDurability, (durability * 100.0 / maxDurability)));
        }
        
        ItemStack boots = mc.player.getInventory().getArmorStack(0);
        if (!boots.isEmpty() && boots.isDamageable()) {
            int durability = boots.getMaxDamage() - boots.getDamage();
            int maxDurability = boots.getMaxDamage();
            if (gear.length() > 0) gear.append("\\n");
            gear.append(String.format("Boots: %d/%d (%.0f%%)", 
                durability, maxDurability, (durability * 100.0 / maxDurability)));
        }
        
        ItemStack mainHand = mc.player.getMainHandStack();
        if (!mainHand.isEmpty() && mainHand.isDamageable()) {
            int durability = mainHand.getMaxDamage() - mainHand.getDamage();
            int maxDurability = mainHand.getMaxDamage();
            if (gear.length() > 0) gear.append("\\n");
            gear.append(String.format("%s: %d/%d (%.0f%%)", 
                mainHand.getName().getString(),
                durability, maxDurability, (durability * 100.0 / maxDurability)));
        }
        
        return gear.toString();
    }

    private String formatCoordinate(double coord) {
        // Format coordinate with comma separators
        return String.format("%,.1f", coord);
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
        ticksPerInterval = (int) (logInterval.get() * 1200);
        hasLastPosition = false;
        hasStartPosition = false;
        
        if (mc.player != null) {
            lastX = mc.player.getX();
            lastY = mc.player.getY();
            lastZ = mc.player.getZ();
            hasLastPosition = true;
            
            // Set starting coordinates
            startX = mc.player.getX();
            startY = mc.player.getY();
            startZ = mc.player.getZ();
            hasStartPosition = true;
        }
    }
}
