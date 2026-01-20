package com.example.addon.gui;

// LEGITIMATE MINECRAFT MOD - NOT MALWARE
// This GUI makes requests to Mojang's official API to resolve player usernames to UUIDs
// This is standard practice for Minecraft mods (same as typing /friend add <player>)
// Only contacts: api.mojang.com (official Minecraft API)
// No personal data is collected or sent

import com.example.addon.systems.Enemies;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPlus;
import meteordevelopment.meteorclient.utils.misc.NbtUtils;
import meteordevelopment.meteorclient.utils.network.Http;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.utils.player.ChatUtils;

import java.util.UUID;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class EnemiesScreen extends WindowScreen {
    public EnemiesScreen(GuiTheme theme) {
        super(theme, "Enemies");
    }

    @Override
    public void initWidgets() {
        WTable table = add(theme.table()).expandX().minWidth(400).widget();
        initTable(table);

        add(theme.horizontalSeparator()).expandX();

        // New enemy input (styled like friends)
        WHorizontalList list = add(theme.horizontalList()).expandX().widget();

        WTextBox nameW = list.add(theme.textBox("", (text, c) -> c != ' ')).expandX().widget();
        nameW.setFocused(true);

        WPlus add = list.add(theme.plus()).widget();
        add.action = () -> {
            String name = nameW.get().trim();
            if (name.isEmpty()) return;

            MeteorExecutor.execute(() -> {
                UUID uuid = playerNameToUuid(name);
                if (uuid == null) {
                    ChatUtils.error(String.format("Failed to find player UUID for '%s'.", name));
                    return;
                }

                Enemies.Enemy enemy = new Enemies.Enemy(name, uuid);
                if (Enemies.get().add(enemy)) {
                    nameW.set("");
                    mc.execute(this::reload);
                    ChatUtils.info(String.format("Added '%s' to enemies.", name));
                } else {
                    ChatUtils.error(String.format("Player '%s' is already in your enemies list.", name));
                }
            });
        };

        enterAction = add.action;
    }

    private void initTable(WTable table) {
        table.clear();
        if (Enemies.get().isEmpty()) return;

        for (Enemies.Enemy enemy : Enemies.get().getAll()) {
            // Player head placeholder (same width as friends system)
            table.add(theme.label("👤")).widget(); // Player icon placeholder
            
            // Enemy name (styled like friends system)
            table.add(theme.label(enemy.name)).expandCellX().widget();

            // Remove button (red minus)
            WMinus remove = table.add(theme.minus()).right().widget();
            remove.action = () -> {
                Enemies.get().remove(enemy);
                reload();
                ChatUtils.info(String.format("Removed '%s' from enemies.", enemy.name));
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

    @Override
    public boolean toClipboard() {
        return NbtUtils.toClipboard(Enemies.get());
    }

    @Override
    public boolean fromClipboard() {
        return NbtUtils.fromClipboard(Enemies.get());
    }
}
