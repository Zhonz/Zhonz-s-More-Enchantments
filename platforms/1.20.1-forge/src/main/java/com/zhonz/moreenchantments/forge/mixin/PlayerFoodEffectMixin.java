package com.zhonz.moreenchantments.forge.mixin;

import com.zhonz.moreenchantments.common.enchant.EnchantIds;
import com.zhonz.moreenchantments.forge.EnchantmentLookup1201;
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
 * Implementation: Intercept canBeAffected for the Hunger effect and cancel it
 * if the player has Scavenger on the head slot.
 *
 * 文档 #2(README.md:42 / ENCHANTMENTS.md:23-25)只授权屏蔽"饥饿" —— 原先一并屏蔽的
 * POISON/CONFUSION 属未授权, 已删(与 1.21.1 主工程一致)。
 *
 * 1.20.1 移植差异: 目标方法 canBeAffected(MobEffectInstance) 在 1.20.1 同样存在
 * 且被 addEffect(MobEffectInstance, Entity) 调用(字节码已核实), 可注入。
 * MobEffectInstance.getEffect() 在 1.20.1 直接返回 MobEffect(无 Holder.value());
 * MobEffects.HUNGER/POISON/CONFUSION 为 MobEffect 常量。附魔等级改用
 * EnchantmentLookup1201 槽位查询。
 */
@Mixin(LivingEntity.class)
public class PlayerFoodEffectMixin {

    @Inject(method = "canBeAffected", at = @At("RETURN"), cancellable = true)
    private void blockHarmfulEffects(MobEffectInstance effectInstance, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return; // already blocked, skip

        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide()) return;

        if (!(self instanceof Player player)) return;

        int scavengerLevel = EnchantmentLookup1201.INSTANCE.slot(player, EnchantIds.SCAVENGER, EquipmentSlot.HEAD);
        if (scavengerLevel <= 0) return;

        // 只屏蔽"饥饿"(文档 #2 唯一授权项); 中毒/反胃原为过度屏蔽, 已按文档移除。
        MobEffect effect = effectInstance.getEffect();
        if (effect == MobEffects.HUNGER) {
            cir.setReturnValue(false);
        }
    }
}
