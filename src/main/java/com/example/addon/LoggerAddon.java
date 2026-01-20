package com.example.addon;

import com.example.addon.gui.EnemiesScreen;
import com.example.addon.hud.DistanceTraveled;
import com.example.addon.hud.LoggerInfo;
import com.example.addon.hud.RespawnInfo;
import com.example.addon.modules.*;
import com.example.addon.systems.Enemies;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPlus;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.network.Http;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class LoggerAddon extends MeteorAddon {
    public static final Logger LOG = LoggerFactory.getLogger("Logger Addon");
    public static final Category CATEGORY = new Category("Kitywiel's", Items.WRITABLE_BOOK.getDefaultStack());
    public static final HudGroup HUD_GROUP = new HudGroup("Kitywiel's");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Kitywiel's Logger Addon");

        // Initialize Enemies system
        Enemies.get();

        // Register all modules
        Modules.get().add(new com.example.addon.modules.Logger());
        Modules.get().add(new AutoLog());
        Modules.get().add(new AutoRespawn());
        Modules.get().add(new AutoUnload());
        Modules.get().add(new EnemyAdder());

        // Register HUD elements
        Hud.get().register(LoggerInfo.INFO);
        Hud.get().register(RespawnInfo.INFO);
        Hud.get().register(DistanceTraveled.INFO);

        // Add Enemies tab
        Tabs.add(new EnemiesTab());

        LOG.info("Kitywiel's Logger Addon initialized successfully - 5 modules, 3 HUD elements, 1 tab");
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("kitywiel", "logger-addon");
    }

    @Override
    public String getCommit() {
        return null;
    }

    // Enemies Tab implementation
    private static class EnemiesTab extends Tab {
        public EnemiesTab() {
            super("Enemies");
        }

        @Override
        public TabScreen createScreen(GuiTheme theme) {
            return new EnemiesTabScreen(theme, this);
        }

        @Override
        public boolean isScreen(net.minecraft.client.gui.screen.Screen screen) {
            return screen instanceof EnemiesTabScreen;
        }
    }

    private static class EnemiesTabScreen extends TabScreen {
        public EnemiesTabScreen(GuiTheme theme, Tab tab) {
            super(theme, tab);
        }

        @Override
        public void initWidgets() {
            // Replicate the EnemiesScreen functionality here using the official friends system style
            WTable table = add(theme.table()).expandX().minWidth(400).widget();

            // Add enemy input (like friends system with + button)
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

            // Enemies list table
            initEnemiesTable(table);
        }

        private void initEnemiesTable(WTable table) {
            table.clear();
            if (Enemies.get().isEmpty()) return;

            for (Enemies.Enemy enemy : Enemies.get().getAll()) {
                table.add(theme.label(enemy.name)).expandCellX();

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
}
