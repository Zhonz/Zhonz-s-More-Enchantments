package com.zhonz.moreenchantments.util;

import com.zhonz.moreenchantments.enchantment.ModEnchantments;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 攻击伤害类型转换的统一入口(在 hurt() HEAD 调用):
 *
 * - 主手 哭泣之子   → 攻击转为 weeping_fire(带穿透语义, 见 FirePierceMixin 系)
 * - 主手 雪的伤     → 攻击转为 frost(冰霜, 已加入 is_freezing)
 * - 身穿 唯有命运   → 攻击转为 true_damage(真伤, ×6)
 *
 * 只做一次转换, 递归以"源已是这些类型之一"防住; 玩家路径先于难度缩放拦截,
 * 避免二次缩放(见 PlayerWeepingFireMixin)。
 *
 * 注意: 本类是普通工具类, 绝不能放进 mixin 包(否则被 mixin 类加载规则禁止从外部引用)。
 */
public final class WeepingFireHelper {

    private static final String MODID = com.zhonz.moreenchantments.common.CommonConstants.MODID;

    public static final ResourceKey<DamageType> WEEPING_FIRE = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(MODID, "weeping_fire"));
    public static final ResourceKey<DamageType> FROST = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(MODID, "frost"));
    public static final ResourceKey<DamageType> TRUE_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(MODID, "true_damage"));

    private WeepingFireHelper() {
    }

    /** 哭泣之火穿透判定: 该火焰是否由哭泣之子持有者造成(无视对方火免/抗火)。 */
    public static boolean isPiercingWeepingFire(DamageSource source) {
        if (!source.is(WEEPING_FIRE)) return false;
        return source.getEntity() instanceof LivingEntity le && hasEnchant(le, EquipmentSlot.MAINHAND, ModEnchantments.WEEPING_CHILD);
    }

    /** 返回 true 表示已在本层完成转换(调用方应 setReturnValue 并中止原逻辑)。 */
    public static boolean tryConvert(LivingEntity self, DamageSource source, float amount,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (self.level().isClientSide()) return false;
        if (source.is(WEEPING_FIRE) || source.is(FROST) || source.is(TRUE_DAMAGE)) return false; // 已转换(重放层)
        if (amount <= 0.0F) return false;
        if (self.isInvulnerableTo(source)) return false; // 原本就打不中, 无需转换

        AttackerType at = findWielderType(source);
        if (at == null || at.attacker == self) return false;

        Registry<DamageType> reg = self.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        Holder.Reference<DamageType> holder = reg.getHolderOrThrow(at.typeKey);
        DamageSource newSource = new DamageSource(holder, at.attacker, at.attacker);
        float newAmount = at.multiplier == 1.0f ? amount : amount * at.multiplier;
        boolean result = self.hurt(newSource, newAmount);
        cir.setReturnValue(result);
        return true;
    }

    private record AttackerType(LivingEntity attacker, ResourceKey<DamageType> typeKey, float multiplier) {
    }

    private static AttackerType findWielderType(DamageSource source) {
        Entity direct = source.getDirectEntity();
        Entity causing = source.getEntity();

        // 近战 / 直接: 直接实体 == 造成者
        if (direct == causing && causing instanceof LivingEntity le) {
            if (hasEnchant(le, EquipmentSlot.MAINHAND, ModEnchantments.WEEPING_CHILD)) {
                return new AttackerType(le, WEEPING_FIRE, 1.0f);
            }
            if (hasEnchant(le, EquipmentSlot.MAINHAND, ModEnchantments.SNOW_WOUND)) {
                return new AttackerType(le, FROST, 1.0f);
            }
            if (wearsUnyieldingFate(le)) {
                return new AttackerType(le, TRUE_DAMAGE, 6.0f);
            }
            // 挂(hang): 文档 #27(ENCHANTMENTS.md L162)「在背包内时…攻击造成真实伤害」。
            // 原实现完全没有这条路径(真伤只由唯有命运产生), 属**文档要求但未实现**;
            // 文档未给倍率, 故取 ×1。放在最后 → 不影响哭泣之子/雪的伤/唯有命运既有的优先级。
            if (hasHangInInventory(le)) {
                return new AttackerType(le, TRUE_DAMAGE, 1.0f);
            }
        }
        // 投射物: 直接实体是箭/三叉戟/雪球等
        if (direct instanceof Projectile proj) {
            Entity owner = proj.getOwner();
            if (owner == causing && causing instanceof LivingEntity shooter) {
                if (hasEnchant(shooter, EquipmentSlot.MAINHAND, ModEnchantments.WEEPING_CHILD)) {
                    return new AttackerType(shooter, WEEPING_FIRE, 1.0f);
                }
                if (hasEnchant(shooter, EquipmentSlot.MAINHAND, ModEnchantments.SNOW_WOUND)) {
                    return new AttackerType(shooter, FROST, 1.0f);
                }
                if (wearsUnyieldingFate(shooter)) {
                    return new AttackerType(shooter, TRUE_DAMAGE, 6.0f);
                }
                // 挂(hang) 的"攻击"不限于近战 → 远程同样转为真伤(同上, ×1)
                if (hasHangInInventory(shooter)) {
                    return new AttackerType(shooter, TRUE_DAMAGE, 1.0f);
                }
            }
            // 投掷三叉戟: 掷出后主手已空, 附魔在三叉戟自身物品上
            if (direct instanceof ThrownTrident tt && !tt.getWeaponItem().isEmpty()
                    && tt.getWeaponItem().getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.WEEPING_CHILD)) > 0) {
                if (owner instanceof LivingEntity wielder) {
                    return new AttackerType(wielder, WEEPING_FIRE, 1.0f);
                }
            }
        }
        return null;
    }

    /**
     * 挂(hang) 的触发条件: **背包内**有带该附魔的物品
     * (文档 #27「在背包内时获得无敌、飞行、速度 7、力量 255;周围 35 格内的敌对生物被斩杀;攻击造成真实伤害」)。
     *
     * <p>只有玩家有"背包"; 其它生物没有该附魔的载体, 因此对非玩家恒为 false。
     */
    public static boolean hasHangInInventory(LivingEntity entity) {
        if (!(entity instanceof net.minecraft.world.entity.player.Player player)) return false;
        var holder = ModEnchantments.getHolder(ModEnchantments.HANG);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getEnchantmentLevel(holder) > 0) return true;
        }
        return false;
    }

    private static boolean hasEnchant(LivingEntity entity, EquipmentSlot slot, ResourceKey<net.minecraft.world.item.enchantment.Enchantment> ench) {
        ItemStack stack = entity.getItemBySlot(slot);
        if (stack.isEmpty()) return false;
        // 缺陷修复(03-E2 同族, 最后两处客户端可达路径的收口):
        // 本方法经 isPiercingWeepingFire(48) 被 FireImmunePierceMixin:28 / FireResistancePierceMixin:29
        // 的 @Redirect 调用 —— 目标是 Entity.isInvulnerableTo / LivingEntity.hurt, 两者都不是服务端专属方法,
        // 远程客户端的预测路径会进来; 而 getHolder 依赖 ServerLifecycleHooks.getCurrentServer(),
        // 远程客户端没有服务端句柄 → NPE。
        // 改走不依赖 Holder 的读法(直接读 ItemStack 组件并比对 registry key), 两侧都可用、永不抛异常。
        return ModEnchantments.getLevel(stack, ench) > 0;
    }

    /** 唯有命运: 任一身装备有该附魔(胸甲优先, 但为稳判四件都查)。 */
    public static boolean wearsUnyieldingFate(LivingEntity entity) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (hasEnchant(entity, slot, ModEnchantments.UNYIELDING_FATE)) return true;
        }
        return false;
    }
}
