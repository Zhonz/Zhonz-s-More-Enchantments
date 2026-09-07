package com.zhonz.moreenchantments.forge.mixin;

import com.zhonz.moreenchantments.common.enchant.EnchantIds;
import com.zhonz.moreenchantments.forge.CommonConstants1201;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for Item to implement the "神咒" (Divine Curse) enchantment's
 * use duration doubling effect.
 *
 * Description: "蓄力速度、冷却时间翻倍" (charge speed and cooldown time doubled)
 * 项目武器(Bow/Trident/Crossbow)的充能/冷却时间翻倍: 玩家需要 2 倍的时间来蓄力.
 *
 * 1.20.1 移植差异: 注入目标从 1.21 的 Item.getUseDuration(ItemStack, LivingEntity)
 * 改为 1.20.1 的单参 Item.getUseDuration(ItemStack)。1.20.1 各武器(Bow 72000 /
 * Trident 72000 / Crossbow chargeDuration+3)均覆写该方法, 此处拦截基类实现;
 * 弩的翻倍另行由 CrossbowChargeMixin 在 CrossbowItem 覆写上处理, 与本 mixin
 * 行为一致(与 1.21 相同设计)。
 */
@Mixin(Item.class)
public class ProjectileWeaponCooldownMixin {

    @Inject(method = "getUseDuration", at = @At("RETURN"), cancellable = true)
    private void modifyUseDuration(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        int level = enchLevel(stack, EnchantIds.DIVINE_CURSE);
        if (level <= 0) return;

        int original = cir.getReturnValue();
        // 充能/冷却时间翻倍(取最大值避免变成0)
        cir.setReturnValue(Math.max(1, original * 2));
    }

    /** 物品自身是否带某附魔(id → ForgeRegistries, 对应 1.21 stack.getEnchantmentLevel(holder))。 */
    private static int enchLevel(ItemStack stack, String id) {
        if (stack.isEmpty()) return 0;
        Enchantment ench = ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation(CommonConstants1201.MODID, id));
        if (ench == null) return 0;
        return EnchantmentHelper.getItemEnchantmentLevel(ench, stack);
    }
}
