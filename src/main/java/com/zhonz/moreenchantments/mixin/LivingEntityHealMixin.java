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

    @ModifyVariable(method = "heal(F)V", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private float modifyHeal(float amount) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (self.level().isClientSide()) return amount;

        var data = EntityDataStorage.getEntityData(self);
        if (data.contains("zhonz_grievous_wound_until")) {
            long until = data.getLong("zhonz_grievous_wound_until");
            if (self.level().getGameTime() < until) {
                // 减少30%治疗效果
                return amount * 0.7f;
            } else {
                data.remove("zhonz_grievous_wound_until");
            }
        }
        return amount;
    }
}
