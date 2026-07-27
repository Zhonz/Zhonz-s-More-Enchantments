package com.zhonz.moreenchantments.entity;

import com.zhonz.moreenchantments.event.EntityDataStorage;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 必须开辟的通路 重锤投掷物实体。
 * 从玩家手中飞出，命中实体或方块时造成1000%伤害并将玩家传送至命中点。
 */
public class ThrownMaceEntity extends AbstractArrow {

    private ItemStack maceStack = new ItemStack(Items.MACE);
    private float damageMultiplier = 10.0f;

    public ThrownMaceEntity(EntityType<? extends AbstractArrow> entityType, Level level) {
        super(entityType, level);
        this.setBaseDamage(7.0);
        this.pickup = Pickup.DISALLOWED;
    }

    public ThrownMaceEntity(Level level, Player player, ItemStack stack, float chargeRatio) {
        super(com.zhonz.moreenchantments.ZhonzMoreEnchantments.THROWN_MACE_ENTITY.get(), player, level, stack, stack.copy());
        this.maceStack = stack.copy();
        this.pickup = Pickup.DISALLOWED;
        this.damageMultiplier = 10.0f;

        // 根据蓄力比例设置基础伤害
        double playerDamage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        this.setBaseDamage(playerDamage * damageMultiplier);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return ItemStack.EMPTY;
    }

    @Override
    public void tick() {
        super.tick();
        // 飞行轨迹粒子效果（水花特效）
        if (this.level() instanceof ServerLevel serverLevel && this.tickCount % 2 == 0) {
            Vec3 pos = this.position();
            serverLevel.sendParticles(ParticleTypes.BUBBLE,
                    pos.x, pos.y, pos.z,
                    2, 0.1, 0.1, 0.1, 0.02);
            serverLevel.sendParticles(ParticleTypes.SPLASH,
                    pos.x, pos.y, pos.z,
                    1, 0.1, 0.1, 0.1, 0.01);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (this.level().isClientSide()) return;

        Entity entity = result.getEntity();
        if (entity instanceof LivingEntity target && this.getOwner() instanceof Player player) {
            // 先造成伤害（super.onHitEntity会处理）
            super.onHitEntity(result);

            // 玩家传送至目标位置
            player.teleportTo(target.getX(), target.getY(), target.getZ());

            // 范围溅射伤害（对附近生物造成50%伤害）
            applySplashDamage(player, target.position(), (float) this.getBaseDamage());

            // 进入冷却
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
        if (this.level().isClientSide()) return;

        super.onHitBlock(result);

        if (this.getOwner() instanceof Player player) {
            Vec3 pos = result.getLocation();
            // 玩家传送至命中位置（稍微抬高一点避免卡进方块）
            player.teleportTo(pos.x, pos.y + 0.5, pos.z);

            // 范围溅射伤害
            applySplashDamage(player, pos, (float) this.getBaseDamage());

            // 进入冷却
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
        float splashDamage = damage * 0.5f;
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(center.x - 3, center.y - 2, center.z - 3,
                        center.x + 3, center.y + 3, center.z + 3))) {
            if (nearby != player && nearby.isAlive() && player.canAttack(nearby)) {
                nearby.hurt(level.damageSources().playerAttack(player), splashDamage);
            }
        }
    }

    private static void setCooldown(Player player) {
        CompoundTag data = EntityDataStorage.getEntityData(player);
        data.putInt("zhonz_must_open_path_cd", 100); // 5秒冷却
    }
}
