package com.zhonz.moreenchantments.mixin;

import com.zhonz.moreenchantments.enchantment.ModEnchantments;
import com.zhonz.moreenchantments.event.EntityDataStorage;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for Player to implement the "挂" (Hang) enchantment's true damage:
 * When attacking, the Hang item's true damage is applied to bypass armor.
 * Also handles the special damage event for entities with HANG equipment.
 */
@Mixin(Player.class)
public abstract class PlayerAttackMixin {

    /**
     * 玩家攻击时,如果背包中有"挂"附魔物品,附加真实伤害
     */
    @Inject(method = "attack", at = @At("TAIL"))
    private void onAttack(net.minecraft.world.entity.Entity target, CallbackInfo ci) {
        Player self = (Player)(Object)this;
        if (self.level().isClientSide()) return;
        if (!(target instanceof LivingEntity living)) return;

        // 检查背包中是否有"挂"附魔物品
        boolean hasHang = false;
        for (int i = 0; i < self.getInventory().getContainerSize(); i++) {
            ItemStack stack = self.getInventory().getItem(i);
            if (stack.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.HANG)) > 0) {
                hasHang = true;
                break;
            }
        }

        if (!hasHang) return;

        // 附挂"挂"附魔时,玩家攻击造成真实伤害(直接扣血,无视护甲)
        // 在已造成普通伤害后,再附加"额外真实伤害"
        // 真实伤害 = 防御方10%最大生命值
        float trueDamage = living.getMaxHealth() * 0.1f;
        float newHp = Math.max(0, living.getHealth() - trueDamage);
        living.setHealth(newHp);
        if (newHp <= 0 && living.isAlive()) {
            living.die(self.damageSources().playerAttack(self));
        }
    }
}
