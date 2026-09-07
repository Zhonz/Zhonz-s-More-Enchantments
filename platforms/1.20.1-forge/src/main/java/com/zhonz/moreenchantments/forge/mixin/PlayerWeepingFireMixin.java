package com.zhonz.moreenchantments.forge.mixin;

import com.zhonz.moreenchantments.common.enchant.EnchantIds;
import com.zhonz.moreenchantments.forge.EnchantmentLookup1201;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
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
        if (source.is(DamageTypeTags.IS_FIRE)) return; // 已转换(或任何火焰): 递归护栏
        if (amount <= 0.0F) return;
        if (self.isInvulnerableTo(source)) return;

        LivingEntity attacker = zhonz$findWeepingAttacker(source);
        if (attacker == null || attacker == self) return;

        Holder<DamageType> fireHolder = self.level().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.ON_FIRE);
        DamageSource fireSource = new DamageSource(fireHolder, attacker, attacker);
        cir.setReturnValue(self.hurt(fireSource, amount));
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
        // TODO(1.21 有): 投掷三叉戟自身附魔分支 —— 1.20.1 需 @Accessor 读 ThrownTrident.tridentItem, 本批不移植。
        return null;
    }

    @Unique
    private static boolean zhonz$hasWeeping(LivingEntity entity) {
        return EnchantmentLookup1201.INSTANCE.mainHand(entity, EnchantIds.WEEPING_CHILD) > 0;
    }
}
