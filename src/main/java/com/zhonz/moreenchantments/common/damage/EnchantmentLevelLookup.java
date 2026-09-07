package com.zhonz.moreenchantments.common.damage;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/**
 * 附魔等级查询抽象(stage 跨版本层)。
 *
 * 平台差异点: 1.21 数据驱动附魔用 {@code ItemStack.getEnchantmentLevel(Holder)} 且键为
 * ResourceKey<Enchantment>; 1.20.1 代码注册 + 不同查询 API —— 故 common 判定
 * 以<b>附魔 id 字符串</b>(见 {@code common.enchant.EnchantIds}, 如 "liberator")
 * 查询; 平台实现负责把 id 映射到本版本注册/键并读取等级。
 */
public interface EnchantmentLevelLookup {

    /** 全装备槽中最高等级(某附魔)。@param enchantId 附魔 id 如 "liberator" */
    int anySlot(LivingEntity entity, String enchantId);

    /** 主手等级。 */
    int mainHand(LivingEntity entity, String enchantId);

    /** 指定槽位等级。 */
    int slot(LivingEntity entity, String enchantId, EquipmentSlot slot);
}
