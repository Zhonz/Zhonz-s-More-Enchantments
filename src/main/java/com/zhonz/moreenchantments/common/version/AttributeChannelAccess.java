package com.zhonz.moreenchantments.common.version;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.resources.ResourceLocation;

/**
 * 属性通道访问适配(stage 跨版本层)。
 *
 * 1.20.1 与 1.21.1 的 AttributeModifier/AttributeInstance/Operation API 差异:
 * <ul>
 *   <li>1.21: {@code AttributeModifier(ResourceLocation, double, Operation)} /
 *       {@code AttributeInstance.removeModifier(ResourceLocation)} /
 *       Operation.ADD_VALUE</li>
 *   <li>1.20.1: {@code AttributeModifier(UUID, String, double, Operation)} /
 *       {@code AttributeInstance.removeModifier(AttributeModifier)} /
 *       Operation.ADDITION</li>
 * </ul>
 * 引擎(UnifiedDamageEngine)只经本接口操作属性: 构造 modifier(本模组统一用"加值"
 * operation, 平台实现各自写对版本的枚举成员), 按 id 移除; 属性读取/实例获取由平台
 * 事件层直接按各自版本完成(平台代码), 不经过本接口。平台实现者(NeoForge/Forge)各自实现。
 */
public interface AttributeChannelAccess {

    /** 构造"加值"型 AttributeModifier(ADD_VALUE/ADDITION, 平台实现写对版本枚举)。 */
    AttributeModifier makeAddModifier(ResourceLocation id, double amount);

    /** 从实例移除指定 id 的 modifier(1.21 removeModifier(RL); 1.20.1 需按 UUID 匹配)。 */
    void removeModifier(AttributeInstance inst, ResourceLocation id);
}
