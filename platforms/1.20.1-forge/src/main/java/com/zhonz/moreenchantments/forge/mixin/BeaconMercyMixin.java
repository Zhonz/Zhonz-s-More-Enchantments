package com.zhonz.moreenchantments.forge.mixin;

import dev.shadowsoffire.attributeslib.api.ALObjects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * "慈悲之下,众生平等": 已绑定的信标在作用范围内周期性地把范围内玩家的攻击类属性均分。
 *
 * <h2>2026-09 缺陷修复: 不再改写 base 值(与 1.21.1 同步)</h2>
 * 旧实现直接 {@code inst.setBaseValue(avg)} —— base 值会随玩家存档落盘, 于是只要进过一次
 * 信标范围, 7 项属性就被**永久**改写(拆信标/走远/下线都不恢复), 还会波及范围内完全没有
 * 本模组附魔的原版玩家, 且没有任何还原路径。
 *
 * <p>现在改为写入**临时修饰符**(ADDITION delta), 只影响运行时总值:
 * 在范围内每 2 秒重算一次 delta; 不在范围内的玩家会被同维度下一次信标 tick 撤回修饰符。
 * 临时修饰符不进存档 → 即便漏了清理也不会污染存档。
 *
 * <p>1.20.1 的 AttributeModifier 以 UUID 标识且无按 ResourceLocation 移除的 API,
 * 故用"稳定 UUID + name 匹配遍历"实现移除(与 {@code AttributeAccess1201} 同一手法)。
 */
@Mixin(BeaconBlockEntity.class)
public abstract class BeaconMercyMixin {

    @Unique
    private static final String ZHONZ_MERCY_MARK = "zhonz_mercy_mark";

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

        // 清理: 本维度在线玩家里不在本次范围内、但带着均分痕迹的 → 撤回修饰符
        for (ServerPlayer p : serverLevel.players()) {
            if (!players.contains(p) && p.getPersistentData().getBoolean(ZHONZ_MERCY_MARK)) {
                zhonz$clearMercyModifiers(p);
            }
        }

        if (players.size() < 2) return; // 单人不需均分(可视为"没有变化")

        for (Attribute attr : zhonz$mercyAttributes()) {
            zhonz$averageAttribute(players, attr);
        }
    }

    /** 用总值的平均作为目标, 并以临时修饰符把每人的总值拉到平均值(不动 base)。 */
    @Unique
    private static void zhonz$averageAttribute(List<ServerPlayer> players, Attribute attr) {
        List<ServerPlayer> valid = new ArrayList<>();
        List<AttributeInstance> instances = new ArrayList<>();
        double sum = 0.0;
        for (ServerPlayer p : players) {
            AttributeInstance inst = p.getAttribute(attr);
            if (inst == null) continue;
            valid.add(p);
            instances.add(inst);
            sum += inst.getValue(); // 总值(含已有修饰符), 而不是 base
        }
        if (valid.isEmpty()) return;
        double avg = sum / valid.size();
        String name = zhonz$mercyModifierName(attr);
        for (int i = 0; i < instances.size(); i++) {
            AttributeInstance inst = instances.get(i);
            zhonz$removeMercyModifier(inst, attr);      // 先撤回旧值 → 幂等, 不会叠加
            double delta = avg - inst.getValue();
            if (Math.abs(delta) > 0.0001) {
                UUID uuid = UUID.nameUUIDFromBytes(("zhonz:" + name).getBytes(StandardCharsets.UTF_8));
                inst.addTransientModifier(new AttributeModifier(uuid, name, delta,
                        AttributeModifier.Operation.ADDITION));
                valid.get(i).getPersistentData().putBoolean(ZHONZ_MERCY_MARK, true);
            }
        }
    }

    @Unique
    private static void zhonz$clearMercyModifiers(ServerPlayer player) {
        for (Attribute attr : zhonz$mercyAttributes()) {
            AttributeInstance inst = player.getAttribute(attr);
            if (inst != null) zhonz$removeMercyModifier(inst, attr);
        }
        player.getPersistentData().remove(ZHONZ_MERCY_MARK);
    }

    @Unique
    private static String zhonz$mercyModifierName(Attribute attr) {
        ResourceLocation rl = ForgeRegistries.ATTRIBUTES.getKey(attr);
        return "zhonz_mercy_avg_" + (rl == null ? "unknown" : rl.getPath());
    }

    /** 1.20.1 无按 id 移除的 API → 按 name 在实例的 modifier 列表里找出来删。 */
    @Unique
    private static void zhonz$removeMercyModifier(AttributeInstance inst, Attribute attr) {
        String name = zhonz$mercyModifierName(attr);
        AttributeModifier toRemove = null;
        for (AttributeModifier m : inst.getModifiers()) {
            if (m.getName().equals(name)) {
                toRemove = m;
                break;
            }
        }
        if (toRemove != null) {
            inst.removeModifier(toRemove);
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