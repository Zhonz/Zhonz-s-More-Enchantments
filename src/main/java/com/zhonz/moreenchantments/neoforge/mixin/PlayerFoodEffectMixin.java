package com.zhonz.moreenchantments.neoforge.mixin;

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
 * Mixin for LivingEntity to implement the "食腐者" (Scavenger) enchantment's
 * food consumption protection.
 *
 * Description: "在吃腐肉等可以给玩家造成饥饿buff的食物时不再获得饥饿"
 * (When eating rotten flesh and other foods that give the Hunger effect,
 * the player no longer gains the Hunger effect.)
 *
 * Implementation: Intercept canBeAffected for the Hunger effect
 * and cancel it if the player has Scavenger on the head slot.
 * Using canBeAffected instead of addEffect because addEffect(MobEffectInstance)
 * is final and cannot be injected by Mixin.
 *
 * 文档 #2(ENCHANTMENTS.md:24 / README.md:42):吃致饥饿食物时「不再获得饥饿」——
 * 文档只承诺饥饿, 故不再屏蔽 POISON / CONFUSION(原实现多屏蔽了这两种, 已删)。
 */
@Mixin(LivingEntity.class)
public class PlayerFoodEffectMixin {

    @Inject(method = "canBeAffected", at = @At("RETURN"), cancellable = true)
    private void blockHarmfulEffects(MobEffectInstance effectInstance, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return; // already blocked, skip

        LivingEntity self = (LivingEntity)(Object)this;
        if (self.level().isClientSide()) return;

        if (!(self instanceof Player player)) return;

        int scavengerLevel = player.getItemBySlot(EquipmentSlot.HEAD)
                .getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.SCAVENGER));
        if (scavengerLevel <= 0) return;

        MobEffect effect = effectInstance.getEffect().value();
        if (effect == MobEffects.HUNGER) {
            cir.setReturnValue(false);
        }
    }
}
