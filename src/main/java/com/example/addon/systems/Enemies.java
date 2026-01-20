package com.example.addon.systems;

import meteordevelopment.meteorclient.systems.System;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.utils.misc.NbtUtils;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.*;

public class Enemies extends System<Enemies> {
    private static final Enemies INSTANCE = new Enemies();

    private final Map<UUID, Enemy> enemies = new HashMap<>();

    private Enemies() {
        super("enemies");
    }

    public static Enemies get() {
        return Systems.get(Enemies.class);
    }

    @Override
    public void init() {}

    public boolean add(Enemy enemy) {
        if (enemies.containsKey(enemy.id)) return false;

        enemies.put(enemy.id, enemy);
        save();

        return true;
    }

    public boolean remove(Enemy enemy) {
        boolean removed = enemies.remove(enemy.id) != null;
        if (removed) save();
        return removed;
    }

    public Enemy get(String name) {
        for (Enemy enemy : enemies.values()) {
            if (enemy.name.equalsIgnoreCase(name)) {
                return enemy;
            }
        }
        return null;
    }

    public Enemy get(UUID uuid) {
        return enemies.get(uuid);
    }

    public Enemy get(PlayerEntity player) {
        return get(player.getUuid());
    }

    public boolean isEnemy(String name) {
        return get(name) != null;
    }

    public boolean isEnemy(UUID uuid) {
        return enemies.containsKey(uuid);
    }

    public boolean isEnemy(PlayerEntity player) {
        return isEnemy(player.getUuid());
    }

    public boolean shouldAttack(PlayerEntity player) {
        return isEnemy(player);
    }

    public int count() {
        return enemies.size();
    }

    public boolean isEmpty() {
        return enemies.isEmpty();
    }

    public List<Enemy> getAll() {
        return new ArrayList<>(enemies.values());
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();

        NbtList list = new NbtList();
        for (Enemy enemy : enemies.values()) {
            list.add(enemy.toTag());
        }
        tag.put("enemies", list);

        return tag;
    }

    @Override
    public Enemies fromTag(NbtCompound tag) {
        enemies.clear();

        NbtList list = tag.getList("enemies", NbtElement.COMPOUND_TYPE);
        for (NbtElement element : list) {
            NbtCompound compound = (NbtCompound) element;
            Enemy enemy = new Enemy(compound);
            enemies.put(enemy.id, enemy);
        }

        return this;
    }

    public static class Enemy {
        public UUID id;
        public String name;

        public Enemy(String name, UUID id) {
            this.name = name;
            this.id = id;
        }

        public Enemy(NbtCompound tag) {
            name = tag.getString("name");
            id = NbtUtils.uuidFromNbt(tag.getCompound("id"));
        }

        public NbtCompound toTag() {
            NbtCompound tag = new NbtCompound();
            tag.putString("name", name);
            tag.put("id", NbtUtils.uuidToNbt(id));
            return tag;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Enemy enemy = (Enemy) o;
            return Objects.equals(id, enemy.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }
}
