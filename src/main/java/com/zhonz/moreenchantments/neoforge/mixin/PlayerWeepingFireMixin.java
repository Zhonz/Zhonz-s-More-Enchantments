package com.zhonz.moreenchantments.neoforge.mixin;

import com.zhonz.moreenchantments.util.WeepingFireHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "比任何人都要悲伤的哭泣之子" 的玩家防御者路径。
 *
 * Player 覆写了 {@code hurt(DamageSource, float)}: 先做 isInvulnerableTo / 创造模式
 * 无敌检查与难度缩放, 再 invokespecial 调 LivingEntity.hurt。若只拦 LivingEntity.hurt,
 * 重放会再次经过 Player.hurt 的难度缩放 → 伤害二次缩放。因此在 Player.hurt 的
 * HEAD(缩放前)拦截并共用 {@link WeepingFireHelper}。
 */
@Mixin(Player.class)
public abstract class PlayerWeepingFireMixin {

    @Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At("HEAD"), cancellable = true)
    private void zhonz$playerWeepingFireConversion(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        WeepingFireHelper.tryConvert(self, source, amount, cir);
    }
}
