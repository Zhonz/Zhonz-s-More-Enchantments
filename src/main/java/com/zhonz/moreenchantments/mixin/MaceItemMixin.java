package com.zhonz.moreenchantments.mixin;

import com.zhonz.moreenchantments.ZhonzMoreEnchantments;
import com.zhonz.moreenchantments.enchantment.ModEnchantments;
import com.zhonz.moreenchantments.entity.ThrownMaceEntity;
import com.zhonz.moreenchantments.event.EntityDataStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.level.Level;
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
 *  - Release to throw the mace as a projectile entity
 *  - When it hits a block or entity, deal 1000% damage and teleport the thrower to the hit location
 *  - 5 second cooldown (like a shield)
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
     * On release, spawn the thrown mace projectile entity.
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

        float chargeRatio = Math.min(1.0f, chargeTicks / 40.0f);

        // 创建并发射投掷物实体
        ThrownMaceEntity thrownMace = new ThrownMaceEntity(level, player, stack.copy(), chargeRatio);

        // 设置投掷方向与速度
        thrownMace.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F,
                1.5F + chargeRatio * 0.5F, 1.0F);

        // 添加到世界
        level.addFreshEntity(thrownMace);

        // 播放投掷音效
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0F, 0.8F);

        // 玩家手中的重锤消耗（不消失但扣耐久）
        if (stack.isDamageableItem()) {
            stack.hurtAndBreak(3, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        }
    }
}
