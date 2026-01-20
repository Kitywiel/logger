package com.example.addon.modules;

import com.example.addon.LoggerAddon;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.text.Text;
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
import java.util.ArrayList;
import java.util.List;

public class AutoRespawn extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWebhook = settings.createGroup("Webhook");
    private final SettingGroup sgHighway = settings.createGroup("Highway");

    public enum Mode {
        Overworld,
        Nether
    }

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Overworld (bed) or Nether (respawn anchor) mode.")
        .defaultValue(Mode.Nether)
        .build()
    );

    private final Setting<Integer> respawnDistance = sgGeneral.add(new IntSetting.Builder()
        .name("respawn-distance")
        .description("Distance in thousands of blocks before setting new respawn.")
        .defaultValue(1)
        .min(1)
        .sliderMax(1000)
        .build()
    );

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("How far from the highway to place respawn point.")
        .defaultValue(5)
        .min(0)
        .sliderMax(1000)
        .build()
    );

    private final Setting<String> webhookUrl = sgWebhook.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL to send notifications to.")
        .defaultValue("")
        .build()
    );

    private final Setting<Boolean> notifyOnPlace = sgWebhook.add(new BoolSetting.Builder()
        .name("notify-on-place")
        .description("Send Discord notification when respawn point is placed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> notifyOnStart = sgWebhook.add(new BoolSetting.Builder()
        .name("notify-on-start")
        .description("Send Discord notification when starting to place respawn.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> highwayMode = sgHighway.add(new BoolSetting.Builder()
        .name("highway-mode")
        .description("Use Baritone to tunnel and place respawn points automatically.")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<Class<? extends Module>>> modulesToDisable = sgHighway.add(new ModuleListSetting.Builder()
        .name("disable-modules")
        .description("Modules to disable while traveling to place respawn.")
        .build()
    );

    private BlockPos lastRespawnPos = null;
    private BlockPos startPos = null;
    private boolean isPlacingRespawn = false;
    private boolean waitingForMessage = false;
    private boolean returningHome = false;
    private int messageWaitTicks = 0;
    private int retryCount = 0;
    private static final int MAX_RETRIES = 3;
    private static final int MESSAGE_TIMEOUT = 60; // 3 seconds
    private List<Module> disabledModules = new ArrayList<>();
    private BlockPos targetPlacementPos = null;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    public AutoRespawn() {
        super(LoggerAddon.CATEGORY, "auto-respawn", "Automatically places respawn points along highways.");
    }

    // Public getters for HUD
    public BlockPos getLastRespawnPos() {
        return lastRespawnPos;
    }

    public int getRespawnDistance() {
        return respawnDistance.get();
    }

    @Override
    public void onActivate() {
        lastRespawnPos = null;
        isPlacingRespawn = false;
        waitingForMessage = false;
        returningHome = false;
        retryCount = 0;
        disabledModules.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // Handle message timeout
        if (waitingForMessage) {
            messageWaitTicks++;
            if (messageWaitTicks >= MESSAGE_TIMEOUT) {
                warning("Respawn message not received, retrying...");
                waitingForMessage = false;
                retryCount++;
                if (retryCount >= MAX_RETRIES) {
                    error("Failed to set respawn after " + MAX_RETRIES + " attempts");
                    finishPlacement();
                } else {
                    // Retry placement
                    attemptPlacement();
                }
            }
            return;
        }

        // Check if we need to place new respawn
        if (!isPlacingRespawn && shouldPlaceRespawn()) {
            startPlacement();
        }

        // Handle highway mode baritone movement
        if (isPlacingRespawn && highwayMode.get()) {
            handleBaritoneMovement();
        }

        // Check if we've arrived back home
        if (returningHome && highwayMode.get() && startPos != null) {
            if (mc.player.getBlockPos().isWithinDistance(startPos, 2.0)) {
                returningHome = false;
                
                // Re-enable modules now that we're back
                for (Module module : disabledModules) {
                    if (!module.isActive()) {
                        module.toggle();
                    }
                }
                disabledModules.clear();
                
                info("Returned to start position, modules re-enabled!");
            }
        }
    }

    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        if (!waitingForMessage) return;

        String message = event.getMessage().getString().toLowerCase();
        
        // Check for respawn point set message
        if (message.contains("respawn point set") || 
            message.contains("spawn point set") ||
            message.contains("bed") && message.contains("set")) {
            
            info("Respawn point set successfully!");
            waitingForMessage = false;
            retryCount = 0;
            lastRespawnPos = mc.player.getBlockPos();
            
            // Send success notification
            if (notifyOnPlace.get() && !webhookUrl.get().isEmpty()) {
                BlockPos pos = mc.player.getBlockPos();
                String dimension = mc.world.getRegistryKey().getValue().getPath();
                sendDiscordNotification("✅ Respawn Point Set Successfully", 
                    String.format("Mode: %s\nLocation: %d, %d, %d\nDimension: %s\nDistance from last: %.0f blocks",
                        mode.get().toString(),
                        pos.getX(), pos.getY(), pos.getZ(),
                        dimension,
                        startPos != null ? Math.sqrt(pos.getSquaredDistance(startPos)) : 0.0),
                    0x00FF00); // Green color
            }
            
            // Place ender chest
            placeEnderChest();
            
            // Finish up
            finishPlacement();
        }
    }

    private boolean shouldPlaceRespawn() {
        if (mc.player == null) return false;

        BlockPos currentPos = mc.player.getBlockPos();
        
        if (lastRespawnPos == null) {
            return true; // First respawn
        }

        // Check distance (multiply by 1000 as per specification)
        double distance = Math.sqrt(currentPos.getSquaredDistance(lastRespawnPos));
        return distance >= (respawnDistance.get() * 1000);
    }

    private void startPlacement() {
        isPlacingRespawn = true;
        startPos = mc.player.getBlockPos();
        
        info("Starting respawn placement...");

        // Send start notification
        if (notifyOnStart.get() && !webhookUrl.get().isEmpty()) {
            sendDiscordNotification("Starting Respawn Placement", 
                String.format("Mode: %s\nCurrent Position: %d, %d, %d\nTarget Range: %d blocks from highway",
                    mode.get().toString(),
                    startPos.getX(), startPos.getY(), startPos.getZ(),
                    range.get()),
                0xFFA500); // Orange color
        }

        if (highwayMode.get()) {
            // Disable specified modules
            for (Class<? extends Module> moduleClass : modulesToDisable.get()) {
                Module module = Modules.get().get(moduleClass);
                if (module != null && module.isActive()) {
                    module.toggle();
                    disabledModules.add(module);
                }
            }

            // Calculate target position off the highway
            calculateTargetPosition();
            
            // Use Baritone to path to target
            useBaritone();
        } else {
            // Manual mode - just try to place at current location
            attemptPlacement();
        }
    }

    private void calculateTargetPosition() {
        BlockPos playerPos = mc.player.getBlockPos();
        
        // Determine if we're on a highway (X or Z axis aligned)
        boolean onXHighway = Math.abs(playerPos.getZ()) < 10;
        boolean onZHighway = Math.abs(playerPos.getX()) < 10;
        
        if (onXHighway) {
            // Move in Z direction
            targetPlacementPos = playerPos.add(0, 0, range.get());
        } else if (onZHighway) {
            // Move in X direction
            targetPlacementPos = playerPos.add(range.get(), 0, 0);
        } else {
            // Not on main highway, just offset
            targetPlacementPos = playerPos.add(range.get(), 0, 0);
        }
    }

    private void useBaritone() {
        try {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            baritone.getCustomGoalProcess().setGoalAndPath(
                new baritone.api.pathing.goals.GoalBlock(targetPlacementPos)
            );
        } catch (Exception e) {
            error("Failed to use Baritone: " + e.getMessage());
            finishPlacement();
        }
    }

    private void handleBaritoneMovement() {
        if (targetPlacementPos == null) return;

        // Check if we've arrived at target
        if (mc.player.getBlockPos().isWithinDistance(targetPlacementPos, 2.0)) {
            try {
                IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
                baritone.getPathingBehavior().cancelEverything();
            } catch (Exception ignored) {}

            // Attempt placement
            attemptPlacement();
        }
    }

    private void attemptPlacement() {
        if (mode.get() == Mode.Overworld) {
            placeBed();
        } else {
            placeRespawnAnchor();
        }
    }

    private void placeBed() {
        // Find bed in inventory
        int bedSlot = findItemInHotbar(Items.RED_BED, Items.BLUE_BED, Items.BLACK_BED, 
                                       Items.WHITE_BED, Items.YELLOW_BED, Items.GREEN_BED);
        
        if (bedSlot == -1) {
            error("No bed found in hotbar!");
            finishPlacement();
            return;
        }

        // Switch to bed
        mc.player.getInventory().selectedSlot = bedSlot;

        // Find suitable position to place bed
        BlockPos placePos = findSuitablePlacePosition();
        if (placePos == null) {
            error("No suitable position to place bed!");
            finishPlacement();
            return;
        }

        // Place bed
        placeBlock(placePos, Blocks.RED_BED.getDefaultState());

        // Click on bed to set spawn
        new Thread(() -> {
            try {
                Thread.sleep(500); // Wait for bed to be placed
                interactWithBlock(placePos);
                waitingForMessage = true;
                messageWaitTicks = 0;
            } catch (InterruptedException e) {
                error("Interrupted while placing bed");
            }
        }).start();
    }

    private void placeRespawnAnchor() {
        // Find respawn anchor in inventory
        int anchorSlot = findItemInHotbar(Items.RESPAWN_ANCHOR);
        
        if (anchorSlot == -1) {
            error("No respawn anchor found in hotbar!");
            finishPlacement();
            return;
        }

        // Find glowstone
        int glowstoneSlot = findItemInHotbar(Items.GLOWSTONE);
        if (glowstoneSlot == -1) {
            error("No glowstone found in hotbar!");
            finishPlacement();
            return;
        }

        // Switch to anchor
        mc.player.getInventory().selectedSlot = anchorSlot;

        // Find suitable position
        BlockPos placePos = findSuitablePlacePosition();
        if (placePos == null) {
            error("No suitable position to place respawn anchor!");
            finishPlacement();
            return;
        }

        // Place anchor
        placeBlock(placePos, Blocks.RESPAWN_ANCHOR.getDefaultState());

        // Charge with glowstone and set spawn
        new Thread(() -> {
            try {
                Thread.sleep(300);
                
                // Switch to glowstone
                mc.player.getInventory().selectedSlot = glowstoneSlot;
                Thread.sleep(100);
                
                // Right-click twice to charge
                interactWithBlock(placePos);
                Thread.sleep(300);
                interactWithBlock(placePos);
                Thread.sleep(300);
                
                // Switch to any other item (not glowstone)
                int otherSlot = findItemInHotbar();
                if (otherSlot != -1) {
                    mc.player.getInventory().selectedSlot = otherSlot;
                    Thread.sleep(100);
                    
                    // Right-click to set spawn
                    interactWithBlock(placePos);
                    waitingForMessage = true;
                    messageWaitTicks = 0;
                }
            } catch (InterruptedException e) {
                error("Interrupted while placing respawn anchor");
            }
        }).start();
    }

    private void placeEnderChest() {
        int chestSlot = findItemInHotbar(Items.ENDER_CHEST);
        if (chestSlot == -1) {
            warning("No ender chest found in hotbar");
            return;
        }

        mc.player.getInventory().selectedSlot = chestSlot;

        BlockPos chestPos = findSuitablePlacePosition();
        if (chestPos != null) {
            placeBlock(chestPos, Blocks.ENDER_CHEST.getDefaultState());
            info("Placed ender chest");
        }
    }

    private BlockPos findSuitablePlacePosition() {
        BlockPos playerPos = mc.player.getBlockPos();
        
        // Try positions around player
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = -1; y <= 1; y++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (mc.world.getBlockState(pos).isAir() && 
                        !mc.world.getBlockState(pos.down()).isAir()) {
                        return pos;
                    }
                }
            }
        }
        
        return null;
    }

    private void placeBlock(BlockPos pos, net.minecraft.block.BlockState state) {
        // This is a simplified version - you'd want more robust placement
        mc.interactionManager.interactBlock(
            mc.player,
            Hand.MAIN_HAND,
            new BlockHitResult(
                Vec3d.ofCenter(pos),
                Direction.UP,
                pos,
                false
            )
        );
    }

    private void interactWithBlock(BlockPos pos) {
        mc.interactionManager.interactBlock(
            mc.player,
            Hand.MAIN_HAND,
            new BlockHitResult(
                Vec3d.ofCenter(pos),
                Direction.UP,
                pos,
                false
            )
        );
    }

    private int findItemInHotbar(net.minecraft.item.Item... items) {
        for (int i = 0; i < 9; i++) {
            for (net.minecraft.item.Item item : items) {
                if (mc.player.getInventory().getStack(i).getItem() == item) {
                    return i;
                }
            }
        }
        return -1;
    }

    private int findItemInHotbar() {
        // Find any non-empty slot that's not glowstone
        for (int i = 0; i < 9; i++) {
            if (!mc.player.getInventory().getStack(i).isEmpty() &&
                mc.player.getInventory().getStack(i).getItem() != Items.GLOWSTONE) {
                return i;
            }
        }
        return 0; // Return first slot as fallback
    }

    private void finishPlacement() {
        isPlacingRespawn = false;
        waitingForMessage = false;
        targetPlacementPos = null;

        // Return to start position if highway mode
        if (highwayMode.get() && startPos != null) {
            try {
                IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
                baritone.getCustomGoalProcess().setGoalAndPath(
                    new baritone.api.pathing.goals.GoalBlock(startPos)
                );
                returningHome = true;
                info("Respawn placement complete! Returning to start position...");
            } catch (Exception e) {
                warning("Failed to return to start position: " + e.getMessage());
                // Re-enable modules if we can't return
                for (Module module : disabledModules) {
                    if (!module.isActive()) {
                        module.toggle();
                    }
                }
                disabledModules.clear();
            }
        } else {
            // Not in highway mode, re-enable modules immediately
            for (Module module : disabledModules) {
                if (!module.isActive()) {
                    module.toggle();
                }
            }
            disabledModules.clear();
            info("Respawn placement complete!");
        }
    }

    @Override
    public void onDeactivate() {
        // Re-enable any disabled modules
        for (Module module : disabledModules) {
            if (!module.isActive()) {
                module.toggle();
            }
        }
        disabledModules.clear();

        // Cancel baritone
        if (highwayMode.get()) {
            try {
                IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
                baritone.getPathingBehavior().cancelEverything();
            } catch (Exception ignored) {}
        }
    }

    private void sendDiscordNotification(String title, String description, int color) {
        new Thread(() -> {
            try {
                String jsonPayload = String.format(
                    "{\"embeds\":[{\"title\":\"%s\",\"description\":\"%s\",\"color\":%d,\"timestamp\":\"%s\",\"footer\":{\"text\":\"Kitywiel's Auto Respawn\"}}]}", 
                    title.replace("\"", "\\\""),
                    description.replace("\"", "\\\"").replace("\n", "\\n"),
                    color,
                    java.time.Instant.now().toString()
                );

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl.get()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 204 || response.statusCode() == 200) {
                    info("Discord notification sent successfully!");
                } else {
                    warning("Failed to send Discord notification. Status code: " + response.statusCode());
                }
            } catch (Exception e) {
                error("Error sending Discord notification: " + e.getMessage());
            }
        }).start();
    }
}
