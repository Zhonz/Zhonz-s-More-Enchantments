package com.zhonz.moreenchantments.common.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Per-entity persistent data storage using WeakHashMap.
 * Used for non-player LivingEntities that don't have getPersistentData().
 * Entries are automatically cleaned up when entities are garbage-collected.
 *
 * 纯 MC 实现, 无平台依赖(stage2 下沉到 common)。
 */
public class EntityDataStorage {

    private static final Map<LivingEntity, CompoundTag> ENTITY_DATA = new WeakHashMap<>();

    /**
     * Get or create persistent data for an entity.
     */
    public static CompoundTag getData(LivingEntity entity) {
        return ENTITY_DATA.computeIfAbsent(entity, k -> new CompoundTag());
    }

    /**
     * Get persistent data for an entity, returning null if not present.
     */
    public static CompoundTag getDataIfExists(LivingEntity entity) {
        return ENTITY_DATA.get(entity);
    }

    /**
     * Remove persistent data for an entity.
     */
    public static void removeData(LivingEntity entity) {
        ENTITY_DATA.remove(entity);
    }

    /**
     * Check if an entity has stored data.
     */
    public static boolean hasData(LivingEntity entity) {
        return ENTITY_DATA.containsKey(entity);
    }

    /**
     * Get data for any LivingEntity. For Player, uses getPersistentData().
     * For other entities, uses the WeakHashMap.
     */
    public static CompoundTag getEntityData(LivingEntity entity) {
        if (entity instanceof Player player) {
            return player.getPersistentData();
        }
        return getData(entity);
    }
}
