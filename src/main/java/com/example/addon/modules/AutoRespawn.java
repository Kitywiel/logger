package com.example.addon.modules;

// LEGITIMATE MINECRAFT MOD - NOT MALWARE
// This module sends notifications to YOUR OWN Discord webhook (optional)
// You must manually configure the webhook URL in settings
// Network requests only go to Discord webhooks YOU control

import com.example.addon.LoggerAddon;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class AutoRespawn extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWebhook = settings.createGroup("Webhook");

    public enum RespawnMode {
        OVERWORLD,
        NETHER
    }

    private final Setting<RespawnMode> mode = sgGeneral.add(new EnumSetting.Builder<RespawnMode>()
        .name("mode")
        .description("Overworld (beds) or Nether (respawn anchors).")
        .defaultValue(RespawnMode.OVERWORLD)
        .build()
    );

    private final Setting<Integer> respawnDistance = sgGeneral.add(new IntSetting.Builder()
        .name("respawn-distance")
        .description("Distance between respawn points (in thousands of blocks).")
        .defaultValue(1)
        .min(1)
        .sliderMax(10)
        .build()
    );

    private final Setting<Integer> placementRange = sgGeneral.add(new IntSetting.Builder()
        .name("placement-range")
        .description("How far to place blocks from player.")
        .defaultValue(4)
        .min(1)
        .sliderMax(6)
        .build()
    );

    private final Setting<Boolean> placeEnderChest = sgGeneral.add(new BoolSetting.Builder()
        .name("place-ender-chest")
        .description("Place an ender chest after setting respawn.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> notifyWebhook = sgWebhook.add(new BoolSetting.Builder()
        .name("notify-webhook")
        .description("Send notification to Discord webhook when respawn is set.")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> webhookUrl = sgWebhook.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL for notifications.")
        .defaultValue("")
        .visible(notifyWebhook::get)
        .build()
    );

    private BlockPos lastRespawnPos = null;
    private boolean waitingForRespawnConfirm = false;
    private boolean needsEnderChest = false;
    private int ticksSincePlace = 0;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    public AutoRespawn() {
        super(LoggerAddon.CATEGORY, "auto-respawn", "Automatically place respawn points along highways.");
    }

    // Public getters for HUD
    public BlockPos getLastRespawnPos() {
        return lastRespawnPos;
    }

    public int getRespawnDistance() {
        return respawnDistance.get();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        ticksSincePlace++;

        // Handle ender chest placement after respawn is set
        if (needsEnderChest && placeEnderChest.get() && ticksSincePlace > 20) {
            if (placeEnderChestNearby()) {
                needsEnderChest = false;
                info("Placed ender chest");
            }
        }

        if (waitingForRespawnConfirm) return;

        BlockPos currentPos = mc.player.getBlockPos();

        // Check if we should place a new respawn point
        if (shouldPlaceRespawn(currentPos)) {
            placeRespawnPoint(currentPos);
        }
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        String message = event.getMessage().getString().toLowerCase();
        
        // Wait for respawn confirmation message for both beds and anchors
        if (waitingForRespawnConfirm) {
            if (message.contains("respawn point set") || 
                message.contains("respawn") && message.contains("set")) {
                
                waitingForRespawnConfirm = false;
                String respawnType = mode.get() == RespawnMode.OVERWORLD ? "Bed" : "Respawn Anchor";
                
                info("Respawn point confirmed at " + lastRespawnPos.toShortString());
                
                // Send webhook notification after confirmation
                if (notifyWebhook.get() && !webhookUrl.get().isEmpty()) {
                    if (isValidWebhookUrl(webhookUrl.get())) {
                        sendWebhookNotification(lastRespawnPos, respawnType);
                    } else {
                        error("Invalid webhook URL format. Must start with https://");
                    }
                }
            }
        }
    }

    private boolean shouldPlaceRespawn(BlockPos currentPos) {
        if (lastRespawnPos == null) return true;

        double distance = Math.sqrt(currentPos.getSquaredDistance(lastRespawnPos));
        int targetDistance = respawnDistance.get() * 1000;

        return distance >= targetDistance;
    }

    private void placeRespawnPoint(BlockPos playerPos) {
        if (mode.get() == RespawnMode.OVERWORLD) {
            placeBed(playerPos);
        } else {
            placeRespawnAnchor(playerPos);
        }
    }

    private void placeBed(BlockPos playerPos) {
        // Find bed in hotbar
        int bedSlot = findItemInHotbar(Items.RED_BED, Items.WHITE_BED, Items.BLUE_BED, 
                                        Items.BLACK_BED, Items.BROWN_BED, Items.CYAN_BED,
                                        Items.GRAY_BED, Items.GREEN_BED, Items.LIGHT_BLUE_BED,
                                        Items.LIGHT_GRAY_BED, Items.LIME_BED, Items.MAGENTA_BED,
                                        Items.ORANGE_BED, Items.PINK_BED, Items.PURPLE_BED, Items.YELLOW_BED);
        
        if (bedSlot == -1) {
            error("No bed found in hotbar!");
            return;
        }

        // Find suitable placement location
        BlockPos placePos = findPlacementLocation(playerPos);
        if (placePos == null) {
            error("No suitable location for bed!");
            return;
        }

        // Switch to bed and place
        int previousSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = bedSlot;

        BlockHitResult hitResult = new BlockHitResult(
            Vec3d.ofCenter(placePos),
            Direction.UP,
            placePos.down(),
            false
        );

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        
        mc.player.getInventory().selectedSlot = previousSlot;
        
        waitingForRespawnConfirm = true;
        lastRespawnPos = placePos;
        needsEnderChest = placeEnderChest.get();
        ticksSincePlace = 0;
        
        info("Placed bed at " + placePos.toShortString() + " - waiting for confirmation");
    }

    private void placeRespawnAnchor(BlockPos playerPos) {
        // Find respawn anchor
        int anchorSlot = findItemInHotbar(Items.RESPAWN_ANCHOR);
        if (anchorSlot == -1) {
            error("No respawn anchor found in hotbar!");
            return;
        }

        // Find glowstone for charging
        int glowstoneSlot = findItemInHotbar(Items.GLOWSTONE);
        if (glowstoneSlot == -1) {
            error("No glowstone found in hotbar!");
            return;
        }

        // Find suitable placement location
        BlockPos placePos = findPlacementLocation(playerPos);
        if (placePos == null) {
            error("No suitable location for respawn anchor!");
            return;
        }

        int previousSlot = mc.player.getInventory().selectedSlot;

        // Place anchor
        mc.player.getInventory().selectedSlot = anchorSlot;
        BlockHitResult hitResult = new BlockHitResult(
            Vec3d.ofCenter(placePos),
            Direction.UP,
            placePos.down(),
            false
        );
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);

        // Charge with glowstone
        mc.player.getInventory().selectedSlot = glowstoneSlot;
        BlockHitResult anchorHitResult = new BlockHitResult(
            Vec3d.ofCenter(placePos),
            Direction.UP,
            placePos,
            false
        );
        
        // Charge 4 times for full charge
        for (int i = 0; i < 4; i++) {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, anchorHitResult);
        }

        // Click the anchor again to set respawn point (empty hand or any item)
        mc.player.getInventory().selectedSlot = previousSlot;
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, anchorHitResult);

        waitingForRespawnConfirm = true; // Wait for confirmation message
        lastRespawnPos = placePos;
        needsEnderChest = placeEnderChest.get();
        ticksSincePlace = 0;
        
        info("Placed, charged, and clicked respawn anchor: " + placePos.toShortString() + " - waiting for confirmation");
    }

    private boolean placeEnderChestNearby() {
        int chestSlot = findItemInHotbar(Items.ENDER_CHEST);
        if (chestSlot == -1) return false;

        BlockPos placePos = findPlacementLocation(mc.player.getBlockPos());
        if (placePos == null) return false;

        int previousSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = chestSlot;

        BlockHitResult hitResult = new BlockHitResult(
            Vec3d.ofCenter(placePos),
            Direction.UP,
            placePos.down(),
            false
        );

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        mc.player.getInventory().selectedSlot = previousSlot;

        return true;
    }

    private BlockPos findPlacementLocation(BlockPos origin) {
        int range = placementRange.get();
        
        // Search for a suitable block to place on
        for (int x = -range; x <= range; x++) {
            for (int z = -range; z <= range; z++) {
                for (int y = -2; y <= 2; y++) {
                    BlockPos checkPos = origin.add(x, y, z);
                    BlockPos abovePos = checkPos.up();
                    
                    // Check if block below is solid and space above is air
                    if (mc.world.getBlockState(checkPos).isSolidBlock(mc.world, checkPos) &&
                        mc.world.getBlockState(abovePos).isAir()) {
                        return abovePos;
                    }
                }
            }
        }
        
        return null;
    }

    private int findItemInHotbar(Item... items) {
        for (int i = 0; i < 9; i++) {
            Item slotItem = mc.player.getInventory().getStack(i).getItem();
            for (Item item : items) {
                if (slotItem == item) {
                    return i;
                }
            }
        }
        return -1;
    }

    private void sendWebhookNotification(BlockPos pos, String respawnType) {
        new Thread(() -> {
            try {
                info("Sending webhook notification for " + respawnType + " at " + pos.toShortString());
                String dimension = mc.world.getRegistryKey().getValue().toString();
                String emoji = respawnType.equals("Bed") ? "🛏️" : "⚓";
                
                String jsonPayload = String.format(
                    "{\"embeds\":[{\"title\":\"%s %s Placed\",\"description\":\"Position: %s\\nDimension: %s\\nType: %s\",\"color\":5814783,\"timestamp\":\"%s\"}]}",
                    emoji,
                    respawnType,
                    pos.toShortString(),
                    dimension,
                    respawnType,
                    java.time.Instant.now().toString()
                );

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl.get()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 204 || response.statusCode() == 200) {
                    info("Webhook notification sent successfully!");
                } else {
                    error("Webhook failed with status: " + response.statusCode());
                }
            } catch (Exception e) {
                error("Failed to send webhook: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public void onActivate() {
        waitingForRespawnConfirm = false;
        needsEnderChest = false;
        ticksSincePlace = 0;
    }

    @Override
    public void onDeactivate() {
        // Log distance traveled when AutoRespawn is disabled
        Logger loggerModule = meteordevelopment.meteorclient.systems.modules.Modules.get().get(Logger.class);
        
        if (loggerModule != null) {
            double totalDistance = loggerModule.getTotalDistanceTraveled();
            info("AutoRespawn disabled. Total distance traveled: " + String.format("%.2f", totalDistance) + " blocks");
            
            // Send to webhook if enabled
            if (notifyWebhook.get() && !webhookUrl.get().isEmpty()) {
                if (isValidWebhookUrl(webhookUrl.get())) {
                    sendDistanceWebhook(totalDistance);
                } else {
                    error("Invalid webhook URL format. Must start with https://");
                }
            }
        }
    }

    private boolean isValidWebhookUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        return url.startsWith("https://discord.com/api/webhooks/") || 
               url.startsWith("https://discordapp.com/api/webhooks/");
    }

    private void sendDistanceWebhook(double distance) {
        new Thread(() -> {
            try {
                String playerName = mc.getSession().getUsername();
                String coords = String.format("X: %.0f, Y: %.0f, Z: %.0f", 
                    mc.player.getX(), mc.player.getY(), mc.player.getZ());
                
                String jsonPayload = String.format(
                    "{\"content\":\"**AutoRespawn Disabled**\\n" +
                    "Player: %s\\n" +
                    "Total Distance Traveled: %.2f blocks\\n" +
                    "Current Position: %s\"}",
                    playerName, distance, coords
                );

                HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl.get()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 204 || response.statusCode() == 200) {
                    info("Distance webhook sent successfully!");
                } else {
                    error("Distance webhook failed with status: " + response.statusCode());
                }
            } catch (Exception e) {
                error("Failed to send distance webhook: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }
}
