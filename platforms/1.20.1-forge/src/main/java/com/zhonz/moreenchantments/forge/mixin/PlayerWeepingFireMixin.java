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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "比任何人都要悲伤的哭泣之子" 的玩家防御者路径。
 *
 * Player 覆写了 {@code hurt(DamageSource, float)}(1.20.1 亦存在, 见下): 先做
 * ForgeHooks.onPlayerAttack / isInvulnerableTo / 创造模式无敌检查与难度缩放,
 * 再 invokespecial 调 LivingEntity.hurt。若只拦 LivingEntity.hurt,
 * 重放会再次经过 Player.hurt 的难度缩放 → 伤害二次缩放。因此在 Player.hurt 的
 * HEAD(缩放前)拦截并做与 WeepingFireMixin 相同的"哭泣之子 → 火焰"转换。
 *
 * 1.21.1 NeoForge → 1.20.1 Forge 移植差异:
 * - 注入点两版相同(1.20.1 Player.hurt(DamageSource,float) 覆写存在, 结构一致:
 *   onPlayerAttack → isInvulnerableTo → abilities.invulnerable → 难度缩放 → invokespecial hurt)。
 * - 转换/判定/护栏与 {@link WeepingFireMixin} 完全一致(见其类 javadoc 的 1.20.1 移植说明):
 *   1.20.1 无自定义 weeping_fire damage_type, 用 ON_FIRE + 攻击者归属近似; 递归护栏 = IS_FIRE。
 * - 附带收益: 因为拦截发生在缩放前, ON_FIRE 的 when_caused_by_living_non_player 缩放语义
 *   与原玩家/生物近战攻击的难度缩放基本一致, 不会出现 1.21 注释所担心的二次缩放。
 */
@Mixin(Player.class)
public abstract class PlayerWeepingFireMixin {

    @Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At("HEAD"), cancellable = true)
    private void zhonz$playerWeepingFireConversion(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        if (self.level().isClientSide()) return;
        // 递归护栏(与 WeepingFireMixin 同): 已是本 mod 自定义类型 / 任何火焰 → 不二次转换
        if (zhonz$isConverted(source) || source.is(DamageTypeTags.IS_FIRE)) return;
        if (amount <= 0.0F) return;
        if (self.isInvulnerableTo(source)) return;

        AttackerType at = zhonz$findWielderType(source);
        if (at == null || at.attacker == self) return;

        Holder<DamageType> holder = self.level().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(at.type);
        DamageSource converted = new DamageSource(holder, at.attacker, at.attacker);
        float newAmount = at.multiplier == 1.0F ? amount : amount * at.multiplier;
        cir.setReturnValue(self.hurt(converted, newAmount));
    }

    /** 攻击者 + 目标伤害类型 + 倍率(等价 1.21 {@code WeepingFireHelper.AttackerType})。 */
    @Unique
    private record AttackerType(LivingEntity attacker, net.minecraft.resources.ResourceKey<DamageType> type,
                               float multiplier) {
    }

    @Unique
    private static boolean zhonz$isConverted(DamageSource source) {
        return source.is(DamageTypes1201.WEEPING_FIRE.getKey())
                || source.is(DamageTypes1201.FROST.getKey())
                || source.is(DamageTypes1201.TRUE_DAMAGE.getKey());
    }

    /**
     * 有效攻击者 + 伤害类型 + 倍率(1.21 源: {@code WeepingFireHelper.findWielderType})。
     * 优先级同 1.21: 哭泣之子(weeping_fire ×1) → 雪的伤(frost ×1) → 唯有命运(true_damage ×6)。
     */
    @Unique
    private static AttackerType zhonz$findWielderType(DamageSource source) {
        Entity direct = source.getDirectEntity();
        Entity causing = source.getEntity();
        if (direct != null && direct == causing && direct instanceof LivingEntity le) {
            AttackerType t = zhonz$typeFor(le);
            if (t != null) return t;
        }
        if (direct instanceof Projectile proj && causing instanceof LivingEntity shooter
                && proj.getOwner() == causing) {
            AttackerType t = zhonz$typeFor(shooter);
            if (t != null) return t;
        }
        // 投掷三叉戟: 附魔在三叉戟自身物品上(1.21 getWeaponItem → 1.20.1 @Accessor)
        if (direct instanceof net.minecraft.world.entity.projectile.ThrownTrident tt) {
            ItemStack weapon = ((ThrownTridentAccessor) tt).zhonz$getTridentItem();
            if (weapon != null && !weapon.isEmpty() && zhonz$hasWeeping(weapon)
                    && tt.getOwner() instanceof LivingEntity wielder) {
                return new AttackerType(wielder, DamageTypes1201.WEEPING_FIRE.getKey(), 1.0F);
            }
        }
        return null;
    }

    /** 按"主手附魔 / 全身唯有命运"给出伤害类型(1.21 同序)。 */
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

    /** 唯有命运: 任一件盔甲带该附魔(1.21 源: wearsUnyieldingFate)。 */
    @Unique
    private static boolean zhonz$wearsUnyieldingFate(LivingEntity entity) {
        for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[]{
                net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
            if (EnchantmentLookup1201.INSTANCE.slot(entity, EnchantIds.UNYIELDING_FATE, slot) > 0) return true;
        }
        return false;
    }

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
        // 投掷三叉戟: 掷出后抬手已空, 附魔在三叉戟自身物品上(1.21 getWeaponItem → @Accessor)
        if (direct instanceof net.minecraft.world.entity.projectile.ThrownTrident tt) {
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
        net.minecraft.world.item.enchantment.Enchantment ench =
                net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS.getValue(
                        new net.minecraft.resources.ResourceLocation(
                                com.zhonz.moreenchantments.forge.CommonConstants1201.MODID,
                                EnchantIds.WEEPING_CHILD));
        if (ench == null) return false;
        return net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(ench, stack) > 0;
    }

    @Unique
    private static boolean zhonz$hasWeeping(LivingEntity entity) {
        return EnchantmentLookup1201.INSTANCE.mainHand(entity, EnchantIds.WEEPING_CHILD) > 0;
    }
}
