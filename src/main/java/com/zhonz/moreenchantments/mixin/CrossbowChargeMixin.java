package com.zhonz.moreenchantments.mixin;

import com.zhonz.moreenchantments.enchantment.ModEnchantments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CrossbowItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for CrossbowItem to implement the "爆裂黎明" (Explosive Dawn) enchantment:
 * Slows down the crossbow charge time significantly.
 */
@Mixin(CrossbowItem.class)
public class CrossbowChargeMixin {

    /**
     * 弩的装填时间 (Ticks)
     * 默认: 25 ticks (1.25 秒)
     * 爆裂黎明: 翻倍到 50 ticks (2.5 秒)
     */
    @Inject(method = "getUseDuration", at = @At("RETURN"), cancellable = true)
    private void modifyUseDuration(ItemStack stack, net.minecraft.world.entity.LivingEntity entity,
                                   CallbackInfoReturnable<Integer> cir) {
        int level = stack.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.EXPLOSIVE_DAWN));
        if (level <= 0) return;

        // 装填时间翻倍
        cir.setReturnValue(cir.getReturnValue() * 2);
    }
}
