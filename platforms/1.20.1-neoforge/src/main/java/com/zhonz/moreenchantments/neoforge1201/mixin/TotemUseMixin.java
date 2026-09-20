package com.zhonz.moreenchantments.neoforge1201.mixin;

import com.zhonz.moreenchantments.common.enchant.EnchantIds;
import com.zhonz.moreenchantments.common.eternal.EternalReturnHelper;
import com.zhonz.moreenchantments.neoforge1201.CommonConstants1201;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "永劫回归": <b>主手</b>持带该附魔的不死图腾右键 → 踢出所有玩家(保留背包/经验) +
 * 登记世界重置(下次启动按原种子重建) + 停服。
 *
 * <p>动作本体在共享的 {@link EternalReturnHelper}(三平台同一份):
 * 旧实现在运行中直接递归删世界目录 —— Windows 上文件句柄未释放导致 delete 静默失败,
 * 且 halt 的保存流程会把地形写回; 而且删掉整个 world/ 会连 playerdata(背包/经验)一起删,
 * 与文档"保留所有玩家的背包、经验等内容"矛盾。现在改为"触发登记 + 启动前重建"。
 *
 * <p>1.20.1 移植差异: {@code Item.use(Level, Player, InteractionHand)} 签名与 1.21 一致,
 * 仅附魔等级查询改用 ForgeRegistries。
 */
@Mixin(Item.class)
public abstract class TotemUseMixin {

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void zhonz$onUse(Level level, Player player, InteractionHand hand,
                             CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        // 文档口径: **主手**持有 —— 副手图腾不触发(右键会为两只手各调一次 use)
        if (hand != InteractionHand.MAIN_HAND) return;

        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(Items.TOTEM_OF_UNDYING)) return;
        if (zhonz$eternalReturnLevel(stack) <= 0) return;

        if (!(level instanceof ServerLevel serverLevel)) return;

        EternalReturnHelper.trigger(serverLevel.getServer(), player,
                net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().toFile());
        cir.setReturnValue(InteractionResultHolder.consume(stack));
    }

    /**
     * 物品自身是否带"永劫回归"(id → ForgeRegistries, 对应 1.21 {@code stack.getEnchantmentLevel(holder)})。
     *
     * <p>方法名带 {@code zhonz$} 前缀且唯一: 本包多个 mixin 都注入 {@code Item}/{@code CrossbowItem}
     * 等类, 同名 {@code private static} 辅助方法会在合并时触发
     * "Method overwrite conflict … Skipping method", 可能让调用点找不到实现。
     */
    private static int zhonz$eternalReturnLevel(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        Enchantment ench = ForgeRegistries.ENCHANTMENTS.getValue(
                new ResourceLocation(CommonConstants1201.MODID, EnchantIds.ETERNAL_RETURN));
        if (ench == null) return 0;
        return EnchantmentHelper.getItemEnchantmentLevel(ench, stack);
    }
}
