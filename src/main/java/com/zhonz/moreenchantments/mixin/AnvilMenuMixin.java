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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Mixin(value = ItemCombinerMenu.class, remap = false)
public abstract class AnvilMenuMixin {

    // Field SRG IDs from NeoFormRuntime joined.tsrg for ItemCombinerMenu
    private static final int SRG_INPUT_SLOTS = 39769;
    private static final int SRG_RESULT_SLOTS = 39768;
    // Method SRG ID for createResult()V in ItemCombinerMenu: m 266110
    private static final int SRG_CREATE_RESULT = 266110;

    private static final Field F_INPUT_SLOTS = resolveField(SRG_INPUT_SLOTS, Container.class);
    private static final Field F_RESULT_SLOTS = resolveField(SRG_RESULT_SLOTS, ResultContainer.class);
    private static final boolean FIELDS_OK = F_INPUT_SLOTS != null && F_RESULT_SLOTS != null;

    private static Field resolveField(int srgId, Class<?> expectedType) {
        // Try several SRG-style patterns
        String[] patterns = {"f_" + srgId + "_", "f_" + srgId, "f" + srgId, "field_" + srgId + "_"};
        for (Field f : ItemCombinerMenu.class.getDeclaredFields()) {
            String n = f.getName();
            for (String p : patterns) if (n.equals(p)) { try { f.setAccessible(true); return f; } catch (Throwable ignored) {} }
        }
        // Fallback: first field of matching type (deterministic order by declared fields)
        Field fallback = null;
        for (Field f : ItemCombinerMenu.class.getDeclaredFields()) {
            if (expectedType.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                fallback = f;
                break;
            }
        }
        return fallback;
    }

    private static String resolveCreateResultMethodName() {
        String[] patterns = {"m_" + SRG_CREATE_RESULT + "_", "m_" + SRG_CREATE_RESULT, "m" + SRG_CREATE_RESULT,
                "method_" + SRG_CREATE_RESULT + "_", "createResult", "m"};
        // Look for exact pattern matches first
        for (Method m : ItemCombinerMenu.class.getDeclaredMethods()) {
            String n = m.getName();
            Class<?>[] params = m.getParameterTypes();
            if (params.length == 0 && m.getReturnType() == void.class) {
                for (String p : patterns) if (n.equals(p)) return n;
            }
        }
        // Find any parameter-less void method not matching standard Object methods or other known patterns
        // Known: createResult = m (one void param-less method)
        int voidNoParamCount = 0;
        String last = null;
        for (Method m : ItemCombinerMenu.class.getDeclaredMethods()) {
            if (m.getParameterTypes().length == 0 && m.getReturnType() == void.class) {
                String n = m.getName();
                if (n.equals("removed") || n.equals("stillValid") || n.equals("quickMoveStack")
                        || n.equals("canMoveIntoInputSlots") || n.equals("getInventorySlotStart")
                        || n.equals("getResultSlot") || n.equals("getInventorySlotEnd")
                        || n.equals("getUseRowStart") || n.equals("getUseRowEnd")
                        || n.equals("createContainer") || n.equals("createInputSlotDefinitions")
                        || n.equals("createResult")) continue;
                voidNoParamCount++;
                last = n;
            }
        }
        return last != null ? last : "createResult";
    }

    private static final String METHOD_CREATE_RESULT = resolveCreateResultMethodName();

    private static Container getInputSlots(Object menu) {
        try { return (Container) F_INPUT_SLOTS.get(menu); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    private static ResultContainer getResultSlots(Object menu) {
        try { return (ResultContainer) F_RESULT_SLOTS.get(menu); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    @ModifyConstant(method = "createResult", constant = @Constant(intValue = 40), remap = false, expect = 0)
    private static int modifyMaxCostA(int original) { return Integer.MAX_VALUE; }

    @ModifyConstant(method = "m", constant = @Constant(intValue = 40), remap = false, expect = 0)
    private static int modifyMaxCostB(int original) { return Integer.MAX_VALUE; }

    @Inject(method = "createResult", at = @At("RETURN"), remap = false, expect = 0)
    private void mergeBloodPathKillsA(CallbackInfo ci) { doMerge(); }

    @Inject(method = "m", at = @At("RETURN"), remap = false, expect = 0)
    private void mergeBloodPathKillsB(CallbackInfo ci) { doMerge(); }

    private void doMerge() {
        if (!FIELDS_OK) return;
        if (!((Object) this instanceof AnvilMenu)) return;
        Container inputSlots = getInputSlots(this);
        ResultContainer resultSlots = getResultSlots(this);
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
            for (String key : leftKills.getAllKeys()) merged.putInt(key, leftKills.getInt(key));
        }
        if (rightHas) {
            CompoundTag rightKills = ModEventHandlers.getBloodPathKillTag(right);
            for (String key : rightKills.getAllKeys()) {
                int rightVal = rightKills.getInt(key);
                int existing = merged.contains(key, CompoundTag.TAG_INT) ? merged.getInt(key) : 0;
                merged.putInt(key, existing + rightVal);
            }
        }
        if (!merged.isEmpty()) ModEventHandlers.setBloodPathKillTag(result, merged);
    }
}
