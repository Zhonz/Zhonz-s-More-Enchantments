package com.zhonz.moreenchantments.neoforge.mixin;

import com.zhonz.moreenchantments.util.WeepingFireHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "比任何人都要悲伤的哭泣之子": 攻击造成真·火焰伤害。
 *
 * 拦截 {@link LivingEntity#hurt}(非玩家防御者路径; 玩家防御者由
 * {@link PlayerWeepingFireMixin} 在 Player.hurt 的难度缩放之前拦截, 避免二次缩放)。
 */
@Mixin(LivingEntity.class)
public abstract class WeepingFireMixin {

    @Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At("HEAD"), cancellable = true)
    private void zhonz$weepingFireConversion(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        WeepingFireHelper.tryConvert(self, source, amount, cir);
    }
}
