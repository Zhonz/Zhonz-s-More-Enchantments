package com.zhonz.moreenchantments.neoforge.mixin;

import dev.shadowsoffire.apothic_attributes.api.ALObjects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * "慈悲之下,众生平等": 已绑定的信标(持久数据带 {@code zhonz_mercy_level})在其原版
 * 作用范围内, 周期性地把范围内所有玩家的"攻击类属性"均分。
 *
 * <p>绑定方式: 手持带慈悲附魔的物品右键信标 → 消耗该附魔(移除物品上的慈悲),
 * 同时把等级写入信标 BlockEntity 的持久数据(NeoForge 自动持久化)。
 *
 * <p>均分属性: ATTACK_DAMAGE / ATTACK_SPEED / CRIT_CHANCE / CRIT_DAMAGE /
 * ARROW_DAMAGE / PROJECTILE_DAMAGE / CURRENT_HP_DAMAGE。
 *
 * <h2>2026-09 缺陷修复: 不再改写 base 值</h2>
 * 旧实现直接 {@code inst.setBaseValue(avg)} —— base 值会随玩家存档落盘, 于是:
 * <ul>
 *   <li>只要进过一次信标范围, 玩家 7 项属性就被**永久**改写, 拆信标/走远/下线都不恢复;</li>
 *   <li>波及范围内**完全没有本模组附魔的原版玩家**(他们只是站在信标旁);</li>
 *   <li>"绝对还原"没有任何路径 —— 存档里也找不到原值。</li>
 * </ul>
 * 现在改为写入**临时修饰符**({@code ADD_VALUE} delta), 只影响运行时的总值:
 * <ul>
 *   <li>在范围内: 每 2 秒重算一次 delta(先撤回自己的旧修饰符), 使总值收敛到平均值;</li>
 *   <li>离开范围: 该信标的下一次 tick 就会撤回修饰符, 属性回到原样(不再改 base);</li>
 *   <li>临时修饰符本就不进存档, 重登必然消失 —— 即便极端情况漏了清理也不会污染存档。</li>
 * </ul>
 * 平均值也改为按 {@code getValue()}(含修饰符的总值)计算, 与"属性平均分配"语义一致。
 */
@Mixin(BeaconBlockEntity.class)
public abstract class BeaconMercyMixin {

    /** 均分属性的临时修饰符 id 前缀(按属性名区分, 7 项互不覆盖)。 */
    private static final String MERCY_MODIFIER_PREFIX = "zhonz_mercy_avg_";

    /** 玩家 persistentData 里的标记: 当前是否有本模组加上的均分修饰符。 */
    private static final String KEY_MERCY_MARK = "zhonz_mercy_mark";

    private static final List<Holder<Attribute>> MERCY_ATTRIBUTES = List.of(
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

        // 清理: 本维度所有在线玩家里, 不在本次范围内但带着均分痕迹的 → 撤回修饰符
        for (ServerPlayer p : serverLevel.players()) {
            if (!players.contains(p) && p.getPersistentData().getBoolean(KEY_MERCY_MARK)) {
                clearMercyModifiers(p);
            }
        }

        if (players.size() < 2) return; // 单人不需均分(可视为"没有变化")
        for (Holder<Attribute> holder : MERCY_ATTRIBUTES) {
            averageAttribute(players, holder);
        }
    }

    /**
     * 把一组玩家的某个属性总值拉到平均值上 —— 通过临时修饰符, <b>不改 base 值</b>。
     */
    private static void averageAttribute(List<ServerPlayer> players, Holder<Attribute> attr) {
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
        ResourceLocation id = modifierId(attr);
        for (int i = 0; i < instances.size(); i++) {
            AttributeInstance inst = instances.get(i);
            // 先撤回自己的旧修饰符再重算 delta → 幂等, 反复刷新不会叠加
            inst.removeModifier(id);
            double delta = avg - inst.getValue();
            if (Math.abs(delta) > 0.0001) {
                inst.addTransientModifier(new AttributeModifier(id, delta,
                        AttributeModifier.Operation.ADD_VALUE));
                valid.get(i).getPersistentData().putBoolean(KEY_MERCY_MARK, true);
            }
        }
    }

    /** 撤回本模组加的所有均分修饰符。 */
    private static void clearMercyModifiers(ServerPlayer player) {
        for (Holder<Attribute> holder : MERCY_ATTRIBUTES) {
            AttributeInstance inst = player.getAttribute(holder);
            if (inst != null) {
                inst.removeModifier(modifierId(holder));
            }
        }
        player.getPersistentData().remove(KEY_MERCY_MARK);
    }

    /** 每个属性一个独立 id, 避免 7 项属性互相 removeModifier 覆盖。 */
    private static ResourceLocation modifierId(Holder<Attribute> attr) {
        String path = attr.unwrapKey().map(k -> k.location().getPath()).orElse("unknown");
        return ResourceLocation.fromNamespaceAndPath(
                com.zhonz.moreenchantments.common.CommonConstants.MODID, MERCY_MODIFIER_PREFIX + path);
    }
}
