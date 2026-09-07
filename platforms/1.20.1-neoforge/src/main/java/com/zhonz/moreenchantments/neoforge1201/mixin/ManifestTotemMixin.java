package com.zhonz.moreenchantments.neoforge1201.mixin;

import com.zhonz.moreenchantments.common.enchant.EnchantIds;
import com.zhonz.moreenchantments.neoforge1201.CommonConstants1201;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "于此显圣": 不死图腾被触发消耗时,周围全部生物无法移动3秒,自身获得30秒抗性提升V。
 *
 * 1.21.1 NeoForge → 1.20.1 Forge 移植差异:
 * - 注入点 LivingEntity.checkTotemDeathProtection(DamageSource) 两版均存在(1.20.1 mojmap 同为
 *   private boolean checkTotemDeathProtection(DamageSource)), 无需调整。
 * - 附魔等级: 1.21 用 stack.getEnchantmentLevel(Holder) → 1.20.1 用
 *   EnchantmentHelper.getItemEnchantmentLevel(ForgeRegistries 查得的 Enchantment, stack)。
 * - 其余 API(level()/getEntitiesOfClass/addEffect/MobEffects) 两版一致。
 */
@Mixin(LivingEntity.class)
public abstract class ManifestTotemMixin {

    @Unique
    private boolean zhonz$manifestTriggered = false;

    @Inject(method = "checkTotemDeathProtection", at = @At("HEAD"))
    private void zhonz$preTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        zhonz$manifestTriggered = zhonz$hasManifest(self.getItemBySlot(EquipmentSlot.MAINHAND))
                || zhonz$hasManifest(self.getItemBySlot(EquipmentSlot.OFFHAND));
    }

    @Inject(method = "checkTotemDeathProtection", at = @At("RETURN"))
    private void zhonz$postTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && zhonz$manifestTriggered) {
            LivingEntity self = (LivingEntity) (Object) this;
            for (LivingEntity e : self.level().getEntitiesOfClass(LivingEntity.class, self.getBoundingBox().inflate(16.0))) {
                if (e != self && e.isAlive()) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 10, false, false));
                }
            }
            self.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 600, 4, false, false));
        }
        zhonz$manifestTriggered = false;
    }

    @Unique
    private static boolean zhonz$hasManifest(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.TOTEM_OF_UNDYING)
                && zhonz$enchLevel(stack, EnchantIds.MANIFEST) > 0;
    }

    /** 物品自身是否带某附魔(id → ForgeRegistries, 对应 1.21 stack.getEnchantmentLevel(holder))。 */
    @Unique
    private static int zhonz$enchLevel(ItemStack stack, String id) {
        Enchantment ench = ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation(CommonConstants1201.MODID, id));
        if (ench == null) return 0;
        return EnchantmentHelper.getItemEnchantmentLevel(ench, stack);
    }
}
