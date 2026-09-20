package com.zhonz.moreenchantments.enchantment;

import com.zhonz.moreenchantments.common.enchant.EnchantmentKeys;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * In NeoForge 1.21.1 enchantments are data-driven: they are defined by JSON
 * files under {@code data/<modid>/enchantment/} and only need a
 * {@link ResourceKey} reference in code. The actual {@link Enchantment} holder
 * is resolved at runtime from the server registry via {@link #getHolder}.
 *
 * 键常量已下沉平台无关 {@link EnchantmentKeys}(继承使既有引用
 * {@code ModEnchantments.FINALE} 等零改动); 本类只保留平台层 runtime
 * Holder 解析(NeoForge ServerLifecycleHooks, stage2)。
 */
public class ModEnchantments extends EnchantmentKeys {

    // ===== Helper Methods =====

    /**
     * Resolves the runtime {@link Holder} for an enchantment from the server
     * registry. Must be called server-side (e.g. inside event handlers).
     *
     * @throws IllegalStateException if the enchantment is not registered
     */
    public static Holder<Enchantment> getHolder(ResourceKey<Enchantment> key) {
        return ServerLifecycleHooks.getCurrentServer().registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(key);
    }

    /**
     * 读取 {@link ItemStack} 上某个附魔的等级, <b>不解析注册表 Holder</b>。
     *
     * <p>为什么存在: 客户端连<b>远程服务器</b>时没有 integrated server,
     * {@link ServerLifecycleHooks#getCurrentServer()} 返回 {@code null}, 于是
     * {@link #getHolder} 会在这里 NPE。但 {@code Item#getUseDuration} /
     * {@code Item#getUseAnimation} 这类方法客户端同样要执行(蓄力进度条、投掷姿势预测),
     * 不能简单按侧跳过, 故改为直接比较附魔组件里的 {@link ResourceKey}:
     * {@link DataComponents#ENCHANTMENTS} 与 {@link DataComponents#STORED_ENCHANTMENTS}
     * (两者都是 {@link ItemEnchantments})。两侧通用, 且<b>永不抛异常</b>。
     *
     * @param stack 目标物品; {@code null} 或空物品一律返回 0
     * @param key   附魔键; 为 {@code null} 或解析不到时返回 0
     * @return 该附魔的等级; 未附魔返回 0(调用方无需判空/捕获)
     */
    public static int getLevel(ItemStack stack, ResourceKey<Enchantment> key) {
        if (stack == null || stack.isEmpty() || key == null) return 0;
        int level = getLevelFrom(stack.get(DataComponents.ENCHANTMENTS), key);
        return level > 0 ? level : getLevelFrom(stack.get(DataComponents.STORED_ENCHANTMENTS), key);
    }

    /** 在 {@link ItemEnchantments} 中按 {@link ResourceKey} 查等级; 找不到返回 0。 */
    private static int getLevelFrom(ItemEnchantments enchantments, ResourceKey<Enchantment> key) {
        if (enchantments == null) return 0;
        for (var entry : enchantments.entrySet()) {
            if (key.equals(entry.getKey().unwrapKey().orElse(null))) return entry.getIntValue();
        }
        return 0;
    }

    /**
     * Resolves the runtime {@link Holder} for an enchantment, returning
     * {@code null} if the enchantment is not registered. Useful for user-facing
     * input validation where a friendly error message is preferred over an
     * exception.
     */
    public static Holder<Enchantment> getHolderOrNull(ResourceKey<Enchantment> key) {
        return ServerLifecycleHooks.getCurrentServer().registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT)
                .getHolder(key)
                .orElse(null);
    }
}
