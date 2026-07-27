package com.zhonz.moreenchantments.event;

import com.zhonz.moreenchantments.command.ModTestCommands;
import com.zhonz.moreenchantments.enchantment.ModEnchantments;
import net.minecraft.core.Holder;
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
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ModEventHandlers {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("ZhonzMoreEnchantments");
    private static final Random RANDOM = new Random();

    // ===== Persistent Data Keys =====
    private static final String KEY_LIBERATOR_LAST_ATTACK = "zhonz_liberator_last_attack";
    private static final String KEY_FLEETING_GRACE_STORED = "zhonz_fleeting_grace_stored";
    private static final String KEY_SHELL_STRIP_RAW = "zhonz_shell_strip_raw";
    private static final String KEY_MY_SEA_DOMAIN_TICK = "zhonz_my_sea_domain_tick";
    private static final String KEY_MY_SEA_DOMAIN_START = "zhonz_my_sea_domain_start";
    private static final String KEY_GRIEVOUS_WOUND_UNTIL = "zhonz_grievous_wound_until";
    private static final String KEY_FOOLS_MASK_LUCKY = "zhonz_fools_mask_lucky";
    private static final String KEY_FOOLS_MASK_CHANGE_TICK = "zhonz_fools_mask_change_tick";
    private static final String KEY_RETURN_FROM_HELL_CD = "zhonz_return_from_hell_cd";
    private static final String KEY_MUST_OPEN_PATH_CD = "zhonz_must_open_path_cd";
    private static final String KEY_FLIPPING_COIN_ATTACK_STACKS = "zhonz_flipping_coin_attack_stacks";
    private static final String KEY_FLIPPING_COIN_TARGET_STACKS = "zhonz_flipping_coin_target_stacks";
    private static final String KEY_EMERGENCY_RESCUE_CD = "zhonz_emergency_rescue_cd";
    private static final String KEY_DIVINE_CURSE_DURABILITY_TICK = "zhonz_divine_curse_durability_tick";
    private static final String KEY_EXPLOSIVE_DAWN_RELOADING = "zhonz_explosive_dawn_reloading";
    private static final String KEY_PROPHETS_CALL_ACTIVE = "zhonz_prophets_call_active";
    private static final String KEY_PROPHETS_CALL_UNTIL = "zhonz_prophets_call_until";
    private static final String KEY_SUPREME_ART_LAST_LEVEL = "zhonz_supreme_art_last_level";
    private static final String KEY_FOREKNOWLEDGE_DODGE = "zhonz_foreknowledge_dodge_prob";
    private static final String KEY_FOREKNOWLEDGE_LAST_COMBAT = "zhonz_foreknowledge_last_combat";

    // ===== Attribute Modifier ResourceLocations =====
    private static final ResourceLocation SUPREME_ART_RANGE_MODIFIER = ResourceLocation.fromNamespaceAndPath("zhonz_more_enchantments", "supreme_art_range");
    private static final ResourceLocation SUPREME_ART_ATTACK_SPEED_MODIFIER = ResourceLocation.fromNamespaceAndPath("zhonz_more_enchantments", "supreme_art_attack_speed");
    private static final ResourceLocation DIVINE_CURSE_DAMAGE_MODIFIER = ResourceLocation.fromNamespaceAndPath("zhonz_more_enchantments", "divine_curse_damage");
    private static final ResourceLocation DIVINE_CURSE_ATTACK_SPEED_MODIFIER = ResourceLocation.fromNamespaceAndPath("zhonz_more_enchantments", "divine_curse_attack_speed");
    private static final ResourceLocation TOUGHNESS_ARMOR_MODIFIER = ResourceLocation.fromNamespaceAndPath("zhonz_more_enchantments", "toughness_armor");
    private static final ResourceLocation TOUGHNESS_TOUGHNESS_MODIFIER = ResourceLocation.fromNamespaceAndPath("zhonz_more_enchantments", "toughness_toughness");
    private static final ResourceLocation FLIPPING_COIN_MAX_HP = ResourceLocation.fromNamespaceAndPath("zhonz_more_enchantments", "flipping_coin_max_hp");
    private static final ResourceLocation FLIPPING_COIN_TARGET_MAX_HP = ResourceLocation.fromNamespaceAndPath("zhonz_more_enchantments", "flipping_coin_target_max_hp");
    private static final ResourceLocation DIVINE_CURSE_RANGE_MODIFIER = ResourceLocation.fromNamespaceAndPath("zhonz_more_enchantments", "divine_curse_range");
    private static final ResourceLocation DIVINE_CURSE_BLOCK_RANGE_MODIFIER = ResourceLocation.fromNamespaceAndPath("zhonz_more_enchantments", "divine_curse_block_range");

    // ===== Registration =====

    public static void register() {
        NeoForge.EVENT_BUS.register(ModEventHandlers.class);
        NeoForge.EVENT_BUS.register(ModTestCommands.class);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ModTestCommands.register(event.getDispatcher());
    }

    // ===== Utility Methods =====

    private static int getEnchantmentLevel(LivingEntity entity, ResourceKey<Enchantment> enchantment) {
        Holder<Enchantment> holder = ModEnchantments.getHolder(enchantment);
        int level = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = entity.getItemBySlot(slot);
            int enchantLevel = stack.getEnchantmentLevel(holder);
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
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getEnchantmentLevel(holder) > 0) {
                return true;
            }
        }
        return false;
    }

    private static List<LivingEntity> getNearbyEnemies(LivingEntity center, double radius) {
        Level level = center.level();
        AABB box = center.getBoundingBox().inflate(radius);
        List<LivingEntity> enemies = new ArrayList<>();
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (living != center && living.isAlive() && center.canAttack(living)) {
                enemies.add(living);
            }
        }
        return enemies;
    }

    private static List<LivingEntity> getNearbySameType(LivingEntity center, double radius) {
        Level level = center.level();
        AABB box = center.getBoundingBox().inflate(radius);
        EntityType<?> type = center.getType();
        List<LivingEntity> result = new ArrayList<>();
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box)) {
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

    private static boolean isIllager(LivingEntity entity) {
        return entity.getType() == EntityType.EVOKER
                || entity.getType() == EntityType.VINDICATOR
                || entity.getType() == EntityType.PILLAGER
                || entity.getType() == EntityType.ILLUSIONER
                || entity.getType() == EntityType.RAVAGER;
    }

    private static void removeEffects(LivingEntity entity, boolean beneficial) {
        // Use iterator to avoid intermediate list allocation
        for (var it = entity.getActiveEffects().iterator(); it.hasNext(); ) {
            MobEffectInstance effect = it.next();
            if (effect.getEffect().value().isBeneficial() == beneficial) {
                entity.removeEffect(effect.getEffect());
            }
        }
    }

    private static void removeDebuffs(LivingEntity entity) {
        removeEffects(entity, false);
    }

    private static void removeBeneficialEffects(LivingEntity entity) {
        removeEffects(entity, true);
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

    // ===== AnvilUpdateEvent - Too Expensive Fix =====

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        // Note: To fully disable "Too Expensive!" in anvils, a Mixin on AnvilMenu
        // is recommended (e.g., @ModifyConstant to change the 40 threshold to Integer.MAX_VALUE).
        // The AnvilUpdateEvent fires before vanilla computation, so we cannot directly
        // modify the vanilla-computed cost here.
        // When this mod is installed, anvil costs should never be "Too Expensive!"
        // See the companion Mixin class AnvilMenuMixin for the proper fix.
    }

    // ===== LivingHurtEvent - Pre-Armor Damage Recording =====

    // 防止递归调用的标志
    private static boolean isProcessingCustomDamage = false;

    @SubscribeEvent
    public static void onLivingHurt(LivingIncomingDamageEvent event) {
        LivingEntity defender = event.getEntity();
        if (defender.level().isClientSide()) return;
        // 防止递归：如果是我们自己调用的hurt()，跳过附魔处理
        if (isProcessingCustomDamage) return;

        DamageSource source = event.getSource();
        Entity attackerEntity = source.getEntity();
        float rawDamage = event.getAmount();

        // === 18. 剥壳 Shell Strip: Record pre-armor damage if attacker has Shell Strip ===
        if (attackerEntity instanceof LivingEntity attacker) {
            int shellStripAttackerLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.SHELL_STRIP);
            if (shellStripAttackerLevel > 0) {
                CompoundTag defenderData = getEntityData(defender);
                defenderData.putFloat(KEY_SHELL_STRIP_RAW, rawDamage);
            }

            // === 1. 终结 Finale: If melee weapon attack damage >= 7, deal 100000x damage ===
            int finaleLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.FINALE);
            if (finaleLevel > 0) {
                ItemStack mainHand = attacker.getMainHandItem();
                if (isMeleeWeapon(mainHand)) {
                    double attackDamage = attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
                    if (attackDamage >= 7.0) {
                        LOGGER.debug("[Finale] Triggered! 100000x damage");
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
                        return;
                    }
                }
            }

            // === 32. 必须开辟的通路 Must Open Path: 1000% damage (pre-armor) ===
            int mustOpenPathLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.MUST_OPEN_PATH);
            if (mustOpenPathLevel > 0) {
                CompoundTag data = getEntityData(attacker);
                int cooldown = data.contains(KEY_MUST_OPEN_PATH_CD) ? data.getInt(KEY_MUST_OPEN_PATH_CD) : 0;
                if (cooldown <= 0) {
                    float newDamage = rawDamage * 10.0f;
                    LOGGER.debug("[MustOpenPath] Triggered! Dealing {} damage directly", newDamage);
                    event.setCanceled(true);
                    attacker.teleportTo(defender.getX(), defender.getY(), defender.getZ());
                    data.putInt(KEY_MUST_OPEN_PATH_CD, 100);
                    // 使用setHealth直接扣除血量，避免hurt()的问题
                    float newHealth = Math.max(0, defender.getHealth() - newDamage);
                    defender.setHealth(newHealth);
                    if (newHealth <= 0) {
                        defender.kill();
                    }
                    return;
                }
            }

            // === 3. 收割 Harvest: If target HP after damage <= threshold, instant kill ===
            int harvestLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.HARVEST);
            if (harvestLevel > 0) {
                float remainingHp = defender.getHealth() - rawDamage;
                float threshold = defender.getMaxHealth() * (0.1f * harvestLevel);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Harvest] Pre-armor check: harvestLevel={}, defenderHP={}, rawDamage={}, remainingHp={}, threshold={}",
                            harvestLevel, defender.getHealth(), rawDamage, remainingHp, threshold);
                }
                if (remainingHp > 0 && remainingHp <= threshold) {
                    LOGGER.debug("[Harvest] Triggered! Instant kill");
                    event.setCanceled(true);
                    defender.setHealth(0);
                    defender.die(source);
                    return;
                }
            }
        }

        // === 33. 不完整的预知眼 Incomplete Foreknowledge Eye: Dodge incoming attack ===
        {
            int foreknowledgeLevel = getSlotEnchantmentLevel(defender, ModEnchantments.INCOMPLETE_FOREKNOWLEDGE_EYE, EquipmentSlot.HEAD);
            if (foreknowledgeLevel > 0) {
                CompoundTag data = getEntityData(defender);
                float dodgeProb = data.contains(KEY_FOREKNOWLEDGE_DODGE) ? data.getFloat(KEY_FOREKNOWLEDGE_DODGE) : 0.80f;
                data.putLong(KEY_FOREKNOWLEDGE_LAST_COMBAT, defender.level().getGameTime());

                if (RANDOM.nextFloat() < dodgeProb) {
                    // Successful dodge: move 1/4 block in a random direction
                    float angle = RANDOM.nextFloat() * 2.0f * (float) Math.PI;
                    double offsetX = Math.cos(angle) * 0.25;
                    double offsetZ = Math.sin(angle) * 0.25;
                    defender.teleportTo(defender.getX() + offsetX, defender.getY(), defender.getZ() + offsetZ);
                    // Cancel the damage
                    event.setCanceled(true);
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[ForeknowledgeEye] Dodged attack! dodgeProb={}", dodgeProb);
                    }
                    return;
                } else {
                    // Failed dodge: reduce dodge probability by 10%
                    float newProb = Math.max(0.05f, dodgeProb - 0.10f);
                    data.putFloat(KEY_FOREKNOWLEDGE_DODGE, newProb);
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[ForeknowledgeEye] Failed dodge! New prob={}", newProb);
                    }
                }
            }
        }

        // === 29. 倏忽恩赐 Fleeting Grace: Record unmitigated damage for defender ===
        int fleetingGraceLevel = getEnchantmentLevel(defender, ModEnchantments.FLEETING_GRACE);
        if (fleetingGraceLevel > 0) {
            CompoundTag data = getEntityData(defender);
            float currentStored = data.contains(KEY_FLEETING_GRACE_STORED) ? data.getFloat(KEY_FLEETING_GRACE_STORED) : 0;
            data.putFloat(KEY_FLEETING_GRACE_STORED, currentStored + rawDamage);
        }
    }

    // ===== LivingDamageEvent - Final Damage Calculation =====

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        LivingEntity defender = event.getEntity();
        if (defender.level().isClientSide()) return;

        DamageSource source = event.getSource();
        Entity attackerEntity = source.getEntity();
        float amount = event.getNewDamage();

        // === Attacker-Side Effects (Outgoing Damage) ===
        if (attackerEntity instanceof LivingEntity attacker) {
            amount = applyAttackerEnchantments(attacker, defender, source, amount, event);
        }

        // === Defender-Side Effects (Incoming Damage) ===
        amount = applyDefenderEnchantments(defender, source, amount, event);

        // === Defender Vulnerability (My Sea Domain on target) ===
        {
            CompoundTag targetData = getEntityData(defender);
            if (targetData.contains(KEY_MY_SEA_DOMAIN_START)) {
                long startTick = targetData.getLong(KEY_MY_SEA_DOMAIN_START);
                long currentTick = defender.level().getGameTime();
                long elapsed = currentTick - startTick;
                // Expire after 60 seconds
                if (elapsed > 1200) {
                    targetData.remove(KEY_MY_SEA_DOMAIN_START);
                } else {
                    float vulnerability;
                    if (elapsed < 200) { // 0-10s: 30% more damage
                        vulnerability = 0.30f;
                    } else if (elapsed < 600) { // 10-30s: increasing from 30% to 60%
                        vulnerability = 0.30f + 0.30f * ((float)(elapsed - 200) / 400f);
                    } else { // 30s+: 60% more damage
                        vulnerability = 0.60f;
                    }
                    amount *= (1.0f + vulnerability);
                }
            }
        }

        // === Prophet's Call Damage Bonus: 170% more damage to glowing hostiles ===
        {
            CompoundTag defenderData = getEntityData(defender);
            if (defenderData.contains(KEY_PROPHETS_CALL_ACTIVE) && defenderData.getBoolean(KEY_PROPHETS_CALL_ACTIVE)) {
                long until = defenderData.contains(KEY_PROPHETS_CALL_UNTIL) ? defenderData.getLong(KEY_PROPHETS_CALL_UNTIL) : 0;
                if (defender.level().getGameTime() < until) {
                    amount *= 2.7f; // 170% more damage = 2.7x total
                } else {
                    defenderData.remove(KEY_PROPHETS_CALL_ACTIVE);
                    defenderData.remove(KEY_PROPHETS_CALL_UNTIL);
                }
            }
        }

        // === 17. 重伤 Grievous Wound: Reduce healing on target ===
        // Applied in attacker section above; healing reduction checked here for defender
        {
            CompoundTag defenderData = getEntityData(defender);
            if (defenderData.contains(KEY_GRIEVOUS_WOUND_UNTIL)) {
                long until = defenderData.getLong(KEY_GRIEVOUS_WOUND_UNTIL);
                if (defender.level().getGameTime() < until) {
                    // Target receives 30% less healing - reduce effective healing
                    // Note: Healing reduction requires a Mixin on LivingEntity.heal()
                    // or checking in PlayerTickEvent. Marked as needing Mixin support.
                } else {
                    defenderData.remove(KEY_GRIEVOUS_WOUND_UNTIL);
                }
            }
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[DamageEvent] Final amount={} (original={})", amount, event.getOriginalDamage());
        }
        event.setNewDamage(amount);
    }

    /**
     * Apply attacker-side enchantment effects (modify outgoing damage).
     */
    private static float applyAttackerEnchantments(LivingEntity attacker, LivingEntity defender, DamageSource source, float amount, LivingDamageEvent.Pre event) {
        ItemStack mainHand = attacker.getMainHandItem();

        // NOTE: 终结(Finale)、必须开辟的通路(MustOpenPath)和收割(Harvest)已在LivingIncomingDamageEvent中处理

        // --- 4. 制裁 Sanction: Deal 1%/2%/3% of target's MAX HP as TRUE damage (bypasses armor) ---
        int sanctionLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.SANCTION);
        if (sanctionLevel > 0) {
            float trueDamage = defender.getMaxHealth() * (0.01f * sanctionLevel);
            float newHp = Math.max(0, defender.getHealth() - trueDamage);
            defender.setHealth(newHp);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sanction] Dealt {} true damage (level={})", trueDamage, sanctionLevel);
            }
        }

        // --- 7. 冲锋手 Charger: Damage scales with movement speed ---
        int chargerLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.CHARGER);
        if (chargerLevel > 0) {
            double speed = attacker.getDeltaMovement().horizontalDistance();
            // Normal walking speed is ~0.1 blocks/tick; sprinting ~0.13
            // Bonus scales with speed: at normal speed (0.1), +50% per level; at sprint (0.13), +65% per level
            float speedBonus = (float) (speed / 0.1) * chargerLevel * 0.5f;
            amount *= (1.0f + speedBonus);
        }

        // --- 9. 解放者 Liberator: Longer since last attack = more damage ---
        int liberatorLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.LIBERATOR);
        if (liberatorLevel > 0) {
            CompoundTag data = getEntityData(attacker);
            long lastAttackTick = data.contains(KEY_LIBERATOR_LAST_ATTACK) ? data.getLong(KEY_LIBERATOR_LAST_ATTACK) : 0;
            long currentTick = attacker.level().getGameTime();
            long elapsedTicks = currentTick - lastAttackTick;
            double elapsedSeconds = elapsedTicks / 20.0;

            float multiplier;
            if (elapsedSeconds <= 5.0) {
                multiplier = 0.1f;
            } else if (elapsedSeconds >= 400.0) {
                multiplier = 20.0f;
            } else {
                // Linear interpolation from 0.1 to 20 over 5s-400s
                multiplier = 0.1f + (20.0f - 0.1f) * (float)((elapsedSeconds - 5.0) / (400.0 - 5.0));
            }
            float beforeMult = amount;
            amount *= multiplier;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Liberator] elapsed={}s, multiplier={}, amount={} -> {}",
                        String.format("%.1f", elapsedSeconds), multiplier, beforeMult, amount);
            }
            data.putLong(KEY_LIBERATOR_LAST_ATTACK, currentTick);
        }

        // --- 10. 至高之术 Supreme Art: +20%/40% attack damage ---
        int supremeArtLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.SUPREME_ART);
        if (supremeArtLevel > 0) {
            float damageBonus = 0.2f * supremeArtLevel; // Level 1: +20%, Level 2: +40%
            amount *= (1.0f + damageBonus);
        }

        // --- 11. 我的海疆 My Sea Domain: Trident +60% damage, apply vulnerability ---
        int seaDomainLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.MY_SEA_DOMAIN);
        if (seaDomainLevel > 0) {
            ItemStack weapon = attacker.getMainHandItem();
            if (weapon.getItem() == Items.TRIDENT) {
                amount *= 1.6f; // +60% damage
                // Apply vulnerability to target (refreshable)
                CompoundTag targetData = getEntityData(defender);
                targetData.putLong(KEY_MY_SEA_DOMAIN_START, defender.level().getGameTime());
            }
        }

        // --- 12. 群体打击 Area Strike: AoE damage for ranged attacks ---
        int areaStrikeLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.AREA_STRIKE);
        if (areaStrikeLevel > 0 && isRangedWeapon(mainHand)) {
            double radius = 2.0 + areaStrikeLevel * 2.0; // Level 1: 2, Level 2: 4, Level 3: 6
            float aoeRatio = areaStrikeLevel == 1 ? 0.30f : (areaStrikeLevel == 2 ? 0.40f : 0.55f);
            float aoeDamage = amount * aoeRatio;
            Level level = defender.level();
            AABB box = defender.getBoundingBox().inflate(radius);
            for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, box)) {
                if (nearby != defender && nearby != attacker && nearby.isAlive() && attacker.canAttack(nearby)) {
                    // Damage falls off with distance
                    double dist = nearby.distanceTo(defender);
                    float falloff = (float) Math.max(0, 1.0 - (dist / radius));
                    nearby.hurt(source, aoeDamage * falloff);
                }
            }
        }

        // --- 14. 可是我的自卑胜过了一切爱我的 Self Doubt: 20% chance to steal a beneficial effect ---
        int selfDoubtLevel = getEnchantmentLevel(attacker, ModEnchantments.SELF_DOUBT);
        if (selfDoubtLevel > 0) {
            if (RANDOM.nextFloat() < 0.20f) {
                // Find a beneficial effect on the target and transfer it to the attacker
                MobEffectInstance stolen = null;
                for (MobEffectInstance effect : defender.getActiveEffects()) {
                    if (effect.getEffect().value().isBeneficial()) {
                        stolen = effect;
                        break;
                    }
                }
                if (stolen != null) {
                    defender.removeEffect(stolen.getEffect());
                    attacker.addEffect(new MobEffectInstance(stolen.getEffect(), stolen.getDuration(), stolen.getAmplifier()));
                }
            }
        }

        // --- 16. 血泣 Blood Weep: Cost HP, increase damage ---
        int bloodWeepLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.BLOOD_WEEP);
        if (bloodWeepLevel > 0) {
            float hpCost = bloodWeepLevel == 1 ? 10.0f : (bloodWeepLevel == 2 ? 7.0f : 4.0f);
            float damageBonus = bloodWeepLevel == 1 ? 0.20f : (bloodWeepLevel == 2 ? 0.30f : 0.45f);
            // Cost HP from attacker
            float newHealth = Math.max(0, attacker.getHealth() - hpCost);
            attacker.setHealth(newHealth);
            // Increase damage
            amount *= (1.0f + damageBonus);
        }

        // --- 18. 剥壳 Shell Strip: True damage from armor reduction, percentage increases per hit ---
        int shellStripLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.SHELL_STRIP);
        if (shellStripLevel > 0) {
            CompoundTag defenderData = getEntityData(defender);
            if (defenderData.contains(KEY_SHELL_STRIP_RAW)) {
                float rawDamage = defenderData.getFloat(KEY_SHELL_STRIP_RAW);
                float reducedByArmor = rawDamage - amount;
                if (reducedByArmor > 0) {
                    // Stacking percentage: starts at 40%, increases by 5% per hit on same target, up to 75%
                    String percentKey = "zhonz_shell_strip_percent_" + attacker.getId();
                    float currentPercent = defenderData.contains(percentKey) ? defenderData.getFloat(percentKey) : 0.40f;
                    float trueDamagePercent = Math.min(0.75f, currentPercent + 0.05f);
                    float trueDamage = reducedByArmor * trueDamagePercent;
                    defenderData.putFloat(percentKey, trueDamagePercent);

                    // Apply true damage (bypasses armor)
                    if (trueDamage > 0) {
                        defender.setHealth(Math.max(0, defender.getHealth() - trueDamage));
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("[ShellStrip] {}% true damage: {} (reducedByArmor={}, percent={}%)",
                                    (int)(trueDamagePercent * 100), trueDamage, reducedByArmor, (int)(trueDamagePercent * 100));
                        }
                    }
                }
                defenderData.remove(KEY_SHELL_STRIP_RAW);
            }
        }

        // --- 19. 破军 Army Breaker: Bonus damage against low HP targets ---
        int armyBreakerLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.ARMY_BREAKER);
        if (armyBreakerLevel > 0) {
            float hpRatio = defender.getHealth() / defender.getMaxHealth();
            float threshold = armyBreakerLevel == 1 ? 0.30f : (armyBreakerLevel == 2 ? 0.40f : 0.50f);
            float bonusRatio = armyBreakerLevel == 1 ? 0.10f : (armyBreakerLevel == 2 ? 0.20f : 0.30f);
            if (hpRatio <= threshold) {
                amount *= (1.0f + bonusRatio);
            }
        }

        // --- 22. 压制 Suppression: Target cannot move for 6 seconds, weapon destroyed ---
        int suppressionLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.SUPPRESSION);
        if (suppressionLevel > 0) {
            // Apply Slowness VI for 6 seconds (effectively cannot move)
            defender.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 5));
            // Destroy the weapon
            attacker.getMainHandItem().setCount(0);
        }

        // --- 25. 爆裂黎明 Explosive Dawn: Crossbow +300% damage, splash, invincibility during reload ---
        int explosiveDawnLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.EXPLOSIVE_DAWN);
        if (explosiveDawnLevel > 0 && mainHand.getItem() == Items.CROSSBOW) {
            amount *= 4.0f;
            Level level = defender.level();
            double splashRadius = 15.0;
            AABB box = defender.getBoundingBox().inflate(splashRadius);
            for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, box)) {
                if (nearby != defender && nearby != attacker && nearby.isAlive() && attacker.canAttack(nearby)) {
                    double dist = nearby.distanceTo(defender);
                    float falloff = (float) Math.max(0, 1.0 - (dist / splashRadius));
                    nearby.hurt(source, amount * falloff);
                }
            }
            // Mark attacker as invincible during reload (tracked via entity data)
            CompoundTag attackerData = getEntityData(attacker);
            attackerData.putBoolean(KEY_EXPLOSIVE_DAWN_RELOADING, true);
        }

        // --- 26. 假面的愚者 Fools Mask: Lucky/Unlucky - defender takes buff/debuff, attacker damage modified ---
        int foolsMaskLevel = getEnchantmentLevel(defender, ModEnchantments.FOOLS_MASK);
        if (foolsMaskLevel > 0) {
            CompoundTag data = getEntityData(defender);
            boolean isLucky = data.getBoolean(KEY_FOOLS_MASK_LUCKY);
            if (isLucky) {
                // Lucky: defender gets random buff, attacker deals 100-300% damage
                applyRandomBuff(defender);
                float multiplier = 1.0f + RANDOM.nextFloat() * RANDOM.nextFloat() * 2.0f;
                amount *= multiplier;
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[FoolsMask] Lucky! Damage x{}", String.format("%.2f", multiplier));
                }
            } else {
                // Unlucky: defender gets random debuff, attacker deals 100-1% damage
                applyRandomDebuff(defender);
                float multiplier = 1.0f - RANDOM.nextFloat() * RANDOM.nextFloat() * 0.99f;
                amount *= Math.max(0.01f, multiplier);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[FoolsMask] Unlucky! Damage x{}", String.format("%.2f", multiplier));
                }
            }
        }

        // --- 29. 倏忽恩赐 Fleeting Grace: Add stored damage * 2 to attack ---
        int fleetingGraceLevel = getEnchantmentLevel(attacker, ModEnchantments.FLEETING_GRACE);
        if (fleetingGraceLevel > 0) {
            CompoundTag data = getEntityData(attacker);
            if (data.contains(KEY_FLEETING_GRACE_STORED)) {
                float stored = data.getFloat(KEY_FLEETING_GRACE_STORED);
                if (stored > 0) {
                    amount += stored * 2.0f;
                    data.putFloat(KEY_FLEETING_GRACE_STORED, 0); // Reset after use
                }
            }
        }

        // --- 31. 翻飞之币 Flipping Coin: Gain Strength + max HP, target loses max HP + Weakness ---
        int flippingCoinLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.FLIPPING_COIN);
        if (flippingCoinLevel > 0) {
            CompoundTag attackerData = getEntityData(attacker);
            int attackStacks = attackerData.contains(KEY_FLIPPING_COIN_ATTACK_STACKS) ? attackerData.getInt(KEY_FLIPPING_COIN_ATTACK_STACKS) : 0;
            if (attackStacks < 3) {
                attackStacks++;
                attackerData.putInt(KEY_FLIPPING_COIN_ATTACK_STACKS, attackStacks);
                attacker.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, Integer.MAX_VALUE, attackStacks - 1, false, false));
                // Gain 1 max HP via attribute modifier
                var attackerMaxHp = attacker.getAttribute(Attributes.MAX_HEALTH);
                if (attackerMaxHp != null) {
                    attackerMaxHp.removeModifier(FLIPPING_COIN_MAX_HP);
                    attackerMaxHp.addPermanentModifier(new AttributeModifier(
                            FLIPPING_COIN_MAX_HP, attackStacks, AttributeModifier.Operation.ADD_VALUE
                    ));
                    attacker.setHealth(attacker.getHealth() + 1.0f);
                }
            }

            CompoundTag defenderData = getEntityData(defender);
            int targetStacks = defenderData.contains(KEY_FLIPPING_COIN_TARGET_STACKS) ? defenderData.getInt(KEY_FLIPPING_COIN_TARGET_STACKS) : 0;
            if (targetStacks < 3) {
                targetStacks++;
                defenderData.putInt(KEY_FLIPPING_COIN_TARGET_STACKS, targetStacks);
                defender.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 0));
                // Lose 1 max HP via attribute modifier
                var defenderMaxHp = defender.getAttribute(Attributes.MAX_HEALTH);
                if (defenderMaxHp != null) {
                    defenderMaxHp.removeModifier(FLIPPING_COIN_TARGET_MAX_HP);
                    defenderMaxHp.addPermanentModifier(new AttributeModifier(
                            FLIPPING_COIN_TARGET_MAX_HP, -targetStacks, AttributeModifier.Operation.ADD_VALUE
                    ));
                    defender.setHealth(Math.min(defender.getHealth(), (float) defenderMaxHp.getValue()));
                }
            }
        }

        // --- 27. 挂 Hang: 攻击造成真实伤害(补偿护甲减免) ---
        if (attacker instanceof Player p && hasEnchantmentInInventory(p, ModEnchantments.HANG)) {
            // 获取防御方护甲值与韧性,补偿护甲减免让伤害接近真实伤害
            double armorValue = defender.getAttributeValue(Attributes.ARMOR);
            double toughnessValue = defender.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            // 防御方护甲百分比减免 (封顶80%)
            float reductionPercent = (float) (Math.min(0.8, armorValue / (armorValue + 8.0)) * Math.max(0, 1.0 - toughnessValue / 16.0));
            if (reductionPercent > 0.01f) {
                // 补偿被减免的伤害部分
                float bonusDamage = amount * reductionPercent / (1.0f - reductionPercent);
                amount += bonusDamage;
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Hang] 真实伤害补偿: 减免{}%, 额外伤害 {}", (int)(reductionPercent*100), bonusDamage);
                }
            }
        }

        // NOTE: 必须开辟的通路(MustOpenPath)已在LivingIncomingDamageEvent中处理

        // --- 17. 重伤 Grievous Wound: Target receives 30% less healing for 5 seconds ---
        int grievousWoundLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.GRIEVOUS_WOUND);
        if (grievousWoundLevel > 0) {
            CompoundTag defenderData = getEntityData(defender);
            defenderData.putLong(KEY_GRIEVOUS_WOUND_UNTIL, defender.level().getGameTime() + 100);
            // Apply Wither II to reduce healing by 30% (Wither II reduces healing by 30%)
            defender.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
        }

        return amount;
    }

    /**
     * Apply defender-side enchantment effects (modify incoming damage / react to damage).
     */
    private static float applyDefenderEnchantments(LivingEntity defender, DamageSource source, float amount, LivingDamageEvent.Pre event) {
        Entity attackerEntity = source.getEntity();

        // --- 5. 深海的供养 Deep Sea's Grace: Heal after taking damage ---
        int deepSeasGraceLevel = getEnchantmentLevel(defender, ModEnchantments.DEEP_SEAS_GRACE);
        if (deepSeasGraceLevel > 0) {
            float healRatio = deepSeasGraceLevel == 1 ? 0.05f : (deepSeasGraceLevel == 2 ? 0.10f : 0.20f);
            float healAmount = defender.getMaxHealth() * healRatio;
            // Apply full damage first, then heal on next tick
            if (defender.level() instanceof ServerLevel serverLevel) {
                serverLevel.getServer().tell(new net.minecraft.server.TickTask(1, () -> {
                    if (defender.isAlive()) {
                        defender.heal(healAmount);
                    }
                }));
            }
        }

        // --- 6. 宝石伞 Gem Umbrella: Knock attacker back 10 blocks, drop random minerals ---
        int gemUmbrellaLevel = getSlotEnchantmentLevel(defender, ModEnchantments.GEM_UMBRELLA, EquipmentSlot.LEGS);
        if (gemUmbrellaLevel > 0) {
            if (attackerEntity instanceof LivingEntity attacker) {
                // Knock attacker away from defender by 10 blocks
                Vec3 knockDir = defender.position().vectorTo(attacker.position()).normalize().scale(10.0);
                attacker.setDeltaMovement(knockDir.x, Math.abs(knockDir.y) + 0.5, knockDir.z);
                attacker.hurtMarked = true;

                // Drop random quantity (1-5) of random mineral items at attacker location
                if (attacker.level() instanceof ServerLevel serverLevel) {
                    int quantity = 1 + RANDOM.nextInt(5);
                    for (int i = 0; i < quantity; i++) {
                        ItemStack mineralDrop = getRandomMineral();
                        ItemEntity itemEntity = new ItemEntity(
                                serverLevel,
                                attacker.getX(), attacker.getY() + 1, attacker.getZ(),
                                mineralDrop
                        );
                        itemEntity.setDeltaMovement(
                                (RANDOM.nextFloat() - 0.5f) * 0.3f,
                                RANDOM.nextFloat() * 0.5f,
                                (RANDOM.nextFloat() - 0.5f) * 0.3f
                        );
                        serverLevel.addFreshEntity(itemEntity);
                    }
                }
            }
        }

        // --- 8. 鱼丸 Fishball: Transfer damage from nearby same-type entities to the wearer ---
        // When an entity WITHOUT Fishball takes damage, check for nearby same-type entities WITH Fishball.
        // If found, transfer 30% of the damage to the Fishball wearer (they take the damage for the ally).
        // Entities WITH Fishball don't transfer their damage away (they're the tanks).
        {
            int fishballLevel = getEnchantmentLevel(defender, ModEnchantments.FISHBALL);
            if (fishballLevel == 0) {
                // Defender does NOT have Fishball - check if there's a Fishball wearer nearby to transfer damage TO
                List<LivingEntity> nearbyFishballWearers = getNearbySameTypeWithFishball(defender, 10.0);
                if (!nearbyFishballWearers.isEmpty()) {
                    LivingEntity fishballWearer = nearbyFishballWearers.get(0);
                    // Transfer 30% of damage to the fishball wearer
                    float transferDamage = amount * 0.3f;
                    fishballWearer.hurt(source, transferDamage);
                    // Reduce the original damage by the transferred amount
                    amount -= transferDamage;
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[Fishball] Transferred {} damage from {} to fishball wearer {}",
                                transferDamage, defender.getName().getString(), fishballWearer.getName().getString());
                    }
                }
            }
            // If defender HAS Fishball, don't transfer its damage to others
        }

        // --- 13. 坚韧 Toughness: When shield is about to break, gain +20 armor and +20 toughness temporarily ---
        int toughnessLevel = getEnchantmentLevel(defender, ModEnchantments.TOUGHNESS);
        if (toughnessLevel > 0) {
            if (defender.isUsingItem() && defender.getUseItem().is(Items.SHIELD)) {
                ItemStack shield = defender.getUseItem();
                if (shield.getDamageValue() >= shield.getMaxDamage() - 1) {
                    // Shield about to break - apply +20 armor and +20 toughness for 10 seconds
                    CompoundTag data = getEntityData(defender);
                    if (!data.getBoolean("zhonz_toughness_triggered")) {
                        data.putBoolean("zhonz_toughness_triggered", true);
                        // Add Armor modifier (+20 armor = 80% damage reduction for armor)
                        var armorAttr = defender.getAttribute(Attributes.ARMOR);
                        if (armorAttr != null) {
                            armorAttr.addTransientModifier(new AttributeModifier(
                                    TOUGHNESS_ARMOR_MODIFIER, 20.0, AttributeModifier.Operation.ADD_VALUE
                            ));
                        }
                        // Add Toughness modifier (+20 toughness)
                        var toughnessAttr = defender.getAttribute(Attributes.ARMOR_TOUGHNESS);
                        if (toughnessAttr != null) {
                            toughnessAttr.addTransientModifier(new AttributeModifier(
                                    TOUGHNESS_TOUGHNESS_MODIFIER, 20.0, AttributeModifier.Operation.ADD_VALUE
                            ));
                        }
                        // Remove after 10 seconds
                        if (defender.level() instanceof ServerLevel serverLevel) {
                            serverLevel.getServer().tell(new net.minecraft.server.TickTask(200, () -> {
                                var armor = defender.getAttribute(Attributes.ARMOR);
                                if (armor != null) armor.removeModifier(TOUGHNESS_ARMOR_MODIFIER);
                                var tough = defender.getAttribute(Attributes.ARMOR_TOUGHNESS);
                                if (tough != null) tough.removeModifier(TOUGHNESS_TOUGHNESS_MODIFIER);
                                CompoundTag d = getEntityData(defender);
                                d.remove("zhonz_toughness_triggered");
                            }));
                        }
                    }
                }
            }
        }

        // --- 21. 紧急救援 Emergency Rescue: When HP <= 5, trigger Regeneration III ---
        int emergencyRescueLevel = getEnchantmentLevel(defender, ModEnchantments.EMERGENCY_RESCUE);
        if (emergencyRescueLevel > 0) {
            CompoundTag data = getEntityData(defender);
            int cooldown = data.contains(KEY_EMERGENCY_RESCUE_CD) ? data.getInt(KEY_EMERGENCY_RESCUE_CD) : 0;
            float hpAfterDamage = defender.getHealth() - amount;
            if (hpAfterDamage <= 5.0f && hpAfterDamage > 0 && cooldown <= 0) {
                // Self gets Regeneration III for 3 seconds
                defender.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 2));
                // Nearby same-type entities also get Regeneration III for 3 seconds
                List<LivingEntity> nearbySameType = getNearbySameType(defender, 10.0);
                for (LivingEntity nearby : nearbySameType) {
                    nearby.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 2));
                }
                // Set cooldown (10 seconds)
                data.putInt(KEY_EMERGENCY_RESCUE_CD, 200);
            }
        }

        return amount;
    }

    // ===== LivingDeathEvent - Death Prevention =====

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;

        // --- 24. 自地狱中归来 Return from Hell: Negate fatal damage, set HP to max, halve boots durability ---
        if (entity instanceof Player player) {
            int returnFromHellLevel = getEnchantmentLevel(player, ModEnchantments.RETURN_FROM_HELL);
            CompoundTag data = player.getPersistentData();
            int cooldown = data.contains(KEY_RETURN_FROM_HELL_CD) ? data.getInt(KEY_RETURN_FROM_HELL_CD) : 0;
            if (returnFromHellLevel > 0 && cooldown <= 0) {
                event.setCanceled(true);
                player.setHealth(player.getMaxHealth());
                player.removeAllEffects();
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 300, 0));
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 300, 1));
                // Halve boots durability
                ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
                if (!boots.isEmpty() && boots.isDamageableItem()) {
                    int newDamage = boots.getDamageValue() + (boots.getMaxDamage() - boots.getDamageValue()) / 2;
                    boots.setDamageValue(newDamage);
                }
                // Set cooldown (5 minutes = 6000 ticks)
                data.putInt(KEY_RETURN_FROM_HELL_CD, 6000);
                if (player.level() instanceof ServerLevel serverLevel) {
                    serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 0.8f);
                }
                return;
            }

            // --- 31. 翻飞之币 Flipping Coin: Clear all max HP modifiers and stacks on death ---
            CompoundTag playerData = getEntityData(player);
            if (playerData.contains(KEY_FLIPPING_COIN_ATTACK_STACKS)) {
                playerData.remove(KEY_FLIPPING_COIN_ATTACK_STACKS);
                // 移除生命上限修改器
                var maxHp = player.getAttribute(Attributes.MAX_HEALTH);
                if (maxHp != null) {
                    maxHp.removeModifier(FLIPPING_COIN_MAX_HP);
                }
                // 移除所有永久力量buff(此mod添加的)
                player.removeEffect(MobEffects.DAMAGE_BOOST);
            }
        }

        // --- 30. 神护 Divine Protection: Totem effect on fatal damage, remove 25% durability ---
        {
            int divineProtectionLevel = getEnchantmentLevel(entity, ModEnchantments.DIVINE_PROTECTION);
            if (divineProtectionLevel > 0) {
                // Check if any equipped item has Divine Protection
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    ItemStack armor = entity.getItemBySlot(slot);
                    if (armor.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.DIVINE_PROTECTION)) > 0) {
                        event.setCanceled(true);
                        entity.setHealth(entity.getMaxHealth());
                        entity.removeAllEffects();
                        entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 400, 1));
                        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 1));
                        entity.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 400, 0));
                        // Remove 25% of this item's durability
                        if (armor.isDamageableItem()) {
                            int durabilityToRemove = (armor.getMaxDamage() - armor.getDamageValue()) / 4;
                            armor.hurtAndBreak(durabilityToRemove, entity, slot);
                        }
                        if (entity.level() instanceof ServerLevel serverLevel) {
                            serverLevel.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                                    SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
                        }
                        return;
                    }
                }
            }
        }
    }

    // ===== LivingDropsEvent - Loot Modification =====

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        // Currently no enchantments modify drops in the new spec
    }

    // ===== PlayerTickEvent - Tick-Based Effects =====

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        CompoundTag data = player.getPersistentData();
        int tickCount = player.tickCount;

        // --- 2. 食腐者 Scavenger: Remove Hunger effect from bad food ---
        int scavengerLevel = getSlotEnchantmentLevel(player, ModEnchantments.SCAVENGER, EquipmentSlot.HEAD);
        if (scavengerLevel > 0) {
            // Continuously remove Hunger effect (prevents Hunger from Rotten Flesh etc.)
            if (player.hasEffect(MobEffects.HUNGER)) {
                player.removeEffect(MobEffects.HUNGER);
            }
            // Also remove Poison from suspicious stew / pufferfish
            if (player.hasEffect(MobEffects.POISON)) {
                player.removeEffect(MobEffects.POISON);
            }
            // Also remove Nausea
            if (player.hasEffect(MobEffects.CONFUSION)) {
                player.removeEffect(MobEffects.CONFUSION);
            }
        }

        // --- 5. 深海的供养 Deep Sea's Grace: Shield block healing ---
        int deepSeasGraceLevel = getEnchantmentLevel(player, ModEnchantments.DEEP_SEAS_GRACE);
        if (deepSeasGraceLevel > 0) {
            if (player.isUsingItem() && player.getUseItem().is(Items.SHIELD)) {
                // Heal while blocking with shield (every 2 seconds)
                if (tickCount % 40 == 0) {
                    float healRatio = deepSeasGraceLevel == 1 ? 0.05f : (deepSeasGraceLevel == 2 ? 0.10f : 0.20f);
                    player.heal(player.getMaxHealth() * healRatio);
                }
            }
            // Underwater speed boost
            if (player.isInWater()) {
                player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 40, deepSeasGraceLevel - 1));
            }
        }

        // --- 10. 至高之术 Supreme Art: Range and attack speed attribute modifiers ---
        // Only update attribute modifiers when the level changes (avoids per-tick remove+add overhead)
        int supremeArtLevel = getEnchantmentLevel(player, ModEnchantments.SUPREME_ART);
        int lastSupremeArtLevel = data.contains(KEY_SUPREME_ART_LAST_LEVEL) ? data.getInt(KEY_SUPREME_ART_LAST_LEVEL) : -1;
        if (supremeArtLevel != lastSupremeArtLevel) {
            data.putInt(KEY_SUPREME_ART_LAST_LEVEL, supremeArtLevel);

            var entityRangeAttr = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
            if (entityRangeAttr != null) {
                entityRangeAttr.removeModifier(SUPREME_ART_RANGE_MODIFIER);
                if (supremeArtLevel > 0) {
                    double rangeBonus = supremeArtLevel * 2.0; // +2/4 blocks
                    entityRangeAttr.addTransientModifier(new AttributeModifier(
                            SUPREME_ART_RANGE_MODIFIER, rangeBonus, AttributeModifier.Operation.ADD_VALUE
                    ));
                }
            }
            var blockRangeAttr = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
            if (blockRangeAttr != null) {
                blockRangeAttr.removeModifier(SUPREME_ART_RANGE_MODIFIER);
                if (supremeArtLevel > 0) {
                    double rangeBonus = supremeArtLevel * 2.0;
                    blockRangeAttr.addTransientModifier(new AttributeModifier(
                            SUPREME_ART_RANGE_MODIFIER, rangeBonus, AttributeModifier.Operation.ADD_VALUE
                    ));
                }
            }
            // Attack speed: +30%/60% via attribute modifier
            var attackSpeedAttr = player.getAttribute(Attributes.ATTACK_SPEED);
            if (attackSpeedAttr != null) {
                attackSpeedAttr.removeModifier(SUPREME_ART_ATTACK_SPEED_MODIFIER);
                if (supremeArtLevel > 0) {
                    double speedBonus = 0.3 * supremeArtLevel;
                    attackSpeedAttr.addTransientModifier(new AttributeModifier(
                            SUPREME_ART_ATTACK_SPEED_MODIFIER, speedBonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                    ));
                }
            }
        }

        // --- 15. 无垢之人 The Pure: Remove all debuffs ---
        int thePureLevel = getEnchantmentLevel(player, ModEnchantments.THE_PURE);
        if (thePureLevel > 0) {
            removeDebuffs(player);
        }

        // --- 20. 红莲业火 Crimson Hellfire: Damage nearby entities every 2 seconds ---
        int crimsonHellfireLevel = getEnchantmentLevel(player, ModEnchantments.CRIMSON_HELLFIRE);
        if (crimsonHellfireLevel > 0) {
            if (tickCount % 40 == 0) { // Every 2 seconds
                double radius = 3.75;
                AABB box = player.getBoundingBox().inflate(radius);
                for (LivingEntity nearby : player.level().getEntitiesOfClass(LivingEntity.class, box)) {
                    if (nearby != player && nearby.isAlive() && player.canAttack(nearby)) {
                        nearby.hurt(player.damageSources().magic(), 1.0f);
                    }
                }
                // Visual effect
                if (player.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.FLAME,
                            player.getX(), player.getY() + 1, player.getZ(),
                            10, 2.0, 1.0, 2.0, 0.05);
                }
            }
        }

        // --- 23. 先知的长鸣 Prophet's Call: Trigger when blowing goat horn ---
        int prophetsCallLevel = getMainHandEnchantmentLevel(player, ModEnchantments.PROPHETS_CALL);
        if (prophetsCallLevel > 0 && player.isUsingItem() && player.getUseItem().is(Items.GOAT_HORN)) {
            // Make nearby hostile entities glow and take 170% more damage
            double detectRange = 20.0 + prophetsCallLevel * 10.0;
            AABB box = player.getBoundingBox().inflate(detectRange);
            // 检测所有能够攻击玩家的敌对生物(不限于已选中玩家的目标)
            List<Monster> hostiles = player.level().getEntitiesOfClass(Monster.class, box,
                    mob -> mob.isAlive() && !mob.hasEffect(MobEffects.GLOWING));
            if (!hostiles.isEmpty()) {
                for (Monster hostile : hostiles) {
                    // 只对真正的敌对生物生效(可以攻击玩家)
                    if (hostile.canAttack(player)) {
                        hostile.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0));
                        CompoundTag hostileData = getEntityData(hostile);
                        hostileData.putBoolean(KEY_PROPHETS_CALL_ACTIVE, true);
                        hostileData.putLong(KEY_PROPHETS_CALL_UNTIL, player.level().getGameTime() + 80);
                    }
                }
                if (tickCount % 40 == 0) {
                    player.displayClientMessage(
                            Component.translatable("enchantment.zhonz_more_enchantments.prophets_call.warning",
                                    hostiles.size()),
                            true
                    );
                }
            }
        }

        // --- 26. 假面的愚者 Fools Mask: Update lucky/unlucky state (helmet slot only) ---
        int foolsMaskLevel = getSlotEnchantmentLevel(player, ModEnchantments.FOOLS_MASK, EquipmentSlot.HEAD);
        if (foolsMaskLevel > 0) {
            CompoundTag foolsData = data;
            int changeTick = foolsData.contains(KEY_FOOLS_MASK_CHANGE_TICK) ? foolsData.getInt(KEY_FOOLS_MASK_CHANGE_TICK) : 0;
            if (tickCount >= changeTick) {
                // Randomly set Lucky or Unlucky
                boolean isLucky = RANDOM.nextBoolean();
                foolsData.putBoolean(KEY_FOOLS_MASK_LUCKY, isLucky);
                // Next change in 1-60 seconds (20-1200 ticks)
                int nextChange = tickCount + RANDOM.nextInt(1200 - 20 + 1) + 20;
                foolsData.putInt(KEY_FOOLS_MASK_CHANGE_TICK, nextChange);

                if (player.level() instanceof ServerLevel serverLevel) {
                    player.displayClientMessage(
                            Component.literal(isLucky ? "§a§l✦ 幸运 ✦" : "§c§l✧ 不幸 ✧"),
                            true
                    );
                }
            }
        }

        // --- 27. 挂 Hang: Invincibility, fly, speed, strength, kill hostiles, true damage ---
        if (hasEnchantmentInInventory(player, ModEnchantments.HANG)) {
            // Invincibility (Resistance 5 = 100% damage reduction)
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 4, false, false));
            // Flight
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            // Speed 7
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 6, false, false));
            // Strength 255
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 254, false, false));
            // Kill hostiles in 35 blocks with true damage
            if (tickCount % 20 == 0) {
                AABB box = player.getBoundingBox().inflate(35.0);
                for (Monster hostile : player.level().getEntitiesOfClass(Monster.class, box)) {
                    if (hostile.isAlive()) {
                        hostile.setHealth(0);
                    }
                }
            }
        } else {
            // Remove flight if HANG is no longer in inventory
            if (!player.isCreative() && !player.isSpectator()) {
                if (player.getAbilities().mayfly) {
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                }
            }
        }

        // --- 8. 鱼丸 Fishball: Durability regeneration (10x effective durability) + absorption ---
        int fishballLevel = getEnchantmentLevel(player, ModEnchantments.FISHBALL);
        if (fishballLevel > 0) {
            // 每2秒回复鱼丸物品耐久(等效10x耐久)
            if (tickCount % 40 == 0) {
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.OFFHAND}) {
                    ItemStack fishballStack = player.getItemBySlot(slot);
                    if (fishballStack.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.FISHBALL)) > 0
                            && fishballStack.isDamageableItem()) {
                        int currentDurability = fishballStack.getMaxDamage() - fishballStack.getDamageValue();
                        if (currentDurability < fishballStack.getMaxDamage()) {
                            // Regen 1 durability every 2 seconds = effectively 10x durability
                            fishballStack.setDamageValue(Math.max(0, fishballStack.getDamageValue() - 1));
                        }
                    }
                }
                // Also regen from inventory
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (stack.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.FISHBALL)) > 0
                            && stack.isDamageableItem()) {
                        if (stack.getDamageValue() > 0) {
                            stack.setDamageValue(Math.max(0, stack.getDamageValue() - 1));
                        }
                    }
                }
            }

            // 鱼丸耐久吸收: 抵消装备者其他物品的耐久消耗
            // 通过追踪每个槽位的耐久值变化,如果物品耐久增加,让鱼丸消耗等量耐久来抵消
            CompoundTag fishballData = getEntityData(player);

            // 找到鱼丸物品及其槽位
            ItemStack fishballStack = null;
            EquipmentSlot fishballSlot = null;
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.OFFHAND}) {
                ItemStack stack = player.getItemBySlot(slot);
                if (stack.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.FISHBALL)) > 0
                        && stack.isDamageableItem()) {
                    fishballStack = stack;
                    fishballSlot = slot;
                    break;
                }
            }

            if (fishballStack != null) {
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    if (slot == fishballSlot) continue;
                    ItemStack stack = player.getItemBySlot(slot);
                    if (stack.isEmpty() || !stack.isDamageableItem()) continue;

                    String key = "zhonz_fishball_last_damage_" + slot.getName();
                    int currentDamage = stack.getDamageValue();
                    int lastDamage = fishballData.contains(key) ? fishballData.getInt(key) : currentDamage;
                    fishballData.putInt(key, currentDamage);

                    if (currentDamage > lastDamage) {
                        // 物品耐久增加,需要鱼丸吸收
                        int damageIncrease = currentDamage - lastDamage;
                        int fishballRemaining = fishballStack.getMaxDamage() - fishballStack.getDamageValue();
                        int absorb = Math.min(damageIncrease, fishballRemaining);
                        if (absorb > 0) {
                            // 物品恢复耐久,鱼丸消耗耐久
                            stack.setDamageValue(currentDamage - absorb);
                            fishballStack.setDamageValue(fishballStack.getDamageValue() + absorb);
                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug("[Fishball] 吸收{}点耐久消耗, 物品从{}恢复, 鱼丸消耗到{}",
                                        absorb, currentDamage - absorb, fishballStack.getDamageValue());
                            }
                        }
                    }
                }
            }
        }

        // --- 28. 神咒 Divine Curse: Durability decreases 1% per second, halve attributes ---
        int divineCurseLevel = getEnchantmentLevel(player, ModEnchantments.DIVINE_CURSE);
        if (divineCurseLevel > 0) {
            // Decrease durability of cursed items by 1% per second
            if (tickCount % 20 == 0) {
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    ItemStack stack = player.getItemBySlot(slot);
                    if (stack.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.DIVINE_CURSE)) > 0 && stack.isDamageableItem()) {
                        int durabilityToRemove = Math.max(1, (stack.getMaxDamage() - stack.getDamageValue()) / 100);
                        stack.hurtAndBreak(durabilityToRemove, player, slot);
                    }
                }
            }
            // 攻击伤害减半 (Weakness as approximation)
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 3, false, false));
            // 挖掘速度减半 (Mining Fatigue)
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 3, false, false));
            // 移动速度略微减慢(对应触摸范围减半的副作用)
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false));

            // 触摸范围减半 (Entity Interaction Range)
            var entityRange = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
            if (entityRange != null) {
                entityRange.removeModifier(DIVINE_CURSE_RANGE_MODIFIER);
                entityRange.addTransientModifier(new AttributeModifier(
                        DIVINE_CURSE_RANGE_MODIFIER, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                ));
            }
            // 方块交互范围减半
            var blockRange = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
            if (blockRange != null) {
                blockRange.removeModifier(DIVINE_CURSE_BLOCK_RANGE_MODIFIER);
                blockRange.addTransientModifier(new AttributeModifier(
                        DIVINE_CURSE_BLOCK_RANGE_MODIFIER, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                ));
            }
            // 攻击伤害属性减半
            var attackDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attackDamage != null) {
                attackDamage.removeModifier(DIVINE_CURSE_DAMAGE_MODIFIER);
                attackDamage.addTransientModifier(new AttributeModifier(
                        DIVINE_CURSE_DAMAGE_MODIFIER, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                ));
            }
            // 攻击速度减半
            var attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
            if (attackSpeed != null) {
                attackSpeed.removeModifier(DIVINE_CURSE_ATTACK_SPEED_MODIFIER);
                attackSpeed.addTransientModifier(new AttributeModifier(
                        DIVINE_CURSE_ATTACK_SPEED_MODIFIER, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                ));
            }
            // Note: 蓄力速度、冷却时间、可破坏方块硬度 requires Mixin support
        } else {
            // 没有神咒时,清理属性修饰符
            var entityRange = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
            if (entityRange != null) entityRange.removeModifier(DIVINE_CURSE_RANGE_MODIFIER);
            var blockRange = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
            if (blockRange != null) blockRange.removeModifier(DIVINE_CURSE_BLOCK_RANGE_MODIFIER);
            var attackDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attackDamage != null) attackDamage.removeModifier(DIVINE_CURSE_DAMAGE_MODIFIER);
            var attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
            if (attackSpeed != null) attackSpeed.removeModifier(DIVINE_CURSE_ATTACK_SPEED_MODIFIER);
        }

        // --- 25. 爆裂黎明 Explosive Dawn: Invincibility during crossbow reload ---
        int explosiveDawnLevel = getMainHandEnchantmentLevel(player, ModEnchantments.EXPLOSIVE_DAWN);
        if (explosiveDawnLevel > 0 && player.isUsingItem() && player.getUseItem().is(Items.CROSSBOW)) {
            CompoundTag edData = getEntityData(player);
            if (edData.getBoolean(KEY_EXPLOSIVE_DAWN_RELOADING)) {
                // During reload: grant invincibility (Resistance V = 100% damage reduction)
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 4, false, false));
                // If crossbow is fully loaded, reset the reloading flag
                if (!player.getUseItem().is(Items.CROSSBOW)) {
                    edData.putBoolean(KEY_EXPLOSIVE_DAWN_RELOADING, false);
                }
            }
        } else if (getEntityData(player).getBoolean(KEY_EXPLOSIVE_DAWN_RELOADING)) {
            // Reload complete
            getEntityData(player).putBoolean(KEY_EXPLOSIVE_DAWN_RELOADING, false);
        }

        // --- 32. 必须开辟的通路 Must Open Path: Water effect around mace wielder ---
        int mustOpenPathLevel = getMainHandEnchantmentLevel(player, ModEnchantments.MUST_OPEN_PATH);
        if (mustOpenPathLevel > 0) {
            // Water bubble effect around player
            if (tickCount % 10 == 0 && player.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.BUBBLE,
                        player.getX(), player.getY() + 1, player.getZ(),
                        5, 1.0, 0.5, 1.0, 0.1);
            }
            // Note: The throw mechanic (charging and throwing mace like trident)
            // requires a Mixin on MaceItem or a custom item interaction handler.
        }

        // --- 33. 不完整的预知眼 Incomplete Foreknowledge Eye: Regenerate dodge probability ---
        int foreknowledgeEyeLevel = getSlotEnchantmentLevel(player, ModEnchantments.INCOMPLETE_FOREKNOWLEDGE_EYE, EquipmentSlot.HEAD);
        if (foreknowledgeEyeLevel > 0) {
            CompoundTag eyeData = getEntityData(player);
            long lastCombat = eyeData.contains(KEY_FOREKNOWLEDGE_LAST_COMBAT) ? eyeData.getLong(KEY_FOREKNOWLEDGE_LAST_COMBAT) : 0;
            long currentTick = player.level().getGameTime();
            long combatFreeTicks = currentTick - lastCombat;

            // Regenerate 1% per second when not in combat (80 seconds without interaction)
            if (combatFreeTicks > 1600) { // 80 seconds = 1600 ticks
                float currentProb = eyeData.contains(KEY_FOREKNOWLEDGE_DODGE) ? eyeData.getFloat(KEY_FOREKNOWLEDGE_DODGE) : 0.80f;
                if (currentProb < 0.80f) {
                    float newProb = Math.min(0.80f, currentProb + 0.01f);
                    eyeData.putFloat(KEY_FOREKNOWLEDGE_DODGE, newProb);
                }
            }

            // Screen blur when dodge probability is below 20%
            float dodgeProb = eyeData.contains(KEY_FOREKNOWLEDGE_DODGE) ? eyeData.getFloat(KEY_FOREKNOWLEDGE_DODGE) : 0.80f;
            if (dodgeProb < 0.20f && tickCount % 40 == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 40, 0, false, false));
            }
        }

        // ===== Cooldown Management =====
        tickCooldown(data, KEY_EMERGENCY_RESCUE_CD);
        tickCooldown(data, KEY_RETURN_FROM_HELL_CD);
        tickCooldown(data, KEY_MUST_OPEN_PATH_CD);

        // ===== Liberator: Update last attack time for non-attack tracking =====
        // (The last attack time is updated in LivingDamageEvent when the player attacks)

        // ===== Healing Reduction for Grievous Wound =====
        // Expire the healing reduction flag when the timer runs out.
        // Note: Actual healing reduction requires a Mixin on LivingEntity.heal().
        {
            CompoundTag playerEntityData = getEntityData(player);
            if (playerEntityData.contains(KEY_GRIEVOUS_WOUND_UNTIL)) {
                long until = playerEntityData.getLong(KEY_GRIEVOUS_WOUND_UNTIL);
                if (player.level().getGameTime() >= until) {
                    playerEntityData.remove(KEY_GRIEVOUS_WOUND_UNTIL);
                }
            }
        }

        // ===== Prophet's Call Damage Bonus =====
        // (Handled in LivingDamageEvent by checking KEY_PROPHETS_CALL_ACTIVE on the defender)

        // ===== Emergency Rescue: Tick-based HP check =====
        {
            int erLevel = getEnchantmentLevel(player, ModEnchantments.EMERGENCY_RESCUE);
            if (erLevel > 0 && player.getHealth() <= 5.0f) {
                int erCd = data.getInt(KEY_EMERGENCY_RESCUE_CD);
                if (erCd <= 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 2));
                    for (LivingEntity nearby : getNearbySameType(player, 10.0)) {
                        nearby.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 2));
                    }
                    data.putInt(KEY_EMERGENCY_RESCUE_CD, 200);
                }
            }
        }

        // ===== Shell Strip: Periodically clean up stale per-attacker stacking data =====
        if (tickCount % 100 == 0) {
            CompoundTag playerEntityData = getEntityData(player);
            for (String key : new ArrayList<>(playerEntityData.getAllKeys())) {
                if (key.startsWith("zhonz_shell_strip_percent_")) {
                    playerEntityData.remove(key);
                }
            }
        }
    }

    /**
     * Decrement a cooldown counter stored in CompoundTag if it's positive.
     */
    private static void tickCooldown(CompoundTag data, String key) {
        int cd = data.contains(key) ? data.getInt(key) : 0;
        if (cd > 0) {
            data.putInt(key, cd - 1);
        }
    }

    // ===== Additional LivingDamageEvent Handling for Prophet's Call =====
    // Prophet's Call damage bonus is checked in onLivingDamage via defender's data

    // ===== Helper: Get random mineral item for Gem Umbrella =====

    private static ItemStack getRandomMineral() {
        ItemStack[] minerals = {
                new ItemStack(Items.RAW_IRON),
                new ItemStack(Items.RAW_GOLD),
                new ItemStack(Items.DIAMOND),
                new ItemStack(Items.EMERALD),
                new ItemStack(Items.LAPIS_LAZULI),
                new ItemStack(Items.REDSTONE),
                new ItemStack(Items.RAW_COPPER),
                new ItemStack(Items.COAL),
                new ItemStack(Items.IRON_NUGGET),
                new ItemStack(Items.GOLD_NUGGET),
                new ItemStack(Items.AMETHYST_SHARD),
                new ItemStack(Items.QUARTZ)
        };
        return minerals[RANDOM.nextInt(minerals.length)].copy();
    }

    // ===== Helper: Apply random beneficial effect =====

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

    // ===== Helper: Apply random harmful effect =====

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
