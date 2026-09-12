package com.zhonz.moreenchantments.util;

import com.zhonz.moreenchantments.enchantment.ModEnchantments;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * "于此显圣"的共享触发逻辑。
 *
 * 设计口径(用户确认): 只要「免死」生效, 且该实体身上持有带「于此显圣」的不死图腾,
 * 就触发显圣(周围生物定身 3 秒 / 自身抗性提升 V 30 秒)。
 * 覆盖 4 条免死路径:
 * <ul>
 *   <li>原版不死图腾消耗 —— {@code ManifestTotemMixin} 钩 checkTotemDeathProtection</li>
 *   <li>智能图腾(从背包消耗图腾) —— 看被消耗的那颗图腾</li>
 *   <li>自地狱中归来 / 神护 —— 看主手/副手是否持有该附魔图腾</li>
 * </ul>
 *
 * 注意: 本类是普通工具类, 不放 mixin 包(mixin 类不可被外部引用); mixin 反向引用本类是允许的。
 */
public final class ManifestHelper {

    /** 显圣范围(格)。 */
    private static final double RADIUS = 16.0;
    /** 周围生物定身时长(tick): 3 秒。 */
    private static final int SLOW_TICKS = 60;
    /** 自身抗性提升时长(tick): 30 秒。 */
    private static final int RESISTANCE_TICKS = 600;

    private ManifestHelper() {
    }

    /** 该物品是否为带「于此显圣」的不死图腾。 */
    public static boolean hasManifest(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.TOTEM_OF_UNDYING)
                && stack.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.MANIFEST)) > 0;
    }

    /** 主手/副手是否持有带「于此显圣」的不死图腾。 */
    public static boolean holdsManifest(LivingEntity entity) {
        return hasManifest(entity.getItemBySlot(EquipmentSlot.MAINHAND))
                || hasManifest(entity.getItemBySlot(EquipmentSlot.OFFHAND));
    }

    /**
     * 触发显圣: 周围 16 格内生物(不含自身)定身 3 秒, 自身获得抗性提升 V 30 秒。
     *
     * 调用时机: 必须在免死的 removeAllEffects() 之后调用, 否则自身的抗性提升会被清掉。
     */
    public static void burst(LivingEntity self) {
        for (LivingEntity e : self.level().getEntitiesOfClass(LivingEntity.class, self.getBoundingBox().inflate(RADIUS))) {
            if (e != self && e.isAlive()) {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, 10, false, false));
            }
        }
        self.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, RESISTANCE_TICKS, 4, false, false));
    }
}
