package com.example.addon.modules;

import com.example.addon.LoggerAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
        .description("Logs distance moved since last update.")
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

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

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

        if (logDistance.get() && hasLastPosition) {
            double distance = Math.sqrt(
                Math.pow(currentX - lastX, 2) + 
                Math.pow(currentY - lastY, 2) + 
                Math.pow(currentZ - lastZ, 2)
            );
            if (!first) fields.append(",");
            fields.append(String.format("{\"name\":\"🚶 Distance Moved\",\"value\":\"%.1f blocks\",\"inline\":true}", distance));
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
        }).start();
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
    }
}
