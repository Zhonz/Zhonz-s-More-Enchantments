package com.zhonz.moreenchantments.neoforge.mixin;

import com.zhonz.moreenchantments.util.WeepingFireHelper;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 统一火焰免疫模型的一部分: 让"哭泣之火"穿透原版对火免生物的免疫。
 *
 * 原版 {@link Entity#isInvulnerableTo} 含: source.is(IS_FIRE) && fireImmune() → 无敌。
 * 本 mixin 把其中对 IS_FIRE 标签的判定, 在"哭泣之火且由哭泣之子造成"时改为 false,
 * 从而跳过 fireImmune 分支(烈焰人/僵尸猪灵等照常被哭泣之火伤害)。
 * 其余判定(creative、BYPASSES 等)一律照原样委派。
 */
@Mixin(Entity.class)
public abstract class FireImmunePierceMixin {

    @Redirect(method = "isInvulnerableTo",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/damagesource/DamageSource;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean zhonz$weepingFirePiercesFireImmune(DamageSource source, TagKey<DamageType> tag) {
        if (tag == DamageTypeTags.IS_FIRE && WeepingFireHelper.isPiercingWeepingFire(source)) {
            return false; // 哭泣之火: 绕过 fireImmune 生物的原版火焰免疫
        }
        return source.is(tag);
    }
}
