package com.zhonz.moreenchantments.neoforge.mixin;

import com.zhonz.moreenchantments.common.eternal.EternalReturnHelper;
import com.zhonz.moreenchantments.enchantment.ModEnchantments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "永劫回归": <b>主手</b>持带该附魔的不死图腾右键 → 踢出所有玩家(保留背包/经验) +
 * 登记世界重置(下次启动按原种子重建) + 停服。
 *
 * <p>本 mixin 只负责"识别 + 转发": 附魔判定在各平台侧(1.21 数据驱动 → {@link ModEnchantments},
 * 1.20.1 代码注册 → ForgeRegistries), 实际动作统一在
 * {@link EternalReturnHelper#trigger(net.minecraft.server.MinecraftServer, Player, java.io.File)}。
 * 这样三平台共用同一份重置逻辑, 且能被 {@code /zhonztest eternaltest} 直接驱动。
 */
@Mixin(Item.class)
public abstract class TotemUseMixin {

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void zhonz$onUse(Level level, Player player, InteractionHand hand,
                             CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        // 文档口径: **主手**持有 —— 副手图腾不触发(1.21 右键会为两只手各调一次 use)
        if (hand != InteractionHand.MAIN_HAND) return;

        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(Items.TOTEM_OF_UNDYING)) return;

        // 方案(a) 服务端权威逻辑(踢人/登记重置/停服客户端毫无意义): 客户端直接返回。
        // 必须在 getHolder 之前 —— 远程客户端没有 integrated server, 解析 Holder 会 NPE。
        if (level.isClientSide()) return;

        if (stack.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.ETERNAL_RETURN)) <= 0) return;

        if (!(level instanceof ServerLevel serverLevel)) return;

        EternalReturnHelper.trigger(serverLevel.getServer(), player,
                net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().toFile());
        cir.setReturnValue(InteractionResultHolder.consume(stack));
    }
}
