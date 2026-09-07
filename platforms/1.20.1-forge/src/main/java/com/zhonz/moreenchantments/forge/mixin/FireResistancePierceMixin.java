package com.zhonz.moreenchantments.forge.mixin;

import com.zhonz.moreenchantments.common.enchant.EnchantIds;
import com.zhonz.moreenchantments.forge.EnchantmentLookup1201;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 统一火焰免疫模型的一部分: 让"哭泣之火"穿透原版抗火效果的免疫。
 *
 * 原版 1.20.1 {@link LivingEntity#hurt} 中第一处 DamageSource.is(TagKey)(经 javap 核实,
 * ordinal=0)即抗火早退判定:
 *   if (source.is(IS_FIRE) && hasEffect(FIRE_RESISTANCE)) return false;
 * (其后才是护盾/冻结/冷却/护甲等其它 is() 调用, 顺序与 1.21 注释描述一致。)
 * 本 mixin 只把该方法中第一处 is() 在"哭泣之火穿透"时改为 false → 抗火效果不再整体拒绝伤害;
 * 其余调用一律委派原逻辑。
 *
 * 1.21.1 NeoForge → 1.20.1 Forge 移植差异:
 * - 注入点/方法签名两版相同(LivingEntity.hurt(DamageSource,float) ordinal=0 的 is(TagKey) 调用)。
 * - 穿透判定同 FireImmunePierceMixin: 1.21 source.is(WEEPING_FIRE) → 1.20.1
 *   "IS_FIRE tag + 直接造成者主手持哭泣之子"近似。
 */
@Mixin(LivingEntity.class)
public abstract class FireResistancePierceMixin {

    @Redirect(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Lnet/minecraft/world/damagesource/DamageSource;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean zhonz$weepingFirePiercesFireResistance(DamageSource source, TagKey<DamageType> tag) {
        if (tag == DamageTypeTags.IS_FIRE && zhonz$isPiercingWeepingFire(source)) {
            return false; // 哭泣之火: 无视对方抗火效果
        }
        return source.is(tag);
    }

    /** 哭泣之火穿透判定(1.20.1 近似): 火焰伤害 且 直接造成者主手持"哭泣之子"。 */
    @Unique
    private static boolean zhonz$isPiercingWeepingFire(DamageSource source) {
        if (source == null || !source.is(DamageTypeTags.IS_FIRE)) return false;
        Entity direct = source.getDirectEntity();
        Entity causing = source.getEntity();
        Entity wielder = direct != null ? direct : causing;
        return wielder instanceof LivingEntity le
                && EnchantmentLookup1201.INSTANCE.mainHand(le, EnchantIds.WEEPING_CHILD) > 0;
    }
}
