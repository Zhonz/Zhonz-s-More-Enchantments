package com.zhonz.moreenchantments.mixin;

import com.zhonz.moreenchantments.event.EntityDataStorage;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Mixin to reduce healing by 30% when the entity has the Grievous Wound effect
 * (重伤) applied. The key {@code zhonz_grievous_wound_until} is set by
 * ModEventHandlers when the entity is attacked with the Grievous Wound enchantment.
 */
@Mixin(LivingEntity.class)
public class LivingEntityHealMixin {

    private static final String KEY_GRIEVOUS_WOUND_UNTIL = "zhonz_grievous_wound_until";
    /** 重伤附魔的治疗减少系数(原值的 70%). */
    private static final float GRIEVOUS_WOUND_HEAL_FACTOR = 0.7f;

    @ModifyVariable(method = "heal(F)V", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private float modifyHeal(float amount) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (self.level().isClientSide()) return amount;

        var data = EntityDataStorage.getEntityData(self);
        if (!data.contains(KEY_GRIEVOUS_WOUND_UNTIL)) return amount;

        long until = data.getLong(KEY_GRIEVOUS_WOUND_UNTIL);
        if (self.level().getGameTime() >= until) {
            data.remove(KEY_GRIEVOUS_WOUND_UNTIL);
            return amount;
        }
        return amount * GRIEVOUS_WOUND_HEAL_FACTOR;
    }
}
