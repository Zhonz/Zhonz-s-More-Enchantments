# 1.20.1 Forge 效果移植契约(子代理移植指南)

目标: 把根工程(1.21.1 NeoForge)ModEventHandlers.java 中的附魔效果逻辑, 移植为
platforms/1.20.1-forge 的 ForgeEventHandler1201.java 中的方法, 编译通过即可(数值验证留人工)。

## 源文件
- 根工程: src/main/java/com/zhonz/moreenchantments/event/ModEventHandlers.java(约 3000 行, 1.21 API)
- 目标: platforms/1.20.1-forge/src/main/java/com/zhonz/moreenchantments/forge/ForgeEventHandler1201.java

## 已完成的基础设施(移植时直接使用, 勿重建)
- `EnchantmentLookup1201.INSTANCE`(EnchantmentLevelLookup): id 字符串("liberator"等)查等级
  - mainHand(entity, id) / anySlot(entity, id) / slot(entity, id, slot)
- `ZhonzAttributes1201.BONUS_DAMAGE/.DAMAGE_MULTIPLIER/.FLAT_DAMAGE`(RegistryObject<Attribute>)
  - 读值: entity.getAttributeValue(attr.get())
  - 实例: entity.getAttribute(attr.get())
- `UnifiedDamageEngine`(common): settle / addPercentBonus(inst,id,percent) / setDamageMultiplier /
  setFlatDamage / apply/clearEventMultiplierTemporary / apply/clearEventBonusTemporary
- `TickBonusRules`(common): supremeArt/newSun/corneredBeast/rapidAscent/sorrowfulRed(lookup, entity)
- `EventDamageConditions`(common): computeConditionalMultiplier / computeBonusPercent(ctx,...) /
  tickMultiplierProduct(ctx,...)
- 属性通道写入已由 AttributeAccess1201.install() 注入(内部用 1.20.1 UUID modifier)
- EntityDataStorage(common): getData(entity)/getEntityData(entity)(Player 用 persistent data)
- ctx 示例见 ForgeEventHandler1201.CTX

## 1.21 → 1.20.1 API 映射
| 1.21 | 1.20.1 |
|---|---|
| ModEnchantments.X(ResourceKey) | EnchantmentLookup1201.INSTANCE.mainHand/anySlot/slot(entity,"x") 或需检查时用 >=1 |
| getMainHandEnchantmentLevel(e,X) | EnchantmentLookup1201.INSTANCE.mainHand(e,"x") |
| getSlotEnchantmentLevel(e,X,slot) | EnchantmentLookup1201.INSTANCE.slot(e,"x",slot) |
| getEnchantmentLevel(e,X)(全槽) | EnchantmentLookup1201.INSTANCE.anySlot(e,"x") |
| itemStack.getEnchantmentLevel(holder) | itemStack.getEnchantmentLevel(ench) 其中 ench=ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation(MODID,id)) |
| setTransient(e, attr, id, val, op) | 属性 modifier 经 AttributeInstance.removeModifier(按 UUID)+ addTransientModifier; 本模组加伤通道用 UnifiedDamageEngine |
| Attributes.XXX(1.21 Holder) | entity.getAttribute(Attributes.XXX)(1.20.1 直接 Attribute) |
| EquipmentSlot 同 | 同 |
| CompoundTag 同 | 同(1.20.1 NBT 同 API) |
| MobEffectInstance/MobEffects 同 | 同 |
| setRemainingFireTicks/isOnFire 同 | 同 |
| ALObjects.Attributes(仅 Apothic) | 同字段名可用(依赖 Apothic 1.20.1) |
| DamageSource 同 | 同 |

## 附魔 id 列表
见 src/main/java/com/zhonz/moreenchantments/common/enchant/EnchantIds.java(80+ 个 String 常量)

## 移植方法
1. 从根工程读 1.21 实现(函数体)
2. 按上表映射改写为 1.20.1 API
3. 加入 ForgeEventHandler1201(public static, 由事件订阅调用; 或私有被现有订阅调用)
4. 如某效果需 1.21 专属事件(LivingIncomingDamageEvent 等 1.20.1 没有)→ 适配为 1.20.1 等价
   (LivingHurtEvent/LivingDamageEvent) 或标记 TODO 跳过(记录在注释)
5. 事件注册: 游戏事件在 ZhonzMoreEnchantments1201 构造器 MinecraftForge.EVENT_BUS.register
   (已在)。新增订阅方法需 @SubscribeEvent
6. 编译验证: cd platforms/1.20.1-forge && gradlew compileJava(GRADLE_USER_HOME=项目/.gradle_home)
