package com.example.addon.hud;

import com.example.addon.LoggerAddon;
import com.example.addon.modules.AutoRespawn;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.util.math.BlockPos;

public class RespawnInfo extends HudElement {
    public static final HudElementInfo<RespawnInfo> INFO = new HudElementInfo<>(
        LoggerAddon.HUD_GROUP,
        "respawn-info",
        "Displays distance until next respawn placement.",
        false
    );

    public RespawnInfo() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        setSize(renderer.textWidth("Next Respawn: 00000 blocks"), renderer.textHeight());

        AutoRespawn autoRespawn = Modules.get().get(AutoRespawn.class);
        
        if (autoRespawn == null || !autoRespawn.isActive()) {
            renderer.text("AutoRespawn: Disabled", x, y, Color.GRAY);
            return;
        }

        if (mc.player == null) {
            renderer.text("AutoRespawn: No Player", x, y, Color.GRAY);
            return;
        }

        BlockPos lastRespawn = autoRespawn.getLastRespawnPos();
        BlockPos currentPos = mc.player.getBlockPos();
        int respawnDistance = autoRespawn.getRespawnDistance() * 1000; // Multiply by 1000
        
        if (lastRespawn == null) {
            renderer.text("Next Respawn: Ready", x, y, Color.GREEN);
            return;
        }

        // Calculate distance from last respawn
        double distance = Math.sqrt(currentPos.getSquaredDistance(lastRespawn));
        int distanceRemaining = respawnDistance - (int) distance;
        
        if (distanceRemaining <= 0) {
            renderer.text("Next Respawn: Ready", x, y, Color.GREEN);
            return;
        }
        
        // Color based on distance remaining
        Color color;
        if (distanceRemaining < 500) {
            color = Color.RED;
        } else if (distanceRemaining < 1000) {
            color = Color.ORANGE;
        } else {
            color = Color.GREEN;
        }
        
        renderer.text("Next Respawn: " + distanceRemaining + " blocks", x, y, color);
    }
}
