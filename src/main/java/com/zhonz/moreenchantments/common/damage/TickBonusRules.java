package com.zhonz.moreenchantments.common.damage;

import com.zhonz.moreenchantments.common.enchant.EnchantIds;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 无条件/自身状态可 tick 的加伤 percent 计算(stage2 下沉)。
 *
 * 每个方法返回应写入 bonus_damage 的百分比(0.2 = +20%); 写入动作(事件层
 * addPercentBonus)与副作用(攻击速度/范围等属性)留在平台层 tick 函数。
 * 不 import 任何平台 API; 等级查询经 {@link EnchantmentLevelLookup} 注入。
 */
public final class TickBonusRules {

    private TickBonusRules() {
    }

    /** 7. 至高的艺术(主手): +20%/级。 */
    public static double supremeArt(EnchantmentLevelLookup lv, LivingEntity entity) {
        int level = lv.mainHand(entity, EnchantIds.SUPREME_ART);
        return level > 0 ? 0.2 * level : 0.0;
    }

    /** 50. 新太阳(护腿): +150% ×(光照/15), 0 光 = 0。 */
    public static double newSun(EnchantmentLevelLookup lv, LivingEntity entity) {
        if (lv.slot(entity, EnchantIds.NEW_SUN, EquipmentSlot.LEGS) <= 0) return 0.0;
        int light = entity.level().getMaxLocalRawBrightness(entity.blockPosition());
        return (light / 15.0) * 1.5;
    }

    /** 46. 困兽之斗(头盔, 生命<25%): +60%。 */
    public static double corneredBeast(EnchantmentLevelLookup lv, LivingEntity entity) {
        boolean active = lv.slot(entity, EnchantIds.CORNERED_BEAST, EquipmentSlot.HEAD) > 0
                && entity.getHealth() <= entity.getMaxHealth() * 0.25f;
        return active ? 0.60 : 0.0;
    }

    /** 52. 极速攀升(靴子): y>0 时 +y/100(y=20 → +20%)。 */
    public static double rapidAscent(EnchantmentLevelLookup lv, LivingEntity entity) {
        if (lv.slot(entity, EnchantIds.RAPID_ASCENT, EquipmentSlot.FEET) <= 0) return 0.0;
        double y = entity.getY();
        return y > 0 ? y * 0.01 : 0.0;
    }

    /** 57. 悲伤的红(胸甲): 背包每格有物品 +10%。 */
    public static double sorrowfulRed(EnchantmentLevelLookup lv, LivingEntity entity) {
        if (lv.slot(entity, EnchantIds.SORROWFUL_RED, EquipmentSlot.CHEST) <= 0) return 0.0;
        if (!(entity instanceof Player player)) return 0.0;
        int filled = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (!player.getInventory().getItem(i).isEmpty()) filled++;
        }
        return 0.10 * filled;
    }
}
