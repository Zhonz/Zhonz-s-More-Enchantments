package com.zhonz.moreenchantments.neoforge1201;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

/**
 * "攻击者 + 目标伤害类型 + 倍率"三元组(等价 1.21 {@code WeepingFireHelper.AttackerType})。
 *
 * <p><b>为什么必须放在这里、而不是写成 mixin 里的嵌套 record</b>:
 * 该类型是 {@code WeepingFireMixin} / {@code PlayerWeepingFireMixin} 注入方法的返回值 ——
 * 一旦注入进目标类({@code LivingEntity} / {@code Player}), JVM 解析目标类时就必须加载这个类型;
 * 而它若位于 mixin 包({@code com.zhonz.moreenchantments.neoforge1201.mixin})内, Mixin 会直接拒绝:
 * <pre>
 * IllegalClassLoadError: ...WeepingFireMixin$AttackerType
 * is in a defined mixin package ... and cannot be referenced directly
 * </pre>
 * 后果是**服务端启动即崩**。放在 mixin 包之外就是普通类。
 */
public record AttackerType1201(LivingEntity attacker, ResourceKey<DamageType> type, float multiplier) {
}
