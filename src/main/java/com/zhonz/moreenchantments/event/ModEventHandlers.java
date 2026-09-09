package com.zhonz.moreenchantments.event;

import com.zhonz.moreenchantments.command.ModTestCommands;
import com.zhonz.moreenchantments.common.storage.EntityDataStorage;
import com.zhonz.moreenchantments.enchantment.ModEnchantments;
import dev.shadowsoffire.apothic_attributes.api.ALObjects;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Central event handler for all Zhonz's More Enchantments effects.
 *
 * Each per-tick enchantment effect lives in its own private method named
 * {@code tickXxx}, called once per player tick. The damage pipeline uses
 * {@link LivingIncomingDamageEvent} for pre-armor logic and
 * {@link LivingDamageEvent.Pre} for post-armor logic.
 */
public class ModEventHandlers {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("ZhonzMoreEnchantments");
    private static final Random RANDOM = new Random();

    // ===== Persistent Data Keys =====
    private static final String KEY_LIBERATOR_LAST_ATTACK = "zhonz_liberator_last_attack";
    private static final String KEY_FLEETING_GRACE_STORED = "zhonz_fleeting_grace_stored";
    private static final String KEY_SHELL_STRIP_RAW = "zhonz_shell_strip_raw";
    private static final String KEY_MY_SEA_DOMAIN_START = "zhonz_my_sea_domain_start";
    private static final String KEY_GRIEVOUS_WOUND_UNTIL = "zhonz_grievous_wound_until";
    private static final String KEY_FOOLS_MASK_LUCKY = "zhonz_fools_mask_lucky";
    private static final String KEY_FOOLS_MASK_CHANGE_TICK = "zhonz_fools_mask_change_tick";
    private static final String KEY_RETURN_FROM_HELL_CD = "zhonz_return_from_hell_cd";
    private static final String KEY_MUST_OPEN_PATH_CD = "zhonz_must_open_path_cd";
    private static final String KEY_FLIPPING_COIN_ATTACK_STACKS = "zhonz_flipping_coin_attack_stacks";
    private static final String KEY_FLIPPING_COIN_TARGET_STACKS = "zhonz_flipping_coin_target_stacks";
    private static final String KEY_EMERGENCY_RESCUE_CD = "zhonz_emergency_rescue_cd";
    private static final String KEY_EXPLOSIVE_DAWN_RELOADING = "zhonz_explosive_dawn_reloading";
    private static final String KEY_PROPHETS_CALL_ACTIVE = "zhonz_prophets_call_active";
    private static final String KEY_PROPHETS_CALL_UNTIL = "zhonz_prophets_call_until";
    private static final String KEY_SUPREME_ART_LAST_LEVEL = "zhonz_supreme_art_last_level";
    private static final String KEY_DIVINE_CURSE_LAST_LEVEL = "zhonz_divine_curse_last_level";
    private static final String KEY_FOREKNOWLEDGE_DODGE = "zhonz_foreknowledge_dodge_prob";
    private static final String KEY_FOREKNOWLEDGE_LAST_COMBAT = "zhonz_foreknowledge_last_combat";
    private static final String KEY_FOREKNOWLEDGE_DODGE_APPLIED = "zhonz_foreknowledge_dodge_applied"; // last DODGE_CHANCE modifier value synced to Apothic
    private static final String BLOOD_PATH_TAG = "zhonz_blood_path_kills"; // NBT CompoundTag on weapon, keys = mob type IDs, values = kill counts (int)
    private static final String KEY_BONE_BREAK_SWITCH_TICK = "zhonz_bone_break_switch_tick"; // tick when flesh→bone switch happened

    // 38. 终点倒计时
    private static final String KEY_COUNTDOWN_UNTIL = "zhonz_final_countdown_until";
    private static final String KEY_COUNTDOWN_DAMAGE = "zhonz_final_countdown_damage";
    private static final int FINAL_COUNTDOWN_TICKS = 200; // 10 秒倒计时
    // 40. 目不能追，耳未可即
    private static final String KEY_FLEET_LAST_ATTACK = "zhonz_fleet_last_attack";
    private static final String KEY_FLEET_FOOTSTEPS_LAST_LEVEL = "zhonz_fleet_footsteps_last_level";
    // 41. 节奏
    private static final String KEY_RHYTHM_LAST_ATTACK = "zhonz_rhythm_last_attack";
    private static final String KEY_RHYTHM_RECORDED_INTERVAL = "zhonz_rhythm_recorded_interval";
    // 41. 节奏: 攻击事件内"本次间隔命中"标志(副作用在链上判定并写入, 事件乘伤通道读取后清除)
    private static final String KEY_RHYTHM_HIT_ATTACK = "zhonz_rhythm_hit_attack";
    // 43. 燃烧的黄昏
    private static final String KEY_BURNING_DUSK_PCT = "zhonz_burning_dusk_pct";
    private static final String KEY_BURNING_DUSK_UNTIL = "zhonz_burning_dusk_until";
    // 48. 耐心
    private static final String KEY_PATIENCE_HOLD_TICKS = "zhonz_patience_hold_ticks";
    private static final String KEY_PATIENCE_READY = "zhonz_patience_ready";
    // 49. 不停狩
    private static final String KEY_CEASELESS_LAST_COMBAT = "zhonz_ceaseless_last_combat";
    private static final String KEY_CEASELESS_STACKS = "zhonz_ceaseless_stacks";
    // 53. 加速的未来
    private static final String KEY_ACCELERATED_LAST_DODGE = "zhonz_accelerated_last_dodge";
    // 56. 惨白的午夜
    private static final String KEY_PALE_VULN_UNTIL = "zhonz_pale_midnight_vuln_until";

    // ===== Attribute Modifier ResourceLocations =====
    // MODID 引用 common(唯一来源, stage2), 供自定义伤害类型 key 使用
    private static final String MOD_ID = com.zhonz.moreenchantments.common.CommonConstants.MODID;
    // 哭泣之子: 自定义"哭泣之火"伤害类型(数据驱动, data/zhonz_more_enchantments/damage_type/weeping_fire.json)
    private static final ResourceKey<DamageType> WEEPING_FIRE = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(MOD_ID, "weeping_fire"));
    // 雪的伤: 自定义"冰霜"伤害类型 + 统一真实伤害类型(见 damage_type/frost.json, true_damage.json)
    private static final ResourceKey<DamageType> FROST = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(MOD_ID, "frost"));
    private static final ResourceKey<DamageType> TRUE_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(MOD_ID, "true_damage"));

    // ===== 新附魔(73-89)数据键 =====
    private static final String KEY_KEEN_STACKS = "zhonz_keen_stacks";
    private static final String KEY_KEEN_LAST = "zhonz_keen_last_tick";
    private static final String KEY_SHATTER_STACKS = "zhonz_shatter_stacks";
    private static final String KEY_SHATTER_LAST = "zhonz_shatter_last_tick";
    private static final String KEY_WINTER_MARK_UNTIL = "zhonz_winter_mark_until";
    private static final String KEY_EYE_LAMP_MARK = "zhonz_eye_lamp_mark_until";
    private static final String KEY_MOURNING_LAST = "zhonz_mourning_last_damage";
    private static final String KEY_CHAIN_UNTIL = "zhonz_heaven_chain_until";
    private static final String KEY_CHAIN_PREV_DURATION = "zhonz_heaven_chain_prev_duration";
    private static final String KEY_TITAN_ELITE = "zhonz_elite"; // 泰坦: 自定义精英标记(置于实体 persistent data)
    private static final String KEY_HYPERTHYMESIA_LAST = "zhonz_hyperthymesia_last";
    private static final String KEY_ETERNAL_STANDING = "zhonz_eternal_standing_repair"; // 每秒修复节流
    private static final ResourceLocation SUPREME_ART_RANGE_MODIFIER = rl("supreme_art_range");
    private static final ResourceLocation SUPREME_ART_ATTACK_SPEED_MODIFIER = rl("supreme_art_attack_speed");
    private static final ResourceLocation DIVINE_CURSE_DAMAGE_MODIFIER = rl("divine_curse_damage");
    private static final ResourceLocation DIVINE_CURSE_ATTACK_SPEED_MODIFIER = rl("divine_curse_attack_speed");
    private static final ResourceLocation DIVINE_CURSE_RANGE_MODIFIER = rl("divine_curse_range");
    private static final ResourceLocation DIVINE_CURSE_BLOCK_RANGE_MODIFIER = rl("divine_curse_block_range");
    private static final ResourceLocation TOUGHNESS_ARMOR_MODIFIER = rl("toughness_armor");
    private static final ResourceLocation TOUGHNESS_TOUGHNESS_MODIFIER = rl("toughness_toughness");
    private static final ResourceLocation FLIPPING_COIN_MAX_HP = rl("flipping_coin_max_hp");
    private static final ResourceLocation FLIPPING_COIN_TARGET_MAX_HP = rl("flipping_coin_target_max_hp");
    // ===== Apothic Attributes 接入用修饰符 ID =====
    private static final ResourceLocation GRIEVOUS_WOUND_HEALING_MODIFIER = rl("grievous_wound_healing");
    private static final ResourceLocation FOREKNOWLEDGE_DODGE_MODIFIER = rl("foreknowledge_dodge");
    private static final ResourceLocation DIVINE_CURSE_MINING_MODIFIER = rl("divine_curse_mining");
    private static final ResourceLocation DIVINE_CURSE_DRAW_MODIFIER = rl("divine_curse_draw");
    private static final ResourceLocation DIVINE_CURSE_COOLDOWN_MODIFIER = rl("divine_curse_cooldown");
    private static final ResourceLocation FLEET_FOOTSTEPS_SPEED_MODIFIER = rl("fleet_footsteps_speed");
    private static final ResourceLocation FLEET_FOOTSTEPS_STEP_MODIFIER = rl("fleet_footsteps_step");
    // ===== 新附魔(54-72)修饰符 ID =====
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
    private static final ResourceLocation HEAVENFALL_CRIT_DAMAGE_MODIFIER = rl("heavenfall_crit_damage");
    private static final ResourceLocation HEAVENFALL_DAMAGE_MODIFIER = rl("heavenfall_damage");
    private static final ResourceLocation PRIMAL_ATTACK_SPEED_MODIFIER = rl("primal_attack_speed");
    private static final ResourceLocation PRIMAL_MOVE_MODIFIER = rl("primal_move");
    private static final ResourceLocation PRIMAL_MINING_MODIFIER = rl("primal_mining");
    private static final ResourceLocation PRIMAL_DRAW_MODIFIER = rl("primal_draw");
    private static final ResourceLocation GOLD_WINE_CUP_ATTACK_SPEED_MODIFIER = rl("gold_wine_cup_attack_speed");
    // ===== 新附魔(45-53)修饰符 ID =====
    private static final ResourceLocation APEX_DODGE_MODIFIER = rl("apex_dodge");
    private static final ResourceLocation APEX_DAMAGE_MODIFIER = rl("apex_damage");
    private static final ResourceLocation CORNERED_HEALING_MODIFIER = rl("cornered_beast_healing");
    private static final ResourceLocation VIOLENT_ATTACK_SPEED_MODIFIER = rl("violent_pulse_attack_speed");
    private static final ResourceLocation VIOLENT_MOVEMENT_MODIFIER = rl("violent_pulse_movement");
    private static final ResourceLocation SELF_BOUND_STEP_MODIFIER = rl("self_bound_step");
    private static final ResourceLocation SELF_BOUND_DAMAGE_MODIFIER = rl("self_bound_damage");
    private static final ResourceLocation SELF_BOUND_MOVEMENT_MODIFIER = rl("self_bound_movement");
    private static final ResourceLocation RAPID_ASCENT_STEP_MODIFIER = rl("rapid_ascent_step");
    private static final ResourceLocation ACCELERATED_DAMAGE_MODIFIER = rl("accelerated_future_damage");
    private static final ResourceLocation ACCELERATED_ATTACK_SPEED_MODIFIER = rl("accelerated_future_attack_speed");

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    // ===== Registration =====

    public static void register() {
        // 平台属性通道访问注入(1.21.1 NeoForge 实现; common 引擎经接口写属性, 便于 1.20.1 复用)
        com.zhonz.moreenchantments.common.damage.UnifiedDamageEngine.install(
                new com.zhonz.moreenchantments.common.version.AttributeChannelAccess() {
                    @Override
                    public net.minecraft.world.entity.ai.attributes.AttributeModifier makeAddModifier(ResourceLocation id, double amount) {
                        return new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, amount,
                                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
                    }

                    @Override
                    public void removeModifier(net.minecraft.world.entity.ai.attributes.AttributeInstance inst, ResourceLocation id) {
                        inst.removeModifier(id);
                    }
                });
        NeoForge.EVENT_BUS.register(ModEventHandlers.class);
        NeoForge.EVENT_BUS.register(ModTestCommands.class);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ModTestCommands.register(event.getDispatcher());
    }

    // ===================================================================
    // Utility Methods
    // ===================================================================

    private static int getEnchantmentLevel(LivingEntity entity, ResourceKey<Enchantment> enchantment) {
        Holder<Enchantment> holder = ModEnchantments.getHolder(enchantment);
        int level = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            int enchantLevel = entity.getItemBySlot(slot).getEnchantmentLevel(holder);
            if (enchantLevel > level) {
                level = enchantLevel;
            }
        }
        return level;
    }

    private static int getMainHandEnchantmentLevel(LivingEntity entity, ResourceKey<Enchantment> enchantment) {
        return entity.getMainHandItem().getEnchantmentLevel(ModEnchantments.getHolder(enchantment));
    }

    private static int getSlotEnchantmentLevel(LivingEntity entity, ResourceKey<Enchantment> enchantment, EquipmentSlot slot) {
        return entity.getItemBySlot(slot).getEnchantmentLevel(ModEnchantments.getHolder(enchantment));
    }

    private static boolean hasEnchantmentInInventory(Player player, ResourceKey<Enchantment> enchantment) {
        Holder<Enchantment> holder = ModEnchantments.getHolder(enchantment);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getEnchantmentLevel(holder) > 0) {
                return true;
            }
        }
        return false;
    }

    private static List<LivingEntity> getNearbySameType(LivingEntity center, double radius) {
        EntityType<?> type = center.getType();
        List<LivingEntity> result = new ArrayList<>();
        for (LivingEntity living : center.level().getEntitiesOfClass(LivingEntity.class, center.getBoundingBox().inflate(radius))) {
            if (living != center && living.isAlive() && living.getType() == type) {
                result.add(living);
            }
        }
        return result;
    }

    private static List<LivingEntity> getNearbySameTypeWithFishball(LivingEntity center, double radius) {
        List<LivingEntity> sameType = getNearbySameType(center, radius);
        List<LivingEntity> result = new ArrayList<>();
        for (LivingEntity living : sameType) {
            if (getEnchantmentLevel(living, ModEnchantments.FISHBALL) > 0) {
                result.add(living);
            }
        }
        return result;
    }

    private static CompoundTag getEntityData(LivingEntity entity) {
        return EntityDataStorage.getEntityData(entity);
    }

    private static void removeEffects(LivingEntity entity, boolean beneficial) {
        for (var it = entity.getActiveEffects().iterator(); it.hasNext(); ) {
            MobEffectInstance effect = it.next();
            if (effect.getEffect().value().isBeneficial() == beneficial) {
                entity.removeEffect(effect.getEffect());
            }
        }
    }

    private static boolean isMeleeWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item != Items.BOW && item != Items.CROSSBOW && item != Items.TRIDENT
                && item != Items.FISHING_ROD;
    }

    private static boolean isRangedWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item == Items.BOW || item == Items.CROSSBOW || item == Items.TRIDENT;
    }

    private static void tickCooldown(CompoundTag data, String key) {
        int cd = data.getInt(key);
        if (cd > 0) {
            data.putInt(key, cd - 1);
        }
    }

    // ===================================================================
    // LivingIncomingDamageEvent - Pre-armor damage routing
    // ===================================================================

    @SubscribeEvent
    public static void onLivingHurt(LivingIncomingDamageEvent event) {
        LivingEntity defender = event.getEntity();
        if (defender.level().isClientSide()) return;

        DamageSource source = event.getSource();
        Entity attackerEntity = source.getEntity();
        float rawDamage = event.getAmount();

        if (attackerEntity instanceof LivingEntity attacker) {
            // --- 18. 剥壳 Shell Strip: record pre-armor damage for the armor-reduction bonus ---
            if (getMainHandEnchantmentLevel(attacker, ModEnchantments.SHELL_STRIP) > 0) {
                getEntityData(defender).putFloat(KEY_SHELL_STRIP_RAW, rawDamage);
            }

            // --- 1. 终结 Finale: melee weapon with attack-damage >= 7 deals 100000x damage ---
            if (!tryFinale(attacker, defender, source, rawDamage, event)) return;

            // --- 3. 收割 Harvest: instant kill if post-damage HP <= 10%/20%/30% ---
            if (!tryHarvest(attacker, defender, source, rawDamage, event)) return;
        }

        // --- 33. 不完整的预知眼 Incomplete Foreknowledge Eye: dodge incoming attack ---
        if (!tryForeknowledgeDodge(defender, event)) {
            // 倏忽恩赐: fall through only when foreknowledge eye did not trigger
            recordFleetingGrace(defender, rawDamage);
        }

        // --- 44. 哭泣之子: 主手持有时免疫火焰伤害 ---
        applyWeepingChildImmunity(defender, event);
        // --- 26. 假面的愚者: 佩戴者受击时获得随机增益/减益 ---
        applyFoolsMaskOnHit(defender);
        // --- 73-89 防御侧(免疫/保命/标记副作用; 易伤/减伤乘法已迁 incoming_damage 通道) ---
        applyUnyieldingFateInvuln(defender, event);
        applyEyeLampMark(defender, event);
    }

        private static float applyFleshBoneIncoming(LivingEntity defender, float amount) {
        // 已迁 refreshIncomingAggregate(主手 ×1.3/×0.7); 保留文档对照
        return amount;
    }

    private static boolean tryFinale(LivingEntity attacker, LivingEntity defender, DamageSource source,
                                     float rawDamage, LivingIncomingDamageEvent event) {
        int finaleLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.FINALE);
        if (finaleLevel <= 0) return true;

        ItemStack mainHand = attacker.getMainHandItem();
        if (!isMeleeWeapon(mainHand)) return true;
        if (attacker.getAttributeValue(Attributes.ATTACK_DAMAGE) < 7.0) return true;

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Finale] Triggered! 100000x damage");
        }
        float newDamage = rawDamage * 100000.0f;
        event.setCanceled(true);

        if (mainHand.isDamageableItem()) {
            int durabilityCost = Math.min((int) newDamage, mainHand.getMaxDamage() - mainHand.getDamageValue());
            if (durabilityCost > 0) {
                mainHand.hurtAndBreak(durabilityCost, attacker, EquipmentSlot.MAINHAND);
            }
        }
        float newHealth = Math.max(0, defender.getHealth() - newDamage);
        defender.setHealth(newHealth);
        if (newHealth <= 0) {
            defender.die(source);
        }
        return false;
    }

    private static boolean tryHarvest(LivingEntity attacker, LivingEntity defender, DamageSource source,
                                      float rawDamage, LivingIncomingDamageEvent event) {
        int harvestLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.HARVEST);
        if (harvestLevel <= 0) return true;

        float remainingHp = defender.getHealth() - rawDamage;
        float threshold = defender.getMaxHealth() * (0.1f * harvestLevel);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Harvest] Pre-armor check: level={}, hp={}, rawDmg={}, remaining={}, threshold={}",
                    harvestLevel, defender.getHealth(), rawDamage, remainingHp, threshold);
        }
        if (remainingHp > 0 && remainingHp <= threshold) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Harvest] Triggered! Instant kill");
            }
            event.setCanceled(true);
            defender.setHealth(0);
            defender.die(source);
            return false;
        }
        return true;
    }

    /**
     * 不完整的预知眼:闪避判定由 Apothic Attributes 的 DODGE_CHANCE 属性执行。
     *
     * 事件顺序:本模组将 apothic_attributes 声明为必需依赖,Apothic 的 dodge
     * (LivingIncomingDamageEvent) 处理器先于本方法注册,因此先执行。
     * - 事件被 Apothic 取消 => 闪避成功,补上设计的 1/4 格位移;
     * - 事件未被取消 => 闪避失败,概率 -10%。
     */
    private static boolean tryForeknowledgeDodge(LivingEntity defender, LivingIncomingDamageEvent event) {
        if (getSlotEnchantmentLevel(defender, ModEnchantments.INCOMPLETE_FOREKNOWLEDGE_EYE, EquipmentSlot.HEAD) <= 0) {
            return false;
        }
        CompoundTag data = getEntityData(defender);
        data.putLong(KEY_FOREKNOWLEDGE_LAST_COMBAT, defender.level().getGameTime());

        if (event.isCanceled()) {
            // Apothic 已判定闪避成功:按设计向随机方向位移 1/4 格
            float angle = RANDOM.nextFloat() * 2.0f * (float) Math.PI;
            defender.teleportTo(defender.getX() + Math.cos(angle) * 0.25, defender.getY(),
                    defender.getZ() + Math.sin(angle) * 0.25);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[ForeknowledgeEye] Dodged via Apothic! entity={}", defender.getName().getString());
            }
            return true;
        }

        // Apothic 事件未拦截时兜底: 该 tick 的确定性闪避判定为真则自行闪避(兼容事件顺序差异)
        if (dev.shadowsoffire.apothic_attributes.impl.AttributeEvents.isDodging(defender)) {
            float angle = RANDOM.nextFloat() * 2.0f * (float) Math.PI;
            defender.teleportTo(defender.getX() + Math.cos(angle) * 0.25, defender.getY(),
                    defender.getZ() + Math.sin(angle) * 0.25);
            event.setCanceled(true);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[ForeknowledgeEye] Dodged via fallback (apoth isDodging)! entity={}", defender.getName().getString());
            }
            return true;
        }

        // 闪避失败:概率 -10%,下次 tick 同步到 DODGE_CHANCE 修饰
        float prob = data.contains(KEY_FOREKNOWLEDGE_DODGE) ? data.getFloat(KEY_FOREKNOWLEDGE_DODGE) : 0.80f;
        float newProb = Math.max(0.05f, prob - 0.10f);
        data.putFloat(KEY_FOREKNOWLEDGE_DODGE, newProb);
        data.putFloat(KEY_FOREKNOWLEDGE_DODGE_APPLIED, -1.0f); // 强制下次 tick 重新同步
        if (LOGGER.isDebugEnabled()) {
            // 诊断:Apothic 的属性值与该 tick 的确定性闪避判定
            var dodgeAttr = defender.getAttribute(ALObjects.Attributes.DODGE_CHANCE);
            LOGGER.debug("[ForeknowledgeEye] Dodge failed! newProb={} dodgeChance={} apothIsDodging={}",
                    newProb,
                    dodgeAttr != null ? dodgeAttr.getValue() : -1.0,
                    dev.shadowsoffire.apothic_attributes.impl.AttributeEvents.isDodging(defender));
        }
        return false;
    }

    private static void recordFleetingGrace(LivingEntity defender, float rawDamage) {
        if (getEnchantmentLevel(defender, ModEnchantments.FLEETING_GRACE) <= 0) return;
        CompoundTag data = getEntityData(defender);
        data.putFloat(KEY_FLEETING_GRACE_STORED, data.getFloat(KEY_FLEETING_GRACE_STORED) + rawDamage);
    }

    // ===================================================================
    // LivingDamageEvent.Pre - Post-armor damage pipeline
    // ===================================================================

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        LivingEntity defender = event.getEntity();
        if (defender.level().isClientSide()) return;

        DamageSource source = event.getSource();
        Entity attackerEntity = source.getEntity();
        float amount = event.getNewDamage();

        if (attackerEntity instanceof LivingEntity attacker) {
            // 攻击结算前确保乘伤/加伤属性已按当前状态刷新(幂等; 对所有 LivingEntity 生效, Q8)
            refreshDamageMultiplierAggregate(attacker);
            amount = applyAttackerEnchantments(attacker, defender, source, amount);
            // Q9: 事件条件型乘伤(泰坦打精英×2)在结算前以"临时 modifier"乘入
            // damage_multiplier(与 tick 聚合乘积严格相乘), 统一结算后立即清除。
            // 事件条件型加伤(冲锋手/我的海疆/血泣/破军/血路等 "+%" 语义)同样以临时
            // modifier 累加进 bonus_damage, 结算后清除, 与 tick 加伤严格累加。
            // 见 computeEventConditionalMultiplier / computeEventBonusPercent 及配套 apply/clear。
            double eventMult = computeEventConditionalMultiplier(attacker, defender);
            applyEventMultiplierTemporary(attacker, eventMult);
            double eventBonus = computeEventBonusPercent(attacker, defender);
            applyEventBonusTemporary(attacker, eventBonus);
            // flat 绝对加伤(fleet_footsteps/floating_grace)由链上副作用函数写入属性
            // 统一增伤属性结算: 先加算(bonus_damage), 再乘算(damage_multiplier), 最后 + flat
            amount = applyUnifiedDamageAttributes(attacker, amount);
            clearEventMultiplierTemporary(attacker);
            clearEventBonusTemporary(attacker);
            clearEventFlatTemporary(attacker);
            // 庄严哀悼: 以最终伤害记录"本次命中伤害"(击杀溅射基准)
            if (attacker instanceof Player mourner) {
                recordMourningDamage(mourner, amount);
            }
        }
        // 收到伤害通道: 结算前刷新 defender 的 tick 常驻减伤/易伤聚合(穿戴+状态判定)
        refreshIncomingAggregate(defender);
        amount = applyDefenderEnchantments(defender, source, amount);

        // 收到伤害通道(round-incoming): 易伤(>1)与减伤(<1)统一聚合于 defender.incoming_damage,
        // 护甲结算后乘一次(用户口径)。applyIncomingSettlement 计算本事件条件乘积 → 写聚合 → 乘 → 清除。
        amount = applyIncomingSettlement(defender, source, amount);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[DamageEvent] Final amount={} (original={})", amount, event.getOriginalDamage());
        }
        event.setNewDamage(amount);

        // --- 36. 舍吾皮肉 → 37. 断汝筋骨: switch enchantment after being hit ---
        tryFleshToBoneBreak(defender);
    }

    private static final ResourceLocation INCOMING_EVENT_MULT = rl("incoming_event_mult");

    /**
     * 收到伤害统一结算: 本伤害事件内受击侧易伤/减伤的总乘积 → incoming_damage 事件临时聚合
     * → amount × 属性值 → 清除(下一次事件重算)。副作用函数(标记过期清理等)仍各自调用。
     */
    private static float applyIncomingSettlement(LivingEntity defender, DamageSource source, float amount) {
        double product = 1.0D;
        // My Sea Domain 标记易伤: +30%~60%(1200 tick 内)
        product *= mySeaDomainVulnerabilityFactor(defender);
        // Prophet's Call 标记: ×2.7
        product *= prophetsCallFactor(defender);
        // 74. 冬痕(雪的殇标记, 冰霜伤害 +50%)
        product *= winterMarkFactor(defender, source);
        // 43. 燃烧的黄昏: 目标火焰易伤(火焰来源 ×(1+pct))
        product *= burningDuskFactor(defender, source);
        // 56. 惨白的午夜: 标记目标易伤 ×1.3
        product *= paleVulnerabilityFactor(defender);
        // 49. 不停狩: 叠层受伤 -(10%×stacks)(主手, 叠层更新一次)
        product *= ceaselessHuntFactor(defender);
        // 事件条件乘数必须以"当前属性值"为基数追加, 才能与 tick 常驻聚合保持严格乘积
        // (属性 ADD_VALUE 是加和: 直接写 product-1 会变成 product_t + product_e - 1)。
        com.zhonz.moreenchantments.common.damage.UnifiedDamageEngine.applyEventMultiplierTemporary(
                defender.getAttribute(com.zhonz.moreenchantments.attribute.ZhonzAttributes.INCOMING_DAMAGE),
                INCOMING_EVENT_MULT, product);
        amount = applyIncomingDamageAttributes(defender, amount);
        // 清除事件临时聚合(幂等)
        var inst = defender.getAttribute(com.zhonz.moreenchantments.attribute.ZhonzAttributes.INCOMING_DAMAGE);
        if (inst != null) inst.removeModifier(INCOMING_EVENT_MULT);
        return amount;
    }

    private static double winterMarkFactor(LivingEntity defender, DamageSource source) {
        CompoundTag defData = getEntityData(defender);
        long until = defData.getLong(KEY_WINTER_MARK_UNTIL);
        if (until <= 0 || defender.level().getGameTime() >= until) return 1.0D;
        boolean frostish = isFrostSource(source);
        if (!frostish && source.getEntity() instanceof LivingEntity atk
                && getMainHandEnchantmentLevel(atk, ModEnchantments.SNOW_WOUND) > 0) {
            frostish = true;
        }
        return frostish ? 1.5D : 1.0D; // 冬痕: 冰霜伤害 +50%
    }

    private static double burningDuskFactor(LivingEntity defender, DamageSource source) {
        float pct = getEntityData(defender).getFloat(KEY_BURNING_DUSK_PCT);
        if (pct <= 0) return 1.0D;
        if (!source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE) && !source.is(WEEPING_FIRE)) return 1.0D;
        return 1.0f + pct;
    }

    private static double paleVulnerabilityFactor(LivingEntity defender) {
        CompoundTag d = getEntityData(defender);
        if (!d.contains(KEY_PALE_VULN_UNTIL)) return 1.0D;
        if (defender.level().getGameTime() >= d.getLong(KEY_PALE_VULN_UNTIL)) {
            d.remove(KEY_PALE_VULN_UNTIL);
            return 1.0D;
        }
        return 1.3D;
    }

    private static double ceaselessHuntFactor(LivingEntity defender) {
        if (getMainHandEnchantmentLevel(defender, ModEnchantments.CEASELESS_HUNT) <= 0) return 1.0D;
        int stacks = updateCeaselessHunt(getEntityData(defender), defender.level().getGameTime());
        if (stacks <= 0) return 1.0D;
        return 1.0f - 0.10f * stacks;
    }

    private static double mySeaDomainVulnerabilityFactor(LivingEntity defender) {
        CompoundTag targetData = getEntityData(defender);
        if (!targetData.contains(KEY_MY_SEA_DOMAIN_START)) return 1.0D;
        long elapsed = defender.level().getGameTime() - targetData.getLong(KEY_MY_SEA_DOMAIN_START);
        if (elapsed > 1200) {
            targetData.remove(KEY_MY_SEA_DOMAIN_START);
            return 1.0D;
        }
        float vulnerability;
        if (elapsed < 200) {
            vulnerability = 0.30f;
        } else if (elapsed < 600) {
            vulnerability = 0.30f + 0.30f * ((float) (elapsed - 200) / 400f);
        } else {
            vulnerability = 0.60f;
        }
        return 1.0f + vulnerability;
    }

    private static double prophetsCallFactor(LivingEntity defender) {
        CompoundTag data = getEntityData(defender);
        if (!data.getBoolean(KEY_PROPHETS_CALL_ACTIVE)) return 1.0D;
        if (defender.level().getGameTime() >= data.getLong(KEY_PROPHETS_CALL_UNTIL)) {
            data.remove(KEY_PROPHETS_CALL_ACTIVE);
            data.remove(KEY_PROPHETS_CALL_UNTIL);
            return 1.0D;
        }
        return 2.7D; // 170% more = 2.7x
    }

    private static void tryFleshToBoneBreak(LivingEntity defender) {
        ItemStack weapon = defender.getMainHandItem();
        if (weapon.isEmpty()) return;
        if (weapon.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.FLESH_SACRIFICE)) <= 0) return;

        // Switch flesh_sacrifice → bone_break on the weapon
        Holder<Enchantment> fleshHolder = ModEnchantments.getHolder(ModEnchantments.FLESH_SACRIFICE);
        Holder<Enchantment> boneHolder = ModEnchantments.getHolder(ModEnchantments.BONE_BREAK);
        if (fleshHolder == null || boneHolder == null) return;

        ItemEnchantments current = weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(current);
        mutable.set(fleshHolder, 0); // remove old
        mutable.set(boneHolder, 1);  // add new
        weapon.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());

        // Record the switch tick for the 3-second timer
        if (defender instanceof Player player) {
            player.getPersistentData().putLong(KEY_BONE_BREAK_SWITCH_TICK, player.tickCount);
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[FleshSacrifice] Switched to Bone Break on {}'s weapon at tick {}",
                    defender.getName().getString(), defender.tickCount);
        }
    }

    private static float applyAttackerEnchantments(LivingEntity attacker, LivingEntity defender,
                                                   DamageSource source, float amount) {
        ItemStack mainHand = attacker.getMainHandItem();

        // 预知眼:主动攻击也计入战斗时间(恢复判定用)
        if (attacker instanceof Player p && getSlotEnchantmentLevel(p, ModEnchantments.INCOMPLETE_FOREKNOWLEDGE_EYE, EquipmentSlot.HEAD) > 0) {
            getEntityData(p).putLong(KEY_FOREKNOWLEDGE_LAST_COMBAT, p.level().getGameTime());
        }

        // NOTE: 终结/必须开辟的通路/收割 handled in onLivingHurt
        // NOTE: 挂 不在攻击事件中处理任何额外伤害

        amount = applySanction(attacker, defender, amount);
        // charger / army_breaker / my_sea_domain / blood_weep / blood_path / liberator 的加成
        // 已迁入统一属性通道事件临时 modifier(见 computeEventBonusPercent /
        // computeEventConditionalMultiplier, 于 onLivingDamage 结算段执行)
        // supreme_art 加伤已迁移到 bonus_damage 属性通道(tickSupremeArt 维护)
        applyMySeaDomainMark(attacker, defender);
        applyAreaStrike(attacker, defender, source, mainHand, amount);
        applySelfDoubt(attacker, defender);
        applyBloodWeepCost(attacker);
        applyShellStrip(attacker, defender, amount);
        applySuppression(attacker, defender);
        applyExplosiveDawn(attacker, defender, source, mainHand, amount);
        applyFoolsMaskSideEffects(attacker, defender);
        amount = applyFleetingGraceBonus(attacker, amount);
        applyFlippingCoin(attacker, defender);
        applyGrievousWound(attacker, defender);
        // blood_path 加成已迁入 bonus_damage 事件临时通道(computeEventBonusPercent)
        // 30. 骨碎/Bone Break ×6: 已迁移到乘伤聚合器(tickDamageMultiplierAggregator)
        // --- 38. 终点倒计时: 施加记录buff ---
        amount = applyFinalCountdown(attacker, defender, amount);
        // --- 39. 将我抹去，将你也抹去: 击杀目标及其同类 ---
        applyEraseMe(attacker, defender, source);
        // --- 40. 目不能追，耳未可即: 下次攻击+上次伤害10% ---
        amount = applyFleetFootstepsDamage(attacker, amount);
        // --- 41. 节奏: 攻击间隔稳定则+50%(计时副作用在链上, ×1.5 乘伤已迁乘伤事件通道) ---
        amount = applyRhythm(attacker, amount);
        // --- 42/44/73 乘伤已迁入 computeEventConditionalMultiplier(lonely_noon 着火 /
        //     fools_mask 随机 / weeping_child 自燃 / snow_wound 雪天), 链上只留副作用 ---
        applyWeepingChildIgnite(attacker, defender);
        // --- 43. 燃烧的黄昏: 对燃烧目标叠加火焰易伤 ---
        applyBurningDusk(attacker, defender);
        // --- 46-52. 攻击侧: 困兽之斗/剧烈搏动/不停狩/新太阳/极速攀升 ---
        amount = applyCorneredBeastDamage(attacker, amount);
        amount = applyViolentPulseAttack(attacker, amount);
        applyCeaselessHuntStack(attacker); // 叠层副作用(伤害+5%/层已迁 computeEventBonusPercent)
        amount = applyNewSun(attacker, defender, amount);
        amount = applyRapidAscentDamage(attacker, amount);
        // --- 54-57. 止步/惨白的午夜/悲伤的红 ---
        applyHaltSlow(attacker, defender); // 减速副作用(伤害+40%已迁 computeEventBonusPercent)
        applyPaleMidnightMark(attacker, defender); // 发光+易伤副作用(伤害+50%已迁 computeEventBonusPercent)
        // sorrowful_red 已迁移到 bonus_damage 属性通道(见 tickSorrowfulRedBonus)
        // titan 乘伤已迁入事件乘伤(computeEventConditionalMultiplier, Q9 试点), 链上不再手写乘
        if (attacker instanceof Player p) {
            CompoundTag pdata = p.getPersistentData();
            applyKeenWill(p, pdata, p.tickCount); // 叠层并立即把基础攻击+1 同步到 ATTACK_DAMAGE
            applySnowSorrow(p, defender);
            gainShatterStack(p, pdata, p.tickCount);
            tryHeavenChain(defender, source);
        }
        return amount;
    }

    private static float applySanction(LivingEntity attacker, LivingEntity defender, float amount) {
        int sanctionLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.SANCTION);
        if (sanctionLevel <= 0) return amount;
        float trueDamage = defender.getMaxHealth() * (0.01f * sanctionLevel);
        defender.setHealth(Math.max(0, defender.getHealth() - trueDamage));
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sanction] Dealt {} true damage (level={})", trueDamage, sanctionLevel);
        }
        return amount;
    }

    /** 11. 我的海疆攻击加成(+60%)已迁入 bonus_damage 事件临时通道; 此函数仅保留标记副作用。 */
    private static void applyMySeaDomainMark(LivingEntity attacker, LivingEntity defender) {
        if (getMainHandEnchantmentLevel(attacker, ModEnchantments.MY_SEA_DOMAIN) <= 0) return;
        if (attacker.getMainHandItem().getItem() != Items.TRIDENT) return;
        getEntityData(defender).putLong(KEY_MY_SEA_DOMAIN_START, defender.level().getGameTime());
    }

    private static void applyAreaStrike(LivingEntity attacker, LivingEntity defender, DamageSource source,
                                        ItemStack mainHand, float amount) {
        int areaStrikeLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.AREA_STRIKE);
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

    private static void applySelfDoubt(LivingEntity attacker, LivingEntity defender) {
        if (getEnchantmentLevel(attacker, ModEnchantments.SELF_DOUBT) <= 0) return;
        if (RANDOM.nextFloat() >= 0.20f) return;

        for (MobEffectInstance effect : defender.getActiveEffects()) {
            if (effect.getEffect().value().isBeneficial()) {
                defender.removeEffect(effect.getEffect());
                attacker.addEffect(new MobEffectInstance(effect.getEffect(), effect.getDuration(), effect.getAmplifier()));
                return;
            }
        }
    }

    /** 16. 血泣: 攻击自伤副作用(10/7/4 点), 加成 +20/30/45% 已迁入 bonus_damage 事件临时通道。 */
    private static void applyBloodWeepCost(LivingEntity attacker) {
        int bloodWeepLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.BLOOD_WEEP);
        if (bloodWeepLevel <= 0) return;
        float hpCost = bloodWeepLevel == 1 ? 10.0f : (bloodWeepLevel == 2 ? 7.0f : 4.0f);
        attacker.setHealth(Math.max(0, attacker.getHealth() - hpCost));
    }

    private static void applyShellStrip(LivingEntity attacker, LivingEntity defender, float amount) {
        if (getMainHandEnchantmentLevel(attacker, ModEnchantments.SHELL_STRIP) <= 0) return;
        CompoundTag defenderData = getEntityData(defender);
        if (!defenderData.contains(KEY_SHELL_STRIP_RAW)) return;

        float rawDamage = defenderData.getFloat(KEY_SHELL_STRIP_RAW);
        float reducedByArmor = rawDamage - amount;
        if (reducedByArmor <= 0) {
            defenderData.remove(KEY_SHELL_STRIP_RAW);
            return;
        }

        // Stacking percentage: 40% -> 75% (+5% per consecutive hit on the same target)
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

    private static void applySuppression(LivingEntity attacker, LivingEntity defender) {
        if (getMainHandEnchantmentLevel(attacker, ModEnchantments.SUPPRESSION) <= 0) return;
        defender.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 5));
        attacker.getMainHandItem().setCount(0);
    }

    private static void applyExplosiveDawn(LivingEntity attacker, LivingEntity defender, DamageSource source,
                                           ItemStack mainHand, float amount) {
        if (getMainHandEnchantmentLevel(attacker, ModEnchantments.EXPLOSIVE_DAWN) <= 0
                || mainHand.getItem() != Items.CROSSBOW) {
            return;
        }
        float splashDamage = amount * 4.0f;
        double splashRadius = 15.0;
        // 三次大范围溅射
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
        getEntityData(attacker).putBoolean(KEY_EXPLOSIVE_DAWN_RELOADING, true);
    }

    /** 假面的愚者: 随机乘数 ×(1~3)/×(0.01~1) 已迁 computeEventConditionalMultiplier; 此处仅随机 buff/debuff 副作用。 */
    private static void applyFoolsMaskSideEffects(LivingEntity attacker, LivingEntity defender) {
        if (getSlotEnchantmentLevel(attacker, ModEnchantments.FOOLS_MASK, EquipmentSlot.HEAD) <= 0) return;
        CompoundTag data = EntityDataStorage.getData(attacker);
        if (data.getBoolean(KEY_FOOLS_MASK_LUCKY)) {
            applyRandomBuff(attacker);
        } else {
            applyRandomDebuff(attacker);
        }
    }

    /** 假面的愚者: 佩戴者受到攻击时获得随机增益(幸运)或减益(不幸) */
    private static void applyFoolsMaskOnHit(LivingEntity defender) {
        if (getSlotEnchantmentLevel(defender, ModEnchantments.FOOLS_MASK, EquipmentSlot.HEAD) <= 0) return;
        CompoundTag data = getEntityData(defender);
        if (data.getBoolean(KEY_FOOLS_MASK_LUCKY)) {
            applyRandomBuff(defender);
        } else {
            applyRandomDebuff(defender);
        }
    }

    /** 倏忽恩赐: 记录受击值已由 recordFleetingGrace 累积; 攻击时把记录×2 写入 flat_damage(绝对加伤通道), 不再 amount 上加。 */
    private static float applyFleetingGraceBonus(LivingEntity attacker, float amount) {
        if (getEnchantmentLevel(attacker, ModEnchantments.FLEETING_GRACE) <= 0) return amount;
        CompoundTag data = getEntityData(attacker);
        float stored = data.getFloat(KEY_FLEETING_GRACE_STORED);
        if (stored <= 0) return amount;
        data.putFloat(KEY_FLEETING_GRACE_STORED, 0);
        setFlatDamage(attacker, EVENT_FLAT_GRACE, stored * 2.0D);
        return amount;
    }

    private static void applyFlippingCoin(LivingEntity attacker, LivingEntity defender) {
        if (getMainHandEnchantmentLevel(attacker, ModEnchantments.FLIPPING_COIN) <= 0) return;

        CompoundTag attackerData = getEntityData(attacker);
        int attackStacks = attackerData.getInt(KEY_FLIPPING_COIN_ATTACK_STACKS);
        if (attackStacks < 3) {
            attackStacks++;
            attackerData.putInt(KEY_FLIPPING_COIN_ATTACK_STACKS, attackStacks);
            attacker.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, Integer.MAX_VALUE, attackStacks - 1, false, false));
            var maxHp = attacker.getAttribute(Attributes.MAX_HEALTH);
            if (maxHp != null) {
                maxHp.removeModifier(FLIPPING_COIN_MAX_HP);
                maxHp.addPermanentModifier(new AttributeModifier(
                        FLIPPING_COIN_MAX_HP, attackStacks, AttributeModifier.Operation.ADD_VALUE));
                attacker.setHealth(attacker.getHealth() + 1.0f);
            }
        }

        CompoundTag defenderData = getEntityData(defender);
        int targetStacks = defenderData.getInt(KEY_FLIPPING_COIN_TARGET_STACKS);
        if (targetStacks < 3) {
            targetStacks++;
            defenderData.putInt(KEY_FLIPPING_COIN_TARGET_STACKS, targetStacks);
            defender.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 0));
            var maxHp = defender.getAttribute(Attributes.MAX_HEALTH);
            if (maxHp != null) {
                maxHp.removeModifier(FLIPPING_COIN_TARGET_MAX_HP);
                maxHp.addPermanentModifier(new AttributeModifier(
                        FLIPPING_COIN_TARGET_MAX_HP, -targetStacks, AttributeModifier.Operation.ADD_VALUE));
                defender.setHealth(Math.min(defender.getHealth(), (float) maxHp.getValue()));
            }
        }
    }

    private static void applyGrievousWound(LivingEntity attacker, LivingEntity defender) {
        if (getMainHandEnchantmentLevel(attacker, ModEnchantments.GRIEVOUS_WOUND) <= 0) return;
        // Apothic Attributes 接入: 给目标挂 HEALING_RECEIVED -30% 修饰,5 秒(100 tick)后移除。
        // Apothic 的 heal(LivingHealEvent) 处理会自动按该属性缩放所有治疗效果。
        addTransient(defender, ALObjects.Attributes.HEALING_RECEIVED,
                GRIEVOUS_WOUND_HEALING_MODIFIER, -0.30, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        if (defender.level() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().tell(new net.minecraft.server.TickTask(100, () ->
                    removeModifier(defender, ALObjects.Attributes.HEALING_RECEIVED, GRIEVOUS_WOUND_HEALING_MODIFIER)));
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[GrievousWound] Applied -30% healing received to {} for 5s", defender.getName().getString());
        }
    }

    private static float applyBloodPathBonus(LivingEntity attacker, LivingEntity defender, float amount) {
        ItemStack weapon = attacker.getMainHandItem();
        if (weapon.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.BLOOD_PATH)) <= 0) return amount;
        String mobKey = getMobTypeId(defender);
        int kills = getBloodPathKillCount(weapon, mobKey);
        if (kills <= 0) return amount;
        float multiplier = 1.0f + kills * 0.001f; // +0.1% per kill
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[BloodPath] mob={}, kills={}, multiplier={}, amount={} -> {}",
                    mobKey, kills, multiplier, amount, amount * multiplier);
        }
        return amount * multiplier;
    }

    private static String getMobTypeId(LivingEntity entity) {
        return EntityType.getKey(entity.getType()).toString(); // e.g. "minecraft:zombie"
    }

    public static int getBloodPathKillCount(ItemStack weapon, String mobKey) {
        CustomData customData = weapon.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return 0;
        CompoundTag root = customData.copyTag();
        if (!root.contains(BLOOD_PATH_TAG, CompoundTag.TAG_COMPOUND)) return 0;
        CompoundTag killsTag = root.getCompound(BLOOD_PATH_TAG);
        return killsTag.contains(mobKey, CompoundTag.TAG_INT) ? killsTag.getInt(mobKey) : 0;
    }

    public static void incrementBloodPathKill(ItemStack weapon, String mobKey) {
        CustomData.update(DataComponents.CUSTOM_DATA, weapon, root -> {
            CompoundTag killsTag;
            if (root.contains(BLOOD_PATH_TAG, CompoundTag.TAG_COMPOUND)) {
                killsTag = root.getCompound(BLOOD_PATH_TAG);
            } else {
                killsTag = new CompoundTag();
                root.put(BLOOD_PATH_TAG, killsTag);
            }
            int current = killsTag.contains(mobKey, CompoundTag.TAG_INT) ? killsTag.getInt(mobKey) : 0;
            killsTag.putInt(mobKey, current + 1);
        });
    }

    public static CompoundTag getBloodPathKillTag(ItemStack weapon) {
        CustomData customData = weapon.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return new CompoundTag();
        CompoundTag root = customData.copyTag();
        if (!root.contains(BLOOD_PATH_TAG, CompoundTag.TAG_COMPOUND)) return new CompoundTag();
        return root.getCompound(BLOOD_PATH_TAG);
    }

    public static void setBloodPathKillTag(ItemStack weapon, CompoundTag killCounts) {
        CustomData.update(DataComponents.CUSTOM_DATA, weapon, root -> {
            root.put(BLOOD_PATH_TAG, killCounts.copy());
        });
    }

    // --- 37. 断汝筋骨 Bone Break: +500% outgoing damage (6x multiplier) ---
    private static float applyBoneBreakAttack(LivingEntity attacker, float amount) {
        // 已迁移: ×6 乘伤由 tickDamageMultiplierAggregator 写入 damage_multiplier
        return amount;
    }

    private static float applyDefenderEnchantments(LivingEntity defender, DamageSource source, float amount) {
        Entity attackerEntity = source.getEntity();
        applyDeepSeasGrace(defender, amount);
        applyGemUmbrella(defender, attackerEntity);
        applyFishballTransfer(defender, source, amount); // adjusts `amount` via local variable
        applyToughnessShield(defender);
        applyEmergencyRescue(defender, amount);
        // 终点倒计时: 倒计时期间累积目标受到的伤害
        amount = accumulateFinalCountdown(defender, amount);
        return amount;
    }

    private static float applyDeepSeasGrace(LivingEntity defender, float amount) {
        int level = getEnchantmentLevel(defender, ModEnchantments.DEEP_SEAS_GRACE);
        if (level <= 0) return amount;
        float healRatio = level == 1 ? 0.05f : (level == 2 ? 0.10f : 0.20f);
        float healAmount = defender.getMaxHealth() * healRatio;
        // Heal on the next tick so the player sees the full damage first.
        if (defender.level() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().tell(new net.minecraft.server.TickTask(1, () -> {
                if (defender.isAlive()) defender.heal(healAmount);
            }));
        }
        return amount;
    }

    private static void applyGemUmbrella(LivingEntity defender, Entity attackerEntity) {
        if (getSlotEnchantmentLevel(defender, ModEnchantments.GEM_UMBRELLA, EquipmentSlot.LEGS) <= 0) return;
        if (!(attackerEntity instanceof LivingEntity attacker)) return;

        // Knock attacker 10 blocks away and up
        Vec3 knockDir = defender.position().vectorTo(attacker.position()).normalize().scale(10.0);
        attacker.setDeltaMovement(knockDir.x, Math.abs(knockDir.y) + 0.5, knockDir.z);
        attacker.hurtMarked = true;

        // Drop 1-5 random minerals at the attacker's location
        if (attacker.level() instanceof ServerLevel serverLevel) {
            int quantity = 1 + RANDOM.nextInt(5);
            for (int i = 0; i < quantity; i++) {
                ItemEntity item = new ItemEntity(serverLevel,
                        attacker.getX(), attacker.getY() + 1, attacker.getZ(), getRandomMineral());
                item.setDeltaMovement(
                        (RANDOM.nextFloat() - 0.5f) * 0.3f,
                        RANDOM.nextFloat() * 0.5f,
                        (RANDOM.nextFloat() - 0.5f) * 0.3f);
                serverLevel.addFreshEntity(item);
            }
        }
    }

    private static void applyFishballTransfer(LivingEntity defender, DamageSource source, float amount) {
        // Fishball-equipped entities are tanks: they absorb 30% of nearby same-type allies' damage.
        if (getEnchantmentLevel(defender, ModEnchantments.FISHBALL) > 0) return;
        List<LivingEntity> nearbyWearer = getNearbySameTypeWithFishball(defender, 10.0);
        if (nearbyWearer.isEmpty()) return;

        float transferDamage = amount * 0.3f;
        nearbyWearer.get(0).hurt(source, transferDamage);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Fishball] Transferred {} damage from {} to fishball wearer {}",
                    transferDamage, defender.getName().getString(),
                    nearbyWearer.get(0).getName().getString());
        }
    }

    private static void applyToughnessShield(LivingEntity defender) {
        if (getEnchantmentLevel(defender, ModEnchantments.TOUGHNESS) <= 0) return;
        if (!defender.isUsingItem() || !defender.getUseItem().is(Items.SHIELD)) return;

        ItemStack shield = defender.getUseItem();
        if (shield.getDamageValue() < shield.getMaxDamage() - 1) return;
        CompoundTag data = getEntityData(defender);
        if (data.getBoolean("zhonz_toughness_triggered")) return;

        data.putBoolean("zhonz_toughness_triggered", true);
        addTransient(defender, Attributes.ARMOR, TOUGHNESS_ARMOR_MODIFIER, 20.0, AttributeModifier.Operation.ADD_VALUE);
        addTransient(defender, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_TOUGHNESS_MODIFIER, 20.0, AttributeModifier.Operation.ADD_VALUE);

        // Remove after 10 seconds
        if (defender.level() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().tell(new net.minecraft.server.TickTask(200, () -> {
                removeModifier(defender, Attributes.ARMOR, TOUGHNESS_ARMOR_MODIFIER);
                removeModifier(defender, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_TOUGHNESS_MODIFIER);
                getEntityData(defender).remove("zhonz_toughness_triggered");
            }));
        }
    }

    private static void applyEmergencyRescue(LivingEntity defender, float amount) {
        if (getEnchantmentLevel(defender, ModEnchantments.EMERGENCY_RESCUE) <= 0) return;
        CompoundTag data = getEntityData(defender);
        if (data.getInt(KEY_EMERGENCY_RESCUE_CD) > 0) return;
        float hpAfterDamage = defender.getHealth() - amount;
        if (hpAfterDamage > 5.0f || hpAfterDamage <= 0) return;

        defender.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 2));
        for (LivingEntity nearby : getNearbySameType(defender, 10.0)) {
            nearby.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 2));
        }
        data.putInt(KEY_EMERGENCY_RESCUE_CD, 200);
    }

    /** 已迁入 incoming_damage 通道(mySeaDomainVulnerabilityFactor), 保留文档对照。 */
    private static float applyMySeaDomainVulnerability(LivingEntity defender, float amount) {
        return amount;
    }

    /** 已迁入 incoming_damage 通道(prophetsCallFactor), 保留文档对照。 */
    private static float applyProphetsCallBonus(LivingEntity defender, float amount) {
        return amount;
    }

    // ===================================================================
    // 新设计附魔: 38 终点倒计时 / 39 将我抹去 / 40 目不能追 / 41 节奏
    //            42 孤独的正午 / 43 燃烧的黄昏 / 44 哭泣之子
    // ===================================================================

    // --- 38. 终点倒计时: 命中施加倒计时 buff,期间记录目标所受伤害,结束时造成记录值10%伤害 ---
    private static float applyFinalCountdown(LivingEntity attacker, LivingEntity defender, float amount) {
        if (getMainHandEnchantmentLevel(attacker, ModEnchantments.FINAL_COUNTDOWN) <= 0) return amount;
        CompoundTag d = getEntityData(defender);
        if (d.contains(KEY_COUNTDOWN_UNTIL)) return amount; // 已有倒计时,不重复施加

        long until = defender.level().getGameTime() + FINAL_COUNTDOWN_TICKS;
        d.putLong(KEY_COUNTDOWN_UNTIL, until);
        d.putFloat(KEY_COUNTDOWN_DAMAGE, 0);

        // 倒计时结束时结算
        if (defender.level() instanceof ServerLevel serverLevel) {
            LivingEntity target = defender;
            serverLevel.getServer().tell(new net.minecraft.server.TickTask(FINAL_COUNTDOWN_TICKS, () -> {
                CompoundTag td = getEntityData(target);
                if (!td.contains(KEY_COUNTDOWN_UNTIL)) return;
                if (target.isAlive()) {
                    float recorded = td.getFloat(KEY_COUNTDOWN_DAMAGE);
                    if (recorded > 0) {
                        target.hurt(target.damageSources().magic(), recorded * 0.1f);
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("[FinalCountdown] Dealt {} (10% of recorded {})", recorded * 0.1f, recorded);
                        }
                    }
                }
                td.remove(KEY_COUNTDOWN_UNTIL);
                td.remove(KEY_COUNTDOWN_DAMAGE);
            }));
        }
        return amount;
    }

    private static float accumulateFinalCountdown(LivingEntity defender, float amount) {
        CompoundTag d = getEntityData(defender);
        if (!d.contains(KEY_COUNTDOWN_UNTIL)) return amount;
        if (defender.level().getGameTime() >= d.getLong(KEY_COUNTDOWN_UNTIL)) {
            d.remove(KEY_COUNTDOWN_UNTIL);
            d.remove(KEY_COUNTDOWN_DAMAGE);
            return amount;
        }
        d.putFloat(KEY_COUNTDOWN_DAMAGE, d.getFloat(KEY_COUNTDOWN_DAMAGE) + amount);
        return amount;
    }

    // --- 39. 将我抹去，将你也抹去: 直接杀死目标及其同类(无法正常获取) ---
    private static void applyEraseMe(LivingEntity attacker, LivingEntity defender, DamageSource source) {
        if (getMainHandEnchantmentLevel(attacker, ModEnchantments.ERASE_ME_ERASE_YOU) <= 0) return;
        EntityType<?> type = defender.getType();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[EraseMe] target={} type={} pos={}", defender.getName().getString(), type, defender.blockPosition());
        }
        defender.kill();
        var box = defender.getBoundingBox().inflate(10.0);
        var entities = defender.level().getEntitiesOfClass(LivingEntity.class, box);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[EraseMe] found {} living entities in range, searching for type {}", entities.size(), type);
        }
        for (LivingEntity nearby : entities) {
            boolean match = nearby != defender && nearby.isAlive() && nearby.getType() == type;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[EraseMe]   entity={} type={} alive={} match={}", nearby.getName().getString(), nearby.getType(), nearby.isAlive(), match);
            }
            if (match) {
                nearby.kill();
            }
        }
    }

    // --- 40. 目不能追，耳未可即: 记录本次伤害,下次攻击额外造成其10%(flat 绝对加伤 → flat_damage 通道) ---
    private static float applyFleetFootstepsDamage(LivingEntity attacker, float amount) {
        if (getEnchantmentLevel(attacker, ModEnchantments.FLEET_FOOTSTEPS) <= 0) return amount;
        CompoundTag data = getEntityData(attacker);
        float last = data.getFloat(KEY_FLEET_LAST_ATTACK);
        float bonus = last * 0.10f;
        // 副作用保留: 记录"本次伤害+本次加成"作为下次基准(与旧链语义一致)
        data.putFloat(KEY_FLEET_LAST_ATTACK, amount + bonus);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[FleetFootsteps] last={} bonus={} amount={}", last, bonus, amount);
        }
        setFlatDamage(attacker, EVENT_FLAT_FLEET, bonus);
        return amount;
    }

    private static void tickFleetFootsteps(Player player, CompoundTag data) {
        int level = getSlotEnchantmentLevel(player, ModEnchantments.FLEET_FOOTSTEPS, EquipmentSlot.FEET);
        int last = data.getInt(KEY_FLEET_FOOTSTEPS_LAST_LEVEL);
        if (level == last) return;
        data.putInt(KEY_FLEET_FOOTSTEPS_LAST_LEVEL, level);
        setTransient(player, Attributes.MOVEMENT_SPEED, FLEET_FOOTSTEPS_SPEED_MODIFIER,
                level > 0 ? 0.5 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        setTransient(player, Attributes.STEP_HEIGHT, FLEET_FOOTSTEPS_STEP_MODIFIER,
                level > 0 ? 0.5 : 0, AttributeModifier.Operation.ADD_VALUE);
    }

    // --- 41. 节奏: 攻击间隔与记录值相差 ±0.2 秒(4 tick)内则伤害 +50% ---
    // 拆解方案(round13): 计时副作用(读/写 last/recorded)保留在此链上函数;
    // 命中判定在此完成并写 KEY_RHYTHM_HIT_ATTACK 标志, ×1.5 乘伤由
    // computeEventConditionalMultiplier(乘伤事件通道)在结算前读取标志并应用。
    private static float applyRhythm(LivingEntity attacker, float amount) {
        if (getMainHandEnchantmentLevel(attacker, ModEnchantments.RHYTHM) <= 0) return amount;
        CompoundTag data = getEntityData(attacker);
        long now = attacker.level().getGameTime();
        long last = data.getLong(KEY_RHYTHM_LAST_ATTACK);
        if (last != 0) {
            long interval = now - last;
            int recorded = data.getInt(KEY_RHYTHM_RECORDED_INTERVAL);
            if (recorded == 0) {
                data.putInt(KEY_RHYTHM_RECORDED_INTERVAL, (int) interval);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Rhythm] recorded interval={} ticks", interval);
                }
            } else if (Math.abs(interval - recorded) <= 4) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Rhythm] interval={} matches recorded={} -> +50%", interval, recorded);
                }
                data.putBoolean(KEY_RHYTHM_HIT_ATTACK, true);
            } else if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Rhythm] interval={} vs recorded={} (miss)", interval, recorded);
            }
        }
        data.putLong(KEY_RHYTHM_LAST_ATTACK, now);
        return amount;
    }

    // --- 42. 孤独的正午: 对着火目标伤害 +50%/×2 已迁入 computeEventConditionalMultiplier(乘伤事件通道) ---

    // --- 43. 燃烧的黄昏: 对燃烧目标叠加 +10% 火焰易伤(可叠加无上限,配孤独的正午 ×2) ---
    private static void applyBurningDusk(LivingEntity attacker, LivingEntity defender) {
        if (getMainHandEnchantmentLevel(attacker, ModEnchantments.BURNING_DUSK) <= 0) return;
        if (!defender.isOnFire()) return;
        boolean noon = getMainHandEnchantmentLevel(attacker, ModEnchantments.LONELY_NOON) > 0;
        CompoundTag d = getEntityData(defender);
        float pct = d.getFloat(KEY_BURNING_DUSK_PCT);
        float add = noon ? 0.20f : 0.10f;
        d.putFloat(KEY_BURNING_DUSK_PCT, pct + add);
        d.putLong(KEY_BURNING_DUSK_UNTIL, defender.level().getGameTime() + 200); // 10 秒未刷新则清除
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[BurningDusk] target burning, +{}% -> total {}%", add * 100, (pct + add) * 100);
        }
    }

        private static void applyBurningDuskVulnerability(LivingEntity defender, LivingIncomingDamageEvent event) {
        // 已迁 burningDuskFactor(incoming_damage 通道); 保留文档对照
    }

    // --- 44. 比任何人都要悲伤的哭泣之子: 火焰免疫 / 点燃双方 / 自身燃烧增伤 ---
    // 统一火焰免疫: 免疫与穿透共存, 穿透优先 —— 哭泣之火的"哭泣分支"无视对方一切火免
    private static boolean isPiercingWeepingFire(DamageSource source) {
        return source.is(WEEPING_FIRE)
                && source.getEntity() instanceof LivingEntity le
                && getMainHandEnchantmentLevel(le, ModEnchantments.WEEPING_CHILD) > 0;
    }

    private static void applyWeepingChildImmunity(LivingEntity defender, LivingIncomingDamageEvent event) {
        if (getMainHandEnchantmentLevel(defender, ModEnchantments.WEEPING_CHILD) <= 0) return;
        if (isPiercingWeepingFire(event.getSource())) return; // 穿透优先: 不挡哭泣之火
        // 免疫所有原版火焰(IS_FIRE, 含哭泣之火的一般 is_fire 属性)
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
            event.setCanceled(true);
        }
    }

    /** 44. 哭泣之子: 点燃双方 3 秒(乘伤 ×3/联动 ×2 已迁 computeEventConditionalMultiplier)。 */
    private static void applyWeepingChildIgnite(LivingEntity attacker, LivingEntity defender) {
        if (getMainHandEnchantmentLevel(attacker, ModEnchantments.WEEPING_CHILD) <= 0) return;
        // 点燃双方 3 秒
        defender.setRemainingFireTicks(Math.max(defender.getRemainingFireTicks(), 60));
        attacker.setRemainingFireTicks(Math.max(attacker.getRemainingFireTicks(), 60));
    }

    // ===================================================================
    // 新附魔(45-53): 顶点/困兽之斗/剧烈搏动/耐心/不停狩/新太阳/自缚者/极速攀升/加速的未来
    // ===================================================================

    private static boolean isHoldingApex(LivingEntity entity) {
        return getMainHandEnchantmentLevel(entity, ModEnchantments.APEX) > 0
                || getSlotEnchantmentLevel(entity, ModEnchantments.APEX, EquipmentSlot.OFFHAND) > 0;
    }

    // --- 45. "顶点": 主/副手 闪避+20%, 伤害+500%, 受伤-60% (无法正常获取) ---
    private static void tickApex(Player player, CompoundTag data) {
        int level = isHoldingApex(player) ? 1 : 0;
        int last = data.getInt("zhonz_apex_last");
        if (level == last) return;
        data.putInt("zhonz_apex_last", level);
        setTransient(player, ALObjects.Attributes.DODGE_CHANCE, APEX_DODGE_MODIFIER,
                level > 0 ? 0.20 : 0, AttributeModifier.Operation.ADD_VALUE);
        setTransient(player, Attributes.ATTACK_DAMAGE, APEX_DAMAGE_MODIFIER,
                level > 0 ? 5.0 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

        private static void applyApexIncoming(LivingEntity defender, LivingIncomingDamageEvent event) {
        // 已迁 refreshIncomingAggregate(×0.4); 保留文档对照
    }

    // --- 46. 困兽之斗: 头盔, 生命<25% 受伤-50% 伤害+60% 治疗+50% ---
    private static void tickCorneredBeast(Player player, CompoundTag data) {
        boolean active = getSlotEnchantmentLevel(player, ModEnchantments.CORNERED_BEAST, EquipmentSlot.HEAD) > 0
                && player.getHealth() <= player.getMaxHealth() * 0.25f;
        // 伤害+60% 迁入 bonus_damage(加伤通道), 每次 tick 刷新以响应血量变化(percent 计算下沉 common)
        addPercentBonus(player, CORNERED_BEAST_BONUS_MODIFIER,
                com.zhonz.moreenchantments.common.damage.TickBonusRules.corneredBeast(EVENT_COND_CTX.levels, player));
        int level = active ? 1 : 0;
        int last = data.getInt("zhonz_cornered_last");
        if (level == last) return;
        data.putInt("zhonz_cornered_last", level);
        setTransient(player, ALObjects.Attributes.HEALING_RECEIVED, CORNERED_HEALING_MODIFIER,
                level > 0 ? 0.50 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

        private static void applyCorneredBeastIncoming(LivingEntity defender, LivingIncomingDamageEvent event) {
        // 已迁 refreshIncomingAggregate(×0.5); 保留文档对照
    }

    private static float applyCorneredBeastDamage(LivingEntity attacker, float amount) {
        // 伤害+60% 已迁移到 bonus_damage 通道(tickCorneredBeast), 链上不再乘算
        return amount;
    }

    // --- 47. 剧烈搏动: 胸甲, 生命<50% 攻速+50% 移速+30% 攻击回复1生命 ---
    private static void tickViolentPulse(Player player, CompoundTag data) {
        boolean active = getSlotEnchantmentLevel(player, ModEnchantments.VIOLENT_PULSE, EquipmentSlot.CHEST) > 0
                && player.getHealth() <= player.getMaxHealth() * 0.5f;
        int level = active ? 1 : 0;
        int last = data.getInt("zhonz_violent_last");
        if (level == last) return;
        data.putInt("zhonz_violent_last", level);
        setTransient(player, Attributes.ATTACK_SPEED, VIOLENT_ATTACK_SPEED_MODIFIER,
                level > 0 ? 0.50 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        setTransient(player, Attributes.MOVEMENT_SPEED, VIOLENT_MOVEMENT_MODIFIER,
                level > 0 ? 0.30 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    private static float applyViolentPulseAttack(LivingEntity attacker, float amount) {
        if (getSlotEnchantmentLevel(attacker, ModEnchantments.VIOLENT_PULSE, EquipmentSlot.CHEST) <= 0) return amount;
        if (attacker.getHealth() <= attacker.getMaxHealth() * 0.5f && attacker.isAlive()) {
            attacker.heal(1.0f);
        }
        return amount;
    }

    // --- 48. 耐心: 盾牌, 举盾>5秒后下一次抵挡伤害不消耗耐久 ---
    private static void tickPatience(Player player, CompoundTag data, int tickCount) {
        boolean holding = player.isUsingItem() && player.getUseItem().is(Items.SHIELD);
        if (getSlotEnchantmentLevel(player, ModEnchantments.PATIENCE, EquipmentSlot.OFFHAND) <= 0) {
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

    // --- 49. 不停狩: 武器, 5秒内连续战斗叠层(受伤-10%/层, 伤害+5%/层, 上限5层) ---
    private static int updateCeaselessHunt(CompoundTag data, long now) {
        long last = data.getLong(KEY_CEASELESS_LAST_COMBAT);
        int stacks = data.getInt(KEY_CEASELESS_STACKS);
        if (last != 0 && now - last <= 100) {
            stacks = Math.min(5, stacks + 1);
        } else {
            stacks = 1;
        }
        data.putLong(KEY_CEASELESS_LAST_COMBAT, now);
        data.putInt(KEY_CEASELESS_STACKS, stacks);
        return stacks;
    }

    /** 49. 不停狩: 叠层副作用(5秒内连续战斗+1层, 上限5)。伤害 +5%/层 已迁 computeEventBonusPercent。 */
    private static void applyCeaselessHuntStack(LivingEntity attacker) {
        if (getMainHandEnchantmentLevel(attacker, ModEnchantments.CEASELESS_HUNT) > 0) {
            updateCeaselessHunt(EntityDataStorage.getData(attacker), attacker.level().getGameTime());
        }
    }

        private static void applyCeaselessHuntIncoming(LivingEntity defender, LivingIncomingDamageEvent event) {
        // 已迁 ceaselessHuntFactor(incoming_damage 通道); 保留文档对照
    }

    // --- 50. "新太阳": 护腿, 光照越高伤害越高(15级+150%), 攻击点燃目标 ---
    private static float applyNewSun(LivingEntity attacker, LivingEntity defender, float amount) {
        if (getSlotEnchantmentLevel(attacker, ModEnchantments.NEW_SUN, EquipmentSlot.LEGS) <= 0) return amount;
        defender.setRemainingFireTicks(Math.max(defender.getRemainingFireTicks(), 60)); // 点燃 3 秒
        // 加伤部分已迁移到 bonus_damage 属性通道(见 tickNewSunBonus), 此处仅保留点燃副作用
        return amount;
    }

    /** 新太阳: 每 tick 按所处光照把 +150%×(光/15) 写入 bonus_damage(加伤通道, percent 计算下沉 common)。 */
    private static void tickNewSunBonus(Player player) {
        addPercentBonus(player, NEW_SUN_BONUS_MODIFIER,
                com.zhonz.moreenchantments.common.damage.TickBonusRules.newSun(EVENT_COND_CTX.levels, player));
    }

    // --- 51. 自缚者: 护腿(诅咒), 台阶-1 伤害-90% 移速-50% ---
    private static void tickSelfBound(Player player, CompoundTag data) {
        int level = getSlotEnchantmentLevel(player, ModEnchantments.SELF_BOUND, EquipmentSlot.LEGS);
        int last = data.getInt("zhonz_self_bound_last");
        if (level == last) return;
        data.putInt("zhonz_self_bound_last", level);
        setTransient(player, Attributes.STEP_HEIGHT, SELF_BOUND_STEP_MODIFIER,
                level > 0 ? -1 : 0, AttributeModifier.Operation.ADD_VALUE);
        setTransient(player, Attributes.ATTACK_DAMAGE, SELF_BOUND_DAMAGE_MODIFIER,
                level > 0 ? -0.9 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        setTransient(player, Attributes.MOVEMENT_SPEED, SELF_BOUND_MOVEMENT_MODIFIER,
                level > 0 ? -0.5 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    // --- 52. 极速攀升: 靴子, 台阶+2, y越高增伤越高, y越低减伤越高 ---
    private static void tickRapidAscent(Player player, CompoundTag data) {
        int level = getSlotEnchantmentLevel(player, ModEnchantments.RAPID_ASCENT, EquipmentSlot.FEET);
        int last = data.getInt("zhonz_rapid_ascent_last");
        if (level == last) return;
        data.putInt("zhonz_rapid_ascent_last", level);
        setTransient(player, Attributes.STEP_HEIGHT, RAPID_ASCENT_STEP_MODIFIER,
                level > 0 ? 2 : 0, AttributeModifier.Operation.ADD_VALUE);
    }

    private static float applyRapidAscentDamage(LivingEntity attacker, float amount) {
        if (getSlotEnchantmentLevel(attacker, ModEnchantments.RAPID_ASCENT, EquipmentSlot.FEET) <= 0) return amount;
        // 加伤部分已迁移到 bonus_damage 属性通道(tickRapidAscentBonus), 此处不再乘算
        return amount;
    }

    /** 极速攀升: 攻击侧增伤 = y/100(y=20 → +20%), 写入 bonus_damage(percent 计算下沉 common)。受伤减半在事件内。 */
    private static void tickRapidAscentBonus(Player player) {
        addPercentBonus(player, RAPID_ASCENT_BONUS_MODIFIER,
                com.zhonz.moreenchantments.common.damage.TickBonusRules.rapidAscent(EVENT_COND_CTX.levels, player));
    }

        private static void applyRapidAscentIncoming(LivingEntity defender, LivingIncomingDamageEvent event) {
        // 已迁 refreshIncomingAggregate(y<0 减伤); 保留文档对照
    }

    // --- 53. 加速的未来: 头盔, 增伤=闪避率×2, 攻速=闪避率 ---
    private static void tickAcceleratedFuture(Player player, CompoundTag data) {
        int level = getSlotEnchantmentLevel(player, ModEnchantments.ACCELERATED_FUTURE, EquipmentSlot.HEAD);
        double dodge = player.getAttributeValue(ALObjects.Attributes.DODGE_CHANCE);
        if (level <= 0) {
            if (data.getFloat(KEY_ACCELERATED_LAST_DODGE) > 0) {
                setTransient(player, Attributes.ATTACK_DAMAGE, ACCELERATED_DAMAGE_MODIFIER, 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
                setTransient(player, Attributes.ATTACK_SPEED, ACCELERATED_ATTACK_SPEED_MODIFIER, 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                data.putFloat(KEY_ACCELERATED_LAST_DODGE, 0);
            }
            return;
        }
        float lastDodge = data.getFloat(KEY_ACCELERATED_LAST_DODGE);
        if (Math.abs(lastDodge - dodge) < 0.001f) return;
        data.putFloat(KEY_ACCELERATED_LAST_DODGE, (float) dodge);
        setTransient(player, Attributes.ATTACK_DAMAGE, ACCELERATED_DAMAGE_MODIFIER,
                dodge * 2.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        setTransient(player, Attributes.ATTACK_SPEED, ACCELERATED_ATTACK_SPEED_MODIFIER,
                dodge, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    // ===================================================================
    // 新附魔(54-72): 止步/于此显圣/惨白的午夜/悲伤的红/苦难原色/永劫回归/
    //                奢侈的希望/嗜光/畏光/礼仪/敷衍/沉默沉入沉渊/狂热撕咬光芒/
    //                噤声击坠天堂/?!合合!?/农场主的监督/花圃/光环/金酒之杯
    // ===================================================================

    // --- 54. 止步: 攻击使目标无法移动0.2秒; 对不移动目标伤害+40% ---
    /** 54. 止步: 减速副作用(0.2s)。对不动目标伤害 +40% 已迁 computeEventBonusPercent。 */
    private static void applyHaltSlow(LivingEntity attacker, LivingEntity defender) {
        if (getMainHandEnchantmentLevel(attacker, ModEnchantments.HALT) <= 0) return;
        defender.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 10, false, false));
    }

    // --- 56. 惨白的午夜: 头盔, 夜视/低光隐身/伤害+50%/命中敌人发光+易伤30% ---
    private static void tickPaleMidnight(Player player, int tickCount) {
        if (getSlotEnchantmentLevel(player, ModEnchantments.PALE_MIDNIGHT, EquipmentSlot.HEAD) <= 0) return;
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 220, 0, false, false));
        int light = player.level().getMaxLocalRawBrightness(player.blockPosition());
        if (light < 7) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20, 0, false, false));
        }
    }

    /** 56. 惨白的午夜: 发光+易伤标记副作用。攻击伤害 +50% 已迁 computeEventBonusPercent。 */
    private static void applyPaleMidnightMark(LivingEntity attacker, LivingEntity defender) {
        if (getSlotEnchantmentLevel(attacker, ModEnchantments.PALE_MIDNIGHT, EquipmentSlot.HEAD) <= 0) return;
        // 被攻击的敌人: 30 秒发光 + 易伤 30%
        defender.addEffect(new MobEffectInstance(MobEffects.GLOWING, 600, 0, false, false));
        getEntityData(defender).putLong(KEY_PALE_VULN_UNTIL, defender.level().getGameTime() + 600);
    }

        private static void applyPaleMidnightVulnerability(LivingEntity defender, LivingIncomingDamageEvent event) {
        // 已迁 paleVulnerabilityFactor(incoming_damage 通道); 保留文档对照
    }

    // --- 57. "悲伤的红": 胸甲, 背包每有一格有物品增伤10% ---
    // 已迁移: 增伤百分比累加到 bonus_damage 属性(见 tickSorrowfulRedBonus), 不再在伤害链手写乘法。
    @Deprecated
    private static float applySorrowfulRed(LivingEntity attacker, float amount) {
        return amount;
    }

    /** 每 tick 把悲伤的红"背包每格+10%"写入 bonus_damage(percent 计算下沉 common)。 */
    private static void tickSorrowfulRedBonus(Player player) {
        addPercentBonus(player, SORROWFUL_RED_BONUS_MODIFIER,
                com.zhonz.moreenchantments.common.damage.TickBonusRules.sorrowfulRed(EVENT_COND_CTX.levels, player));
    }

    // --- 58. 苦难原色: 盔甲, 速度类属性低于默认则补回差额×2 ---
    private static void tickPrimalSuffering(Player player, CompoundTag data) {
        int level = getEnchantmentLevel(player, ModEnchantments.PRIMAL_SUFFERING);
        int last = data.getInt("zhonz_primal_last");
        if (level == last) return;
        data.putInt("zhonz_primal_last", level);
        if (level <= 0) {
            removeModifier(player, Attributes.ATTACK_SPEED, PRIMAL_ATTACK_SPEED_MODIFIER);
            removeModifier(player, Attributes.MOVEMENT_SPEED, PRIMAL_MOVE_MODIFIER);
            removeModifier(player, ALObjects.Attributes.MINING_SPEED, PRIMAL_MINING_MODIFIER);
            removeModifier(player, ALObjects.Attributes.DRAW_SPEED, PRIMAL_DRAW_MODIFIER);
            return;
        }
        applyPrimal(player, Attributes.ATTACK_SPEED, PRIMAL_ATTACK_SPEED_MODIFIER);
        applyPrimal(player, Attributes.MOVEMENT_SPEED, PRIMAL_MOVE_MODIFIER);
        applyPrimal(player, ALObjects.Attributes.MINING_SPEED, PRIMAL_MINING_MODIFIER);
        applyPrimal(player, ALObjects.Attributes.DRAW_SPEED, PRIMAL_DRAW_MODIFIER);
    }

    private static void applyPrimal(Player player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, ResourceLocation id) {
        var instance = player.getAttribute(attr);
        if (instance == null) return;
        double base = instance.getBaseValue();
        double current = instance.getValue();
        double deficit = base - current;
        if (deficit > 0.01) {
            setTransient(player, attr, id, deficit * 2.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        } else {
            removeModifier(player, attr, id);
        }
    }

    // --- 60. "奢侈的希望": 盔甲, 满血受伤+50%; 低于满血 50%穿甲+20%幸运 ---
    private static void tickLuxuriousHope(Player player, CompoundTag data) {
        boolean active = getEnchantmentLevel(player, ModEnchantments.LUXURIOUS_HOPE) > 0;
        int last = data.getInt("zhonz_luxurious_last");
        int now = active ? 1 : 0;
        if (now == last) return;
        data.putInt("zhonz_luxurious_last", now);
        if (now == 0) {
            removeModifier(player, ALObjects.Attributes.ARMOR_PIERCE, LUXURIOUS_PIERCE_MODIFIER);
            removeModifier(player, Attributes.LUCK, LUXURIOUS_LUCK_MODIFIER);
            return;
        }
        setTransient(player, ALObjects.Attributes.ARMOR_PIERCE, LUXURIOUS_PIERCE_MODIFIER,
                0.5, AttributeModifier.Operation.ADD_VALUE);
        setTransient(player, Attributes.LUCK, LUXURIOUS_LUCK_MODIFIER,
                0.2, AttributeModifier.Operation.ADD_VALUE);
    }

        private static void applyLuxuriousHopeIncoming(LivingEntity defender, LivingIncomingDamageEvent event) {
        // 已迁 refreshIncomingAggregate(×1.5); 保留文档对照
    }

    // --- 61. 嗜光: 头盔, 光照>0 持续回饱食度 ---
    private static void tickPhotophile(Player player, int tickCount) {
        if (getSlotEnchantmentLevel(player, ModEnchantments.PHOTOPHILE, EquipmentSlot.HEAD) <= 0) return;
        if (player.level().getMaxLocalRawBrightness(player.blockPosition()) > 0 && tickCount % 40 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 40, 0, false, false));
        }
    }

    // --- 62. 畏光: 头盔, 光照>0 移速-20%; 光照=0 移速+50%攻速+30% ---
    private static void tickPhotophobe(Player player, CompoundTag data) {
        int level = getSlotEnchantmentLevel(player, ModEnchantments.PHOTOPHOBE, EquipmentSlot.HEAD);
        int light = player.level().getMaxLocalRawBrightness(player.blockPosition());
        int mode = level > 0 ? (light > 0 ? 1 : 2) : 0;
        int last = data.getInt("zhonz_photophobe_last");
        if (mode == last) return;
        data.putInt("zhonz_photophobe_last", mode);
        if (mode == 1) {
            setTransient(player, Attributes.MOVEMENT_SPEED, PHOTOPHOBE_MOVE_MODIFIER, -0.2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            removeModifier(player, Attributes.ATTACK_SPEED, PHOTOPHOBE_ATTACK_SPEED_MODIFIER);
        } else if (mode == 2) {
            setTransient(player, Attributes.MOVEMENT_SPEED, PHOTOPHOBE_MOVE_MODIFIER, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            setTransient(player, Attributes.ATTACK_SPEED, PHOTOPHOBE_ATTACK_SPEED_MODIFIER, 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        } else {
            removeModifier(player, Attributes.MOVEMENT_SPEED, PHOTOPHOBE_MOVE_MODIFIER);
            removeModifier(player, Attributes.ATTACK_SPEED, PHOTOPHOBE_ATTACK_SPEED_MODIFIER);
        }
    }

    // --- 63. 礼仪: 盔甲, 每件+5%闪避+20%攻速 ---
    private static void tickEtiquette(Player player, CompoundTag data) {
        int count = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (getSlotEnchantmentLevel(player, ModEnchantments.ETIQUETTE, slot) > 0) count++;
        }
        int last = data.getInt("zhonz_etiquette_last");
        if (count == last) return;
        data.putInt("zhonz_etiquette_last", count);
        setTransient(player, ALObjects.Attributes.DODGE_CHANCE, ETIQUETTE_DODGE_MODIFIER,
                count * 0.05, AttributeModifier.Operation.ADD_VALUE);
        setTransient(player, Attributes.ATTACK_SPEED, ETIQUETTE_ATTACK_SPEED_MODIFIER,
                count * 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    // --- 64. 敷衍: 诅咒, 背包每有一件带此诅咒, 移速/攻速/挖掘/蓄力-20% ---
    private static void tickPerfunctory(Player player, CompoundTag data) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.PERFUNCTORY)) > 0) {
                count++;
            }
        }
        int last = data.getInt("zhonz_perfunctory_last");
        if (count == last) return;
        data.putInt("zhonz_perfunctory_last", count);
        double mult = -0.2 * count;
        setTransient(player, Attributes.MOVEMENT_SPEED, PERFUNCTORY_MOVE_MODIFIER, mult, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        setTransient(player, Attributes.ATTACK_SPEED, PERFUNCTORY_ATTACK_SPEED_MODIFIER, mult, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        setTransient(player, ALObjects.Attributes.MINING_SPEED, PERFUNCTORY_MINING_MODIFIER, mult, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        setTransient(player, ALObjects.Attributes.DRAW_SPEED, PERFUNCTORY_DRAW_MODIFIER, mult, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    // --- 65-67. 暴击三件套: 沉默沉入沉渊/狂热撕咬光芒/噤声击坠天堂 ---
    private static void tickCritWeapons(Player player, CompoundTag data) {
        ItemStack weapon = player.getMainHandItem();
        boolean silence = weapon.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.SILENCE_IN_DEPTHS)) > 0;
        boolean frenzied = weapon.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.FRENZIED_BITE)) > 0;
        boolean heavenfall = weapon.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.SILENCED_HEAVENFALL)) > 0;
        int comboCount = (frenzied ? 1 : 0) + (heavenfall ? 1 : 0);

        // 沉默沉入沉渊: 暴击率 = 最大生命×2% (有组合时×4%)
        double maxHp = player.getAttributeValue(Attributes.MAX_HEALTH);
        double critChance = silence ? maxHp * 0.02 * (comboCount > 0 ? 2 : 1) : 0;
        setTransient(player, ALObjects.Attributes.CRIT_CHANCE, SILENCE_CRIT_CHANCE_MODIFIER,
                critChance, AttributeModifier.Operation.ADD_VALUE);

        // 狂热撕咬光芒: 暴击伤害 = 攻速 (组合时×2)
        double attackSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        double critDamage1 = frenzied ? attackSpeed * (comboCount > 0 ? 2 : 1) : 0;
        setTransient(player, ALObjects.Attributes.CRIT_DAMAGE, FRENZIED_CRIT_DAMAGE_MODIFIER,
                critDamage1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

        // 噤声击坠天堂: 暴击伤害+增伤 = 暴击率 (组合时×2) —— 增伤走事件, 暴击伤害走属性
        double critChanceValue = player.getAttributeValue(ALObjects.Attributes.CRIT_CHANCE);
        double critDamage2 = heavenfall ? critChanceValue * (comboCount > 0 ? 2 : 1) : 0;
        setTransient(player, ALObjects.Attributes.CRIT_DAMAGE, HEAVENFALL_CRIT_DAMAGE_MODIFIER,
                critDamage2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        setTransient(player, Attributes.ATTACK_DAMAGE, HEAVENFALL_DAMAGE_MODIFIER,
                heavenfall ? critChanceValue * (comboCount > 0 ? 2 : 1) : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    // --- 68. "?!合合!?": 挖掘工具, 主手自动合成背包矿物块 ---
    private static void tickAutoMerge(Player player, int tickCount) {
        if (tickCount % 10 != 0) return;
        ItemStack hand = player.getMainHandItem();
        if (hand.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.AUTO_MERGE)) <= 0) return;
        java.util.Map<Item, Item> recipes = getMergeRecipes();
        for (java.util.Map.Entry<Item, Item> e : recipes.entrySet()) {
            Item source = e.getKey();
            Item result = e.getValue();
            int count = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty() && stack.is(source)) count += stack.getCount();
            }
            if (count >= 9) {
                int toRemove = 9;
                for (int i = 0; i < player.getInventory().getContainerSize() && toRemove > 0; i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (!stack.isEmpty() && stack.is(source)) {
                        int take = Math.min(stack.getCount(), toRemove);
                        stack.shrink(take);
                        toRemove -= take;
                    }
                }
                player.getInventory().add(new ItemStack(result, 1));
                if (LOGGER.isDebugEnabled()) LOGGER.debug("[AutoMerge] merged 9x{} -> {}", source, result);
                break;
            }
        }
    }

    private static java.util.Map<Item, Item> getMergeRecipes() {
        java.util.Map<Item, Item> m = new java.util.HashMap<>();
        m.put(Items.RAW_IRON, Items.RAW_IRON_BLOCK);
        m.put(Items.RAW_GOLD, Items.RAW_GOLD_BLOCK);
        m.put(Items.RAW_COPPER, Items.RAW_COPPER_BLOCK);
        m.put(Items.COAL, Items.COAL_BLOCK);
        m.put(Items.IRON_INGOT, Items.IRON_BLOCK);
        m.put(Items.GOLD_INGOT, Items.GOLD_BLOCK);
        m.put(Items.COPPER_INGOT, Items.COPPER_BLOCK);
        m.put(Items.NETHERITE_INGOT, Items.NETHERITE_BLOCK);
        m.put(Items.DIAMOND, Items.DIAMOND_BLOCK);
        m.put(Items.EMERALD, Items.EMERALD_BLOCK);
        m.put(Items.LAPIS_LAZULI, Items.LAPIS_BLOCK);
        m.put(Items.REDSTONE, Items.REDSTONE_BLOCK);
        m.put(Items.QUARTZ, Items.QUARTZ_BLOCK);
        m.put(Items.AMETHYST_SHARD, Items.AMETHYST_BLOCK);
        m.put(Items.SNOWBALL, Items.SNOW_BLOCK);
        m.put(Items.BONE, Items.BONE_BLOCK);
        m.put(Items.SLIME_BALL, Items.SLIME_BLOCK);
        return m;
    }

    // --- 69. 农场主的监督: 锄头破坏农作物自动补种 ---
    @SubscribeEvent
    public static void onBlockBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        Player player = event.getPlayer();
        if (player == null) return;
        ItemStack hoe = player.getMainHandItem();
        if (hoe.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.FARMER_OVERSEER)) <= 0) return;

        net.minecraft.world.level.block.state.BlockState state = event.getState();
        net.minecraft.world.level.block.Block block = state.getBlock();
        net.minecraft.world.level.block.Block crop = null;
        Item seed = null;
        if (block == net.minecraft.world.level.block.Blocks.WHEAT) { crop = net.minecraft.world.level.block.Blocks.WHEAT; seed = Items.WHEAT_SEEDS; }
        else if (block == net.minecraft.world.level.block.Blocks.CARROTS) { crop = net.minecraft.world.level.block.Blocks.CARROTS; seed = Items.CARROT; }
        else if (block == net.minecraft.world.level.block.Blocks.POTATOES) { crop = net.minecraft.world.level.block.Blocks.POTATOES; seed = Items.POTATO; }
        else if (block == net.minecraft.world.level.block.Blocks.BEETROOTS) { crop = net.minecraft.world.level.block.Blocks.BEETROOTS; seed = Items.BEETROOT_SEEDS; }
        if (crop == null || seed == null) return;

        // 消耗一个种子
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(seed)) {
                stack.shrink(1);
                // 原地补种
                if (event.getLevel() instanceof ServerLevel sl) {
                    sl.setBlockAndUpdate(event.getPos(), crop.defaultBlockState());
                }
                break;
            }
        }
    }

    // --- 70. 花圃: 靴子, 附近3-4格随机生成花 ---
    private static void tickFlowerBed(Player player, int tickCount) {
        if (getSlotEnchantmentLevel(player, ModEnchantments.FLOWER_BED, EquipmentSlot.FEET) <= 0) return;
        if (tickCount % 60 != 0) return;
        if (!(player.level() instanceof ServerLevel sl)) return;
        net.minecraft.world.level.block.Block[] flowers = {
                net.minecraft.world.level.block.Blocks.DANDELION, net.minecraft.world.level.block.Blocks.POPPY,
                net.minecraft.world.level.block.Blocks.BLUE_ORCHID, net.minecraft.world.level.block.Blocks.ALLIUM,
                net.minecraft.world.level.block.Blocks.AZURE_BLUET, net.minecraft.world.level.block.Blocks.RED_TULIP,
                net.minecraft.world.level.block.Blocks.ORANGE_TULIP, net.minecraft.world.level.block.Blocks.WHITE_TULIP,
                net.minecraft.world.level.block.Blocks.PINK_TULIP, net.minecraft.world.level.block.Blocks.OXEYE_DAISY,
                net.minecraft.world.level.block.Blocks.CORNFLOWER, net.minecraft.world.level.block.Blocks.LILY_OF_THE_VALLEY
        };
        int dist = 3 + RANDOM.nextInt(2); // 3-4 格
        net.minecraft.core.BlockPos base = player.blockPosition();
        net.minecraft.core.BlockPos pos = base.offset(RANDOM.nextInt(2 * dist + 1) - dist, 0, RANDOM.nextInt(2 * dist + 1) - dist);
        if (Math.abs(pos.getX() - base.getX()) + Math.abs(pos.getZ() - base.getZ()) < 2) return;
        if (sl.getBlockState(pos).isAir() && sl.getBlockState(pos.below()).is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)) {
            sl.setBlockAndUpdate(pos, flowers[RANDOM.nextInt(flowers.length)].defaultBlockState());
        }
    }

    // --- 71. 光环: 头盔, 有光照发光, 睡觉反胃, 夜晚跳过耗时翻倍 ---
    private static void tickHalo(Player player, int tickCount) {
        boolean hasHalo = getSlotEnchantmentLevel(player, ModEnchantments.HALO, EquipmentSlot.HEAD) > 0;
        // 维护连续入睡自计时(与 HaloSleepMixin 共享同一持久键)
        CompoundTag pd = player.getPersistentData();
        int slept = pd.getInt("zhonz_halo_sleep_ticks");
        if (player.isSleeping()) {
            if (hasHalo) {
                pd.putInt("zhonz_halo_sleep_ticks", Math.min(slept + 1, 200));
            }
        } else if (slept != 0) {
            pd.putInt("zhonz_halo_sleep_ticks", 0);
        }
        if (!hasHalo) return;
        if (player.level().getMaxLocalRawBrightness(player.blockPosition()) > 8) {
            player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
        }
        if (player.isSleeping()) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 0, false, false));
        }
    }

    // --- 72. 金酒之杯: 背包(含潜影盒)每绿宝石攻速+2% ---
    private static void tickGoldWineCup(Player player, CompoundTag data) {
        if (getEnchantmentLevel(player, ModEnchantments.GOLD_WINE_CUP) <= 0) {
            if (data.getInt("zhonz_gold_wine_last") != 0) {
                removeModifier(player, Attributes.ATTACK_SPEED, GOLD_WINE_CUP_ATTACK_SPEED_MODIFIER);
                data.putInt("zhonz_gold_wine_last", 0);
            }
            return;
        }
        int emeralds = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.is(Items.EMERALD)) {
                emeralds += stack.getCount();
            }
            // 潜影盒内绿宝石
            net.minecraft.world.item.component.ItemContainerContents contents =
                    stack.getOrDefault(DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.EMPTY);
            for (ItemStack inner : contents.nonEmptyItems()) {
                if (inner.is(Items.EMERALD)) emeralds += inner.getCount();
            }
        }
        int last = data.getInt("zhonz_gold_wine_last");
        if (emeralds == last) return;
        data.putInt("zhonz_gold_wine_last", emeralds);
        setTransient(player, Attributes.ATTACK_SPEED, GOLD_WINE_CUP_ATTACK_SPEED_MODIFIER,
                emeralds * 0.02, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    // ===================================================================
    // 测试辅助: 手动应用所有 tick 属性类附魔效果(供 /zhonztest testall 使用,
    // 因为 FakePlayer 不参与正常实体 tick)
    // ===================================================================
    public static void applyTickAttributeEffects(Player player) {
        CompoundTag data = getEntityData(player);
        tickApex(player, data);
        tickCorneredBeast(player, data);
        tickViolentPulse(player, data);
        tickFleetFootsteps(player, data);
        tickSelfBound(player, data);
        tickRapidAscent(player, data);
        tickAcceleratedFuture(player, data);
        tickSupremeArt(player, data);
        tickDivineCurse(player, data, player.tickCount);
        tickPrimalSuffering(player, data);
        tickLuxuriousHope(player, data);
        tickPhotophobe(player, data);
        tickEtiquette(player, data);
        tickPerfunctory(player, data);
        tickCritWeapons(player, data);
        tickGoldWineCup(player, data);
        // 73-89 属性类(测试假玩家直接调用 applyTickAttributeEffects 时也应刷新)
        tickKeenWill(player, data, player.tickCount);
        tickSharpen(player);
        tickHyperthymesia(player);
        tickSojourner(player);
        tickSorrowfulRedBonus(player);
        tickNewSunBonus(player);
        tickRapidAscentBonus(player);
        refreshDamageMultiplierAggregate(player);
    }

    // ===================================================================
    // LivingDeathEvent - Death prevention (Return from Hell, Divine Protection)
    // ===================================================================

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;

        recordBloodPathKill(event.getSource(), entity);
        // --- 83. 庄严哀悼: 击杀溅射(仅当死于远程且最后记录过命中伤害) ---
        trySolemnMourningBurst(event);

        if (trySmartTotem(entity, event)) {
            return;
        }
        if (tryReturnFromHell(entity, event)) {
            return;
        }
        tryDivineProtection(entity, event);
    }

    /** 83. 庄严哀悼: 若目标死于带该附魔的远程武器且最近有命中记录, 对 3 格内敌人造成一次等额伤害。 */
    private static void trySolemnMourningBurst(LivingDeathEvent event) {
        DamageSource source = event.getSource();
        Entity src = source.getEntity();
        if (!(src instanceof LivingEntity attacker)) return;
        if (getMainHandEnchantmentLevel(attacker, ModEnchantments.SOLEMN_MOURNING) <= 0) return;
        float last = getEntityData(attacker).getFloat(KEY_MOURNING_LAST);
        if (last <= 0) return;
        LivingEntity victim = event.getEntity();
        for (LivingEntity target : victim.level().getEntitiesOfClass(LivingEntity.class,
                victim.getBoundingBox().inflate(3.0))) {
            if (target == attacker || target == victim) continue;
            target.hurt(victim.level().damageSources().indirectMagic(source.getDirectEntity(), attacker), last);
        }
        getEntityData(attacker).remove(KEY_MOURNING_LAST);
    }

    /** 81. 铸就全一城盾: 盾挡后控制攻击来源并回复。 */
    @SubscribeEvent
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        onCityShieldBlock(event);
    }

    /** 87/89. 右键交互: 下界之星右键龙蛋(三千万转); 慈悲物品右键信标(慈悲绑定并消耗物品附魔)。 */
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        ItemStack hand = event.getItemStack();
        if (hand.isEmpty()) return;
        // 87. 三千万转: 带附魔的下界之星右键已放置的龙蛋
        if (hand.is(Items.NETHER_STAR)
                && hand.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.THIRTY_MILLION_TURNS)) > 0
                && player.level().getBlockState(event.getPos()).is(Blocks.DRAGON_EGG)) {
            onThirtyMillionTurns(player, hand);
            event.setCanceled(true);
            return;
        }
        // 89. 慈悲: 带慈悲的物品右键信标 → 绑定并移除物品上的慈悲附魔
        int mercyLv = readMercyLevel(hand);
        if (mercyLv <= 0) return;
        var be = player.level().getBlockEntity(event.getPos());
        if (!(be instanceof BeaconBlockEntity beacon)) return;
        if (com.zhonz.moreenchantments.util.BeaconMercyHelper.bindMercy(beacon, mercyLv)) {
            // 移除手持物品上的慈悲附魔(消耗被用来"附魔"信标的物品效果)
            removeMercyFrom(hand);
            event.setCanceled(true);
        }
    }

    /** 从物品(含附魔书 stored 附魔)读取慈悲等级。 */
    private static int readMercyLevel(ItemStack stack) {
        Holder<Enchantment> holder = ModEnchantments.getHolder(ModEnchantments.MERCY_EQUAL);
        int direct = stack.getEnchantmentLevel(holder);
        if (direct > 0) return direct;
        var stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored != null) return stored.getLevel(holder);
        return 0;
    }

    /** 从物品(含附魔书 stored 附魔)移除慈悲。 */
    private static void removeMercyFrom(ItemStack stack) {
        Holder<Enchantment> holder = ModEnchantments.getHolder(ModEnchantments.MERCY_EQUAL);
        ItemEnchantments cur = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments.Mutable mut = new ItemEnchantments.Mutable(cur);
        mut.set(holder, 0);
        stack.set(DataComponents.ENCHANTMENTS, mut.toImmutable());
        var stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored != null) {
            ItemEnchantments.Mutable smut = new ItemEnchantments.Mutable(stored);
            smut.set(holder, 0);
            stack.set(DataComponents.STORED_ENCHANTMENTS, smut.toImmutable());
        }
    }

    private static boolean trySmartTotem(LivingEntity entity, LivingDeathEvent event) {
        if (!(entity instanceof Player player)) return false;
        // 胸甲需有智能图腾附魔
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.SMART_TOTEM)) <= 0) return false;

        // 如果主手或副手已有不死图腾，交给原版处理（避免重复消耗）
        if (isTotem(player.getMainHandItem()) || isTotem(player.getOffhandItem())) return false;

        // 在背包中寻找不死图腾
        Inventory inv = player.getInventory();
        int totemSlot = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isTotem(inv.getItem(i))) {
                totemSlot = i;
                break;
            }
        }
        if (totemSlot < 0) return false;

        // 消耗一个不死图腾
        ItemStack totem = inv.getItem(totemSlot);
        totem.shrink(1);

        // 触发不死图腾效果（与原版行为一致）
        event.setCanceled(true);
        player.setHealth(1.0f);
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
            // 向附近玩家发送使用图腾的粒子效果
            for (var p : serverLevel.players()) {
                if (p.distanceToSqr(player) < 64.0) {
                    ((net.minecraft.server.level.ServerPlayer) p).connection.send(
                            new net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket(
                                    net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING, true,
                                    player.getX(), player.getY() + 1.0, player.getZ(),
                                    0.0f, 0.0f, 0.0f, 0.1f, 30));
                }
            }
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[SmartTotem] Consumed totem from inventory slot {} for {}", totemSlot, player.getName().getString());
        }
        return true;
    }

    private static boolean isTotem(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.TOTEM_OF_UNDYING);
    }

    private static void recordBloodPathKill(DamageSource source, LivingEntity victim) {
        Entity srcEntity = source.getEntity();
        if (!(srcEntity instanceof LivingEntity attacker)) return;
        ItemStack weapon = attacker.getMainHandItem();
        if (weapon.isEmpty()) return;
        if (weapon.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.BLOOD_PATH)) <= 0) return;
        String mobKey = getMobTypeId(victim);
        incrementBloodPathKill(weapon, mobKey);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[BloodPath] Recorded kill of {} (weapon={}, new count={})",
                    mobKey, weapon.getItem(), getBloodPathKillCount(weapon, mobKey));
        }
    }

    private static boolean tryReturnFromHell(LivingEntity entity, LivingDeathEvent event) {
        int returnFromHellLevel = getEnchantmentLevel(entity, ModEnchantments.RETURN_FROM_HELL);
        if (returnFromHellLevel <= 0) return false;

        CompoundTag data = getEntityData(entity);
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
        data.putInt(KEY_RETURN_FROM_HELL_CD, 6000); // 5 minutes
        if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 0.8f);
        }
        return true;
    }

    private static void tryDivineProtection(LivingEntity entity, LivingDeathEvent event) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack armor = entity.getItemBySlot(slot);
            if (armor.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.DIVINE_PROTECTION)) <= 0) continue;

            event.setCanceled(true);
            entity.setHealth(entity.getMaxHealth());
            entity.removeAllEffects();
            entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 400, 1));
            entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 1));
            entity.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 400, 0));
            if (armor.isDamageableItem()) {
                armor.hurtAndBreak((armor.getMaxDamage() - armor.getDamageValue()) / 4, entity, slot);
            }
            if (entity.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
            }
            return;
        }
    }

    // ===================================================================
    // PlayerTickEvent - Per-tick enchantment effects
    // ===================================================================

    @SubscribeEvent
    public static void onPlayerTick(Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        CompoundTag data = player.getPersistentData();
        int tickCount = player.tickCount;

        tickScavenger(player);
        tickDeepSeasGrace(player, tickCount);
        tickSupremeArt(player, data);
        tickThePure(player);
        tickCrimsonHellfire(player, tickCount);
        tickProphetsCall(player, tickCount);
        tickFoolsMask(player, data, tickCount);
        tickHang(player, tickCount);
        tickFishball(player, tickCount);
        tickDivineCurse(player, data, tickCount);
        tickExplosiveDawn(player);
        tickMustOpenPath(player, tickCount);
        tickIncompleteForeknowledge(player, tickCount);
        tickEmergencyRescue(player, data);
        tickFleetFootsteps(player, data);
        tickApex(player, data);
        tickCorneredBeast(player, data);
        tickViolentPulse(player, data);
        tickPatience(player, data, tickCount);
        tickSelfBound(player, data);
        tickRapidAscent(player, data);
        tickAcceleratedFuture(player, data);
        tickPaleMidnight(player, tickCount);
        tickPrimalSuffering(player, data);
        tickLuxuriousHope(player, data);
        tickPhotophile(player, tickCount);
        tickPhotophobe(player, data);
        tickEtiquette(player, data);
        tickPerfunctory(player, data);
        tickCritWeapons(player, data);
        tickAutoMerge(player, tickCount);
        tickFlowerBed(player, tickCount);
        tickHalo(player, tickCount);
        tickGoldWineCup(player, data);
        tickBoneBreakRevert(player, data);
        // --- 73-89 per-tick ---
        tickKeenWill(player, data, tickCount);
        tickSharpen(player);
        tickHyperthymesia(player);
        tickEternalStanding(player);
        tickSolemnMourning(player);
        tickShatter(player, data, tickCount);
        tickSojourner(player);
        tickSorrowfulRedBonus(player);
        tickNewSunBonus(player);
        tickRapidAscentBonus(player);
        refreshDamageMultiplierAggregate(player);
        tickCooldownsAndCleanup(player, data, tickCount);
    }

    private static void tickScavenger(Player player) {
        if (getSlotEnchantmentLevel(player, ModEnchantments.SCAVENGER, EquipmentSlot.HEAD) <= 0) return;
        // Continuously strip Hunger / Poison / Nausea so they never actually take hold.
        if (player.hasEffect(MobEffects.HUNGER)) player.removeEffect(MobEffects.HUNGER);
        if (player.hasEffect(MobEffects.POISON)) player.removeEffect(MobEffects.POISON);
        if (player.hasEffect(MobEffects.CONFUSION)) player.removeEffect(MobEffects.CONFUSION);
    }

    private static void tickDeepSeasGrace(Player player, int tickCount) {
        int level = getEnchantmentLevel(player, ModEnchantments.DEEP_SEAS_GRACE);
        if (level <= 0) return;
        if (player.isUsingItem() && player.getUseItem().is(Items.SHIELD) && tickCount % 40 == 0) {
            float healRatio = level == 1 ? 0.05f : (level == 2 ? 0.10f : 0.20f);
            player.heal(player.getMaxHealth() * healRatio);
        }
        if (player.isInWater()) {
            player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 40, level - 1));
        }
    }

    private static void tickSupremeArt(Player player, CompoundTag data) {
        // 至高之术仅在主手持武器时生效(all_weapon, slots mainhand)
        int level = getMainHandEnchantmentLevel(player, ModEnchantments.SUPREME_ART);
        // 加伤 +20%/级 → bonus_damage 通道(percent 计算下沉 common); 每 tick 刷新以响应装卸
        addPercentBonus(player, SUPREME_ART_BONUS_MODIFIER,
                com.zhonz.moreenchantments.common.damage.TickBonusRules.supremeArt(EVENT_COND_CTX.levels, player));

        int last = data.getInt(KEY_SUPREME_ART_LAST_LEVEL);
        if (level == last) return;
        data.putInt(KEY_SUPREME_ART_LAST_LEVEL, level);

        double rangeBonus = level * 2.0;
        double speedBonus = 0.3 * level;

        setTransient(player, Attributes.ENTITY_INTERACTION_RANGE, SUPREME_ART_RANGE_MODIFIER,
                level > 0 ? rangeBonus : 0, AttributeModifier.Operation.ADD_VALUE);
        setTransient(player, Attributes.BLOCK_INTERACTION_RANGE, SUPREME_ART_RANGE_MODIFIER,
                level > 0 ? rangeBonus : 0, AttributeModifier.Operation.ADD_VALUE);
        setTransient(player, Attributes.ATTACK_SPEED, SUPREME_ART_ATTACK_SPEED_MODIFIER,
                level > 0 ? speedBonus : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    private static void tickThePure(Player player) {
        if (getEnchantmentLevel(player, ModEnchantments.THE_PURE) > 0) {
            removeEffects(player, false);
        }
    }

    private static void tickCrimsonHellfire(Player player, int tickCount) {
        if (getEnchantmentLevel(player, ModEnchantments.CRIMSON_HELLFIRE) <= 0) return;
        if (tickCount % 40 != 0) return;

        for (LivingEntity nearby : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(3.75))) {
            if (nearby != player && nearby.isAlive() && player.canAttack(nearby)) {
                nearby.hurt(player.damageSources().magic(), 1.0f);
            }
        }
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.FLAME,
                    player.getX(), player.getY() + 1, player.getZ(),
                    10, 2.0, 1.0, 2.0, 0.05);
        }
    }

    private static void tickProphetsCall(Player player, int tickCount) {
        int level = getMainHandEnchantmentLevel(player, ModEnchantments.PROPHETS_CALL);
        if (level <= 0 || !player.isUsingItem() || !player.getUseItem().is(Items.GOAT_HORN)) return;

        double detectRange = 20.0 + level * 10.0;
        List<Monster> hostiles = player.level().getEntitiesOfClass(Monster.class,
                player.getBoundingBox().inflate(detectRange),
                mob -> mob.isAlive() && !mob.hasEffect(MobEffects.GLOWING) && mob.canAttack(player));
        if (hostiles.isEmpty()) return;

        for (Monster hostile : hostiles) {
            hostile.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0));
            CompoundTag hostileData = getEntityData(hostile);
            hostileData.putBoolean(KEY_PROPHETS_CALL_ACTIVE, true);
            hostileData.putLong(KEY_PROPHETS_CALL_UNTIL, player.level().getGameTime() + 80);
        }
        if (tickCount % 40 == 0) {
            player.displayClientMessage(
                    Component.translatable("enchantment.zhonz_more_enchantments.prophets_call.warning",
                            hostiles.size()),
                    true);
        }
    }

    private static void tickFoolsMask(Player player, CompoundTag data, int tickCount) {
        if (getSlotEnchantmentLevel(player, ModEnchantments.FOOLS_MASK, EquipmentSlot.HEAD) <= 0) return;
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

    @SuppressWarnings("deprecation")
    private static void tickHang(Player player, int tickCount) {
        if (!hasEnchantmentInInventory(player, ModEnchantments.HANG)) {
            // Strip granted flight if the player no longer carries Hang
            if (player.isCreative() || player.isSpectator()) return;
            // mayfly is marked @Deprecated in 1.21.1 but is still the only field that gates player flight.
            if (!player.getAbilities().mayfly) return;
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            return;
        }

        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 4, false, false));
        // mayfly is marked @Deprecated in 1.21.1 but is still the only field that gates player flight.
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 6, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 254, false, false));

        if (tickCount % 20 == 0) {
            for (Monster hostile : player.level().getEntitiesOfClass(Monster.class,
                    player.getBoundingBox().inflate(35.0))) {
                if (hostile.isAlive()) hostile.setHealth(0);
            }
        }
    }

    private static void tickFishball(Player player, int tickCount) {
        if (getEnchantmentLevel(player, ModEnchantments.FISHBALL) <= 0) return;

        // 10x durability: regen 1 durability every 2s on any Fishball item the player carries.
        // Includes chest/offhand and inventory; the inventory loop also covers the equipment slots.
        if (tickCount % 40 == 0) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (hasFishball(stack) && stack.isDamageableItem() && stack.getDamageValue() > 0) {
                    stack.setDamageValue(Math.max(0, stack.getDamageValue() - 1));
                }
            }
        }

        // Locate a single Fishball piece to act as the durability sink. Prefer equipped chest, then offhand.
        ItemStack fishballStack = player.getItemBySlot(EquipmentSlot.CHEST);
        EquipmentSlot fishballSlot = hasFishball(fishballStack) ? EquipmentSlot.CHEST : null;
        if (fishballSlot == null) {
            ItemStack offhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
            if (hasFishball(offhand)) {
                fishballStack = offhand;
                fishballSlot = EquipmentSlot.OFFHAND;
            }
        }
        if (fishballSlot == null || !fishballStack.isDamageableItem()) return;

        CompoundTag fishballData = getEntityData(player);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot == fishballSlot) continue;
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty() || !stack.isDamageableItem()) continue;

            String key = "zhonz_fishball_last_damage_" + slot.getName();
            int currentDamage = stack.getDamageValue();
            int lastDamage = fishballData.contains(key) ? fishballData.getInt(key) : currentDamage;
            fishballData.putInt(key, currentDamage);

            if (currentDamage <= lastDamage) continue;
            int absorb = Math.min(currentDamage - lastDamage, fishballStack.getMaxDamage() - fishballStack.getDamageValue());
            if (absorb <= 0) continue;

            stack.setDamageValue(currentDamage - absorb);
            fishballStack.setDamageValue(fishballStack.getDamageValue() + absorb);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Fishball] 吸收{}点耐久, 物品从{}恢复, 鱼丸消耗到{}",
                        absorb, currentDamage - absorb, fishballStack.getDamageValue());
            }
        }
    }

    private static void tickDivineCurse(Player player, CompoundTag data, int tickCount) {
        int level = getEnchantmentLevel(player, ModEnchantments.DIVINE_CURSE);

        // Re-apply attribute modifiers only on level transitions (cheap)
        int last = data.getInt(KEY_DIVINE_CURSE_LAST_LEVEL);
        if (level != last) {
            data.putInt(KEY_DIVINE_CURSE_LAST_LEVEL, level);
            double mult = level > 0 ? -0.5 : 0;
            setTransient(player, Attributes.ENTITY_INTERACTION_RANGE, DIVINE_CURSE_RANGE_MODIFIER,
                    mult, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            setTransient(player, Attributes.BLOCK_INTERACTION_RANGE, DIVINE_CURSE_BLOCK_RANGE_MODIFIER,
                    mult, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            setTransient(player, Attributes.ATTACK_DAMAGE, DIVINE_CURSE_DAMAGE_MODIFIER,
                    mult, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            setTransient(player, Attributes.ATTACK_SPEED, DIVINE_CURSE_ATTACK_SPEED_MODIFIER,
                    mult, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            // Apothic Attributes 接入: 挖掘速度/蓄力速度减半,冷却时间翻倍
            setTransient(player, ALObjects.Attributes.MINING_SPEED, DIVINE_CURSE_MINING_MODIFIER,
                    mult, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            setTransient(player, ALObjects.Attributes.DRAW_SPEED, DIVINE_CURSE_DRAW_MODIFIER,
                    mult, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            // 冷却翻倍 = 冷却缩减 -100%
            setTransient(player, ALObjects.Attributes.COOLDOWN_REDUCTION, DIVINE_CURSE_COOLDOWN_MODIFIER,
                    mult * 2.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
        if (level <= 0) return;

        // 1% durability loss per second on items with the curse
        if (tickCount % 20 == 0) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack stack = player.getItemBySlot(slot);
                if (hasDivineCurse(stack) && stack.isDamageableItem()) {
                    int durabilityToRemove = Math.max(1, (stack.getMaxDamage() - stack.getDamageValue()) / 100);
                    stack.hurtAndBreak(durabilityToRemove, player, slot);
                }
            }
        }
        // 注意: 攻击伤害/攻击速度/触摸范围/挖掘速度/蓄力速度/冷却时间均已由属性修饰实现,
        // 不再叠加 Weakness/DigSlowdown 等状态近似效果(避免双重削弱)。
        // "可破坏方块硬度减半"由 BlockBreakMixin / DivineCurseBlockBreakMixin 实现。
    }

    private static void tickExplosiveDawn(Player player) {
        int level = getMainHandEnchantmentLevel(player, ModEnchantments.EXPLOSIVE_DAWN);
        CompoundTag data = getEntityData(player);
        if (level <= 0 || !player.isUsingItem() || !player.getUseItem().is(Items.CROSSBOW)) {
            if (data.getBoolean(KEY_EXPLOSIVE_DAWN_RELOADING)) {
                data.putBoolean(KEY_EXPLOSIVE_DAWN_RELOADING, false);
            }
            return;
        }
        if (data.getBoolean(KEY_EXPLOSIVE_DAWN_RELOADING)) {
            // Invincibility while the crossbow is reloading
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 4, false, false));
        }
    }

    private static void tickMustOpenPath(Player player, int tickCount) {
        if (getMainHandEnchantmentLevel(player, ModEnchantments.MUST_OPEN_PATH) <= 0) return;
        if (tickCount % 10 != 0) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        serverLevel.sendParticles(ParticleTypes.BUBBLE,
                player.getX(), player.getY() + 1, player.getZ(),
                5, 1.0, 0.5, 1.0, 0.1);
    }

    private static void tickIncompleteForeknowledge(Player player, int tickCount) {
        CompoundTag data = getEntityData(player);
        int level = getSlotEnchantmentLevel(player, ModEnchantments.INCOMPLETE_FOREKNOWLEDGE_EYE, EquipmentSlot.HEAD);

        if (level <= 0) {
            // 未穿戴:移除 DODGE_CHANCE 修饰,防止残留
            if (data.getFloat(KEY_FOREKNOWLEDGE_DODGE_APPLIED) > 0) {
                removeModifier(player, ALObjects.Attributes.DODGE_CHANCE, FOREKNOWLEDGE_DODGE_MODIFIER);
                data.putFloat(KEY_FOREKNOWLEDGE_DODGE_APPLIED, 0);
            }
            return;
        }

        long lastCombat = data.getLong(KEY_FOREKNOWLEDGE_LAST_COMBAT);
        // 恢复:停止战斗 2 秒(40 tick)后开始,每秒 +1%(0.0005/tick),约 75 秒从 0.05 回满到 0.80
        // (符合设计"在没有受到攻击或者没有主动攻击的80秒内逐步回升概率，每秒+1%")
        if (player.level().getGameTime() - lastCombat > 40) {
            float prob = data.contains(KEY_FOREKNOWLEDGE_DODGE) ? data.getFloat(KEY_FOREKNOWLEDGE_DODGE) : 0.80f;
            if (prob < 0.80f) {
                data.putFloat(KEY_FOREKNOWLEDGE_DODGE, Math.min(0.80f, prob + 0.0005f));
            }
        }

        // 同步当前概率到 Apothic DODGE_CHANCE 修饰(仅在变化时更新)
        float prob = data.contains(KEY_FOREKNOWLEDGE_DODGE) ? data.getFloat(KEY_FOREKNOWLEDGE_DODGE) : 0.80f;
        float applied = data.getFloat(KEY_FOREKNOWLEDGE_DODGE_APPLIED);
        if (Math.abs(applied - prob) > 0.0001f) {
            data.putFloat(KEY_FOREKNOWLEDGE_DODGE_APPLIED, prob);
            setTransient(player, ALObjects.Attributes.DODGE_CHANCE, FOREKNOWLEDGE_DODGE_MODIFIER,
                    prob, AttributeModifier.Operation.ADD_VALUE);
            if (LOGGER.isDebugEnabled()) {
                // 诊断:确认修饰符是否真正作用到了属性值上
                var attr = player.getAttribute(ALObjects.Attributes.DODGE_CHANCE);
                LOGGER.debug("[ForeknowledgeEye] Synced dodgeChance: prob={} attrPresent={} attrValue={}",
                        prob, attr != null, attr != null ? attr.getValue() : -1.0);
            }
        }

        // 低闪避率时持续反胃模糊视野: 每 10 tick 施加,持续 60 tick,保证无断档且 HUD 可见
        if (prob < 0.20f && tickCount % 10 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 0, false, true));
        }
    }

    private static void tickEmergencyRescue(Player player, CompoundTag data) {
        if (getEnchantmentLevel(player, ModEnchantments.EMERGENCY_RESCUE) <= 0) return;
        if (player.getHealth() > 5.0f) return;
        if (data.getInt(KEY_EMERGENCY_RESCUE_CD) > 0) return;

        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 2));
        for (LivingEntity nearby : getNearbySameType(player, 10.0)) {
            nearby.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 2));
        }
        data.putInt(KEY_EMERGENCY_RESCUE_CD, 200);
    }

    // --- 37→36. 断汝筋骨 → 舍吾皮肉: revert after 3 seconds (60 ticks) ---
    private static void tickBoneBreakRevert(Player player, CompoundTag data) {
        ItemStack weapon = player.getMainHandItem();
        if (weapon.isEmpty()) return;
        if (weapon.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.BONE_BREAK)) <= 0) return;

        long switchTick = data.getLong(KEY_BONE_BREAK_SWITCH_TICK);
        if (switchTick == 0) return; // no switch recorded yet
        // 3 seconds = 60 ticks
        if (player.tickCount - switchTick < 60) return;

        // Switch bone_break → flesh_sacrifice
        Holder<Enchantment> fleshHolder = ModEnchantments.getHolder(ModEnchantments.FLESH_SACRIFICE);
        Holder<Enchantment> boneHolder = ModEnchantments.getHolder(ModEnchantments.BONE_BREAK);
        if (fleshHolder == null || boneHolder == null) return;

        ItemEnchantments current = weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(current);
        mutable.set(boneHolder, 0); // remove old
        mutable.set(fleshHolder, 1); // add new
        weapon.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        data.remove(KEY_BONE_BREAK_SWITCH_TICK);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[BoneBreak] Reverted to Flesh Sacrifice on {}'s weapon at tick {}",
                    player.getName().getString(), player.tickCount);
        }
    }

    private static void tickCooldownsAndCleanup(Player player, CompoundTag data, int tickCount) {
        tickCooldown(data, KEY_EMERGENCY_RESCUE_CD);
        tickCooldown(data, KEY_RETURN_FROM_HELL_CD);
        tickCooldown(data, KEY_MUST_OPEN_PATH_CD);

        // Expire the Grievous Wound debuff window
        CompoundTag entityData = getEntityData(player);
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

        // Periodically drop stale per-attacker shell-strip percent keys
        if (tickCount % 100 == 0) {
            for (String key : new ArrayList<>(entityData.getAllKeys())) {
                if (key.startsWith("zhonz_shell_strip_percent_")) {
                    entityData.remove(key);
                }
            }
        }

        // Flipping coin stacks reset on player respawn (death event already handles this)
        if (entityData.getInt(KEY_FLIPPING_COIN_ATTACK_STACKS) > 0 && !player.isAlive()) {
            entityData.remove(KEY_FLIPPING_COIN_ATTACK_STACKS);
            var maxHp = player.getAttribute(Attributes.MAX_HEALTH);
            if (maxHp != null) maxHp.removeModifier(FLIPPING_COIN_MAX_HP);
            player.removeEffect(MobEffects.DAMAGE_BOOST);
        }
    }

    // ===================================================================
    // Attribute modifier helpers
    // ===================================================================

    private static void addTransient(LivingEntity entity, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                                     ResourceLocation id, double amount, AttributeModifier.Operation op) {
        var instance = entity.getAttribute(attr);
        if (instance != null) instance.addTransientModifier(new AttributeModifier(id, amount, op));
    }

    private static void removeModifier(LivingEntity entity, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                                       ResourceLocation id) {
        var instance = entity.getAttribute(attr);
        if (instance != null) instance.removeModifier(id);
    }

    /**
     * Replace any existing modifier with this id, adding a new one if {@code amount} != 0.
     */
    private static void setTransient(LivingEntity entity,
                                     net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                                     ResourceLocation id, double amount, AttributeModifier.Operation op) {
        var instance = entity.getAttribute(attr);
        if (instance == null) return;
        instance.removeModifier(id);
        if (amount != 0) {
            instance.addTransientModifier(new AttributeModifier(id, amount, op));
        }
    }

    private static boolean hasFishball(ItemStack stack) {
        return !stack.isEmpty() && stack.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.FISHBALL)) > 0;
    }

    private static boolean hasDivineCurse(ItemStack stack) {
        return !stack.isEmpty() && stack.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.DIVINE_CURSE)) > 0;
    }

    // ===================================================================
    // Random effect / mineral helpers
    // ===================================================================

    private static ItemStack getRandomMineral() {
        ItemStack[] minerals = {
                new ItemStack(Items.RAW_IRON), new ItemStack(Items.RAW_GOLD),
                new ItemStack(Items.DIAMOND), new ItemStack(Items.EMERALD),
                new ItemStack(Items.LAPIS_LAZULI), new ItemStack(Items.REDSTONE),
                new ItemStack(Items.RAW_COPPER), new ItemStack(Items.COAL),
                new ItemStack(Items.IRON_NUGGET), new ItemStack(Items.GOLD_NUGGET),
                new ItemStack(Items.AMETHYST_SHARD), new ItemStack(Items.QUARTZ)
        };
        return minerals[RANDOM.nextInt(minerals.length)].copy();
    }

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
    // 统一增伤属性通道(bonus_damage 加算层 + damage_multiplier 乘算层)
    // ===================================================================

    // 迁移到统一通道的加伤型附魔 modifier id(独立于各自 tick 的其他属性)
    private static final ResourceLocation SUPREME_ART_BONUS_MODIFIER = rl("bonus_supreme_art");
    private static final ResourceLocation SORROWFUL_RED_BONUS_MODIFIER = rl("bonus_sorrowful_red");
    private static final ResourceLocation NEW_SUN_BONUS_MODIFIER = rl("bonus_new_sun");
    private static final ResourceLocation RAPID_ASCENT_BONUS_MODIFIER = rl("bonus_rapid_ascent");
    private static final ResourceLocation CORNERED_BEAST_BONUS_MODIFIER = rl("bonus_cornered_beast");

    /**
     * 统一增伤结算: final = amount × (1 + bonus_damage) × damage_multiplier + flat_damage。
     *
     * - bonus_damage 为"加伤": 加百分之多少(X%)就 ×(1+X), 默认 0(即 ×1)。
     *   各加伤附魔以独立 ADD_VALUE modifier 累加百分比。
     * - damage_multiplier 为"乘伤": 直接 × 该值, 默认 1; 乘伤附魔的总乘积写入。
     * - flat_damage 为"固定点加伤": 结算后再直接 +flat(绝对量, 不受 %/× 缩放),
     *   供 fleet_footsteps/floating_grace 等 flat 附魔(round13 用户决策)。
     *
     * 结算顺序固定: (先加伤后乘伤) 再 + flat。目灯"眩惑"会同时把两个属性都 ×0.5。
     * 默认(属性未接入附魔时)等于原样放行。必须在最后一步调用。
     */
    private static float applyUnifiedDamageAttributes(LivingEntity attacker, float amount) {
        // 委托平台无关结算引擎(纯函数; 三通道值按本平台 1.21 API 读取后传入, 引擎本体跨版本可复用)
        double bonus = attacker.getAttributeValue(com.zhonz.moreenchantments.attribute.ZhonzAttributes.BONUS_DAMAGE);
        double mult = attacker.getAttributeValue(com.zhonz.moreenchantments.attribute.ZhonzAttributes.DAMAGE_MULTIPLIER);
        double flat = attacker.getAttributeValue(com.zhonz.moreenchantments.attribute.ZhonzAttributes.FLAT_DAMAGE);
        return com.zhonz.moreenchantments.common.damage.UnifiedDamageEngine.settle(
                attacker.getName().getString(), amount, bonus, mult, flat);
    }

    /** 加伤型写入辅助: 累加"增伤百分比"到 bonus_damage(多个附魔各用独立 modifier id)。LivingEntity 亦可(Q8)。委托引擎。 */
    private static void addPercentBonus(LivingEntity entity, ResourceLocation id, double percent) {
        com.zhonz.moreenchantments.common.damage.UnifiedDamageEngine.addPercentBonus(
                entity.getAttribute(com.zhonz.moreenchantments.attribute.ZhonzAttributes.BONUS_DAMAGE), id, percent);
    }

    /** 乘伤型写入辅助: 把攻击者 damage_multiplier 设为 multiplier(总乘积)。默认 1.0, 故写入 (multiplier-1)。LivingEntity 亦可(Q8)。委托引擎。 */
    private static void setDamageMultiplier(LivingEntity entity, ResourceLocation id, double multiplier) {
        com.zhonz.moreenchantments.common.damage.UnifiedDamageEngine.setDamageMultiplier(
                entity.getAttribute(com.zhonz.moreenchantments.attribute.ZhonzAttributes.DAMAGE_MULTIPLIER), id, multiplier);
    }

    /** 收到伤害乘数写入: 受击者 incoming_damage 聚合(总乘积, 默认1)。委托引擎。 */
    private static void setIncomingDamage(LivingEntity defender, ResourceLocation id, double product) {
        com.zhonz.moreenchantments.common.damage.UnifiedDamageEngine.setIncomingDamage(
                defender.getAttribute(com.zhonz.moreenchantments.attribute.ZhonzAttributes.INCOMING_DAMAGE), id, product);
    }

    /** 收到伤害统一结算: final = amount × defender.incoming_damage(默认1)。护甲结算后调用。委托引擎。 */
    private static float applyIncomingDamageAttributes(LivingEntity defender, float amount) {
        double incoming = defender.getAttributeValue(com.zhonz.moreenchantments.attribute.ZhonzAttributes.INCOMING_DAMAGE);
        return com.zhonz.moreenchantments.common.damage.UnifiedDamageEngine.settleIncoming(
                defender.getName().getString(), amount, incoming);
    }

    // ===================================================================
    // Q9 事件条件型乘伤(tick 无法预知目标/时序): 攻击事件内判定 → 以临时 modifier
    // 乘入 damage_multiplier(与 tick 聚合乘积保持严格乘积语义)→ 统一结算 → 立即清除。
    // 试点: 76. 泰坦(打精英/BOSS ×2)。其余事件型确认模式后同法迁移。
    // ===================================================================
    private static final ResourceLocation EVENT_MULT_TEMP = rl("event_mult_temp");
    private static final ResourceLocation EVENT_BONUS_TEMP = rl("event_bonus_temp");
    // flat 绝对加伤事件临时 modifier(round13): fleet_footsteps/floating_grace 各一独立 id
    private static final ResourceLocation EVENT_FLAT_FLEET = rl("event_flat_fleet");
    private static final ResourceLocation EVENT_FLAT_GRACE = rl("event_flat_grace");

    /** flat 写入辅助: 把固定点数加伤写入 flat_damage(独立 id, ADD_VALUE 绝对量)。委托引擎。 */
    private static void setFlatDamage(LivingEntity entity, ResourceLocation id, double value) {
        com.zhonz.moreenchantments.common.damage.UnifiedDamageEngine.setFlatDamage(
                entity.getAttribute(com.zhonz.moreenchantments.attribute.ZhonzAttributes.FLAT_DAMAGE), id, value);
    }

    /** 结算后清除所有 flat 事件临时 modifier(幂等; 无则不动)。 */
    private static void clearEventFlatTemporary(LivingEntity attacker) {
        var inst = attacker.getAttribute(com.zhonz.moreenchantments.attribute.ZhonzAttributes.FLAT_DAMAGE);
        if (inst == null) return;
        inst.removeModifier(EVENT_FLAT_FLEET);
        inst.removeModifier(EVENT_FLAT_GRACE);
    }

    /** 事件条件判定上下文(stage2: 等级查询/血路击杀委托平台层实现, 键与事件层共享)。 */
    private static final com.zhonz.moreenchantments.common.damage.EventDamageContext EVENT_COND_CTX =
            new com.zhonz.moreenchantments.common.damage.EventDamageContext(
                    new com.zhonz.moreenchantments.common.damage.EnchantmentLevelLookup() {
                        private ResourceKey<Enchantment> key(String id) {
                            return ResourceKey.create(Registries.ENCHANTMENT,
                                    ResourceLocation.fromNamespaceAndPath(MOD_ID, id));
                        }

                        @Override
                        public int anySlot(LivingEntity entity, String enchantId) {
                            return getEnchantmentLevel(entity, key(enchantId));
                        }

                        @Override
                        public int mainHand(LivingEntity entity, String enchantId) {
                            return getMainHandEnchantmentLevel(entity, key(enchantId));
                        }

                        @Override
                        public int slot(LivingEntity entity, String enchantId, EquipmentSlot slot) {
                            return getSlotEnchantmentLevel(entity, key(enchantId), slot);
                        }
                    },
                    (attacker, defender) -> getBloodPathKillCount(attacker.getMainHandItem(), getMobTypeId(defender)),
                    KEY_LIBERATOR_LAST_ATTACK, KEY_FOOLS_MASK_LUCKY,
                    KEY_RHYTHM_HIT_ATTACK, KEY_CEASELESS_STACKS, KEY_TITAN_ELITE);

    /** 事件条件型乘伤总乘积(本事件内判定)。返回 1.0 = 无贡献。委托 common 判定规则(stage2)。 */
    private static double computeEventConditionalMultiplier(LivingEntity attacker, LivingEntity defender) {
        return com.zhonz.moreenchantments.common.damage.EventDamageConditions.computeConditionalMultiplier(
                EVENT_COND_CTX, attacker, defender);
    }

    /**
     * 事件条件型加伤总百分比(本事件内判定, 与 tick 加伤严格累加进 bonus_damage)。
     * 返回 0.0 = 无贡献。委托 common 判定规则(stage2)。
     */
    private static double computeEventBonusPercent(LivingEntity attacker, LivingEntity defender) {
        return com.zhonz.moreenchantments.common.damage.EventDamageConditions.computeBonusPercent(
                EVENT_COND_CTX, attacker, defender);
    }

    /** 结算前把事件加伤百分比临时累加进 bonus_damage(ADD_VALUE 百分比)。结算后必须 clear。委托引擎。 */
    private static void applyEventBonusTemporary(LivingEntity attacker, double percent) {
        com.zhonz.moreenchantments.common.damage.UnifiedDamageEngine.applyEventBonusTemporary(
                attacker.getAttribute(com.zhonz.moreenchantments.attribute.ZhonzAttributes.BONUS_DAMAGE),
                EVENT_BONUS_TEMP, percent);
    }

    /** 统一结算后清除事件临时加伤(幂等; 无则不动)。 */
    private static void clearEventBonusTemporary(LivingEntity attacker) {
        com.zhonz.moreenchantments.common.damage.UnifiedDamageEngine.clearEventBonusTemporary(
                attacker.getAttribute(com.zhonz.moreenchantments.attribute.ZhonzAttributes.BONUS_DAMAGE),
                EVENT_BONUS_TEMP);
    }

    /**
     * 结算前把事件乘积乘入 damage_multiplier: 写入 delta = 当前属性值×(factor-1),
     * 使结算读到"当前值×factor"; 与 tick 聚合/目灯等既有 modifier 保持严格乘积(非加和)。
     * 结算后必须调 clearEventMultiplierTemporary 还原。委托引擎。
     */
    private static void applyEventMultiplierTemporary(LivingEntity attacker, double factor) {
        com.zhonz.moreenchantments.common.damage.UnifiedDamageEngine.applyEventMultiplierTemporary(
                attacker.getAttribute(com.zhonz.moreenchantments.attribute.ZhonzAttributes.DAMAGE_MULTIPLIER),
                EVENT_MULT_TEMP, factor);
    }

    /** 统一结算后清除事件临时乘伤(幂等; 无则不动)。 */
    private static void clearEventMultiplierTemporary(LivingEntity attacker) {
        com.zhonz.moreenchantments.common.damage.UnifiedDamageEngine.clearEventMultiplierTemporary(
                attacker.getAttribute(com.zhonz.moreenchantments.attribute.ZhonzAttributes.DAMAGE_MULTIPLIER),
                EVENT_MULT_TEMP);
    }

    /**
     * 乘伤聚合器(tick 缓存, 用户 Q6; 对所有 LivingEntity 生效, Q8):
     * 在攻击结算前把"主手/自身状态可判定"的乘伤附魔贡献的总乘积写入
     * damage_multiplier 的单一聚合 modifier(id=统一)。事件条件型乘伤
     * (泰坦打精英等, tick 无法预知目标)由攻击事件内以临时 modifier 叠加。
     *
     * 目前乘伤附魔: bone_break(主手无条件 ×6)。
     */
    private static void refreshDamageMultiplierAggregate(LivingEntity attacker) {
        // 判定规则下沉 common(stage2): 见 EventDamageConditions.tickMultiplierProduct
        double product = com.zhonz.moreenchantments.common.damage.EventDamageConditions.tickMultiplierProduct(
                EVENT_COND_CTX, attacker);
        setDamageMultiplier(attacker, UNIFIED_MULT_AGGREGATE, product);
    }

    private static final ResourceLocation UNIFIED_MULT_AGGREGATE = rl("unified_mult_aggregate");
    private static final ResourceLocation INCOMING_TICK_AGGREGATE = rl("incoming_tick_aggregate");

    /**
     * 收到伤害 tick 常驻聚合: 把"穿戴/自身状态可判定"的减伤/易伤总乘积写入
     * defender.incoming_damage 的单一聚合 modifier(默认1, 写 product-1)。
     * 每次受击结算前刷新(与攻击侧 refreshDamageMultiplierAggregate 同频, 免 PlayerTick)。
     * 事件条件型(标记/来源)在 applyIncomingSettlement 以临时 modifier 追加(严格乘积)。
     */
    private static void refreshIncomingAggregate(LivingEntity defender) {
        double product = 1.0D;
        // 45. 顶点(主/副手): 受伤 -60% → ×0.4
        if (isHoldingApex(defender)) product *= 0.4D;
        // 46. 困兽之斗(头盔, 生命<25%): 受伤 -50% → ×0.5
        if (getSlotEnchantmentLevel(defender, ModEnchantments.CORNERED_BEAST, EquipmentSlot.HEAD) > 0
                && defender.getHealth() <= defender.getMaxHealth() * 0.25f) {
            product *= 0.5D;
        }
        // 60. 奢侈的希望(满血): 受伤 +50% → ×1.5
        if (getEnchantmentLevel(defender, ModEnchantments.LUXURIOUS_HOPE) > 0
                && defender.getHealth() >= defender.getMaxHealth() - 0.5f) {
            product *= 1.5D;
        }
        // 36/37. 舍吾皮肉(受伤+30%)/断汝筋骨(受伤-30%): 主手状态
        ItemStack hand = defender.getMainHandItem();
        if (!hand.isEmpty()) {
            if (hand.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.FLESH_SACRIFICE)) > 0) product *= 1.3D;
            else if (hand.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.BONE_BREAK)) > 0) product *= 0.7D;
        }
        // 52. 极速攀升(靴子, y<0): 受伤 ×(1 + y/100)
        if (getSlotEnchantmentLevel(defender, ModEnchantments.RAPID_ASCENT, EquipmentSlot.FEET) > 0) {
            double y = defender.getY();
            if (y < 0) product *= (1.0D + y * 0.01D);
        }
        setIncomingDamage(defender, INCOMING_TICK_AGGREGATE, product);
    }

    // ===================================================================
    // 新设计附魔 73-89(2026-09, 见 ENCHANTMENTS.md 五章)
    // ===================================================================

    /** 是否为冰霜伤害: 自定义 frost 或原版 is_freezing(freeze, 由粉雪触发)。 */
    private static boolean isFrostSource(DamageSource source) {
        return source.is(FROST) || source.is(net.minecraft.tags.DamageTypeTags.IS_FREEZING);
    }

    /** 是否"雪天/雨天"判定(雪的伤: 主手持有者所在世界正在下雨, 任意群系视作下雪)。下沉到 common(EventDamageConditions.isSnowWeather)。 */
    // isSnowWeather 判定已下沉 common/damage/EventDamageConditions, 本文件不再定义

    private static boolean isFrostyAttacker(LivingEntity attacker) {
        return getMainHandEnchantmentLevel(attacker, ModEnchantments.SNOW_WOUND) > 0;
    }

    // --- 73. 雪的伤: 攻击视为冰霜伤害由 WeepingFireHelper 转换; 雪天/配殇 ×1.5 乘伤已迁
    // computeEventConditionalMultiplier(雪天 ×1.5, 同持雪的殇 ×1.5, 乘叠) ---

    // --- 74. 雪的殇: 攻击施加"冬痕"(脚下细雪, 受冰霜伤害 +50%); 与雪的伤同附魔且有冬痕生物时改雪天 ---
    private static void applySnowSorrow(Player attacker, LivingEntity defender) {
        if (getMainHandEnchantmentLevel(attacker, ModEnchantments.SNOW_SORROW) <= 0) return;
        long now = attacker.level().getGameTime();
        CompoundTag defData = getEntityData(defender);
        defData.putLong(KEY_WINTER_MARK_UNTIL, now + 200); // 冬痕 10 秒
        // 脚下生成细雪(空气处)
        var pos = defender.blockPosition();
        var below = pos;
        if (defender.level().getBlockState(below).isAir()) {
            defender.level().setBlock(below, Blocks.POWDER_SNOW.defaultBlockState(), 3);
        }
        // 与雪的伤同附魔: 强制雪天(server 下雨)
        if (getMainHandEnchantmentLevel(attacker, ModEnchantments.SNOW_WOUND) > 0) {
            if (attacker.level() instanceof ServerLevel serverLevel) {
                serverLevel.setWeatherParameters(0, 400, true, false);
            }
        }
    }

        private static float applyWinterMarkVulnerability(LivingEntity defender, LivingIncomingDamageEvent event) {
        // 已迁 winterMarkFactor(incoming_damage 通道); 保留文档对照
        return event.getAmount();
    }

    // --- 75. "唯有命运...": 装备无法破坏; 生命不低于1; 伤害+500% 且类型为真实伤害 ---
    // 注: 攻击×6 + 真实伤害由 WeepingFireHelper(伤害类型转换 TRUE_DAMAGE 分支)实现,
    //     属伤害类型通道而非数值属性, 不迁 bonus/mult; 本文件仅保留生命/免疫逻辑。
    private static boolean wearsUnyieldingFate(LivingEntity entity) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (getSlotEnchantmentLevel(entity, ModEnchantments.UNYIELDING_FATE, slot) > 0) return true;
        }
        return false;
    }

    private static void applyUnyieldingFateInvuln(LivingEntity defender, LivingIncomingDamageEvent event) {
        if (!wearsUnyieldingFate(defender)) return;
        // 生命不会低于 1(允许扣到剩 1)
        if (defender.getHealth() - event.getAmount() <= 0) {
            event.setAmount(defender.getHealth() - 1.0f);
        }
    }

    // --- 76. 泰坦: 对精英/BOSS 额外一次等额伤害(×2 已迁事件乘伤通道, 判定在 common EventDamageConditions) ---

    /** 泰坦: 精英/BOSS 判定已下沉 common(EventDamageConditions.isEliteOrBoss)。 */

    /**
     * 已迁入事件乘伤通道(Q9 试点): 见 computeEventConditionalMultiplier —— 打精英/BOSS 时
     * 由 onLivingDamage 在统一结算前以临时 modifier 乘入 damage_multiplier, 结算后清除。
     * 本函数保留原判定逻辑仅作文档对照, 不再于攻击链中调用。
     */
    // 旧链手写乘参考(applyTitan ×2)已随迁移删除; 判定在 common EventDamageConditions

    // --- 77. 锐意: 武器基础攻击(ATTACK_DAMAGE)临时+1/层, 10秒, 可叠加 ---
    private static final ResourceLocation KEEN_WILL_ATTACK_MODIFIER = rl("keen_will_attack");

    private static void applyKeenWill(Player attacker, CompoundTag data, int tickCount) {
        if (getMainHandEnchantmentLevel(attacker, ModEnchantments.KEEN_WILL) <= 0) return;
        // 攻击命中先叠一层, 武器基础攻击 +1(通过 ATTACK_DAMAGE 属性体现, 见 tickKeenWill)
        gainKeenStack(attacker, data, tickCount);
        // 立刻同步属性(本次后续结算即可读到)
        tickKeenWill(attacker, data, tickCount);
    }

    private static void tickKeenWill(Player player, CompoundTag data, int tickCount) {
        if (getMainHandEnchantmentLevel(player, ModEnchantments.KEEN_WILL) <= 0) {
            if (data.getInt(KEY_KEEN_STACKS) != 0) {
                data.remove(KEY_KEEN_STACKS);
            }
            setTransient(player, Attributes.ATTACK_DAMAGE, KEEN_WILL_ATTACK_MODIFIER, 0,
                    AttributeModifier.Operation.ADD_VALUE);
            return;
        }
        // 10 秒(200 tick)无新攻击则清空
        int lastTick = data.getInt(KEY_KEEN_LAST);
        int stacks = data.getInt(KEY_KEEN_STACKS);
        if (stacks > 0 && tickCount - lastTick > 200) {
            data.remove(KEY_KEEN_STACKS);
            stacks = 0;
        }
        // 武器基础攻击力 +每层1(这是加在原版攻击伤害属性上的"面板+1")
        setTransient(player, Attributes.ATTACK_DAMAGE, KEEN_WILL_ATTACK_MODIFIER, stacks,
                AttributeModifier.Operation.ADD_VALUE);
        if (LOGGER.isDebugEnabled() && stacks > 0) {
            LOGGER.debug("[KeenWill] stacks={}, ATTACK_DAMAGE now={}", stacks,
                    player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        }
    }

    private static void gainKeenStack(Player player, CompoundTag data, int tickCount) {
        if (getMainHandEnchantmentLevel(player, ModEnchantments.KEEN_WILL) <= 0) return;
        int stacks = data.getInt(KEY_KEEN_STACKS) + 1;
        if (stacks > 20) stacks = 20; // 防止无限膨胀
        data.putInt(KEY_KEEN_STACKS, stacks);
        data.putInt(KEY_KEEN_LAST, tickCount);
    }

    // --- 78. 锋化: 攻击获得武器耐久%×0.4 的护甲撕裂(ARMOR_PIERCE 属性) ---
    private static void tickSharpen(Player player) {
        ItemStack weapon = player.getMainHandItem();
        int level = getMainHandEnchantmentLevel(player, ModEnchantments.SHARPEN);
        double pierce = 0.0;
        if (level > 0 && weapon.isDamageableItem() && weapon.getMaxDamage() > 0) {
            double fraction = (double) (weapon.getMaxDamage() - weapon.getDamageValue()) / weapon.getMaxDamage();
            pierce = fraction * 0.4;
        }
        setTransient(player, ALObjects.Attributes.ARMOR_PIERCE, rl("sharpen_pierce"), pierce, AttributeModifier.Operation.ADD_VALUE);
    }

    // --- 79. 超忆症: 获得"身上盔甲保护+耐久附魔等级之和"×1 的最大生命值(不可重复叠加) ---
    private static void tickHyperthymesia(Player player) {
        if (getEnchantmentLevel(player, ModEnchantments.HYPERTHYMESIA) <= 0) {
            setTransient(player, Attributes.MAX_HEALTH, rl("hyperthymesia_hp"), 0, AttributeModifier.Operation.ADD_VALUE);
            return;
        }
        int sum = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack armor = player.getItemBySlot(slot);
            if (armor.isEmpty()) continue;
            var ench = armor.get(DataComponents.ENCHANTMENTS);
            if (ench == null) continue;
            for (var entry : ench.entrySet()) {
                ResourceKey<Enchantment> key = entry.getKey().unwrapKey().orElse(null);
                if (key == null) continue;
                String path = key.location().getPath();
                // 保护 / 耐久类附魔(含原版 protection 各系与 unbreaking)
                if (path.startsWith("protection") || path.equals("unbreaking")
                        || path.equals("fire_protection") || path.equals("blast_protection")
                        || path.equals("projectile_protection") || path.equals("feather_falling")) {
                    sum += entry.getIntValue();
                }
            }
        }
        setTransient(player, Attributes.MAX_HEALTH, rl("hyperthymesia_hp"), sum, AttributeModifier.Operation.ADD_VALUE);
    }

    // --- 80. 千万年永恒屹立: 耐久消耗 -80%; 每秒回复最多耐久值 2% ---
    private static void tickEternalStanding(Player player) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            if (stack.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.ETERNAL_STANDING)) <= 0) continue;
            if (!stack.isDamageableItem() || stack.getDamageValue() <= 0) continue;
            int max = stack.getMaxDamage();
            if (max <= 0) continue;
            if (player.tickCount % 20 == 0) { // 每秒
                int repair = Math.max(1, (int) Math.ceil(max * 0.02));
                stack.setDamageValue(Math.max(0, stack.getDamageValue() - repair));
            }
        }
    }

    // --- 81. 铸就全一城盾: 盾挡后控住来源并回复 ---
    private static void onCityShieldBlock(LivingShieldBlockEvent event) {
        LivingEntity defender = event.getEntity();
        if (defender.level().isClientSide()) return;
        if (!event.getBlocked()) return;
        ItemStack shield = defender.getUseItem();
        if (shield.isEmpty() || !shield.is(Items.SHIELD)) return;
        if (shield.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.CITY_SHIELD)) <= 0) return;
        Entity src = event.getDamageSource().getEntity();
        if (src instanceof LivingEntity attacker) {
            attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 4));
            attacker.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20, 0));
            attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20, 2));
            attacker.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 20, 0));
        }
        float blocked = event.getBlockedDamage();
        if (blocked > 0 && defender.isAlive()) defender.heal(blocked);
    }

    // --- 82. 目灯: 受到攻击时, 使对方一半的增伤和暴击伤害无效 3 秒 ---
    // 严格实现(用户口径): 受击时给攻击者挂 3 秒"眩惑"——攻击者三个增伤相关属性
    // 全部 ×0.5: CRIT_DAMAGE ×0.5、bonus_damage(加伤)×0.5、damage_multiplier(乘伤)×0.5。
    private static final ResourceLocation EYE_LAMP_CRIT_HALF = rl("eye_lamp_crit_half");
    private static final ResourceLocation EYE_LAMP_BONUS_HALF = rl("eye_lamp_bonus_half");
    private static final ResourceLocation EYE_LAMP_MULT_HALF = rl("eye_lamp_mult_half");

    private static void applyEyeLampMark(LivingEntity defender, LivingIncomingDamageEvent event) {
        if (getEnchantmentLevel(defender, ModEnchantments.EYE_LAMP) <= 0) return;
        if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != defender) {
            long now = attacker.level().getGameTime();
            CompoundTag atkData = getEntityData(attacker);
            atkData.putLong(KEY_EYE_LAMP_MARK, now + 60);
            applyHalfMultiplier(attacker, ALObjects.Attributes.CRIT_DAMAGE, EYE_LAMP_CRIT_HALF);
            applyHalfMultiplier(attacker, com.zhonz.moreenchantments.attribute.ZhonzAttributes.BONUS_DAMAGE, EYE_LAMP_BONUS_HALF);
            applyHalfMultiplier(attacker, com.zhonz.moreenchantments.attribute.ZhonzAttributes.DAMAGE_MULTIPLIER, EYE_LAMP_MULT_HALF);
            // 3 秒后移除
            if (attacker.level() instanceof ServerLevel serverLevel) {
                serverLevel.getServer().tell(new net.minecraft.server.TickTask(61, () -> {
                    if (attacker.isAlive()) {
                        removeHalfMultiplier(attacker, ALObjects.Attributes.CRIT_DAMAGE, EYE_LAMP_CRIT_HALF);
                        removeHalfMultiplier(attacker, com.zhonz.moreenchantments.attribute.ZhonzAttributes.BONUS_DAMAGE, EYE_LAMP_BONUS_HALF);
                        removeHalfMultiplier(attacker, com.zhonz.moreenchantments.attribute.ZhonzAttributes.DAMAGE_MULTIPLIER, EYE_LAMP_MULT_HALF);
                    }
                }));
            }
        }
    }

    private static void applyHalfMultiplier(LivingEntity entity, Holder<Attribute> attr, ResourceLocation id) {
        var inst = entity.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        inst.addTransientModifier(new AttributeModifier(id, -0.5D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void removeHalfMultiplier(LivingEntity entity, Holder<Attribute> attr, ResourceLocation id) {
        var inst = entity.getAttribute(attr);
        if (inst != null) inst.removeModifier(id);
    }

    // --- 83. 庄严哀悼: 远程命中黑白粒子; 蓄力 +50%; 命中击杀对半径3格造成一次等额伤害 ---
    private static void tickSolemnMourning(Player player) {
        int lv = getMainHandEnchantmentLevel(player, ModEnchantments.SOLEMN_MOURNING);
        double draw = lv > 0 ? 0.5 : 0;
        setTransient(player, ALObjects.Attributes.DRAW_SPEED, rl("solemn_draw"), draw, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static void recordMourningDamage(LivingEntity attacker, float amount) {
        if (getMainHandEnchantmentLevel(attacker, ModEnchantments.SOLEMN_MOURNING) <= 0) return;
        getEntityData(attacker).putFloat(KEY_MOURNING_LAST, amount);
    }

    // --- 84. 粉碎: 每次攻击获得 4% 保护撕裂(PROT_PIERCE), 5秒可叠加刷新 ---
    private static void gainShatterStack(Player player, CompoundTag data, int tickCount) {
        if (getMainHandEnchantmentLevel(player, ModEnchantments.SHATTER) <= 0) return;
        int stacks = data.getInt(KEY_SHATTER_STACKS) + 1;
        if (stacks > 10) stacks = 10;
        data.putInt(KEY_SHATTER_STACKS, stacks);
        data.putInt(KEY_SHATTER_LAST, tickCount);
    }

    private static void tickShatter(Player player, CompoundTag data, int tickCount) {
        int stacks = data.getInt(KEY_SHATTER_STACKS);
        if (stacks <= 0) return;
        if (tickCount - data.getInt(KEY_SHATTER_LAST) > 100) { // 5秒过期
            data.remove(KEY_SHATTER_STACKS);
            stacks = 0;
        }
        setTransient(player, ALObjects.Attributes.PROT_PIERCE, rl("shatter_pierce"),
                stacks * 0.04, AttributeModifier.Operation.ADD_VALUE);
    }

    // --- 85/86. 他乡客 & 远行客 ---
    private static void tickSojourner(Player player) {
        boolean soj = getSlotEnchantmentLevel(player, ModEnchantments.SOJOURNER, EquipmentSlot.FEET) > 0;
        boolean way = getSlotEnchantmentLevel(player, ModEnchantments.WAYFARER, EquipmentSlot.FEET) > 0;
        if (soj) {
            double atkSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
            double mult = way ? 1.0 : 0.5;
            setTransient(player, Attributes.MOVEMENT_SPEED, rl("sojourner_move"),
                    atkSpeed * mult, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        } else {
            setTransient(player, Attributes.MOVEMENT_SPEED, rl("sojourner_move"), 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
        if (way) {
            player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 40, 0, false, false));
            if (soj) {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 1, false, false));
            }
        }
    }

    // --- 87. 三千万转: 右键已放置的龙蛋(手持带附魔的下界之星) → 永劫回归书 ---
    private static void onThirtyMillionTurns(Player player, ItemStack star) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        var ench = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        ench.set(ModEnchantments.getHolder(ModEnchantments.ETERNAL_RETURN), 1);
        book.set(DataComponents.ENCHANTMENTS, ench.toImmutable());
        player.getInventory().placeItemBackInInventory(book);
        // 不消耗星: 附魔还在星上, 每颗星只能反复用(视为"让龙蛋转出书")
        player.getPersistentData().putLong("zhonz_turns_cd", player.level().getGameTime());
    }

    // --- 88. 天之锁: 远程命中 25% 概率定身禁攻, 间隔 <=10s 则时长减半 ---
    private static void tryHeavenChain(LivingEntity defender, DamageSource source) {
        if (!source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) return;
        Entity src = source.getEntity();
        if (!(src instanceof LivingEntity attacker)) return;
        if (getMainHandEnchantmentLevel(attacker, ModEnchantments.HEAVEN_CHAIN) <= 0) return;
        CompoundTag atkData = getEntityData(attacker);
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
}
