# Zhonz's More Enchantments — 会话状态(压缩上下文)

> 用途:长会话压缩参考。新会话/子代理先读此文件再动手。

## 最新状态(2026-09-12 晚,本轮收尾)
- ✅ **「于此显圣」扩展到 4 条免死路径**(上个会话做到一半被框架崩溃打断,本轮完成)
  - 新增 `util/ManifestHelper`(共享: `hasManifest(ItemStack)` / `holdsManifest(LivingEntity)` / `burst(LivingEntity)`);
    三平台各有一套(`platforms/*/**/ManifestHelper1201.java`)
  - `ManifestTotemMixin` 改为复用 helper(主工程 + 1.20.1 双平台);`ModEventHandlers` 三个免死路径
    (`trySmartTotem` / `tryReturnFromHell` / `tryDivineProtection`)各加一处 `burst` 调用
  - 新增验证命令 **`/zhonztest manifesttest`**:真实僵尸走完整死亡管线,断言"存活 + 自身抗性提升 V(amp 4) +
    旁 2 格僵尸定身(移动缓慢 XI)",另含两项反例(普通图腾 / 无图腾)
  - 结果: **5/5 通过**(原版图腾 / 自地狱中归来 / 神护 三条路径实测;智能图腾分支仅对 Player 生效,僵尸无背包 → SKIP,
    属结构接线,运行时需真实玩家)+ **testall 63/63** ✅,三平台 compile ✅
- ⚠️ **测试陷阱(重要)**:`LivingEntity.kill()` 内部用 `damageSources().genericKill()`,该伤害带
  `BYPASSES_INVULNERABILITY` 标签,而 `checkTotemDeathProtection` 对该标签的伤害**直接返回 false**
  (原版语义:图腾挡不住 /kill)。用 kill() 测图腾类免死会得到假失败 —— 必须用
  `victim.hurt(level.damageSources().generic(), 大数值)`。
  另: **FakePlayer 完全不吃伤害**(hurt(5)→ 0 伤害且返回 false),无法用于死亡管线测试,一律用真实生物。

## 项目
- NeoForge 1.21.1 附魔 Mod,工作区 `D:\WXH\workspace\mcmodmaker\ZhonzsMoreEnchantments`(Windows, gradlew.bat, `GRADLE_USER_HOME` 用工作区内 `.gradle_home`)
- 前置:Apothic Attributes 1.21.1-2.10.1 + Placebo(必需,`build.gradle` 已排除 Curios)
- 设计原则:增伤只参与最后伤害判定;兼容性优先。当前规划:**升级为 UniMined/MultiLoader,同时支持 Forge 1.20.1 + NeoForge 1.21.1**

## 已实现
- 附魔 72 + 新设计 73-89 全部 17 个已注册实现(共 89),`ENCHANTMENTS.md` 为权威设计/实现说明(五章已标 ✅)
- 统一伤害框架:自定义伤害类型 weeping_fire/frost/true_damage;`util/WeepingFireHelper`(hurt HEAD 转伤害类型);mixin 集合(目录 `src/main/java/com/zhonz/moreenchantments/mixin/`)
- **统一增伤属性通道(新建)**:`attribute/ZhonzAttributes.java`
  - `bonus_damage`(加伤,结算 ×(1+加伤))
  - `damage_multiplier`(乘伤,结算 ×乘伤)
  - 结算 `final = damage × (1+bonus) × mult`,已接入 onLivingDamage 最后一步
  - 已挂到全部 LivingEntity(EntityAttributeModificationEvent);lang 键已加中英
- **锐意(keen_will)按用户修正**:武器基础攻击 ATTACK_DAMAGE 每层 +1(10s 可叠),非最终伤害加值 ✓
- **目灯(eye_lamp)严格版**:受击给攻击者 3s"眩惑"——CRIT_DAMAGE ×0.5、bonus_damage ×0.5、damage_multiplier ×0.5(三属性同减半)
- **已迁入属性通道的示范**:sorrowful_red(背包格数+10%/格→bonus)、supreme_art(+20%/级→bonus);recordMourningDamage 移到统一结算后
- 测试:`/zhonztest testall` 63/63 通过(runServer + RCON `tools/rcon.js`,`node tools/rcon.js "zhonztest testall"`)

## 用户最终口径(2026-09 洗澡后一次性回答)
- Q1 <1 乘数 → **乘伤**,允许 <1(不 clamp)
- Q2 "自身燃烧×3"等 ×N 文案 → **乘伤**
- Q3 条件倍率(rapid_ascent/fleet 等)→ **统一乘伤聚合**
- Q4 非 Player 生物 → **也读这两个属性**
- Q5 组合语义 → **接受重组**;两属性公式:`最终伤害 = 基础 × (1 + 增伤百分比) × 乘伤百分比`
- Q6 乘伤刷新时机 → **tick 缓存**(每 tick 把聚合乘积写入 damage_multiplier)
- Q7 副作用基准 → **加成后**(溅射/减免/记录均以最终统一结算后为基准)
- 流程指示:**先完成全部内容迁移(不逐项测试)→ 改框架(阶段二/三)→ 最后统一 testall 63/63 回归**
- 迁移中只保 compileJava 通过;runServer/testall 推迟到内容迁移完成之后
- debuff 允许自定义 MobEffect;附魔名称必须与 ENCHANTMENTS.md 逐字一致(含引号/符号),句子式名称完整翻译

## 待确认问题(Q8/Q9/Q12/Q13 — 用户已拍板)
- ✅ **Q8 = 是(已落地, round10)**:非玩家生物(mob)手持附魔武器也享受属性通道加成。实施:`refreshDamageMultiplierAggregate`(原 tickDamageMultiplierAggregator)、`addPercentBonus`、`setDamageMultiplier` 全部改收 `LivingEntity`;onLivingDamage 结算前兜底刷新取消 `instanceof Player` 分支,对所有 LivingEntity attacker 生效。compile ✅ + runServer testall **63/63** ✅。
  - 边界:加伤型 tick(supreme_art 等)仍在 PlayerTick 路径(依赖玩家 data),mob 侧乘伤通道(骨碎 ×6)已全生物覆盖;如需 mob 侧加伤 → 后续加 LivingTick,不在本回合。
- ✅ **Q9 = 可以(已落地 round11-13)**:事件条件型增伤 = "事件内判定 → 临时 modifier(乘伤 delta=当前mult×(factor-1) 严格乘积 / 加伤 ADD_VALUE 累加)→ 统一结算 → 清除"。
  试点 titan(乘伤)、批量 17+ 项见 `MIGRATION_PLAN.md` 进度段。
- ✅ **Q12/Q13(round13 现场确认)**:flat 绝对加伤(fleet_footsteps/floating_grace)→ **另设 flat_damage 属性通道**;
  rhythm ×1.5 → **拆副作用后进乘伤事件通道**。compile ✅ + testall 63/63 ✅。

## 进行中(阶段一:全量增伤迁移)
- ✅ 攻击侧手写乘法已全部迁入统一属性通道(bonus_damage 加伤% / damage_multiplier 乘伤× / flat_damage 绝对量):
  tick 通道(无条件自身态)+ 事件临时 modifier(目标/时序条件型)+ flat 通道。已迁移 20+ 项,每轮 testall 63/63。
  详细台账见 `MIGRATION_PLAN.md` 进度段(updated round13)。
- 分类规则:无条件/自身状态可 tick 的加伤 → tick 写 bonus(每附魔独立 modifier id);乘伤 → tick 写 damage_multiplier(总乘积单值);目标/事件条件型 → 事件内判定但仍只经统一结算出口;副作用(溅射/减免/记录)移到统一结算后,基准=加成后
- 客户端才可见项未实测(推迟到统一测试):庄严哀悼粒子、三千万转右键龙蛋、慈悲右键信标绑定+均分(BeaconMercyMixin)、铸就全一城盾盾挡、天之锁、唯有命运免死

## 测试栈
- runServer(专属服务器,`run/` world"新的世界",offline;RCON 25575,密码 zhonz_test_rcon)
- 日志 `run/logs/debug.log`(DEBUG 配置)/`latest.log`
- 测试命令: `/zhonztest testall`(63 项攻击侧回归)、`/zhonztest manifesttest`(5 项免死/显圣路径)、
  `giveweapon`(按附魔选载体, 三千万转=下界星、慈悲=附魔书、铸成=盾牌)、`equiparmor`、`attack`、`damage`、`info`
- MaaMCP 曾用于客户端控制,当前未连;窗口"Minecraft NeoForge* 1.21.1"

## 阶段二收尾(round16, 已完成)
- ✅ tick 加伤 percent 下沉 common/damage/TickBonusRules(5 项, 事件层 tick 委托)
- ✅ mixin 18 个 → neoforge/mixin 包, mixins.json package 同步(运行时验证正常)
- ✅ 死代码清理(死存根/未用 import);MODID 单源化(CommonConstants, 全项目引用替换)
- ✅ common 包确认零平台 import(UniMined 复用硬前提)
- ✅ 备份 `_backup_pre_unimined/`(无 git, 切构建前快照)
- 验证: offline compile ✅; runServer testall 63/63 ✅(mixin 平移/清理后)

## 多平台矩阵完成(round 7-9, 最终交付)
- ✅ 目标修订: 4 平台 → **3 平台**(Apothic 前置仅 1.20.1-forge/1.3.7 与 1.21.1-neo/2.10.1; 1.21.1-forge 无前置 → 移除)
- ✅ **NeoForge 1.21.1**(主工程): 89 附魔完整, testall **63/63**(跨版本重构全程零回归)
- ✅ **Forge 1.20.1**(platforms/1.20.1-forge): 89 附魔代码注册 + 效果 3 批(Tick 19/SideEffects 20/
  NewEnchants 73-89)+ 16 mixin(删除 MaceItemMixin, 1.20.1 无此类)+ EnchantWiring1201 接线;
  compile ✅ + 壳启动 Done(25.9s)+ jar 122KB
- ✅ **NeoForge 1.20.1**(platforms/1.20.1-neoforge): 与 forge 全量同步(27 文件), compile ✅ + jar 156KB
- 平台差异: attributeslib(1.20.1 包 dev.shadowsoffire.attributeslib.api, RegistryObject.get)/
  UUID modifier / EnchantmentHelper.getItemEnchantmentLevel / new ResourceLocation /
  ForgeMod.STEP_HEIGHT_ADDITION 等; 详见 UNIMINED_MIGRATION.md 6.7
- 验证边界: 1.20.1 dev 无 attributeslib remap → mixin 运行时注入验证需真实客户端(生产 mods 双装)
- GitHub: 已推送 Zhonz/Zhonz-s-More-Enchantments(干净单提交历史 7b3227e, 8MB; 清除误入 846MB hprof)

## 收到伤害通道(incoming_damage, 用户新增)
- 语义: 最终受到伤害 = 经保护附魔与护甲结算后的伤害 × incoming_damage(默认 1; 易伤>1 减伤<1)
- 主工程实现: ZhonzAttributes.INCOMING_DAMAGE(注册+全 LivingEntity 挂载); common 引擎
  settleIncoming(纯函数)/setIncomingDamage(聚合写入); onLivingDamage 受击段:
  refreshIncomingAggregate(tick 常驻: apex×0.4/cornered低血×0.5/luxurious满血×1.5/flesh_bone×1.3或0.7/
  rapid y<0减伤) + applyIncomingSettlement(事件条件: 海疆易伤/先知×2.7/冬痕冰霜×1.5/燃烧黄昏火焰/
  惨白标记×1.3/不停狩叠层) → 统一乘
- 护甲前(onLivingHurt)已移除全部易伤/减伤乘法, 仅保留免疫/保命/标记副作用
- 实测: cornered低血 zombie 受击 40.0×0.5=20.0(IncomingDamage debug 确认)
- 1.20.1 forge/neoforge 已同步(INCOMING_DAMAGE 注册+挂载+settleIncoming 接入); 三平台 compile ✅
- testall 63/63 ✅(受击侧数值断言需后续受击测试命令; testall 项全为攻击侧)

## 版本差异要点(迁移到 1.20.1 Forge 时注意)
- 1.20.1 Forge:无 LivingIncomingDamageEvent/DamageContainer(1.21 NeoForge 伤害管线大改),附魔为代码注册非 1.21 数据驱动;1.21.1 依赖 Mojang mapped 方法签名,mixin 目标随版本不同
- Apothic Attributes 1.20.1 Forge 存在(独立版本),但 API/事件内部不同,需逐项核对
