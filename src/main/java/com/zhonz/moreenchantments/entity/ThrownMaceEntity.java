package com.zhonz.moreenchantments.entity;

import com.zhonz.moreenchantments.common.storage.EntityDataStorage;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 必须开辟的通路 重锤投掷物实体。
 * 从玩家手中飞出，命中实体或方块时造成1000%伤害并将玩家传送至命中点。
 *
 * <p>实现 {@link ItemSupplier} 是为了让客户端能用 {@code ThrownItemRenderer} 渲染
 * (该类要求 {@code Entity & ItemSupplier}); 否则该实体类型在客户端没有渲染器,
 * {@code EntityRenderDispatcher.shouldRender} 会因 renderer 为 null 而 NPE 崩溃。
 */
public class ThrownMaceEntity extends AbstractArrow implements ItemSupplier {

    /** 冷却时间(Ticks): 5秒 = 100 ticks. */
    private static final int COOLDOWN_TICKS = 100;
    /** 命中时对目标造成攻击伤害的倍数. */
    private static final float DAMAGE_MULTIPLIER = 10.0f;
    // 文档 #32(ENCHANTMENTS.md:189 / README.md:312)只写了"命中造成重锤攻击伤害 1000% + 位移 + 5 秒冷却",
    // 没有任何溅射条款 —— 原来的"半径 3 格、50% 伤害溅射"已按文档删除。

    public ThrownMaceEntity(EntityType<? extends AbstractArrow> entityType, Level level) {
        super(entityType, level);
        this.pickup = Pickup.DISALLOWED;
    }

    public ThrownMaceEntity(Level level, Player player, ItemStack stack) {
        super(com.zhonz.moreenchantments.ZhonzMoreEnchantments.THROWN_MACE_ENTITY.get(), player, level, stack, stack.copy());
        this.pickup = Pickup.DISALLOWED;
        // 基准 = 玩家"面板攻击伤害"×1000%(README.md:312「造成重锤攻击伤害 1000% 的伤害」)。
        // 这里读 ATTACK_DAMAGE 是对的: 文档要的就是面板重锤伤害, 而本模组的增伤已经在
        // apex/自缚者/加速的未来/噤声击坠天堂 迁出 ATTACK_DAMAGE、改走独立乘区 bonus_damage。
        // 不会重复计算: 命中时该投掷物的伤害事件 source.getEntity() = 投掷者本人,
        // 会经过 ModEventHandlers.onLivingDamage(LivingDamageEvent.Pre) 的统一结算,
        // 增伤乘区在那里恰好作用一次 —— 旧口径(增伤写在面板里)总伤 = 面板×10, 新口径 = 面板×10×乘区, 数值同。
        this.setBaseDamage(player.getAttributeValue(Attributes.ATTACK_DAMAGE) * DAMAGE_MULTIPLIER);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return ItemStack.EMPTY;
    }

    /** 客户端渲染用: 返回投掷时携带的重锤(不可拾取, 仅用于 ThrownItemRenderer 画出物品)。 */
    @Override
    public ItemStack getItem() {
        ItemStack stack = this.getPickupItemStackOrigin();
        return stack.isEmpty() ? new ItemStack(net.minecraft.world.item.Items.MACE) : stack;
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
            setCooldown(player);

            if (this.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.EXPLOSION,
                        pos.x, pos.y + 1, pos.z,
                        3, 0.5, 0.5, 0.5, 0.1);
            }
        }
        this.discard();
    }

    private static void setCooldown(Player player) {
        EntityDataStorage.getEntityData(player).putInt("zhonz_must_open_path_cd", COOLDOWN_TICKS);
    }
}
