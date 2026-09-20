package com.zhonz.moreenchantments.common.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Per-entity persistent data storage.
 *
 * <h2>后端选择(2026-09 修复: 玩家统一到 persistentData)</h2>
 * <ul>
 *   <li><b>玩家</b>: 直接用 {@link Player#getPersistentData()} —— 它随玩家存档落盘;</li>
 *   <li><b>非玩家</b>: {@link WeakHashMap}, 实体被 GC 时条目自动消失(怪物状态本来就不需要跨存档)。</li>
 * </ul>
 *
 * <p><b>修复的缺陷(运行时复现)</b>: 此前 {@link #getData} 对玩家也返回弱表条目, 而
 * {@link #getEntityData} 返回 persistentData —— 同一附魔"一个入口写、另一个入口读"时状态读不回来。
 * 已知受害者: {@code fools_mask} 的幸运标志(tick 侧写 persistentData, 事件乘伤侧读弱表)
 * → 幸运分支永不生效, 玩家恒走 ×0.01~1 的减伤分支。
 * 另外写在弱表里的玩家状态**不会存档**, 重登即丢。
 *
 * <p>修复后两个入口对玩家指向同一份数据, 且所有读/写/清除入口语义一致。
 *
 * <p>纯 MC 实现, 无平台依赖(stage2 下沉到 common)。
 */
public class EntityDataStorage {

    /** 本模组所有状态键的前缀(与事件层的 KEY_* 常量一致)。 */
    public static final String KEY_PREFIX = "zhonz_";

    private static final Map<LivingEntity, CompoundTag> ENTITY_DATA = new WeakHashMap<>();

    /**
     * Get or create persistent data for an entity.
     *
     * <p>玩家走 persistentData(会存档), 非玩家走弱表 —— 与 {@link #getEntityData} 同源。
     */
    public static CompoundTag getData(LivingEntity entity) {
        if (entity instanceof Player player) {
            return player.getPersistentData();
        }
        return ENTITY_DATA.computeIfAbsent(entity, k -> new CompoundTag());
    }

    /**
     * Get persistent data for an entity, returning null if not present.
     *
     * <p>玩家的 persistentData 永远存在(可能为空), 因此玩家恒返回非 null —— 这与
     * {@link #hasData} 配合使用: 先判断有没有本模组的状态, 再取。
     */
    public static CompoundTag getDataIfExists(LivingEntity entity) {
        if (entity instanceof Player player) {
            return player.getPersistentData();
        }
        return ENTITY_DATA.get(entity);
    }

    /**
     * 清除该实体的本模组状态。
     *
     * <ul>
     *   <li>非玩家: 直接丢整条弱表记录;</li>
     *   <li>玩家: <b>只删 {@value #KEY_PREFIX} 前缀的键</b> —— persistentData 是所有模组共享的
     *       复合标签, 整块清空会误删别的模组的数据。</li>
     * </ul>
     */
    public static void removeData(LivingEntity entity) {
        if (entity instanceof Player player) {
            CompoundTag data = player.getPersistentData();
            // 先快照再删: 直接遍历 getAllKeys() 的同时 remove 会 ConcurrentModificationException
            List<String> keys = new ArrayList<>(data.getAllKeys());
            for (String key : keys) {
                if (key.startsWith(KEY_PREFIX)) {
                    data.remove(key);
                }
            }
            return;
        }
        ENTITY_DATA.remove(entity);
    }

    /**
     * 该实体是否有本模组的状态。
     *
     * <p>玩家: 检查 persistentData 里是否存在 {@value #KEY_PREFIX} 前缀的键(而不是"persistentData 非空",
     * 因为别的模组也会往里面写)。
     */
    public static boolean hasData(LivingEntity entity) {
        if (entity instanceof Player player) {
            for (String key : player.getPersistentData().getAllKeys()) {
                if (key.startsWith(KEY_PREFIX)) {
                    return true;
                }
            }
            return false;
        }
        return ENTITY_DATA.containsKey(entity);
    }

    /**
     * Get data for any LivingEntity, 与 {@link #getData} 完全同源。
     *
     * <p>历史上这两个入口对玩家指向不同存储, 是"写了读不回"的根因; 现统一委托给 {@link #getData},
     * 保留该方法只为兼容既有调用点。
     */
    public static CompoundTag getEntityData(LivingEntity entity) {
        return getData(entity);
    }
}
