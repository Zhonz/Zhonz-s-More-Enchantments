package com.zhonz.moreenchantments.mixin;

import com.zhonz.moreenchantments.enchantment.ModEnchantments;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for ServerPlayerGameMode to implement the "神咒" (Divine Curse) enchantment's
 * destroy progress halving.
 *
 * Description: "可破坏方块硬度减半"
 * - 装备神咒的工具只能破坏"原本能破坏的方块"中硬度更小的部分
 * - 即 destroyProgress 减半 → 等效于硬度感知减半
 *
 * Target method: ServerPlayerGameMode.incrementDestroyProgress(BlockState, BlockPos, int)
 * This is the private method that calculates block breaking progress each tick.
 */
@Mixin(net.minecraft.server.level.ServerPlayerGameMode.class)
public abstract class BlockBreakMixin {

    @Shadow
    protected net.minecraft.server.level.ServerPlayer player;

    /**
     * 调整 incrementDestroyProgress 返回值: 神咒使进度减半
     * 进度越小,挖掘越慢;进度越接近1,挖掘越快
     * 减半 = 等效硬度翻倍
     */
    @Inject(method = "incrementDestroyProgress", at = @At("RETURN"), cancellable = true)
    private void modifyDestroyProgress(BlockState state, BlockPos pos, int startTick,
                                       CallbackInfoReturnable<Float> cir) {
        if (player.level().isClientSide()) return;

        ItemStack stack = player.getMainHandItem();
        int curseLevel = stack.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.DIVINE_CURSE));
        if (curseLevel <= 0) return;

        float original = cir.getReturnValue();
        // 进度减半 = 等效硬度翻倍
        cir.setReturnValue(original * 0.5f);
    }
}
