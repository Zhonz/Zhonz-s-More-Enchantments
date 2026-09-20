package com.zhonz.moreenchantments.command.test;

import com.zhonz.moreenchantments.common.damage.EnchantmentLevelLookup;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * 声明式"附魔等级"查询桩(round-testkit)。
 *
 * <p>把被测规则从"附魔系统"里解耦出来: 规则函数读到什么等级, 完全由用例声明。
 * 这样就能精确构造这些在真实物品上根本做不出来的输入:
 * <ul>
 *   <li>越界等级(0 / 超过 max_level / 负数) —— 旧套件做不到, 因为 Brigadier 参数限 1..3;</li>
 *   <li>同一实体同时"带"多个附魔的组合(泰坦+破军, 孤独的正午+燃烧的黄昏…);</li>
 *   <li>槽位错配(把头盔附魔声明成主手, 或反之) —— 用于验证规则真的按槽位取值;</li>
 *   <li>把等级只声明在任意槽位而不在主手 —— 用于验证"主手限定"的附魔不会误触发。</li>
 * </ul>
 *
 * <p>它不读实体、不查注册表, 因此规则用例的断言是"纯数值对纯数值",
 * 一旦 FAIL 就能确定是规则本身被改坏, 而不是装备没穿上。
 */
public final class LevelStubs implements EnchantmentLevelLookup {

    private final Map<String, Integer> mainHand = new HashMap<>();
    private final Map<String, Integer> anySlot = new HashMap<>();
    private final Map<String, Integer> bySlot = new HashMap<>();

    // ---- 构造 ----

    public static LevelStubs of(String id, int level) {
        return new LevelStubs().mainHand(id, level);
    }

    public static LevelStubs none() {
        return new LevelStubs();
    }

    public static LevelStubs helmet(String id, int level) {
        return new LevelStubs().slot(id, EquipmentSlot.HEAD, level);
    }

    public static LevelStubs chest(String id, int level) {
        return new LevelStubs().slot(id, EquipmentSlot.CHEST, level);
    }

    public static LevelStubs legs(String id, int level) {
        return new LevelStubs().slot(id, EquipmentSlot.LEGS, level);
    }

    public static LevelStubs feet(String id, int level) {
        return new LevelStubs().slot(id, EquipmentSlot.FEET, level);
    }

    // ---- 链式声明 ----

    /** 主手等级(同时计入 anySlot, 取各槽位最大值)。 */
    public LevelStubs mainHand(String id, int level) {
        mainHand.put(id, level);
        anySlot.merge(id, level, Math::max);
        return this;
    }

    /** 指定槽位等级。 */
    public LevelStubs slot(String id, EquipmentSlot slot, int level) {
        bySlot.put(slot.name() + "|" + id, level);
        anySlot.merge(id, level, Math::max);
        return this;
    }

    /** 只写 anySlot, 不写 mainHand —— 用于验证"主手限定"附魔不应因装备在别处而生效。 */
    public LevelStubs onlyAnySlot(String id, int level) {
        anySlot.put(id, level);
        return this;
    }

    // ---- EnchantmentLevelLookup ----

    @Override
    public int anySlot(LivingEntity entity, String enchantId) {
        return anySlot.getOrDefault(enchantId, 0);
    }

    @Override
    public int mainHand(LivingEntity entity, String enchantId) {
        return mainHand.getOrDefault(enchantId, 0);
    }

    @Override
    public int slot(LivingEntity entity, String enchantId, EquipmentSlot slot) {
        return bySlot.getOrDefault(slot.name() + "|" + enchantId, 0);
    }
}
