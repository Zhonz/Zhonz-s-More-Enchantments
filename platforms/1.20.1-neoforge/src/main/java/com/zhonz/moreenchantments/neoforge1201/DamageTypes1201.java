package com.zhonz.moreenchantments.neoforge1201;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 1.20.1 Forge 平台: 自定义伤害类型注册(对应 1.21.1 主工程的 data/damage_type JSON)。
 *
 * <p><b>背景</b>: 1.21 的 {@code weeping_fire} / {@code frost} / {@code true_damage} 是**数据驱动**
 * damage_type(datapack JSON)。1.20.1 同样存在 damage_type 注册表, 但需**代码注册** + 同名
 * JSON 描述文件(见 {@code src/main/resources/data/zhonz_more_enchantments/damage_type/}),
 * 二者缺一不可: 代码注册给出注册表条目, JSON 提供 message_id/scaling 等描述
 * (由 {@code DamageTypeTags} 与死亡消息使用)。
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

    public static final DeferredRegister<DamageType> DAMAGE_TYPES =
            DeferredRegister.create(Registries.DAMAGE_TYPE, CommonConstants1201.MODID);

    /** 哭泣之火: 带火焰穿透语义(无视火免/抗火由 FireImmunePierceMixin 等提供)。 */
    public static final RegistryObject<DamageType> WEEPING_FIRE =
            DAMAGE_TYPES.register("weeping_fire", () -> new DamageType("weeping_fire", 0.1F));

    /** 冰霜: 并入 is_freezing 标签(冬痕易伤 ×1.5 的判定依据)。 */
    public static final RegistryObject<DamageType> FROST =
            DAMAGE_TYPES.register("frost", () -> new DamageType("frost", 0.1F));

    /** 真伤: 无视护甲/附魔/抗性/效果(标签声明), 由唯有命运以 ×6 施加。 */
    public static final RegistryObject<DamageType> TRUE_DAMAGE =
            DAMAGE_TYPES.register("true_damage", () -> new DamageType("true_damage", 0.1F));

    private DamageTypes1201() {
    }

    /** 该 mod 命名空间下的资源位置(供构造 DamageSource 时取 Holder)。 */
    public static ResourceLocation id(String path) {
        return new ResourceLocation(CommonConstants1201.MODID, path);
    }
}
