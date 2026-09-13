# Zhonz's More Enchantments — 会话状态(压缩上下文)

> 用途:长会话压缩参考。新会话/子代理先读此文件再动手。

## 最新状态(2026-09-13, round-ui:显示问题修复 + v1.3.2 发布 + 仓库编码事故)

### ⚠️ 事故: GitHub 仓库曾被整体存成 UTF-16(已修复 + 已加门禁)
- **现象**: `main` 整条分支 25 个文本文件全部是 **UTF-16LE**(`FF FE` BOM + 大量 NUL 字节),
  git 因此把每个文件当**二进制** → diff/blame 失效、正常 checkout 得到乱码、25 个文件全部无法合并。
- **来源**: 污染提交(`092dea9` / `5af8f67` / `140fe90`)的 SHA **在本地并不存在** ——
  即不是本工作区提交的, 无法定位写入者。判断为外部/CI(极可能是 Windows PowerShell 5.1 的
  `>` 重定向或 `Set-Content` 默认 UTF-16 所致)。用户亦表示"云端我不知道"。
- **修复**: 逐项核对远端无本地缺失文件后, 用 `--force-with-lease`(带期望远端 SHA, 远端若有他人
  新提交会拒绝)推入干净 UTF-8 历史; 损坏态保留为本地 tag `backup-remote-broken-utf16` 以便回查。
- **防复发**: 新增 **`tools/check-remote-encoding.ps1`**(扫描 ref 全部文本文件, 有 NUL/UTF-16 BOM
  即失败并列出文件), 已接入 `release-v*.ps1`: **push 后立即校验, 不通过即中止发布**。
  实测: 干净态 282 文件全过; 损坏态精确报出 25 个文件。
- **教训**: 任何写仓库内容的脚本/工具一律显式无 BOM UTF-8(`UTF8Encoding($false)` 或 `-Encoding utf8`);
  发布后必须跑一次编码门禁。

### 推送经验(下次直接用)
- `github.com` 的 DNS 解析到 `20.205.243.166` 常被重置 → 用项目自带 CONNECT 代理绕:
  `node tools/github_proxy.js <可用IP> <端口>` 然后
  `git -c http.proxy=http://127.0.0.1:<端口> -c http.sslBackend=openssl -c http.version=HTTP/1.1 push`
  —— **必须同时加 openssl 后端与 HTTP/1.1**, 否则 schannel 报 "server closed abruptly"。
- 可用 IP 会漂移, push 常需**多次重试**; 可用 `curl --resolve github.com:443:<ip>` 先探活。
- `api.github.com` 一般可直连(发布 Release / 上传资产用 curl + token, 不走代理)。

### v1.3.2 已发布
`https://github.com/Zhonz/Zhonz-s-More-Enchantments/releases/tag/v1.3.2`(3 个平台 jar 齐全)
- 提交 `98c727d`(63 文件: 敷衍按诅咒计数 + 诅咒红色 + 附魔名颜色 + 受伤倍率常驻 + 版本号 1.3.2)
- 提交 `8bdafbe`(编码门禁); 远端 main HEAD = `8bdafbe`, 与本地一致

## 上一状态(2026-09-13, round-ui:用户反馈的三个显示问题)

### ⚠️ 先记一次误判(避免后续再犯)
用户说"**增伤、乘伤应该出现但没出现**", 我**理解反了**, 把三个通道属性改成 `setSyncable(false)`
(等于彻底隐藏)。实为"该出现却没出现"。**已全部回退**(三个 `ZhonzAttributes*` 文件与 HEAD 一致,
均 `setSyncable(true)`)。教训: 此类"该不该显示"的反馈必须先确认方向再动手。

### ✅ 增伤 / 乘伤数值常驻(用户需求: 在属性面板能看到实际数值)
- **根因(已定位并修)**: 1.20.1 平台在 `onLivingDamage` 尾部把 `incoming_tick_aggregate`
  **重置为 1** → 两次受击之间读回 1, 即"数值只在伤害瞬间存在"。
  现**只清事件临时聚合** `incoming_event_mult`, tick 常驻聚合保持常驻(与 1.21.1 主工程一致)。
- **本就常驻、无需改的**:
  - `bonus_damage`: 各附魔**独立 modifier id**(`bonus_supreme_art`/`bonus_new_sun`/
    `bonus_cornered_beast`/`bonus_rapid_ascent`/`bonus_sorrowful_red`)在 PlayerTick 每 tick 覆盖写入
  - `damage_multiplier`: `refreshDamageMultiplierAggregate` 每 tick 写单一聚合 `unified_mult_aggregate`
  - 三个通道属性保持 `setSyncable(true)`(否则面板根本不列)
- **设计边界(不修复)**: 事件**条件型**加伤/乘伤(泰坦打精英 ×2、破军目标低血 +30% 等)依赖目标状态,
  只在命中瞬间以临时 modifier 计入并立即清除 → **不会**常驻显示; 面板反映的是"自身状态可判定"的常驻部分。

### ✅ 附魔名紫色斜体误加(已修)
lang 里 `§d§o` 被贴到 8 个附魔名上, 按设计**只有 #2「将我抹去, 将你也抹去」(`erase_me_erase_you`)** 该有
(README/ENCHANTMENTS 明写)。已从 #45 顶点 / #50 新太阳 / #55 于此显圣 / #57 悲伤的红 / #59 永劫回归 /
#60 奢侈的希望 / #75 唯有命运(共 7 个)移除; **保留末尾 `§r` 复位码**(防名字样式渗入后续描述行);
en_us 侧移除 #75 的同类问题, 并给 en_us 的 #2 补上紫色斜体(原缺失)。

### ✅ 诅咒附魔无法显示红色(已修)
根因: **1.21 的诅咒红色不来自 `is_curse` 字段**(该版 `EnchantmentDefinition` 无此字段), 而是
`Enchantment.getFullname` 里的 `holder.is(EnchantmentTags.CURSE)` → `ChatFormatting.RED`, 否则 GRAY
(经字节码核实)。项目原先**没有任何 curse 标签** → 三个诅咒附魔一直显示为灰。
- 主工程: 新增 `data/minecraft/tags/enchantment/curse.json`
- 1.20.1 双平台: 该版 `getFullname` 按 `Enchantment.isCurse()` 选 RED/GRAY → 新增 `CurseEnchantment`
  基类覆写 `isCurse()`, 三个附魔改用它注册
- 覆盖 `divine_curse`(神咒) / `self_bound`(自缚者) / `perfunctory`(敷衍 —— 文档稀有度即"诅咒附魔")

### ✅ 「敷衍」改为按诅咒计数(用户确认后实施, v1.3.2)
- **问题**: 文档为"背包内每有一个物品带此**诅咒**, 移速/攻速/挖掘/蓄力 -20%", 但三平台实现
  都在数**带敷衍自己**的物品数(`EnchantIds.PERFUNCTORY`) —— 与文档不符。
- **修复**: 改为统计"背包内**带诅咒附魔的物品件数**", **每件物品最多计 1 次**。
  诅咒清单 = `divine_curse` / `self_bound` / `perfunctory`(与 `tags/enchantment/curse.json` 一致)。
  - 1.21.1 主工程: `zhonz$hasCurseEnchant(ItemStack)`(经 `ModEnchantments.getHolder` 查等级)
  - 1.20.1 双平台: `TickEffectsBatch1.CURSE_ENCHANTS` 清单 + 现有 `hasEnchant(stack, id)`
- **版本号**: 1.3.1 → **1.3.2**(`gradle.properties` + 两平台 `build.gradle`)

### ⚠️ 待用户确认(已发现, 未改)
「敷衍」效果实现与文档不符: 文档"背包内每有一个物品带此**诅咒**, 移速/攻速/挖掘/蓄力 -20%",
实现却是数**带敷衍自己**的物品 —— **已于 v1.3.2 按文档修正**(见上)。

### 验证
**三平台 build ✅**; runServer **testall 65/65**、**incomingtest 4/4**(证明受击侧聚合改常驻未影响结算)。

## 上一状态(2026-09-12 深夜, round-close:未完成项收口 + 文档校正)
- ✅ **1.20.1 双平台交出 4 处真实功能缺口**(此前只存在于注释里的 TODO, 逐项核对后发现是真缺陷)
  1+2. **节奏(rhythm)写侧缺失** —— 1.20.1 只有读侧 `computeConditionalMultiplier`, 没有任何地方写
     `zhonz_rhythm_hit_attack` 标志 → **该附魔 ×1.5 永不生效**。现于 `ForgeEventHandler1201.onLivingDamage`
     内、`computeConditionalMultiplier` **之前**调用新增的 `AttackSideBatch1.applyRhythm`(与 1.21 同序:
     1.21 在 applyAttackerEnchantments L701 写标志、同事件 L508 读)。
  3. **爆裂黎明装填无敌缺失** —— `applyExplosiveDawn` 写了 `KEY_EXPLOSIVE_DAWN_RELOADING` 但**没人消费**
     (标志永久残留)。现补 `TickSideBatch1.tickExplosiveDawn`(装填期抗性提升 V, 未装填则清标志)。
  4. **不完整的预知眼(#33)整条未移植** —— 现补 `tryForeknowledgeDodge` + `tickIncompleteForeknowledge`:
     受击闪避 + 兜底分支(1.20.1 用 `dev.shadowsoffire.attributeslib.impl.AttributeEvents.isDodging`)
     + 脱战概率恢复 + 同步 Apothic `DODGE_CHANCE` + 低概率反胃。
- ✅ **自定义伤害类型三条转换全部落地**(此前被判定"1.20.1 不可表达", 实为可代码注册)
  - 新增 `DamageTypes1201`(DeferredRegister DAMAGE_TYPE)+ 同名 JSON 描述
    (`resources/data/zhonz_more_enchantments/damage_type/{weeping_fire,frost,true_damage}.json`)
    + 标签 `resources/data/minecraft/tags/damage_type/`(true_damage→bypasses_armor/enchantments/
    resistance/effects; frost→is_freezing; weeping_fire→is_fire)
  - `WeepingFireMixin` / `PlayerWeepingFireMixin` 重写为 1.21 等价的三路判定与倍率:
    哭泣之子→weeping_fire ×1、**雪的伤→frost ×1**、**唯有命运→true_damage ×6**(优先级同 1.21)
  - 新增 mixin **`ThrownTridentAccessor`**(`@Accessor("tridentItem")`)补上"投掷三叉戟自身附魔"分支
    —— 1.20.1 只有 protected `getPickupItem()`, 1.21 是 public `getWeaponItem()`
- ✅ **`/zhonztest incomingtest`(1.21.1 主工程, 新增)** —— 补齐受击侧 `incoming_damage` 数值回归
  (testall 全为攻击侧)。**实测 4/4 通过**:
  | 用例 | 条件 | 实测 |
  |---|---|---|
  | baseline | 无附魔·满血 | 4/4 伤害,incoming=**1.0** ✅ |
  | luxurious_full | 奢侈的希望·满血 | 6/4,incoming=**1.5** ✅ |
  | cornered_low | 困兽之斗·生命≤25% | 2/4,incoming=**0.5** ✅ |
  | product_low | 困兽(×0.5)+极速攀升(y=-5 → ×0.95) | 1.9/4,incoming=**0.475** ✅ 严格乘积(0.5×0.95), 非加和 |
  - 实现要点: 真实僵尸(FakePlayer 不吃 hurt)、伤害基准取目标最大生命的 20%(自适应, 保证受击后存活)、
    低血用例起始生命同为 20%(≤25% 满足困兽条件)、断言"设定生命−剩余生命 == 期望"
- ✅ **回归全绿(本轮实测)**: testall **65/65**、settest **4/4**、manifesttest **5/5**、incomingtest **4/4**
- ✅ **注释与文档校正**: 1.20.1 两平台 71 处 TODO 逐条核对, 其中"tick 侧未接线/待 PlayerTick 批补齐/
  未接线:本类尚未被任何事件订阅调用"等**大段已过时注释**(这些早已落地并接线)已同步为实际状态;
  剩余 TODO 降为"平台不可表达(保留差异)"明确标注(仅 COOLDOWN_REDUCTION / PROJECTILE_DAMAGE 两项)
- 验证: **三平台 compile ✅**(main / forge / neoforge) + 主工程 runServer 四项测试全绿
- 版本号仍 1.3.1(本轮为缺陷修复 + 移植补齐, 未发新版; 如需发布建议递增到 1.3.2)
  - ⚠️ **注意版本漂移**: GitHub Release **v1.3.1 发布于 2026-09-12T14:41Z**(即本轮之前),
    **不含本轮任何修复**; 本地重建的 jar 仍标 1.3.1 但内容已不同 → 若要让用户拿到本轮修复,
    必须**递增版本号(建议 1.3.2)并重新发布**, 切勿直接覆盖 v1.3.1 的资产
- 产物(本地已重建, 均含本轮新增类与资源):
  | 平台 | jar | 大小 |
  |---|---|---|
  | NeoForge 1.21.1 | `build/libs/zhonz_more_enchantments-1.3.1.jar` | 197.7 KB |
  | Forge 1.20.1 | `platforms/1.20.1-forge/build/libs/zhonz-more-enchantments-1.20.1-forge-1.3.1.jar` | 187.7 KB |
  | NeoForge 1.20.1 | `platforms/1.20.1-neoforge/build/libs/zhonz-more-enchantments-1.20.1-neoforge-1.3.1.jar` | 189.9 KB |

## 上一状态(2026-09-12 晚二轮)
- ✅ **新增附魔 90/91 + 暴击五件套口径重写**(用户口径: 六槽位任意"其他"一件)
  - 90 热烈诚挚希望 `fervent_sincere_hope` / 91 自私澄澈天光 `selfish_clear_sky`(胸甲, 宝藏)
  - 65/66/67 加强档判定从"同时附魔另两件"改为 `EnchantSetPieces.hasOtherPiece`(头盔/胸甲/护腿/靴子/主手/副手,
    排除自身 → 任意其他一件); common 新类 `common/damage/EnchantSetPieces.java`(平台经 `EnchantmentLookup1201` 接)
  - 90: `LivingHealEvent` 溢出治疗→吸收值, **必须用 `MobEffects.ABSORPTION` 承载**(原版 tick 无该效果会把吸收值清零;
    原版不死图腾同样如此); 上限 = 最大生命 100%, 六槽位有其他套装附魔时取消
  - 91: `HEALING_RECEIVED`(ADD_MULTIPLIED_BASE)= 暴击率×暴击伤害(套装时另乘增伤; Apothic heal 按该属性缩放治疗)
  - 验证: **testall 65/65**、**settest 4/4**、**manifesttest 5/5**
  - 三平台同步: 1.20.1 双平台已注册 90/91 并同步 crit 口径 + 溢出治疗(同 `SideEffectsBatch1`/`TickEffectsBatch1`)
- ✅ **修复线上崩溃(1.3.0 缺陷)**: 投掷重锤 `thrown_mace` 无客户端渲染器 →
  `EntityRenderDispatcher.shouldRender` NPE(右键松手投掷即崩)。修复: `ThrownMaceEntity implements ItemSupplier`
  + `ZhonzMoreEnchantments.ClientRenderers` 注册 `ThrownItemRenderer`
  - ⚠️ 客户端注册**必须加 dist 守卫**(`FMLEnvironment.dist.isClient()`): 否则专用服务端解析方法引用会加载
    `net.minecraft.client.*` → `RuntimeDistCleaner` 直接判定 mod 加载失败。已实测服务端可正常启动。
  - 加了启动自检 `[RenderCheck] thrown_mace renderer OK: ThrownItemRenderer`(客户端进世界后打日志)
- 版本号 1.3.1; 发布前必须跑三平台 build + 回归

## 最新状态(2026-09-12 晚,上一轮)
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
- 测试命令: `/zhonztest testall`(攻击侧回归)、`/zhonztest manifesttest`(5 项免死/显圣路径)、
  `/zhonztest settest`(90/91 套装)、**`/zhonztest incomingtest`(受击侧 incoming_damage 4 用例, round-close 新增)**、
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
  → **已补(round-close)**: `/zhonztest incomingtest` 4 用例覆盖受击侧 incoming_damage 通道

## 版本差异要点(迁移到 1.20.1 Forge 时注意)
- 1.20.1 Forge:无 LivingIncomingDamageEvent/DamageContainer(1.21 NeoForge 伤害管线大改),附魔为代码注册非 1.21 数据驱动;1.21.1 依赖 Mojang mapped 方法签名,mixin 目标随版本不同
- Apothic Attributes 1.20.1 Forge 存在(独立版本),但 API/事件内部不同,需逐项核对
