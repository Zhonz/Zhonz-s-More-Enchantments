package com.zhonz.moreenchantments.forge.mixin;

import com.zhonz.moreenchantments.common.enchant.EnchantIds;
import com.zhonz.moreenchantments.forge.CommonConstants1201;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for the three projectile weapons to implement the "神咒" (Divine Curse)
 * use duration doubling effect.
 *
 * Description: "蓄力速度、冷却时间翻倍" (charge speed and cooldown time doubled)
 * 项目武器(Bow/Trident/Crossbow)的充能/冷却时间翻倍: 玩家需要 2 倍的时间来蓄力.
 *
 * 1.20.1 关键修正: 1.20.1 的 BowItem / TridentItem / CrossbowItem **各自覆写**了
 * `getUseDuration(ItemStack)`, Mixin 注入基类方法不会拦截子类覆写 → 原先 @Mixin(Item.class)
 * 对这三种武器完全无效。改为多目标注入这三个类的覆写方法。
 * (弩的装填另由 CrossbowChargeMixin 处理, 与 1.21 设计一致。)
 */
@Mixin({net.minecraft.world.item.BowItem.class,
        net.minecraft.world.item.TridentItem.class,
        net.minecraft.world.item.CrossbowItem.class})
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
