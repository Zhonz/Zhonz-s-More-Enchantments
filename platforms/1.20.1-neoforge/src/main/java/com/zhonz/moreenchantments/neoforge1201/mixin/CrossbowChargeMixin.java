package com.zhonz.moreenchantments.neoforge1201.mixin;

import com.zhonz.moreenchantments.common.enchant.EnchantIds;
import com.zhonz.moreenchantments.neoforge1201.CommonConstants1201;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.item.CrossbowItem;

/**
 * Mixin for CrossbowItem to implement the "爆裂黎明" (Explosive Dawn) enchantment:
 * Slows down the crossbow charge time significantly.
 *
 * 1.20.1 移植差异: 注入目标从 1.21 的 getUseDuration(ItemStack, LivingEntity)
 * 改为 1.20.1 的单参 getUseDuration(ItemStack) — 1.20.1 弩的 use duration =
 * getChargeDuration(stack) + 3, 且 releaseUsing/onUseTick 均以该值为准,
 * 翻倍后装填+自动发射的总时长翻倍, 语义与 1.21 一致。
 */
@Mixin(CrossbowItem.class)
public class CrossbowChargeMixin {

    /**
     * 弩的装填时间 (Ticks)
     * 默认: 25 ticks (1.25 秒)
     * 爆裂黎明: 翻倍到 50 ticks (2.5 秒)
     */
    @Inject(method = "getUseDuration", at = @At("RETURN"), cancellable = true)
    private void modifyUseDuration(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        int level = enchLevel(stack, EnchantIds.EXPLOSIVE_DAWN);
        if (level <= 0) return;

        // 装填时间翻倍
        cir.setReturnValue(cir.getReturnValue() * 2);
    }

    /** 物品自身是否带某附魔(id → ForgeRegistries, 对应 1.21 stack.getEnchantmentLevel(holder))。 */
    private static int enchLevel(ItemStack stack, String id) {
        if (stack.isEmpty()) return 0;
        Enchantment ench = ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation(CommonConstants1201.MODID, id));
        if (ench == null) return 0;
        return EnchantmentHelper.getItemEnchantmentLevel(ench, stack);
    }
}
