package com.example.addon.hud;

import com.example.addon.LoggerAddon;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import com.example.addon.modules.Logger;

public class LoggerInfo extends HudElement {
    public static final HudElementInfo<LoggerInfo> INFO = new HudElementInfo<>(
        LoggerAddon.HUD_GROUP,
        "logger-info",
        "Displays time until next Logger update.",
        LoggerInfo::new
    );

    public LoggerInfo() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        setSize(renderer.textWidth("Next Logger Update: 00:00"), renderer.textHeight());

        Logger logger = Modules.get().get(Logger.class);
        
        if (logger == null || !logger.isActive()) {
            renderer.text("Logger: Disabled", (double) x, (double) y, Color.GRAY, true);
            return;
        }

        // Get time until next log
        double intervalMinutes = logger.getLogInterval();
        int ticksPerInterval = (int) (intervalMinutes * 1200); // 1200 ticks = 1 minute
        int ticksPassed = logger.getTickCounter();
        int ticksRemaining = ticksPerInterval - ticksPassed;
        
        if (ticksRemaining < 0) ticksRemaining = 0;
        
        // Convert to seconds
        int secondsRemaining = ticksRemaining / 20;
        int minutes = secondsRemaining / 60;
        int seconds = secondsRemaining % 60;
        
        String timeStr = String.format("%02d:%02d", minutes, seconds);
        
        // Color based on time remaining
        Color color;
        if (secondsRemaining < 30) {
            color = Color.RED;
        } else if (secondsRemaining < 60) {
            color = Color.ORANGE;
        } else {
            color = Color.GREEN;
        }
        
        renderer.text("Next Logger: " + timeStr, (double) x, (double) y, color, true);
    }
}
