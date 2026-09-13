package com.zhonz.moreenchantments.neoforge1201;

import com.zhonz.moreenchantments.common.damage.UnifiedDamageEngine;
import com.zhonz.moreenchantments.common.enchant.EnchantIds;
import com.zhonz.moreenchantments.common.storage.EntityDataStorage;
import dev.shadowsoffire.attributeslib.api.ALObjects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 1.20.1 Forge: 附魔【攻击侧】缺失效果补齐(AttackSideBatch1)。
 *
 * <p>1.21 源: 根工程 {@code ModEventHandlers.java}
 * (src/main/java/com/zhonz/moreenchantments/event/ModEventHandlers.java)。
 * 每个方法 javadoc 标注 1.21 源函数名与行号, 函数体按 PORTING_CONTRACT 的 API 映射表改写。
 *
 * <p><b>相位约定(按 1.21 原相位, 不做"就近"简化)</b>
 * <ul>
 *   <li><b>护甲前相位</b> —— 1.21 {@code LivingIncomingDamageEvent}(onLivingHurt, rawDamage):
 *       1 终结 / 3 收割 / 倏忽恩赐受击累积。1.20.1 等价事件为
 *       {@link net.minecraftforge.event.entity.living.LivingHurtEvent}
 *       (Forge 在 {@code LivingEntity.hurt} 护甲吸收之前触发, 取消即完全取消本次伤害),
 *       由本类的 {@link #onLivingHurt} 订阅。若把它们放到 LivingDamageEvent(护甲后)会
 *       让"护甲前 rawDamage"变成护甲后数值, 与 1.21 数值口径不一致。</li>
 *   <li><b>护甲后相位</b> —— 1.21 {@code LivingDamageEvent.Pre} 的攻击链
 *       (applyAttackerEnchantments): 38 终点倒计时 / 39 抹去 / 40 目不能追 /
 *       47 剧烈搏动 / 50 新太阳 / 11 我的海疆标记 / 36↔37 皮肉筋骨切换,
 *       由 {@link ForgeEventHandler1201#onLivingDamage} 的攻击段调用(见该类内的接线注释)。</li>
 *   <li><b>tick 相位</b> —— {@link #tickBoneBreakRevert} 由 {@link EnchantWiring1201#onPlayerTick} 调用。</li>
 * </ul>
 *
 * <p><b>flat 通道(倏忽恩赐 / 目不能追)相位说明</b>: 1.21 在
 * {@code applyAttackerEnchantments} 内 setFlatDamage, 随后同一次命中由
 * {@code applyUnifiedDamageAttributes} 结算 → 结算后 {@code clearEventFlatTemporary} 清除。
 * 1.20.1 的结算发生在 {@link ForgeEventHandler1201#onLivingDamage}, 因此这两个方法必须由
 * 调用方在"读 flat 属性值 / settle 之前"调用, 并在 settle 之后调用 {@link #clearTransientFlat}
 * —— ForgeEventHandler1201 已按该顺序接线。
 *
 * <p><b>1.21 → 1.20.1 API 适配</b>
 * <ul>
 *   <li>附魔等级: {@link EnchantmentLookup1201#INSTANCE}.mainHand/anySlot/slot(entity, id),
 *       id 取 {@link EnchantIds} 常量(1.21 的 ModEnchantments.X / getXEnchantmentLevel)。</li>
 *   <li>物品附魔读写: 1.20.1 无 DataComponents, 用
 *       {@code ForgeRegistries.ENCHANTMENTS.getValue(rl)} +
 *       {@code ItemStack.getEnchantmentLevel(Enchantment)} /
 *       {@code EnchantmentHelper.get|setEnchantments}。</li>
 *   <li>状态数据键与 1.21 源 KEY_xxx 常量值完全一致(内联字符串), 供 common 读侧共用
 *       (例: "zhonz_my_sea_domain_start" 由 ForgeEventHandler1201.incomingConditionalFactor 读)。</li>
 *   <li>{@code ItemStack.hurtAndBreak(int, LivingEntity, EquipmentSlot)} 是 1.21 API;
 *       1.20.1 用 {@code hurtAndBreak(int, LivingEntity, Consumer)}。</li>
 *   <li>33. 不完整的预知眼闪避({@link #tryForeknowledgeDodge} / {@link #tickIncompleteForeknowledge})
 *       已移植: 1.20.1 的等价相位同为护甲前的 {@code LivingHurtEvent}(Apothic 在此相位处理
 *       DODGE_CHANCE 并取消事件表示闪避)。1.21 的 {@code AttributeEvents.isDodging} 兜底分支
 *       (自行取消 + 位移 1/4 格)经 {@code dev.shadowsoffire.attributeslib.impl.AttributeEvents}
 *       等价实现。tick 侧(概率恢复/sync DODGE_CHANCE/低概率反胃)由
 *       {@link EnchantWiring1201#onPlayerTick} 调用。</li>
 * </ul>
 */
public final class AttackSideBatch1 {

    private static final Logger LOGGER = LoggerFactory.getLogger("ZhonzMoreEnchantments1201");

    /** 1.21 源: ModEventHandlers#RANDOM —— 预知眼闪避位移方向。 */
    private static final net.minecraft.util.RandomSource RANDOM = net.minecraft.util.RandomSource.create();

    // ===== Persistent Data 键(与 1.21 ModEventHandlers 常量值一致, 直接内联)=====
    /** 1.21: KEY_FLEETING_GRACE_STORED(L73)。 */
    private static final String KEY_FLEETING_GRACE_STORED = "zhonz_fleeting_grace_stored";
    /** 1.21: KEY_FLEET_LAST_ATTACK(L100)。 */
    private static final String KEY_FLEET_LAST_ATTACK = "zhonz_fleet_last_attack";
    /** 1.21: KEY_COUNTDOWN_UNTIL(L96)。 */
    private static final String KEY_COUNTDOWN_UNTIL = "zhonz_final_countdown_until";
    /** 1.21: KEY_COUNTDOWN_DAMAGE(L97)。 */
    private static final String KEY_COUNTDOWN_DAMAGE = "zhonz_final_countdown_damage";
    /** 1.21: KEY_MY_SEA_DOMAIN_START(L75); 由 ForgeEventHandler1201.incomingConditionalFactor 消费。 */
    private static final String KEY_MY_SEA_DOMAIN_START = "zhonz_my_sea_domain_start";
    /** 1.21: KEY_BONE_BREAK_SWITCH_TICK(L93)。 */
    private static final String KEY_BONE_BREAK_SWITCH_TICK = "zhonz_bone_break_switch_tick";

    // ===== 33. 不完整的预知眼(与 1.21 ModEventHandlers L101-103 常量值一致)=====
    /** 1.21: KEY_FOREKNOWLEDGE_DODGE(L101) —— 当前闪避概率(初始 0.80)。 */
    private static final String KEY_FOREKNOWLEDGE_DODGE = "zhonz_foreknowledge_dodge_prob";
    /** 1.21: KEY_FOREKNOWLEDGE_LAST_COMBAT(L102) —— 上次战斗 tick(用于脱战恢复)。 */
    private static final String KEY_FOREKNOWLEDGE_LAST_COMBAT = "zhonz_foreknowledge_last_combat";
    /** 1.21: KEY_FOREKNOWLEDGE_DODGE_APPLIED(L103) —— 上次已同步到 Apothic 的值。 */
    private static final String KEY_FOREKNOWLEDGE_DODGE_APPLIED = "zhonz_foreknowledge_dodge_applied";
    /** 1.21: FOREKNOWLEDGE_DODGE_MODIFIER(L170)。 */
    private static final ResourceLocation FOREKNOWLEDGE_DODGE_MODIFIER = rl("foreknowledge_dodge");
    /** 1.21: 闪避概率上下限与恢复速率(L465/L475)。 */
    private static final float FOREK_MIN_PROB = 0.05F;
    private static final float FOREK_MAX_PROB = 0.80F;
    private static final float FOREK_FAIL_PENALTY = 0.10F;
    private static final float FOREK_RECOVER_PER_TICK = 0.0005F;
    private static final long FOREK_RECOVER_DELAY_TICKS = 40L;

    /** 1.21: FINAL_COUNTDOWN_TICKS(L98) = 10 秒。 */
    private static final int FINAL_COUNTDOWN_TICKS = 200;
    /** 1.21: 皮肉→筋骨切换后 60 tick(3 秒)还原。 */
    private static final int BONE_BREAK_REVERT_TICKS = 60;
    /** 1.21: 终结触发所需的攻击力下限(L369)。 */
    private static final double FINALE_MIN_ATTACK_DAMAGE = 7.0D;

    /** flat 事件临时 modifier id(与 1.21 EVENT_FLAT_GRACE / EVENT_FLAT_FLEET 同名, 便于对账)。 */
    private static final ResourceLocation EVENT_FLAT_GRACE = rl("event_flat_grace");
    private static final ResourceLocation EVENT_FLAT_FLEET = rl("event_flat_fleet");

    private AttackSideBatch1() {
    }

    private static ResourceLocation rl(String path) {
        return new ResourceLocation(CommonConstants1201.MODID, path);
    }

    private static CompoundTag edata(LivingEntity entity) {
        return EntityDataStorage.getEntityData(entity);
    }

    private static int mainHand(LivingEntity entity, String id) {
        return EnchantmentLookup1201.INSTANCE.mainHand(entity, id);
    }

    private static int anySlot(LivingEntity entity, String id) {
        return EnchantmentLookup1201.INSTANCE.anySlot(entity, id);
    }

    private static int slot(LivingEntity entity, String id, EquipmentSlot equipmentSlot) {
        return EnchantmentLookup1201.INSTANCE.slot(entity, id, equipmentSlot);
    }

    private static Enchantment ench(String id) {
        return ForgeRegistries.ENCHANTMENTS.getValue(rl(id));
    }

    private static AttributeInstance flatInstance(LivingEntity entity) {
        return entity.getAttribute(ZhonzAttributes1201.FLAT_DAMAGE.get());
    }

    /** 1.21 源: ModEventHandlers#isMeleeWeapon(L296)。 */
    private static boolean isMeleeWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        net.minecraft.world.item.Item item = stack.getItem();
        return item != Items.BOW && item != Items.CROSSBOW && item != Items.TRIDENT
                && item != Items.FISHING_ROD;
    }

    /** 事件总线接线(由 {@link EnchantWiring1201#register()} 调用)。 */
    public static void register() {
        MinecraftForge.EVENT_BUS.register(AttackSideBatch1.class);
    }

    // ===================================================================
    // 护甲前相位:LivingHurtEvent(= 1.21 LivingIncomingDamageEvent, rawDamage 口径)
    // ===================================================================

    /**
     * 护甲前攻击侧路由(1.21 源: ModEventHandlers#onLivingHurt L320-346 的
     * 1 终结 / 3 收割 / 倏忽恩赐受击累积 三段)。
     *
     * <p>调用顺序与 1.21 一致: 终结 → 收割 → (预知眼未闪避)记录倏忽恩赐。
     * 终结/收割触发时取消事件并 return(1.20.1 取消 LivingHurtEvent = 本次伤害作废,
     * 与 1.21 取消 LivingIncomingDamageEvent 等价, 后续护甲/伤害链不再执行)。
     *
     * <p>注册顺序要求: 本类必须在 {@code NewEnchantsBatch1} 之前注册, 使"唯有命运"
     * (NewEnchantsBatch1.onLivingHurt 的伤害下限钳制)晚于终结/收割执行 —— 1.21 源中
     * 终结/收割(L336/339)同样先于 applyUnyieldingFateInvuln(L353)。
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity defender = event.getEntity();
        if (defender.level().isClientSide()) return;
        float rawDamage = event.getAmount();
        if (rawDamage <= 0.0F) return;
        DamageSource source = event.getSource();

        if (source.getEntity() instanceof LivingEntity attacker && attacker != defender) {
            // --- 1. 终结 Finale: 主手近战且攻击力 >= 7 → 100000× 伤害处决 ---
            if (tryFinale(attacker, defender, source, rawDamage, event)) return;
            // --- 3. 收割 Harvest: 本次伤害后剩余生命 <= 10%/20%/30% → 立即击杀 ---
            if (tryHarvest(attacker, defender, source, rawDamage, event)) return;
        }

        // --- 33. 不完整的预知眼: 受击闪避(Apothic DODGE_CHANCE 已由 tickIncompleteForeknowledge 同步) ---
        // 1.21: tryForeknowledgeDodge 触发(闪避成功)则不累积倏忽恩赐。
        if (tryForeknowledgeDodge(defender, event)) return;

        // --- 倏忽恩赐: 受击累积(1.21 仅在预知眼未闪避时累积) ---
        if (!event.isCanceled()) {
            recordFleetingGrace(defender, rawDamage);
        }
    }

    /**
     * 33. 不完整的预知眼 —— 闪避判定(1.21 源: ModEventHandlers#tryForeknowledgeDodge L434-478)。
     *
     * <p>1.21 依赖 Apothic 在 {@code LivingIncomingDamageEvent} 中自行取消事件表示闪避成功;
     * 1.20.1 的等价事件是 {@link LivingHurtEvent}(护甲前), Apothic 同样在此相位处理
     * DODGE_CHANCE 并取消事件。因此本方法:
     * <ol>
     *   <li>头盔带该附魔 → 记录战斗 tick(阻止脱战恢复);</li>
     *   <li>事件已被取消(= Apothic 判定闪避成功)→ 按设计向随机方向位移 1/4 格, 返回 true;</li>
     *   <li>事件未被取消 → 用 {@code AttributeEvents.isDodging} 兜底(兼容事件注册顺序差异),
     *       若该 tick 判定为闪避则自行取消事件 + 位移;</li>
     *   <li>闪避失败 → 概率 -10%(下限 0.05), 下次 tick 重新同步到 DODGE_CHANCE。</li>
     * </ol>
     *
     * @return true = 已闪避(调用方须 return, 不再执行受击累积)
     */
    public static boolean tryForeknowledgeDodge(LivingEntity defender, LivingHurtEvent event) {
        if (slot(defender, EnchantIds.INCOMPLETE_FOREKNOWLEDGE_EYE, EquipmentSlot.HEAD) <= 0) return false;
        CompoundTag data = edata(defender);
        data.putLong(KEY_FOREKNOWLEDGE_LAST_COMBAT, defender.level().getGameTime());

        if (event.isCanceled()) {
            dodgeTeleport(defender);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[ForeknowledgeEye] Dodged via Apothic! entity={}", defender.getName().getString());
            }
            return true;
        }

        // Apothic 事件未拦截时兜底: 该 tick 的确定性闪避判定为真则自行闪避(兼容事件顺序差异)
        if (dev.shadowsoffire.attributeslib.impl.AttributeEvents.isDodging(defender)) {
            dodgeTeleport(defender);
            event.setCanceled(true);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[ForeknowledgeEye] Dodged via fallback (apoth isDodging)! entity={}",
                        defender.getName().getString());
            }
            return true;
        }

        // 闪避失败: 概率 -10%, 下次 tick 同步到 DODGE_CHANCE 修饰
        float prob = data.contains(KEY_FOREKNOWLEDGE_DODGE)
                ? data.getFloat(KEY_FOREKNOWLEDGE_DODGE) : FOREK_MAX_PROB;
        float newProb = Math.max(FOREK_MIN_PROB, prob - FOREK_FAIL_PENALTY);
        data.putFloat(KEY_FOREKNOWLEDGE_DODGE, newProb);
        data.putFloat(KEY_FOREKNOWLEDGE_DODGE_APPLIED, -1.0F); // 强制下次 tick 重新同步
        if (LOGGER.isDebugEnabled()) {
            var dodgeAttr = defender.getAttribute(ALObjects.Attributes.DODGE_CHANCE.get());
            LOGGER.debug("[ForeknowledgeEye] Dodge failed! newProb={} dodgeChance={}",
                    newProb, dodgeAttr != null ? dodgeAttr.getValue() : -1.0);
        }
        return false;
    }

    /** 闪避成功: 按设计向随机方向位移 1/4 格(1.21 源 L443-445)。 */
    private static void dodgeTeleport(LivingEntity defender) {
        float angle = RANDOM.nextFloat() * 2.0F * (float) Math.PI;
        defender.teleportTo(defender.getX() + Math.cos(angle) * 0.25, defender.getY(),
                defender.getZ() + Math.sin(angle) * 0.25);
    }

    /**
     * 33. 不完整的预知眼 —— tick 侧(1.21 源: ModEventHandlers#tickIncompleteForeknowledge L2461-2503)。
     *
     * <p>职责: ①未穿戴时移除 DODGE_CHANCE 修饰防残留; ②脱战 2 秒后每秒 +1% 概率回复(上限 0.80);
     * ③概率变化时同步到 Apothic 的 DODGE_CHANCE; ④概率 &lt; 0.20 时每 10 tick 施加反胃。
     */
    public static void tickIncompleteForeknowledge(Player player, int tickCount) {
        CompoundTag data = edata(player);
        int level = slot(player, EnchantIds.INCOMPLETE_FOREKNOWLEDGE_EYE, EquipmentSlot.HEAD);

        if (level <= 0) {
            // 未穿戴: 移除 DODGE_CHANCE 修饰, 防止残留
            if (data.getFloat(KEY_FOREKNOWLEDGE_DODGE_APPLIED) > 0.0F) {
                removeDodgeModifier(player);
                data.putFloat(KEY_FOREKNOWLEDGE_DODGE_APPLIED, 0.0F);
            }
            return;
        }

        long lastCombat = data.getLong(KEY_FOREKNOWLEDGE_LAST_COMBAT);
        // 恢复: 停止战斗 2 秒(40 tick)后开始, 每秒 +1%(0.0005/tick), 约 75 秒从 0.05 回满到 0.80
        if (player.level().getGameTime() - lastCombat > FOREK_RECOVER_DELAY_TICKS) {
            float prob = data.contains(KEY_FOREKNOWLEDGE_DODGE)
                    ? data.getFloat(KEY_FOREKNOWLEDGE_DODGE) : FOREK_MAX_PROB;
            if (prob < FOREK_MAX_PROB) {
                data.putFloat(KEY_FOREKNOWLEDGE_DODGE, Math.min(FOREK_MAX_PROB, prob + FOREK_RECOVER_PER_TICK));
            }
        }

        // 同步当前概率到 Apothic DODGE_CHANCE 修饰(仅在变化时更新)
        float prob = data.contains(KEY_FOREKNOWLEDGE_DODGE)
                ? data.getFloat(KEY_FOREKNOWLEDGE_DODGE) : FOREK_MAX_PROB;
        float applied = data.getFloat(KEY_FOREKNOWLEDGE_DODGE_APPLIED);
        if (Math.abs(applied - prob) > 0.0001F) {
            data.putFloat(KEY_FOREKNOWLEDGE_DODGE_APPLIED, prob);
            AttributeInstance dodge = player.getAttribute(ALObjects.Attributes.DODGE_CHANCE.get());
            if (dodge != null) {
                // 注: Apothic DODGE_CHANCE 以 ADDITION(加和)语义累加概率, 故经 addPercentBonus
                // 写入(该辅助内部先清同 id 残留, 幂等, 不会叠加)。
                UnifiedDamageEngine.addPercentBonus(dodge, FOREKNOWLEDGE_DODGE_MODIFIER, prob);
            }
            if (LOGGER.isDebugEnabled()) {
                var attr = player.getAttribute(ALObjects.Attributes.DODGE_CHANCE.get());
                LOGGER.debug("[ForeknowledgeEye] Synced dodgeChance: prob={} attrValue={}",
                        prob, attr != null ? attr.getValue() : -1.0);
            }
        }

        // 低闪避率时持续反胃模糊视野: 每 10 tick 施加, 持续 60 tick
        if (prob < 0.20F && tickCount % 10 == 0) {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.CONFUSION, 60, 0, false, true));
        }
    }

    /** 移除预知眼的 DODGE_CHANCE 修饰(1.21 源: removeModifier L2468; 写 0% = 仅移除该 id)。 */
    private static void removeDodgeModifier(Player player) {
        AttributeInstance dodge = player.getAttribute(ALObjects.Attributes.DODGE_CHANCE.get());
        if (dodge == null) return;
        UnifiedDamageEngine.addPercentBonus(dodge, FOREKNOWLEDGE_DODGE_MODIFIER, 0.0D);
    }

    /**
     * 1. 终结(1.21 源: ModEventHandlers#tryFinale L362-389)。
     *
     * <p>主手为近战武器且 ATTACK_DAMAGE >= 7 时, 以 rawDamage × 100000 直接扣血(绕过护甲),
     * 扣光则 die(); 同时按新伤害扣除主手耐久(上限 = 剩余耐久)。
     *
     * <p>1.20.1 适配: 相位为 LivingHurtEvent(护甲前), 取消事件 + 直接改生命/死亡,
     * 与 1.21 取消 LivingIncomingDamageEvent + setHealth/die 完全等价。
     *
     * @return true = 已触发并处理完毕(调用方须立即 return, 后续副作用不再执行)
     */
    public static boolean tryFinale(LivingEntity attacker, LivingEntity defender, DamageSource source,
                                    float rawDamage, LivingHurtEvent event) {
        if (mainHand(attacker, EnchantIds.FINALE) <= 0) return false;

        ItemStack mainHand = attacker.getMainHandItem();
        if (!isMeleeWeapon(mainHand)) return false;
        AttributeInstance atk = attacker.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk == null || atk.getValue() < FINALE_MIN_ATTACK_DAMAGE) return false;

        float newDamage = rawDamage * 100000.0F;
        event.setCanceled(true);

        if (mainHand.isDamageableItem()) {
            int durabilityCost = Math.min((int) newDamage, mainHand.getMaxDamage() - mainHand.getDamageValue());
            if (durabilityCost > 0) {
                // 1.20.1: hurtAndBreak(int, LivingEntity, Consumer)(1.21 的 EquipmentSlot 重载不存在)
                mainHand.hurtAndBreak(durabilityCost, attacker, broken -> { });
            }
        }
        float newHealth = Math.max(0.0F, defender.getHealth() - newDamage);
        defender.setHealth(newHealth);
        if (newHealth <= 0.0F) {
            defender.die(source);
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Finale] Triggered! dmg={} target={}", newDamage, defender.getName().getString());
        }
        return true;
    }

    /**
     * 3. 收割(1.21 源: ModEventHandlers#tryHarvest L391-412)。
     *
     * <p>剩余生命(= 当前生命 - 护甲前伤害) > 0 且 <= 最大生命 × 10%×等级 → 立即击杀。
     *
     * @return true = 已触发并击杀(调用方须立即 return)
     */
    public static boolean tryHarvest(LivingEntity attacker, LivingEntity defender, DamageSource source,
                                     float rawDamage, LivingHurtEvent event) {
        int harvestLevel = mainHand(attacker, EnchantIds.HARVEST);
        if (harvestLevel <= 0) return false;

        float remainingHp = defender.getHealth() - rawDamage;
        float threshold = defender.getMaxHealth() * (0.1F * harvestLevel);
        if (remainingHp > 0.0F && remainingHp <= threshold) {
            event.setCanceled(true);
            defender.setHealth(0.0F);
            defender.die(source);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Harvest] Triggered! level={} remaining={} threshold={}",
                        harvestLevel, remainingHp, threshold);
            }
            return true;
        }
        return false;
    }

    /**
     * 倏忽恩赐: 受击累积(1.21 源: ModEventHandlers#recordFleetingGrace L468-472)。
     *
     * <p>全槽带该附魔时, 把本次护甲前伤害累加进 "zhonz_fleeting_grace_stored";
     * 攻击侧 {@link #applyFleetingGraceBonus} 消费(×2 写入 flat 通道)。
     */
    public static void recordFleetingGrace(LivingEntity defender, float rawDamage) {
        if (anySlot(defender, EnchantIds.FLEETING_GRACE) <= 0) return;
        // 防御性守卫(1.20.1 特有): LivingEntity.kill() 内部以 genericKill + Float.MAX_VALUE 走
        // hurt() → 会再次进入本事件(攻击者为 null, 不触发终结/收割, 但会走到这里)。
        // 1.21 源无此守卫, 会把 MAX_VALUE 累积进记录; 玩家数据跨死亡保留 → 之后一次攻击
        // 经 applyFleetingGraceBonus ×2 写入 flat 通道得到 Infinity。此处排除非有限/异常大值。
        if (!Float.isFinite(rawDamage) || rawDamage > 1.0E6F) return;
        CompoundTag data = edata(defender);
        data.putFloat(KEY_FLEETING_GRACE_STORED, data.getFloat(KEY_FLEETING_GRACE_STORED) + rawDamage);
    }

    // ===================================================================
    // 攻击侧 flat 通道(必须在 ForgeEventHandler1201 读 flat / settle 之前调用)
    // ===================================================================

    /**
     * 倏忽恩赐: 攻击附加(1.21 源: ModEventHandlers#applyFleetingGraceBonus L854-862)。
     *
     * <p>把累积受击值 ×2 写入 flat_damage(绝对加伤通道)并清零累积; 同一次命中即生效
     * (1.21 在结算前写入, 故本方法也必须由调用方在 settle 前调用)。
     */
    public static void applyFleetingGraceBonus(LivingEntity attacker) {
        if (anySlot(attacker, EnchantIds.FLEETING_GRACE) <= 0) return;
        CompoundTag data = edata(attacker);
        float stored = data.getFloat(KEY_FLEETING_GRACE_STORED);
        if (stored <= 0.0F) return;
        data.putFloat(KEY_FLEETING_GRACE_STORED, 0.0F);
        UnifiedDamageEngine.setFlatDamage(flatInstance(attacker), EVENT_FLAT_GRACE, stored * 2.0D);
    }

    /**
     * 40. 目不能追, 耳未可即(1.21 源: ModEventHandlers#applyFleetFootstepsDamage L1160-1172)。
     *
     * <p>本次命中额外造成"上次命中伤害 × 10%"(flat 通道), 并把
     * "本次伤害 + 本次加成"记为下次基准。
     *
     * @param preUnified 统一结算前的伤害基准(与 1.21 applyAttackerEnchantments 的 amount 同相位)
     */
    public static void applyFleetFootstepsDamage(LivingEntity attacker, float preUnified) {
        if (anySlot(attacker, EnchantIds.FLEET_FOOTSTEPS) <= 0) return;
        CompoundTag data = edata(attacker);
        float last = data.getFloat(KEY_FLEET_LAST_ATTACK);
        float bonus = last * 0.10F;
        data.putFloat(KEY_FLEET_LAST_ATTACK, preUnified + bonus);
        UnifiedDamageEngine.setFlatDamage(flatInstance(attacker), EVENT_FLAT_FLEET, bonus);
    }

    /** 统一结算后清除 flat 事件临时 modifier(1.21 源: clearEventFlatTemporary L2651-2656)。 */
    public static void clearTransientFlat(LivingEntity attacker) {
        AttributeInstance inst = flatInstance(attacker);
        if (inst == null) return;
        UnifiedDamageEngine.setFlatDamage(inst, EVENT_FLAT_GRACE, 0.0D);
        UnifiedDamageEngine.setFlatDamage(inst, EVENT_FLAT_FLEET, 0.0D);
    }

    /**
     * 41. 节奏(1.21 源: ModEventHandlers#applyRhythm L1192-1216, 调用点 L701)。
     *
     * <p>主手带 rhythm 时计时: 与上次攻击的间隔若在"首次记录的间隔 ±4 tick(0.2 秒)"内,
     * 写入 {@code keyHit} 标志; 命中判定的 ×1.5 <b>乘伤</b>由 common
     * {@code EventDamageConditions.computeConditionalMultiplier} 读取该标志后应用并清除
     * (round13 拆解: 链上只留计时副作用)。
     *
     * <p><b>调用顺序要求</b>: 必须在 {@code computeConditionalMultiplier} <b>之前</b>调用,
     * 否则本 tick 写入的标志要等到下一次命中才被消费 —— 与 1.21 源一致
     * (1.21 在 applyAttackerEnchantments L701 写标志, 同事件 L508 读取)。
     *
     * @param keyLast     上次攻击 tick 的数据键(与 1.21 KEY_RHYTHM_LAST_ATTACK 同值)
     * @param keyRecorded 已记录攻击间隔的数据键(与 1.21 KEY_RHYTHM_RECORDED_INTERVAL 同值)
     * @param keyHit      命中标志的数据键(与 1.21 KEY_RHYTHM_HIT_ATTACK 同值, 由 common 判定侧读取并清除)
     * @return 本次是否命中节奏判定
     */
    public static boolean applyRhythm(LivingEntity attacker, String keyLast, String keyRecorded, String keyHit) {
        if (mainHand(attacker, EnchantIds.RHYTHM) <= 0) return false;
        CompoundTag data = edata(attacker);
        long now = attacker.level().getGameTime();
        long last = data.getLong(keyLast);
        boolean matched = false;
        if (last != 0L) {
            long interval = now - last;
            int recorded = data.getInt(keyRecorded);
            if (recorded == 0) {
                data.putInt(keyRecorded, (int) interval);
            } else if (Math.abs(interval - recorded) <= 4L) {
                matched = true;
                data.putBoolean(keyHit, true);
            }
        }
        data.putLong(keyLast, now);
        return matched;
    }

    // ===================================================================
    // 护甲后相位:攻击命中副作用(LivingDamageEvent 攻击段调用)
    // ===================================================================

    /**
     * 38. 终点倒计时(1.21 源: ModEventHandlers#applyFinalCountdown L1092-1121)。
     *
     * <p>主手带该附魔且目标尚无倒计时 → 标记 200 tick(10 秒)倒计时并清零记录;
     * 到期(server tick 任务)对仍存活的目标造成"期间累积伤害 × 10%"魔法伤害, 然后清标记。
     */
    public static void applyFinalCountdown(LivingEntity attacker, LivingEntity defender) {
        if (mainHand(attacker, EnchantIds.FINAL_COUNTDOWN) <= 0) return;
        CompoundTag d = edata(defender);
        if (d.contains(KEY_COUNTDOWN_UNTIL)) return; // 已有倒计时, 不重复施加

        long until = defender.level().getGameTime() + FINAL_COUNTDOWN_TICKS;
        d.putLong(KEY_COUNTDOWN_UNTIL, until);
        d.putFloat(KEY_COUNTDOWN_DAMAGE, 0.0F);

        if (defender.level() instanceof ServerLevel serverLevel) {
            LivingEntity target = defender;
            serverLevel.getServer().tell(new TickTask(FINAL_COUNTDOWN_TICKS, () -> {
                CompoundTag td = edata(target);
                if (!td.contains(KEY_COUNTDOWN_UNTIL)) return;
                if (target.isAlive()) {
                    float recorded = td.getFloat(KEY_COUNTDOWN_DAMAGE);
                    if (recorded > 0.0F) {
                        target.hurt(target.damageSources().magic(), recorded * 0.1F);
                    }
                }
                td.remove(KEY_COUNTDOWN_UNTIL);
                td.remove(KEY_COUNTDOWN_DAMAGE);
            }));
        }
    }

    /**
     * 38. 终点倒计时: 倒计时期间累积目标所受伤害(1.21 源:
     * ModEventHandlers#accumulateFinalCountdown L1123-1133)。
     *
     * <p>1.21 在受击链(applyDefenderEnchantments L982)调用; 1.20.1 由
     * ForgeEventHandler1201.onLivingDamage 的受击段调用(护甲后 amount 口径一致)。
     */
    public static float accumulateFinalCountdown(LivingEntity defender, float amount) {
        CompoundTag d = edata(defender);
        if (!d.contains(KEY_COUNTDOWN_UNTIL)) return amount;
        if (defender.level().getGameTime() >= d.getLong(KEY_COUNTDOWN_UNTIL)) {
            d.remove(KEY_COUNTDOWN_UNTIL);
            d.remove(KEY_COUNTDOWN_DAMAGE);
            return amount;
        }
        d.putFloat(KEY_COUNTDOWN_DAMAGE, d.getFloat(KEY_COUNTDOWN_DAMAGE) + amount);
        return amount;
    }

    /**
     * 39. 将我抹去, 将你也抹去(1.21 源: ModEventHandlers#applyEraseMe L1136-1157)。
     *
     * <p>主手带该附魔 → 直接击杀目标, 并击杀 10 格内所有同类(同一 EntityType)。
     */
    public static void applyEraseMe(LivingEntity attacker, LivingEntity defender, DamageSource source) {
        if (mainHand(attacker, EnchantIds.ERASE_ME_ERASE_YOU) <= 0) return;
        EntityType<?> type = defender.getType();
        defender.kill();
        for (LivingEntity nearby : defender.level().getEntitiesOfClass(LivingEntity.class,
                defender.getBoundingBox().inflate(10.0))) {
            if (nearby != defender && nearby.isAlive() && nearby.getType() == type) {
                nearby.kill();
            }
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[EraseMe] erased target={} type={}", defender.getName().getString(), type);
        }
    }

    /** 47. 剧烈搏动: 攻击回复 1 生命(1.21 源: ModEventHandlers#applyViolentPulseAttack L1324-1330)。 */
    public static void applyViolentPulseAttack(LivingEntity attacker) {
        if (slot(attacker, EnchantIds.VIOLENT_PULSE, EquipmentSlot.CHEST) <= 0) return;
        if (attacker.getHealth() <= attacker.getMaxHealth() * 0.5F && attacker.isAlive()) {
            attacker.heal(1.0F);
        }
    }

    /** 50. 新太阳: 攻击点燃目标 3 秒(1.21 源: ModEventHandlers#applyNewSun L1377-1382)。 */
    public static void applyNewSunIgnite(LivingEntity attacker, LivingEntity defender) {
        if (slot(attacker, EnchantIds.NEW_SUN, EquipmentSlot.LEGS) <= 0) return;
        defender.setRemainingFireTicks(Math.max(defender.getRemainingFireTicks(), 60)); // 点燃 3 秒
    }

    /**
     * 11. 我的海疆标记(1.21 源: ModEventHandlers#applyMySeaDomainMark L728-732)。
     *
     * <p>主手带该附魔且手持三叉戟 → 在目标数据写入 "zhonz_my_sea_domain_start" = 当前 gameTime;
     * 受击侧易伤(+30%~60%, 1200 tick 内)由
     * {@link ForgeEventHandler1201#incomingConditionalFactor} 读同一键实现。
     */
    public static void applyMySeaDomainMark(LivingEntity attacker, LivingEntity defender) {
        if (mainHand(attacker, EnchantIds.MY_SEA_DOMAIN) <= 0) return;
        if (attacker.getMainHandItem().getItem() != Items.TRIDENT) return;
        edata(defender).putLong(KEY_MY_SEA_DOMAIN_START, defender.level().getGameTime());
    }

    // ===================================================================
    // 36↔37 舍吾皮肉 / 断汝筋骨 切换(LivingDamageEvent 尾部 + PlayerTick)
    // ===================================================================

    /**
     * 36 → 37 舍吾皮肉 → 断汝筋骨(1.21 源: ModEventHandlers#tryFleshToBoneBreak L626-650)。
     *
     * <p>受击者主手带 flesh_sacrifice 时, 把该附魔替换为 bone_break 1 级, 并记录切换 tick;
     * 3 秒后由 {@link #tickBoneBreakRevert} 换回。1.20.1 用
     * {@code EnchantmentHelper.getEnchantments/setEnchantments}(替代 1.21 DataComponents)。
     */
    public static void tryFleshToBoneBreak(LivingEntity defender) {
        ItemStack weapon = defender.getMainHandItem();
        if (weapon.isEmpty()) return;
        Enchantment flesh = ench(EnchantIds.FLESH_SACRIFICE);
        Enchantment bone = ench(EnchantIds.BONE_BREAK);
        if (flesh == null || bone == null) return;
        if (weapon.getEnchantmentLevel(flesh) <= 0) return;

        Map<Enchantment, Integer> enchantments = new HashMap<>(EnchantmentHelper.getEnchantments(weapon));
        enchantments.remove(flesh);
        enchantments.put(bone, 1);
        EnchantmentHelper.setEnchantments(enchantments, weapon);

        if (defender instanceof Player player) {
            player.getPersistentData().putLong(KEY_BONE_BREAK_SWITCH_TICK, player.tickCount);
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[FleshSacrifice] Switched to Bone Break on {}'s weapon at tick {}",
                    defender.getName().getString(), defender.tickCount);
        }
    }

    /**
     * 37 → 36 断汝筋骨 → 舍吾皮肉(3 秒后还原; 1.21 源:
     * ModEventHandlers#tickBoneBreakRevert L2420-2446)。
     *
     * <p>由 {@link EnchantWiring1201#onPlayerTick} 每 tick 调用(1.21 在 onPlayerTick L2097 调用)。
     */
    public static void tickBoneBreakRevert(Player player, CompoundTag data) {
        ItemStack weapon = player.getMainHandItem();
        if (weapon.isEmpty()) return;
        Enchantment flesh = ench(EnchantIds.FLESH_SACRIFICE);
        Enchantment bone = ench(EnchantIds.BONE_BREAK);
        if (flesh == null || bone == null) return;
        if (weapon.getEnchantmentLevel(bone) <= 0) return;

        long switchTick = data.getLong(KEY_BONE_BREAK_SWITCH_TICK);
        if (switchTick == 0L) return; // 尚未发生切换
        if (player.tickCount - switchTick < BONE_BREAK_REVERT_TICKS) return;

        Map<Enchantment, Integer> enchantments = new HashMap<>(EnchantmentHelper.getEnchantments(weapon));
        enchantments.remove(bone);
        enchantments.put(flesh, 1);
        EnchantmentHelper.setEnchantments(enchantments, weapon);
        data.remove(KEY_BONE_BREAK_SWITCH_TICK);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[BoneBreak] Reverted to Flesh Sacrifice on {}'s weapon at tick {}",
                    player.getName().getString(), player.tickCount);
        }
    }
}
