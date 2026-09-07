package com.zhonz.moreenchantments.forge.mixin;

import com.zhonz.moreenchantments.common.enchant.EnchantIds;
import com.zhonz.moreenchantments.common.storage.EntityDataStorage;
import com.zhonz.moreenchantments.forge.CommonConstants1201;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Mixin for Player to implement the "坚韧" (Toughness) enchantment's
 * axe-shield-break event trigger.
 *
 * When the player gets hit by an axe attack that disables their shield,
 * if their shield has the Toughness enchantment, +20 armor and +20 toughness
 * attribute modifiers are applied temporarily.
 *
 * 1.20.1 移植差异: 注入目标从 1.21 的 Player.disableShield()
 * 改为 1.20.1 的 Player.disableShield(boolean)(1.20.1 带一个 boolean 参数,
 * 表示是否因斧击禁用), 处理器补上该参数; 属性修饰符/附魔查询同 1.20.1 约定。
 */
@Mixin(Player.class)
public abstract class PlayerShieldBlockMixin {

    private static final String KEY_TOUGHNESS_AXE_BROKEN_TICK = "zhonz_toughness_axe_broken_tick";
    /** 坚韧斧头破盾触发后属性修饰符持续时间(Ticks): 20秒 = 400 ticks. */
    private static final int TOUGHNESS_BONUS_DURATION_TICKS = 400;
    /** 坚韧附魔给予的护甲/韧性加成值. */
    private static final double TOUGHNESS_BONUS_AMOUNT = 20.0;

    /**
     * Player.disableShield(boolean) - 在被斧头攻击时调用
     * 通过注入这个方法,当disableShield触发时,检查玩家格挡的盾是否有坚韧附魔
     */
    @Inject(method = "disableShield", at = @At("HEAD"))
    private void onDisableShield(boolean wasAxeAttack, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self.level().isClientSide()) return;

        // 检查玩家是否在格挡
        if (!self.isUsingItem()) return;
        ItemStack useItem = self.getUseItem();
        if (!useItem.is(Items.SHIELD)) return;

        // 检查盾是否有坚韧附魔
        if (enchLevel(useItem, EnchantIds.TOUGHNESS) <= 0) return;

        // 防止重复触发
        CompoundTag data = EntityDataStorage.getEntityData(self);
        long currentTick = self.level().getGameTime();
        long lastTriggered = data.contains(KEY_TOUGHNESS_AXE_BROKEN_TICK)
                ? data.getLong(KEY_TOUGHNESS_AXE_BROKEN_TICK) : -1;
        if (lastTriggered == currentTick) return;
        data.putLong(KEY_TOUGHNESS_AXE_BROKEN_TICK, currentTick);

        // +20 护甲 + 韧性
        applyToughnessBonus(self, "_axe_");

        // 20秒后移除
        if (self.level() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().tell(new TickTask(TOUGHNESS_BONUS_DURATION_TICKS, () -> {
                if (self.isAlive()) removeToughnessBonus(self, "_axe_");
            }));
            serverLevel.playSound(null, self.getX(), self.getY(), self.getZ(),
                    SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1.0f, 0.7f);
        }
    }

    private static ResourceLocation armorModifier(LivingEntity entity, String suffix) {
        return new ResourceLocation(CommonConstants1201.MODID, "toughness_armor" + suffix + entity.getId());
    }

    private static ResourceLocation toughnessModifier(LivingEntity entity, String suffix) {
        return new ResourceLocation(CommonConstants1201.MODID, "toughness_tough" + suffix + entity.getId());
    }

    private static void applyToughnessBonus(LivingEntity entity, String suffix) {
        replaceTransient(entity, Attributes.ARMOR, armorModifier(entity, suffix), TOUGHNESS_BONUS_AMOUNT);
        replaceTransient(entity, Attributes.ARMOR_TOUGHNESS, toughnessModifier(entity, suffix), TOUGHNESS_BONUS_AMOUNT);
    }

    private static void removeToughnessBonus(LivingEntity entity, String suffix) {
        removeIfPresent(entity, Attributes.ARMOR, armorModifier(entity, suffix));
        removeIfPresent(entity, Attributes.ARMOR_TOUGHNESS, toughnessModifier(entity, suffix));
    }

    private static void replaceTransient(LivingEntity entity, Attribute attr, ResourceLocation id, double amount) {
        AttributeInstance instance = entity.getAttribute(attr);
        if (instance == null) return;
        instance.removeModifier(uuidOf(id));
        instance.addTransientModifier(new AttributeModifier(uuidOf(id), id.toString(), amount,
                AttributeModifier.Operation.ADDITION));
    }

    private static void removeIfPresent(LivingEntity entity, Attribute attr, ResourceLocation id) {
        AttributeInstance instance = entity.getAttribute(attr);
        if (instance != null) instance.removeModifier(uuidOf(id));
    }

    /** 稳定 UUID 派生(与 AttributeAccess1201 一致)。 */
    private static UUID uuidOf(ResourceLocation id) {
        return UUID.nameUUIDFromBytes(("zhonz:" + id.getPath()).getBytes(StandardCharsets.UTF_8));
    }

    /** 物品自身是否带某附魔(id → ForgeRegistries, 对应 1.21 stack.getEnchantmentLevel(holder))。 */
    private static int enchLevel(ItemStack stack, String id) {
        if (stack.isEmpty()) return 0;
        Enchantment ench = ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation(CommonConstants1201.MODID, id));
        if (ench == null) return 0;
        return EnchantmentHelper.getItemEnchantmentLevel(ench, stack);
    }
}
