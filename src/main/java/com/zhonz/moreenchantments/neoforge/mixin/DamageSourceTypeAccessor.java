package com.zhonz.moreenchantments.neoforge.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@link DamageSource} 的"原地改写"入口: 只替换"这是什么伤害 / 谁打的"这三个字段。
 *
 * <p>本 mod 的伤害类型转换(哭泣之火 / 冰霜 / 真伤)必须把**已有** DamageSource 原地改写,
 * 不能新建对象顶替 —— 其它 mod 的伤害链路依赖 {@code source instanceof 其子类} 与子类私有
 * 状态工作。典型如 Epic Fight: {@code EntityEvents.damageEvent} 只在
 * {@code event.getSource() instanceof EpicFightDamageSource} 时触发
 * {@code DEAL_DAMAGE_EVENT_DAMAGE}, 武器技能充能(WEAPON_CHARGE)就挂在该事件上
 * (EF 1.20.1 {@code ServerPlayerPatch.java:56-69})。新建普通 DamageSource 后
 * instanceof 为假 → 事件不触发 → "装了哭泣之子后武器技能进度条不动"。
 *
 * <p>改写范围与原实现 {@code new DamageSource(holder, attacker, attacker)} 严格对齐 ——
 * 只有这三个参数(伤害类型 / directEntity / causingEntity)被改, 因此**本 mod 其它附魔效果
 * 与死亡消息口径不变**; 新增保留下来的是子类身份、子类私有状态(EF 的充能/硬直/伤害修正器)
 * 与 damageSourcePosition(EF 的击退方向) —— 这些在原实现里被丢弃。
 *
 * <p>三个字段在 vanilla 都是 {@code private final}, 故需 {@link Mutable} 去掉 final。
 * 字段名 → SRG 的映射由 refmap 在构建期生成(先例: {@link ThrownTridentAccessor} 的
 * "tridentItem" → "f_37555_"), 故 1.20.1 生产环境亦可用。
 */
@Mixin(DamageSource.class)
public interface DamageSourceTypeAccessor {

    /** 把该伤害源的伤害类型原地换成 {@code type}(保留对象身份, 不新建实例)。 */
    @Mutable
    @Accessor("type")
    void zhonz$setDamageType(Holder<DamageType> type);

    /** 直接实体(与原实现 new DamageSource(holder, attacker, attacker) 的第 2 参数对齐)。 */
    @Mutable
    @Accessor("directEntity")
    void zhonz$setDirectEntity(Entity directEntity);

    /** 造成者(与原实现 new DamageSource(holder, attacker, attacker) 的第 3 参数对齐)。 */
    @Mutable
    @Accessor("causingEntity")
    void zhonz$setCausingEntity(Entity causingEntity);
}
