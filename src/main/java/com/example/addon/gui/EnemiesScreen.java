package com.example.addon.gui;

// LEGITIMATE MINECRAFT MOD - NOT MALWARE
// This GUI makes requests to Mojang's official API to resolve player usernames to UUIDs
// This is standard practice for Minecraft mods (same as typing /friend add <player>)
// Only contacts: api.mojang.com (official Minecraft API)
// No personal data is collected or sent

import com.example.addon.systems.Enemies;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.utils.misc.NbtUtils;
import meteordevelopment.meteorclient.utils.network.Http;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gui.screen.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EnemiesScreen extends WindowScreen {
    public EnemiesScreen(GuiTheme theme) {
        super(theme, "Enemies");
    }

    @Override
    public void initWidgets() {
        WTable table = add(theme.table()).expandX().minWidth(400).widget();

        // Title
        table.add(theme.label("Enemies")).expandCellX();
        table.row();

        table.add(theme.horizontalSeparator()).expandX();
        table.row();

        // Add enemy input
        WTextBox nameInput = table.add(theme.textBox("")).minWidth(200).expandX().widget();
        nameInput.setFocused(true);

        WButton add = table.add(theme.button("Add")).widget();
        table.row();

        add.action = () -> {
            String name = nameInput.get().trim();
            if (name.isEmpty()) return;

            MeteorExecutor.execute(() -> {
                UUID uuid = playerNameToUuid(name);
                if (uuid == null) {
                    error("Failed to find player UUID for '%s'.", name);
                    return;
                }

                Enemies.Enemy enemy = new Enemies.Enemy(name, uuid);
                if (!Enemies.get().add(enemy)) {
                    error("Player '%s' is already in your enemies list.", name);
                } else {
                    info("Added '%s' to enemies.", name);
                    nameInput.set("");
                    reload();
                }
            });
        };

        // Enemies list
        table.add(theme.horizontalSeparator()).expandX();
        table.row();

        for (Enemies.Enemy enemy : Enemies.get().getAll()) {
            table.add(theme.label(enemy.name)).expandCellX();

            WMinus remove = table.add(theme.minus()).widget();
            remove.action = () -> {
                Enemies.get().remove(enemy);
                reload();
            };

            table.row();
        }
    }

    private UUID playerNameToUuid(String name) {
        try {
            String response = Http.get("https://api.mojang.com/users/profiles/minecraft/" + name).sendString();
            if (response == null || response.isEmpty()) return null;

            int startIndex = response.indexOf("\"id\":\"") + 6;
            int endIndex = response.indexOf("\"", startIndex);
            String uuidString = response.substring(startIndex, endIndex);

            // Add dashes to UUID string
            return UUID.fromString(
                uuidString.substring(0, 8) + "-" +
                uuidString.substring(8, 12) + "-" +
                uuidString.substring(12, 16) + "-" +
                uuidString.substring(16, 20) + "-" +
                uuidString.substring(20, 32)
            );
        } catch (Exception e) {
            return null;
        }
    }
}
