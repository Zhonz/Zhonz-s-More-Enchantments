package com.zhonz.moreenchantments.neoforge1201.mixin;

import com.zhonz.moreenchantments.common.enchant.EnchantIds;
import com.zhonz.moreenchantments.neoforge1201.EnchantmentLookup1201;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
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
 * - 1.21 的 WeepingFireHelper 用数据驱动自定义 damage_type(weeping_fire/frost/true_damage) 转换,
 *   1.20.1 无该数据层 → 本移植只处理哭泣之子(weeping_child)→ 火焰 一条路径,
 *   用原版 DamageSource(ON_FIRE holder + 攻击者 attribution)近似"哭泣之火";
 *   snow_wound(霜) / unyielding_fate(真伤×6) 两条 1.21 转换路径在 1.20.1 不移植
 *   (无对应原版伤害类型, 若需复刻需自建 data/damage_type JSON + 平台数据层, 见 SideEffectsBatch1 TODO)。
 * - "是否该转换"的判定保留(与 1.21 一致): 近战直接实体==造成者 且 主手持哭泣之子;
 *   投射物 owner==造成者 且 造成者主手持哭泣之子。1.21 额外的"三叉戟自身物品附魔"
 *   分支需要 @Accessor 读 ThrownTrident.tridentItem(1.20.1 仅暴露 protected getPickupItem()),
 *   本批不新增 accessor 文件 → 该分支留 TODO(见 zhonz$findWeepingAttacker)。
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
        // 已转换(或任何火焰): 1.20.1 递归护栏 —— 火焰不再二次转换
        if (source.is(DamageTypeTags.IS_FIRE)) return;
        if (amount <= 0.0F) return;
        if (self.isInvulnerableTo(source)) return; // 原本就打不中, 无需转换

        LivingEntity attacker = zhonz$findWeepingAttacker(source);
        if (attacker == null || attacker == self) return;

        // 真·火焰近似: 原版 ON_FIRE 类型, 保留 direct=causing=attacker 归属(供穿透判定识别)
        Holder<DamageType> fireHolder = self.level().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.ON_FIRE);
        DamageSource fireSource = new DamageSource(fireHolder, attacker, attacker);
        boolean result = self.hurt(fireSource, amount);
        cir.setReturnValue(result);
    }

    /** 找出"主手持哭泣之子"的攻击者(近战直接命中 / 投射物)。1.20.1 无 trident 自身附魔分支 → TODO。 */
    @Unique
    private static LivingEntity zhonz$findWeepingAttacker(DamageSource source) {
        Entity direct = source.getDirectEntity();
        Entity causing = source.getEntity();

        // 近战 / 直接: 直接实体 == 造成者
        if (direct != null && direct == causing && direct instanceof LivingEntity le) {
            if (zhonz$hasWeeping(le)) return le;
        }
        // 投射物: 直接实体是箭/三叉戟/雪球等, owner == 造成者
        if (direct instanceof Projectile proj && causing instanceof LivingEntity shooter) {
            if (proj.getOwner() == causing && zhonz$hasWeeping(shooter)) return shooter;
        }
        // TODO(1.21 有): 投掷三叉戟掷出后主手已空, 附魔在三叉戟自身物品(1.21 ThrownTrident.getWeaponItem)上;
        // 1.20.1 ThrownTrident 仅暴露 protected getPickupItem(), 需 @Accessor 才能读, 本批不移植。
        return null;
    }

    /** 主手是否持"哭泣之子"附魔(1.20.1 经 EnchantmentLookup1201 查 ForgeRegistries)。 */
    @Unique
    private static boolean zhonz$hasWeeping(LivingEntity entity) {
        return EnchantmentLookup1201.INSTANCE.mainHand(entity, EnchantIds.WEEPING_CHILD) > 0;
    }
}
