package com.zhonz.moreenchantments.forge;

import com.zhonz.moreenchantments.common.enchant.EnchantIds;
import com.zhonz.moreenchantments.common.storage.EntityDataStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeMod;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * 1.20.1 Forge 平台: 附魔 PlayerTick「tick 侧」缺失项补齐批(对应 1.21 主工程
 * {@code ModEventHandlers} 中尚未移植到 1.20.1 的 tick 函数)。
 *
 * <p>覆盖项(1.21 源函数 → 本类方法):
 * <ol>
 *   <li>{@code tickCooldownsAndCleanup(L2448)} → {@link #tickCooldownsAndCleanup}
 *       —— <b>必须</b>: 永劫回归 KEY_RETURN_FROM_HELL_CD(6000 tick)只在此递减,
 *       缺失则冷却永不归零 → 该附魔只能触发一次。</li>
 *   <li>{@code tickCorneredBeast(L1287)} 的治疗 +50% 部分 → {@link #tickCorneredBeast}
 *       (加伤 +60% 已由 ForgeEventHandler1201.onPlayerTick 经 TickBonusRules 处理, 本类不重复)。</li>
 *   <li>{@code tickSupremeArt(L2133)} 的属性部分 → {@link #tickSupremeArt}
 *       (加伤 +20%/级 同上已处理, 本类只写 3 个属性修饰符)。</li>
 *   <li>{@code tickHalo(L1753)} → {@link #tickHalo} —— 写 "zhonz_halo_sleep_ticks"
 *       (供 {@code HaloSleepMixin} 读取), 否则佩戴者永远无法跳过夜晚。</li>
 *   <li>{@code tickPatience(L1333)} → {@link #tickPatience} —— 写 "zhonz_patience_hold_ticks" /
 *       "zhonz_patience_ready"(供 {@code ItemStackDurabilityMixin} 读取)。</li>
 *   <li>{@code tickFoolsMask(L2202)} → {@link #tickFoolsMask} —— 随机翻转
 *       "zhonz_fools_mask_lucky"(供 ForgeEventHandler1201 的 CTX 读取)。</li>
 *   <li>{@code tickPaleMidnight(L1465)} → {@link #tickPaleMidnight} —— 夜视 / 低光隐身。</li>
 *   <li>{@code tickEmergencyRescue(L2407)} → {@link #tickEmergencyRescue} —— 低血自愈 + 同类救援
 *       (其冷却递减在 {@link #tickCooldownsAndCleanup})。</li>
 * </ol>
 *
 * <p><b>接线</b>: 由 {@code EnchantWiring1201.onPlayerTick}(PlayerTickEvent.END)统一调用,
 * 参数与 1.21 一致: {@code (Player, CompoundTag data = player.getPersistentData(), int tickCount = player.tickCount)}。
 *
 * <p><b>守卫约定</b>: 每个 tick 函数开头检查对应附魔等级, {@code <= 0} 时不做任何施加;
 * 若上一 tick 曾施加过修饰符, 则先清除再返回(避免卸下附魔后残留加成)。
 * 唯一例外是 {@link #tickCooldownsAndCleanup} —— 它是纯簿记(递减冷却 / 清理过期标记),
 * 1.21 亦无条件执行; 若加附魔守卫, 玩家卸下附魔后冷却会永久冻结, 反而破坏语义。
 *
 * <p><b>1.20.1 API 差异</b>(对照 PORTING_CONTRACT 映射表):
 * <ul>
 *   <li>1.21 {@code Attributes.ENTITY_INTERACTION_RANGE} → 1.20.1 {@code ForgeMod.ENTITY_REACH};
 *       {@code BLOCK_INTERACTION_RANGE} → {@code ForgeMod.BLOCK_REACH}(两者均为
 *       {@code RegistryObject<Attribute>}, 需 {@code .get()})。</li>
 *   <li>1.21 属性写入 {@code setTransient(entity, Holder<Attribute>, ResourceLocation id, ...)} →
 *       1.20.1 按 UUID 移除/添加(与 TickEffectsBatch1 / AttributeAccess1201 同一 UUID 派生方案)。</li>
 *   <li>1.21 {@code ALObjects.Attributes.HEALING_RECEIVED}(apothic_attributes) → 1.20.1
 *       {@code dev.shadowsoffire.attributeslib.api.ALObjects.Attributes.HEALING_RECEIVED}
 *       ({@code RegistryObject<Attribute>}, Apothic 未加载时 {@code get()} 为 null → no-op 保护)。</li>
 *   <li>附魔等级: {@code getSlotEnchantmentLevel/getMainHandEnchantmentLevel/getEnchantmentLevel}
 *       → {@code EnchantmentLookup1201.INSTANCE.slot/mainHand/anySlot}。</li>
 * </ul>
 */
public final class TickSideBatch1 {

    private static final Random RANDOM = new Random();

    // ===== Persistent Data Keys(值与 1.21 ModEventHandlers 常量一致, 跨版本对齐便于对账)=====
    private static final String KEY_CORNERED_LAST = "zhonz_cornered_last";
    private static final String KEY_SUPREME_ART_LAST_LEVEL = "zhonz_supreme_art_last_level";
    private static final String KEY_HALO_SLEEP_TICKS = "zhonz_halo_sleep_ticks";
    private static final String KEY_PATIENCE_HOLD_TICKS = "zhonz_patience_hold_ticks";
    private static final String KEY_PATIENCE_READY = "zhonz_patience_ready";
    private static final String KEY_FOOLS_MASK_LUCKY = "zhonz_fools_mask_lucky";
    private static final String KEY_FOOLS_MASK_CHANGE_TICK = "zhonz_fools_mask_change_tick";
    private static final String KEY_EMERGENCY_RESCUE_CD = "zhonz_emergency_rescue_cd";
    private static final String KEY_RETURN_FROM_HELL_CD = "zhonz_return_from_hell_cd";
    private static final String KEY_MUST_OPEN_PATH_CD = "zhonz_must_open_path_cd";
    private static final String KEY_GRIEVOUS_WOUND_UNTIL = "zhonz_grievous_wound_until";
    private static final String KEY_BURNING_DUSK_PCT = "zhonz_burning_dusk_pct";
    private static final String KEY_BURNING_DUSK_UNTIL = "zhonz_burning_dusk_until";
    private static final String KEY_PALE_VULN_UNTIL = "zhonz_pale_midnight_vuln_until";
    private static final String KEY_FLIPPING_COIN_ATTACK_STACKS = "zhonz_flipping_coin_attack_stacks";
    /** 爆裂黎明"装填中"标志(与 1.21 ModEventHandlers L96 同值; 写入侧在 SideEffectsBatch1)。 */
    private static final String KEY_EXPLOSIVE_DAWN_RELOADING = "zhonz_explosive_dawn_reloading";
    /** 剥壳逐攻击者百分比键前缀(1.21 同: "zhonz_shell_strip_percent_")。 */
    private static final String SHELL_STRIP_PREFIX = "zhonz_shell_strip_percent_";

    // ===== Attribute Modifier ids(与 1.21 ModEventHandlers 的 rl(...) 路径一致)=====
    private static final ResourceLocation CORNERED_HEALING_MODIFIER = rl("cornered_beast_healing");
    private static final ResourceLocation SUPREME_ART_RANGE_MODIFIER = rl("supreme_art_range");
    private static final ResourceLocation SUPREME_ART_ATTACK_SPEED_MODIFIER = rl("supreme_art_attack_speed");

    /** 硬币最大生命修饰符 UUID(1.20.1 按 UUID 移除; 与 SideEffectsBatch1 的派生一致)。 */
    private static final UUID FLIPPING_COIN_MAX_HP_UUID =
            UUID.nameUUIDFromBytes("zhonz:flipping_coin_max_hp".getBytes(StandardCharsets.UTF_8));

    /** 操作映射: 1.21 ADD_VALUE → 1.20.1 ADDITION。 */
    private static final AttributeModifier.Operation ADD = AttributeModifier.Operation.ADDITION;
    /** 操作映射: 1.21 ADD_MULTIPLIED_BASE → 1.20.1 MULTIPLY_BASE。 */
    private static final AttributeModifier.Operation MULT_BASE = AttributeModifier.Operation.MULTIPLY_BASE;
    /** 操作映射: 1.21 ADD_MULTIPLIED_TOTAL → 1.20.1 MULTIPLY_TOTAL。 */
    private static final AttributeModifier.Operation MULT_TOTAL = AttributeModifier.Operation.MULTIPLY_TOTAL;

    private TickSideBatch1() {
    }

    // ===================================================================
    // 通用辅助(与 TickEffectsBatch1 保持同一套写入/查询方式)
    // ===================================================================

    private static ResourceLocation rl(String path) {
        return new ResourceLocation(CommonConstants1201.MODID, path);
    }

    /** 稳定 UUID 派生: 与 AttributeAccess1201 / TickEffectsBatch1 同一方案。 */
    private static UUID uuidOf(ResourceLocation id) {
        return UUID.nameUUIDFromBytes(("zhonz:" + id.getPath()).getBytes(StandardCharsets.UTF_8));
    }

    /** 1.20.1 版 setTransient: 先按 UUID 移除旧值, amount == 0 时不添加(等价清除)。 */
    private static void setTransient(Player player, Attribute attr, ResourceLocation id, double amount,
                                     AttributeModifier.Operation op) {
        if (attr == null) return; // Apothic 未加载等场景
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(uuidOf(id));
        if (amount != 0) {
            inst.addTransientModifier(new AttributeModifier(uuidOf(id), id.toString(), amount, op));
        }
    }

    private static void removeModifier(Player player, Attribute attr, ResourceLocation id) {
        if (attr == null) return;
        AttributeInstance inst = player.getAttribute(attr);
        if (inst != null) inst.removeModifier(uuidOf(id));
    }

    private static int anySlot(Player player, String id) {
        return EnchantmentLookup1201.INSTANCE.anySlot(player, id);
    }

    private static int mainHand(Player player, String id) {
        return EnchantmentLookup1201.INSTANCE.mainHand(player, id);
    }

    private static int slot(Player player, String id, EquipmentSlot equipmentSlot) {
        return EnchantmentLookup1201.INSTANCE.slot(player, id, equipmentSlot);
    }

    /** 1.21 tickCooldown(L309): 冷却 > 0 时递减 1。 */
    private static void tickCooldown(CompoundTag data, String key) {
        int cd = data.getInt(key);
        if (cd > 0) {
            data.putInt(key, cd - 1);
        }
    }

    /** 1.21 getNearbySameType(L261): 半径内同生物类型的其它存活实体。 */
    private static List<LivingEntity> getNearbySameType(LivingEntity center, double radius) {
        List<LivingEntity> result = new ArrayList<>();
        for (LivingEntity living : center.level().getEntitiesOfClass(LivingEntity.class,
                center.getBoundingBox().inflate(radius))) {
            if (living != center && living.isAlive() && living.getType() == center.getType()) {
                result.add(living);
            }
        }
        return result;
    }

    // ===================================================================
    // 46. 困兽之斗 cornered_beast —— 治疗 +50% 部分
    // (1.21 源函数: ModEventHandlers#tickCorneredBeast L1287-1299)
    // 头盔 + 生命 ≤25% → Apothic HEALING_RECEIVED ×(1+0.50)(MULTIPLY_TOTAL), 状态快照避免重复写。
    // 注: 伤害 +60% 已由 ForgeEventHandler1201.onPlayerTick 经 TickBonusRules.corneredBeast
    //     写入 bonus 通道, 本方法不重复。
    // ===================================================================
    public static void tickCorneredBeast(Player player, CompoundTag data) {
        int headLevel = slot(player, EnchantIds.CORNERED_BEAST, EquipmentSlot.HEAD);
        int last = data.getInt(KEY_CORNERED_LAST);

        if (headLevel <= 0) {
            // 守卫: 未附魔不生效; 若上一 tick 曾激活, 先清除残留修饰符
            if (last != 0) {
                data.putInt(KEY_CORNERED_LAST, 0);
                removeModifier(player, healingReceived(), CORNERED_HEALING_MODIFIER);
            }
            return;
        }

        boolean active = player.getHealth() <= player.getMaxHealth() * 0.25f;
        int level = active ? 1 : 0;
        if (level == last) return;
        data.putInt(KEY_CORNERED_LAST, level);
        setTransient(player, healingReceived(), CORNERED_HEALING_MODIFIER,
                level > 0 ? 0.50 : 0, MULT_TOTAL);
    }

    /**
     * Apothic(1.20.1 为 attributeslib)HEALING_RECEIVED 属性; 未加载时返回 null(setTransient 内 no-op)。
     * 1.21 源: {@code ALObjects.Attributes.HEALING_RECEIVED}(Holder)。
     */
    private static Attribute healingReceived() {
        return dev.shadowsoffire.attributeslib.api.ALObjects.Attributes.HEALING_RECEIVED.get();
    }

    // ===================================================================
    // 至高之术 supreme_art —— 属性部分
    // (1.21 源函数: ModEventHandlers#tickSupremeArt L2133-2153)
    // 主手持武器时: 交互距离 +2×等级(ADD, 实体+方块各一份)、攻速 ×(1+0.3×等级)(MULTIPLY_BASE)。
    // 1.20.1 属性映射: ENTITY_INTERACTION_RANGE → ForgeMod.ENTITY_REACH,
    //                  BLOCK_INTERACTION_RANGE → ForgeMod.BLOCK_REACH。
    // 注: 加伤 +20%/级 已由 ForgeEventHandler1201.onPlayerTick 处理, 本方法不重复。
    // ===================================================================
    public static void tickSupremeArt(Player player, CompoundTag data) {
        int level = mainHand(player, EnchantIds.SUPREME_ART);
        int last = data.getInt(KEY_SUPREME_ART_LAST_LEVEL);

        if (level <= 0) {
            // 守卫: 未主手持至高之术武器 → 清除残留修饰符
            if (last != 0) {
                data.putInt(KEY_SUPREME_ART_LAST_LEVEL, 0);
                removeModifier(player, ForgeMod.ENTITY_REACH.get(), SUPREME_ART_RANGE_MODIFIER);
                removeModifier(player, ForgeMod.BLOCK_REACH.get(), SUPREME_ART_RANGE_MODIFIER);
                removeModifier(player, Attributes.ATTACK_SPEED, SUPREME_ART_ATTACK_SPEED_MODIFIER);
            }
            return;
        }
        if (level == last) return;
        data.putInt(KEY_SUPREME_ART_LAST_LEVEL, level);

        double rangeBonus = level * 2.0;
        double speedBonus = 0.3 * level;

        setTransient(player, ForgeMod.ENTITY_REACH.get(), SUPREME_ART_RANGE_MODIFIER, rangeBonus, ADD);
        setTransient(player, ForgeMod.BLOCK_REACH.get(), SUPREME_ART_RANGE_MODIFIER, rangeBonus, ADD);
        setTransient(player, Attributes.ATTACK_SPEED, SUPREME_ART_ATTACK_SPEED_MODIFIER, speedBonus, MULT_BASE);
    }

    // ===================================================================
    // 71. 光环 halo
    // (1.21 源函数: ModEventHandlers#tickHalo L1753-1772)
    // 头盔: 光照 > 8 → 发光 40 tick; 睡觉 → 反胃 60 tick;
    //       并维护连续入睡自计时 "zhonz_halo_sleep_ticks"(+1 封顶 200 / 起床清零),
    //       该键由 HaloSleepMixin 读取 → 夜晚跳过耗时翻倍。
    // ===================================================================
    public static void tickHalo(Player player, int tickCount) {
        int level = slot(player, EnchantIds.HALO, EquipmentSlot.HEAD);
        // 维护连续入睡自计时(与 HaloSleepMixin 共享同一持久键)
        // 注: 该簿记与 1.21 一致地先于守卫执行 —— 未佩戴光环时只清零、不累加,
        //     否则摘下头盔再戴回会从旧计数续接。
        CompoundTag pd = player.getPersistentData();
        int slept = pd.getInt(KEY_HALO_SLEEP_TICKS);
        if (player.isSleeping()) {
            if (level > 0) {
                pd.putInt(KEY_HALO_SLEEP_TICKS, Math.min(slept + 1, 200));
            }
        } else if (slept != 0) {
            pd.putInt(KEY_HALO_SLEEP_TICKS, 0);
        }

        if (level <= 0) return; // 守卫: 未附魔不施加任何效果
        if (player.level().getMaxLocalRawBrightness(player.blockPosition()) > 8) {
            player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
        }
        if (player.isSleeping()) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 0, false, false));
        }
    }

    // ===================================================================
    // 48. 耐心 patience
    // (1.21 源函数: ModEventHandlers#tickPatience L1333-1349)
    // 副手盾牌 + 举盾: 累加 "zhonz_patience_hold_ticks", > 100 tick(5 秒)置
    // "zhonz_patience_ready" = true —— 由 ItemStackDurabilityMixin 消费(下次抵挡不耗耐久)。
    // ===================================================================
    public static void tickPatience(Player player, CompoundTag data, int tickCount) {
        boolean holding = player.isUsingItem() && player.getUseItem().is(Items.SHIELD);
        if (slot(player, EnchantIds.PATIENCE, EquipmentSlot.OFFHAND) <= 0) {
            // 守卫: 未附魔 → 清掉全部状态键(与 1.21 同)
            data.remove(KEY_PATIENCE_HOLD_TICKS);
            data.remove(KEY_PATIENCE_READY);
            return;
        }
        if (!holding) {
            data.remove(KEY_PATIENCE_HOLD_TICKS);
            return;
        }
        int hold = data.getInt(KEY_PATIENCE_HOLD_TICKS) + 1;
        data.putInt(KEY_PATIENCE_HOLD_TICKS, hold);
        if (hold > 100) { // 5 秒
            data.putBoolean(KEY_PATIENCE_READY, true);
        }
    }

    // ===================================================================
    // 假面的愚者 fools_mask
    // (1.21 源函数: ModEventHandlers#tickFoolsMask L2202-2216)
    // 头盔: 每 20~1200 tick 随机翻转 "zhonz_fools_mask_lucky" 并提示玩家。
    // 该键由 ForgeEventHandler1201 的 EventDamageContext 读取(幸运 → 伤害/掉落分支)。
    // ===================================================================
    public static void tickFoolsMask(Player player, CompoundTag data, int tickCount) {
        if (slot(player, EnchantIds.FOOLS_MASK, EquipmentSlot.HEAD) <= 0) return; // 守卫
        int changeTick = data.getInt(KEY_FOOLS_MASK_CHANGE_TICK);
        if (tickCount < changeTick) return;

        boolean isLucky = RANDOM.nextBoolean();
        data.putBoolean(KEY_FOOLS_MASK_LUCKY, isLucky);
        data.putInt(KEY_FOOLS_MASK_CHANGE_TICK, tickCount + RANDOM.nextInt(1181) + 20);

        if (player.level() instanceof ServerLevel) {
            player.displayClientMessage(
                    Component.literal(isLucky ? "§a§l✦ 幸运 ✦" : "§c§l✧ 不幸 ✧"),
                    true);
        }
    }

    // ===================================================================
    // 56. 惨白的午夜 pale_midnight
    // (1.21 源函数: ModEventHandlers#tickPaleMidnight L1465-1472)
    // 头盔: 常驻夜视 220 tick; 光照 < 7 → 隐身 20 tick。
    // 注: 攻击侧的"发光 + 易伤标记"属攻击链(1.21 applyPaleMidnightMark), 不在本 tick。
    // ===================================================================
    public static void tickPaleMidnight(Player player, int tickCount) {
        if (slot(player, EnchantIds.PALE_MIDNIGHT, EquipmentSlot.HEAD) <= 0) return; // 守卫
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 220, 0, false, false));
        int light = player.level().getMaxLocalRawBrightness(player.blockPosition());
        if (light < 7) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20, 0, false, false));
        }
    }

    // ===================================================================
    // 紧急救援 emergency_rescue
    // (1.21 源函数: ModEventHandlers#tickEmergencyRescue L2407-2417)
    // 任意槽位: 生命 ≤ 5 且冷却为 0 → 自身 + 10 格内同类生物 再生 III 3 秒, 冷却置 200 tick。
    // 冷却递减在 tickCooldownsAndCleanup(1.21 同)。
    // ===================================================================
    public static void tickEmergencyRescue(Player player, CompoundTag data) {
        if (anySlot(player, EnchantIds.EMERGENCY_RESCUE) <= 0) return; // 守卫
        if (player.getHealth() > 5.0f) return;
        if (data.getInt(KEY_EMERGENCY_RESCUE_CD) > 0) return;

        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 2));
        for (LivingEntity nearby : getNearbySameType(player, 10.0)) {
            nearby.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 2));
        }
        data.putInt(KEY_EMERGENCY_RESCUE_CD, 200);
    }

    // ===================================================================
    // 爆裂黎明 explosive_dawn —— 装填期无敌
    // (1.21 源函数: ModEventHandlers#tickExplosiveDawn L2437-2450)
    //
    // applyExplosiveDawn(攻击事件内)在命中时写 KEY_EXPLOSIVE_DAWN_RELOADING=true,
    // 本方法在玩家 tick 消费该标志: 装填期(手持弩且正在使用)持续给抗性提升 V;
    // 未装填则清除标志(避免残留)。
    // ===================================================================
    public static void tickExplosiveDawn(Player player, CompoundTag data) {
        int level = mainHand(player, EnchantIds.EXPLOSIVE_DAWN);
        if (level <= 0 || !player.isUsingItem() || !player.getUseItem().is(Items.CROSSBOW)) {
            if (data.getBoolean(KEY_EXPLOSIVE_DAWN_RELOADING)) {
                data.putBoolean(KEY_EXPLOSIVE_DAWN_RELOADING, false);
            }
            return;
        }
        if (data.getBoolean(KEY_EXPLOSIVE_DAWN_RELOADING)) {
            // 装填中无敌(1.21: 抗性提升 V, amp 4, 40 tick 每次刷新)
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 4, false, false));
        }
    }

    // ===================================================================
    // 冷却递减 + 过期标记清理
    // (1.21 源函数: ModEventHandlers#tickCooldownsAndCleanup L2448-2489)
    //
    // 【必须项】永劫回归 return_from_hell 的 6000 tick 冷却只在此递减 —— 缺失则永不归零,
    // 该附魔只能触发一次(1.20.1 SideEffectsBatch1 写入 KEY_RETURN_FROM_HELL_CD, 本方法递减)。
    //
    // 守卫说明: 本方法是纯簿记(递减 / 清理), 与 1.21 一致地无条件执行 ——
    // 若加附魔守卫, 玩家卸下附魔后冷却会永久冻结, 语义反而错误。
    // ===================================================================
    public static void tickCooldownsAndCleanup(Player player, CompoundTag data, int tickCount) {
        tickCooldown(data, KEY_EMERGENCY_RESCUE_CD);
        tickCooldown(data, KEY_RETURN_FROM_HELL_CD);
        tickCooldown(data, KEY_MUST_OPEN_PATH_CD);

        // 重伤(Grievous Wound)易伤窗口过期
        CompoundTag entityData = EntityDataStorage.getEntityData(player);
        if (entityData.contains(KEY_GRIEVOUS_WOUND_UNTIL)
                && player.level().getGameTime() >= entityData.getLong(KEY_GRIEVOUS_WOUND_UNTIL)) {
            entityData.remove(KEY_GRIEVOUS_WOUND_UNTIL);
        }

        // 燃烧的黄昏易伤过期(10 秒未刷新则清除)
        if (entityData.contains(KEY_BURNING_DUSK_UNTIL)
                && player.level().getGameTime() >= entityData.getLong(KEY_BURNING_DUSK_UNTIL)) {
            entityData.remove(KEY_BURNING_DUSK_PCT);
            entityData.remove(KEY_BURNING_DUSK_UNTIL);
        }

        // 惨白的午夜易伤标记过期(30 秒)
        if (entityData.contains(KEY_PALE_VULN_UNTIL)
                && player.level().getGameTime() >= entityData.getLong(KEY_PALE_VULN_UNTIL)) {
            entityData.remove(KEY_PALE_VULN_UNTIL);
        }

        // 周期性丢弃过期的"逐攻击者剥壳百分比"键
        if (tickCount % 100 == 0) {
            for (String key : new ArrayList<>(entityData.getAllKeys())) {
                if (key.startsWith(SHELL_STRIP_PREFIX)) {
                    entityData.remove(key);
                }
            }
        }

        // 硬币叠层在玩家死亡时重置(1.21: 死亡事件亦处理, 此处兜底)
        if (entityData.getInt(KEY_FLIPPING_COIN_ATTACK_STACKS) > 0 && !player.isAlive()) {
            entityData.remove(KEY_FLIPPING_COIN_ATTACK_STACKS);
            AttributeInstance maxHp = player.getAttribute(Attributes.MAX_HEALTH);
            if (maxHp != null) maxHp.removeModifier(FLIPPING_COIN_MAX_HP_UUID);
            player.removeEffect(MobEffects.DAMAGE_BOOST);
        }
    }
}
