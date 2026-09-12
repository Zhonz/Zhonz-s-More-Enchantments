package com.zhonz.moreenchantments.forge;

import com.zhonz.moreenchantments.common.damage.EnchantSetPieces;
import com.zhonz.moreenchantments.common.enchant.EnchantIds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.registries.ForgeRegistries;

import dev.shadowsoffire.attributeslib.api.ALObjects;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 1.20.1 Forge 平台: 附魔 PlayerTick「属性类」效果移植批 1(对应 1.21 主工程
 * {@code ModEventHandlers.tickXxx} 函数体, 数值保持不变, 数值验证留人工)。
 *
 * <p>这些效果是"写在原版 / Apothic 属性通道上"的 tick 效果, 不依赖 1.21 专属伤害事件:
 * 逐 tick 由调用方(PlayerTickEvent 订阅)传入相同的 {@code data}(见
 * {@code EntityDataStorage.getEntityData(player)} / persistent data)与
 * {@code tickCount = player.tickCount}, 每个函数内部以"上次状态快照"避免每 tick 重复写修饰符。
 *
 * <p><b>1.20.1 API 差异说明</b>(对照 PORTING_CONTRACT 映射表):
 * <ul>
 *   <li>1.21 属性引用为 Holder 风格; 1.20.1 直接用 {@link Attribute}, 写入方式:
 *       {@code inst = player.getAttribute(attr); inst.removeModifier(<UUID>);
 *       inst.addTransientModifier(new AttributeModifier(uuid, name, value, op));}</li>
 *   <li>1.21 的 {@code ALObjects.Attributes.X}(apothic_attributes 包, Holder<Attribute>)
 *       在 1.20.1 是 {@code dev.shadowsoffire.attributeslib.api.ALObjects.Attributes.X},
 *       且字段类型为 {@code RegistryObject<Attribute>}(需 {@code .get()}); 字段名大部分同名。</li>
 *   <li>1.21 无 cooldown_reduction 之外的字段缺口见各方法 TODO。</li>
 *   <li>AttributeModifier 构造 1.21 用 ResourceLocation id; 1.20.1 用
 *       (UUID, name, amount, operation) —— 本文件沿用 AttributeAccess1201 的稳定 UUID 派生
 *       (uuidOf = nameUUIDFromBytes("zhonz:" + path)), name = id.toString()。</li>
 * </ul>
 *
 * 未接线: 本类尚未被任何事件订阅调用, 仅自身可编译(compileJava 验证)。
 */
public final class TickEffectsBatch1 {

    // ===== 修饰符 id(与 1.21 主工程 ModEventHandlers 常量一致, 跨版本对齐便于对账)=====
    private static final ResourceLocation FLEET_FOOTSTEPS_SPEED_MODIFIER = rl("fleet_footsteps_speed");
    private static final ResourceLocation FLEET_FOOTSTEPS_STEP_MODIFIER = rl("fleet_footsteps_step");
    private static final ResourceLocation APEX_DODGE_MODIFIER = rl("apex_dodge");
    private static final ResourceLocation APEX_DAMAGE_MODIFIER = rl("apex_damage");
    private static final ResourceLocation VIOLENT_ATTACK_SPEED_MODIFIER = rl("violent_pulse_attack_speed");
    private static final ResourceLocation VIOLENT_MOVEMENT_MODIFIER = rl("violent_pulse_movement");
    private static final ResourceLocation SELF_BOUND_STEP_MODIFIER = rl("self_bound_step");
    private static final ResourceLocation SELF_BOUND_DAMAGE_MODIFIER = rl("self_bound_damage");
    private static final ResourceLocation SELF_BOUND_MOVEMENT_MODIFIER = rl("self_bound_movement");
    private static final ResourceLocation RAPID_ASCENT_STEP_MODIFIER = rl("rapid_ascent_step");
    private static final ResourceLocation ACCELERATED_DAMAGE_MODIFIER = rl("accelerated_future_damage");
    private static final ResourceLocation ACCELERATED_ATTACK_SPEED_MODIFIER = rl("accelerated_future_attack_speed");
    private static final ResourceLocation DIVINE_CURSE_RANGE_MODIFIER = rl("divine_curse_range");
    private static final ResourceLocation DIVINE_CURSE_BLOCK_RANGE_MODIFIER = rl("divine_curse_block_range");
    private static final ResourceLocation DIVINE_CURSE_DAMAGE_MODIFIER = rl("divine_curse_damage");
    private static final ResourceLocation DIVINE_CURSE_ATTACK_SPEED_MODIFIER = rl("divine_curse_attack_speed");
    private static final ResourceLocation DIVINE_CURSE_MINING_MODIFIER = rl("divine_curse_mining");
    private static final ResourceLocation DIVINE_CURSE_DRAW_MODIFIER = rl("divine_curse_draw");
    private static final ResourceLocation DIVINE_CURSE_COOLDOWN_MODIFIER = rl("divine_curse_cooldown");
    private static final ResourceLocation PRIMAL_ATTACK_SPEED_MODIFIER = rl("primal_attack_speed");
    private static final ResourceLocation PRIMAL_MOVE_MODIFIER = rl("primal_move");
    private static final ResourceLocation PRIMAL_MINING_MODIFIER = rl("primal_mining");
    private static final ResourceLocation PRIMAL_DRAW_MODIFIER = rl("primal_draw");
    private static final ResourceLocation LUXURIOUS_PIERCE_MODIFIER = rl("luxurious_hope_pierce");
    private static final ResourceLocation LUXURIOUS_LUCK_MODIFIER = rl("luxurious_hope_luck");
    private static final ResourceLocation PHOTOPHOBE_MOVE_MODIFIER = rl("photophobe_move");
    private static final ResourceLocation PHOTOPHOBE_ATTACK_SPEED_MODIFIER = rl("photophobe_attack_speed");
    private static final ResourceLocation ETIQUETTE_DODGE_MODIFIER = rl("etiquette_dodge");
    private static final ResourceLocation ETIQUETTE_ATTACK_SPEED_MODIFIER = rl("etiquette_attack_speed");
    private static final ResourceLocation PERFUNCTORY_MOVE_MODIFIER = rl("perfunctory_move");
    private static final ResourceLocation PERFUNCTORY_ATTACK_SPEED_MODIFIER = rl("perfunctory_attack_speed");
    private static final ResourceLocation PERFUNCTORY_MINING_MODIFIER = rl("perfunctory_mining");
    private static final ResourceLocation PERFUNCTORY_DRAW_MODIFIER = rl("perfunctory_draw");
    private static final ResourceLocation SILENCE_CRIT_CHANCE_MODIFIER = rl("silence_in_depths_crit");
    private static final ResourceLocation FRENZIED_CRIT_DAMAGE_MODIFIER = rl("frenzied_bite_crit_damage");
    /** 91. 自私澄澈天光: 受治疗量经 HEALING_RECEIVED 吃到暴击/暴伤(套装时另加增伤)。 */
    private static final ResourceLocation SELFISH_HEALING_MODIFIER = rl("selfish_clear_sky_healing");
    private static final ResourceLocation HEAVENFALL_CRIT_DAMAGE_MODIFIER = rl("heavenfall_crit_damage");
    private static final ResourceLocation HEAVENFALL_DAMAGE_MODIFIER = rl("heavenfall_damage");
    private static final ResourceLocation GOLD_WINE_CUP_ATTACK_SPEED_MODIFIER = rl("gold_wine_cup_attack_speed");
    private static final ResourceLocation KEEN_WILL_ATTACK_MODIFIER = rl("keen_will_attack");
    private static final ResourceLocation SHARPEN_PIERCE_MODIFIER = rl("sharpen_pierce");
    private static final ResourceLocation HYPERTHYMESIA_HP_MODIFIER = rl("hyperthymesia_hp");
    private static final ResourceLocation SOJOURNER_MOVE_MODIFIER = rl("sojourner_move");

    // ===== 状态快照键(与 1.21 主工程 data 键一致)=====
    private static final String KEY_FLEET_FOOTSTEPS_LAST_LEVEL = "zhonz_fleet_footsteps_last_level";
    private static final String KEY_APEX_LAST = "zhonz_apex_last";
    private static final String KEY_VIOLENT_LAST = "zhonz_violent_last";
    private static final String KEY_SELF_BOUND_LAST = "zhonz_self_bound_last";
    private static final String KEY_RAPID_ASCENT_LAST = "zhonz_rapid_ascent_last";
    private static final String KEY_ACCELERATED_LAST_DODGE = "zhonz_accelerated_last_dodge";
    private static final String KEY_DIVINE_CURSE_LAST_LEVEL = "zhonz_divine_curse_last_level";
    private static final String KEY_PRIMAL_LAST = "zhonz_primal_last";
    private static final String KEY_LUXURIOUS_LAST = "zhonz_luxurious_last";
    private static final String KEY_PHOTOPHOBE_LAST = "zhonz_photophobe_last";
    private static final String KEY_ETIQUETTE_LAST = "zhonz_etiquette_last";
    private static final String KEY_PERFUNCTORY_LAST = "zhonz_perfunctory_last";
    private static final String KEY_GOLD_WINE_LAST = "zhonz_gold_wine_last";
    private static final String KEY_KEEN_STACKS = "zhonz_keen_stacks";
    private static final String KEY_KEEN_LAST = "zhonz_keen_last_tick";

    /** 操作映射: 1.21 ADD_VALUE → 1.20.1 ADDITION。 */
    private static final AttributeModifier.Operation ADD = AttributeModifier.Operation.ADDITION;
    /** 操作映射: 1.21 ADD_MULTIPLIED_BASE → 1.20.1 MULTIPLY_BASE。 */
    private static final AttributeModifier.Operation MULT_BASE = AttributeModifier.Operation.MULTIPLY_BASE;
    /** 操作映射: 1.21 ADD_MULTIPLIED_TOTAL → 1.20.1 MULTIPLY_TOTAL。 */
    private static final AttributeModifier.Operation MULT_TOTAL = AttributeModifier.Operation.MULTIPLY_TOTAL;

    private TickEffectsBatch1() {
    }

    // ===================================================================
    // 通用辅助(1.20.1 属性 modifier 写入方式)
    // ===================================================================

    private static ResourceLocation rl(String path) {
        return new ResourceLocation(CommonConstants1201.MODID, path);
    }

    /**
     * 稳定 UUID 派生, 与 AttributeAccess1201 同一方案 —— 同一 id 的修饰符跨实现不重复叠加,
     * removeModifier 与 addTransientModifier 都按该 UUID 精确匹配。
     */
    private static UUID uuidOf(ResourceLocation id) {
        return UUID.nameUUIDFromBytes(("zhonz:" + id.getPath()).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 替换语义的修饰符写入(1.20.1 版): 先按 UUID 移除旧值, amount == 0 时不加(等价清除)。
     * 对应 1.21 的 setTransient(entity, attr, id, amount, op)。
     */
    private static void setTransient(Player player, Attribute attr, ResourceLocation id, double amount,
                                     AttributeModifier.Operation op) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(uuidOf(id));
        if (amount != 0) {
            inst.addTransientModifier(new AttributeModifier(uuidOf(id), id.toString(), amount, op));
        }
    }

    /** 按 UUID 移除修饰符(1.20.1 无按 ResourceLocation 移除 API)。 */
    private static void removeModifier(Player player, Attribute attr, ResourceLocation id) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst != null) inst.removeModifier(uuidOf(id));
    }

    // ===================================================================
    // 等级查询(经 EnchantmentLookup1201: id → ForgeRegistries, 与 1.21 getXxxEnchantmentLevel 等价)
    // ===================================================================

    private static int anySlot(Player player, String id) {
        return EnchantmentLookup1201.INSTANCE.anySlot(player, id);
    }

    private static int mainHand(Player player, String id) {
        return EnchantmentLookup1201.INSTANCE.mainHand(player, id);
    }

    private static int slot(Player player, String id, EquipmentSlot equipmentSlot) {
        return EnchantmentLookup1201.INSTANCE.slot(player, id, equipmentSlot);
    }

    private static boolean hasEnchant(ItemStack stack, String id) {
        return !stack.isEmpty() && enchantLevel(stack, id) > 0;
    }

    private static int enchantLevel(ItemStack stack, String id) {
        net.minecraft.world.item.enchantment.Enchantment ench =
                ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation(CommonConstants1201.MODID, id));
        if (ench == null) return 0;
        return stack.getEnchantmentLevel(ench);
    }

    // ===================================================================
    // 45. 顶点 apex(1.21 源函数: ModEventHandlers#tickApex)
    //
    // 属性部分: 主手/副手持有 → 闪避 DODGE_CHANCE +0.20(ADD)、ATTACK_DAMAGE ×(1+5)(MULT_TOTAL)。
    // TODO(受伤-60%): 1.21 由 applyApexIncoming(LivingIncomingDamageEvent, ×0.4) 实现 ——
    // 1.20.1 无该事件, 需在 1.20.1 等价受击事件(LivingHurtEvent)适配, 见批内注; 此处仅 tick 属性。
    // ===================================================================
    public static void tickApex(Player player, CompoundTag data) {
        int level = isHoldingApex(player) ? 1 : 0;
        int last = data.getInt(KEY_APEX_LAST);
        if (level == last) return;
        data.putInt(KEY_APEX_LAST, level);
        setTransient(player, ALObjects.Attributes.DODGE_CHANCE.get(), APEX_DODGE_MODIFIER,
                level > 0 ? 0.20 : 0, ADD);
        setTransient(player, Attributes.ATTACK_DAMAGE, APEX_DAMAGE_MODIFIER,
                level > 0 ? 5.0 : 0, MULT_TOTAL);
    }

    private static boolean isHoldingApex(Player player) {
        return mainHand(player, EnchantIds.APEX) > 0
                || slot(player, EnchantIds.APEX, EquipmentSlot.OFFHAND) > 0;
    }

    // ===================================================================
    // 47. 剧烈搏动 violent_pulse(1.21 源函数: ModEventHandlers#tickViolentPulse)
    // 胸甲 + 生命<50%: 攻速 ×(1+0.5)(MULT_BASE)、移速 ×(1+0.3)(MULT_BASE)。
    // 注: "攻击回复1生命" 是攻击链副作用(1.21 applyViolentPulseAttack), 不在本 tick。
    // ===================================================================
    public static void tickViolentPulse(Player player, CompoundTag data) {
        boolean active = slot(player, EnchantIds.VIOLENT_PULSE, EquipmentSlot.CHEST) > 0
                && player.getHealth() <= player.getMaxHealth() * 0.5f;
        int level = active ? 1 : 0;
        int last = data.getInt(KEY_VIOLENT_LAST);
        if (level == last) return;
        data.putInt(KEY_VIOLENT_LAST, level);
        setTransient(player, Attributes.ATTACK_SPEED, VIOLENT_ATTACK_SPEED_MODIFIER,
                level > 0 ? 0.50 : 0, MULT_BASE);
        setTransient(player, Attributes.MOVEMENT_SPEED, VIOLENT_MOVEMENT_MODIFIER,
                level > 0 ? 0.30 : 0, MULT_BASE);
    }

    // ===================================================================
    // 40. 目不能追，耳未可即 fleet_footsteps —— tick 属性部分
    // (1.21 源函数: ModEventHandlers#tickFleetFootsteps)
    // 靴子: 移速 ×(1+0.5)、台阶 +0.5。
    // 1.20.1 无 Attributes.STEP_HEIGHT(1.21 引入) → 等价物为 Forge 提供的
    // ForgeMod.STEP_HEIGHT_ADDITION(对实体台阶高度 ADD, 语义同 1.21 step_height)。
    // 注: 伤害侧("下次攻击+上次伤害10%")走事件/flat 通道, 已由 ForgeEventHandler1201 或后续批处理。
    // ===================================================================
    public static void tickFleetFootsteps(Player player, CompoundTag data) {
        int level = slot(player, EnchantIds.FLEET_FOOTSTEPS, EquipmentSlot.FEET);
        int last = data.getInt(KEY_FLEET_FOOTSTEPS_LAST_LEVEL);
        if (level == last) return;
        data.putInt(KEY_FLEET_FOOTSTEPS_LAST_LEVEL, level);
        setTransient(player, Attributes.MOVEMENT_SPEED, FLEET_FOOTSTEPS_SPEED_MODIFIER,
                level > 0 ? 0.5 : 0, MULT_BASE);
        setTransient(player, ForgeMod.STEP_HEIGHT_ADDITION.get(), FLEET_FOOTSTEPS_STEP_MODIFIER,
                level > 0 ? 0.5 : 0, ADD);
    }

    // ===================================================================
    // 51. 自缚者 self_bound(1.21 源函数: ModEventHandlers#tickSelfBound)
    // 护腿(诅咒): 台阶 -1(ADD)、攻击伤害 ×(1-0.9)(MULT_TOTAL)、移速 ×(1-0.5)(MULT_BASE)。
    // 台阶属性 1.20.1 用 ForgeMod.STEP_HEIGHT_ADDITION(见 tickFleetFootsteps 注)。
    // ===================================================================
    public static void tickSelfBound(Player player, CompoundTag data) {
        int level = slot(player, EnchantIds.SELF_BOUND, EquipmentSlot.LEGS);
        int last = data.getInt(KEY_SELF_BOUND_LAST);
        if (level == last) return;
        data.putInt(KEY_SELF_BOUND_LAST, level);
        setTransient(player, ForgeMod.STEP_HEIGHT_ADDITION.get(), SELF_BOUND_STEP_MODIFIER,
                level > 0 ? -1 : 0, ADD);
        setTransient(player, Attributes.ATTACK_DAMAGE, SELF_BOUND_DAMAGE_MODIFIER,
                level > 0 ? -0.9 : 0, MULT_TOTAL);
        setTransient(player, Attributes.MOVEMENT_SPEED, SELF_BOUND_MOVEMENT_MODIFIER,
                level > 0 ? -0.5 : 0, MULT_BASE);
    }

    // ===================================================================
    // 52. 极速攀升 rapid_ascent —— tick 台阶属性部分
    // (1.21 源函数: ModEventHandlers#tickRapidAscent)
    // 靴子: 台阶 +2(ADD; 1.20.1 = ForgeMod.STEP_HEIGHT_ADDITION)。
    // 加伤 bonus(y/100 百分比)已由 common TickBonusRules.rapidAscent
    // 在 ForgeEventHandler1201.onPlayerTick 处理, 勿在此重复。受伤减半(y<0)是受击事件逻辑。
    // ===================================================================
    public static void tickRapidAscent(Player player, CompoundTag data) {
        int level = slot(player, EnchantIds.RAPID_ASCENT, EquipmentSlot.FEET);
        int last = data.getInt(KEY_RAPID_ASCENT_LAST);
        if (level == last) return;
        data.putInt(KEY_RAPID_ASCENT_LAST, level);
        setTransient(player, ForgeMod.STEP_HEIGHT_ADDITION.get(), RAPID_ASCENT_STEP_MODIFIER,
                level > 0 ? 2 : 0, ADD);
    }

    // ===================================================================
    // 53. 加速的未来 accelerated_future(1.21 源函数: ModEventHandlers#tickAcceleratedFuture)
    // 头盔: 攻击伤害 ×(1+闪避率×2)(MULT_TOTAL)、攻速 ×(1+闪避率)(MULT_BASE),
    // 每次闪避率变化时重写(阈值 0.001)。
    // ===================================================================
    public static void tickAcceleratedFuture(Player player, CompoundTag data) {
        int level = slot(player, EnchantIds.ACCELERATED_FUTURE, EquipmentSlot.HEAD);
        double dodge = player.getAttributeValue(ALObjects.Attributes.DODGE_CHANCE.get());
        if (level <= 0) {
            if (data.getFloat(KEY_ACCELERATED_LAST_DODGE) > 0) {
                setTransient(player, Attributes.ATTACK_DAMAGE, ACCELERATED_DAMAGE_MODIFIER, 0, MULT_TOTAL);
                setTransient(player, Attributes.ATTACK_SPEED, ACCELERATED_ATTACK_SPEED_MODIFIER, 0, MULT_BASE);
                data.putFloat(KEY_ACCELERATED_LAST_DODGE, 0);
            }
            return;
        }
        float lastDodge = data.getFloat(KEY_ACCELERATED_LAST_DODGE);
        if (Math.abs(lastDodge - dodge) < 0.001f) return;
        data.putFloat(KEY_ACCELERATED_LAST_DODGE, (float) dodge);
        setTransient(player, Attributes.ATTACK_DAMAGE, ACCELERATED_DAMAGE_MODIFIER,
                dodge * 2.0, MULT_TOTAL);
        setTransient(player, Attributes.ATTACK_SPEED, ACCELERATED_ATTACK_SPEED_MODIFIER,
                dodge, MULT_BASE);
    }

    // ===================================================================
    // 27. 神圣诅咒 divine_curse —— tick 属性部分
    // (1.21 源函数: ModEventHandlers#tickDivineCurse)
    // 任何槽位持有诅咒: 触摸/攻击距离、攻击伤害、攻速、挖掘/蓄力速度 ×(1-0.5);
    // 每秒(20 tick)对带诅咒的可损坏装备扣 1% 耐久。
    // 1.20.1 无 1.21 Attributes.ENTITY_INTERACTION_RANGE/BLOCK_INTERACTION_RANGE → 用 Forge 等价
    // ForgeMod.ENTITY_REACH/BLOCK_REACH(默认 3.0/4.5, MULT_TOTAL -0.5 语义同 1.21 触摸距离 -50%)。
    // TODO(冷却翻倍): 1.21 另写 COOLDOWN_REDUCTION ×(1-1.0); 1.20.1 Apothic
    // (attributeslib 1.3.7) ALObjects.Attributes 无 COOLDOWN_REDUCTION 字段, 无法直引 → 待联调确认
    // attributeslib 是否提供等价属性后补(涉及运行时属性 id 与实体挂接, 见 UNIMINED_MIGRATION 6.7)。
    // ===================================================================
    public static void tickDivineCurse(Player player, CompoundTag data, int tickCount) {
        int level = anySlot(player, EnchantIds.DIVINE_CURSE);
        int last = data.getInt(KEY_DIVINE_CURSE_LAST_LEVEL);
        if (level != last) {
            data.putInt(KEY_DIVINE_CURSE_LAST_LEVEL, level);
            double mult = level > 0 ? -0.5 : 0;
            setTransient(player, ForgeMod.ENTITY_REACH.get(), DIVINE_CURSE_RANGE_MODIFIER,
                    mult, MULT_TOTAL);
            setTransient(player, ForgeMod.BLOCK_REACH.get(), DIVINE_CURSE_BLOCK_RANGE_MODIFIER,
                    mult, MULT_TOTAL);
            setTransient(player, Attributes.ATTACK_DAMAGE, DIVINE_CURSE_DAMAGE_MODIFIER,
                    mult, MULT_TOTAL);
            setTransient(player, Attributes.ATTACK_SPEED, DIVINE_CURSE_ATTACK_SPEED_MODIFIER,
                    mult, MULT_TOTAL);
            setTransient(player, ALObjects.Attributes.MINING_SPEED.get(), DIVINE_CURSE_MINING_MODIFIER,
                    mult, MULT_TOTAL);
            setTransient(player, ALObjects.Attributes.DRAW_SPEED.get(), DIVINE_CURSE_DRAW_MODIFIER,
                    mult, MULT_TOTAL);
            // TODO(1.21: COOLDOWN_REDUCTION ×2): 1.20.1 attributeslib 无该字段, 见方法头注。
        }
        if (level <= 0) return;
        // 每秒 1% 耐久损耗(带诅咒的装备)
        if (tickCount % 20 == 0) {
            for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
                ItemStack stack = player.getItemBySlot(equipmentSlot);
                if (hasEnchant(stack, EnchantIds.DIVINE_CURSE) && stack.isDamageableItem()) {
                    int durabilityToRemove = Math.max(1, (stack.getMaxDamage() - stack.getDamageValue()) / 100);
                    stack.hurtAndBreak(durabilityToRemove, player, item -> {
                    });
                }
            }
        }
    }

    // ===================================================================
    // 58. 苦难原色 primal_suffering(1.21 源函数: ModEventHandlers#tickPrimalSuffering)
    // 任何槽位: 对攻速/移速/挖掘/蓄力属性, 若当前值低于基础值则补回差额×2(MULT_BASE)。
    // ===================================================================
    public static void tickPrimalSuffering(Player player, CompoundTag data) {
        int level = anySlot(player, EnchantIds.PRIMAL_SUFFERING);
        int last = data.getInt(KEY_PRIMAL_LAST);
        if (level == last) return;
        data.putInt(KEY_PRIMAL_LAST, level);
        if (level <= 0) {
            removeModifier(player, Attributes.ATTACK_SPEED, PRIMAL_ATTACK_SPEED_MODIFIER);
            removeModifier(player, Attributes.MOVEMENT_SPEED, PRIMAL_MOVE_MODIFIER);
            removeModifier(player, ALObjects.Attributes.MINING_SPEED.get(), PRIMAL_MINING_MODIFIER);
            removeModifier(player, ALObjects.Attributes.DRAW_SPEED.get(), PRIMAL_DRAW_MODIFIER);
            return;
        }
        applyPrimal(player, Attributes.ATTACK_SPEED, PRIMAL_ATTACK_SPEED_MODIFIER);
        applyPrimal(player, Attributes.MOVEMENT_SPEED, PRIMAL_MOVE_MODIFIER);
        applyPrimal(player, ALObjects.Attributes.MINING_SPEED.get(), PRIMAL_MINING_MODIFIER);
        applyPrimal(player, ALObjects.Attributes.DRAW_SPEED.get(), PRIMAL_DRAW_MODIFIER);
    }

    /** 1.21 applyPrimal 的 1.20.1 版: 基础值 - 当前值 > 0.01 时写入差额×2, 否则清除。 */
    private static void applyPrimal(Player player, Attribute attr, ResourceLocation id) {
        AttributeInstance instance = player.getAttribute(attr);
        if (instance == null) return;
        double base = instance.getBaseValue();
        double current = instance.getValue();
        double deficit = base - current;
        if (deficit > 0.01) {
            setTransient(player, attr, id, deficit * 2.0, MULT_BASE);
        } else {
            removeModifier(player, attr, id);
        }
    }

    // ===================================================================
    // 60. 奢侈的希望 luxurious_hope —— tick 属性部分
    // (1.21 源函数: ModEventHandlers#tickLuxuriousHope)
    // 任何槽位: 护甲穿透 ARMOR_PIERCE +0.5(ADD)、幸运 LUCK +0.2(ADD)。
    // TODO(满血受伤+50%): 1.21 applyLuxuriousHopeIncoming(LivingIncomingDamageEvent ×1.5),
    // 1.20.1 需在等价受击事件适配。
    // ===================================================================
    public static void tickLuxuriousHope(Player player, CompoundTag data) {
        boolean active = anySlot(player, EnchantIds.LUXURIOUS_HOPE) > 0;
        int last = data.getInt(KEY_LUXURIOUS_LAST);
        int now = active ? 1 : 0;
        if (now == last) return;
        data.putInt(KEY_LUXURIOUS_LAST, now);
        if (now == 0) {
            removeModifier(player, ALObjects.Attributes.ARMOR_PIERCE.get(), LUXURIOUS_PIERCE_MODIFIER);
            removeModifier(player, Attributes.LUCK, LUXURIOUS_LUCK_MODIFIER);
            return;
        }
        setTransient(player, ALObjects.Attributes.ARMOR_PIERCE.get(), LUXURIOUS_PIERCE_MODIFIER,
                0.5, ADD);
        setTransient(player, Attributes.LUCK, LUXURIOUS_LUCK_MODIFIER,
                0.2, ADD);
    }

    // ===================================================================
    // 61. 嗜光 photophile(1.21 源函数: ModEventHandlers#tickPhotophile)
    // 头盔: 光照 > 0 时每 40 tick 施加饱食度 40。
    // ===================================================================
    public static void tickPhotophile(Player player, int tickCount) {
        if (slot(player, EnchantIds.PHOTOPHILE, EquipmentSlot.HEAD) <= 0) return;
        if (player.level().getMaxLocalRawBrightness(player.blockPosition()) > 0 && tickCount % 40 == 0) {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.SATURATION, 40, 0, false, false));
        }
    }

    // ===================================================================
    // 62. 畏光 photophobe(1.21 源函数: ModEventHandlers#tickPhotophobe)
    // 头盔: 光照>0 → 移速 ×(1-0.2); 光照=0 → 移速 ×(1+0.5)、攻速 ×(1+0.3); 无附魔清除。
    // ===================================================================
    public static void tickPhotophobe(Player player, CompoundTag data) {
        int level = slot(player, EnchantIds.PHOTOPHOBE, EquipmentSlot.HEAD);
        int light = player.level().getMaxLocalRawBrightness(player.blockPosition());
        int mode = level > 0 ? (light > 0 ? 1 : 2) : 0;
        int last = data.getInt(KEY_PHOTOPHOBE_LAST);
        if (mode == last) return;
        data.putInt(KEY_PHOTOPHOBE_LAST, mode);
        if (mode == 1) {
            setTransient(player, Attributes.MOVEMENT_SPEED, PHOTOPHOBE_MOVE_MODIFIER, -0.2, MULT_BASE);
            removeModifier(player, Attributes.ATTACK_SPEED, PHOTOPHOBE_ATTACK_SPEED_MODIFIER);
        } else if (mode == 2) {
            setTransient(player, Attributes.MOVEMENT_SPEED, PHOTOPHOBE_MOVE_MODIFIER, 0.5, MULT_BASE);
            setTransient(player, Attributes.ATTACK_SPEED, PHOTOPHOBE_ATTACK_SPEED_MODIFIER, 0.3, MULT_BASE);
        } else {
            removeModifier(player, Attributes.MOVEMENT_SPEED, PHOTOPHOBE_MOVE_MODIFIER);
            removeModifier(player, Attributes.ATTACK_SPEED, PHOTOPHOBE_ATTACK_SPEED_MODIFIER);
        }
    }

    // ===================================================================
    // 63. 礼仪 etiquette(1.21 源函数: ModEventHandlers#tickEtiquette)
    // 每件盔甲带附魔: 闪避 DODGE_CHANCE +0.05(ADD)、攻速 ×(1+0.2)(MULT_BASE), 按件数累加。
    // ===================================================================
    public static void tickEtiquette(Player player, CompoundTag data) {
        int count = 0;
        for (EquipmentSlot equipmentSlot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (slot(player, EnchantIds.ETIQUETTE, equipmentSlot) > 0) count++;
        }
        int last = data.getInt(KEY_ETIQUETTE_LAST);
        if (count == last) return;
        data.putInt(KEY_ETIQUETTE_LAST, count);
        setTransient(player, ALObjects.Attributes.DODGE_CHANCE.get(), ETIQUETTE_DODGE_MODIFIER,
                count * 0.05, ADD);
        setTransient(player, Attributes.ATTACK_SPEED, ETIQUETTE_ATTACK_SPEED_MODIFIER,
                count * 0.2, MULT_BASE);
    }

    // ===================================================================
    // 64. 敷衍 perfunctory(1.21 源函数: ModEventHandlers#tickPerfunctory)
    // 诅咒: 背包每有一件带此诅咒的物品, 移速/攻速/挖掘/蓄力 ×(1-0.2×件数)。
    // ===================================================================
    public static void tickPerfunctory(Player player, CompoundTag data) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (hasEnchant(stack, EnchantIds.PERFUNCTORY)) count++;
        }
        int last = data.getInt(KEY_PERFUNCTORY_LAST);
        if (count == last) return;
        data.putInt(KEY_PERFUNCTORY_LAST, count);
        double mult = -0.2 * count;
        setTransient(player, Attributes.MOVEMENT_SPEED, PERFUNCTORY_MOVE_MODIFIER, mult, MULT_BASE);
        setTransient(player, Attributes.ATTACK_SPEED, PERFUNCTORY_ATTACK_SPEED_MODIFIER, mult, MULT_BASE);
        setTransient(player, ALObjects.Attributes.MINING_SPEED.get(), PERFUNCTORY_MINING_MODIFIER, mult, MULT_BASE);
        setTransient(player, ALObjects.Attributes.DRAW_SPEED.get(), PERFUNCTORY_DRAW_MODIFIER, mult, MULT_BASE);
    }

    // ===================================================================
    // 65-67. 暴击三件套 crit_weapons(1.21 源函数: ModEventHandlers#tickCritWeapons)
    // 沉默沉入沉渊(主手): 暴击率 CRIT_CHANCE = 最大生命×2%(组合×4%)(ADD)。
    // 狂热撕咬光芒(主手): 暴击伤害 CRIT_DAMAGE = 攻速(组合×2)(MULT_BASE)。
    // 噤声击坠天堂(主手): 暴击伤害 = 暴击率(组合×2)(MULT_BASE); 增伤 ATTACK_DAMAGE ×(1+暴击率×组合倍率)(MULT_TOTAL)。
    // ===================================================================
    public static void tickCritWeapons(Player player, CompoundTag data) {
        ItemStack weapon = player.getMainHandItem();
        boolean silence = hasEnchant(weapon, EnchantIds.SILENCE_IN_DEPTHS);
        boolean frenzied = hasEnchant(weapon, EnchantIds.FRENZIED_BITE);
        boolean heavenfall = hasEnchant(weapon, EnchantIds.SILENCED_HEAVENFALL);

        // 套装联动: 各附魔各自判定"除自己以外的其他套装附魔是否在身"(六槽位, 见 EnchantSetPieces)
        boolean silenceCombo = silence && hasSetOtherPiece(player, EnchantIds.SILENCE_IN_DEPTHS);
        boolean frenziedCombo = frenzied && hasSetOtherPiece(player, EnchantIds.FRENZIED_BITE);
        boolean heavenfallCombo = heavenfall && hasSetOtherPiece(player, EnchantIds.SILENCED_HEAVENFALL);

        // 沉默沉入沉渊: 暴击率 = 最大生命×2% (套装联动时×4%)
        double maxHp = player.getAttributeValue(Attributes.MAX_HEALTH);
        double critChance = silence ? maxHp * 0.02 * (silenceCombo ? 2 : 1) : 0;
        setTransient(player, ALObjects.Attributes.CRIT_CHANCE.get(), SILENCE_CRIT_CHANCE_MODIFIER,
                critChance, ADD);

        // 狂热撕咬光芒: 暴击伤害 = 攻速 (联动时×2)
        double attackSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        double critDamage1 = frenzied ? attackSpeed * (frenziedCombo ? 2 : 1) : 0;
        setTransient(player, ALObjects.Attributes.CRIT_DAMAGE.get(), FRENZIED_CRIT_DAMAGE_MODIFIER,
                critDamage1, MULT_BASE);

        // 噤声击坠天堂: 暴击伤害+增伤 = 暴击率 (联动时×2) —— 增伤走事件, 暴击伤害走属性
        double critChanceValue = player.getAttributeValue(ALObjects.Attributes.CRIT_CHANCE.get());
        double critDamage2 = heavenfall ? critChanceValue * (heavenfallCombo ? 2 : 1) : 0;
        setTransient(player, ALObjects.Attributes.CRIT_DAMAGE.get(), HEAVENFALL_CRIT_DAMAGE_MODIFIER,
                critDamage2, MULT_BASE);
        setTransient(player, Attributes.ATTACK_DAMAGE, HEAVENFALL_DAMAGE_MODIFIER,
                heavenfall ? critChanceValue * (heavenfallCombo ? 2 : 1) : 0, MULT_TOTAL);
    }

    /** 套装联动判定: 六槽位(护甲四件+正副手)中是否存在除 {@code selfId} 以外的套装附魔。 */
    private static boolean hasSetOtherPiece(Player player, String selfId) {
        return EnchantSetPieces.hasOtherPiece(player, selfId,
                (entity, id, slot) -> EnchantmentLookup1201.INSTANCE.slot(entity, id, slot));
    }

    // ===================================================================
    // 91. 自私澄澈天光 selfish_clear_sky(1.21 源函数: ModEventHandlers#tickSelfishClearSky)
    // 胸甲: 受治疗量 ×(1 + 暴击率×暴击伤害); 六槽位有"其他"套装附魔时另乘增伤。
    // ===================================================================
    public static void tickSelfishClearSky(Player player) {
        boolean hasSelfish = slot(player, EnchantIds.SELFISH_CLEAR_SKY, EquipmentSlot.CHEST) > 0;
        Attribute healingReceived = ALObjects.Attributes.HEALING_RECEIVED.get();
        if (!hasSelfish) {
            removeModifier(player, healingReceived, SELFISH_HEALING_MODIFIER);
            return;
        }
        double critChance = player.getAttributeValue(ALObjects.Attributes.CRIT_CHANCE.get());
        double critDamage = player.getAttributeValue(ALObjects.Attributes.CRIT_DAMAGE.get());
        double mult = critChance * critDamage;
        if (hasSetOtherPiece(player, EnchantIds.SELFISH_CLEAR_SKY)) {
            double bonus = player.getAttributeValue(ZhonzAttributes1201.BONUS_DAMAGE.get());
            double dmgMult = player.getAttributeValue(ZhonzAttributes1201.DAMAGE_MULTIPLIER.get());
            mult *= dmgMult * (1.0 + bonus);
        }
        setTransient(player, healingReceived, SELFISH_HEALING_MODIFIER, mult, MULT_BASE);
    }

    // ===================================================================
    // 72. 金酒之杯 gold_wine_cup(1.21 源函数: ModEventHandlers#tickGoldWineCup)
    // 任何槽位: 背包(含潜影盒)每绿宝石 攻速 ×(1+0.02)。
    // 1.20.1 潜影盒内容走物品 BlockEntityTag/Items NBT(无 1.21 DataComponents CONTAINER)。
    // ===================================================================
    public static void tickGoldWineCup(Player player, CompoundTag data) {
        if (anySlot(player, EnchantIds.GOLD_WINE_CUP) <= 0) {
            if (data.getInt(KEY_GOLD_WINE_LAST) != 0) {
                removeModifier(player, Attributes.ATTACK_SPEED, GOLD_WINE_CUP_ATTACK_SPEED_MODIFIER);
                data.putInt(KEY_GOLD_WINE_LAST, 0);
            }
            return;
        }
        int emeralds = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() == Items.EMERALD) {
                emeralds += stack.getCount();
            }
            // 潜影盒内绿宝石(1.20.1: BlockEntityTag → Items 列表)
            CompoundTag bet = stack.getTagElement("BlockEntityTag");
            if (bet != null) {
                ListTag items = bet.getList("Items", net.minecraft.nbt.Tag.TAG_COMPOUND);
                for (int j = 0; j < items.size(); j++) {
                    CompoundTag inner = items.getCompound(j);
                    String id = inner.getString("id");
                    if (id.isEmpty()) continue;
                    if (ForgeRegistries.ITEMS.getValue(new ResourceLocation(id)) == Items.EMERALD) {
                        emeralds += inner.getByte("Count");
                    }
                }
            }
        }
        int last = data.getInt(KEY_GOLD_WINE_LAST);
        if (emeralds == last) return;
        data.putInt(KEY_GOLD_WINE_LAST, emeralds);
        setTransient(player, Attributes.ATTACK_SPEED, GOLD_WINE_CUP_ATTACK_SPEED_MODIFIER,
                emeralds * 0.02, MULT_BASE);
    }

    // ===================================================================
    // 77. 锋锐意志 keen_will(1.21 源函数: ModEventHandlers#tickKeenWill)
    // 主手武器: 每次攻击叠一层(上限20), 200 tick 无新攻击清空; 每层给
    // ATTACK_DAMAGE +1(ADD, 直接加在原版攻击伤害属性上)。叠层副作用在攻击链
    // (gainKeenStack 等价逻辑, 本批不含), 本函数负责衰减+属性同步。
    // ===================================================================
    public static void tickKeenWill(Player player, CompoundTag data, int tickCount) {
        if (mainHand(player, EnchantIds.KEEN_WILL) <= 0) {
            if (data.getInt(KEY_KEEN_STACKS) != 0) {
                data.remove(KEY_KEEN_STACKS);
            }
            setTransient(player, Attributes.ATTACK_DAMAGE, KEEN_WILL_ATTACK_MODIFIER, 0, ADD);
            return;
        }
        // 10 秒(200 tick)无新攻击则清空
        int lastTick = data.getInt(KEY_KEEN_LAST);
        int stacks = data.getInt(KEY_KEEN_STACKS);
        if (stacks > 0 && tickCount - lastTick > 200) {
            data.remove(KEY_KEEN_STACKS);
            stacks = 0;
        }
        // 武器基础攻击力 +每层1
        setTransient(player, Attributes.ATTACK_DAMAGE, KEEN_WILL_ATTACK_MODIFIER, stacks, ADD);
    }

    // ===================================================================
    // 78. 锋化 sharpen(1.21 源函数: ModEventHandlers#tickSharpen)
    // 主手武器: 护甲撕裂 ARMOR_PIERCE = 武器耐久剩余比例×0.4(ADD)。
    // ===================================================================
    public static void tickSharpen(Player player) {
        ItemStack weapon = player.getMainHandItem();
        int level = mainHand(player, EnchantIds.SHARPEN);
        double pierce = 0.0;
        if (level > 0 && weapon.isDamageableItem() && weapon.getMaxDamage() > 0) {
            double fraction = (double) (weapon.getMaxDamage() - weapon.getDamageValue()) / weapon.getMaxDamage();
            pierce = fraction * 0.4;
        }
        setTransient(player, ALObjects.Attributes.ARMOR_PIERCE.get(), SHARPEN_PIERCE_MODIFIER,
                pierce, ADD);
    }

    // ===================================================================
    // 79. 超忆症 hyperthymesia(1.21 源函数: ModEventHandlers#tickHyperthymesia)
    // 任何槽位: 最大生命 MAX_HEALTH + "身上盔甲保护/耐久类附魔等级之和"×1(ADD)。
    // 1.20.1 读盔甲附魔用 ItemStack.getEnchantmentTags()(NBT ListTag), 非 1.21 DataComponents。
    // ===================================================================
    public static void tickHyperthymesia(Player player) {
        if (anySlot(player, EnchantIds.HYPERTHYMESIA) <= 0) {
            setTransient(player, Attributes.MAX_HEALTH, HYPERTHYMESIA_HP_MODIFIER, 0, ADD);
            return;
        }
        int sum = 0;
        for (EquipmentSlot equipmentSlot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack armor = player.getItemBySlot(equipmentSlot);
            if (armor.isEmpty()) continue;
            ListTag ench = armor.getEnchantmentTags();
            for (int i = 0; i < ench.size(); i++) {
                CompoundTag entry = ench.getCompound(i);
                String id = entry.getString("id");
                if (id.isEmpty()) continue;
                String path = new ResourceLocation(id).getPath();
                // 保护 / 耐久类附魔(含原版 protection 各系与 unbreaking)
                if (path.startsWith("protection") || path.equals("unbreaking")
                        || path.equals("fire_protection") || path.equals("blast_protection")
                        || path.equals("projectile_protection") || path.equals("feather_falling")) {
                    sum += entry.getShort("lvl");
                }
            }
        }
        setTransient(player, Attributes.MAX_HEALTH, HYPERTHYMESIA_HP_MODIFIER, sum, ADD);
    }

    // ===================================================================
    // 85/86. 他乡客 / 远行客 sojourner(1.21 源函数: ModEventHandlers#tickSojourner)
    // 靴子: 他乡客 → 移速 ×(1+攻速×0.5)(远行客同穿时 ×1); 远行客 → 饱食, 双持再回血。
    // ===================================================================
    public static void tickSojourner(Player player) {
        boolean soj = slot(player, EnchantIds.SOJOURNER, EquipmentSlot.FEET) > 0;
        boolean way = slot(player, EnchantIds.WAYFARER, EquipmentSlot.FEET) > 0;
        if (soj) {
            double atkSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
            double mult = way ? 1.0 : 0.5;
            setTransient(player, Attributes.MOVEMENT_SPEED, SOJOURNER_MOVE_MODIFIER,
                    atkSpeed * mult, MULT_TOTAL);
        } else {
            setTransient(player, Attributes.MOVEMENT_SPEED, SOJOURNER_MOVE_MODIFIER, 0, MULT_TOTAL);
        }
        if (way) {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.SATURATION, 40, 0, false, false));
            if (soj) {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.REGENERATION, 40, 1, false, false));
            }
        }
    }
}
