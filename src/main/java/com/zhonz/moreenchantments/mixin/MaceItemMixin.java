package com.zhonz.moreenchantments.mixin;

import com.zhonz.moreenchantments.enchantment.ModEnchantments;
import com.zhonz.moreenchantments.event.EntityDataStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for MaceItem to implement the "必须开辟的通路" (Must Open Path) enchantment:
 *  - Right-click to charge up (like Trident)
 *  - Release to launch the mace in a straight line
 *  - When it hits a block or entity, deal 1000% damage and teleport the thrower to the hit location
 *  - 5 second cooldown (like a shield)
 *
 * 简化实现: 使用 raycast 直接命中目标,而不是创建抛射物实体
 */
@Mixin(MaceItem.class)
public abstract class MaceItemMixin {

    @Shadow public abstract int getUseDuration(ItemStack stack, LivingEntity entity);

    @Unique
    private static final String ZHONZ_CHARGE_TICKS = "zhonz_must_open_path_charge_ticks";

    /**
     * Right-click to start charging the mace like a trident.
     */
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void onUse(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        ItemStack stack = player.getItemInHand(hand);
        int mustOpenPathLevel = stack.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.MUST_OPEN_PATH));
        if (mustOpenPathLevel <= 0) return;

        // 检查冷却
        CompoundTag data = EntityDataStorage.getEntityData(player);
        int cooldown = data.contains("zhonz_must_open_path_cd") ? data.getInt("zhonz_must_open_path_cd") : 0;
        if (cooldown > 0) {
            return;
        }

        player.startUsingItem(hand);
        cir.setReturnValue(InteractionResultHolder.consume(stack));
    }

    /**
     * Track charge ticks during use.
     */
    @Inject(method = "onUseTick", at = @At("TAIL"))
    private void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration, CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;
        int mustOpenPathLevel = stack.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.MUST_OPEN_PATH));
        if (mustOpenPathLevel <= 0) return;

        CompoundTag data = EntityDataStorage.getEntityData(player);
        int chargeTicks = data.contains(ZHONZ_CHARGE_TICKS) ? data.getInt(ZHONZ_CHARGE_TICKS) : 0;
        chargeTicks++;
        data.putInt(ZHONZ_CHARGE_TICKS, chargeTicks);
    }

    /**
     * On release, perform the throw: raycast, damage, teleport, cooldown.
     */
    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    private void onReleaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft, CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;
        int mustOpenPathLevel = stack.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.MUST_OPEN_PATH));
        if (mustOpenPathLevel <= 0) return;

        CompoundTag data = EntityDataStorage.getEntityData(player);
        int chargeTicks = data.contains(ZHONZ_CHARGE_TICKS) ? data.getInt(ZHONZ_CHARGE_TICKS) : 0;
        data.putInt(ZHONZ_CHARGE_TICKS, 0);

        // 至少蓄力 10 ticks (0.5s) 才执行
        if (chargeTicks < 10) return;

        // 投掷范围: 基于蓄力时间,基础30格
        float chargeRatio = Math.min(1.0f, chargeTicks / 40.0f);
        double range = 20.0 + chargeRatio * 20.0; // 20-40 blocks

        // Raycast 找到目标
        Vec3 start = player.getEyePosition(1.0f);
        Vec3 lookVec = player.getLookAngle();
        Vec3 end = start.add(lookVec.scale(range));

        // 实体命中检测
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                player, start, end,
                player.getBoundingBox().expandTowards(lookVec.scale(range)).inflate(1.0),
                entity1 -> entity1 instanceof LivingEntity && entity1.isAlive() && player.canAttack((LivingEntity) entity1),
                range * range
        );

        HitResult blockHit = null;
        // 找到最近的目标
        double entityDist = entityHit != null ? start.distanceTo(entityHit.getLocation()) : Double.MAX_VALUE;
        // 简化的方块命中检测: 用 raycast
        double blockDist = Double.MAX_VALUE;
        Vec3 blockHitPos = null;
        net.minecraft.world.phys.BlockHitResult blockHitResult = raycastBlocks(level, player, start, lookVec, range);
        if (blockHitResult.getType() != HitResult.Type.MISS) {
            blockDist = start.distanceTo(blockHitResult.getLocation());
            blockHitPos = blockHitResult.getLocation();
        }

        if (entityDist == Double.MAX_VALUE && blockDist == Double.MAX_VALUE) return;

        // 计算重锤基础伤害
        double baseDamage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float damageMultiplier = 10.0f; // 1000% = 10x
        float totalDamage = (float) (baseDamage * damageMultiplier);

        // 伤害目标(如果命中实体)
        if (entityDist < blockDist && entityHit != null) {
            LivingEntity target = (LivingEntity) entityHit.getEntity();
            target.hurt(level.damageSources().playerAttack(player), totalDamage);
            // 玩家传送至目标位置
            player.teleportTo(target.getX(), target.getY(), target.getZ());
        } else if (blockDist != Double.MAX_VALUE) {
            // 命中方块: 玩家传送至命中位置
            Vec3 hitPos = blockHitPos != null ? blockHitPos : end;
            player.teleportTo(hitPos.x, hitPos.y, hitPos.z);
            // 范围伤害(对附近生物)
            level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(3.0))
                    .forEach(e -> {
                        if (e != player && e.isAlive() && player.canAttack(e)) {
                            e.hurt(level.damageSources().playerAttack(player), totalDamage * 0.5f);
                        }
                    });
        }

        // 消耗耐久(被击中或传送后)
        if (stack.isDamageableItem()) {
            stack.hurtAndBreak(5, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        }

        // 进入冷却5秒 (100 ticks)
        data.putInt("zhonz_must_open_path_cd", 100);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0f, 0.8f);
    }

    /**
     * 简化的方块raycast: 按step步进检测路径上的方块
     */
    @Unique
    private static net.minecraft.world.phys.BlockHitResult raycastBlocks(Level level, Player player, Vec3 start, Vec3 direction, double range) {
        Vec3 step = direction.normalize().scale(0.5); // 每0.5格检测一次
        Vec3 pos = start;
        for (double d = 0; d < range; d += 0.5) {
            net.minecraft.core.BlockPos blockPos = net.minecraft.core.BlockPos.containing(pos);
            var state = level.getBlockState(blockPos);
            if (!state.isAir() && !state.liquid()) {
                // 找到非空气/液体方块
                return new net.minecraft.world.phys.BlockHitResult(
                        pos, net.minecraft.core.Direction.getNearest(direction),
                        blockPos, false
                );
            }
            pos = pos.add(step);
        }
        return net.minecraft.world.phys.BlockHitResult.miss(Vec3.ZERO, net.minecraft.core.Direction.UP, player.blockPosition());
    }
}
