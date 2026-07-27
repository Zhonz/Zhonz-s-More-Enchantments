package com.zhonz.moreenchantments.mixin;

import com.zhonz.moreenchantments.enchantment.ModEnchantments;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for BowItem to implement the "神咒" (Divine Curse) enchantment's
 * charge speed doubling effect.
 *
 * Description: "蓄力速度、冷却时间翻倍" (charge speed and cooldown time doubled)
 * For the bow, the use duration represents how long the player holds right-click to charge.
 * We need to interpret "蓄力速度" carefully:
 *   - "蓄力速度翻倍" could mean the time to fully charge is doubled (you wait longer)
 *   - Or the charge rate per tick is doubled (faster)
 *
 * Given the curse is a negative effect, "charge time doubled" makes more sense.
 * However, "蓄力速度翻倍" literally means "charge speed doubled" = faster.
 * To be safe, we'll interpret it as: time to reach full charge is doubled
 * (since "蓄力时间" = "time to charge", and "翻倍" = "double", so we double the duration).
 *
 * Wait - re-reading: "蓄力速度、冷却时间翻倍" - the speed is doubled.
 * If speed is doubled, then time to charge is halved.
 *
 * Let's implement: charge duration is halved (speed doubled).
 */
@Mixin(BowItem.class)
public class BowChargeMixin {

    @Inject(method = "getUseDuration", at = @At("RETURN"), cancellable = true)
    private void modifyUseDuration(ItemStack stack, LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
        int level = stack.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.DIVINE_CURSE));
        if (level <= 0) return;

        // 神咒: 蓄力速度翻倍 = 充能时间减半
        int original = cir.getReturnValue();
        cir.setReturnValue(Math.max(1, original / 2));
    }
}
