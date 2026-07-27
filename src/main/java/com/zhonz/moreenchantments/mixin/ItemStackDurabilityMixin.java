package com.zhonz.moreenchantments.mixin;

import com.zhonz.moreenchantments.enchantment.ModEnchantments;
import com.zhonz.moreenchantments.event.EntityDataStorage;
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
 * Also: When the Toughness item is a shield, axe-shield-break or zero-durability events
 * grant +20 armor and +20 toughness attribute modifiers temporarily.
 */
@Mixin(ItemStack.class)
public class ItemStackDurabilityMixin {

    private static final String SHIELD_BROKEN_TICK_KEY = "zhonz_toughness_shield_broken_tick";
    /** 坚韧触发后属性修饰符持续时间(Ticks): 20秒 = 400 ticks. */
    private static final int TOUGHNESS_BONUS_DURATION_TICKS = 400;
    /** 坚韧附魔给予的护甲/韧性加成值. */
    private static final double TOUGHNESS_BONUS_AMOUNT = 20.0;

    /**
     * Modify the amount of durability damage taken.
     * Called by {@code ItemStack.hurtAndBreak(int, LivingEntity, EquipmentSlot)}.
     */
    @Inject(method = "hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V",
            at = @At("HEAD"), cancellable = true)
    private void onHurtAndBreak(int amount, LivingEntity entity, EquipmentSlot slot, CallbackInfo ci) {
        if (amount <= 0) return;
        ItemStack self = (ItemStack)(Object)this;
        if (!self.isDamageableItem()) return;

        // 检查坚韧附魔
        int toughnessLevel = self.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.TOUGHNESS));
        if (toughnessLevel <= 0) return;

        int maxDamage = self.getMaxDamage();
        int currentDamage = self.getDamageValue();
        int remainingDurability = maxDamage - currentDamage;

        // 限制单次最多扣除49%总耐久
        int maxSingleDamage = Math.max(1, maxDamage * 49 / 100);
        if (amount > maxSingleDamage) {
            amount = maxSingleDamage;
        }

        // 如果剩余耐久<amount但大于限制,不能一次扣光
        if (amount > remainingDurability) {
            amount = remainingDurability;
        }

        // 直接修改耐久
        self.setDamageValue(currentDamage + amount);

        ci.cancel();
    }

    /**
     * 当盾被破时（无论是耐久归零还是被斧头破盾）触发+20护甲/韧性奖励
     */
    @Inject(method = "hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V",
            at = @At("TAIL"))
    private void onShieldBreak(int amount, LivingEntity entity, EquipmentSlot slot, CallbackInfo ci) {
        if (entity == null) return;
        ItemStack self = (ItemStack)(Object)this;

        // 只对盾生效
        if (!self.is(Items.SHIELD)) return;

        int toughnessLevel = self.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.TOUGHNESS));
        if (toughnessLevel <= 0) return;

        // 触发条件：耐久归零或被破坏
        if (self.getDamageValue() < self.getMaxDamage() - 1) return;

        CompoundTag data = EntityDataStorage.getEntityData(entity);
        long currentTick = entity.level().getGameTime();
        long lastTriggered = data.contains(SHIELD_BROKEN_TICK_KEY) ? data.getLong(SHIELD_BROKEN_TICK_KEY) : -1;

        // 防止重复触发
        if (lastTriggered == currentTick) return;
        data.putLong(SHIELD_BROKEN_TICK_KEY, currentTick);

        // 添加+20护甲和+20韧性的临时属性修饰符
        applyToughnessBonus(entity, TOUGHNESS_BONUS_AMOUNT);

        // 20秒后移除属性修饰符
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
