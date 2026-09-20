package com.zhonz.moreenchantments.forge;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

import java.util.function.Predicate;

/**
 * 只在**指定物品**上可用的附魔。
 *
 * <h2>为什么需要它</h2>
 * 1.21 的附魔是数据驱动的, 载体由 {@code supported_items} 标签给出(例如 #59 永劫回归 =
 * {@code #zhonz_more_enchantments:enchantable/totem})。1.20.1 没有该字段, 只能靠
 * {@link EnchantmentCategory} —— 而原版分类里**根本没有"图腾/下界之星/山羊角"这一类**
 * (最近的 WEARABLE 只认盔甲/鞘翅/头颅)。
 *
 * <p>于是原先用 {@code EnchantmentCategory.WEARABLE} 注册的 4 个附魔
 * ({@code eternal_return} / {@code manifest} / {@code prophets_call} / {@code thirty_million_turns})
 * 在 1.20.1 上 {@code canEnchant} 恒为 false —— 铁砧/附魔台/`/enchant` **都无法把它们放到
 * 目标物品上**(实测: `enchant <僵尸> zhonz_more_enchantments:eternal_return 1` 报
 * "Totem of Undying cannot support that enchantment"), 即这些附魔在 1.20.1 上等于无法获得。
 *
 * <p>本类直接覆写 {@link Enchantment#canEnchant(ItemStack)}, 用谓词精确表达载体,
 * 语义与 1.21 的 {@code supported_items} 对齐。
 */
public class ItemBoundEnchantment extends Enchantment {

    private final Predicate<ItemStack> supported;

    public ItemBoundEnchantment(Rarity rarity, EnchantmentCategory category, EquipmentSlot[] slots,
                                Predicate<ItemStack> supported) {
        super(rarity, category, slots);
        this.supported = supported;
    }

    @Override
    public boolean canEnchant(ItemStack stack) {
        return supported.test(stack);
    }
}
