package com.example.addon.modules;

import com.example.addon.LoggerAddon;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * AutoUnload module (scaffold)
 *
 * Purpose:
 * - Move a selected group of items from inventory into shulker boxes
 * - Optionally craft items into blocks (9 -> block) using a crafting table
 * - Pause Baritone (or other modules) while filling/crafting
 *
 * Implementation notes:
 * - This is a safe initial implementation that provides settings and the
 *   pause/resume logic. Actual inventory manipulation and crafting code are
 *   intentionally left as TODOs and must be implemented carefully because
 *   GUI/container interactions are game-state sensitive.
 */
public class AutoUnload extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgItems = settings.createGroup("Items");
    private final SettingGroup sgBehavior = settings.createGroup("Behavior");

    private final Setting<Boolean> enabledByDefault = sgGeneral.add(new BoolSetting.Builder()
        .name("enabled-by-default")
        .description("If true the module will run when toggled on. Default: false")
        .defaultValue(false)
        .build()
    );

    // A simple textual slot selector (e.g. "0,1,2-8") for the initial version.
    // Future UI can provide a 3x9 grid. This is safer than adding many boolean settings now.
    private final Setting<String> protectedSlots = sgBehavior.add(new StringSetting.Builder()
        .name("protected-slots")
        .description("Comma-separated slot indices or ranges to PROTECT from being moved/thrown (e.g. \"0-8,18,26\"). Use 0..35 for main inventory, 36..44 for hotbar")
        .defaultValue("")
        .build()
    );

    private final Setting<Boolean> pauseBaritone = sgBehavior.add(new BoolSetting.Builder()
        .name("pause-baritone")
        .description("Pause configured modules (Baritone, movement) while performing unload/craft operations")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> craftBlocks = sgBehavior.add(new BoolSetting.Builder()
        .name("craft-blocks")
        .description("Automatically craft supported items into blocks (9 -> block) when possible")
        .defaultValue(true)
        .build()
    );

    // Which item families to craft into blocks. Keep names user-facing.
    private final Setting<Boolean> craftCoal = sgItems.add(new BoolSetting.Builder().name("coal").defaultValue(true).build());
    private final Setting<Boolean> craftRawIron = sgItems.add(new BoolSetting.Builder().name("raw-iron").defaultValue(true).build());
    private final Setting<Boolean> craftIron = sgItems.add(new BoolSetting.Builder().name("iron").defaultValue(true).build());
    private final Setting<Boolean> craftRawCopper = sgItems.add(new BoolSetting.Builder().name("raw-copper").defaultValue(true).build());
    private final Setting<Boolean> craftCopper = sgItems.add(new BoolSetting.Builder().name("copper").defaultValue(true).build());
    private final Setting<Boolean> craftRawGold = sgItems.add(new BoolSetting.Builder().name("raw-gold").defaultValue(true).build());
    private final Setting<Boolean> craftGold = sgItems.add(new BoolSetting.Builder().name("gold").defaultValue(true).build());
    private final Setting<Boolean> craftDiamond = sgItems.add(new BoolSetting.Builder().name("diamond").defaultValue(true).build());
    private final Setting<Boolean> craftRedstone = sgItems.add(new BoolSetting.Builder().name("redstone").defaultValue(true).build());
    private final Setting<Boolean> craftEmerald = sgItems.add(new BoolSetting.Builder().name("emerald").defaultValue(true).build());

    // Modules to disable while operating (Baritone / AutoRespawn etc.)
    private final Setting<List<Module>> modulesToDisable = sgBehavior.add(new ModuleListSetting.Builder()
        .name("modules-to-disable")
        .description("Modules to disable while AutoUnload is operating (e.g., Baritone)")
        .build()
    );

    private final List<Module> disabledModules = new ArrayList<>();
    private boolean isOperating = false;

    public AutoUnload() {
        super(LoggerAddon.CATEGORY, "auto-unload", "Unload/craft selected items into shulkers and blocks, pausing Baritone while running.");
    }

    @Override
    public void onActivate() {
        if (pauseBaritone.get()) {
            // Disable configured modules to pause Baritone/movement
            disabledModules.clear();
            for (Module m : modulesToDisable.get()) {
                if (m != null && m.isActive()) {
                    m.toggle();
                    disabledModules.add(m);
                }
            }
        }

        isOperating = true;
        info("AutoUnload started. Scanning inventory...");
    }

    @Override
    public void onDeactivate() {
        // Re-enable any disabled modules
        for (Module module : disabledModules) {
            if (!module.isActive()) module.toggle();
        }
        disabledModules.clear();
        isOperating = false;
        info("AutoUnload stopped.");
    }
}
