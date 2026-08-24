package com.zhonz.moreenchantments.event;

import com.zhonz.moreenchantments.command.ModTestCommands;
import com.zhonz.moreenchantments.enchantment.ModEnchantments;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
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
    private static final String BLOOD_PATH_TAG = "zhonz_blood_path_kills"; // NBT CompoundTag on weapon, keys = mob type IDs, values = kill counts (int)

    // ===== Attribute Modifier ResourceLocations =====
    private static final String MOD_ID = "zhonz_more_enchantments";
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

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    // ===== Registration =====

    public static void register() {
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

            // --- 32. 必须开辟的通路 Must Open Path: 1000% damage + teleport (5s cd) ---
            if (!tryMustOpenPath(attacker, defender, rawDamage, event)) return;

            // --- 3. 收割 Harvest: instant kill if post-damage HP <= 10%/20%/30% ---
            if (!tryHarvest(attacker, defender, source, rawDamage, event)) return;
        }

        // --- 33. 不完整的预知眼 Incomplete Foreknowledge Eye: dodge incoming attack ---
        if (!tryForeknowledgeDodge(defender, event)) {
            // 倏忽恩赐: fall through only when foreknowledge eye did not trigger
            recordFleetingGrace(defender, rawDamage);
        }
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

    private static boolean tryMustOpenPath(LivingEntity attacker, LivingEntity defender,
                                           float rawDamage, LivingIncomingDamageEvent event) {
        int mustOpenPathLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.MUST_OPEN_PATH);
        if (mustOpenPathLevel <= 0) return true;

        CompoundTag data = getEntityData(attacker);
        if (data.getInt(KEY_MUST_OPEN_PATH_CD) > 0) return true;

        float newDamage = rawDamage * 10.0f;
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[MustOpenPath] Triggered! Dealing {} damage directly", newDamage);
        }
        event.setCanceled(true);
        attacker.teleportTo(defender.getX(), defender.getY(), defender.getZ());
        data.putInt(KEY_MUST_OPEN_PATH_CD, 100);

        float newHealth = Math.max(0, defender.getHealth() - newDamage);
        defender.setHealth(newHealth);
        if (newHealth <= 0) {
            defender.kill();
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

    private static boolean tryForeknowledgeDodge(LivingEntity defender, LivingIncomingDamageEvent event) {
        if (getSlotEnchantmentLevel(defender, ModEnchantments.INCOMPLETE_FOREKNOWLEDGE_EYE, EquipmentSlot.HEAD) <= 0) {
            return false;
        }
        CompoundTag data = getEntityData(defender);
        float dodgeProb = data.contains(KEY_FOREKNOWLEDGE_DODGE) ? data.getFloat(KEY_FOREKNOWLEDGE_DODGE) : 0.80f;
        data.putLong(KEY_FOREKNOWLEDGE_LAST_COMBAT, defender.level().getGameTime());

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[ForeknowledgeEye] Check dodgeProb={}, entity={}", dodgeProb, defender.getName().getString());
        }

        if (RANDOM.nextFloat() >= dodgeProb) {
            float newProb = Math.max(0.05f, dodgeProb - 0.10f);
            data.putFloat(KEY_FOREKNOWLEDGE_DODGE, newProb);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[ForeknowledgeEye] Failed dodge! New prob={}", newProb);
            }
            return false;
        }

        // Successful dodge: short random teleport + cancel damage
        float angle = RANDOM.nextFloat() * 2.0f * (float) Math.PI;
        defender.teleportTo(defender.getX() + Math.cos(angle) * 0.25, defender.getY(),
                defender.getZ() + Math.sin(angle) * 0.25);
        event.setCanceled(true);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[ForeknowledgeEye] Dodged attack! dodgeProb={}", dodgeProb);
        }
        return true;
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
            amount = applyAttackerEnchantments(attacker, defender, source, amount);
        }
        amount = applyDefenderEnchantments(defender, source, amount);

        // My Sea Domain vulnerability stacks onto the defender for 60s
        amount = applyMySeaDomainVulnerability(defender, amount);

        // Prophet's Call: +170% damage to currently-marked hostiles
        amount = applyProphetsCallBonus(defender, amount);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[DamageEvent] Final amount={} (original={})", amount, event.getOriginalDamage());
        }
        event.setNewDamage(amount);
    }

    private static float applyAttackerEnchantments(LivingEntity attacker, LivingEntity defender,
                                                   DamageSource source, float amount) {
        ItemStack mainHand = attacker.getMainHandItem();

        // NOTE: 终结/必须开辟的通路/收割 handled in onLivingHurt
        // NOTE: 挂 不在攻击事件中处理任何额外伤害

        amount = applySanction(attacker, defender, amount);
        amount = applyCharger(attacker, amount);
        amount = applyLiberator(attacker, amount);
        amount = applySupremeArtDamage(attacker, amount);
        amount = applyArmyBreaker(attacker, defender, amount);
        amount = applyMySeaDomainDamage(attacker, defender, amount);
        applyAreaStrike(attacker, defender, source, mainHand, amount);
        applySelfDoubt(attacker, defender);
        amount = applyBloodWeep(attacker, amount);
        applyShellStrip(attacker, defender, amount);
        applySuppression(attacker, defender);
        applyExplosiveDawn(attacker, defender, source, mainHand, amount);
        amount = applyFoolsMask(attacker, defender, amount);
        amount = applyFleetingGraceBonus(attacker, amount);
        applyFlippingCoin(attacker, defender);
        applyGrievousWound(attacker, defender);
        amount = applyBloodPathBonus(attacker, defender, amount);
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

    private static float applyCharger(LivingEntity attacker, float amount) {
        int chargerLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.CHARGER);
        if (chargerLevel <= 0) return amount;
        // Normal walking ~0.1 blocks/tick, sprinting ~0.13. Linear bonus around that.
        float speedBonus = (float) (attacker.getDeltaMovement().horizontalDistance() / 0.1) * chargerLevel * 0.5f;
        return amount * (1.0f + speedBonus);
    }

    private static float applyLiberator(LivingEntity attacker, float amount) {
        int liberatorLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.LIBERATOR);
        if (liberatorLevel <= 0) return amount;
        // Use EntityDataStorage WeakHashMap instead of persistent data to avoid stale values
        // from singleton FakePlayer across test runs.
        CompoundTag data = EntityDataStorage.getData(attacker);
        long currentTick = attacker.level().getGameTime();
        long lastAttackTick = data.getLong(KEY_LIBERATOR_LAST_ATTACK);
        // If never attacked before (e.g. just equipped), treat as 0 seconds elapsed
        long elapsedTicks = lastAttackTick == 0 ? 0 : currentTick - lastAttackTick;
        double elapsedSeconds = elapsedTicks / 20.0;

        float multiplier;
        if (elapsedSeconds <= 5.0) {
            multiplier = 0.1f;
        } else if (elapsedSeconds >= 400.0) {
            multiplier = 20.0f;
        } else {
            multiplier = 0.1f + (20.0f - 0.1f) * (float) ((elapsedSeconds - 5.0) / (400.0 - 5.0));
        }
        data.putLong(KEY_LIBERATOR_LAST_ATTACK, currentTick);
        // Always log at INFO so we can see this in production logs
        LOGGER.info("[Liberator] attacker={}, lastTick={}, currTick={}, elapsed={}s, multiplier={}, amount={} -> {}",
                attacker.getName().getString(), lastAttackTick, currentTick,
                String.format("%.1f", elapsedSeconds), multiplier, amount, amount * multiplier);
        return amount * multiplier;
    }

    private static float applySupremeArtDamage(LivingEntity attacker, float amount) {
        int supremeArtLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.SUPREME_ART);
        return supremeArtLevel > 0 ? amount * (1.0f + 0.2f * supremeArtLevel) : amount;
    }

    private static float applyArmyBreaker(LivingEntity attacker, LivingEntity defender, float amount) {
        int armyBreakerLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.ARMY_BREAKER);
        if (armyBreakerLevel <= 0) return amount;
        float threshold = armyBreakerLevel == 1 ? 0.30f : (armyBreakerLevel == 2 ? 0.40f : 0.50f);
        float bonus = armyBreakerLevel == 1 ? 0.10f : (armyBreakerLevel == 2 ? 0.20f : 0.30f);
        if (defender.getHealth() <= defender.getMaxHealth() * threshold) {
            return amount * (1.0f + bonus);
        }
        return amount;
    }

    private static float applyMySeaDomainDamage(LivingEntity attacker, LivingEntity defender, float amount) {
        if (getMainHandEnchantmentLevel(attacker, ModEnchantments.MY_SEA_DOMAIN) <= 0) return amount;
        if (attacker.getMainHandItem().getItem() != Items.TRIDENT) return amount;
        getEntityData(defender).putLong(KEY_MY_SEA_DOMAIN_START, defender.level().getGameTime());
        return amount * 1.6f;
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

    private static float applyBloodWeep(LivingEntity attacker, float amount) {
        int bloodWeepLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.BLOOD_WEEP);
        if (bloodWeepLevel <= 0) return amount;
        float hpCost = bloodWeepLevel == 1 ? 10.0f : (bloodWeepLevel == 2 ? 7.0f : 4.0f);
        float damageBonus = bloodWeepLevel == 1 ? 0.20f : (bloodWeepLevel == 2 ? 0.30f : 0.45f);
        attacker.setHealth(Math.max(0, attacker.getHealth() - hpCost));
        return amount * (1.0f + damageBonus);
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
        for (LivingEntity nearby : defender.level().getEntitiesOfClass(LivingEntity.class,
                defender.getBoundingBox().inflate(splashRadius))) {
            if (nearby == defender || nearby == attacker || !nearby.isAlive() || !attacker.canAttack(nearby)) {
                continue;
            }
            float falloff = (float) Math.max(0, 1.0 - (nearby.distanceTo(defender) / splashRadius));
            nearby.hurt(source, splashDamage * falloff);
        }
        getEntityData(attacker).putBoolean(KEY_EXPLOSIVE_DAWN_RELOADING, true);
    }

    private static float applyFoolsMask(LivingEntity attacker, LivingEntity defender, float amount) {
        if (getEnchantmentLevel(defender, ModEnchantments.FOOLS_MASK) <= 0) return amount;
        CompoundTag data = getEntityData(defender);
        if (data.getBoolean(KEY_FOOLS_MASK_LUCKY)) {
            applyRandomBuff(defender);
            float multiplier = 1.0f + RANDOM.nextFloat() * RANDOM.nextFloat() * 2.0f;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[FoolsMask] Lucky! Damage x{}", String.format("%.2f", multiplier));
            }
            return amount * multiplier;
        } else {
            applyRandomDebuff(defender);
            float multiplier = Math.max(0.01f, 1.0f - RANDOM.nextFloat() * RANDOM.nextFloat() * 0.99f);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[FoolsMask] Unlucky! Damage x{}", String.format("%.2f", multiplier));
            }
            return amount * multiplier;
        }
    }

    private static float applyFleetingGraceBonus(LivingEntity attacker, float amount) {
        if (getEnchantmentLevel(attacker, ModEnchantments.FLEETING_GRACE) <= 0) return amount;
        CompoundTag data = getEntityData(attacker);
        float stored = data.getFloat(KEY_FLEETING_GRACE_STORED);
        if (stored <= 0) return amount;
        data.putFloat(KEY_FLEETING_GRACE_STORED, 0);
        return amount + stored * 2.0f;
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
        // Wither II for 5s = ~30% healing reduction. Actual reduction is enforced by LivingEntityHealMixin.
        getEntityData(defender).putLong(KEY_GRIEVOUS_WOUND_UNTIL, defender.level().getGameTime() + 100);
        defender.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
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

    private static float applyDefenderEnchantments(LivingEntity defender, DamageSource source, float amount) {
        Entity attackerEntity = source.getEntity();
        applyDeepSeasGrace(defender, amount);
        applyGemUmbrella(defender, attackerEntity);
        applyFishballTransfer(defender, source, amount); // adjusts `amount` via local variable
        applyToughnessShield(defender);
        applyEmergencyRescue(defender, amount);
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

    private static float applyMySeaDomainVulnerability(LivingEntity defender, float amount) {
        CompoundTag targetData = getEntityData(defender);
        if (!targetData.contains(KEY_MY_SEA_DOMAIN_START)) return amount;

        long elapsed = defender.level().getGameTime() - targetData.getLong(KEY_MY_SEA_DOMAIN_START);
        if (elapsed > 1200) {
            targetData.remove(KEY_MY_SEA_DOMAIN_START);
            return amount;
        }

        float vulnerability;
        if (elapsed < 200) {
            vulnerability = 0.30f;
        } else if (elapsed < 600) {
            vulnerability = 0.30f + 0.30f * ((float) (elapsed - 200) / 400f);
        } else {
            vulnerability = 0.60f;
        }
        return amount * (1.0f + vulnerability);
    }

    private static float applyProphetsCallBonus(LivingEntity defender, float amount) {
        CompoundTag data = getEntityData(defender);
        if (!data.getBoolean(KEY_PROPHETS_CALL_ACTIVE)) return amount;
        if (defender.level().getGameTime() >= data.getLong(KEY_PROPHETS_CALL_UNTIL)) {
            data.remove(KEY_PROPHETS_CALL_ACTIVE);
            data.remove(KEY_PROPHETS_CALL_UNTIL);
            return amount;
        }
        return amount * 2.7f; // 170% more = 2.7x
    }

    // ===================================================================
    // LivingDeathEvent - Death prevention (Return from Hell, Divine Protection)
    // ===================================================================

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;

        recordBloodPathKill(event.getSource(), entity);

        if (tryReturnFromHell(entity, event)) {
            return;
        }
        tryDivineProtection(entity, event);
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
        // Only re-apply attribute modifiers when the level changes
        int level = getEnchantmentLevel(player, ModEnchantments.SUPREME_ART);
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
        // Status approximations for effects without an attribute
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 3, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 3, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false));
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
        if (getSlotEnchantmentLevel(player, ModEnchantments.INCOMPLETE_FOREKNOWLEDGE_EYE, EquipmentSlot.HEAD) <= 0) return;

        CompoundTag data = getEntityData(player);
        long lastCombat = data.getLong(KEY_FOREKNOWLEDGE_LAST_COMBAT);
        // Recover 1% per second after 80s of no combat
        if (player.level().getGameTime() - lastCombat > 1600) {
            float prob = data.getFloat(KEY_FOREKNOWLEDGE_DODGE);
            if (prob < 0.80f) {
                data.putFloat(KEY_FOREKNOWLEDGE_DODGE, Math.min(0.80f, prob + 0.01f));
            }
        }
        // Apply Nausea at low dodge chance so vision blurs
        float prob = data.getFloat(KEY_FOREKNOWLEDGE_DODGE);
        if (prob < 0.20f && tickCount % 40 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 40, 0, false, false));
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
}
