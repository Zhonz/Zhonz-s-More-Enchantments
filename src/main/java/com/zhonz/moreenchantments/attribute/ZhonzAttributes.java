package com.zhonz.moreenchantments.attribute;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.zhonz.moreenchantments.ZhonzMoreEnchantments.MODID;

/**
 * 本模组统一"增伤"属性(挂在前置 Apothic Attributes 生态之上)。
 *
 * 说明: Apothic 没有通用增伤属性(只有 FIRE/COLD/ARROW/PROJECTILE/CURRENT_HP 等
 * 元素/类型属性),故本模组自注册两个通用增伤通道,所有附魔的增伤统一经此结算:
 *
 * <ul>
 *   <li>{@link #BONUS_DAMAGE}(bonus_damage): <b>加算层</b>, 默认 0。
 *       值 = "在最终伤害上额外增加的数值"(每个附魔用自己的 modifier 累加)。</li>
 *   <li>{@link #DAMAGE_MULTIPLIER}(damage_multiplier): <b>乘算层</b>, 默认 1。
 *       值 = "最终伤害 × 几"。每次结算把乘算类附魔的总乘积写入(保持 × 语义,
 *       避免属性加算把两个 ×1.5 变成 ×2)。</li>
 * </ul>
 *
 * 结算顺序固定为 <b>先加算后乘算</b>: final = (amount + bonus) × multiplier。
 * 均挂给全部 LivingEntity(见 mod 构造器中的 EntityAttributeModificationEvent)。
 */
public final class ZhonzAttributes {

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, MODID);

    public static final DeferredHolder<Attribute, Attribute> BONUS_DAMAGE = ATTRIBUTES.register("bonus_damage",
            () -> new RangedAttribute("attribute." + MODID + ".bonus_damage", 0.0D, -1.0E9D, 1.0E9D)
                    .setSyncable(true));

    public static final DeferredHolder<Attribute, Attribute> DAMAGE_MULTIPLIER = ATTRIBUTES.register("damage_multiplier",
            () -> new RangedAttribute("attribute." + MODID + ".damage_multiplier", 1.0D, 0.0D, 1.0E9D)
                    .setSyncable(true));

    /** 固定点加伤通道(default 0, ADD_VALUE 绝对量): flat 绝对加伤(如 fleet_footsteps/floating_grace)。 */
    public static final DeferredHolder<Attribute, Attribute> FLAT_DAMAGE = ATTRIBUTES.register("flat_damage",
            () -> new RangedAttribute("attribute." + MODID + ".flat_damage", 0.0D, -1.0E9D, 1.0E9D)
                    .setSyncable(true));

    /**
     * 收到伤害通道(defender 侧, default 1): 最终受到伤害 = 护甲结算后伤害 × incoming_damage。
     * 易伤(&gt;1, 如燃烧黄昏火焰易伤/冬痕/先知标记)与减伤(&lt;1, 如困兽-50%/急速攀升)统一写此属性;
     * 各乘数经独立 modifier 乘积聚合(默认 1, ADD_VALUE 写 mult-1), 结算一次乘。
     */
    public static final DeferredHolder<Attribute, Attribute> INCOMING_DAMAGE = ATTRIBUTES.register("incoming_damage",
            () -> new RangedAttribute("attribute." + MODID + ".incoming_damage", 1.0D, 0.0D, 1.0E9D)
                    .setSyncable(true));

    private ZhonzAttributes() {
    }

    public static ResourceLocation bonusModifier(String enchantId) {
        return ResourceLocation.fromNamespaceAndPath(MODID, "bonus_" + enchantId);
    }

    public static ResourceLocation multModifier(String enchantId) {
        return ResourceLocation.fromNamespaceAndPath(MODID, "mult_" + enchantId);
    }
}
