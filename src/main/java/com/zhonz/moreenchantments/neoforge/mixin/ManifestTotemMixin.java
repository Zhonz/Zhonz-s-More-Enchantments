package com.zhonz.moreenchantments.neoforge.mixin;

import com.zhonz.moreenchantments.util.ManifestHelper;
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
 * 由 {@code ModEventHandlers} 的死亡侧处理器调用 {@link ManifestHelper#burst} 触发。
 * 注入点用 RETURN, 保证在原版 removeAllEffects() 之后施放(否则抗性提升会被清掉)。
 */
@Mixin(LivingEntity.class)
public abstract class ManifestTotemMixin {

    @Unique
    private boolean zhonz$manifestTriggered = false;

    @Inject(method = "checkTotemDeathProtection", at = @At("HEAD"))
    private void zhonz$preTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        // 方案(a) 图腾免死是服务端权威逻辑; 且 ManifestHelper 内部解析 Holder,
        // 远程客户端没有服务器 registry 会 NPE → 客户端不登记(顺带清掉标记)。
        if (self.level().isClientSide()) {
            zhonz$manifestTriggered = false;
            return;
        }
        zhonz$manifestTriggered = ManifestHelper.holdsManifest(self);
    }

    @Inject(method = "checkTotemDeathProtection", at = @At("RETURN"))
    private void zhonz$postTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && zhonz$manifestTriggered) {
            ManifestHelper.burst((LivingEntity) (Object) this);
        }
        zhonz$manifestTriggered = false;
    }
}
