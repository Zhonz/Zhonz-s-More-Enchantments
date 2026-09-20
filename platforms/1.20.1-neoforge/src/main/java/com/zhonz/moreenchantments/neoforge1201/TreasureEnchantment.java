package com.zhonz.moreenchantments.neoforge1201;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * 1.20.1 平台: **宝藏附魔**基类。
 *
 * <p><b>为什么需要它</b>: 1.21.1 里"宝藏"由数据包标签表达
 * ({@code data/minecraft/tags/enchantment/treasure.json}); 而 **1.20.1 没有附魔标签注册表**,
 * 该版本用 {@link Enchantment#isTreasureOnly()} 决定"能否出现在附魔台/战利品/交易"。
 * 其默认实现返回 {@code false}, 且 {@code Enchantment} 构造器是 protected,
 * 所以必须由子类覆写。
 *
 * <p><b>与 1.21.1 的对应关系</b>: 主工程 {@code treasure.json} 里的每个 id,
 * 在 1.20.1 都用本类注册 —— 两版语义等价(都不出现在附魔台, 都被视为宝藏)。
 */
public class TreasureEnchantment extends Enchantment {

    public TreasureEnchantment(Rarity rarity, EnchantmentCategory category, EquipmentSlot[] slots) {
        super(rarity, category, slots);
    }

    /** 宝藏附魔: 不参与附魔台抽选(与 1.21.1 的 #minecraft:treasure 语义一致)。 */
    @Override
    public boolean isTreasureOnly() {
        return true;
    }
}