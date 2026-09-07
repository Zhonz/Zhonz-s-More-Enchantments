package com.zhonz.moreenchantments.forge.mixin;

import com.zhonz.moreenchantments.common.enchant.EnchantIds;
import com.zhonz.moreenchantments.forge.EnchantmentLookup1201;
import net.minecraft.core.BlockPos;
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
 * 1.20.1 中该方法同样为私有 (BlockState, BlockPos, int) → float, 与 1.21 签名一致。
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

        int curseLevel = EnchantmentLookup1201.INSTANCE.mainHand(player, EnchantIds.DIVINE_CURSE);
        if (curseLevel <= 0) return;

        float original = cir.getReturnValue();
        // 进度减半 = 等效硬度翻倍
        cir.setReturnValue(original * 0.5f);
    }
}
