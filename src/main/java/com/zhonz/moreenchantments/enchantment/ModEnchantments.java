package com.zhonz.moreenchantments.enchantment;

import com.zhonz.moreenchantments.common.enchant.EnchantmentKeys;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
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
