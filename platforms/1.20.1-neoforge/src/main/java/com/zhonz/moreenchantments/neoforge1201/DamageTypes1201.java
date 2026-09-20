package com.zhonz.moreenchantments.neoforge1201;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

/**
 * 1.20.1 NeoForge 平台: 自定义伤害类型键(对应 1.21.1 主工程的 {@code data/damage_type/*.json})。
 *
 * <h2>为什么**不能**用 DeferredRegister 注册(1.20.1 的真实缺陷, 已修)</h2>
 * 早期实现写的是
 * {@code DeferredRegister.create(Registries.DAMAGE_TYPE, MODID).register(...)},
 * 但 1.20.1 的 {@code minecraft:damage_type} 是**数据包驱动**的注册表, 不在 Forge 的
 * {@code GameData} 里 —— 注册时会抛:
 * <pre>
 * IllegalStateException: Unable to find registry with key minecraft:damage_type for mod "zhonz_more_enchantments"
 * </pre>
 * 后果是**服务端启动即失败**(FATAL: Detected errors during registry event dispatch, rolling back to VANILLA),
 * 与 1.21 侧"仅靠 datapack JSON 即可"的做法不一致。
 *
 * <h2>正确做法(与 1.21 完全同构)</h2>
 * 只提供 {@link ResourceKey} 常量; 伤害类型实体由本 mod 的 datapack JSON 提供
 * ({@code src/main/resources/data/zhonz_more_enchantments/damage_type/}), 使用时经
 * {@code level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key)}
 * 取 {@code Holder<DamageType>} 再构造 {@code DamageSource}(见 {@code WeepingFireMixin})。
 *
 * <p><b>标签(决定实际穿透语义, 见 resources 下 data/minecraft/tags/damage_type/)</b>:
 * <ul>
 *   <li>{@code true_damage} → bypasses_armor / bypasses_enchantments / bypasses_resistance /
 *       bypasses_effects(与 1.21 完全一致 → 真伤无视护甲附魔抗性效果)</li>
 *   <li>{@code frost} → is_freezing(与 1.21 一致 → 被"冬痕"等冰霜判定识别)</li>
 *   <li>{@code weeping_fire} → is_fire</li>
 * </ul>
 *
 * <p><b>1.21 源对应</b>: {@code WeepingFireHelper.WEEPING_FIRE / FROST / TRUE_DAMAGE} 三个 ResourceKey。
 */
public final class DamageTypes1201 {

    /** 哭泣之火: 带火焰穿透语义(无视火免/抗火由 FireImmunePierceMixin 等提供)。 */
    public static final ResourceKey<DamageType> WEEPING_FIRE = key("weeping_fire");

    /** 冰霜: 并入 is_freezing 标签(冬痕易伤 ×1.5 的判定依据)。 */
    public static final ResourceKey<DamageType> FROST = key("frost");

    /** 真伤: 无视护甲/附魔/抗性/效果(标签声明), 由唯有命运以 ×6 施加。 */
    public static final ResourceKey<DamageType> TRUE_DAMAGE = key("true_damage");

    private DamageTypes1201() {
    }

    private static ResourceKey<DamageType> key(String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE,
                new ResourceLocation(CommonConstants1201.MODID, path));
    }

    /** 该 mod 命名空间下的资源位置(供构造 DamageSource 时取 Holder)。 */
    public static ResourceLocation id(String path) {
        return new ResourceLocation(CommonConstants1201.MODID, path);
    }
}
