package com.zhonz.moreenchantments.forge;

import com.zhonz.moreenchantments.common.damage.EnchantSetPieces;
import com.zhonz.moreenchantments.common.enchant.EnchantIds;
import com.zhonz.moreenchantments.common.storage.EntityDataStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.UUID;

/**
 * 1.20.1 Forge: 附魔【攻击/死亡事件副作用】批量移植(Batch 1)。
 *
 * 1.21 源: 根工程 {@code ModEventHandlers.java}(src/main/java/com/zhonz/moreenchantments/event/ModEventHandlers.java)。
 * 每个静态方法对应源文件中的一个副作用函数/事件内行为; javadoc 标注 1.21 源函数名与行号。
 *
 * 事件挂接约定(本文件只提供可编译静态方法, 由平台事件订阅调用):
 * <ul>
 *   <li>攻击命中组(护甲结算后, 对应 1.20.1 {@code LivingDamageEvent}, 与 ForgeEventHandler1201.onLivingDamage 同相位):
 *       applySanction / recordShellStripRaw + applyShellStrip / applySelfDoubt / applyFlippingCoin /
 *       applyGrievousWound / applyAreaStrike / applyExplosiveDawn / applyWeepingChildIgnite /
 *       applyBurningDusk / applyHaltSlow / applyPaleMidnightMark / applyFoolsMaskSideEffects</li>
 *   <li>防御/受击组(护甲结算前, 对应 1.20.1 {@code LivingHurtEvent} —— 1.21 的 LivingIncomingDamageEvent):
 *       recordShellStripRaw / applyBurningDuskVulnerability / applyPaleMidnightVulnerability / applyFoolsMaskOnHit</li>
 *   <li>死亡组({@code LivingDeathEvent}): recordBloodPathKill / trySmartTotem / tryReturnFromHell / tryDivineProtection</li>
 * </ul>
 *
 * 1.21 → 1.20.1 适配说明:
 * <ul>
 *   <li>附魔等级: EnchantmentLookup1201.INSTANCE.mainHand/anySlot/slot(entity, id)(id = EnchantIds 常量)。</li>
 *   <li>EntityDataStorage 键字符串与 1.21 源 KEY_xxx 常量值相同(内联字符串)。</li>
 *   <li>1.20.1 AttributeModifier = (UUID, name, amount, Operation): 移除按 UUID(无 1.21 按 ResourceLocation 移除)。</li>
 *   <li>Operation 改名: 1.21 ADD_VALUE/ADD_MULTIPLIED_TOTAL → 1.20.1 ADDITION/MULTIPLY_TOTAL。</li>
 *   <li>加伤/乘伤"百分比"部分已下沉 common(EventDamageConditions / UnifiedDamageEngine)由
 *       ForgeEventHandler1201 结算, 本批只移植链上"非纯属性"副作用, 不重复移植加成。</li>
 * </ul>
 *
 * <b>移植状态(round 9 起陆续补齐, 现全项已落地)</b>:
 * <ol>
 *   <li>my_sea_domain(11): <b>已补齐</b> —— 标记写入 {@link AttackSideBatch1#applyMySeaDomainMark},
 *       受击读取 ForgeEventHandler1201.incomingConditionalFactor(含 1200 tick 超时清理);
 *       +60% 加成由 common computeBonusPercent 结算。{@link #applyMySeaDomainMarkTODOSkip}
 *       仅保留为文档对照, 不再被调用。</li>
 *   <li>explosive_dawn(9): <b>已补齐</b> —— 装填期无敌由
 *       {@link TickSideBatch1#tickExplosiveDawn} 消费 KEY_EXPLOSIVE_DAWN_RELOADING。</li>
 *   <li>weeping_child 自定义伤害类型 weeping_fire / frost / true_damage:
 *       <b>1.20.1 平台不可表达(保留差异)</b> —— 1.21 为数据驱动 damage_type(datapack JSON),
 *       1.20.1 无数据注册入口, 现用原版等价标签近似
 *       (IS_FREEZING 代 frost、IS_FIRE 代 weeping_fire), 见 WeepingFireMixin 脆弱点注释。</li>
 *   <li>tick 侧维护(标记过期清理 tickCooldownsAndCleanup、flipping_coin 死亡重置、
 *       fools_mask 幸运翻转、burning_dusk/pale_midnight 定时过期、explosive_dawn 装填恢复):
 *       <b>已补齐</b> —— 全部位于 {@link TickSideBatch1}, 由
 *       {@link EnchantWiring1201#onPlayerTick} 统一调用。</li>
 * </ol>
 */
public final class SideEffectsBatch1 {

    private static final Logger LOGGER = LoggerFactory.getLogger("ZhonzMoreEnchantments1201");
    private static final Random RANDOM = new Random();

    // ===== Persistent Data Keys(与 1.21 ModEventHandlers 常量值一致, 直接内联)=====
    private static final String KEY_SHELL_STRIP_RAW = "zhonz_shell_strip_raw";
    private static final String KEY_FLIPPING_COIN_ATTACK_STACKS = "zhonz_flipping_coin_attack_stacks";
    private static final String KEY_FLIPPING_COIN_TARGET_STACKS = "zhonz_flipping_coin_target_stacks";
    private static final String KEY_BURNING_DUSK_PCT = "zhonz_burning_dusk_pct";
    private static final String KEY_BURNING_DUSK_UNTIL = "zhonz_burning_dusk_until";
    private static final String KEY_PALE_VULN_UNTIL = "zhonz_pale_midnight_vuln_until";
    private static final String KEY_FOOLS_MASK_LUCKY = "zhonz_fools_mask_lucky";
    private static final String KEY_EXPLOSIVE_DAWN_RELOADING = "zhonz_explosive_dawn_reloading";
    private static final String KEY_RETURN_FROM_HELL_CD = "zhonz_return_from_hell_cd";
    private static final String BLOOD_PATH_TAG = "zhonz_blood_path_kills"; // 武器 NBT CompoundTag, key = 生物类型 id, value = 击杀数

    // 属性 modifier 用 UUID(1.20.1 移除按 UUID; 名字串与 1.21 ResourceLocation 一致以便追溯)
    private static final UUID GRIEVOUS_WOUND_HEALING_UUID = uuidOf("zhonz:grievous_wound_healing");
    private static final UUID FLIPPING_COIN_MAX_HP_UUID = uuidOf("zhonz:flipping_coin_max_hp");
    private static final UUID FLIPPING_COIN_TARGET_MAX_HP_UUID = uuidOf("zhonz:flipping_coin_target_max_hp");

    private SideEffectsBatch1() {
    }

    private static UUID uuidOf(String s) {
        return UUID.nameUUIDFromBytes(s.getBytes(StandardCharsets.UTF_8));
    }

    private static CompoundTag edata(LivingEntity entity) {
        return EntityDataStorage.getEntityData(entity);
    }

    private static boolean isRangedWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        net.minecraft.world.item.Item item = stack.getItem();
        return item == Items.BOW || item == Items.CROSSBOW || item == Items.TRIDENT;
    }

    private static boolean isTotem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.TOTEM_OF_UNDYING;
    }

    // ===================================================================
    // A. 攻击命中副作用组(护甲结算后, LivingDamageEvent 相位调用)
    // ===================================================================

    /**
     * 1. 制裁(Sanction): 攻击直接扣血 = 目标最大生命 × 1% × 等级(真伤, 不经伤害事件)。
     * 1.21 源: {@code ModEventHandlers.applySanction(L645)}。
     * amount 原样返回(真伤为额外副作用)。
     */
    public static float applySanction(LivingEntity attacker, LivingEntity defender, float amount) {
        int sanctionLevel = EnchantmentLookup1201.INSTANCE.mainHand(attacker, EnchantIds.SANCTION);
        if (sanctionLevel <= 0) return amount;
        float trueDamage = defender.getMaxHealth() * (0.01f * sanctionLevel);
        defender.setHealth(Math.max(0, defender.getHealth() - trueDamage));
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sanction] Dealt {} true damage (level={})", trueDamage, sanctionLevel);
        }
        return amount;
    }

    /**
     * 3(侧). 定身禁言(Suppression): 目标获得 6 秒(120 tick)缓慢 VI(amp 5), 并摧毁攻击者主手武器。
     * 1.21 源: {@code ModEventHandlers.applySuppression(L731)}。
     */
    public static void applySuppression(LivingEntity attacker, LivingEntity defender) {
        if (EnchantmentLookup1201.INSTANCE.mainHand(attacker, EnchantIds.SUPPRESSION) <= 0) return;
        defender.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 5));
        attacker.getMainHandItem().setCount(0);
    }

    /**
     * 18(前置). 剥壳(Shell Strip): 护甲结算前记录本次原始伤害(1.21 在 LivingIncomingDamageEvent 内,
     * 1.20.1 等价为 LivingHurtEvent 相位, 记录到目标数据)。
     * 1.21 源: {@code ModEventHandlers.onLivingHurt(L330-333)}(内联)。
     */
    public static void recordShellStripRaw(LivingEntity attacker, LivingEntity defender, float rawDamage) {
        if (EnchantmentLookup1201.INSTANCE.mainHand(attacker, EnchantIds.SHELL_STRIP) <= 0) return;
        edata(defender).putFloat(KEY_SHELL_STRIP_RAW, rawDamage);
    }

    /**
     * 18. 剥壳(Shell Strip): 护甲减伤部分按叠层比例(40% 起步, 同一攻击者连续命中 +5%/次, 上限 75%)
     * 作为真伤扣血。需先由 {@link #recordShellStripRaw} 记录护甲前伤害。
     * 1.21 源: {@code ModEventHandlers.applyShellStrip(L702)}。
     */
    public static void applyShellStrip(LivingEntity attacker, LivingEntity defender, float amount) {
        if (EnchantmentLookup1201.INSTANCE.mainHand(attacker, EnchantIds.SHELL_STRIP) <= 0) return;
        CompoundTag defenderData = edata(defender);
        if (!defenderData.contains(KEY_SHELL_STRIP_RAW)) return;

        float rawDamage = defenderData.getFloat(KEY_SHELL_STRIP_RAW);
        float reducedByArmor = rawDamage - amount;
        if (reducedByArmor <= 0) {
            defenderData.remove(KEY_SHELL_STRIP_RAW);
            return;
        }

        // 叠层百分比键与 1.21 相同: 挂在目标数据上, 键含攻击者 id
        String percentKey = "zhonz_shell_strip_percent_" + attacker.getId();
        float currentPercent = defenderData.contains(percentKey) ? defenderData.getFloat(percentKey) : 0.40f;
        float trueDamagePercent = Math.min(0.75f, currentPercent + 0.05f);
        float trueDamage = reducedByArmor * trueDamagePercent;
        defenderData.putFloat(percentKey, trueDamagePercent);

        if (trueDamage > 0) {
            defender.setHealth(Math.max(0, defender.getHealth() - trueDamage));
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[ShellStrip] {}% true damage: {} (reducedByArmor={})",
                        (int) (trueDamagePercent * 100), trueDamage, reducedByArmor);
            }
        }
        defenderData.remove(KEY_SHELL_STRIP_RAW);
    }

    /**
     * 5. 自我怀疑(Self Doubt): 20% 概率窃取目标第一个正面效果(时长/等级不变)给自己。
     * 1.21 源: {@code ModEventHandlers.applySelfDoubt(L681)}。
     */
    public static void applySelfDoubt(LivingEntity attacker, LivingEntity defender) {
        if (EnchantmentLookup1201.INSTANCE.anySlot(attacker, EnchantIds.SELF_DOUBT) <= 0) return;
        if (RANDOM.nextFloat() >= 0.20f) return;

        for (MobEffectInstance effect : defender.getActiveEffects()) {
            if (effect.getEffect().isBeneficial()) {
                defender.removeEffect(effect.getEffect());
                attacker.addEffect(new MobEffectInstance(effect.getEffect(), effect.getDuration(), effect.getAmplifier()));
                return;
            }
        }
    }

    /**
     * 6. 抛硬币(Flipping Coin): 攻击叠层副作用 —— 攻击者≤3 层: 力量 buff + MAX_HEALTH +层数 +1 血;
     * 目标≤3 层: 虚弱 30 秒 + MAX_HEALTH -层数。
     * (纯属性加伤未在链上; 叠层计数与 MAX_HEALTH modifier 均为 1.21 语义直移。)
     * 1.21 源: {@code ModEventHandlers.applyFlippingCoin(L793)}。
     * tick 侧(玩家死亡/重生时移除 KEY_FLIPPING_COIN_ATTACK_STACKS 及 MAX_HEALTH modifier)
     * 已由 {@link TickSideBatch1#tickCooldownsAndCleanup} 补齐。
     */
    public static void applyFlippingCoin(LivingEntity attacker, LivingEntity defender) {
        if (EnchantmentLookup1201.INSTANCE.mainHand(attacker, EnchantIds.FLIPPING_COIN) <= 0) return;

        CompoundTag attackerData = edata(attacker);
        int attackStacks = attackerData.getInt(KEY_FLIPPING_COIN_ATTACK_STACKS);
        if (attackStacks < 3) {
            attackStacks++;
            attackerData.putInt(KEY_FLIPPING_COIN_ATTACK_STACKS, attackStacks);
            attacker.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, Integer.MAX_VALUE, attackStacks - 1, false, false));
            AttributeInstance maxHp = attacker.getAttribute(Attributes.MAX_HEALTH);
            if (maxHp != null) {
                maxHp.removeModifier(FLIPPING_COIN_MAX_HP_UUID);
                maxHp.addPermanentModifier(new AttributeModifier(
                        FLIPPING_COIN_MAX_HP_UUID, "zhonz:flipping_coin_max_hp", attackStacks, AttributeModifier.Operation.ADDITION));
                attacker.setHealth(attacker.getHealth() + 1.0f);
            }
        }

        CompoundTag defenderData = edata(defender);
        int targetStacks = defenderData.getInt(KEY_FLIPPING_COIN_TARGET_STACKS);
        if (targetStacks < 3) {
            targetStacks++;
            defenderData.putInt(KEY_FLIPPING_COIN_TARGET_STACKS, targetStacks);
            defender.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 0));
            AttributeInstance maxHp = defender.getAttribute(Attributes.MAX_HEALTH);
            if (maxHp != null) {
                maxHp.removeModifier(FLIPPING_COIN_TARGET_MAX_HP_UUID);
                maxHp.addPermanentModifier(new AttributeModifier(
                        FLIPPING_COIN_TARGET_MAX_HP_UUID, "zhonz:flipping_coin_target_max_hp", -targetStacks, AttributeModifier.Operation.ADDITION));
                defender.setHealth(Math.min(defender.getHealth(), (float) maxHp.getValue()));
            }
        }
    }

    /**
     * 7. 重伤(Grievous Wound): 目标 5 秒内治疗 -30%, 经 Apothic(1.20.1 为 attributeslib)HEALING_RECEIVED 属性。
     *
     * 1.20.1 适配: ALObjects 位于 dev.shadowsoffire.attributeslib.api.ALObjects(modid 1.20.1 为
     * attributeslib, 非 1.21 的 apothic_attributes; 见 attributeslib 1.3.7 源码 ALObjects.Attributes.
     * HEALING_RECEIVED = R.attribute("healing_received")); 1.20.1 字段为 RegistryObject&lt;Attribute&gt;, 需 .get()。
     * 编译依赖为 compileOnly(ApothicAttributes 1.20.1-1.3.7), 运行期由生产环境 mods 提供;
     * get() 返回 null(Apothic 未加载)时 no-op 保护。
     * 移除时机: 1.21 用 server.tell(new TickTask(100,…)); 1.20.1 用 MinecraftServer.execute 链式延迟 100 tick(约 5 秒)。
     *
     * 1.21 源: {@code ModEventHandlers.applyGrievousWound(L827)}。
     */
    public static void applyGrievousWound(LivingEntity attacker, LivingEntity defender) {
        if (EnchantmentLookup1201.INSTANCE.mainHand(attacker, EnchantIds.GRIEVOUS_WOUND) <= 0) return;
        Attribute healingReceived = dev.shadowsoffire.attributeslib.api.ALObjects.Attributes.HEALING_RECEIVED.get();
        if (healingReceived == null) return;
        AttributeInstance inst = defender.getAttribute(healingReceived);
        if (inst == null) return;

        inst.removeModifier(GRIEVOUS_WOUND_HEALING_UUID);
        inst.addTransientModifier(new AttributeModifier(
                GRIEVOUS_WOUND_HEALING_UUID, "zhonz:grievous_wound_healing", -0.30D, AttributeModifier.Operation.MULTIPLY_TOTAL));

        // 5 秒(100 tick)后移除。attributeslib 的 heal 处理按 HEALING_RECEIVED 属性值自动缩放治疗。
        if (defender.level() instanceof ServerLevel serverLevel) {
            final int[] remaining = {100};
            Runnable removal = new Runnable() {
                @Override
                public void run() {
                    if (--remaining[0] > 0) {
                        if (defender.isAlive()) serverLevel.getServer().execute(this);
                        return;
                    }
                    AttributeInstance i = defender.getAttribute(healingReceived);
                    if (i != null) i.removeModifier(GRIEVOUS_WOUND_HEALING_UUID);
                }
            };
            serverLevel.getServer().execute(removal);
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[GrievousWound] Applied -30% healing received to {} for 5s", defender.getName().getString());
        }
    }

    /**
     * 8. 范围打击(Area Strike): 远程武器(弓/弩/三叉戟)命中后对目标周围溅射 AoE 伤害。
     * 半径 2+2×等级, 溅射比例 Lv1 30% / Lv2 40% / Lv3+ 55%, 随距离线性衰减, 用原版 hurt(与 1.21 相同)。
     * 1.21 源: {@code ModEventHandlers.applyAreaStrike(L663)}。
     */
    public static void applyAreaStrike(LivingEntity attacker, LivingEntity defender, DamageSource source,
                                       ItemStack mainHand, float amount) {
        int areaStrikeLevel = EnchantmentLookup1201.INSTANCE.mainHand(attacker, EnchantIds.AREA_STRIKE);
        if (areaStrikeLevel <= 0 || !isRangedWeapon(mainHand)) return;

        double radius = 2.0 + areaStrikeLevel * 2.0;
        float aoeRatio = areaStrikeLevel == 1 ? 0.30f : (areaStrikeLevel == 2 ? 0.40f : 0.55f);
        float aoeDamage = amount * aoeRatio;
        for (LivingEntity nearby : defender.level().getEntitiesOfClass(LivingEntity.class,
                defender.getBoundingBox().inflate(radius))) {
            if (nearby == defender || nearby == attacker || !nearby.isAlive() || !attacker.canAttack(nearby)) {
                continue;
            }
            float falloff = (float) Math.max(0, 1.0 - (nearby.distanceTo(defender) / radius));
            nearby.hurt(source, aoeDamage * falloff);
        }
    }

    /**
     * 9. 爆裂黎明(Explosive Dawn): 弩命中后对目标周围 3 波(半径 9/12/15)大范围溅射,
     * 每波 = 本次伤害 ×4 ×(1 - 距离/波半径), 并打"装填中"标记。
     *
     * 1.21 源: {@code ModEventHandlers.applyExplosiveDawn(L737)}。
     * KEY_EXPLOSIVE_DAWN_RELOADING 的消费已由 {@link TickSideBatch1#tickExplosiveDawn}
     * (玩家 tick)补齐 —— 装填期给抗性提升 V, 未装填则清除标志。
     * 本方法负责溅射 + 写标记两处事件内行为。若 1.20.1 弩命中链无法保证 source.getEntity()
     * 为主手持弩者(箭矢为主手物), 则该触发点由挂接方决定是否保留。
     */
    public static void applyExplosiveDawn(LivingEntity attacker, LivingEntity defender, DamageSource source,
                                          ItemStack mainHand, float amount) {
        if (EnchantmentLookup1201.INSTANCE.mainHand(attacker, EnchantIds.EXPLOSIVE_DAWN) <= 0
                || mainHand.getItem() != Items.CROSSBOW) {
            return;
        }
        float splashDamage = amount * 4.0f;
        double splashRadius = 15.0;
        for (int wave = 0; wave < 3; wave++) {
            double waveRadius = splashRadius * (0.6 + 0.2 * wave);
            for (LivingEntity nearby : defender.level().getEntitiesOfClass(LivingEntity.class,
                    defender.getBoundingBox().inflate(waveRadius))) {
                if (nearby == defender || nearby == attacker || !nearby.isAlive() || !attacker.canAttack(nearby)) {
                    continue;
                }
                float falloff = (float) Math.max(0, 1.0 - (nearby.distanceTo(defender) / waveRadius));
                nearby.hurt(source, splashDamage * falloff);
            }
        }
        edata(attacker).putBoolean(KEY_EXPLOSIVE_DAWN_RELOADING, true);
    }

    /**
     * 11. 我的海疆(My Sea Domain)—— <b>已补齐</b>(本方法保留为文档对照):
     * 1.21 链上副作用为"三叉戟命中把 KEY_MY_SEA_DOMAIN_START 写到目标" + 受击结算读标记放大
     * (+30%→+60%, 60 秒), 属"写标记与同一伤害结算循环内读标记"的 1.21 标记流;
     * 且该附魔 +60% 加成已由 common EventDamageConditions.computeBonusPercent 在 1.20.1 通道结算。
     * 【已补齐】标记写入与受击读取已实现: {@link AttackSideBatch1#applyMySeaDomainMark}(写键, 由
     * ForgeEventHandler1201.onLivingDamage 攻击段调用) + ForgeEventHandler1201.incomingConditionalFactor
     * (读键, 含 1200 tick 超时清理)。本方法仅保留为文档对照, 不再被调用。
     * 1.21 源: {@code ModEventHandlers.applyMySeaDomainMark(L657)} / applyMySeaDomainVulnerability(L1005)。
     */
    public static void applyMySeaDomainMarkTODOSkip(LivingEntity attacker, LivingEntity defender) {
        // 空实现保留仅为文档对照: 标记流已由 AttackSideBatch1.applyMySeaDomainMark 落地, 本方法不再被调用。
    }

    /**
     * 43(攻击侧). 燃烧的黄昏(Burning Dusk): 目标在燃烧时叠加火焰易伤 +10%
     * (同持孤独的正午 → +20%, 无上限), 刷新 10 秒窗口(200 tick)。
     * 1.21 源: {@code ModEventHandlers.applyBurningDusk(L1169)}。
     * 窗口过期清理(KEY_BURNING_DUSK_PCT/UNTIL)已由 {@link TickSideBatch1#tickCooldownsAndCleanup}
     * 在玩家 tick 补齐; 非玩家目标过期另在受击时惰性清理(见 applyBurningDuskVulnerability)。
     */
    public static void applyBurningDusk(LivingEntity attacker, LivingEntity defender) {
        if (EnchantmentLookup1201.INSTANCE.mainHand(attacker, EnchantIds.BURNING_DUSK) <= 0) return;
        if (!defender.isOnFire()) return;
        boolean noon = EnchantmentLookup1201.INSTANCE.mainHand(attacker, EnchantIds.LONELY_NOON) > 0;
        CompoundTag d = edata(defender);
        float pct = d.getFloat(KEY_BURNING_DUSK_PCT);
        float add = noon ? 0.20f : 0.10f;
        d.putFloat(KEY_BURNING_DUSK_PCT, pct + add);
        d.putLong(KEY_BURNING_DUSK_UNTIL, defender.level().getGameTime() + 200);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[BurningDusk] target burning, +{}% -> total {}%", add * 100, (pct + add) * 100);
        }
    }

    /**
     * 43(受击侧). 燃烧的黄昏火焰易伤结算: 目标带燃烧易伤叠层且本次伤害为火焰(IS_FIRE tag)
     * 时放大 ×(1+pct)。
     * 1.20.1 适配: LivingIncomingDamageEvent(1.21)→ LivingHurtEvent(1.20.1, 护甲前),
     * 以"返回放大后 amount"方式让挂接方 setAmount。
     * 【平台差异·保留】1.21 另检查自定义 weeping_fire damage_type(1.21 数据驱动, source.is(WEEPING_FIRE));
     * 1.20.1 无该 damage_type 数据, 哭泣之火相关分支不移植。
     * 1.21 源: {@code ModEventHandlers.applyBurningDuskVulnerability(L1183)}。
     */
    public static float applyBurningDuskVulnerability(LivingEntity defender, DamageSource source, float amount) {
        CompoundTag d = edata(defender);
        // 惰性过期(10 秒窗口, 与 1.21 tickCooldownsAndCleanup 相同规则; 非玩家目标也在此清理)
        if (d.contains(KEY_BURNING_DUSK_UNTIL)
                && defender.level().getGameTime() >= d.getLong(KEY_BURNING_DUSK_UNTIL)) {
            d.remove(KEY_BURNING_DUSK_PCT);
            d.remove(KEY_BURNING_DUSK_UNTIL);
        }
        float pct = d.getFloat(KEY_BURNING_DUSK_PCT);
        if (pct <= 0) return amount;
        if (!source.is(DamageTypeTags.IS_FIRE)) return amount;
        return amount * (1.0f + pct);
    }

    /**
     * 44(攻击侧). 哭泣之子(Weeping Child): 点燃攻击双方 3 秒(60 tick), 取更长剩余时间。
     * (乘伤 ×3/与孤独的正午+燃烧的黄昏联动 ×2 已由 common computeConditionalMultiplier 结算。)
     * 1.21 源: {@code ModEventHandlers.applyWeepingChildIgnite(L1209)}。
     */
    public static void applyWeepingChildIgnite(LivingEntity attacker, LivingEntity defender) {
        if (EnchantmentLookup1201.INSTANCE.mainHand(attacker, EnchantIds.WEEPING_CHILD) <= 0) return;
        defender.setRemainingFireTicks(Math.max(defender.getRemainingFireTicks(), 60));
        attacker.setRemainingFireTicks(Math.max(attacker.getRemainingFireTicks(), 60));
    }

    /**
     * 54. 止步(Halt): 减速副作用 —— 目标 0.2 秒(4 tick)移动缓慢 XI(amp 10, 隐藏)。
     * (对不动目标伤害 +40% 已由 common computeBonusPercent 结算, 不重复。)
     * 1.21 源: {@code ModEventHandlers.applyHaltSlow(L1424)}。
     */
    public static void applyHaltSlow(LivingEntity attacker, LivingEntity defender) {
        if (EnchantmentLookup1201.INSTANCE.mainHand(attacker, EnchantIds.HALT) <= 0) return;
        defender.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 10, false, false));
    }

    /**
     * 56(攻击侧). 惨白的午夜(Pale Midnight, 头盔): 命中目标 30 秒发光 + 写 30 秒易伤标记。
     * (伤害 +50% 已由 common computeBonusPercent 结算, 不重复。)
     * 1.21 源: {@code ModEventHandlers.applyPaleMidnightMark(L1440)}。
     */
    public static void applyPaleMidnightMark(LivingEntity attacker, LivingEntity defender) {
        if (EnchantmentLookup1201.INSTANCE.slot(attacker, EnchantIds.PALE_MIDNIGHT, EquipmentSlot.HEAD) <= 0) return;
        defender.addEffect(new MobEffectInstance(MobEffects.GLOWING, 600, 0, false, false));
        edata(defender).putLong(KEY_PALE_VULN_UNTIL, defender.level().getGameTime() + 600);
    }

    /**
     * 56(受击侧). 惨白的午夜易伤结算: 目标带 30 秒易伤标记时, 受到伤害 ×1.3。
     * 1.20.1 适配: LivingIncomingDamageEvent(1.21)→ LivingHurtEvent(1.20.1), 返回放大后 amount。
     * 1.21 源: {@code ModEventHandlers.applyPaleMidnightVulnerability(L1447)}。
     */
    public static float applyPaleMidnightVulnerability(LivingEntity defender, float amount) {
        CompoundTag d = edata(defender);
        if (!d.contains(KEY_PALE_VULN_UNTIL)) return amount;
        if (defender.level().getGameTime() >= d.getLong(KEY_PALE_VULN_UNTIL)) {
            d.remove(KEY_PALE_VULN_UNTIL);
            return amount;
        }
        return amount * 1.3f;
    }

    /**
     * 26(攻击侧). 假面的愚者(Fools Mask, 头盔): 攻击时按幸运标志给自己随机增益(buff)或减益(debuff)。
     * (随机乘伤 ×(1~3)/×(0.01~1) 已由 common computeConditionalMultiplier 结算。)
     * 1.21 源: {@code ModEventHandlers.applyFoolsMaskSideEffects(L761)}。
     * 说明: 1.21 该函数用 EntityDataStorage.getData(弱引用表), 而幸运标志由 tickFoolsMask
     * 写进玩家 persistent data —— 对玩家为不一致; 本移植统一用 getEntityData
     * (玩家 persistent / 非玩家弱表), 与写入侧一致。
     * 幸运翻转(KEY_FOOLS_MASK_CHANGE_TICK)已由 {@link TickSideBatch1#tickFoolsMask} 在玩家 tick 补齐。
     */
    public static void applyFoolsMaskSideEffects(LivingEntity attacker, LivingEntity defender) {
        if (EnchantmentLookup1201.INSTANCE.slot(attacker, EnchantIds.FOOLS_MASK, EquipmentSlot.HEAD) <= 0) return;
        CompoundTag data = edata(attacker);
        if (data.getBoolean(KEY_FOOLS_MASK_LUCKY)) {
            applyRandomBuff(attacker);
        } else {
            applyRandomDebuff(attacker);
        }
    }

    /**
     * 26(受击侧, 附赠, 与上同族). 假面的愚者: 佩戴者(头盔)受到攻击时获得随机增益(幸运)或减益(不幸)。
     * 1.21 源: {@code ModEventHandlers.applyFoolsMaskOnHit(L772)}(1.21 挂在 LivingIncomingDamageEvent,
     * 1.20.1 等价挂 LivingHurtEvent)。
     */
    public static void applyFoolsMaskOnHit(LivingEntity defender) {
        if (EnchantmentLookup1201.INSTANCE.slot(defender, EnchantIds.FOOLS_MASK, EquipmentSlot.HEAD) <= 0) return;
        CompoundTag data = edata(defender);
        if (data.getBoolean(KEY_FOOLS_MASK_LUCKY)) {
            applyRandomBuff(defender);
        } else {
            applyRandomDebuff(defender);
        }
    }

    /**
     * 随机增益池(10 种, 10 秒)。1.21 源: {@code ModEventHandlers.applyRandomBuff(L2519)}。
     */
    private static void applyRandomBuff(LivingEntity entity) {
        MobEffectInstance[] buffs = {
                new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 1),
                new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 1),
                new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 1),
                new MobEffectInstance(MobEffects.REGENERATION, 200, 0),
                new MobEffectInstance(MobEffects.JUMP, 200, 1),
                new MobEffectInstance(MobEffects.DIG_SPEED, 200, 1),
                new MobEffectInstance(MobEffects.INVISIBILITY, 200, 0),
                new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0),
                new MobEffectInstance(MobEffects.WATER_BREATHING, 200, 0),
                new MobEffectInstance(MobEffects.NIGHT_VISION, 200, 0)
        };
        entity.addEffect(buffs[RANDOM.nextInt(buffs.length)]);
    }

    /**
     * 随机减益池(9 种)。1.21 源: {@code ModEventHandlers.applyRandomDebuff(L2535)}。
     */
    private static void applyRandomDebuff(LivingEntity entity) {
        MobEffectInstance[] debuffs = {
                new MobEffectInstance(MobEffects.WEAKNESS, 200, 1),
                new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1),
                new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 200, 1),
                new MobEffectInstance(MobEffects.WITHER, 200, 0),
                new MobEffectInstance(MobEffects.POISON, 200, 1),
                new MobEffectInstance(MobEffects.BLINDNESS, 200, 0),
                new MobEffectInstance(MobEffects.HUNGER, 200, 1),
                new MobEffectInstance(MobEffects.LEVITATION, 100, 0),
                new MobEffectInstance(MobEffects.UNLUCK, 200, 1)
        };
        entity.addEffect(debuffs[RANDOM.nextInt(debuffs.length)]);
    }

    // ===================================================================
    // B. 死亡事件组(onLivingDeath, LivingDeathEvent 调用)
    // ===================================================================

    /**
     * 34. 血路(Blood Path)击杀计数: 目标死亡时若来源攻击者主手武器带血路,
     * 在武器 NBT(zhonz_blood_path_kills)内按目标生物类型递增击杀数。
     * 1.20.1 适配: 1.21 用 DataComponents.CUSTOM_DATA + CustomData.update →
     * 1.20.1 stack.getOrCreateTag()(见契约表)。
     * 1.21 源: {@code ModEventHandlers.recordBloodPathKill(L1963)}(在 onLivingDeath L1820 调用)。
     */
    public static void recordBloodPathKill(DamageSource source, LivingEntity victim) {
        if (!(source.getEntity() instanceof LivingEntity attacker)) return;
        ItemStack weapon = attacker.getMainHandItem();
        if (weapon.isEmpty()) return;
        if (EnchantmentLookup1201.INSTANCE.mainHand(attacker, EnchantIds.BLOOD_PATH) <= 0) return;
        String mobKey = EntityType.getKey(victim.getType()).toString();
        incrementBloodPathKill(weapon, mobKey);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[BloodPath] Recorded kill of {} (weapon={}, new count={})",
                    mobKey, weapon.getItem(), bloodPathKills(weapon, mobKey));
        }
    }

    /** 读取武器 NBT 血路击杀数(与 ForgeEventHandler1201.bloodPathKills 语义一致, 供本类记录/回读)。 */
    private static int bloodPathKills(ItemStack weapon, String mobKey) {
        CompoundTag root = weapon.getOrCreateTag();
        if (!root.contains(BLOOD_PATH_TAG, CompoundTag.TAG_COMPOUND)) return 0;
        CompoundTag kills = root.getCompound(BLOOD_PATH_TAG);
        return kills.contains(mobKey, CompoundTag.TAG_INT) ? kills.getInt(mobKey) : 0;
    }

    /** 武器 NBT 血路击杀 +1(1.20.1 getOrCreateTag 直写)。 */
    private static void incrementBloodPathKill(ItemStack weapon, String mobKey) {
        CompoundTag root = weapon.getOrCreateTag();
        CompoundTag kills;
        if (root.contains(BLOOD_PATH_TAG, CompoundTag.TAG_COMPOUND)) {
            kills = root.getCompound(BLOOD_PATH_TAG);
        } else {
            kills = new CompoundTag();
            root.put(BLOOD_PATH_TAG, kills);
        }
        kills.putInt(mobKey, bloodPathKills(weapon, mobKey) + 1);
    }

    /**
     * 智能图腾(Smart Totem): 胸甲附魔时, 玩家主/副手无图腾但背包有图腾 → 自动消耗背包图腾
     * 触发原版不死图腾效果(回 1 血 / 清除效果 / 再生+吸收+抗火 / 音效 / 图腾粒子)。
     * 返回 true = 已触发并取消死亡(调用方 return)。
     * 1.20.1 适配: 1.21 向 64 格内玩家逐一发包 ClientboundLevelParticlesPacket →
     * 1.20.1 ServerLevel.sendParticles(同 ParticleTypes.TOTEM_OF_UNDYING, 范围不限维内可见, 表现近似)。
     * 1.21 源: {@code ModEventHandlers.trySmartTotem(L1908)} / isTotem(L1959)。
     */
    public static boolean trySmartTotem(LivingEntity entity, LivingDeathEvent event) {
        if (!(entity instanceof Player player)) return false;
        if (EnchantmentLookup1201.INSTANCE.slot(player, EnchantIds.SMART_TOTEM, EquipmentSlot.CHEST) <= 0) return false;

        // 主/副手已有图腾则交给原版(避免重复消耗)
        if (isTotem(player.getMainHandItem()) || isTotem(player.getOffhandItem())) return false;

        Inventory inv = player.getInventory();
        int totemSlot = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isTotem(inv.getItem(i))) {
                totemSlot = i;
                break;
            }
        }
        if (totemSlot < 0) return false;

        ItemStack totem = inv.getItem(totemSlot);
        // 于此显圣: 消耗前记录该图腾是否带该附魔(消耗后无法再判断)
        boolean manifestTotem = ManifestHelper1201.hasManifest(totem);
        totem.shrink(1);

        event.setCanceled(true);
        player.setHealth(1.0f);
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING,
                    player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.0, 0.0, 0.0, 0.1);
        }
        // 于此显圣: 免死生效且被消耗的那颗图腾带该附魔 → 触发显圣
        if (manifestTotem) {
            ManifestHelper1201.burst(player);
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[SmartTotem] Consumed totem from inventory slot {} for {}", totemSlot, player.getName().getString());
        }
        return true;
    }

    /**
     * 永劫回归(Return From Hell, 任意槽位): 死亡时复活满血 + 清除效果 + 抗火/再生 15 秒,
     * 鞋子耐久减半, 冷却 6000 tick(5 分钟, 存 KEY_RETURN_FROM_HELL_CD)。
     * 返回 true = 已触发(调用方 return)。
     * 冷却倒计时递减(tickCooldown)已由 {@link TickSideBatch1#tickCooldownsAndCleanup} 补齐
     * —— 该键是 6000 tick 冷却唯一递减点。
     * 1.21 源: {@code ModEventHandlers.tryReturnFromHell(L1977)}。
     */
    public static boolean tryReturnFromHell(LivingEntity entity, LivingDeathEvent event) {
        int returnFromHellLevel = EnchantmentLookup1201.INSTANCE.anySlot(entity, EnchantIds.RETURN_FROM_HELL);
        if (returnFromHellLevel <= 0) return false;

        CompoundTag data = edata(entity);
        if (data.getInt(KEY_RETURN_FROM_HELL_CD) > 0) return false;

        event.setCanceled(true);
        entity.setHealth(entity.getMaxHealth());
        entity.removeAllEffects();
        entity.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 300, 0));
        entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 300, 1));

        ItemStack boots = entity.getItemBySlot(EquipmentSlot.FEET);
        if (!boots.isEmpty() && boots.isDamageableItem()) {
            int newDamage = boots.getDamageValue() + (boots.getMaxDamage() - boots.getDamageValue()) / 2;
            boots.setDamageValue(newDamage);
        }
        data.putInt(KEY_RETURN_FROM_HELL_CD, 6000);
        if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 0.8f);
        }
        // 于此显圣: 免死生效且身上(主手/副手)持有带该附魔的不死图腾 → 触发显圣
        if (ManifestHelper1201.holdsManifest(entity)) {
            ManifestHelper1201.burst(entity);
        }
        return true;
    }

    /**
     * 神圣守护(Divine Protection, 任意盔甲槽): 死亡时由第一件带附魔的盔甲触发复活
     * (满血 / 清效果 / 再生+抗性+抗火 20 秒), 该盔甲损失 1/4 耐久, 播放图腾音效。
     * 1.20.1 适配: armor.hurtAndBreak((max-damage)/4, entity, slot) 同 1.21 签名可用。
     * 1.21 源: {@code ModEventHandlers.tryDivineProtection(L2003)}。
     */
    public static void tryDivineProtection(LivingEntity entity, LivingDeathEvent event) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack armor = entity.getItemBySlot(slot);
            if (EnchantmentLookup1201.INSTANCE.slot(entity, EnchantIds.DIVINE_PROTECTION, slot) <= 0) continue;

            event.setCanceled(true);
            entity.setHealth(entity.getMaxHealth());
            entity.removeAllEffects();
            entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 400, 1));
            entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 1));
            entity.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 400, 0));
            if (armor.isDamageableItem()) {
                // 1.20.1 ItemStack 仅提供 hurtAndBreak(int, T extends LivingEntity, Consumer<T>) 泛型重载
                // (1.21 才加了 (int, LivingEntity, EquipmentSlot) 重载); 损耗后无需额外动作, 空 Consumer。
                armor.hurtAndBreak((armor.getMaxDamage() - armor.getDamageValue()) / 4, entity, ignored -> { });
            }
            if (entity.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
            }
            // 于此显圣: 免死生效且身上(主手/副手)持有带该附魔的不死图腾 → 触发显圣
            if (ManifestHelper1201.holdsManifest(entity)) {
                ManifestHelper1201.burst(entity);
            }
            return;
        }
    }

    // ===================================================================
    // 90. 热烈诚挚希望 fervent_sincere_hope(1.21 源: ModEventHandlers#onFerventOverheal)
    // 胸甲: 溢出治疗转临时生命(黄心), 上限 = 最大生命 100%; 六槽位有其他套装附魔时取消上限。
    // 吸收值必须由 ABSORPTION 效果承载 —— 原版 tick 在无该效果时会把吸收值清零。
    // ===================================================================
    private static final int FERVENT_ABSORPTION_TICKS = 600;

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onFerventOverheal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (EnchantmentLookup1201.INSTANCE.slot(entity, EnchantIds.FERVENT_SINCERE_HOPE, EquipmentSlot.CHEST) <= 0) return;

        float amount = event.getAmount();
        if (amount <= 0) return;
        float missing = entity.getMaxHealth() - entity.getHealth();
        float overflow = amount - missing;
        if (overflow <= 0) return;

        boolean setBonus = EnchantSetPieces.hasOtherPiece(entity, EnchantIds.FERVENT_SINCERE_HOPE,
                (e, id, slot) -> EnchantmentLookup1201.INSTANCE.slot(e, id, slot));
        double cap = setBonus ? Double.MAX_VALUE : entity.getMaxHealth();
        double absorp = entity.getAbsorptionAmount();
        double room = cap - absorp;
        if (room > 0) {
            double gained = Math.min(overflow, room);
            double target = absorp + gained;
            int amplifier = Math.max(0, (int) Math.ceil(target / 4.0) - 1);
            entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, FERVENT_ABSORPTION_TICKS,
                    amplifier, false, false, false));
            entity.setAbsorptionAmount((float) target);
        }

        float heal = Math.min(amount, missing);
        event.setAmount(heal > 0 ? heal : 0.0f);
        if (heal <= 0) event.setCanceled(true);
    }
}
