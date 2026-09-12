package com.zhonz.moreenchantments.neoforge1201;

import com.zhonz.moreenchantments.common.enchant.EnchantIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * "于此显圣"的共享触发逻辑(1.20.1 NeoForge 平台版, 对应 1.21 主工程的 util/ManifestHelper)。
 *
 * 设计口径: 只要「免死」生效, 且该实体身上持有带「于此显圣」的不死图腾, 就触发显圣
 * (周围生物定身 3 秒 / 自身抗性提升 V 30 秒)。覆盖 4 条免死路径:
 * 原版图腾消耗(ManifestTotemMixin) / 智能图腾(看被消耗的那颗图腾) /
 * 自地狱中归来 / 神护(看主手副手是否持有该附魔图腾)。
 *
 * 1.20.1 差异: 附魔等级经 ForgeRegistries + EnchantmentHelper 查得(1.21 用 stack.getEnchantmentLevel(Holder))。
 */
public final class ManifestHelper1201 {

    /** 显圣范围(格)。 */
    private static final double RADIUS = 16.0;
    /** 周围生物定身时长(tick): 3 秒。 */
    private static final int SLOW_TICKS = 60;
    /** 自身抗性提升时长(tick): 30 秒。 */
    private static final int RESISTANCE_TICKS = 600;

    private ManifestHelper1201() {
    }

    /** 该物品是否为带「于此显圣」的不死图腾。 */
    public static boolean hasManifest(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.TOTEM_OF_UNDYING)
                && enchLevel(stack, EnchantIds.MANIFEST) > 0;
    }

    /** 主手/副手是否持有带「于此显圣」的不死图腾。 */
    public static boolean holdsManifest(LivingEntity entity) {
        return hasManifest(entity.getItemBySlot(EquipmentSlot.MAINHAND))
                || hasManifest(entity.getItemBySlot(EquipmentSlot.OFFHAND));
    }

    /**
     * 触发显圣: 周围 16 格内生物(不含地身)定身 3 秒, 地身获得抗性提升 V 30 秒。
     * 必须在免死的 removeAllEffects() 之后调用, 否则自身抗性提升会被清掉。
     */
    public static void burst(LivingEntity self) {
        for (LivingEntity e : self.level().getEntitiesOfClass(LivingEntity.class, self.getBoundingBox().inflate(RADIUS))) {
            if (e != self && e.isAlive()) {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, 10, false, false));
            }
        }
        self.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, RESISTANCE_TICKS, 4, false, false));
    }

    /** 物品地身是否带某附魔(id → ForgeRegistries)。 */
    private static int enchLevel(ItemStack stack, String id) {
        Enchantment ench = ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation(CommonConstants1201.MODID, id));
        if (ench == null) return 0;
        return EnchantmentHelper.getItemEnchantmentLevel(ench, stack);
    }
}
