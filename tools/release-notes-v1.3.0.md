## v1.3.0

v1.2.0 之后的全部改动：**「于此显圣」扩展到全部免死路径** + 版本号与产物名对齐。

### 新功能

- **「于此显圣」现在覆盖 4 条免死路径**(此前只有原版图腾被消耗时才触发):
  1. 原版不死图腾被消耗(主手/副手)
  2. 智能图腾(从背包消耗带该附魔的图腾)
  3. 自地狱中归来(免死时判定主手/副手是否持有该附魔图腾)
  4. 神护(同上)
  - 抽出共享触发逻辑 `ManifestHelper`(1.21.1)/ `ManifestHelper1201`(1.20.1 双平台)
  - 触发效果不变: 周围 16 格内生物定身 3 秒 + 自身抗性提升 V 30 秒
  - 边界: 智能图腾分支仅对 `Player` 生效(僵尸无背包)

### 新增验证命令

- `/zhonztest manifesttest` —— 用真实生物走完整死亡管线, 断言「存活 + 自身抗性提升 V(amplifier 4) + 旁 2 格僵尸定身(移动缓慢 XI)」,
  另含两项反例(普通图腾 / 无图腾), 确保「必须带该附魔」这一条件真的被判定

### 版本号

- `mod_version` 1.0.0 → **1.3.0**(主工程 + 1.20.1 双平台), 修正此前产物名带 `-1.0.0` 而 Release tag 为 `v1.2.0` 的不一致

### 产物

| 平台 | 文件 |
|---|---|
| NeoForge 1.21.1 | `zhonz_more_enchantments-1.3.0.jar` |
| Forge 1.20.1 | `zhonz-more-enchantments-1.20.1-forge-1.3.0.jar` |
| NeoForge 1.20.1 | `zhonz-more-enchantments-1.20.1-neoforge-1.3.0.jar` |

### 前置

- **必需**: Apothic Attributes(1.21.1 → 2.10.1 / 1.20.1 → 1.3.7) + Placebo

### 验证结果

| 检查项 | 结果 |
|---|---|
| `zhonztest manifesttest` | **5/5**(原版图腾 / 自地狱中归来 / 神护 实测通过; 智能图腾 SKIP 需真实玩家; 两项反例正确) |
| `zhonztest testall` | **63/63**, failed=0 |
| 三平台构建 | 全部 BUILD SUCCESSFUL |
| 异常 / 标签警告 | 无 |

### 测试口径提示(给后续维护者)

- `LivingEntity.kill()` 的伤害带 `BYPASSES_INVULNERABILITY` 标签, `checkTotemDeathProtection` 对该类伤害直接返回 false
  (原版语义: 图腾挡不住 `/kill`)——用 `kill()` 测图腾免死会得到假失败, 必须用 `damageSources().generic()`。
- `FakePlayer` 完全不吃伤害(`hurt()` 直接返回 false), 死亡管线测试必须用真实生物。
