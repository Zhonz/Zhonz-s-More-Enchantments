package com.zhonz.moreenchantments.mixin;

import com.zhonz.moreenchantments.enchantment.ModEnchantments;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for ProjectileWeaponItem to implement the "神咒" (Divine Curse) enchantment's
 * cooldown doubling.
 *
 * The description says "蓄力速度、冷却时间翻倍" (charge speed, cooldown doubled).
 * The "cooldown" in this context refers to the time after firing before the weapon
 * can be used again. For bows, the "cooldown" is essentially the use duration.
 *
 * We interpret "cooldown doubled" as: the total time between consecutive shots
 * (use duration + post-use cooldown) is doubled.
 *
 * For simplicity, we double the use duration for projectile weapons under Divine Curse.
 */
@Mixin(ProjectileWeaponItem.class)
public class ProjectileWeaponCooldownMixin {

    @Inject(method = "getUseDuration", at = @At("RETURN"), cancellable = true)
    private void modifyUseDuration(ItemStack stack, LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
        int level = stack.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.DIVINE_CURSE));
        if (level <= 0) return;

        int original = cir.getReturnValue();
        // 冷却时间翻倍
        cir.setReturnValue(original * 2);
    }
}
