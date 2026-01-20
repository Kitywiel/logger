package com.example.addon.hud;

import com.example.addon.LoggerAddon;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public class DistanceTraveled extends HudElement {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    public static final HudElementInfo<DistanceTraveled> INFO = new HudElementInfo<>(
        LoggerAddon.HUD_GROUP,
        "distance-traveled",
        "Displays total distance traveled in this session.",
        DistanceTraveled::new
    );

    private double totalDistance = 0.0;
    private Vec3d lastPos = null;
    private boolean hasStarted = false;

    public DistanceTraveled() {
        super(INFO);
    }

    @Override
    public void tick(HudRenderer renderer) {
        super.tick(renderer);

        if (mc.player == null) {
            lastPos = null;
            hasStarted = false;
            return;
        }

        Vec3d currentPos = mc.player.getPos();

        if (!hasStarted) {
            lastPos = currentPos;
            hasStarted = true;
            return;
        }

        if (lastPos != null) {
            double distance = currentPos.distanceTo(lastPos);
            
            // Only add distance if it's reasonable (prevent teleport from adding huge distances)
            if (distance < 100) {
                totalDistance += distance;
            }
            
            lastPos = currentPos;
        }
    }

    @Override
    public void render(HudRenderer renderer) {
        setSize(renderer.textWidth("Distance: 999,999.9m"), renderer.textHeight());

        if (mc.player == null) {
            renderer.text("Distance: 0.0m", (double) x, (double) y, Color.GRAY, true);
            return;
        }

        String distanceStr;
        Color color;

        if (totalDistance >= 1000000) {
            // Display in kilometers if over 1 million meters
            distanceStr = String.format("Distance: %,.1fkm", totalDistance / 1000);
            color = Color.MAGENTA;
        } else if (totalDistance >= 10000) {
            // Display in kilometers if over 10k meters
            distanceStr = String.format("Distance: %,.1fkm", totalDistance / 1000);
            color = Color.BLUE;
        } else if (totalDistance >= 1000) {
            distanceStr = String.format("Distance: %,.1fm", totalDistance);
            color = Color.GREEN;
        } else {
            distanceStr = String.format("Distance: %.1fm", totalDistance);
            color = Color.WHITE;
        }

        renderer.text(distanceStr, (double) x, (double) y, color, true);
    }

    // Reset on world change - removed @Override as method signature changed
    public void onActivate() {
        totalDistance = 0.0;
        lastPos = null;
        hasStarted = false;
    }

    // Public method to reset distance
    public void reset() {
        totalDistance = 0.0;
        lastPos = null;
        hasStarted = false;
    }

    // Public method to get total distance
    public double getTotalDistance() {
        return totalDistance;
    }
}
