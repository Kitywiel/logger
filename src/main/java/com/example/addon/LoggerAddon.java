package com.example.addon;

import com.example.addon.gui.EnemiesScreen;
import com.example.addon.hud.DistanceTraveled;
import com.example.addon.hud.LoggerInfo;
import com.example.addon.hud.RespawnInfo;
import com.example.addon.modules.AutoLog;
import com.example.addon.modules.AutoRespawn;
import com.example.addon.modules.AutoUnload;
import com.example.addon.modules.EnemyAdder;
import com.example.addon.modules.Logger;
import com.example.addon.systems.Enemies;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Items;

public class LoggerAddon extends MeteorAddon {
    public static final org.slf4j.Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("kitywiel's", Items.WRITABLE_BOOK.getDefaultStack());
    public static final HudGroup HUD_GROUP = new HudGroup("kitywiel's");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Logger Addon");

        // Register systems
        Systems.add(new Enemies());

        // Register modules
        Modules.get().add(new Logger());
        Modules.get().add(new AutoLog());
    Modules.get().add(new AutoRespawn());
    Modules.get().add(new AutoUnload());
    Modules.get().add(new EnemyAdder());

        // Register HUD elements
        Hud.get().register(LoggerInfo.INFO);
        Hud.get().register(RespawnInfo.INFO);
        Hud.get().register(DistanceTraveled.INFO);

        // Add Enemies tab
        Tabs.add(new Tab("Enemies", Items.IRON_SWORD.getDefaultStack(), () -> new EnemiesTab()));
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }

    // Enemies Tab
    private static class EnemiesTab extends TabScreen {
        public EnemiesTab() {
            super("Enemies");
        }

        @Override
        protected void init() {
            super.init();
            client.setScreen(new EnemiesScreen(theme));
        }
    }
}
