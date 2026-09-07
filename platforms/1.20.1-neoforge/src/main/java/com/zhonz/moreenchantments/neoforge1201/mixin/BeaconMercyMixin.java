package com.zhonz.moreenchantments.neoforge1201.mixin;

import dev.shadowsoffire.attributeslib.api.ALObjects;
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
import org.spongepowered.asm.mixin.Unique;
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
 * 同时把等级写入信标 BlockEntity 的持久数据(Forge 1.20.1 BlockEntity.getPersistentData() 亦自动持久化)。
 *
 * 均分属性: ATTACK_DAMAGE / ATTACK_SPEED / CRIT_CHANCE / CRIT_DAMAGE /
 * ARROW_DAMAGE / CURRENT_HP_DAMAGE。
 * TODO: 1.21 另有 PROJECTILE_DAMAGE —— 该属性在 1.20.1 attributeslib(1.3.7)
 * ALObjects 中不存在(1.21 才加入 ApothicAttributes), 本平台不移植该项。
 *
 * 1.21.1 NeoForge → 1.20.1 Forge 移植差异:
 * - 注入点两版相同: 1.20.1 BeaconBlockEntity 亦有 public static void tick(Level, BlockPos,
 *   BlockState, BeaconBlockEntity)(Forge 47.3.0 jar 核实), 静态 handler 签名不变。
 * - 1.21 属性为 Holder<Attribute>(List.of), 1.20.1 为 Attribute; Apothic 1.20.1
 *   (attributeslib 1.3.7) 的 ALObjects.Attributes 字段是 RegistryObject<Attribute>(包名
 *   dev.shadowsoffire.attributeslib.api, 非 1.21 的 apothic_attributes) → 需 .get()。
 * - 为避免 Apothic 未加载时 mixin 类静态初始化即崩溃, 属性列表改为惰性构建 + null 跳过
 *   (原版两属性恒在; Apothic 四属性缺失则该项不参与均分)。
 * - 均分只动 base 值、保留 modifier, 与 1.21 相同。
 */
@Mixin(BeaconBlockEntity.class)
public abstract class BeaconMercyMixin {

    /** 惰性缓存(不参与静态初始化, 避免 attributeslib 缺载时类初始化崩溃)。 */
    @Unique
    private static List<Attribute> zhonz$mercyAttributesCache = null;

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
        if (players.size() < 2) return; // 单人不需均分(可视为"没有变化")

        for (Attribute attr : zhonz$mercyAttributes()) {
            zhonz$averageAttribute(players, attr);
        }
    }

    @Unique
    private static void zhonz$averageAttribute(List<ServerPlayer> players, Attribute attr) {
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

    /** 原版两属性 + Apothic(attributeslib)四属性; Apothic 缺失/未注册项跳过。 */
    @Unique
    private static List<Attribute> zhonz$mercyAttributes() {
        if (zhonz$mercyAttributesCache != null) return zhonz$mercyAttributesCache;
        List<Attribute> list = new ArrayList<>();
        list.add(Attributes.ATTACK_DAMAGE);
        list.add(Attributes.ATTACK_SPEED);
        zhonz$add(list, ALObjects.Attributes.CRIT_CHANCE.get());
        zhonz$add(list, ALObjects.Attributes.CRIT_DAMAGE.get());
        zhonz$add(list, ALObjects.Attributes.ARROW_DAMAGE.get());
        // TODO: 1.21 有 ALObjects.Attributes.PROJECTILE_DAMAGE, 1.20.1 attributeslib 无此属性
        zhonz$add(list, ALObjects.Attributes.CURRENT_HP_DAMAGE.get());
        zhonz$mercyAttributesCache = list;
        return list;
    }

    @Unique
    private static void zhonz$add(List<Attribute> list, Attribute attr) {
        if (attr != null && !list.contains(attr)) list.add(attr);
    }
}
