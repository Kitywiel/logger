package com.example.addon.modules;

import com.example.addon.LoggerAddon;
import com.example.addon.systems.Enemies;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public class EnemyAdder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Keybind> addEnemyKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("add-enemy-keybind")
        .description("Press this key while looking at a player to add them as an enemy.")
        .defaultValue(Keybind.none())
        .action(() -> addEnemyFromCrosshair())
        .build()
    );

    private final Setting<Keybind> removeEnemyKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("remove-enemy-keybind")
        .description("Press this key while looking at a player to remove them from enemies.")
        .defaultValue(Keybind.none())
        .action(() -> removeEnemyFromCrosshair())
        .build()
    );

    public EnemyAdder() {
        super(LoggerAddon.CATEGORY, "enemy-adder", "Add/remove enemies using keybinds while looking at players.");
    }

    private void addEnemyFromCrosshair() {
        if (mc.player == null || mc.world == null) return;

        HitResult hitResult = mc.crosshairTarget;
        if (hitResult == null || hitResult.getType() != HitResult.Type.ENTITY) {
            warning("You must be looking at a player!");
            return;
        }

        Entity entity = ((EntityHitResult) hitResult).getEntity();
        if (!(entity instanceof PlayerEntity player)) {
            warning("You must be looking at a player!");
            return;
        }

        if (player == mc.player) {
            warning("You cannot add yourself as an enemy!");
            return;
        }

        Enemies.Enemy enemy = new Enemies.Enemy(player.getName().getString(), player.getUuid());
        
        if (Enemies.get().add(enemy)) {
            info("Added §c" + player.getName().getString() + "§r to enemies!");
        } else {
            warning(player.getName().getString() + " is already in your enemies list!");
        }
    }

    private void removeEnemyFromCrosshair() {
        if (mc.player == null || mc.world == null) return;

        HitResult hitResult = mc.crosshairTarget;
        if (hitResult == null || hitResult.getType() != HitResult.Type.ENTITY) {
            warning("You must be looking at a player!");
            return;
        }

        Entity entity = ((EntityHitResult) hitResult).getEntity();
        if (!(entity instanceof PlayerEntity player)) {
            warning("You must be looking at a player!");
            return;
        }

        Enemies.Enemy enemy = Enemies.get().get(player.getUuid());
        if (enemy == null) {
            warning(player.getName().getString() + " is not in your enemies list!");
            return;
        }

        if (Enemies.get().remove(enemy)) {
            info("Removed " + player.getName().getString() + " from enemies!");
        } else {
            error("Failed to remove " + player.getName().getString() + " from enemies!");
        }
    }
}
