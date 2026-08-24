package com.zhonz.moreenchantments.mixin;

import com.zhonz.moreenchantments.enchantment.ModEnchantments;
import com.zhonz.moreenchantments.event.ModEventHandlers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
    @Shadow
    public Container inputSlots;

    @Shadow
    public ResultContainer resultSlots;

    @ModifyConstant(method = "createResult", constant = @Constant(intValue = 40))
    private static int modifyMaxCost(int original) {
        return Integer.MAX_VALUE;
    }

    @Inject(method = "createResult", at = @At("RETURN"))
    private void mergeBloodPathKills(CallbackInfo ci) {
        ItemStack left = inputSlots.getItem(0);
        ItemStack right = inputSlots.getItem(1);
        ItemStack result = resultSlots.getItem(0);
        if (left.isEmpty() || right.isEmpty() || result.isEmpty()) return;
        if (result.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.BLOOD_PATH)) <= 0) return;

        boolean leftHas = left.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.BLOOD_PATH)) > 0;
        boolean rightHas = right.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.BLOOD_PATH)) > 0;
        if (!leftHas && !rightHas) return;

        CompoundTag merged = new CompoundTag();
        if (leftHas) {
            CompoundTag leftKills = ModEventHandlers.getBloodPathKillTag(left);
            for (String key : leftKills.getAllKeys()) {
                merged.putInt(key, leftKills.getInt(key));
            }
        }
        if (rightHas) {
            CompoundTag rightKills = ModEventHandlers.getBloodPathKillTag(right);
            for (String key : rightKills.getAllKeys()) {
                int rightVal = rightKills.getInt(key);
                int existing = merged.contains(key, CompoundTag.TAG_INT) ? merged.getInt(key) : 0;
                merged.putInt(key, existing + rightVal);
            }
        }
        if (!merged.isEmpty()) {
            ModEventHandlers.setBloodPathKillTag(result, merged);
        }
    }
}
