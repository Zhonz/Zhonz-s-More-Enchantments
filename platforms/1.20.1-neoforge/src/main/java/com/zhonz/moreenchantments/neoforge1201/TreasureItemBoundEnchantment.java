package com.zhonz.moreenchantments.neoforge1201;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

import java.util.function.Predicate;

/**
 * 1.20.1 平台: **载体限定 + 宝藏** 的附魔基类。
 *
 * <p>1.21.1 用 {@code supported_items} 标签同时表达"限定载体"与"宝藏"; 1.20.1 这两件事
 * 分属两个机制 —— 载体由 {@link ItemBoundEnchantment#canEnchant(ItemStack)} 表达,
 * 宝藏由 {@link TreasureEnchantment#isTreasureOnly()} 表达。本类把二者组合,
 * 供"既是宝藏、又只能附在特定物品上"的附魔使用
 * ({@code prophets_call} 山羊角 / {@code thirty_million_turns} 下界之星)。
 */
public class TreasureItemBoundEnchantment extends ItemBoundEnchantment {

    public TreasureItemBoundEnchantment(Rarity rarity, EnchantmentCategory category, EquipmentSlot[] slots,
                                        Predicate<ItemStack> supported) {
        super(rarity, category, slots, supported);
    }

    @Override
    public boolean isTreasureOnly() {
        return true;
    }
}