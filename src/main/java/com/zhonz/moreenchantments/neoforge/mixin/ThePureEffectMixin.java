package com.zhonz.moreenchantments.neoforge.mixin;

import com.zhonz.moreenchantments.enchantment.ModEnchantments;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for LivingEntity to implement the "无垢之人" (The Pure) enchantment's
 * debuff immunity.
 *
 * Description: "装备者不会有任何debuff"
 * (The wearer will not have any debuff.)
 *
 * Implementation: Intercept canBeAdded for non-beneficial effects
 * and cancel it if the entity has The Pure on the legs slot.
 */
@Mixin(LivingEntity.class)
public class ThePureEffectMixin {

    @Inject(method = "canBeAffected", at = @At("RETURN"), cancellable = true)
    private void blockDebuffs(MobEffectInstance effectInstance, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return; // already blocked, skip

        LivingEntity self = (LivingEntity)(Object)this;
        if (self.level().isClientSide()) return;

        // Check for The Pure enchantment on legs
        int thePureLevel = self.getItemBySlot(EquipmentSlot.LEGS)
                .getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.THE_PURE));
        if (thePureLevel <= 0) return;

        MobEffect effect = effectInstance.getEffect().value();
        // Block all non-beneficial effects (debuffs)
        if (!effect.isBeneficial()) {
            cir.setReturnValue(false);
        }
    }
}