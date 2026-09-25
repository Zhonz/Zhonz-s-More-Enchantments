package com.zhonz.moreenchantments.neoforge1201.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@link DamageSource} 的"原地改写"入口(1.20.1 NeoForge 版), 与主工程
 * {@code com.zhonz.moreenchantments.neoforge.mixin.DamageSourceTypeAccessor} 完全同构。
 *
 * <p>只替换"这是什么伤害 / 谁打的"三个字段(type / directEntity / causingEntity),
 * 与原实现 {@code new DamageSource(holder, attacker, attacker)} 严格对齐 ⇒ 本 mod 其它
 * 附魔效果与死亡消息口径不变; 保留下来的是子类身份与子类私有状态 —— Epic Fight 的武器技能
 * 充能(WEAPON_CHARGE)挂在"仅当 {@code event.getSource() instanceof EpicFightDamageSource}
 * 才触发的 DEAL_DAMAGE_EVENT_DAMAGE"上, 新对象顶替会让它静默失效。
 *
 * <p>三个字段在 1.20.1 都是 {@code private final} → 需 {@link Mutable}。字段名 → SRG 的映射
 * 由 refmap 生成(先例: 同包 {@link ThrownTridentAccessor} 的 "tridentItem" → "f_37555_")。
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
