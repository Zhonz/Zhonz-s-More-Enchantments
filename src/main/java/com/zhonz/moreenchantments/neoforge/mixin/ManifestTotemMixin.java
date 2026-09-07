package com.zhonz.moreenchantments.neoforge.mixin;

import com.zhonz.moreenchantments.enchantment.ModEnchantments;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "于此显圣": 不死图腾被触发消耗时,周围全部生物无法移动3秒,自身获得30秒抗性提升V。
 */
@Mixin(LivingEntity.class)
public abstract class ManifestTotemMixin {

    @Unique
    private boolean zhonz$manifestTriggered = false;

    @Inject(method = "checkTotemDeathProtection", at = @At("HEAD"))
    private void zhonz$preTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        zhonz$manifestTriggered = hasManifest(self.getItemBySlot(EquipmentSlot.MAINHAND))
                || hasManifest(self.getItemBySlot(EquipmentSlot.OFFHAND));
    }

    @Inject(method = "checkTotemDeathProtection", at = @At("RETURN"))
    private void zhonz$postTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && zhonz$manifestTriggered) {
            LivingEntity self = (LivingEntity) (Object) this;
            for (LivingEntity e : self.level().getEntitiesOfClass(LivingEntity.class, self.getBoundingBox().inflate(16.0))) {
                if (e != self && e.isAlive()) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 10, false, false));
                }
            }
            self.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 600, 4, false, false));
        }
        zhonz$manifestTriggered = false;
    }

    @Unique
    private static boolean hasManifest(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.TOTEM_OF_UNDYING)
                && stack.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.MANIFEST)) > 0;
    }
}
