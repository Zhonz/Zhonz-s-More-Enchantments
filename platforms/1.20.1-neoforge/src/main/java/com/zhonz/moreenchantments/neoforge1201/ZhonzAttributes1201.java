package com.zhonz.moreenchantments.neoforge1201;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 1.20.1 Forge 平台: 统一增伤属性注册(bonus_damage / damage_multiplier / flat_damage)。
 *
 * 与 1.21.1 NeoForge 同名同语义(见根工程 ZhonzAttributes), 注册 API 按 1.20.1 Forge
 * (DeferredRegister.create(ForgeRegistries.ATTRIBUTES, modid) + RegistryObject)。
 * 三通道语义: bonus 加伤% / mult 乘伤x / flat 固定点(与 common 引擎一致)。
 */
public final class ZhonzAttributes1201 {

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(ForgeRegistries.ATTRIBUTES, CommonConstants1201.MODID);

    public static final RegistryObject<Attribute> BONUS_DAMAGE = ATTRIBUTES.register("bonus_damage",
            () -> new RangedAttribute("attribute." + CommonConstants1201.MODID + ".bonus_damage", 0.0D, -1.0E9D, 1.0E9D)
                    .setSyncable(true));

    public static final RegistryObject<Attribute> DAMAGE_MULTIPLIER = ATTRIBUTES.register("damage_multiplier",
            () -> new RangedAttribute("attribute." + CommonConstants1201.MODID + ".damage_multiplier", 1.0D, 0.0D, 1.0E9D)
                    .setSyncable(true));

    /** flat 固定点加伤(绝对量)。 */
    public static final RegistryObject<Attribute> FLAT_DAMAGE = ATTRIBUTES.register("flat_damage",
            () -> new RangedAttribute("attribute." + CommonConstants1201.MODID + ".flat_damage", 0.0D, -1.0E9D, 1.0E9D)
                    .setSyncable(true));

    /** 收到伤害通道(default 1): 最终受到伤害 = 护甲后伤害 × incoming_damage(易伤>1 减伤<1)。 */
    public static final RegistryObject<Attribute> INCOMING_DAMAGE = ATTRIBUTES.register("incoming_damage",
            () -> new RangedAttribute("attribute." + CommonConstants1201.MODID + ".incoming_damage", 1.0D, 0.0D, 1.0E9D)
                    .setSyncable(true));

    private ZhonzAttributes1201() {
    }
}
