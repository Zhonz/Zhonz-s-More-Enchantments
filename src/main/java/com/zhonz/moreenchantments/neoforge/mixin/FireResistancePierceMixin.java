package com.zhonz.moreenchantments.neoforge.mixin;

import com.zhonz.moreenchantments.util.WeepingFireHelper;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 统一火焰免疫模型的一部分: 让"哭泣之火"穿透原版抗火效果的免疫。
 *
 * 原版 {@link LivingEntity#hurt} 开头(事件触发前):
 *   if (source.is(IS_FIRE) && hasEffect(FIRE_RESISTANCE)) return false;
 * 即目标身上有抗火效果时, 一切 is_fire 伤害在进入任何事件前就被整体拒绝。
 * 本 mixin 只把该方法中"第一处 DamageSource.is(IS_FIRE)"(即抗火早退判定)
 * 在哭泣之火穿透时改为 false, 其余调用一律委派原逻辑。
 */
@Mixin(LivingEntity.class)
public abstract class FireResistancePierceMixin {

    @Redirect(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Lnet/minecraft/world/damagesource/DamageSource;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean zhonz$weepingFirePiercesFireResistance(DamageSource source, TagKey<DamageType> tag) {
        if (tag == DamageTypeTags.IS_FIRE && WeepingFireHelper.isPiercingWeepingFire(source)) {
            return false; // 哭泣之火: 无视对方抗火效果
        }
        return source.is(tag);
    }
}
