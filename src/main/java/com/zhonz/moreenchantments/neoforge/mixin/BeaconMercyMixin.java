package com.zhonz.moreenchantments.neoforge.mixin;

import dev.shadowsoffire.apothic_attributes.api.ALObjects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * "慈悲之下,众生平等": 已绑定的信标(持久数据带 zhonz_mercy_level)在其原版
 * 作用范围内, 周期性地把范围内所有玩家的"攻击类属性"均分。
 *
 * 绑定方式: 手持带慈悲附魔的物品右键信标 → 消耗该附魔(移除物品上的慈悲),
 * 同时把等级写入信标 BlockEntity 的持久数据(NeoForge 自动持久化)。
 *
 * 均分属性: ATTACK_DAMAGE / ATTACK_SPEED / CRIT_CHANCE / CRIT_DAMAGE /
 * ARROW_DAMAGE / PROJECTILE_DAMAGE / CURRENT_HP_DAMAGE。
 */
@Mixin(BeaconBlockEntity.class)
public abstract class BeaconMercyMixin {

    private static final List<net.minecraft.core.Holder<Attribute>> MERCY_ATTRIBUTES = List.of(
            Attributes.ATTACK_DAMAGE,
            Attributes.ATTACK_SPEED,
            ALObjects.Attributes.CRIT_CHANCE,
            ALObjects.Attributes.CRIT_DAMAGE,
            ALObjects.Attributes.ARROW_DAMAGE,
            ALObjects.Attributes.PROJECTILE_DAMAGE,
            ALObjects.Attributes.CURRENT_HP_DAMAGE
    );

    @Inject(method = "tick", at = @At("HEAD"))
    private static void zhonz$mercyEqualTick(Level level, BlockPos pos, BlockState state,
                                             BeaconBlockEntity beacon, CallbackInfo ci) {
        if (level.isClientSide()) return;
        CompoundTag data = beacon.getPersistentData();
        int mercyLevel = data.getInt("zhonz_mercy_level");
        if (mercyLevel <= 0) return;
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (level.getGameTime() % 40 != 0) return; // 每 2 秒一次, 避免每 tick 刷新

        int range = 10 + mercyLevel * 10; // 原版信标层级范围(10/20/30/40/50)
        List<ServerPlayer> players = serverLevel.getPlayers(p ->
                p.position().distanceToSqr(beacon.getBlockPos().getCenter()) <= (double) range * range);
        if (players.size() < 2) return; // 单人不需均分(可视为“没有变化”)

        for (var holder : MERCY_ATTRIBUTES) {
            averageAttribute(players, holder);
        }
    }

    private static void averageAttribute(List<ServerPlayer> players, net.minecraft.core.Holder<Attribute> attr) {
        List<AttributeInstance> instances = new ArrayList<>();
        double sum = 0.0;
        int count = 0;
        for (Player p : players) {
            AttributeInstance inst = p.getAttribute(attr);
            if (inst == null) continue;
            instances.add(inst);
            sum += inst.getBaseValue();
            count++;
        }
        if (count == 0) return;
        double avg = sum / count;
        // 仅均分 base 值; 保留各自 modifier(多数玩家没有), 并把差异收拢到 base
        for (AttributeInstance inst : instances) {
            if (Math.abs(inst.getBaseValue() - avg) > 0.0001) {
                inst.setBaseValue(avg);
            }
        }
    }
}
