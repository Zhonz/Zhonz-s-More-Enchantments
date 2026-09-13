package com.zhonz.moreenchantments.forge.mixin;

import com.zhonz.moreenchantments.common.enchant.EnchantIds;
import com.zhonz.moreenchantments.forge.DamageTypes1201;
import com.zhonz.moreenchantments.forge.EnchantmentLookup1201;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "比任何人都要悲伤的哭泣之子": 攻击造成真·火焰伤害。
 *
 * 拦截 {@link LivingEntity#hurt}(非玩家防御者路径; 玩家防御者由
 * {@link PlayerWeepingFireMixin} 在 Player.hurt 的难度缩放之前拦截, 避免二次缩放)。
 *
 * 1.21.1 NeoForge → 1.20.1 Forge 移植说明:
 * - 注入点两版相同(1.20.1 LivingEntity.hurt(DamageSource,float) 亦在 ForgeHooks/无敌/抗火判定之前)。
 * - <b>三条伤害类型转换全部落地</b>(2026-09-12 round-close): 1.21 用数据驱动自定义 damage_type
 *   (weeping_fire / frost / true_damage); 1.20.1 改为**代码注册**
 *   ({@link com.zhonz.moreenchantments.forge.DamageTypes1201}) + 同名 JSON 描述
 *   (resources/data/zhonz_more_enchantments/damage_type/) + 标签声明穿透语义
 *   (resources/data/minecraft/tags/damage_type/: true_damage→bypasses_armor/enchantments/
 *   resistance/effects, frost→is_freezing, weeping_fire→is_fire)。与 1.21 语义等价。
 * - 判定与倍率: 哭泣之子 → weeping_fire ×1; 雪的伤 → frost ×1; 唯有命运 → true_damage **×6**
 *   (1.21 源 WeepingFireHelper.findWielderType, 优先级同序)。
 * - "是否该转换"的判定保留(与 1.21 一致): 近战直接实体==造成者 且 主手持哭泣之子;
 *   投射物 owner==造成者 且 造成者主手持哭泣之子; 投掷三叉戟则读<b>三叉戟自身物品</b>的附魔
 *   (1.21 {@code ThrownTrident.getWeaponItem()} → 1.20.1 {@link ThrownTridentAccessor} 读 private
 *   tridentItem 字段, 已补齐)。
 * - 递归护栏: 1.21 用 source.is(WEEPING_FIRE/FROST/TRUE_DAMAGE) 判"已转换";
 *   1.20.1 用 source.is(DamageTypeTags.IS_FIRE)(凡火焰不二次转换, 与"已转换"语义等价且更宽)。
 * - 火焰穿透语义(无视火免/抗火)由 FireImmunePierceMixin / FireResistancePierceMixin 提供,
 *   二者共用"火焰 + 由主手持哭泣之子者造成"判定 —— 与 1.21 source.is(WEEPING_FIRE) 判定相比,
 *   1.20.1 改判"是火焰 tag 且造成者主手持哭泣之子"(原版火焰多数无造成者 → 误伤可控)。
 */
@Mixin(LivingEntity.class)
public abstract class WeepingFireMixin {

    @Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At("HEAD"), cancellable = true)
    private void zhonz$weepingFireConversion(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide()) return;
        // 递归护栏(与 1.21 source.is(WEEPING_FIRE/FROST/TRUE_DAMAGE) 等价): 已是本 mod 的三种
        // 自定义类型 → 不再二次转换; 另任何火焰也直接返回(原版火焰无需转换)。
        if (zhonz$isConverted(source) || source.is(DamageTypeTags.IS_FIRE)) return;
        if (amount <= 0.0F) return;
        if (self.isInvulnerableTo(source)) return; // 原本就打不中, 无需转换

        AttackerType at = zhonz$findWielderType(source);
        if (at == null || at.attacker == self) return;

        Holder<DamageType> holder = self.level().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(at.type);
        DamageSource converted = new DamageSource(holder, at.attacker, at.attacker);
        float newAmount = at.multiplier == 1.0F ? amount : amount * at.multiplier;
        boolean result = self.hurt(converted, newAmount);
        cir.setReturnValue(result);
    }

    /** 攻击者 + 目标伤害类型 + 倍率(等价 1.21 {@code WeepingFireHelper.AttackerType})。 */
    @Unique
    private record AttackerType(LivingEntity attacker, net.minecraft.resources.ResourceKey<DamageType> type,
                               float multiplier) {
    }

    /** 该源是否已是本 mod 的自定义伤害类型(重放层护栏)。 */
    @Unique
    private static boolean zhonz$isConverted(DamageSource source) {
        return source.is(DamageTypes1201.WEEPING_FIRE.getKey())
                || source.is(DamageTypes1201.FROST.getKey())
                || source.is(DamageTypes1201.TRUE_DAMAGE.getKey());
    }

    /**
     * 找出"有效攻击者 + 伤害类型 + 倍率"(1.21 源: {@code WeepingFireHelper.findWielderType})。
     *
     * <p>判定优先级与 1.21 严格一致: 悲伤之子 → 雪的伤 → 唯有命运;
     * 近战直接命中取攻击者主手, 投射物取 shooter 主手, 投掷三叉戟取三叉戟自身物品。
     */
    @Unique
    private static AttackerType zhonz$findWielderType(DamageSource source) {
        Entity direct = source.getDirectEntity();
        Entity causing = source.getEntity();

        // 近战 / 直接: 直接实体 == 造成者
        if (direct != null && direct == causing && direct instanceof LivingEntity le) {
            AttackerType t = zhonz$typeFor(le);
            if (t != null) return t;
        }
        // 投射物: 直接实体是箭/三叉戟/雪球等, owner == 造成者
        if (direct instanceof Projectile proj && causing instanceof LivingEntity shooter
                && proj.getOwner() == causing) {
            AttackerType t = zhonz$typeFor(shooter);
            if (t != null) return t;
        }
        // 投掷三叉戟: 掷出后抬手已空, 附魔在三叉戟自身物品上(1.21 getWeaponItem → 1.20.1 @Accessor)
        if (direct instanceof ThrownTrident tt) {
            ItemStack weapon = ((ThrownTridentAccessor) tt).zhonz$getTridentItem();
            if (weapon != null && !weapon.isEmpty()
                    && zhonz$hasEnchant(weapon, EnchantIds.WEEPING_CHILD)
                    && tt.getOwner() instanceof LivingEntity wielder) {
                return new AttackerType(wielder, DamageTypes1201.WEEPING_FIRE.getKey(), 1.0F);
            }
        }
        return null;
    }

    /** 按"主手附魔 / 全身唯有命运"给出攻击者的伤害类型(1.21 同序: 哭泣之子 → 雪的伤 → 唯有命运)。 */
    @Unique
    private static AttackerType zhonz$typeFor(LivingEntity le) {
        if (zhonz$hasWeeping(le)) {
            return new AttackerType(le, DamageTypes1201.WEEPING_FIRE.getKey(), 1.0F);
        }
        if (EnchantmentLookup1201.INSTANCE.mainHand(le, EnchantIds.SNOW_WOUND) > 0) {
            return new AttackerType(le, DamageTypes1201.FROST.getKey(), 1.0F);
        }
        if (zhonz$wearsUnyieldingFate(le)) {
            return new AttackerType(le, DamageTypes1201.TRUE_DAMAGE.getKey(), 6.0F);
        }
        return null;
    }

    /** 唯有命运: 任一件盔甲带该附魔(1.21 源: {@code WeepingFireHelper.wearsUnyieldingFate})。 */
    @Unique
    private static boolean zhonz$wearsUnyieldingFate(LivingEntity entity) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (EnchantmentLookup1201.INSTANCE.slot(entity, EnchantIds.UNYIELDING_FATE, slot) > 0) return true;
        }
        return false;
    }

    /** 近战 / 投射物路径: 攻击者主手是否带某附魔。 */
    @Unique
    private static LivingEntity zhonz$findWeepingAttacker(DamageSource source) {
        Entity direct = source.getDirectEntity();
        Entity causing = source.getEntity();

        if (direct != null && direct == causing && direct instanceof LivingEntity le) {
            if (zhonz$hasWeeping(le)) return le;
        }
        if (direct instanceof Projectile proj && causing instanceof LivingEntity shooter) {
            if (proj.getOwner() == causing && zhonz$hasWeeping(shooter)) return shooter;
        }
        if (direct instanceof ThrownTrident tt) {
            ItemStack weapon = ((ThrownTridentAccessor) tt).zhonz$getTridentItem();
            if (weapon != null && !weapon.isEmpty() && zhonz$hasWeeping(weapon)
                    && tt.getOwner() instanceof LivingEntity wielder) {
                return wielder;
            }
        }
        return null;
    }

    /** 物品自身是否带"哭泣之子"(1.20.1: ForgeRegistries 查 → getEnchantmentLevel)。 */
    @Unique
    private static boolean zhonz$hasWeeping(ItemStack stack) {
        return zhonz$hasEnchant(stack, EnchantIds.WEEPING_CHILD);
    }

    /** 物品自身是否带指定附魔(id → ForgeRegistries, 对应 1.21 stack.getEnchantmentLevel(holder))。 */
    @Unique
    private static boolean zhonz$hasEnchant(ItemStack stack, String enchantId) {
        net.minecraft.world.item.enchantment.Enchantment ench =
                net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS.getValue(
                        new net.minecraft.resources.ResourceLocation(
                                com.zhonz.moreenchantments.forge.CommonConstants1201.MODID,
                                enchantId));
        if (ench == null) return false;
        return net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(ench, stack) > 0;
    }

    /** 主手是否持"哭泣之子"附魔(1.20.1 经 EnchantmentLookup1201 查 ForgeRegistries)。 */
    @Unique
    private static boolean zhonz$hasWeeping(LivingEntity entity) {
        return EnchantmentLookup1201.INSTANCE.mainHand(entity, EnchantIds.WEEPING_CHILD) > 0;
    }
}
