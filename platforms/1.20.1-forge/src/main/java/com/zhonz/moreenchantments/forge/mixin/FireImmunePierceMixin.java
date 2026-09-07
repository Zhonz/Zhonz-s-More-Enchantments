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
 * 统一火焰免疫模型的一部分: 让"哭泣之火"穿透原版对火免生物的免疫。
 *
 * 原版 1.20.1 {@link Entity#isInvulnerableTo} 含(经 javap 对 Forge 47.3.0 反混淆 jar 核实):
 *   invulnerable && !is(BYPASSES_INVULNERABILITY) && !isCreativePlayer → 无敌
 *   is(IS_FIRE) && fireImmune() → 无敌            ← 火免生物(烈焰人/僵尸猪灵…)
 *   is(IS_FALL) && type.is(FALL_DAMAGE_IMMUNE) → 无敌
 * 本 mixin @Redirect 该方法的全部 DamageSource.is(TagKey) 调用(无 ordinal → 逐处回调委托):
 * 对"哭泣之火且由哭泣之子造成"的 IS_FIRE 判定返回 false, 从而跳过 fireImmune 分支;
 * 其余判定(BYPASSES_INVULNERABILITY / IS_FALL 等)一律照原样委托 source.is(tag)。
 *
 * 1.21.1 NeoForge → 1.20.1 Forge 移植差异:
 * - 注入点与方法签名两版相同(Entity.isInvulnerableTo(DamageSource), Redirect 到
 *   DamageSource.is(TagKey<DamageType>) —— 1.20.1 该重定向调用即上面三处, 委托语义安全)。
 * - 穿透判定: 1.21 用数据驱动 source.is(WEEPING_FIRE); 1.20.1 无该 damage_type, 近似为
 *   "source 带 IS_FIRE tag 且直接造成者是主手持哭泣之子的活体"(见 zhonz$isPiercingWeepingFire)。
 *   原版火焰来源多数无活体造成者(燃烧/岩浆环境伤), 可接受误判边界已在 javadoc 标注。
 */
@Mixin(Entity.class)
public abstract class FireImmunePierceMixin {

    @Redirect(method = "isInvulnerableTo",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/damagesource/DamageSource;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean zhonz$weepingFirePiercesFireImmune(DamageSource source, TagKey<DamageType> tag) {
        if (tag == DamageTypeTags.IS_FIRE && zhonz$isPiercingWeepingFire(source)) {
            return false; // 哭泣之火: 绕过 fireImmune 生物的原版火焰免疫
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
