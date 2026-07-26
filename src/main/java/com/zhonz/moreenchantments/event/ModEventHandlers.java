package com.zhonz.moreenchantments.event;

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

    // ===== Attribute Modifier ResourceLocations =====
    private static final ResourceLocation SUPREME_ART_RANGE_MODIFIER = ResourceLocation.fromNamespaceAndPath("zhonz_more_enchantments", "supreme_art_range");
    private static final ResourceLocation SUPREME_ART_ATTACK_SPEED_MODIFIER = ResourceLocation.fromNamespaceAndPath("zhonz_more_enchantments", "supreme_art_attack_speed");
    private static final ResourceLocation DIVINE_CURSE_DAMAGE_MODIFIER = ResourceLocation.fromNamespaceAndPath("zhonz_more_enchantments", "divine_curse_damage");
    private static final ResourceLocation DIVINE_CURSE_ATTACK_SPEED_MODIFIER = ResourceLocation.fromNamespaceAndPath("zhonz_more_enchantments", "divine_curse_attack_speed");

    // ===== Registration =====

    public static void register() {
        NeoForge.EVENT_BUS.register(ModEventHandlers.class);
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

    private static void removeDebuffs(LivingEntity entity) {
        List<Holder<net.minecraft.world.effect.MobEffect>> toRemove = new ArrayList<>();
        for (MobEffectInstance effect : entity.getActiveEffects()) {
            if (!effect.getEffect().value().isBeneficial()) {
                toRemove.add(effect.getEffect());
            }
        }
        for (Holder<net.minecraft.world.effect.MobEffect> effect : toRemove) {
            entity.removeEffect(effect);
        }
    }

    private static void removeBeneficialEffects(LivingEntity entity) {
        List<Holder<net.minecraft.world.effect.MobEffect>> toRemove = new ArrayList<>();
        for (MobEffectInstance effect : entity.getActiveEffects()) {
            if (effect.getEffect().value().isBeneficial()) {
                toRemove.add(effect.getEffect());
            }
        }
        for (Holder<net.minecraft.world.effect.MobEffect> effect : toRemove) {
            entity.removeEffect(effect);
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

    @SubscribeEvent
    public static void onLivingHurt(LivingIncomingDamageEvent event) {
        LivingEntity defender = event.getEntity();
        if (defender.level().isClientSide()) return;

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
                float vulnerability;
                if (elapsed < 200) { // 0-10s: 30% more damage
                    vulnerability = 0.30f;
                } else if (elapsed < 600) { // 10-30s: increasing from 30% to 60%
                    vulnerability = 0.30f + 0.30f * ((float)(elapsed - 200) / 400f);
                } else { // 30s+: 60% more damage
                    vulnerability = 0.60f;
                }
                // Expire after 60 seconds
                if (elapsed > 1200) {
                    targetData.remove(KEY_MY_SEA_DOMAIN_START);
                } else {
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

        event.setNewDamage(amount);
    }

    /**
     * Apply attacker-side enchantment effects (modify outgoing damage).
     */
    private static float applyAttackerEnchantments(LivingEntity attacker, LivingEntity defender, DamageSource source, float amount, LivingDamageEvent.Pre event) {
        ItemStack mainHand = attacker.getMainHandItem();

        // --- 1. 终结 Finale: If base attack damage >= 7, deal 100000x damage ---
        int finaleLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.FINALE);
        if (finaleLevel > 0) {
            double attackDamage = attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
            if (attackDamage >= 7.0) {
                float originalDamage = amount;
                amount *= 100000.0f;
                // Reduce weapon durability by the original damage amount (before multiplier)
                if (mainHand.isDamageableItem()) {
                    int durabilityCost = (int) Math.min(originalDamage, mainHand.getMaxDamage() - mainHand.getDamageValue());
                    if (durabilityCost > 0) {
                        mainHand.hurtAndBreak(durabilityCost, attacker, EquipmentSlot.MAINHAND);
                    }
                }
            }
        }

        // --- 3. 收割 Harvest: If target HP after damage <= threshold, instant kill ---
        int harvestLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.HARVEST);
        if (harvestLevel > 0) {
            float remainingHp = defender.getHealth() - amount;
            if (remainingHp > 0) {
                float threshold = defender.getMaxHealth() * (0.1f * harvestLevel);
                if (remainingHp <= threshold) {
                    amount = defender.getHealth(); // Set damage to remaining health to kill
                }
            }
        }

        // --- 4. 制裁 Sanction: Deal 1%/2%/3% of target's MAX HP as bonus damage ---
        int sanctionLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.SANCTION);
        if (sanctionLevel > 0) {
            float bonusDamage = defender.getMaxHealth() * (0.01f * sanctionLevel);
            amount += bonusDamage;
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
            amount *= multiplier;
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
                        LOGGER.info("[ShellStrip] {}% true damage: {} (reducedByArmor={}, percent={}%)",
                                (int)(trueDamagePercent * 100), trueDamage, reducedByArmor, (int)(trueDamagePercent * 100));
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

        // --- 25. 爆裂黎明 Explosive Dawn: Crossbow +300% damage, splash ---
        int explosiveDawnLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.EXPLOSIVE_DAWN);
        if (explosiveDawnLevel > 0 && mainHand.getItem() == Items.CROSSBOW) {
            amount *= 4.0f; // +300% = 4x total
            // Splash damage: 100% at center, 0% at 15 blocks
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
            // Note: Slower reload and invincibility during reload require a Mixin
            // on CrossbowItem for full implementation.
        }

        // --- 26. 假面的愚者 Fools Mask: Lucky/Unlucky damage modifier ---
        int foolsMaskLevel = getEnchantmentLevel(attacker, ModEnchantments.FOOLS_MASK);
        if (foolsMaskLevel > 0) {
            CompoundTag data = getEntityData(attacker);
            boolean isLucky = data.getBoolean(KEY_FOOLS_MASK_LUCKY);
            if (isLucky) {
                // Lucky: 100-300% damage, lower = more likely
                float multiplier = 1.0f + RANDOM.nextFloat() * RANDOM.nextFloat() * 2.0f;
                amount *= multiplier;
                // Random buff on hit
                applyRandomBuff(attacker);
            } else {
                // Unlucky: 100-1% damage, higher = more likely (skewed toward 1.0)
                float multiplier = 1.0f - RANDOM.nextFloat() * RANDOM.nextFloat() * 0.99f;
                amount *= Math.max(0.01f, multiplier);
                // Random debuff on hit
                applyRandomDebuff(attacker);
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
                // Gain 1 permanent Strength level
                attacker.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, Integer.MAX_VALUE, attackStacks - 1, false, false));
                // Gain 1 max HP (via Health attribute modifier)
                // Note: Permanent max HP modification requires attribute modifier with MULTIPLY_BASE or ADDITION
            }

            // Target loses 1 max HP and gains Weakness I for 30s
            CompoundTag defenderData = getEntityData(defender);
            int targetStacks = defenderData.contains(KEY_FLIPPING_COIN_TARGET_STACKS) ? defenderData.getInt(KEY_FLIPPING_COIN_TARGET_STACKS) : 0;
            if (targetStacks < 3) {
                targetStacks++;
                defenderData.putInt(KEY_FLIPPING_COIN_TARGET_STACKS, targetStacks);
                defender.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 0)); // 30 seconds
                // Note: Reducing max HP requires attribute modifier removal
            }
        }

        // --- 32. 必须开辟的通路 Must Open Path: Mace 1000% damage, teleport ---
        int mustOpenPathLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.MUST_OPEN_PATH);
        if (mustOpenPathLevel > 0) {
            CompoundTag data = getEntityData(attacker);
            int cooldown = data.contains(KEY_MUST_OPEN_PATH_CD) ? data.getInt(KEY_MUST_OPEN_PATH_CD) : 0;
            if (cooldown <= 0) {
                amount *= 10.0f; // 1000% damage (10x)
                // Teleport attacker to target location
                attacker.teleportTo(defender.getX(), defender.getY(), defender.getZ());
                // 5 second cooldown (100 ticks)
                data.putInt(KEY_MUST_OPEN_PATH_CD, 100);
            }
        }

        // --- 17. 重伤 Grievous Wound: Target receives 30% less healing for 5 seconds ---
        int grievousWoundLevel = getMainHandEnchantmentLevel(attacker, ModEnchantments.GRIEVOUS_WOUND);
        if (grievousWoundLevel > 0) {
            CompoundTag defenderData = getEntityData(defender);
            // Apply healing reduction for 5 seconds (100 ticks)
            defenderData.putLong(KEY_GRIEVOUS_WOUND_UNTIL, defender.level().getGameTime() + 100);
            // Note: Actual healing reduction requires a Mixin on LivingEntity.heal()
            // to check this data and reduce the healing amount by 30%.
            // As a visual indicator, apply Wither effect
            defender.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 0));
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
            LOGGER.info("[DeepSeasGrace] Level {}: will heal {} = {}% max HP on next tick",
                    deepSeasGraceLevel, healAmount, (int) (healRatio * 100));
        }

        // --- 6. 宝石伞 Gem Umbrella: Knock attacker back 10 blocks, drop random minerals ---
        int gemUmbrellaLevel = getSlotEnchantmentLevel(defender, ModEnchantments.GEM_UMBRELLA, EquipmentSlot.LEGS);
        if (gemUmbrellaLevel > 0) {
            if (attackerEntity instanceof LivingEntity attacker) {
                // Knock attacker away from defender by 10 blocks
                Vec3 knockDir = defender.position().vectorTo(attacker.position()).normalize().scale(10.0);
                attacker.setDeltaMovement(knockDir.x, Math.abs(knockDir.y) + 0.5, knockDir.z);
                attacker.hurtMarked = true;

                // Drop random mineral items at attacker location
                if (attacker.level() instanceof ServerLevel serverLevel) {
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
                    LOGGER.info("[Fishball] Transferred {} damage from {} to fishball wearer {}",
                            transferDamage, defender.getName().getString(), fishballWearer.getName().getString());
                }
            }
            // If defender HAS Fishball, don't transfer its damage to others
        }

        // --- 13. 坚韧 Toughness: When shield broken, gain temporary armor/toughness ---
        int toughnessLevel = getEnchantmentLevel(defender, ModEnchantments.TOUGHNESS);
        if (toughnessLevel > 0) {
            // Note: Max 49% durability loss per hit on shield requires a Mixin
            // on ItemStack.hurt() or ShieldItem usage.
            // Check if defender is using a shield and it just broke
            if (defender.isUsingItem() && defender.getUseItem().is(Items.SHIELD)) {
                ItemStack shield = defender.getUseItem();
                if (shield.getDamageValue() >= shield.getMaxDamage() - 1) {
                    // Shield about to break - apply temporary armor and toughness
                    defender.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 1)); // ~20% damage reduction
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
                        entity.setHealth(entity.getMaxHealth() * 0.5f);
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
        int supremeArtLevel = getEnchantmentLevel(player, ModEnchantments.SUPREME_ART);
        {
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

        // --- 23. 先知的长鸣 Prophet's Call: Detect nearby hostiles, glow effect ---
        int prophetsCallLevel = getEnchantmentLevel(player, ModEnchantments.PROPHETS_CALL);
        if (prophetsCallLevel > 0) {
            // Make nearby hostile entities glow and take 170% more damage
            double detectRange = 20.0 + prophetsCallLevel * 10.0;
            AABB box = player.getBoundingBox().inflate(detectRange);
            List<Monster> hostiles = player.level().getEntitiesOfClass(Monster.class, box,
                    mob -> mob.isAlive() && mob.getTarget() == player);
            if (!hostiles.isEmpty()) {
                for (Monster hostile : hostiles) {
                    hostile.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0));
                    // Store damage vulnerability in entity data
                    CompoundTag hostileData = getEntityData(hostile);
                    hostileData.putBoolean(KEY_PROPHETS_CALL_ACTIVE, true);
                    hostileData.putLong(KEY_PROPHETS_CALL_UNTIL, player.level().getGameTime() + 40);
                }
                // Notify player
                if (tickCount % 40 == 0) {
                    player.displayClientMessage(
                            Component.translatable("enchantment.zhonz_more_enchantments.prophets_call.warning",
                                    hostiles.size()),
                            true
                    );
                }
            }
            // Note: The 170% more damage effect requires checking in LivingDamageEvent
            // when the target has Prophet's Call active. Currently handled via
            // the KEY_PROPHETS_CALL_ACTIVE flag checked below.
        }

        // --- 26. 假面的愚者 Fools Mask: Update lucky/unlucky state ---
        int foolsMaskLevel = getEnchantmentLevel(player, ModEnchantments.FOOLS_MASK);
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
            // Halve attack damage (Weakness effect as approximation)
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 3, false, false));
            // Halve mining speed (Mining Fatigue)
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 3, false, false));
            // Halve movement speed
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false));
            // Note: Precise halving of attack speed, mining speed, reach, damage, block hardness,
            // and doubling of charge speed/cooldown requires Mixin support with attribute modifiers.
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

        // --- 33. 不完整的预知眼 Incomplete Foreknowledge Eye: Detect nearby hostiles ---
        int foreknowledgeEyeLevel = getSlotEnchantmentLevel(player, ModEnchantments.INCOMPLETE_FOREKNOWLEDGE_EYE, EquipmentSlot.HEAD);
        if (foreknowledgeEyeLevel > 0) {
            // Every 3 seconds, detect hostiles within 16 blocks and make them glow briefly
            if (tickCount % 60 == 0) {
                double detectRange = 16.0;
                AABB box = player.getBoundingBox().inflate(detectRange);
                List<Monster> hostiles = player.level().getEntitiesOfClass(Monster.class, box, mob -> mob.isAlive());
                for (Monster hostile : hostiles) {
                    hostile.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0));
                }
            }
        }

        // ===== Cooldown Management =====

        // Emergency Rescue cooldown
        {
            int erCd = data.contains(KEY_EMERGENCY_RESCUE_CD) ? data.getInt(KEY_EMERGENCY_RESCUE_CD) : 0;
            if (erCd > 0) {
                data.putInt(KEY_EMERGENCY_RESCUE_CD, erCd - 1);
            }
        }

        // Return from Hell cooldown
        {
            int rfhCd = data.contains(KEY_RETURN_FROM_HELL_CD) ? data.getInt(KEY_RETURN_FROM_HELL_CD) : 0;
            if (rfhCd > 0) {
                data.putInt(KEY_RETURN_FROM_HELL_CD, rfhCd - 1);
            }
        }

        // Must Open Path cooldown
        {
            int mopCd = data.contains(KEY_MUST_OPEN_PATH_CD) ? data.getInt(KEY_MUST_OPEN_PATH_CD) : 0;
            if (mopCd > 0) {
                data.putInt(KEY_MUST_OPEN_PATH_CD, mopCd - 1);
            }
        }

        // ===== Liberator: Update last attack time for non-attack tracking =====
        // (The last attack time is updated in LivingDamageEvent when the player attacks)

        // ===== Healing Reduction for Grievous Wound =====
        // Check if player has active Grievous Wound healing reduction
        {
            CompoundTag playerEntityData = getEntityData(player);
            if (playerEntityData.contains(KEY_GRIEVOUS_WOUND_UNTIL)) {
                long until = playerEntityData.getLong(KEY_GRIEVOUS_WOUND_UNTIL);
                if (player.level().getGameTime() >= until) {
                    playerEntityData.remove(KEY_GRIEVOUS_WOUND_UNTIL);
                } else {
                    // Reduce healing by 30% - check if player was recently healed
                    // Note: Full implementation requires a Mixin on LivingEntity.heal()
                    // to intercept and reduce healing by 30%.
                }
            }
        }

        // ===== Prophet's Call Damage Bonus =====
        // Check if player is attacking an entity with Prophet's Call active
        // (Handled in LivingDamageEvent by checking KEY_PROPHETS_CALL_ACTIVE on the defender)

        // ===== Emergency Rescue: Tick-based HP check =====
        {
            int erLevel = getEnchantmentLevel(player, ModEnchantments.EMERGENCY_RESCUE);
            if (erLevel > 0 && player.getHealth() <= 5.0f) {
                int erCd = data.contains(KEY_EMERGENCY_RESCUE_CD) ? data.getInt(KEY_EMERGENCY_RESCUE_CD) : 0;
                if (erCd <= 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 2));
                    List<LivingEntity> nearbySameType = getNearbySameType(player, 10.0);
                    for (LivingEntity nearby : nearbySameType) {
                        nearby.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 2));
                    }
                    data.putInt(KEY_EMERGENCY_RESCUE_CD, 200);
                }
            }
        }

        // ===== Shell Strip: Reset stacking on new tick cycle =====
        {
            CompoundTag playerEntityData = getEntityData(player);
            // Clean up old stack data periodically
            if (tickCount % 100 == 0) {
                for (String key : new ArrayList<>(playerEntityData.getAllKeys())) {
                    if (key.startsWith("zhonz_shell_strip_stacks_")) {
                        playerEntityData.remove(key);
                    }
                }
            }
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
