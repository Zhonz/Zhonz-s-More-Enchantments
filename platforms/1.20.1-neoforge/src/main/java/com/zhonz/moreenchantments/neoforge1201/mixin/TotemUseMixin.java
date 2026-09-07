package com.zhonz.moreenchantments.neoforge1201.mixin;

import com.zhonz.moreenchantments.common.enchant.EnchantIds;
import com.zhonz.moreenchantments.neoforge1201.CommonConstants1201;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;

/**
 * "永劫回归": 主手持不死图腾右键 -> 将所有玩家踢出,保留数据,删除世界,停止服务器。
 * 下次启动时按 server.properties 的种子重新生成世界。
 *
 * 1.20.1 移植差异: Item.use(Level, Player, InteractionHand) 签名与 1.21 一致,
 * 仅附魔等级查询改用 ForgeRegistries; 服务器 API (TickTask / tell / halt /
 * getWorldPath / disconnect) 1.20.1 均存在。
 */
@Mixin(Item.class)
public abstract class TotemUseMixin {

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void zhonz$onUse(Level level, Player player, InteractionHand hand,
                             CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(Items.TOTEM_OF_UNDYING)) return;
        if (enchLevel(stack, EnchantIds.ETERNAL_RETURN) <= 0) return;

        if (!(level instanceof ServerLevel serverLevel)) return;
        net.minecraft.server.MinecraftServer server = serverLevel.getServer();

        // 踢出所有玩家(保留背包/经验)
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.disconnect(Component.literal("§d永劫回归……世界将重新诞生"));
        }

        // 延迟: 等玩家数据保存后删除世界目录并停止服务器
        File worldRoot = server.getWorldPath(LevelResource.ROOT).toFile();
        server.tell(new TickTask(2, () -> {
            try {
                deleteRecursively(worldRoot);
            } catch (Exception ignored) {
            }
            server.halt(false);
        }));

        cir.setReturnValue(InteractionResultHolder.consume(stack));
    }

    /** 物品自身是否带某附魔(id → ForgeRegistries, 对应 1.21 stack.getEnchantmentLevel(holder))。 */
    private static int enchLevel(ItemStack stack, String id) {
        if (stack.isEmpty()) return 0;
        Enchantment ench = ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation(CommonConstants1201.MODID, id));
        if (ench == null) return 0;
        return EnchantmentHelper.getItemEnchantmentLevel(ench, stack);
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) {
                deleteRecursively(c);
            }
        }
        f.delete();
    }
}
