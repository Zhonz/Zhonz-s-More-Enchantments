package com.zhonz.moreenchantments.mixin;

import com.zhonz.moreenchantments.enchantment.ModEnchantments;
import com.zhonz.moreenchantments.event.EntityDataStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin for Player to implement the "坚韧" (Toughness) enchantment's
 * axe-shield-break event trigger.
 *
 * When the player gets hit by an axe attack that disables their shield,
 * if their shield has the Toughness enchantment, +20 armor and +20 toughness
 * attribute modifiers are applied temporarily.
 *
 * 检测条件: 玩家在格挡时被斧头攻击,导致格挡被禁用
 */
@Mixin(Player.class)
public abstract class PlayerShieldBlockMixin {

    /**
     * Player.disableShield(boolean) - 在被斧头攻击时调用
     * 通过注入这个方法,当disableShield触发时,检查玩家格挡的盾是否有坚韧附魔
     */
    @Inject(method = "disableShield", at = @At("HEAD"))
    private void onDisableShield(boolean axe, CallbackInfo ci) {
        Player self = (Player)(Object)this;
        if (self.level().isClientSide()) return;

        // 只在axe attack导致的disableShield时触发
        if (!axe) return;

        // 检查玩家是否在格挡
        if (!self.isUsingItem()) return;
        ItemStack useItem = self.getUseItem();
        if (!useItem.is(Items.SHIELD)) return;

        // 检查盾是否有坚韧附魔
        int toughnessLevel = useItem.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.TOUGHNESS));
        if (toughnessLevel <= 0) return;

        // 触发+20护甲和+20韧性
        CompoundTag data = EntityDataStorage.getEntityData(self);
        long currentTick = self.level().getGameTime();
        long lastTriggered = data.contains("zhonz_toughness_axe_broken_tick")
                ? data.getLong("zhonz_toughness_axe_broken_tick")
                : -1;

        if (lastTriggered == currentTick) return;
        data.putLong("zhonz_toughness_axe_broken_tick", currentTick);

        // +20 护甲
        var armorAttr = self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        if (armorAttr != null) {
            net.minecraft.resources.ResourceLocation armorModLoc =
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                            "zhonz_more_enchantments", "toughness_armor_axe_" + self.getId());
            armorAttr.removeModifier(armorModLoc);
            armorAttr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    armorModLoc, 20.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE
            ));
        }
        // +20 韧性
        var toughnessAttr = self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS);
        if (toughnessAttr != null) {
            net.minecraft.resources.ResourceLocation toughModLoc =
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                            "zhonz_more_enchantments", "toughness_tough_axe_" + self.getId());
            toughnessAttr.removeModifier(toughModLoc);
            toughnessAttr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    toughModLoc, 20.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE
            ));
        }

        // 20秒后移除
        if (self.level() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().tell(new net.minecraft.server.TickTask(400, () -> {
                if (self.isAlive()) {
                    var armorAttr2 = self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
                    if (armorAttr2 != null) {
                        net.minecraft.resources.ResourceLocation armorModLoc2 =
                                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                        "zhonz_more_enchantments", "toughness_armor_axe_" + self.getId());
                        armorAttr2.removeModifier(armorModLoc2);
                    }
                    var toughnessAttr2 = self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS);
                    if (toughnessAttr2 != null) {
                        net.minecraft.resources.ResourceLocation toughModLoc2 =
                                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                        "zhonz_more_enchantments", "toughness_tough_axe_" + self.getId());
                        toughnessAttr2.removeModifier(toughModLoc2);
                    }
                }
            }));
        }

        if (self.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, self.getX(), self.getY(), self.getZ(),
                    net.minecraft.sounds.SoundEvents.SHIELD_BREAK,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.7f);
        }
    }
}
