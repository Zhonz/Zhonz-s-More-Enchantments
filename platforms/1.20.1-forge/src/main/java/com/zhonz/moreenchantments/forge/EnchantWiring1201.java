package com.zhonz.moreenchantments.forge;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 1.20.1 Forge 平台: 移植效果统一接线。
 *
 * - 注册 NewEnchantsBatch1(其含 73-89 的 @SubscribeEvent 方法: onPlayerTick/onLivingHurt/
 *   onLivingDamage/onLivingDeath/onShieldBlock/onRightClickBlock/onLivingTick)
 * - 在 PlayerTick 中调用 TickEffectsBatch1 的全部 tick 属性效果(1.21 applyTickAttributeEffects 的
 *   tick 集合移植); data 统一用 player.getPersistentData()
 */
public final class EnchantWiring1201 {

    public static void register() {
        // 攻击侧补齐(批 AttackSideBatch1): LivingHurtEvent 订阅(终结/收割/倏忽恩赐累积)。
        // 必须早于 NewEnchantsBatch1 注册 —— 1.21 源中终结/收割(L336/339)先于
        // applyUnyieldingFateInvuln(L353)执行, 事件总线同优先级按注册顺序调用。
        AttackSideBatch1.register();
        // 73-89 新设计附魔事件订阅(含其自身 tick)
        NewEnchantsBatch1.register();
        // 本类: 补调 TickEffectsBatch1(1-72 tick 属性)与 tick 维护
        MinecraftForge.EVENT_BUS.register(EnchantWiring1201.class);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide()) return;
        CompoundTag data = player.getPersistentData();
        int tc = player.tickCount;

        // ===== 1-72 tick 属性效果(批 A)=====
        TickEffectsBatch1.tickApex(player, data);
        TickEffectsBatch1.tickViolentPulse(player, data);
        TickEffectsBatch1.tickFleetFootsteps(player, data);
        TickEffectsBatch1.tickSelfBound(player, data);
        TickEffectsBatch1.tickRapidAscent(player, data);
        TickEffectsBatch1.tickAcceleratedFuture(player, data);
        TickEffectsBatch1.tickDivineCurse(player, data, tc);
        TickEffectsBatch1.tickPrimalSuffering(player, data);
        TickEffectsBatch1.tickLuxuriousHope(player, data);
        TickEffectsBatch1.tickPhotophile(player, tc);
        TickEffectsBatch1.tickPhotophobe(player, data);
        TickEffectsBatch1.tickEtiquette(player, data);
        TickEffectsBatch1.tickPerfunctory(player, data);
        TickEffectsBatch1.tickCritWeapons(player, data);
        TickEffectsBatch1.tickSelfishClearSky(player);
        TickEffectsBatch1.tickGoldWineCup(player, data);
        TickEffectsBatch1.tickKeenWill(player, data, tc);
        TickEffectsBatch1.tickSharpen(player);
        TickEffectsBatch1.tickHyperthymesia(player);
        TickEffectsBatch1.tickSojourner(player);

        // ===== tick 侧缺失项(批 B: TickSideBatch1, 1.21 ModEventHandlers 尚未移植的 tick 函数)=====
        TickSideBatch1.tickSupremeArt(player, data);
        TickSideBatch1.tickFoolsMask(player, data, tc);
        TickSideBatch1.tickEmergencyRescue(player, data);
        TickSideBatch1.tickCorneredBeast(player, data);
        TickSideBatch1.tickPatience(player, data, tc);
        TickSideBatch1.tickPaleMidnight(player, tc);
        TickSideBatch1.tickHalo(player, tc);
        // 冷却递减 + 过期标记清理(必须: 永劫回归 6000 tick 冷却只在此递减)
        TickSideBatch1.tickCooldownsAndCleanup(player, data, tc);

        // ===== 攻击侧补齐 tick(批 AttackSideBatch1)=====
        // 37→36 断汝筋骨 → 舍吾皮肉: 切换后 3 秒还原(1.21 源 tickBoneBreakRevert L2420)
        AttackSideBatch1.tickBoneBreakRevert(player, data);
    }
}
