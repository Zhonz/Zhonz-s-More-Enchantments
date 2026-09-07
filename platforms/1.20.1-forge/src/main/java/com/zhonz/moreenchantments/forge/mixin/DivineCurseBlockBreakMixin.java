package com.zhonz.moreenchantments.forge.mixin;

import com.zhonz.moreenchantments.common.enchant.EnchantIds;
import com.zhonz.moreenchantments.forge.EnchantmentLookup1201;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for ServerPlayerGameMode to implement the "神咒" (Divine Curse) enchantment's
 * damage to player when breaking blocks beyond halved hardness.
 *
 * 描述: "可破坏方块硬度减半"
 * Interpretation: 装备神咒的工具只能破坏硬度小于等于原来一半的方块。
 * 也就是说原来硬度 = 2 的方块（草方块），现在仍然能破坏；
 * 但硬度 = 4 的方块（石头），现在无法破坏（4 > 4/2 = 2）。
 *
 * 1.20.1 移植差异: 注入目标 ServerPlayerGameMode.destroyBlock(BlockPos) 在 1.20.1
 * 同样为 public boolean destroyBlock(BlockPos); BlockState.getDestroyProgress
 * (Player, BlockGetter, BlockPos) 1.20.1 位于 BlockBehaviour.BlockStateBase, 可调用。
 */
@Mixin(net.minecraft.server.level.ServerPlayerGameMode.class)
public abstract class DivineCurseBlockBreakMixin {

    /**
     * 在 destroyBlock 之前检查方块硬度
     * 如果硬度超出当前工具的破坏能力,阻止破坏
     */
    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void beforeDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Player player = this.player;
        if (player == null || player.level().isClientSide()) return;

        int curseLevel = EnchantmentLookup1201.INSTANCE.mainHand(player, EnchantIds.DIVINE_CURSE);
        if (curseLevel <= 0) return;

        // 神咒: 可破坏方块硬度减半
        Level level = player.level();
        BlockState state = level.getBlockState(pos);
        float hardness = state.getDestroyProgress(player, level, pos);

        // 如果进度极低,表示硬度太高,禁止破坏
        if (hardness < 0.01f) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Shadow the player field from ServerPlayerGameMode
     */
    @Shadow
    protected net.minecraft.server.level.ServerPlayer player;
}
