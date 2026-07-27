package com.zhonz.moreenchantments.mixin;

import com.zhonz.moreenchantments.enchantment.ModEnchantments;
import com.zhonz.moreenchantments.event.EntityDataStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
        if (!self.is(net.minecraft.world.item.Items.SHIELD)) return;

        int toughnessLevel = self.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.TOUGHNESS));
        if (toughnessLevel <= 0) return;

        // 触发条件：耐久归零或被破坏
        boolean broken = self.getDamageValue() >= self.getMaxDamage() - 1;

        if (broken) {
            CompoundTag data = EntityDataStorage.getEntityData(entity);
            long currentTick = entity.level().getGameTime();
            long lastTriggered = data.contains("zhonz_toughness_shield_broken_tick")
                    ? data.getLong("zhonz_toughness_shield_broken_tick")
                    : -1;

            // 防止重复触发
            if (lastTriggered == currentTick) return;
            data.putLong("zhonz_toughness_shield_broken_tick", currentTick);

            // 添加+20护甲和+20韧性的临时属性修饰符
            var armorAttr = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
            if (armorAttr != null) {
                net.minecraft.resources.ResourceLocation armorModLoc =
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                "zhonz_more_enchantments", "toughness_armor_" + entity.getId());
                armorAttr.removeModifier(armorModLoc);
                armorAttr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                        armorModLoc, 20.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE
                ));
            }
            var toughnessAttr = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS);
            if (toughnessAttr != null) {
                net.minecraft.resources.ResourceLocation toughModLoc =
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                "zhonz_more_enchantments", "toughness_tough_" + entity.getId());
                toughnessAttr.removeModifier(toughModLoc);
                toughnessAttr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                        toughModLoc, 20.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE
                ));
            }

            // 20秒后移除属性修饰符
            if (entity.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                serverLevel.getServer().tell(new net.minecraft.server.TickTask(400, () -> {
                    if (entity.isAlive()) {
                        var armorAttr2 = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
                        if (armorAttr2 != null) {
                            net.minecraft.resources.ResourceLocation armorModLoc2 =
                                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                            "zhonz_more_enchantments", "toughness_armor_" + entity.getId());
                            armorAttr2.removeModifier(armorModLoc2);
                        }
                        var toughnessAttr2 = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS);
                        if (toughnessAttr2 != null) {
                            net.minecraft.resources.ResourceLocation toughModLoc2 =
                                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                            "zhonz_more_enchantments", "toughness_tough_" + entity.getId());
                            toughnessAttr2.removeModifier(toughModLoc2);
                        }
                    }
                }));
            }

            if (entity.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                serverLevel.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        net.minecraft.sounds.SoundEvents.SHIELD_BREAK,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.8f);
            }
        }
    }
}
