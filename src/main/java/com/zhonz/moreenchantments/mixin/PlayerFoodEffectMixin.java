package com.zhonz.moreenchantments.mixin;

import com.zhonz.moreenchantments.enchantment.ModEnchantments;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for Player to implement the "食腐者" (Scavenger) enchantment's
 * food consumption protection.
 *
 * Description: "在吃腐肉等可以给玩家造成饥饿buff的食物时不再获得饥饿"
 * (When eating rotten flesh and other foods that give the Hunger effect,
 * the player no longer gains the Hunger effect.)
 *
 * Implementation: Intercept addEffect for Hunger effect and cancel it
 * if the player has Scavenger on the head slot.
 */
@Mixin(LivingEntity.class)
public class PlayerFoodEffectMixin {

    @Inject(method = "addEffect", at = @At("HEAD"), cancellable = true)
    private void cancelHungerEffect(MobEffectInstance effectInstance, CallbackInfoReturnable<Boolean> cir) {
        // 仅在服务端
        LivingEntity self = (LivingEntity)(Object)this;
        if (self.level().isClientSide()) return;

        // 只对玩家生效
        if (!(self instanceof Player player)) return;

        // 检查头盔是否有食腐者附魔
        int scavengerLevel = player.getItemBySlot(EquipmentSlot.HEAD)
                .getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.SCAVENGER));
        if (scavengerLevel <= 0) return;

        // 拦截饥饿、中毒、恶心
        MobEffect effect = effectInstance.getEffect().value();
        if (effect == MobEffects.HUNGER || effect == MobEffects.POISON || effect == MobEffects.CONFUSION) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}
