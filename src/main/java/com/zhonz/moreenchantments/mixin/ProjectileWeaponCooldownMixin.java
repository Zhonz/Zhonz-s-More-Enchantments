package com.zhonz.moreenchantments.mixin;

import com.zhonz.moreenchantments.enchantment.ModEnchantments;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for Item to implement the "神咒" (Divine Curse) enchantment's
 * use duration doubling effect.
 *
 * Description: "蓄力速度、冷却时间翻倍" (charge speed and cooldown time doubled)
 * 项目武器(Bow/Trident/Crossbow)的充能/冷却时间翻倍: 玩家需要 2 倍的时间来蓄力.
 *
 * getUseDuration is defined on Item and overridden by BowItem, CrossbowItem, TridentItem.
 * This mixin intercepts all of them at the base class level.
 */
@Mixin(Item.class)
public class ProjectileWeaponCooldownMixin {

    @Inject(method = "getUseDuration", at = @At("RETURN"), cancellable = true)
    private void modifyUseDuration(ItemStack stack, LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
        int level = stack.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.DIVINE_CURSE));
        if (level <= 0) return;

        int original = cir.getReturnValue();
        // 充能/冷却时间翻倍(取最大值避免变成0)
        cir.setReturnValue(Math.max(1, original * 2));
    }
}
