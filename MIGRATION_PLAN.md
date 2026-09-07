# 增伤统一迁移计划与审计(MIGRATION_PLAN)

> 目标:把 ModEventHandlers 攻击侧所有"增伤"从手写 amount 乘法迁移到
> `ZhonzAttributes` 属性通道:结算 `final = damage × (1 + bonus_damage) × damage_multiplier`
> 只在最后一步执行一次。**语义口径(用户已定)**:文案是"+X%"或(1+X)类 → 加伤,
> 写入 bonus_damage(ADD_VALUE 累积 X);文案是"×N"或"N倍"类 → 乘伤,写入
> damage_multiplier(总乘积)。执行/秒杀类(finale/harvest/sanction/终点倒计时/剥壳真伤)不迁移。
> 注意:乘伤总乘积需在攻击时动态聚合后写入单一 transient modifier(避免属性加算把两个 ×1.5 变 ×2)。

## 攻击侧增伤函数清单(审计)

| # | 函数 | 现有算式 | 语义(加伤=+X% / 乘伤=×N) | 备注 |
|---|---|---|---|---|
| 1 | applyFleshBoneIncoming | ×1.30 / ×0.70 | 减免类,受击方 | 不迁(防御) |
| 2 | applyCharger | ×(1+speed*level*0.5) | +X% | 乘伤聚合乘积时纳入 (1+X) 需转加伤?**歧义** |
| 3 | applyLiberator | ×multiplier(0.1~20) | 混合 | 0.1<1 部分=减伤,**乘伤**倾向;见待问 Q1 |
| 4 | applySupremeArtDamage | ×(1+0.2×lv) | 加伤 +20%/lv | 加伤 |
| 5 | applyArmyBreaker | ×(1+bonus) | 加伤 | 加伤 |
| 6 | applyMySeaDomainDamage | ×1.6 | +60% | 加伤 |
| 7 | applyBloodWeep | ×(1+damageBonus) | 加伤 | 加伤 |
| 8 | applyFoolsMask | ×mult(0.01~约2) | 乘伤(幸运倍数) | 乘伤/见 Q1 |
| 9 | applyBloodPathBonus | ×multiplier | 乘伤(kills 叠层) | 乘伤 |
| 10 | applyBoneBreakAttack | ×6.0 | ×6 | **乘伤** |
| 11 | applyDeepSeasGrace | ×(受击回复) | 防御 | 不迁 |
| 12 | applyProphetsCallBonus | ×2.7(受击方) | 防御 | 不迁 |
| 13 | applyFleetFootstepsDamage | ×(?) | 见函数 | 待复核 |
| 14 | applyRhythm | ×1.5 | +50% | 加伤 |
| 15 | applyLonelyNoon | ×mult | 火焰易伤乘数 | 乘伤聚合 |
| 16 | applyWeepingChild | ×mult(3 或 1) | +200% | 加伤(自燃×3 是×N→乘伤)**歧义 Q2** |
| 17 | applyCorneredBeastDamage | ×1.6 | +60% | 加伤 |
| 18 | applyViolentPulseAttack | ×(低血加成) | 加伤 | 加伤 |
| 19 | applyCeaselessHuntDamage | ×mult | 加伤 | 加伤 |
| 20 | applyNewSun | ×(1+光照×0.1) | 加伤 | 加伤 |
| 21 | applyRapidAscentDamage | ×mult(下落) | 乘伤? | **歧义 Q3** |
| 22 | applyHalt | ×1.4 | +40% | 加伤 |
| 23 | applyPaleMidnightDamage | ×1.5 | +50% | 加伤 |
| 24 | applySorrowfulRed | ×(1+0.1×filled) | 加伤 10%/格 | 加伤 |
| 25 | applySnowWound | ×1.5(雨天/联动) | +50% | 加伤 |
| 26 | applyTitan | ×2.0 | ×2 | **乘伤** |
| 27 | (73-89)keen_will 已按基础攻击属性 | — | 攻击力属性 | 保持 |
| 28 | (73-89)unyielding 真伤 ×6 于 true 源 | ×6 | 真伤转换 | 保持(特殊) |
| 29 | 85/86 sojourner/wayfarer | 移速/效果 | 属性/效果 | 不属增伤 |

## 迁移骨架(已就绪)
- `ZhonzAttributes.BONUS_DAMAGE` / `DAMAGE_MULTIPLIER`(注册+全 LivingEntity 挂载)
- `ModEventHandlers.applyUnifiedDamageAttributes`(最后统一乘)
- `addPercentBonus` / `setDamageMultiplier` 帮助方法
- 目灯按用户口径:三个属性(CRIT_DAMAGE/bonus/mult)同时 ×0.5

## 迁移进度(updated round12)
- ✅ **收到伤害通道 incoming_damage(用户新增, round-incoming)**: 受击侧易伤/减伤统一迁移
  - 语义: 最终受到伤害 = 经保护附魔与护甲结算后的伤害 × incoming_damage(默认 1; 易伤>1 减伤<1)
  - 属性注册+全 LivingEntity 挂载; common 引擎 settleIncoming/setIncomingDamage(纯函数)
  - 护甲后统一结算(onLivingDamage 受击段); 护甲前仅保留免疫/保命/标记副作用
  - tick 常驻聚合: apex×0.4/cornered低血×0.5/luxurious满血×1.5/flesh_bone×1.3或0.7/rapid y<0
  - 事件条件: 海疆易伤/先知×2.7/冬痕冰霜×1.5/燃烧黄昏火焰/惨白标记×1.3/不停狩叠层
  - 实测: cornered低血受击 40.0×0.5=20.0(IncomingDamage debug); 主工程 testall 63/63
  - 1.20.1 forge/neoforge 同步(注册+挂载+护甲后 settle+事件条件易伤); 三平台 compile ✅
- ✅ sorrowful_red → bonus(背包格, 验证 13.20)
- ✅ supreme_art → bonus(+20%/级, 仅主手, 验证 9.60)
- ✅ new_sun → bonus(+150%×光/15), 点燃副作用保留链中
- ✅ rapid_ascent → bonus(攻击侧 +y/100), incoming 减伤仍在事件内
- ✅ cornered_beast → bonus(生命<25% +60%), 受伤-50%/治疗+50% 保留
- ✅ **bone_break → damage_multiplier 乘伤通道**(×6, 主手无条件), 服务器实测 48.0=8×6
- ✅ **Q8 落地(round10)**: 属性/聚合/辅助函数全收 LivingEntity, 兜底刷新对所有攻击者生效
- ✅ **Q9 乘伤事件通道(round11 试点 titan)**: 事件判定 → 临时 modifier(delta=当前值×(factor-1), 严格乘积)→ 结算 → 清除; 精英牛 16.0=8×2 / 普通牛 8.0 无泄漏
- ✅ **事件加伤通道 + 批量迁移(round12)**: 新增 `computeEventBonusPercent` + `applyEventBonusTemporary`
  (ADD_VALUE 累加进 bonus_damage, 与 tick 加伤严格累加) + `clearEventBonusTemporary`
  - **liberator ×(0.1~20)** → 乘伤事件通道(computeEventConditionalMultiplier, 保留 last-attack 计时副作用)
  - **charger(速度加成)** → 加伤事件通道
  - **my_sea_domain +60%(三叉戟)** → 加伤事件通道(标记副作用保留 applyMySeaDomainMark)
  - **blood_weep +20/30/45%** → 加伤事件通道(自伤副作用保留 applyBloodWeepCost)
  - **army_breaker 目标低血 +10/20/30%** → 加伤事件通道
  - **blood_path 同种击杀 +0.1%/个** → 加伤事件通道
- ✅ **round12b 批量迁移**(compile ✅ + testall 63/63 ✅, 数值实测见下)
  - **乘伤事件通道新增**: fools_mask(随机 ×(1~3)幸运/×(0.01~1)不幸, buff/debuff 副作用拆出 applyFoolsMaskSideEffects)、
    lonely_noon(着火 ×1.5/配燃烧黄昏 ×2)、weeping_child(自燃 ×3, 联动 ×2; 点燃副作用拆出 applyWeepingChildIgnite)、
    snow_wound(雪天 ×1.5, 配雪的殇 ×1.5, 乘叠)
  - **加伤事件通道新增**: halt(目标不动 +40%, 减速副作用拆出 applyHaltSlow)、
    pale_midnight(+50% 无条件, 发光/易伤标记拆出 applyPaleMidnightMark)、
    ceaseless_hunt(叠层 +5%/层, 叠层副作用拆出 applyCeaselessHuntStack)
  - 服务器实测: lonely_noon 16.5(=11×1.5)、weeping_child 33.0(=11×3)、pale_midnight 16.5(=11×1.5)、
    ceaseless_hunt 11.55(=11×1.05) ✅
- ✅ **round13 flat 通道 + rhythm 拆解**(compile ✅ + testall 63/63 ✅)
  - 新增 `ZhonzAttributes.FLAT_DAMAGE`(flat_damage, 绝对量加伤, 全 LivingEntity 挂载)
  - 统一结算: `final = amount ×(1+bonus) ×mult + flat`(Q5 公式 + flat 层, 用户 round13 决策)
  - 辅助: `setFlatDamage(entity, id, value)` / `clearEventFlatTemporary`(结算后清除)
  - **fleet_footsteps(+上次伤害 10%)** → flat 通道(EVENT_FLAT_FLEET, 记录副作用保留)
  - **fleeting_grace(+记录值×2)** → flat 通道(EVENT_FLAT_GRACE)
  - **rhythm ×1.5** → 乘伤事件通道: 计时副作用留链上 applyRhythm(命中写 KEY_RHYTHM_HIT_ATTACK),
    computeEventConditionalMultiplier 读标志 ×1.5; 实测 rhythm 16.5=11×1.5 ✅
- ✅ recordMourningDamage 移到统一结算后(加成后基准)
- 验证方式: 服务器实测各迁移项数值; 按用户指示"内容完成后最后统一 testall 63/63"
- ✅ Q8: 非玩家生物持附魔武器 → 也读属性(乘伤通道已全生物, 见上)

## 实施规则 6(round13 最终版)
- **flat 绝对加伤通道已落地**: flat_damage 属性(绝对量, 结算最后 +flat, 不受 %/× 缩放);
  fleet_footsteps / fleeting_grace 已迁入; 其余攻击侧乘法/百分比全部经 bonus/mult/flat 通道, 链上无手写乘算。

## 待问清单 → 已答(用户官方口径, 2026-09 洗澡后)
- Q1:数值 <1 的乘数(liberator 0.1~20 / fools_mask 0.01~1)→ **乘伤**,乘伤属性允许 <1(减伤),不 clamp。
- Q2:weeping_child"自身燃烧 ×3"、lonely_noon ×mult 等 ×N 文案 → **乘伤**。
- Q3:rapid_ascent 下落、fleet_footsteps 条件倍率 → **统一走乘伤聚合**。
- Q4:非 Player 生物(手持附魔武器) → **也读这两个属性**(applyUnified 已接受 LivingEntity)。
- Q5:**接受组合语义重组**;公式:有两个属性时 `最终伤害 = 基础伤害 × (1 + 增伤百分比) × 乘伤百分比`(与本实现一致);若只留一个属性则先加算后乘算。
- Q6:**乘伤聚合的刷新时机 = tick 缓存**。
- Q7:链中副作用(溅射/护甲减免/命中记录)的基准 = **加成后**(故 recordMourningDamage 已移到统一结算之后;area_strike/shell_strip/explosive_dawn/fleet 等也要以最终 amount 为基准——迁移时把这些副作用移出链,改在统一结算后调用)。
- 架构红线(记入待确认,不阻塞):目标条件型(army_breaker 目标低血 / lonely_noon 目标着火 / pale_midnight 目标标记 / titan 目标精英)在纯 tick 属性模型里无法表达"只对满足条件的目标生效"——倾向保留事件内判定,无条件/自身状态型(可 tick)走属性。此点在最终实现中按"事件判定倍率 × 属性通道"折中,等你最后过目。
- **Q9(新增遗留)**:目标/事件条件型(halt/pale_midnight/cornered_beast/army_breaker/lonely_noon/fools_mask/titan 等)是否也要属性化?(纯 tick 无法表达;若必须,需事件内临时 modifier 方案,复杂)。倾向:保留事件判定,不加属性——待用户确认。

## 实施规则(迁移模板)
1. **无条件/自身状态可 tick 的加伤** → tick 写 bonus_damage(独立 modifier id, ADD_VALUE 百分比),链中删除乘法。(已完成:sorrowful_red、supreme_art、new_sun、rapid_ascent)
2. **无条件/自身状态可 tick 的乘伤** → tick 写 damage_multiplier(独立 modifier id, 乘法语义需乘积——多个乘伤同时时在 tick 末把总乘积写入单一 modifier)。
3. **目标/事件条件型**(halt 目标不动 / pale_midnight 头 / army_breaker 目标低血 / lonely_noon 目标着火 / cornered_beast 自身低血但事件触发 / fools_mask 随机 / titan 精英)→ **Q9 模板(round11 确立, titan 已验证)**: 攻击事件内判定总乘积
   → `applyEventMultiplierTemporary`(delta=当前 mult 属性值×(factor-1), 保持与 tick 聚合严格乘积)
   → 统一结算 → `clearEventMultiplierTemporary` 立即还原。链上删手写乘。
   纯加伤型事件条件(army_breaker +10~30% 等文案"+"的)另议: 可写 bonus_damage 临时 modifier(delta=percent, 同套 apply/clear 模式)。
4. 副作用(溅射/记录/真伤减免)统一移到结算之后,以最终 amount 为基准(Q7)。
5. **先不逐项测试**:按用户指示先完成全部内容迁移,内容完成后改框架(阶段二/三),最后统一跑 testall 63/63 回归。

## 阶段二(切 UniMined 前)
- 把 `applyXxx` 归为"平台无关逻辑层",仅事件订阅留在 NeoForge 层,便于移植 Forge 1.20.1
