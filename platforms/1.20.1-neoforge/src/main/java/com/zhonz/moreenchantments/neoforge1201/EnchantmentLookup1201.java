package com.zhonz.moreenchantments.neoforge1201;

import com.zhonz.moreenchantments.common.damage.EnchantmentLevelLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.20.1 Forge 平台: 附魔等级查询实现(id → ForgeRegistries.ENCHANTMENTS 查注册)。
 *
 * common 判定以 EnchantIds 字符串查询等级; 本实现把 id 映射到 1.20.1 代码注册的
 * Enchantment(ForgeRegistries), 再用 1.20.1 ItemStack.getEnchantmentLevel(Enchantment) 读等级。
 */
public final class EnchantmentLookup1201 implements EnchantmentLevelLookup {

    public static final EnchantmentLookup1201 INSTANCE = new EnchantmentLookup1201();

    private EnchantmentLookup1201() {
    }

    private Enchantment ench(String id) {
        ResourceLocation rl = new ResourceLocation(CommonConstants1201.MODID, id);
        Enchantment e = ForgeRegistries.ENCHANTMENTS.getValue(rl);
        return e;
    }

    private int levelOf(LivingEntity entity, String id, EquipmentSlot slot) {
        Enchantment e = ench(id);
        if (e == null) return 0;
        return entity.getItemBySlot(slot).getEnchantmentLevel(e);
    }

    @Override
    public int anySlot(LivingEntity entity, String enchantId) {
        int max = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            int lv = levelOf(entity, enchantId, slot);
            if (lv > max) max = lv;
        }
        return max;
    }

    @Override
    public int mainHand(LivingEntity entity, String enchantId) {
        return levelOf(entity, enchantId, EquipmentSlot.MAINHAND);
    }

    @Override
    public int slot(LivingEntity entity, String enchantId, EquipmentSlot slot) {
        return levelOf(entity, enchantId, slot);
    }
}
