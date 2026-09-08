# UniMined 多版本迁移可行性说明(阶段二/三准备)

> 目标:从"NeoForge 1.21.1 单平台"迁到 UniMined 骨架,同时支持
> Forge 1.20.1 + NeoForge 1.21.1;1.21.1 侧先以"平台无关核心 + NeoForge 适配层"跑通。

## 一、源码现状盘点(依赖面)
| 层 | 现状 | 平台耦合度 |
|---|---|---|
| 附魔数据 | `data/<mod>/enchantment/*.json`(89 个)+ ResourceKey | 1.21 数据驱动;**1.20.1 为代码注册**,无法复用 |
| 伤害管线 | onLivingHurt/onLivingDamage + DamageContainer 系 | NeoForge 专属;1.20.1 Forge 事件名与语义不同 |
| 事件 | LivingIncomingDamageEvent/LivingShieldBlockEvent/PlayerTickEvent.Post | NeoForge API |
| 属性通道 | `attribute/ZhonzAttributes`(自注册,已平台无关写法) | 低(Attribute API 两版近似) |
| mixin | ~17 个 mixin,依赖 1.21.1 Mojang mapped 字节码 | **高**:逐版本重写 |
| 前置 | Apothic Attributes 1.21.1-2.10.1(NeoForge) | 1.20.1 有独立 Forge 版本,API 有差异 |
| 工具 | ModTestCommands(测试假玩家)/RCON testall | NeoForge |

## 二、可行性结论
- **附魔注册**:1.20.1 是"代码注册 + Enchantment 子类/属性修饰"体系,与 1.21 数据驱动 JSON 完全不同 → 需要 `CommonEnchantments`(效果逻辑)与 `PlatformEnchantRegistrar`(Forge/NeoForge 各自注册)两层;**数据 JSON 只在 1.21 生效**,1.20.1 侧等价效果需代码重写。
- **伤害管线**:本项目所有"最后一步统一结算"(bonus×mult)与伤害类型转换(weeping/frost/true)基于 1.21 NeoForge 事件;1.20.1 Forge 无 DamageContainer,LivingDamageEvent 语义不同 → 建议 1.20.1 侧"事件适配层"另行实现,Common 只放纯规则计算。
- **mixins**:建议按版本维护两组(`mixins.neoforge.json` / `mixins.forge_1_20_1.json`),各自引用对应字节码目标,目录 `src/main/java/…/mixin/<platform>/`。
- **Apothic**:1.20.1 Forge 用 `apothic_attributes` 独立版本;属性 API(ALObjects.Attributes 字段名)大部分同名但需编译验证。

## 三、推荐目录结构(UniMined 双加载器)
```
src/main/java/zhonz_more_enchantments/
  common/            # 平台无关核心(纯逻辑/数据/注册键)
    enchant/         # 89 个 ResourceKey 常量(两版共用 id)
    rule/            # 伤害规则:bonus/mult 计算、事件条件判定(纯函数式,不碰 event API)
    attr/            # ZhonzAttributes(注册键,两版近似)
    util/
  forge/             # Forge 1.20.1 适配
    ...event handlers、注册、mixin 组
  neoforge/          # NeoForge 1.21.1 适配(即当前代码迁入)
    ...event handlers、DamageContainer 适配、mixin 组
src/main/resources/META-INF/{neoforge.mods.toml,mods.toml}
```
- 事件订阅留在各自平台适配层,规则层(规则计算、是否触发)下沉 common,保持"增伤只在最后一步结算"。
- 先在本仓库把当前源码按此三包重排(仍 1.21.1 编译),再交给 UniMined 骨架把 neoforge 包原样带入 + 新增 forge 包。

## 四、阶段顺序建议
1. (当前)阶段一收尾:无条件/自身状态型增伤已迁 5 项示范;事件时序型保留事件判定(Q9 待验收)。
2. 阶段二:三包重排(common/forge/neoforge)在本仓库 1.21.1 编译跑通 + 本文档。
3. 阶段三:UniMined 骨架引入;`common` 与 `neoforge` 原样迁入;补 `forge` 1.20.1 适配;mixin 双组;统一 testall(1.21.1 63/63)后再跑 1.20.1 冒烟。

## 六、阶段二实施记录(round14 起)
### 6.1 目标与做法
- 用户拍板"抽公共伤害引擎(深重构)":把"增伤只在最后一步结算"的纯规则从事件层剥离,
  迁入 `common.damage`,使其 **不 import 任何 NeoForge/Forge API**,供 UniMined 双加载器复用;
  平台差异点(属性注册、AttributeModifier 构造、事件绑定、附魔等级查询)由调用方注入或留适配层。
### 6.2 已完成(round14)
- 新建 `com.zhonz.moreenchantments.common.damage.UnifiedDamageEngine`(平台无关, 零 NeoForge import):
  - `settle(attacker, amount, bonus, mult, flat)` — 统一结算 final = amount×(1+bonus)×mult+flat
  - `addPercentBonus` / `setDamageMultiplier` / `setFlatDamage`(属性写入辅助, 参数 = AttributeInstance + id + 值)
  - `apply/clearEventMultiplierTemporary`(事件临时乘伤, delta=当前值×(factor-1) 严格乘积)
  - `apply/clearEventBonusTemporary`(事件临时加伤, ADD_VALUE 累加)
  - 设计要点: 引擎不依赖 ZhonzAttributes/ModEnchantments 具体注册(平台层传入 Holder/实例);
    结算引擎已从 ModEventHandlers 的 8 个 private 方法改为委托引擎(逻辑单源)
- 验证: compile ✅ + runServer testall **63/63** ✅(委托零回归)
### 6.3 已完成(round15, 判定规则 + 键 + 存储 + 聚合下沉)
- **附魔键下沉**: 80 个 ResourceKey 常量迁 `common/enchant/EnchantmentKeys`;
  `ModEnchantments extends EnchantmentKeys`(静态继承 → 所有引用点零改动), 仅保留平台层
  runtime Holder 解析(getHolder, ServerLifecycleHooks); MODID 下沉 `common/CommonConstants`
- **存储下沉**: `EntityDataStorage` 迁 `common/storage`(纯 MC WeakHashMap, 零依赖)
- **判定规则下沉**: `common/damage/EventDamageConditions`(computeConditionalMultiplier /
  computeBonusPercent / tickMultiplierProduct 纯规则, 零平台 import)+ `EnchantmentLevelLookup`
  (等级查询接口, 平台层实现)+ `EventDamageContext`(状态键注入); 事件层 compute 两函数与
  乘伤聚合 refreshDamageMultiplierAggregate 均改为 ctx 委托; 数值实测与委托前一致
  (bone_break 102=17×6 / rhythm 25.5=17×1.5 / weeping_child 51=17×3 / liberator 1.4 等)
- 验证: compile ✅ + runServer testall **63/63** ✅(每步后回归)
### 6.4 已完成(round16 阶段二收尾)
- **tick 加伤 percent 计算下沉**: 新增 `common/damage/TickBonusRules`(supremeArt/newSun/corneredBeast/
  rapidAscent/sorrowfulRed 纯计算); 事件层 5 个 tick 函数改委托(percent 计算单源, 副作用保留事件层)
- **mixin 分组**: 18 个 mixin 平移到 `neoforge/mixin` 包, mixins.json package 同步
  (为将来 forge.mixin 组留对称位); 运行时验证 mixin 正常加载
- **清理**: 删除死存根(applyCharger/applyLiberator/applyArmyBreaker/applySupremeArtDamage/
  applyTitanLegacyReference/isSnowWeather 等已下沉/无调用者), 移除 EnderDragon/WitherBoss unused import
- 验证: compile ✅ + runServer testall **63/63** ✅(每步后回归)
### 6.5 多平台矩阵规划(round16, 用户扩展为 4 平台)
- 目标矩阵: **NeoForge 1.21.1**(现状) + **Forge 1.21.1** + **NeoForge 1.20.1** + **Forge 1.20.1**
- 工具: [Unimined](https://github.com/unimined/unimined)(LTS 分支 lts/1.4; 插件 id `xyz.wagyourtail.unimined`)
  - 同 MC 版本多加载器 = 一个 gradle 项目内多个 sourceSet, 各声明 loader:
    `unimined.minecraft(sourceSets.forge) { forge { loader '...' mixinConfig '...' } }`
    `unimined.minecraft(sourceSets.neoforge) { neoForge { loader '...' } }`, 均 `combineWith(sourceSets.main)`
  - 不同 MC 版本(1.21.1 vs 1.20.1)需要各自独立 MC 环境 → 建议**每版本一个子项目**
    (如 `:1.21.1-common/:1.21.1-forge/:1.21.1-neoforge` 或版本前缀多项目), common 源码以
    组合 include 共享
  - 示例: repo testing/1.21-NeoForge-Fabric 与 1.20.1-NeoForge-Fabric(build.gradle 形态见上)
- 分平台源码归属(阶段二已完成的基础):
  - `common`(平台无关, 零 loader import): UnifiedDamageEngine / EnchantmentKeys / CommonConstants /
    EventDamageConditions / EnchantmentLevelLookup / EventDamageContext / TickBonusRules / EntityDataStorage
  - `neoforge/*`(NeoForge 1.21.1 适配, 现状已落位): ModEventHandlers / ModEnchantments(getHolder) /
    ZhonzAttributes(注册) / ModTestCommands / ZhonzMoreEnchantments 入口 / neoforge.mixin 18 个 / util / entity
  - `forge/*`(待建): Forge 1.21.1 适配(事件 API 同代差异、附魔仍数据驱动 JSON 可复用、mixin 同目标可复用 → 相对低工作量)
  - 1.20.1 版本: 附魔改为**代码注册**(非数据驱动 JSON), 伤害管线无 DamageContainer(LivingDamageEvent 语义不同),
    mixin 字节码目标不同, Apothic 用 1.20.1 版 → 属高工作量, 见"风险"
- 迁移顺序建议: ①本仓库先备份(无 git, 用 zip) ②建 UniMined 多项目骨架, 先让 NeoForge 1.21.1 在
  UniMined 下编译+63/63 ③加 Forge 1.21.1 sourceSet(数据 JSON 复用) ④再做 1.20.1 两平台(代码注册+事件/mixin 重写)
- 完成标志: ModEventHandlers 内不再有"结算规则/通道写入/条件判定"实现, 只留事件订阅 + 副作用;
  编译 + 63/63 保持; 更新本文档依赖面表格。

## 6.6 平台差异精确审计(round16 离线完成, 1.21.1 → 1.20.1 移植工作量基准)
事件层(ModEventHandlers, 8 个订阅方法)的平台耦合盘点:
| 事件/API | 1.21.1 NeoForge(现状) | 1.20.1 移植 | 量级 |
|---|---|---|---|
| LivingIncomingDamageEvent(预护甲) | import 19 处, onLivingHurt | **无此事件**; 1.20.1 单 LivingDamageEvent(pre-armor 语义不同) | 高: 需按 1.20.1 事件语义拆分防御侧逻辑 |
| LivingDamageEvent.Pre(结算后) | onLivingDamage + getNewDamage/setNewDamage | 1.20.1 LivingDamageEvent 直接 setAmount | 中 |
| setCanceled | 9 处 | 同 API(Forge/NeoForge 均有) | 低 |
| DamageType 数据驱动(WEEPING_FIRE/FROST/TRUE_DAMAGE) | JSON + ResourceKey(21 处引用) | 1.20.1 DamageType 需代码注册(RegistryObject/DeferredRegister) | 中 |
| LivingShieldBlockEvent / PlayerInteractEvent / PlayerTickEvent.Post / LivingDeathEvent / BlockEvent.Break | 各 1-2 订阅 | 1.20.1 同名或近名事件 | 低-中 |
| RegisterCommandsEvent + FakePlayer | 测试命令 | 1.20.1 同族 API | 低 |
- 附魔: 1.21.1 数据驱动(80 JSON + EnchantmentKeys); 1.20.1 代码注册(Enchantment#Builder 或子类, 89 个效果逻辑复用 common, 注册壳重写)
- mixin: 17 个已注册 mixin 引用 1.21.1 字节码; 1.20.1 需重写 target(类名/方法同 mojmap 但 SRG/中间名不同)
- Apothic Attributes: 1.21.1-2.10.1(NeoForge); 1.20.1 用独立版本(API 字段名大部分同名, 需编译验证)
- 结论: 1.20.1 两平台 = "事件层 + 注册层 + mixin 重写", common 与附魔效果规则可原样复用(阶段二已保证零平台 import)

## 6.7 矩阵现实修订与跨版本层实施(round 2, 2026-09)
- **Apothic Attributes 硬前置实测**(shadowsoffire maven HEAD 探测): 仅两版发布
  `ApothicAttributes-1.20.1-1.3.7`(1.20.1 时代 Forge/NeoForge 统一 API)与 `1.21.1-2.10.1`(NeoForge 专用)。
  → **1.21.1 Forge 无 Apothic**, 4 平台矩阵修订为 **3 平台**: NeoForge 1.21.1 + Forge 1.20.1 + NeoForge 1.20.1(后两者共用 Apothic 1.3.7, 需编译验证其 NeoForge 兼容面)
- **UniMined 1.4.1 探针验证**(临时工程, 已删): NeoForge 1.21.1 与 Forge 1.20.1 环境均能配置+下载工具链;
  需 gradle heap ≥2G(默认 512M 会 OOM); 单 gradle 工程只能一个 MC 版本 → 多版本需多项目结构
- **关键发现: common 并非天然跨版本**(非零平台 import 即够): common 用了 1.21 专属 MC API
  `DataComponents`/`CustomData`(物品 NBT)、`Holder<Attribute>`/`getAttributeValue(Holder)`(属性)、
  `ResourceKey<Enchantment>`(附魔引用)、`AttributeModifier(ResourceLocation,…)`/`removeModifier(RL)` 等
  → 需**版本适配层**(用户已拍板"加适配层, 真正跨版本")
- **已完成(round 2)**:
  1. `UnifiedDamageEngine.settle` 纯函数化: 平台层按各自版本读三通道值传入(attacker 名+amount+bonus+mult+flat),
     引擎本体不再依赖 Holder/LivingEntity → settle 可被 1.20.1 原样复用(compile ✅)
  2. 新增 `common/version/AttributeChannelAccess`(声明 1.20.1 vs 1.21.1 属性 API 差异缝: 读值/取实例/
     makeModifier/removeModifier), 供各平台实现
  3. GitHub 备份: Zhonz/Zhonz-s-More-Enchantments(3 commits, 含本 repo 全量)
- **已完成(round 3-4)**:
  1. 引擎写入辅助全面 access 化: addPercentBonus/setDamageMultiplier/setFlatDamage/事件临时 modifier 经
     AttributeChannelAccess(install 注入, 平台实现), 1.21.1 实现注入于 ModEventHandlers.register()
  2. AttributeChannelAccess 精简为 makeAddModifier + removeModifier 两方法(Operation 枚举差异封装,
     引擎不再引用 1.21 专属 ADD_VALUE; 1.20.1 实现成本最低)
  3. 血路击杀 NBT 读取移出 common: EventDamageContext.BloodPathKills 注入(ctx lambda)
  4. **common 1.20.1 编译实证**: 9 文件在 UniMined Forge 1.20.1 compileJava BUILD SUCCESSFUL
     → 跨版本核心(引擎/settle/判定/ctx/存储)真正达标
  5. **EnchantmentKeys 边界结论**: ResourceKey 静态常量依赖 1.21 fromNamespaceAndPath(静态初始化无法等
     工厂注入 → 鸡生蛋, 已回滚工厂方案); 该文件保持 1.21 专用 —— 1.20.1 附魔为代码注册, 本就用
     各自 DeferredRegister 键源, 不属于可复用 common(架构差异, 与 ENCHANTMENTS.md 一致)
- **已完成(round 5)**:
  1. common 判定与平台键彻底解耦: 新增 common/enchant/EnchantIds(80 个纯字符串附魔 id, 跨版本可编译);
     EnchantmentLevelLookup 改收 String id; EventDamageConditions/TickBonusRules 改引用 EnchantIds;
     1.21 事件层 lookup 实现 id→ResourceKey。common 最后一块平台键依赖移除。
  2. **1.20.1 Forge 平台工程骨架落库**: platforms/1.20.1-forge/(UniMined Forge 47.3.0/MC 1.20.1/Java 17),
     srcDir 共享根 common(排除 EnchantmentKeys); compileJava BUILD SUCCESSFUL
- **已完成(round 6, 1.20.1 Forge 平台壳闭环)**:
  1. 平台壳: ZhonzMoreEnchantments1201(@Mod 入口) + ZhonzAttributes1201(三通道 DeferredRegister
     ForgeRegistries.ATTRIBUTES) + ModEnchantments1201(finale 代码注册示范) + mods.toml
  2. **runServer 'Done (25.990s)'** → 壳在 1.20.1 Forge 完整启动(曾因 run/mods 残留 Apothic jar 的
     attributeslib SRG mixin 失配失败, 清后解决)
  3. **mod jar 产出**: build/libs/*.jar(21KB, mods.toml + common 8 类 + 平台类齐备)
  4. Apothic 1.3.7 dev 运行时注入 = UniMined 未覆盖场景(官方样例均无第三方 mod dev 注入);
     正式联调路径 = 打 jar 放真实 mods 目录(生产等同), 待正式移植时执行
- **1.20.1 NeoForge 平台**: 与 Forge 1.20.1 同步推进(loader 47.1.106 复用 forge API 包名), 平台代码
  全量同步; 效果移植与 Forge 版一致(见下)
- **已完成(round 7-8, 1.20.1 效果批量移植)**:
  1. **89 附魔代码注册**(两平台): supported_items tag→EnchantmentCategory 自动映射
  2. **3 子代理并行移植**(自 1.21 ModEventHandlers, ~107KB):
     - TickEffectsBatch1(19 tick 属性)/ SideEffectsBatch1(20 项攻击副作用+死亡组)/
       NewEnchantsBatch1(73-89 事件+register)
  3. **统一接线**: EnchantWiring1201(PlayerTick→批A + 注册批C); ForgeEventHandler1201.onLivingDamage
     settle 后调批B 攻击副作用
  4. 平台差异适配: attributeslib(1.20.1 包名 dev.shadowsoffire.attributeslib.api.ALObjects,
     RegistryObject.get()); ForgeMod.STEP_HEIGHT_ADDITION/ENTITY_REACH/BLOCK_REACH; UUID modifier;
     compileOnly Apothic 1.3.7(仅编译期)
  5. 产物: forge jar 24→122KB(111 class 含移植类); **compile BUILD SUCCESSFUL(两平台)**
- **TODO(1.20.1 不可表达/待补, 各文件 javadoc)**: divine_curse 冷却翻倍(无 COOLDOWN_REDUCTION);
  my_sea_domain 标记易伤流; explosive_dawn 装填 tick; weeping_child 自定义 damage_type(weeping/frost/true);
  mercy_equal 信标绑定; 雪的伤/unyielding 攻击侧伤害类型转换; 若干 tick 维护(标记过期清理等)
- **已完成(round 9, 16 mixin 移植)**: 2 并行组移植全部 1.21 mixin 到 1.20.1 Forge(forge.mixin 包):
  组1(9): ItemStackDurability(hurtAndBreak Consumer 重载)/BlockBreak/CrossbowCharge(单参 getUseDuration)/
    ProjectileWeaponCooldown/PlayerShieldBlock(disableShield(boolean))/PlayerFoodEffect+ThePure(canBeAffected)/
    DivineCurseBlockBreak/TotemUse; 组2(7): ManifestTotem/HaloSleep/WeepingFire+PlayerWeepingFire
    (1.20.1 hurt 结构同, ON_FIRE 近似)/FireImmunePierce/FireResistancePierce(IS_FIRE ordinal)/
    BeaconMercy(attributeslib 1.3.7 惰性列表, 无 PROJECTILE_DAMAGE)
  删除 MaceItemMixin(1.20.1 无 MaceItem); mixins.json 17→16, JAVA_17; neoforge 平台同步(neoforge1201.mixin)
  compile BUILD SUCCESSFUL(两平台); 注入点经 javap 对照 1.20.1 mojmap jar 核实
- **验证边界(重要)**: 1.20.1 dev 环境 attributeslib(Apothic 1.3.7)无 dev-remap 变体 → 运行时
  mixin 注入验证需真实客户端(生产 mods 双装); dev 冒烟上限 = 壳+效果代码启动(已达成 Done 25.9s)。
  主工程(1.21.1 NeoForge)testall 63/63 全程保持(所有跨版本重构零回归)
- **矩阵最终交付**: NeoForge 1.21.1(89 附魔完整 + testall 63/63)✅ / Forge 1.20.1(89 注册+效果 3 批
  +16 mixin+接线, compile+jar)✅ / NeoForge 1.20.1(同 forge 同步, compile)✅
- **剩余**: 数值验证(人工/生产 jar); mixin 1.20.1 重写; 剩余 tick 维护补全; neoforge jar 冒烟

## 6.8 三平台本地服务端实测(round-verify, 全部通过)
在本地服务端实机运行三个平台并验证核心功能(不破坏项目结构):
| 平台 | 启动 | 附魔注册 | incoming 通道 | 攻击乘伤通道 |
|---|---|---|---|---|
| NeoForge 1.21.1(主工程) | runServer ✅ | 89 附魔(/zhonztest) | ✅ | ✅ testall **63/63** |
| Forge 1.20.1 | `Done (27.1s)` ✅ | ✅ 装备 NBT 确认 | ✅ **打 8 扣 4**(×0.5 减伤) | ✅ **打 8 扣 48**(bone_break ×6) |
| NeoForge 1.20.1 | ✅ | ✅ | ✅ **打 8 扣 4** | ✅ **打 8 扣 48** |

- 关键突破: **modImplementation(Apothic+Placebo)** 让 UniMined remap 第三方 mod 进 dev run classpath,
  解决此前 attributeslib mixin SRG 失配(dev 无法启动)问题 → 1.20.1 可在本地服务端完整实测
- 1.20.1 测试方法(无 /zhonztest): 原版命令 `/item replace` 附魔装备 + `/attribute` 血量 +
  `/damage ... by <attacker>`; tools/rcon.js 增加 `--file` 支持(绕开 shell 吞引号)
- 修复: NeoForge 1.20.1 的 mods.toml 依赖 modId 应为 **"forge"**(47.x 分叉初期沿用 forge id,
  非 "neoforge"), 否则报 "neoforge is not installed"
- 复现命令: 平台目录 `gradlew runServer`(需 run/server/eula.txt + server.properties enable-rcon=true)

## 五、风险与缓解
- 附魔 JSON 无法跨版本 → 1.20.1 代码注册需重写,工作量≈新实现;建议按"核心 89 个机制清单"驱动逐条移植,并复用 ENCHANTMENTS.md。
- mixin 字节码目标差异 → 双 mixin 组 + 单测逐版本跑。
- 统一测试 → 1.21.1 runServer + /zhonztest testall 63/63 作为回归门;1.20.1 侧建等价测试命令。
- Apothic 平台缺口 → 1.21.1 Forge 平台不可行(无前置), 已从矩阵移除; 1.20.1 NeoForge 需验证 Apothic 1.3.7 的 NeoForge 兼容面
