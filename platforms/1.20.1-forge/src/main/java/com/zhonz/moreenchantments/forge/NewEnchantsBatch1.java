package com.zhonz.moreenchantments.forge;

import com.zhonz.moreenchantments.common.enchant.EnchantIds;
import com.zhonz.moreenchantments.common.storage.EntityDataStorage;
import dev.shadowsoffire.attributeslib.api.ALObjects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 1.20.1 Forge 平台: 新设计附魔 73-89 效果移植(2026-09, 对应 1.21 主工程
 * {@code ModEventHandlers} 的 "新设计附魔 73-89" 段)。
 *
 * <p>规则计算(雪的殇判据 / 雪天 / 冬痕 / 泰坦精英判定 / 各乘伤通道)下沉 common
 * ({@code EventDamageConditions} / {@code TickBonusRules}), 已由
 * {@link ForgeEventHandler1201#onLivingDamage} 接入; 本类只做"平台副作用":
 * 伤害事件 / 死亡事件 / 盾挡事件 / 右键交互 / 逐 tick 修复节流。</p>
 *
 * <p><b>1.21 → 1.20.1 差异处理:</b></p>
 * <ul>
 *   <li>1.21 {@code LivingIncomingDamageEvent}(护甲前)在 1.20.1 用
 *       {@link LivingHurtEvent}(同样在护甲结算前)表达; {@code LivingDamageEvent.Pre}
 *       用 {@link LivingDamageEvent}; {@code LivingShieldBlockEvent} 用
 *       {@link ShieldBlockEvent}; {@code PlayerTickEvent.Post} 用
 *       {@link TickEvent.PlayerTickEvent}(END 相位)。</li>
 *   <li>1.21 的 {@code ALObjects.Attributes.X}(apothic_attributes 包, Holder)在
 *       1.20.1 为 {@code dev.shadowsoffire.attributeslib.api.ALObjects.Attributes.X}
 *       ({@code RegistryObject<Attribute>}, 需 {@code .get()})。</li>
 *   <li>1.21 用 ResourceLocation 作 AttributeModifier id; 1.20.1 用
 *       (UUID, name, amount, operation), 沿用 AttributeAccess1201 / TickEffectsBatch1 的
 *       稳定 UUID 派生方案(同 id 跨实现不重复叠加)。</li>
 *   <li>1.21 以 {@code server.tell(new TickTask(...))} 延时移除临时修饰符; 1.20.1
 *       {@code MinecraftServer} 无公开延时任务 API, 目灯改以"截止时刻 + LivingTickEvent
 *       清扫"(语义等价: 最后受击 3 秒后解除, 且重复受击自然续期)。</li>
 *   <li>自定义伤害类型(frost / true_damage / weeping_fire)是 1.21 数据驱动
 *       {@code damage_type}; 1.20.1 平台无对应数据与代码构造入口 → 相关分支标 TODO
 *       (仅保留可用原版标签 {@code DamageTypeTags.IS_FREEZING} 的部分)。</li>
 * </ul>
 *
 * <p><b>覆盖清单(附魔名 → 本类方法):</b>
 * 80. eternal_standing → {@link #tickEternalStanding};
 * 81. city_shield → {@link #onShieldBlock};
 * 82. eye_lamp → {@link #onLivingHurt}(applyEyeLampMark) + {@link #onLivingTick}(解除清扫);
 * 83. solemn_mourning → {@link #tickSolemnMourning} / {@link #onLivingDamage}(record)
 *     / {@link #onLivingDeath}(击杀溅射);
 * 84. shatter → {@link #onLivingDamage}(叠层) + {@link #tickShatter}(衰减+PROT_PIERCE);
 * 85/86. sojourner / wayfarer → {@link #tickSojourner};
 * 87. thirty_million_turns → {@link #onRightClickBlock};
 * 88. heaven_chain → {@link #onLivingDamage}(tryHeavenChain);
 * 73/74. snow_wound / snow_sorrow → {@link #onLivingDamage}(applySnowSorrow)
 *     + {@link #onLivingHurt}(冬痕易伤; 雪天 ×1.5 乘伤已由 common EventDamageConditions 处理);
 * 75. unyielding_fate → {@link #onLivingHurt}(生命不低于 1; 攻击×6 真伤类型转换见 TODO)。
 * </p>
 *
 * <p><b>TODO 清单:</b>
 * (a) 雪的伤 / 唯有命运的攻击侧"伤害类型转换"(frost / true_damage, 1.21 由
 *     WeepingFireHelper 的 hurt-HEAD mixin 实现)在 1.20.1 无对应 damage_type 数据与
 *     构造入口, 无法在事件层表达(×6 真伤 / 冰霜源), 待 damage_type 数据方案后补;
 * (b) 89. mercy_equal 信标绑定均分依赖 1.21 util BeaconMercyHelper(DataComponents
 *     化信标数据), 1.20.1 信标数据经 LevelChunk 持久化无等价 API → TODO;
 * (c) 泰坦(titan)判定已下沉 common(EventDamageConditions.isEliteOrBoss), ×2 由
 *     ForgeEventHandler1201 统一乘伤通道结算, 本类无需代码(见类头);
 * (d) keen_will / sharpen / hyperthymesia 已由批 A(TickEffectsBatch1)覆盖;
 *     divine_curse / return_from_hell / smart_totem 等由批 B 覆盖, 本类不重复;
 * (e) 若未来把本类与 TickEffectsBatch1.tickSojourner 都接线, 二者写同一修饰符 id
 *     ("sojourner_move")且数值一致 → 幂等, 不会叠加(接线时只保留一处亦可)。
 * </p>
 *
 * <p>本类自身不被任何入口引用, 仅保证 compileJava 通过; 接线(事件订阅)由
 * {@link #register()} 暴露, 由平台主类在构造器调用(与 ForgeEventHandler1201 同批
 * {@code MinecraftForge.EVENT_BUS.register})。</p>
 */
public final class NewEnchantsBatch1 {

    private static final Random RANDOM = new Random();

    // ===== 数据键(与 1.21 主工程 ModEventHandlers 常量一致, 跨版本对齐)=====
    private static final String KEY_WINTER_MARK_UNTIL = "zhonz_winter_mark_until"; // 74. 雪的殇: 冬痕截止(gameTime)
    private static final String KEY_EYE_LAMP_MARK = "zhonz_eye_lamp_mark_until";   // 82. 目灯: 眩惑截止(gameTime)
    private static final String KEY_MOURNING_LAST = "zhonz_mourning_last_damage";  // 83. 庄严哀悼: 本次命中伤害
    private static final String KEY_SHATTER_STACKS = "zhonz_shatter_stacks";       // 84. 粉碎: 叠层
    private static final String KEY_SHATTER_LAST = "zhonz_shatter_last_tick";      // 84. 粉碎: 最近叠层 tick
    private static final String KEY_CHAIN_UNTIL = "zhonz_heaven_chain_until";      // 88. 天之锁: 上次触发(gameTime)
    private static final String KEY_CHAIN_PREV_DURATION = "zhonz_heaven_chain_prev_duration";
    private static final String KEY_TURNS_CD = "zhonz_turns_cd";                   // 87. 三千万转: 信息性记录

    // ===== 修饰符 id(与 1.21 主工程一致)=====
    private static final ResourceLocation EYE_LAMP_CRIT_HALF = rl("eye_lamp_crit_half");
    private static final ResourceLocation EYE_LAMP_BONUS_HALF = rl("eye_lamp_bonus_half");
    private static final ResourceLocation EYE_LAMP_MULT_HALF = rl("eye_lamp_mult_half");
    private static final ResourceLocation SHATTER_PIERCE_MODIFIER = rl("shatter_pierce");
    private static final ResourceLocation SOJOURNER_MOVE_MODIFIER = rl("sojourner_move");
    private static final ResourceLocation SOLEMN_DRAW_MODIFIER = rl("solemn_draw");

    /** 目灯眩惑截止时刻(实体弱引用 → 自动清理), 等价 1.21 TickTask(61) 延时移除。 */
    private static final Map<LivingEntity, Long> EYE_LAMP_DEADLINES = new WeakHashMap<>();

    // ===== 操作映射(1.21 ADD_VALUE/ADD_MULTIPLIED_TOTAL → 1.20.1)=====
    private static final AttributeModifier.Operation ADD = AttributeModifier.Operation.ADDITION;
    private static final AttributeModifier.Operation MULT_BASE = AttributeModifier.Operation.MULTIPLY_BASE;
    private static final AttributeModifier.Operation MULT_TOTAL = AttributeModifier.Operation.MULTIPLY_TOTAL;

    private NewEnchantsBatch1() {
    }

    /** 事件总线接线(由平台主类构造器调用; 与 ForgeEventHandler1201 同批注册)。 */
    public static void register() {
        MinecraftForge.EVENT_BUS.register(NewEnchantsBatch1.class);
    }

    // ===================================================================
    // 通用辅助(等级查询 / 属性 modifier 写入, 风格同 TickEffectsBatch1)
    // ===================================================================

    private static ResourceLocation rl(String path) {
        return new ResourceLocation(CommonConstants1201.MODID, path);
    }

    /** 稳定 UUID 派生, 同 AttributeAccess1201 —— 同一 id 的修饰符跨实现不重复叠加。 */
    private static UUID uuidOf(ResourceLocation id) {
        return UUID.nameUUIDFromBytes(("zhonz:" + id.getPath()).getBytes(StandardCharsets.UTF_8));
    }

    private static int anySlot(LivingEntity entity, String id) {
        return EnchantmentLookup1201.INSTANCE.anySlot(entity, id);
    }

    private static int mainHand(LivingEntity entity, String id) {
        return EnchantmentLookup1201.INSTANCE.mainHand(entity, id);
    }

    private static int slot(LivingEntity entity, String id, EquipmentSlot equipmentSlot) {
        return EnchantmentLookup1201.INSTANCE.slot(entity, id, equipmentSlot);
    }

    /** 物品自身是否带某附魔(id → ForgeRegistries 查询, 对应 1.21 stack.getEnchantmentLevel(holder))。 */
    private static int enchantLevel(ItemStack stack, String id) {
        if (stack.isEmpty()) return 0;
        net.minecraft.world.item.enchantment.Enchantment ench =
                ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation(CommonConstants1201.MODID, id));
        if (ench == null) return 0;
        return stack.getEnchantmentLevel(ench);
    }

    private static CompoundTag entityData(LivingEntity entity) {
        return EntityDataStorage.getEntityData(entity);
    }

    /**
     * 替换语义修饰符写入(1.20.1 版, 任意 LivingEntity): 先按 UUID 移除旧值,
     * amount == 0 时不加(等价清除)。对应 1.21 setTransient。
     */
    private static void setTransient(LivingEntity entity, Attribute attr, ResourceLocation id,
                                     double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(uuidOf(id));
        if (amount != 0) {
            inst.addTransientModifier(new AttributeModifier(uuidOf(id), id.toString(), amount, op));
        }
    }

    /** 按 UUID 移除修饰符(1.20.1 无按 ResourceLocation 移除 API)。 */
    private static void removeModifier(LivingEntity entity, Attribute attr, ResourceLocation id) {
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst != null) {
            inst.removeModifier(uuidOf(id));
        }
    }

    // ===================================================================
    // 逐 tick 效果(PlayerTickEvent.END 由调用方订阅; 1.21 源: ModEventHandlers#onPlayerTick 73-89 段)
    // ===================================================================

    /** 80. 千万年永恒屹立(1.21 源函数: ModEventHandlers#tickEternalStanding):
     *  每件带该附魔的可损坏装备, 每秒(20 tick)回复其最大耐久 2%(至少 1)。 */
    public static void tickEternalStanding(Player player) {
        for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(equipmentSlot);
            if (stack.isEmpty()) continue;
            if (enchantLevel(stack, EnchantIds.ETERNAL_STANDING) <= 0) continue;
            if (!stack.isDamageableItem() || stack.getDamageValue() <= 0) continue;
            int max = stack.getMaxDamage();
            if (max <= 0) continue;
            if (player.tickCount % 20 == 0) { // 每秒
                int repair = Math.max(1, (int) Math.ceil(max * 0.02));
                stack.setDamageValue(Math.max(0, stack.getDamageValue() - repair));
            }
        }
    }

    /** 82. 目灯(1.21 源函数: ModEventHandlers#tickSolemnMourning 属性部分是 DRAW_SPEED; 见 tickSolemnMourning)。 */
    // 目灯没有 tick 属性侧: 全部在受击时施加临时修饰符, 见 onLivingHurt/onLivingTick。

    /** 83. 庄严哀悼 —— 蓄力 +50%(1.21 源函数: ModEventHandlers#tickSolemnMourning):
     *  Apothic DRAW_SPEED 属性 ×(1+0.5)。(黑白粒子在 1.21 也仅设计文案, 无代码。) */
    public static void tickSolemnMourning(Player player) {
        int level = mainHand(player, EnchantIds.SOLEMN_MOURNING);
        double draw = level > 0 ? 0.5 : 0;
        setTransient(player, ALObjects.Attributes.DRAW_SPEED.get(), SOLEMN_DRAW_MODIFIER, draw, MULT_TOTAL);
    }

    /** 84. 粉碎(1.21 源函数: ModEventHandlers#tickShatter):
     *  叠层 5 秒(100 tick)过期后清空; 每层给 Apothic PROT_PIERCE(保护撕裂)+0.04。
     *  叠层副作用在攻击链(见 onLivingDamage/gainShatterStack)。 */
    public static void tickShatter(Player player, CompoundTag data, int tickCount) {
        int stacks = data.getInt(KEY_SHATTER_STACKS);
        if (stacks <= 0) return;
        if (tickCount - data.getInt(KEY_SHATTER_LAST) > 100) { // 5 秒过期
            data.remove(KEY_SHATTER_STACKS);
            stacks = 0;
        }
        setTransient(player, ALObjects.Attributes.PROT_PIERCE.get(), SHATTER_PIERCE_MODIFIER,
                stacks * 0.04, ADD);
    }

    /** 85/86. 他乡客 / 远行客(1.21 源函数: ModEventHandlers#tickSojourner):
     *  靴子他乡客 → 移速 ×(1+攻速×0.5)(同穿远行客 ×1); 远行客 → 常驻饱食, 双持再回血 II。 */
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
            player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 40, 0, false, false));
            if (soj) {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 1, false, false));
            }
        }
    }

    // ===================================================================
    // PlayerTickEvent 订阅(1.21 源: ModEventHandlers#onPlayerTick 的 73-89 per-tick 段)
    // ===================================================================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide()) return;
        CompoundTag data = player.getPersistentData();
        int tickCount = player.tickCount;
        tickEternalStanding(player);
        tickSolemnMourning(player);
        tickShatter(player, data, tickCount);
        tickSojourner(player);
    }

    // ===================================================================
    // LivingHurtEvent(护甲前, 1.20.1 等价 1.21 LivingIncomingDamageEvent 的 73-89 防御侧)
    // ===================================================================

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity defender = event.getEntity();
        if (defender.level().isClientSide()) return;
        if (event.getAmount() <= 0) return;
        // 调用顺序与 1.21 onLivingHurt 一致:
        // applyWinterMarkVulnerability → applyUnyieldingFateInvuln → applyEyeLampMark
        applyWinterMarkVulnerability(defender, event);
        applyUnyieldingFateInvuln(defender, event);
        applyEyeLampMark(defender, event);
    }

    /** 74. 雪的殇 —— 冬痕易伤(1.21 源函数: ModEventHandlers#applyWinterMarkVulnerability):
     *  带冬痕(10 秒)的目标受到冰霜伤害 +50%。
     *  1.20.1: 自定义 frost 伤害类型不可得(TODO), 用原版 IS_FREEZING 标签(粉雪/细雪冻结) +
     *  主手持雪的伤的近战视为冰霜(与 1.21"雪的伤攻击也算冰霜"同判)。 */
    private static void applyWinterMarkVulnerability(LivingEntity defender, LivingHurtEvent event) {
        CompoundTag defData = entityData(defender);
        long until = defData.getLong(KEY_WINTER_MARK_UNTIL);
        if (until <= 0 || defender.level().getGameTime() >= until) return;
        DamageSource source = event.getSource();
        boolean frostish = source.is(net.minecraft.tags.DamageTypeTags.IS_FREEZING);
        // TODO(1.21: source.is(FROST) 自定义 frost 类型): 1.20.1 无 damage_type 数据/构造,
        // 雪的伤攻击不会真正转为冰霜源; 此处以"雪的伤主手持有者"等价视为冰霜。
        if (!frostish && source.getEntity() instanceof LivingEntity atk
                && mainHand(atk, EnchantIds.SNOW_WOUND) > 0) {
            frostish = true;
        }
        if (!frostish) return;
        event.setAmount(event.getAmount() * 1.5f); // 冬痕: 冰霜伤害 +50%
    }

    /** 75. 唯有命运 —— 生命不低于 1(1.21 源函数: ModEventHandlers#applyUnyieldingFateInvuln /
     *  wearsUnyieldingFate)。攻击侧"伤害+500% 且转真实伤害"依赖自定义 true_damage 类型,
     *  1.20.1 不可表达 → TODO(见类头)。 */
    private static void applyUnyieldingFateInvuln(LivingEntity defender, LivingHurtEvent event) {
        if (!wearsUnyieldingFate(defender)) return;
        // 生命不会低于 1(允许扣到剩 1)
        if (defender.getHealth() - event.getAmount() <= 0) {
            event.setAmount(defender.getHealth() - 1.0f);
        }
    }

    private static boolean wearsUnyieldingFate(LivingEntity entity) {
        for (EquipmentSlot equipmentSlot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (slot(entity, EnchantIds.UNYIELDING_FATE, equipmentSlot) > 0) return true;
        }
        return false;
    }

    /** 82. 目灯(1.21 源函数: ModEventHandlers#applyEyeLampMark):
     *  受击时给攻击者挂 3 秒"眩惑": CRIT_DAMAGE / bonus_damage(加伤) / damage_multiplier(乘伤)
     *  三通道全部 ×0.5(修饰符 amount -0.5, MULT_TOTAL)。重复受击自然续期(覆盖截止时刻)。 */
    private static void applyEyeLampMark(LivingEntity defender, LivingHurtEvent event) {
        if (anySlot(defender, EnchantIds.EYE_LAMP) <= 0) return;
        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof LivingEntity attacker) || attacker == defender) return;
        long now = attacker.level().getGameTime();
        entityData(attacker).putLong(KEY_EYE_LAMP_MARK, now + 60);
        EYE_LAMP_DEADLINES.put(attacker, now + 60); // 3 秒(60 tick)后解除
        applyHalfMultiplier(attacker, ALObjects.Attributes.CRIT_DAMAGE.get(), EYE_LAMP_CRIT_HALF);
        applyHalfMultiplier(attacker, ZhonzAttributes1201.BONUS_DAMAGE.get(), EYE_LAMP_BONUS_HALF);
        applyHalfMultiplier(attacker, ZhonzAttributes1201.DAMAGE_MULTIPLIER.get(), EYE_LAMP_MULT_HALF);
    }

    private static void applyHalfMultiplier(LivingEntity entity, Attribute attr, ResourceLocation id) {
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(uuidOf(id));
        inst.addTransientModifier(new AttributeModifier(uuidOf(id), id.toString(), -0.5D, MULT_TOTAL));
    }

    private static void removeHalfMultiplier(LivingEntity entity, Attribute attr, ResourceLocation id) {
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst != null) {
            inst.removeModifier(uuidOf(id));
        }
    }

    /** 目灯解除清扫(LivingTickEvent 订阅; 等价 1.21 TickTask(61) 延时移除, 且天然支持续期)。 */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        Long deadline = EYE_LAMP_DEADLINES.get(entity);
        if (deadline == null) return;
        if (entity.level().getGameTime() >= deadline) {
            EYE_LAMP_DEADLINES.remove(entity);
            if (entity.isAlive()) {
                removeHalfMultiplier(entity, ALObjects.Attributes.CRIT_DAMAGE.get(), EYE_LAMP_CRIT_HALF);
                removeHalfMultiplier(entity, ZhonzAttributes1201.BONUS_DAMAGE.get(), EYE_LAMP_BONUS_HALF);
                removeHalfMultiplier(entity, ZhonzAttributes1201.DAMAGE_MULTIPLIER.get(), EYE_LAMP_MULT_HALF);
            }
            entityData(entity).remove(KEY_EYE_LAMP_MARK);
        }
    }

    // ===================================================================
    // LivingDamageEvent —— 攻击侧副作用(1.21 源: ModEventHandlers#onLivingDamage 的
    // attacker instanceof Player 段; 结算数值在 ForgeEventHandler1201, 本类只做副作用)
    // ===================================================================

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity defender = event.getEntity();
        if (defender.level().isClientSide()) return;
        if (event.getAmount() <= 0) return;
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof Player attacker)) return;
        CompoundTag data = attacker.getPersistentData();
        // 顺序同 1.21 applyAttackerEnchantments 的 73-89 玩家段
        applySnowSorrow(attacker, defender);          // 74. 雪的殇: 冬痕副作用(雪的伤乘伤在 common)
        gainShatterStack(attacker, data, attacker.tickCount);   // 84. 粉碎: 叠层副作用
        recordMourningDamage(attacker, event.getAmount());      // 83. 庄严哀悼: 记录本次命中伤害
        tryHeavenChain(defender, source);             // 88. 天之锁: 远程命中概率定身
    }

    /** 74. 雪的殇(1.21 源函数: ModEventHandlers#applySnowSorrow):
     *  给目标打 10 秒"冬痕"标记、在脚下空气处放细雪; 与雪的伤同附魔时强制本世界雪天(下雨)。
     *  冬痕 +50% 冰霜易伤见 applyWinterMarkVulnerability; 雪的伤"攻击视为冰霜/雪天乘伤"见 common。 */
    private static void applySnowSorrow(Player attacker, LivingEntity defender) {
        if (mainHand(attacker, EnchantIds.SNOW_SORROW) <= 0) return;
        long now = attacker.level().getGameTime();
        CompoundTag defData = entityData(defender);
        defData.putLong(KEY_WINTER_MARK_UNTIL, now + 200); // 冬痕 10 秒
        // 脚下生成细雪(空气处)
        BlockPos pos = defender.blockPosition();
        if (defender.level().getBlockState(pos).isAir()) {
            defender.level().setBlock(pos, Blocks.POWDER_SNOW.defaultBlockState(), 3);
        }
        // 与雪的伤同附魔: 强制雪天(server 下雨)
        if (mainHand(attacker, EnchantIds.SNOW_WOUND) > 0
                && attacker.level() instanceof ServerLevel serverLevel) {
            serverLevel.setWeatherParameters(0, 400, true, false);
        }
    }

    /** 84. 粉碎叠层(1.21 源函数: ModEventHandlers#gainShatterStack):
     *  每次攻击 +1 层(上限 10), 刷新 5 秒计时; 实际效果(PROT_PIERCE +4%/层)由 tickShatter 写入。 */
    private static void gainShatterStack(Player player, CompoundTag data, int tickCount) {
        if (mainHand(player, EnchantIds.SHATTER) <= 0) return;
        int stacks = data.getInt(KEY_SHATTER_STACKS) + 1;
        if (stacks > 10) stacks = 10;
        data.putInt(KEY_SHATTER_STACKS, stacks);
        data.putInt(KEY_SHATTER_LAST, tickCount);
    }

    /** 83. 庄严哀悼 —— 记录本次命中伤害(1.21 源函数: ModEventHandlers#recordMourningDamage):
     *  带附魔的主手远程命中后记录最终伤害, 供击杀溅射(见 onLivingDeath)使用。 */
    private static void recordMourningDamage(Player attacker, float amount) {
        if (mainHand(attacker, EnchantIds.SOLEMN_MOURNING) <= 0) return;
        entityData(attacker).putFloat(KEY_MOURNING_LAST, amount);
    }

    /** 88. 天之锁(1.21 源函数: ModEventHandlers#tryHeavenChain):
     *  远程(IS_PROJECTILE)命中 25% 概率给目标 3 秒"定身禁攻"
     *  (移速 VI / 挖掘 VI / 虚弱 IV); 距上次触发 <=10 秒则时长减半(下限 10 tick)。 */
    private static void tryHeavenChain(LivingEntity defender, DamageSource source) {
        if (!source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) return;
        Entity sourceEntity = source.getEntity();
        if (!(sourceEntity instanceof LivingEntity attacker)) return;
        if (mainHand(attacker, EnchantIds.HEAVEN_CHAIN) <= 0) return;
        CompoundTag atkData = entityData(attacker);
        long now = attacker.level().getGameTime();
        long last = atkData.getLong(KEY_CHAIN_UNTIL);
        int duration = 60;
        if (last > 0 && now - last <= 200) {
            int prev = atkData.getInt(KEY_CHAIN_PREV_DURATION);
            duration = Math.max(10, prev / 2);
        }
        if (RANDOM.nextFloat() < 0.25f) {
            atkData.putLong(KEY_CHAIN_UNTIL, now + duration);
            atkData.putInt(KEY_CHAIN_PREV_DURATION, duration);
            defender.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 6));
            defender.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, duration, 6));
            defender.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 4));
        }
    }

    // ===================================================================
    // LivingDeathEvent —— 83. 庄严哀悼击杀溅射(1.21 源: ModEventHandlers#onLivingDeath /
    // trySolemnMourningBurst; 使用原版 hurt + indirectMagic 伤害源, 无需 1.21 自定义类型)
    // ===================================================================

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        DamageSource source = event.getSource();
        Entity sourceEntity = source.getEntity();
        if (!(sourceEntity instanceof LivingEntity attacker)) return;
        if (mainHand(attacker, EnchantIds.SOLEMN_MOURNING) <= 0) return;
        CompoundTag data = entityData(attacker);
        float last = data.getFloat(KEY_MOURNING_LAST);
        if (last <= 0) return;
        for (LivingEntity target : victim.level().getEntitiesOfClass(LivingEntity.class,
                victim.getBoundingBox().inflate(3.0))) {
            if (target == attacker || target == victim) continue;
            // 对 3 格内敌人造成一次等额伤害(原版 indirectMagic 源)
            target.hurt(victim.level().damageSources().indirectMagic(source.getDirectEntity(), attacker), last);
        }
        data.remove(KEY_MOURNING_LAST);
    }

    // ===================================================================
    // ShieldBlockEvent —— 81. 铸就全一城盾(1.21 源: ModEventHandlers#onCityShieldBlock /
    // onShieldBlock; 1.20.1 事件为 net.minecraftforge.event.entity.living.ShieldBlockEvent,
    // 仅在成功格挡时触发, getBlockedDamage() = 本次格挡量)
    // ===================================================================

    @SubscribeEvent
    public static void onShieldBlock(ShieldBlockEvent event) {
        LivingEntity defender = event.getEntity();
        if (defender.level().isClientSide()) return;
        ItemStack shield = defender.getUseItem();
        if (shield.isEmpty() || shield.getItem() != Items.SHIELD) return;
        if (enchantLevel(shield, EnchantIds.CITY_SHIELD) <= 0) return;
        Entity sourceEntity = event.getDamageSource().getEntity();
        if (sourceEntity instanceof LivingEntity attacker) {
            // 控住攻击来源 1 秒
            attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 4));
            attacker.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20, 0));
            attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20, 2));
            attacker.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 20, 0));
        }
        // 按格挡量回复
        float blocked = event.getBlockedDamage();
        if (blocked > 0 && defender.isAlive()) {
            defender.heal(blocked);
        }
    }

    // ===================================================================
    // PlayerInteractEvent.RightClickBlock —— 87. 三千万转(1.21 源: ModEventHandlers#onRightClick /
    // onThirtyMillionTurns); 89. 慈悲见 TODO
    // ===================================================================

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        ItemStack hand = event.getItemStack();
        if (hand.isEmpty()) return;
        BlockPos pos = event.getPos();
        if (pos == null) return;
        // 87. 三千万转: 带附魔的下界之星右键已放置的龙蛋 → 获得永劫回归附魔书(不消耗星)
        if (hand.getItem() == Items.NETHER_STAR
                && enchantLevel(hand, EnchantIds.THIRTY_MILLION_TURNS) > 0
                && player.level().getBlockState(pos).is(Blocks.DRAGON_EGG)) {
            tryThirtyMillionTurns(player);
            event.setCanceled(true);
            event.setUseBlock(Event.Result.DENY);
            event.setUseItem(Event.Result.DENY);
            return;
        }
        // 89. 慈悲(mercy_equal): TODO —— 1.21 绑定逻辑在 util BeaconMercyHelper(DataComponents
        // 化信标/消耗物品附魔/均分), 1.20.1 信标数据无等价 API, 见类头 TODO (b)。
    }

    /** 87. 三千万转核心(1.21 源函数: ModEventHandlers#onThirtyMillionTurns)。 */
    private static void tryThirtyMillionTurns(Player player) {
        // 永劫回归附魔书(1.20.1: EnchantedBookItem.createForEnchantment + STORED_ENCHANTMENTS)
        net.minecraft.world.item.enchantment.Enchantment ench = ForgeRegistries.ENCHANTMENTS.getValue(
                new ResourceLocation(CommonConstants1201.MODID, EnchantIds.ETERNAL_RETURN));
        if (ench == null) return;
        ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(ench, 1));
        // 与 1.21 placeItemBackInInventory 同语义: 入背包, 满了掉在脚下(不消耗星)
        player.getInventory().placeItemBackInInventory(book);
        player.getPersistentData().putLong(KEY_TURNS_CD, player.level().getGameTime());
    }
}
