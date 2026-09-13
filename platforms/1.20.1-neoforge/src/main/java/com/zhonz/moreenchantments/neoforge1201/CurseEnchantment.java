package com.zhonz.moreenchantments.neoforge1201;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * 1.20.1 NeoForge 平台: **诅咒附魔**基类。
 *
 * <p><b>为什么需要它</b>: 1.20.1 的 {@link Enchantment#getFullname(int)} 按
 * {@link Enchantment#isCurse()} 决定名字颜色 —— 诅咒为 {@code RED}, 否则 {@code GRAY}
 * (经 1.20.1 mojmap 字节码核实)。而 {@code isCurse()} 默认返回 {@code false},
 * 玩家无法通过父类得到红色;必须子类覆写。
 *
 * <p><b>与 1.21.1 的差异</b>: 1.21 的诅咒改由**标签**判定
 * ({@code getFullname} 里 {@code holder.is(EnchantmentTags.CURSE)} → {@code ChatFormatting.RED}),
 * 因此主工程用 {@code data/minecraft/tags/enchantment/curse.json} 声明, 无需子类。
 * 两版语义等价(红色斜体 + 铁砧"过于昂贵"豁免等诅咒行为)。
 *
 * <p><b>用法</b>: 见 {@link ModEnchantments1201} 的 {@code divine_curse} / {@code self_bound}。
 */
public abstract class CurseEnchantment extends Enchantment {

    protected CurseEnchantment(Rarity rarity, EnchantmentCategory category, EquipmentSlot[] slots) {
        super(rarity, category, slots);
    }

    /** 诅咒附魔: 名字渲染为红色(配合附魔名默认斜体 → 红色斜体)。 */
    @Override
    public boolean isCurse() {
        return true;
    }
}
