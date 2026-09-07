package com.zhonz.moreenchantments.neoforge.mixin;

import com.zhonz.moreenchantments.enchantment.ModEnchantments;
import com.zhonz.moreenchantments.entity.ThrownMaceEntity;
import com.zhonz.moreenchantments.common.storage.EntityDataStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for MaceItem to implement the "必须开辟的通路" (Must Open Path) enchantment:
 *  - Right-click to charge up (like Trident)
 *  - Release to throw the mace as a projectile entity
 *  - When it hits a block or entity, deal 1000% damage and teleport the thrower to the hit location
 *  - 5 second cooldown (like a shield)
 */
@Mixin(Item.class)
public abstract class MaceItemMixin {

    private static final String KEY_MUST_OPEN_PATH_CD = "zhonz_must_open_path_cd";
    private static final String KEY_MUST_OPEN_PATH_CHARGE = "zhonz_must_open_path_charge_ticks";
    /** 触发投掷的最短蓄力时间(ticks): 0.5s = 10 ticks. */
    private static final int MIN_CHARGE_TICKS = 10;
    /** 满蓄力所需时间(ticks): 2s = 40 ticks. */
    private static final int FULL_CHARGE_TICKS = 40;
    /** 投掷时的最小速度. */
    private static final float MIN_THROW_VELOCITY = 1.5F;
    /** 满蓄力时额外增加的速度. */
    private static final float CHARGE_VELOCITY_BONUS = 0.5F;
    /** 投掷后消耗的耐久值. */
    private static final int THROW_DURABILITY_COST = 3;

    /**
     * Right-click to start charging the mace like a trident.
     */
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void onUse(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        ItemStack stack = player.getItemInHand(hand);
        // Only applies to Mace items with the Must Open Path enchantment
        if (!(stack.getItem() instanceof net.minecraft.world.item.MaceItem)) return;
        int mustOpenPathLevel = stack.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.MUST_OPEN_PATH));
        if (mustOpenPathLevel <= 0) return;

        // 检查冷却
        CompoundTag data = EntityDataStorage.getEntityData(player);
        if (data.getInt(KEY_MUST_OPEN_PATH_CD) > 0) return;

        player.startUsingItem(hand);
        cir.setReturnValue(InteractionResultHolder.consume(stack));
    }

    /**
     * Track charge ticks during use.
     */
    @Inject(method = "onUseTick", at = @At("TAIL"))
    private void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration, CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;
        if (!(stack.getItem() instanceof net.minecraft.world.item.MaceItem)) return;
        int mustOpenPathLevel = stack.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.MUST_OPEN_PATH));
        if (mustOpenPathLevel <= 0) return;

        CompoundTag data = EntityDataStorage.getEntityData(player);
        data.putInt(KEY_MUST_OPEN_PATH_CHARGE, data.getInt(KEY_MUST_OPEN_PATH_CHARGE) + 1);
    }

    /**
     * 让蓄力可持续: 原版 MaceItem 的 useDuration 为 0,开始使用后 1 tick 就完成,
     * releaseUsing 立即触发且蓄力不足,导致无法投掷。改为与三叉戟一致的 72000。
     */
    @Inject(method = "getUseDuration", at = @At("RETURN"), cancellable = true)
    private void modifyUseDuration(ItemStack stack, LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
        if (!(stack.getItem() instanceof net.minecraft.world.item.MaceItem)) return;
        int mustOpenPathLevel = stack.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.MUST_OPEN_PATH));
        if (mustOpenPathLevel <= 0) return;
        cir.setReturnValue(72000);
    }

    /**
     * 使用三叉戟的投掷动画(蓄力时摆出投掷姿势)。
     */
    @Inject(method = "getUseAnimation", at = @At("RETURN"), cancellable = true)
    private void modifyUseAnimation(ItemStack stack, CallbackInfoReturnable<UseAnim> cir) {
        if (!(stack.getItem() instanceof net.minecraft.world.item.MaceItem)) return;
        int mustOpenPathLevel = stack.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.MUST_OPEN_PATH));
        if (mustOpenPathLevel <= 0) return;
        cir.setReturnValue(UseAnim.SPEAR);
    }

    /**
     * On release, spawn the thrown mace projectile entity.
     */
    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    private void onReleaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft, CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;
        if (!(stack.getItem() instanceof net.minecraft.world.item.MaceItem)) return;
        int mustOpenPathLevel = stack.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.MUST_OPEN_PATH));
        if (mustOpenPathLevel <= 0) return;

        CompoundTag data = EntityDataStorage.getEntityData(player);
        int chargeTicks = data.getInt(KEY_MUST_OPEN_PATH_CHARGE);
        data.putInt(KEY_MUST_OPEN_PATH_CHARGE, 0);

        if (chargeTicks < MIN_CHARGE_TICKS) return;
        float chargeRatio = Math.min(1.0f, chargeTicks / (float) FULL_CHARGE_TICKS);

        // 创建并发射投掷物实体
        ThrownMaceEntity thrownMace = new ThrownMaceEntity(level, player, stack.copy());

        // 设置投掷方向与速度
        thrownMace.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F,
                MIN_THROW_VELOCITY + chargeRatio * CHARGE_VELOCITY_BONUS, 1.0F);

        level.addFreshEntity(thrownMace);

        // 播放投掷音效
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0F, 0.8F);

        // 玩家手中的重锤消耗耐久
        if (stack.isDamageableItem()) {
            stack.hurtAndBreak(THROW_DURABILITY_COST, player, EquipmentSlot.MAINHAND);
        }
    }
}
