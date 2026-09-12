package com.zhonz.moreenchantments.neoforge1201.mixin;

import com.zhonz.moreenchantments.neoforge1201.ManifestHelper1201;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "于此显圣" 的原版图腾路径: 不死图腾被触发消耗时,周围全部生物无法移动3秒,自身获得30秒抗性提升V。
 *
 * 其余免死路径(智能图腾 / 自地狱中归来 / 神护)不走原版 checkTotemDeathProtection,
 * 由 {@code SideEffectsBatch1} 的死亡侧处理器调用 {@link ManifestHelper1201#burst} 触发。
 * 注入点用 RETURN, 保证在原版 removeAllEffects() 之后施放(否则抗性提升会被清掉)。
 *
 * 1.21.1 NeoForge → 1.20.1 NeoForge 移植差异: 注入点 LivingEntity.checkTotemDeathProtection(DamageSource)
 * 两版均存在; 附魔等级查询差异已收敛到 ManifestHelper1201。
 */
@Mixin(LivingEntity.class)
public abstract class ManifestTotemMixin {

    @Unique
    private boolean zhonz$manifestTriggered = false;

    @Inject(method = "checkTotemDeathProtection", at = @At("HEAD"))
    private void zhonz$preTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        zhonz$manifestTriggered = ManifestHelper1201.holdsManifest(self);
    }

    @Inject(method = "checkTotemDeathProtection", at = @At("RETURN"))
    private void zhonz$postTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && zhonz$manifestTriggered) {
            ManifestHelper1201.burst((LivingEntity) (Object) this);
        }
        zhonz$manifestTriggered = false;
    }
}
