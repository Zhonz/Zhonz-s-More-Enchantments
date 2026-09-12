package com.zhonz.moreenchantments.neoforge.mixin;

import com.zhonz.moreenchantments.enchantment.ModEnchantments;
import com.zhonz.moreenchantments.common.storage.EntityDataStorage;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.zhonz.moreenchantments.ZhonzMoreEnchantments.MODID;

/**
 * Mixin for ItemStack.hurtAndBreak to implement the "坚韧" (Toughness) enchantment:
 * When an item with Toughness is about to take durability damage, the single damage
 * event is capped at 49% of the item's max durability.
 *
 * Also: When the Toughness shield is broken, +20 armor and +20 toughness
 * attribute modifiers are applied temporarily.
 */
@Mixin(ItemStack.class)
public class ItemStackDurabilityMixin {

    private static final String SHIELD_BROKEN_TICK_KEY = "zhonz_toughness_shield_broken_tick";
    private static final int TOUGHNESS_BONUS_DURATION_TICKS = 400;
    private static final double TOUGHNESS_BONUS_AMOUNT = 20.0;

    /**
     * Modify the amount of durability damage taken and handle shield-break bonus.
     * Target: hurtAndBreak(int, LivingEntity, EquipmentSlot)
     */
    @Inject(method = "hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V",
            at = @At("HEAD"), cancellable = true)
    private void onHurtAndBreak(int amount, LivingEntity entity, EquipmentSlot slot, CallbackInfo ci) {
        if (amount <= 0) return;
        ItemStack self = (ItemStack)(Object)this;
        if (!self.isDamageableItem()) return;

        // 耐心: 举盾超过5秒后,下一次抵挡伤害不消耗盾牌耐久
        if (self.is(Items.SHIELD)) {
            int patienceLevel = self.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.PATIENCE));
            if (patienceLevel > 0) {
                CompoundTag patienceData = EntityDataStorage.getEntityData(entity);
                if (patienceData.getBoolean("zhonz_patience_ready")) {
                    patienceData.putBoolean("zhonz_patience_ready", false);
                    patienceData.remove("zhonz_patience_hold_ticks");
                    ci.cancel();
                    return;
                }
            }
        }

        // 唯有命运: 装备无法破坏 → 不消耗耐久
        if (self.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.UNYIELDING_FATE)) > 0) {
            ci.cancel();
            return;
        }

        // 千万年永恒屹立: 耐久消耗 -80%
        int originalAmount = amount;
        if (self.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.ETERNAL_STANDING)) > 0) {
            amount = Math.max(1, (int) Math.ceil(amount * 0.2));
        }

        // 鱼丸: 自身耐久变为十倍 → 耐久消耗降至 1/10
        if (self.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.FISHBALL)) > 0) {
            amount = Math.max(1, (int) Math.ceil(amount * 0.1));
        }

        int toughnessLevel = self.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.TOUGHNESS));
        if (toughnessLevel <= 0) {
            // 无坚韧: 若永恒屹立已削减 amount, 必须自行落盘(否则局部变量修改被丢弃 → -80% 失效)
            if (amount != originalAmount) {
                self.setDamageValue(Math.min(self.getMaxDamage(), self.getDamageValue() + amount));
                ci.cancel();
            }
            return;
        }

        int maxDamage = self.getMaxDamage();
        int currentDamage = self.getDamageValue();
        int remainingDurability = maxDamage - currentDamage;

        // 限制单次最多扣除49%总耐久
        int maxSingleDamage = Math.max(1, maxDamage * 49 / 100);
        if (amount > maxSingleDamage) {
            amount = maxSingleDamage;
        }

        // 不能一次扣光剩余耐久
        if (amount > remainingDurability) {
            amount = remainingDurability;
        }

        // 直接修改耐久
        int newDamage = currentDamage + amount;
        self.setDamageValue(newDamage);

        // 检查盾是否被破（耐久归零或接近归零）
        if (self.is(Items.SHIELD) && newDamage >= maxDamage - 1) {
            triggerShieldBreakBonus(entity);
        }

        ci.cancel();
    }

    private static void triggerShieldBreakBonus(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide()) return;

        CompoundTag data = EntityDataStorage.getEntityData(entity);
        long currentTick = entity.level().getGameTime();
        long lastTriggered = data.contains(SHIELD_BROKEN_TICK_KEY) ? data.getLong(SHIELD_BROKEN_TICK_KEY) : -1;

        if (lastTriggered == currentTick) return;
        data.putLong(SHIELD_BROKEN_TICK_KEY, currentTick);

        applyToughnessBonus(entity, TOUGHNESS_BONUS_AMOUNT);

        if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().tell(new TickTask(TOUGHNESS_BONUS_DURATION_TICKS, () -> {
                if (entity.isAlive()) {
                    removeToughnessBonus(entity);
                }
            }));
            serverLevel.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f);
        }
    }

    private static ResourceLocation toughnessArmorModifier(LivingEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(MODID, "toughness_armor_" + entity.getId());
    }

    private static ResourceLocation toughnessToughnessModifier(LivingEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(MODID, "toughness_tough_" + entity.getId());
    }

    private static void applyToughnessBonus(LivingEntity entity, double amount) {
        replaceTransient(entity, Attributes.ARMOR, toughnessArmorModifier(entity), amount);
        replaceTransient(entity, Attributes.ARMOR_TOUGHNESS, toughnessToughnessModifier(entity), amount);
    }

    private static void removeToughnessBonus(LivingEntity entity) {
        removeIfPresent(entity, Attributes.ARMOR, toughnessArmorModifier(entity));
        removeIfPresent(entity, Attributes.ARMOR_TOUGHNESS, toughnessToughnessModifier(entity));
    }

    private static void replaceTransient(LivingEntity entity, Holder<Attribute> attr, ResourceLocation id, double amount) {
        var instance = entity.getAttribute(attr);
        if (instance == null) return;
        instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    private static void removeIfPresent(LivingEntity entity, Holder<Attribute> attr, ResourceLocation id) {
        var instance = entity.getAttribute(attr);
        if (instance != null) instance.removeModifier(id);
    }
}
