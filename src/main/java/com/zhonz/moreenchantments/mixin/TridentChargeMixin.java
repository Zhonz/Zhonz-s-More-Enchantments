package com.zhonz.moreenchantments.mixin;

import com.zhonz.moreenchantments.enchantment.ModEnchantments;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for TridentItem to implement the "神咒" (Divine Curse) enchantment's
 * charge speed doubling effect.
 */
@Mixin(TridentItem.class)
public class TridentChargeMixin {

    @Inject(method = "getUseDuration", at = @At("RETURN"), cancellable = true)
    private void modifyUseDuration(ItemStack stack, LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
        int level = stack.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.DIVINE_CURSE));
        if (level <= 0) return;

        int original = cir.getReturnValue();
        cir.setReturnValue(Math.max(1, original / 2));
    }
}
