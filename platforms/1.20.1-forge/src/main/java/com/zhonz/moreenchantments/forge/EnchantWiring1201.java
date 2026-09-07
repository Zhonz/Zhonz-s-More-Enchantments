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
        TickEffectsBatch1.tickGoldWineCup(player, data);
        TickEffectsBatch1.tickKeenWill(player, data, tc);
        TickEffectsBatch1.tickSharpen(player);
        TickEffectsBatch1.tickHyperthymesia(player);
        TickEffectsBatch1.tickSojourner(player);
    }
}
