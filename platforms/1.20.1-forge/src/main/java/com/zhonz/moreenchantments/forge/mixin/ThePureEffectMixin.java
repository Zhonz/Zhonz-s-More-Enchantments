package com.zhonz.moreenchantments.forge.mixin;

import com.zhonz.moreenchantments.common.enchant.EnchantIds;
import com.zhonz.moreenchantments.forge.EnchantmentLookup1201;
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
 * Implementation: Intercept canBeAffected for non-beneficial effects
 * and cancel it if the entity has The Pure on the legs slot.
 *
 * 1.20.1 移植差异: canBeAffected(MobEffectInstance) 在 1.20.1 存在且被 addEffect
 * 调用; MobEffectInstance.getEffect() 直接返回 MobEffect; effect.isBeneficial()
 * 1.20.1 亦存在。附魔等级用 EnchantmentLookup1201 槽位查询(LEGS)。
 */
@Mixin(LivingEntity.class)
public class ThePureEffectMixin {

    @Inject(method = "canBeAffected", at = @At("RETURN"), cancellable = true)
    private void blockDebuffs(MobEffectInstance effectInstance, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return; // already blocked, skip

        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide()) return;

        // Check for The Pure enchantment on legs
        int thePureLevel = EnchantmentLookup1201.INSTANCE.slot(self, EnchantIds.THE_PURE, EquipmentSlot.LEGS);
        if (thePureLevel <= 0) return;

        MobEffect effect = effectInstance.getEffect();
        // Block all non-beneficial effects (debuffs)
        if (!effect.isBeneficial()) {
            cir.setReturnValue(false);
        }
    }
}
