package com.zhonz.moreenchantments.neoforge1201.mixin;

import com.zhonz.moreenchantments.common.enchant.EnchantIds;
import com.zhonz.moreenchantments.neoforge1201.EnchantmentLookup1201;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "光环": 夜晚跳过耗时翻倍。
 *
 * 原版: sleepCounter 入睡时每 tick +1(封顶 100, 即 5 秒), {@code isSleepingLongEnough}
 * = isSleeping() && counter>=100; ServerLevel 的 SleepStatus 用该方法过滤"深度睡眠"
 * 玩家, 全部足够后跳过夜晚。
 *
 * 本 mixin 对光环佩戴者额外要求: 连续入睡自计时(见 tickHalo 维护的
 * "zhonz_halo_sleep_ticks")达到 200 tick(10 秒)才放行 → 夜晚跳过耗时翻倍。
 * 只拦截服务端判定, 客户端照常(避免影响跳过夜晚的本地 UI 逻辑)。
 *
 * 1.21.1 NeoForge → 1.20.1 Forge 移植差异:
 * - 注入点 Player.isSleepingLongEnough() 两版均存在(1.20.1 Player 有该方法, 语义同 1.21)。
 * - 附魔等级: 1.21 getEnchantmentLevel(Holder) → 1.20.1 EnchantmentLookup1201.slot(self, HALO, HEAD)。
 * - tick 侧已接线: "zhonz_halo_sleep_ticks" 计数由 {@link TickSideBatch1#tickHalo} 维护
 *   (睡觉 +1 至 200 / 起床清零 / 光环发光 / 睡觉反胃), 由 {@link EnchantWiring1201#onPlayerTick}
 *   调用。本 mixin 只负责服务端放行门(计数由 tick 侧提供)。
 */
@Mixin(Player.class)
public abstract class HaloSleepMixin {

    @Unique
    private static final String KEY_HALO_SLEEP_TICKS = "zhonz_halo_sleep_ticks";
    @Unique
    private static final int HALO_DEEP_SLEEP_REQUIRED = 200; // 100 x 2

    @Inject(method = "isSleepingLongEnough", at = @At("HEAD"), cancellable = true)
    private void zhonz$haloNeedsDoubleSleep(CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        if (self.level().isClientSide()) return; // 只改服务端判定
        if (EnchantmentLookup1201.INSTANCE.slot(self, EnchantIds.HALO, EquipmentSlot.HEAD) <= 0) return;

        int sleptTicks = self.getPersistentData().getInt(KEY_HALO_SLEEP_TICKS);
        if (sleptTicks < HALO_DEEP_SLEEP_REQUIRED) {
            cir.setReturnValue(false);
        }
        // sleptTicks >= 200: 放行, 交由原逻辑(isSleeping && counter>=100)判定
    }
}
