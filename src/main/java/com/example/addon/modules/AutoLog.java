package com.example.addon.modules;

import com.example.addon.LoggerAddon;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Formatting;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Set;

public class AutoLog extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgEntities = settings.createGroup("Entities");
    private final SettingGroup sgWebhook = settings.createGroup("Webhook");

    // Webhook settings
    private final Setting<String> webhookUrl = sgWebhook.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL to send screenshot to.")
        .defaultValue("")
        .build()
    );

    private final Setting<Boolean> enableScreenshot = sgWebhook.add(new BoolSetting.Builder()
        .name("enable-screenshot")
        .description("Send screenshot to Discord when logging.")
        .defaultValue(true)
        .build()
    );

    // General settings (from Meteor's AutoLog)
    private final Setting<Integer> health = sgGeneral.add(new IntSetting.Builder()
        .name("health")
        .description("Automatically disconnects when health is lower or equal to this value. Set to 0 to disable.")
        .defaultValue(6)
        .range(0, 19)
        .sliderMax(19)
        .build()
    );

    private final Setting<Boolean> smart = sgGeneral.add(new BoolSetting.Builder()
        .name("predict-incoming-damage")
        .description("Disconnects when it detects you're about to take enough damage to set you under the 'health' setting.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> totemPops = sgGeneral.add(new IntSetting.Builder()
        .name("totem-pops")
        .description("Disconnects when you have popped this many totems. Set to 0 to disable.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Boolean> onlyTrusted = sgGeneral.add(new BoolSetting.Builder()
        .name("only-trusted")
        .description("Disconnects when a player not on your friends list appears in render distance.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> instantDeath = sgGeneral.add(new BoolSetting.Builder()
        .name("32K")
        .description("Disconnects when a player near you can instantly kill you.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> smartToggle = sgGeneral.add(new BoolSetting.Builder()
        .name("smart-toggle")
        .description("Disables Auto Log after a low-health logout. WILL re-enable once you heal.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> toggleOff = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-off")
        .description("Disables Auto Log after usage.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> toggleAutoReconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-auto-reconnect")
        .description("Whether to disable Auto Reconnect after a logout.")
        .defaultValue(true)
        .build()
    );

    // Entities

    private final Setting<Set<EntityType<?>>> entities = sgEntities.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Disconnects when a specified entity is present within a specified range.")
        .defaultValue(EntityType.END_CRYSTAL)
        .build()
    );

    private final Setting<Boolean> useTotalCount = sgEntities.add(new BoolSetting.Builder()
        .name("use-total-count")
        .description("Toggle between counting the total number of all selected entities or each entity individually.")
        .defaultValue(true)
        .visible(() -> !entities.get().isEmpty())
        .build());

    private final Setting<Integer> combinedEntityThreshold = sgEntities.add(new IntSetting.Builder()
        .name("combined-entity-threshold")
        .description("The minimum total number of selected entities that must be near you before disconnection occurs.")
        .defaultValue(10)
        .min(1)
        .sliderMax(32)
        .visible(() -> useTotalCount.get() && !entities.get().isEmpty())
        .build()
    );

    private final Setting<Integer> individualEntityThreshold = sgEntities.add(new IntSetting.Builder()
        .name("individual-entity-threshold")
        .description("The minimum number of entities individually that must be near you before disconnection occurs.")
        .defaultValue(2)
        .min(1)
        .sliderMax(16)
        .visible(() -> !useTotalCount.get() && !entities.get().isEmpty())
        .build()
    );

    private final Setting<Integer> range = sgEntities.add(new IntSetting.Builder()
        .name("range")
        .description("How close an entity has to be to you before you disconnect.")
        .defaultValue(5)
        .min(1)
        .sliderMax(16)
        .visible(() -> !entities.get().isEmpty())
        .build()
    );

    // Declaring variables outside the loop for better efficiency
    private final Object2IntMap<EntityType<?>> entityCounts = new Object2IntOpenHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private int pops;
    private boolean isLoggingOut = false;

    public AutoLog() {
        super(LoggerAddon.CATEGORY, "auto-log", "Automatically disconnects you when certain requirements are met.");
    }

    @Override
    public void onActivate() {
        pops = 0;
        isLoggingOut = false;
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof EntityStatusS2CPacket p)) return;
        if (p.getStatus() != EntityStatuses.USE_TOTEM_OF_UNDYING) return;

        Entity entity = p.getEntity(mc.world);
        if (entity == null || !entity.equals(mc.player)) return;

        pops++;
        if (totemPops.get() > 0 && pops >= totemPops.get()) {
            disconnect("Popped " + pops + " totems.");
            if (toggleOff.get()) this.toggle();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (isLoggingOut) return;
        
        float playerHealth = mc.player.getHealth();
        if (playerHealth <= 0) {
            this.toggle();
            return;
        }
        if (playerHealth <= health.get()) {
            disconnect("Health was lower than " + health.get() + ".");
            if (smartToggle.get()) {
                if (isActive()) this.toggle();
                enableHealthListener();
            } else if (toggleOff.get()) this.toggle();
            return;
        }

        if (smart.get() && playerHealth + mc.player.getAbsorptionAmount() - PlayerUtils.possibleHealthReductions() < health.get()) {
            disconnect("Health was going to be lower than " + health.get() + ".");
            if (toggleOff.get()) this.toggle();
            return;
        }

        if (!onlyTrusted.get() && !instantDeath.get() && entities.get().isEmpty())
            return; // only check all entities if needed

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof PlayerEntity player && player.getUuid() != mc.player.getUuid()) {
                if (onlyTrusted.get() && player != mc.player && !Friends.get().isFriend(player)) {
                    disconnect(Text.literal("Non-trusted player '" + Formatting.RED + player.getName().getString() + Formatting.WHITE + "' appeared in your render distance."));
                    if (toggleOff.get()) this.toggle();
                    return;
                }

                if (instantDeath.get() && PlayerUtils.isWithin(entity, 8) && DamageUtils.getAttackDamage(player, mc.player)
                    > playerHealth + mc.player.getAbsorptionAmount()) {
                    disconnect("Anti-32k measures.");
                    if (toggleOff.get()) this.toggle();
                    return;
                }
            }
        }

        // Entities detection Logic
        if (!entities.get().isEmpty()) {
            // Reset totalEntities count and clear the entityCounts map
            int totalEntities = 0;
            entityCounts.clear();

            // Iterate through all entities in the world and count the ones that match the selected types and are within range
            for (Entity entity : mc.world.getEntities()) {
                if (PlayerUtils.isWithin(entity, range.get()) && entities.get().contains(entity.getType())) {
                    totalEntities++;
                    if (!useTotalCount.get()) {
                        entityCounts.put(entity.getType(), entityCounts.getOrDefault(entity.getType(), 0) + 1);
                    }
                }
            }

            if (useTotalCount.get() && totalEntities >= combinedEntityThreshold.get()) {
                disconnect("Total number of selected entities within range exceeded the limit.");
                if (toggleOff.get()) this.toggle();
            }
            else if (!useTotalCount.get()) {
                // Check if the count of each entity type exceeds the specified limit
                for (Object2IntMap.Entry<EntityType<?>> entry : entityCounts.object2IntEntrySet()) {
                    if (entry.getIntValue() >= individualEntityThreshold.get()) {
                        disconnect("Number of " + entry.getKey().getName().getString() + " within range exceeded the limit.");
                        if (toggleOff.get()) this.toggle();
                        return;
                    }
                }
            }
        }
    }

    private void disconnect(String reason) {
        disconnect(Text.literal(reason));
    }

    private void disconnect(Text reason) {
        if (isLoggingOut) return;
        isLoggingOut = true;

        MutableText text = Text.literal("[AutoLog] ");
        text.append(reason);

        AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
        if (autoReconnect.isActive() && toggleAutoReconnect.get()) {
            text.append(Text.literal("\n\nINFO - AutoReconnect was disabled").withColor(Colors.GRAY));
            autoReconnect.toggle();
        }

        // Take screenshot if enabled
        if (enableScreenshot.get() && !webhookUrl.get().isEmpty()) {
            captureAndSendScreenshot(reason.getString());
        }

        // Disconnect after a small delay
        new Thread(() -> {
            try {
                Thread.sleep(100); // Small delay to ensure screenshot is captured
                mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(text));
            } catch (Exception e) {
                error("Failed to disconnect: " + e.getMessage());
            }
        }).start();
    }

    private void captureAndSendScreenshot(String reason) {
        new Thread(() -> {
            try {
                // Get framebuffer
                int width = mc.getWindow().getFramebufferWidth();
                int height = mc.getWindow().getFramebufferHeight();

                // Allocate buffer for pixels
                ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4);

                // Read pixels from framebuffer (this needs to be done on render thread)
                mc.execute(() -> {
                    try {
                        com.mojang.blaze3d.systems.RenderSystem.bindTexture(
                            mc.getFramebuffer().getColorAttachment()
                        );
                        org.lwjgl.opengl.GL11.glReadPixels(
                            0, 0, width, height,
                            org.lwjgl.opengl.GL11.GL_RGBA,
                            org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE,
                            buffer
                        );

                        // Convert to BufferedImage
                        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                        for (int y = 0; y < height; y++) {
                            for (int x = 0; x < width; x++) {
                                int i = (x + (height - y - 1) * width) * 4;
                                int r = buffer.get(i) & 0xFF;
                                int g = buffer.get(i + 1) & 0xFF;
                                int b = buffer.get(i + 2) & 0xFF;
                                int a = buffer.get(i + 3) & 0xFF;
                                image.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                            }
                        }

                        // Send to Discord
                        sendToDiscord(image, reason);
                    } catch (Exception e) {
                        error("Failed to capture screenshot: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                error("Failed to prepare screenshot: " + e.getMessage());
            }
        }).start();
    }

    private void sendToDiscord(BufferedImage image, String reason) {
        try {
            // Get player position
            double x = mc.player != null ? mc.player.getX() : 0;
            double y = mc.player != null ? mc.player.getY() : 0;
            double z = mc.player != null ? mc.player.getZ() : 0;
            String dimension = mc.world != null ? mc.world.getRegistryKey().getValue().getPath() : "unknown";
            
            // Format coordinates with commas
            String formattedX = String.format("%,.0f", x);
            String formattedY = String.format("%,.0f", y);
            String formattedZ = String.format("%,.0f", z);
            
            // Convert image to bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            byte[] imageBytes = baos.toByteArray();

            // Create multipart form data
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            StringBuilder payload = new StringBuilder();

            // JSON part with coordinates
            payload.append("--").append(boundary).append("\r\n");
            payload.append("Content-Disposition: form-data; name=\"payload_json\"\r\n");
            payload.append("Content-Type: application/json\r\n\r\n");
            payload.append(String.format(
                "{\"content\":\"**AutoLog Triggered**\\nReason: %s\\nTotem Pops: %d\\nHealth: %.1f\\n📍 Location: %s, %s, %s\\n🌍 Dimension: %s\"}",
                reason.replace("\"", "\\\""), 
                pops, 
                mc.player != null ? mc.player.getHealth() : 0,
                formattedX, formattedY, formattedZ,
                dimension.replace("\"", "\\\"")
            ));
            payload.append("\r\n");

            // File part
            payload.append("--").append(boundary).append("\r\n");
            payload.append("Content-Disposition: form-data; name=\"file\"; filename=\"autolog.png\"\r\n");
            payload.append("Content-Type: image/png\r\n\r\n");

            // Combine payload
            byte[] payloadBytes = payload.toString().getBytes();
            byte[] endBytes = ("\r\n--" + boundary + "--\r\n").getBytes();
            byte[] fullPayload = new byte[payloadBytes.length + imageBytes.length + endBytes.length];
            System.arraycopy(payloadBytes, 0, fullPayload, 0, payloadBytes.length);
            System.arraycopy(imageBytes, 0, fullPayload, payloadBytes.length, imageBytes.length);
            System.arraycopy(endBytes, 0, fullPayload, payloadBytes.length + imageBytes.length, endBytes.length);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl.get()))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(fullPayload))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 204 || response.statusCode() == 200) {
                info("Screenshot sent to Discord successfully!");
            } else {
                error("Failed to send to Discord. Status code: " + response.statusCode());
            }
        } catch (Exception e) {
            error("Error sending to Discord: " + e.getMessage());
        }
    }

    private class StaticListener {
        @EventHandler
        private void healthListener(TickEvent.Post event) {
            if (isActive()) disableHealthListener();

            else if (Utils.canUpdate()
                && !mc.player.isDead()
                && mc.player.getHealth() > health.get()) {
                info("Player health greater than minimum, re-enabling module.");
                toggle();
                disableHealthListener();
            }
        }
    }

    private final StaticListener staticListener = new StaticListener();

    private void enableHealthListener() {
        MeteorClient.EVENT_BUS.subscribe(staticListener);
    }

    private void disableHealthListener() {
        MeteorClient.EVENT_BUS.unsubscribe(staticListener);
    }
}
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWebhook = settings.createGroup("Webhook");
    private final SettingGroup sgTriggers = settings.createGroup("Triggers");

    private final Setting<String> webhookUrl = sgWebhook.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL to send screenshot to.")
        .defaultValue("")
        .build()
    );

    private final Setting<Boolean> enableScreenshot = sgWebhook.add(new BoolSetting.Builder()
        .name("enable-screenshot")
        .description("Send screenshot to Discord when logging.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> totemPops = sgTriggers.add(new IntSetting.Builder()
        .name("totem-pops")
        .description("Disconnect after this many totem pops. 0 to disable.")
        .defaultValue(1)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> healthThreshold = sgTriggers.add(new IntSetting.Builder()
        .name("health")
        .description("Disconnect when health drops below this. 0 to only use totem pops.")
        .defaultValue(6)
        .min(0)
        .sliderMax(36)
        .build()
    );

    private final Setting<Boolean> illegalDisconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("illegal-disconnect")
        .description("Use illegal disconnect packet (may bypass some anti-cheats).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> instantKillCheck = sgTriggers.add(new BoolSetting.Builder()
        .name("instant-kill-check")
        .description("Disconnect if a player can instantly kill you (32k, etc).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> killRange = sgTriggers.add(new DoubleSetting.Builder()
        .name("kill-range")
        .description("Range to check for players that can instant kill.")
        .defaultValue(6.0)
        .min(1.0)
        .sliderMax(10.0)
        .visible(instantKillCheck::get)
        .build()
    );

    private int totemPopCount = 0;
    private boolean isLoggingOut = false;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    public AutoLog() {
        super(LoggerAddon.CATEGORY, "auto-log", "Automatically disconnects when in danger and sends screenshot.");
    }

    @Override
    public void onActivate() {
        totemPopCount = 0;
        isLoggingOut = false;
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (mc.player == null || isLoggingOut) return;

        // Detect totem pop
        if (event.packet instanceof EntityStatusS2CPacket packet) {
            if (packet.getStatus() == 35) { // Totem pop status
                Entity entity = packet.getEntity(mc.world);
                if (entity != null && entity.equals(mc.player)) {
                    totemPopCount++;
                    info("Totem popped! Count: " + totemPopCount);

                    if (totemPops.get() > 0 && totemPopCount >= totemPops.get()) {
                        disconnect("Totem pops reached " + totemPopCount);
                    }
                }
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || isLoggingOut) return;

        // Health check
        if (healthThreshold.get() > 0) {
            float health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
            if (health <= healthThreshold.get()) {
                disconnect("Health dropped to " + health);
                return;
            }
        }

        // Instant kill check
        if (instantKillCheck.get()) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player == mc.player) continue;
                if (player.squaredDistanceTo(mc.player) > killRange.get() * killRange.get()) continue;

                // Check if player is holding a powerful weapon or has dangerous equipment
                // This is a simplified check - in reality you'd check for 32k weapons, etc.
                if (canInstantKill(player)) {
                    disconnect("Potential instant kill threat detected");
                    return;
                }
            }

            // Check for nearby crystals that could kill
            Box box = mc.player.getBoundingBox().expand(killRange.get());
            for (Entity entity : mc.world.getOtherEntities(mc.player, box)) {
                if (entity instanceof EndCrystalEntity) {
                    // Calculate potential damage from crystal
                    double distance = mc.player.squaredDistanceTo(entity);
                    if (distance < 6.0 * 6.0) { // Dangerous crystal range
                        disconnect("Dangerous end crystal nearby");
                        return;
                    }
                }
            }
        }
    }

    private boolean canInstantKill(PlayerEntity player) {
        // Check if player has enchanted items that might be dangerous
        // This is a basic check - you'd want to add more sophisticated detection
        if (!player.getMainHandStack().isEmpty()) {
            if (player.getMainHandStack().hasEnchantments()) {
                // Could check for suspiciously high enchantment levels here
                return false; // Simplified for now
            }
        }
        return false;
    }

    private void disconnect(String reason) {
        if (isLoggingOut) return;
        isLoggingOut = true;

        info("Auto-logging: " + reason);

        // Take screenshot if enabled
        if (enableScreenshot.get() && !webhookUrl.get().isEmpty()) {
            captureAndSendScreenshot(reason);
        }

        // Disconnect
        new Thread(() -> {
            try {
                Thread.sleep(100); // Small delay to ensure screenshot is captured

                if (illegalDisconnect.get()) {
                    // Illegal disconnect - send malformed packet
                    if (mc.getNetworkHandler() != null) {
                        mc.getNetworkHandler().getConnection().disconnect(
                            net.minecraft.text.Text.literal("AutoLog: " + reason)
                        );
                    }
                } else {
                    // Normal disconnect
                    if (mc.world != null) {
                        mc.world.disconnect();
                    }
                    if (mc.getNetworkHandler() != null) {
                        mc.getNetworkHandler().getConnection().disconnect(
                            net.minecraft.text.Text.literal("AutoLog: " + reason)
                        );
                    }
                }
            } catch (Exception e) {
                error("Failed to disconnect: " + e.getMessage());
            }
        }).start();
    }

    private void captureAndSendScreenshot(String reason) {
        new Thread(() -> {
            try {
                // Get framebuffer
                int width = mc.getWindow().getFramebufferWidth();
                int height = mc.getWindow().getFramebufferHeight();

                // Allocate buffer for pixels
                ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4);

                // Read pixels from framebuffer (this needs to be done on render thread)
                mc.execute(() -> {
                    try {
                        com.mojang.blaze3d.systems.RenderSystem.bindTexture(
                            mc.getFramebuffer().getColorAttachment()
                        );
                        org.lwjgl.opengl.GL11.glReadPixels(
                            0, 0, width, height,
                            org.lwjgl.opengl.GL11.GL_RGBA,
                            org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE,
                            buffer
                        );

                        // Convert to BufferedImage
                        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                        for (int y = 0; y < height; y++) {
                            for (int x = 0; x < width; x++) {
                                int i = (x + (height - y - 1) * width) * 4;
                                int r = buffer.get(i) & 0xFF;
                                int g = buffer.get(i + 1) & 0xFF;
                                int b = buffer.get(i + 2) & 0xFF;
                                int a = buffer.get(i + 3) & 0xFF;
                                image.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                            }
                        }

                        // Send to Discord
                        sendToDiscord(image, reason);
                    } catch (Exception e) {
                        error("Failed to capture screenshot: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                error("Failed to prepare screenshot: " + e.getMessage());
            }
        }).start();
    }

    private void sendToDiscord(BufferedImage image, String reason) {
        try {
            // Convert image to bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            byte[] imageBytes = baos.toByteArray();

            // Create multipart form data
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            StringBuilder payload = new StringBuilder();

            // JSON part
            payload.append("--").append(boundary).append("\r\n");
            payload.append("Content-Disposition: form-data; name=\"payload_json\"\r\n");
            payload.append("Content-Type: application/json\r\n\r\n");
            payload.append(String.format(
                "{\"content\":\"**AutoLog Triggered**\\nReason: %s\\nTotem Pops: %d\\nHealth: %.1f\"}",
                reason, totemPopCount, mc.player != null ? mc.player.getHealth() : 0
            ));
            payload.append("\r\n");

            // File part
            payload.append("--").append(boundary).append("\r\n");
            payload.append("Content-Disposition: form-data; name=\"file\"; filename=\"autolog.png\"\r\n");
            payload.append("Content-Type: image/png\r\n\r\n");

            // Combine payload
            byte[] payloadBytes = payload.toString().getBytes();
            byte[] endBytes = ("\r\n--" + boundary + "--\r\n").getBytes();
            byte[] fullPayload = new byte[payloadBytes.length + imageBytes.length + endBytes.length];
            System.arraycopy(payloadBytes, 0, fullPayload, 0, payloadBytes.length);
            System.arraycopy(imageBytes, 0, fullPayload, payloadBytes.length, imageBytes.length);
            System.arraycopy(endBytes, 0, fullPayload, payloadBytes.length + imageBytes.length, endBytes.length);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl.get()))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(fullPayload))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 204 || response.statusCode() == 200) {
                info("Screenshot sent to Discord successfully!");
            } else {
                error("Failed to send to Discord. Status code: " + response.statusCode());
            }
        } catch (Exception e) {
            error("Error sending to Discord: " + e.getMessage());
        }
    }
}
