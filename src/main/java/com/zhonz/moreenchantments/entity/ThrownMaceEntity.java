package com.zhonz.moreenchantments.entity;

import com.zhonz.moreenchantments.common.storage.EntityDataStorage;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 必须开辟的通路 重锤投掷物实体。
 * 从玩家手中飞出，命中实体或方块时造成1000%伤害并将玩家传送至命中点。
 */
public class ThrownMaceEntity extends AbstractArrow {

    /** 冷却时间(Ticks): 5秒 = 100 ticks. */
    private static final int COOLDOWN_TICKS = 100;
    /** 命中时对目标造成攻击伤害的倍数. */
    private static final float DAMAGE_MULTIPLIER = 10.0f;
    /** 溅射伤害比例(基础伤害的百分比). */
    private static final float SPLASH_DAMAGE_RATIO = 0.5f;
    /** 溅射范围半径. */
    private static final double SPLASH_RADIUS = 3.0;

    public ThrownMaceEntity(EntityType<? extends AbstractArrow> entityType, Level level) {
        super(entityType, level);
        this.pickup = Pickup.DISALLOWED;
    }

    public ThrownMaceEntity(Level level, Player player, ItemStack stack) {
        super(com.zhonz.moreenchantments.ZhonzMoreEnchantments.THROWN_MACE_ENTITY.get(), player, level, stack, stack.copy());
        this.pickup = Pickup.DISALLOWED;
        this.setBaseDamage(player.getAttributeValue(Attributes.ATTACK_DAMAGE) * DAMAGE_MULTIPLIER);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return ItemStack.EMPTY;
    }

    @Override
    public void tick() {
        super.tick();
        // 飞行轨迹粒子效果（水花特效）
        if (this.tickCount % 2 == 0 && this.level() instanceof ServerLevel serverLevel) {
            Vec3 pos = this.position();
            serverLevel.sendParticles(ParticleTypes.BUBBLE, pos.x, pos.y, pos.z, 2, 0.1, 0.1, 0.1, 0.02);
            serverLevel.sendParticles(ParticleTypes.SPLASH,  pos.x, pos.y, pos.z, 1, 0.1, 0.1, 0.1, 0.01);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (this.level().isClientSide()) {
            this.discard();
            return;
        }

        if (result.getEntity() instanceof LivingEntity target && this.getOwner() instanceof Player player) {
            super.onHitEntity(result);
            player.teleportTo(target.getX(), target.getY(), target.getZ());
            applySplashDamage(player, target.position(), (float) this.getBaseDamage());
            setCooldown(player);

            if (this.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.EXPLOSION,
                        target.getX(), target.getY() + 1, target.getZ(),
                        3, 0.5, 0.5, 0.5, 0.1);
            }
        }
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (this.level().isClientSide()) {
            this.discard();
            return;
        }

        super.onHitBlock(result);

        if (this.getOwner() instanceof Player player) {
            Vec3 pos = result.getLocation();
            // 玩家传送至命中位置（稍微抬高一点避免卡进方块）
            player.teleportTo(pos.x, pos.y + 0.5, pos.z);
            applySplashDamage(player, pos, (float) this.getBaseDamage());
            setCooldown(player);

            if (this.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.EXPLOSION,
                        pos.x, pos.y + 1, pos.z,
                        3, 0.5, 0.5, 0.5, 0.1);
            }
        }
        this.discard();
    }

    private static void applySplashDamage(Player player, Vec3 center, float damage) {
        Level level = player.level();
        float splashDamage = damage * SPLASH_DAMAGE_RATIO;
        AABB box = new AABB(
                center.x - SPLASH_RADIUS, center.y - SPLASH_RADIUS, center.z - SPLASH_RADIUS,
                center.x + SPLASH_RADIUS, center.y + SPLASH_RADIUS, center.z + SPLASH_RADIUS);
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (nearby != player && nearby.isAlive() && player.canAttack(nearby)) {
                nearby.hurt(level.damageSources().playerAttack(player), splashDamage);
            }
        }
    }

    private static void setCooldown(Player player) {
        EntityDataStorage.getEntityData(player).putInt("zhonz_must_open_path_cd", COOLDOWN_TICKS);
    }
}
