package com.zhonz.moreenchantments.command.test;

import com.zhonz.moreenchantments.common.CommonConstants;
import com.zhonz.moreenchantments.enchantment.ModEnchantments;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageEffects;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.List;

/**
 * 自定义伤害类型测试(round-testkit)。
 *
 * <p>针对的是一类"看不见的坏掉": 附魔把攻击转成自定义伤害类型
 * ({@code weeping_fire} / {@code frost} / {@code true_damage}), 而伤害类型由
 * <b>数据包 JSON</b> 定义。只要 JSON 缺失、键写错、或 `effects`/标签与预期不符,
 * 就会出现"技能说造成了火焰伤害, 实际只是一个普通伤害"——
 * 数值上甚至看不出来, 只有副作用(点燃 / 冻伤 / 无视护甲)会消失。
 *
 * <p>本套件用三层证据把这条链钉死:
 * <ol>
 *   <li><b>注册与定义</b>: 三个伤害类型能在注册表解析, 且 msgId / effects / scaling 正确;</li>
 *   <li><b>伤害来源</b>: 按生产同款方式构造的 DamageSource 必须带攻击者归属
 *       ({@code getEntity()/getDirectEntity()}), 死亡消息必须能本地化且用得到攻击者名;</li>
 *   <li><b>实打</b>: 真攻击真目标, 用<b>只有该伤害类型才会产生的副作用</b>反证类型确实生效 ——
 *       哭泣之火 → 目标着火; 冰霜 → 目标进入冻结; 真伤 → 无视护甲且 ×6。</li>
 * </ol>
 */
public final class DamageTypeSuites {

    private DamageTypeSuites() {
    }

    public static final String SUITE = "damage-type";

    private static final String WEEPING_FIRE = "weeping_fire";
    private static final String FROST = "frost";
    private static final String TRUE_DAMAGE = "true_damage";

    public static List<TestKit.Suite> all(ServerLevel level) {
        TestKit.Suite suite = new TestKit.Suite(SUITE,
                "自定义伤害类型: 注册/定义、伤害来源归属与死亡消息、以及三种转换的实打副作用");

        // ==================================================================
        // 第一层: 注册与定义
        // ==================================================================

        suite.addWorld("all_three_types_registered",
                "三个自定义伤害类型必须都能在运行时注册表解析出 Holder(缺失则 getHolderOrThrow 直接抛异常, 附魔静默失效)",
                "weeping_fire / frost / true_damage 全部 present",
                c -> {
                    Registry<DamageType> registry = registry(level);
                    for (String id : List.of(WEEPING_FIRE, FROST, TRUE_DAMAGE)) {
                        c.that(registry.getHolder(key(id)).isPresent(), "注册表含 " + id);
                    }
                });

        suite.addWorld("definition_matches_intent",
                "伤害类型的定义必须与设计一致: 哭泣之火=点燃(burning)、冰霜=冻结(freezing)、真伤=普通(hurt)且不随难度缩放",
                "weeping_fire.effects=BURNING, frost.effects=FREEZING, true_damage.effects=HURT 且 scaling=NEVER",
                c -> {
                    Registry<DamageType> registry = registry(level);
                    DamageType fire = type(registry, WEEPING_FIRE);
                    DamageType frost = type(registry, FROST);
                    DamageType trueDamage = type(registry, TRUE_DAMAGE);

                    c.notNull("weeping_fire 已定义", fire);
                    c.notNull("frost 已定义", frost);
                    c.notNull("true_damage 已定义", trueDamage);

                    if (fire != null) {
                        c.note("weeping_fire: msgId=" + fire.msgId() + " effects=" + fire.effects()
                                + " scaling=" + fire.scaling());
                        c.eq("weeping_fire.message_id", fire.msgId(), WEEPING_FIRE);
                        c.eq("weeping_fire.effects", fire.effects(), DamageEffects.BURNING);
                    }
                    if (frost != null) {
                        c.note("frost: msgId=" + frost.msgId() + " effects=" + frost.effects()
                                + " scaling=" + frost.scaling());
                        c.eq("frost.message_id", frost.msgId(), FROST);
                        c.eq("frost.effects", frost.effects(), DamageEffects.FREEZING);
                    }
                    if (trueDamage != null) {
                        c.note("true_damage: msgId=" + trueDamage.msgId() + " effects=" + trueDamage.effects()
                                + " scaling=" + trueDamage.scaling());
                        c.eq("true_damage.effects", trueDamage.effects(), DamageEffects.HURT);
                        c.eq("true_damage.scaling", trueDamage.scaling(), DamageScaling.NEVER);
                    }
                });

        suite.addWorld("tags_give_expected_semantics",
                "伤害类型标签必须成立: 哭泣之火属于 is_fire、冰霜属于 is_freezing、真伤四类 bypasses 全中",
                "is_fire / is_freezing / bypasses_armor+enchantments+resistance+effects",
                c -> {
                    Registry<DamageType> registry = registry(level);
                    DamageSource fire = source(registry, WEEPING_FIRE);
                    DamageSource frost = source(registry, FROST);
                    DamageSource trueDamage = source(registry, TRUE_DAMAGE);
                    if (fire == null || frost == null || trueDamage == null) {
                        c.skip("伤害类型未注册, 无法构造 DamageSource");
                        return;
                    }
                    c.that(fire.is(DamageTypeTags.IS_FIRE), "哭泣之火 ∈ is_fire");
                    c.that(frost.is(DamageTypeTags.IS_FREEZING), "冰霜 ∈ is_freezing");
                    c.that(trueDamage.is(DamageTypeTags.BYPASSES_ARMOR), "真伤 ∈ bypasses_armor");
                    c.that(trueDamage.is(DamageTypeTags.BYPASSES_ENCHANTMENTS), "真伤 ∈ bypasses_enchantments");
                    c.that(trueDamage.is(DamageTypeTags.BYPASSES_RESISTANCE), "真伤 ∈ bypasses_resistance");
                    c.that(trueDamage.is(DamageTypeTags.BYPASSES_EFFECTS), "真伤 ∈ bypasses_effects");
                });

        // ==================================================================
        // 第二层: 伤害来源(归属 + 死亡消息)
        // ==================================================================

        suite.addWorld("converted_source_carries_attacker",
                "转换出的 DamageSource 必须带攻击者归属: getEntity()/getDirectEntity() 都要指向攻击者, 否则死亡消息失去来源、部分联动判定失效",
                "getEntity()==attacker 且 getDirectEntity()==attacker",
                c -> {
                    Registry<DamageType> registry = registry(level);
                    try (TestWorld world = new TestWorld(level, anchor(level))) {
                        Zombie attacker = world.zombie();
                        for (String id : List.of(WEEPING_FIRE, FROST, TRUE_DAMAGE)) {
                            Holder.Reference<DamageType> holder = registry.getHolder(key(id)).orElse(null);
                            if (holder == null) {
                                c.that(false, id + " 未注册, 无法构造来源");
                                continue;
                            }
                            // 与 WeepingFireHelper:64 同款构造方式
                            DamageSource converted = new DamageSource(holder, attacker, attacker);
                            c.that(converted.getEntity() == attacker, id + " 的 getEntity() 是攻击者");
                            c.that(converted.getDirectEntity() == attacker, id + " 的 getDirectEntity() 是攻击者");
                        }
                    }
                });

        suite.addWorld("death_message_shows_the_killer",
                "转换伤害的死亡消息必须能显示攻击者名字。原版规则: 伤害\"带攻击者\"时用基础键 death.attack.<msgId> 并把 [受害者, 攻击者] 作为参数传进来(见原版 death.attack.mob=\"%1$s was slain by %2$s\");只有\"无实体但有击杀记录\"时才用 .player 键。因此<b>基础键文案必须含 %2$s</b>, 否则击杀者名被静默丢弃 —— 这就是\"火焰伤害没有伤害来源\"的成因。",
                "键 = death.attack.<msgId>; 参数含攻击者名; zh_cn/en_us 的基础键与 .player 键文案都含 %2$s",
                c -> {
                    Registry<DamageType> registry = registry(level);
                    try (TestWorld world = new TestWorld(level, anchor(level))) {
                        Zombie attacker = world.zombie();
                        Cow victim = world.cow(20.0D);
                        attacker.setCustomName(net.minecraft.network.chat.Component.literal("测试攻击者"));
                        for (String id : List.of(WEEPING_FIRE, FROST, TRUE_DAMAGE)) {
                            Holder.Reference<DamageType> holder = registry.getHolder(key(id)).orElse(null);
                            if (holder == null) {
                                continue;
                            }
                            DamageSource converted = new DamageSource(holder, attacker, attacker);
                            net.minecraft.network.chat.Component message =
                                    converted.getLocalizedDeathMessage(victim);
                            String baseKey = "death.attack." + holder.value().msgId();
                            String playerKey = baseKey + ".player";

                            c.that(message.getContents()
                                            instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                                            && baseKey.equals(tc.getKey()),
                                    id + " 带攻击者时必须用基础消息键",
                                    "期望 " + baseKey + ", 实际 " + message.getContents());

                            if (message.getContents()
                                    instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
                                boolean hasAttackerName = java.util.Arrays.stream(tc.getArgs())
                                        .anyMatch(arg -> String.valueOf(arg).contains("测试攻击者"));
                                c.that(hasAttackerName, id + " 死亡消息参数必须含攻击者名",
                                        "参数: " + java.util.Arrays.toString(tc.getArgs()));
                            }

                            // 关键回归断言: 基础键文案必须能渲染出 %2$s, 否则击杀者不显示
                            for (String lang : List.of("zh_cn", "en_us")) {
                                String template = langValue("lang/" + lang + ".json", baseKey);
                                c.that(template != null && template.contains("%2$s"),
                                        lang + " 的 " + baseKey + " 必须含 %2$s(否则击杀者不显示)",
                                        "实测文案: " + template);
                                String playerTemplate = langValue("lang/" + lang + ".json", playerKey);
                                c.that(playerTemplate != null && playerTemplate.contains("%2$s"),
                                        lang + " 的 " + playerKey + " 必须含 %2$s",
                                        "实测文案: " + playerTemplate);
                            }
                        }
                    }
                });

        suite.addWorld("death_message_item_variant_is_localized",
                "改过名的武器击杀时必须能正常显示死亡消息。原版 DamageSource.getLocalizedDeathMessage 在"
                        + "\"攻击者是活体、且其主手物品带 CUSTOM_NAME(=改过名/铁砧重命名)\"时会改用 <基础键>.item,"
                        + "并传三个参数 [受害者, 攻击者, 物品名](参见原版 death.attack.mob.item)。"
                        + "只要 zh_cn/en_us 里缺这个 .item 键, 客户端就只能显示未翻译的键名 —— 玩家看到的就是\"没有伤害来源\"。",
                "键 = death.attack.<msgId>.item; 参数含攻击者名与物品名; zh_cn/en_us 的 .item 文案必须同时含 %2$s 与 %3$s",
                c -> {
                    Registry<DamageType> registry = registry(level);
                    try (TestWorld world = new TestWorld(level, anchor(level))) {
                        Zombie attacker = world.zombie();
                        Cow victim = world.cow(20.0D);
                        attacker.setCustomName(net.minecraft.network.chat.Component.literal("测试攻击者"));
                        ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
                        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                                net.minecraft.network.chat.Component.literal("测试之剑"));
                        attacker.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, named);
                        for (String id : List.of(WEEPING_FIRE, FROST, TRUE_DAMAGE)) {
                            Holder.Reference<DamageType> holder = registry.getHolder(key(id)).orElse(null);
                            if (holder == null) {
                                continue;
                            }
                            DamageSource converted = new DamageSource(holder, attacker, attacker);
                            net.minecraft.network.chat.Component message =
                                    converted.getLocalizedDeathMessage(victim);
                            String itemKey = "death.attack." + holder.value().msgId() + ".item";

                            c.that(message.getContents()
                                            instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                                            && itemKey.equals(tc.getKey()),
                                    id + " 主手物品改过名时必须用 .item 消息键",
                                    "期望 " + itemKey + ", 实际 " + message.getContents());

                            if (message.getContents()
                                    instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
                                String args = java.util.Arrays.toString(tc.getArgs());
                                c.that(args.contains("测试攻击者"), id + " .item 死亡消息参数必须含攻击者名",
                                        "参数: " + args);
                                c.that(args.contains("测试之剑"), id + " .item 死亡消息参数必须含物品名",
                                        "参数: " + args);
                            }

                            for (String lang : List.of("zh_cn", "en_us")) {
                                String template = langValue("lang/" + lang + ".json", itemKey);
                                c.that(template != null && template.contains("%2$s") && template.contains("%3$s"),
                                        lang + " 的 " + itemKey + " 必须同时含 %2$s 与 %3$s(否则显示原始键/丢来源)",
                                        "实测文案: " + template);
                            }
                        }
                    }
                });

        // ==================================================================
        // 第三层: 实打 —— 用"只有该类型才有的副作用"反证
        // ==================================================================

        suite.addWorld("weeping_fire_direct_hit_burns_by_itself",
                "隔离验证: 先用**原版 hot_floor**(岩浆, 同样 effects=burning)判定\"1.21.1 到底会不会在伤害里执行 effects\"; 会的话, 直接施加 weeping_fire 也必须点燃目标",
                "原版 hot_floor 会点燃的前提下, 直接施加 weeping_fire → getRemainingFireTicks() > 0",
                c -> {
                    Registry<DamageType> registry = registry(level);
                    Holder.Reference<DamageType> fire = registry.getHolder(key(WEEPING_FIRE)).orElse(null);
                    if (fire == null) {
                        c.skip("weeping_fire 未注册");
                        return;
                    }
                    try (TestWorld world = new TestWorld(level, anchor(level))) {
                        // 基准: 原版岩浆伤害(其 damage_type 同样声明 effects=burning)
                        Cow vanillaProbe = world.cow(20.0D);
                        TestWorld.extinguish(vanillaProbe);
                        vanillaProbe.hurt(vanillaProbe.level().damageSources().hotFloor(), 2.0F);
                        boolean vanillaAppliesEffects = vanillaProbe.getRemainingFireTicks() > 0;
                        c.note("原版 hot_floor 命中 → 燃烧 tick=" + vanillaProbe.getRemainingFireTicks()
                                + " ⇒ 1.21.1 " + (vanillaAppliesEffects ? "会" : "不会")
                                + "在伤害里执行 effects 字段");

                        if (!vanillaAppliesEffects) {
                            c.skip("1.21.1 不在伤害调用里执行 DamageType.effects(连原版岩浆都不点燃) ——"
                                    + " 本模组的\"点燃双方\"由自身 applyWeepingChildIgnite 实现(与文档一致),"
                                    + " effects 字段仅为语义/工具元数据");
                            return;
                        }

                        Zombie attacker = world.zombie();
                        Cow victim = world.cow(20.0D);
                        TestWorld.extinguish(victim);
                        victim.hurt(new DamageSource(fire, attacker, attacker), 2.0F);
                        c.note("直接 weeping_fire 命中 → 燃烧 tick=" + victim.getRemainingFireTicks());
                        c.that(victim.getRemainingFireTicks() > 0,
                                "weeping_fire 的 burning 效果必须由原版施加");
                    }
                });

        suite.addWorld("weeping_child_live_hit_actually_burns",
                "哭泣之子的实打必须真的产生\"哭泣之火\"来源: 用 mobAttack 打目标, 目标必须被点燃(只有 effects=burning 的伤害类型会点燃)",
                "带哭泣之子的攻击 → 目标 getRemainingFireTicks() > 0",
                c -> {
                    try (TestWorld world = new TestWorld(level, anchor(level))) {
                        Zombie attacker = world.zombie();
                        Cow victim = world.cow(20.0D);
                        TestWorld.mainHand(attacker, enchanted(ModEnchantments.WEEPING_CHILD, 1));

                        victim.hurt(victim.level().damageSources().mobAttack(attacker), 2.0F);

                        c.note("目标剩余燃烧 tick=" + victim.getRemainingFireTicks()
                                + " 剩余生命=" + TestKit.fmt(victim.getHealth()));
                        c.that(victim.getRemainingFireTicks() > 0,
                                "哭泣之子攻击必须让目标着火(证明 weeping_fire 来源真的被施加)");
                    }
                });

        suite.addWorld("weeping_child_control_without_enchant_does_not_burn",
                "反例: 同一次 mobAttack 攻击, 攻击者不带哭泣之子时必须<b>不</b>点燃目标 —— 证明上面的点火来自转换而不是原版攻击",
                "无附魔 → getRemainingFireTicks() == 0",
                c -> {
                    try (TestWorld world = new TestWorld(level, anchor(level))) {
                        Zombie attacker = world.zombie();
                        Cow victim = world.cow(20.0D);
                        TestWorld.mainHand(attacker, new ItemStack(Items.DIAMOND_SWORD));

                        victim.hurt(victim.level().damageSources().mobAttack(attacker), 2.0F);

                        c.note("对照: 剩余燃烧 tick=" + victim.getRemainingFireTicks());
                        c.that(victim.getRemainingFireTicks() <= 0,
                                "未附魔时不得点燃", "实测 " + victim.getRemainingFireTicks());
                    }
                });

        suite.addWorld("snow_wound_live_hit_actually_freezes",
                "雪的伤实打转 frost: 先探测\"直接命中 frost 源本身是否会冻结\"; 若原版不通过 effects 施加冻结, 该断言无意义(记 SKIP, 冰霜语义由 is_freezing 标签承担); 否则转换出的 frost 必须冻结目标",
                "直接 frost 冻结成立的前提下, 雪的伤命中 → getTicksFrozen() > 0",
                c -> {
                    Registry<DamageType> registry = registry(level);
                    Holder.Reference<DamageType> frost = registry.getHolder(key(FROST)).orElse(null);
                    if (frost == null) {
                        c.skip("frost 未注册");
                        return;
                    }
                    try (TestWorld world = new TestWorld(level, anchor(level))) {
                        // 探测: 不经附魔, 直接施加 frost 源
                        Zombie probeAttacker = world.zombie();
                        Cow probeVictim = world.cow(20.0D);
                        probeVictim.hurt(new DamageSource(frost, probeAttacker, probeAttacker), 2.0F);
                        boolean vanillaAppliesFreezing = probeVictim.getTicksFrozen() > 0;
                        c.note("直接 frost 命中 → 冻结 tick=" + probeVictim.getTicksFrozen());
                        if (!vanillaAppliesFreezing) {
                            c.skip("1.21.1 不通过伤害类型 effects 施加冻结(直接命中 frost 也不冻结);"
                                    + " 冰霜语义由 is_freezing 标签承担, 已由 tags_give_expected_semantics 覆盖");
                            return;
                        }

                        Zombie attacker = world.zombie();
                        Cow victim = world.cow(20.0D);
                        TestWorld.mainHand(attacker, enchanted(ModEnchantments.SNOW_WOUND, 1));
                        victim.hurt(victim.level().damageSources().mobAttack(attacker), 2.0F);
                        c.note("雪的伤命中 → 冻结 tick=" + victim.getTicksFrozen());
                        c.that(victim.getTicksFrozen() > 0,
                                "雪的伤转换出的 frost 必须让目标冻结");
                    }
                });

        suite.addWorld("unyielding_fate_live_hit_ignores_armor",
                "唯有命运的实打必须产生真伤: 穿着者(×6)打带甲目标时伤害不吃护甲减免, 而普通攻击的伤害会被护甲削掉",
                "真伤 delta == 6×基础; 普通攻击 带甲 delta < 无甲 delta",
                c -> {
                    try (TestWorld world = new TestWorld(level, anchor(level))) {
                        float base = 2.0F;

                        // 本用例断言精确伤害, 因此必须先把攻击者的暴击率压到 0 ——
                        // 否则随机暴击(×1.5)会让断言时红时绿: 实测曾出现"真伤 18"而不是 12,
                        // 即 12×1.5, 属测试不确定性而非代码回归。
                        java.util.function.Consumer<LivingEntity> noCrit = a -> {
                            var critAttr = a.getAttribute(
                                    dev.shadowsoffire.apothic_attributes.api.ALObjects.Attributes.CRIT_CHANCE);
                            if (critAttr != null) {
                                critAttr.setBaseValue(0.0D);
                            }
                        };

                        // 真伤: 攻击者穿唯有命运, 目标带 20 点护甲
                        Zombie trueAttacker = world.zombie();
                        TestWorld.equip(trueAttacker, EquipmentSlot.CHEST,
                                enchanted(ModEnchantments.UNYIELDING_FATE, 1));
                        noCrit.accept(trueAttacker);
                        Cow armoredVictim = world.cow(60.0D);
                        setArmor(armoredVictim, 20.0D);
                        double trueDelta = healthLoss(armoredVictim, trueAttacker, base);

                        // 对照 A: 普通攻击 + 同样 20 点护甲
                        Zombie plainA = world.zombie();
                        noCrit.accept(plainA);
                        Cow armoredPlain = world.cow(60.0D);
                        setArmor(armoredPlain, 20.0D);
                        double armoredPlainDelta = healthLoss(armoredPlain, plainA, base);

                        // 对照 B: 普通攻击 + 无护甲
                        Zombie plainB = world.zombie();
                        noCrit.accept(plainB);
                        Cow bare = world.cow(60.0D);
                        setArmor(bare, 0.0D);
                        double bareDelta = healthLoss(bare, plainB, base);

                        c.note("护甲值=" + armoredPlain.getArmorValue()
                                + " 真伤(带甲)=" + TestKit.fmt(trueDelta)
                                + " 普通(带甲)=" + TestKit.fmt(armoredPlainDelta)
                                + " 普通(无甲)=" + TestKit.fmt(bareDelta));
                        c.eq("真伤必须 ×6 且无视护甲", trueDelta, base * 6.0D, 0.05D);
                        c.that(armoredPlainDelta < bareDelta, "普通攻击必须被护甲削减",
                                "带甲 " + TestKit.fmt(armoredPlainDelta) + " 应 < 无甲 " + TestKit.fmt(bareDelta));
                    }
                });

        suite.addWorld("conversion_is_not_recursive",
                "转换只允许发生一次: 若重放层再次转换, 伤害会指数增长(×6 变 ×36); 单次命中的伤害必须约等于一次 ×6",
                "单次攻击的伤害 ≈ 基础 ×6, 不是 ×36",
                c -> {
                    try (TestWorld world = new TestWorld(level, anchor(level))) {
                        Zombie attacker = world.zombie();
                        TestWorld.equip(attacker, EquipmentSlot.CHEST,
                                enchanted(ModEnchantments.UNYIELDING_FATE, 1));
                        Cow victim = world.cow(200.0D);
                        float base = 1.0F;
                        float before = victim.getHealth();
                        victim.hurt(victim.level().damageSources().mobAttack(attacker), base);
                        double delta = before - victim.getHealth();
                        c.note("单次命中 delta=" + TestKit.fmt(delta) + "(期望 6, 递归则为 36)");
                        c.range("单次转换伤害", delta, 5.5D, 6.5D);
                    }
                });

        // ------------------------------------------------------------------
        // Epic Fight 兼容回归(2026-09-25)
        // EF 的伤害链路用 `event.getSource() instanceof EpicFightDamageSource` 判定"这是 EF 的一击",
        // 武器技能充能(WEAPON_CHARGE)就挂在该判定之后的 DEAL_DAMAGE_EVENT_DAMAGE 上
        // (EF 1.20.1 ServerPlayerPatch.java:56-69)。旧实现在转换时 `new DamageSource(...)` 顶替,
        // instanceof 变假 → 事件不触发 → "装了哭泣之子后武器技能进度条不动"。
        // 修法: 原地改写传入 DamageSource 的 type 字段, 对象身份与子类私有状态全部保留。
        // ------------------------------------------------------------------
        suite.addWorld("conversion_retypes_the_source_in_place",
                "Epic Fight 兼容回归: 转换必须\"原地改写传入的那个 DamageSource\"而不是新建对象顶替 —— "
                        + "否则 EpicFightDamageSource 的子类身份与私有状态(武器技能充能等)会随对象一起丢失",
                "实打后: 传入对象自身已变成 weeping_fire、目标记录的伤害来源就是同一个对象(且仍是我们构造的子类)、目标确实掉血",
                c -> {
                    try (TestWorld world = new TestWorld(level, anchor(level))) {
                        Zombie attacker = world.zombie();
                        Cow victim = world.cow(20.0D);
                        TestWorld.mainHand(attacker, enchanted(ModEnchantments.WEEPING_CHILD, 1));

                        Holder<DamageType> mobAttack = registry(level)
                                .getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK);

                        // 带私有状态的 DamageSource 子类(EpicFightDamageSource 的替身):
                        // 转换后必须仍是同一个实例, 且子类字段还在。
                        final class ProbeSource extends DamageSource {
                            final String marker = "probe-state";

                            ProbeSource(Holder<DamageType> type, net.minecraft.world.entity.Entity direct,
                                        net.minecraft.world.entity.Entity causing) {
                                super(type, direct, causing);
                            }
                        }
                        ProbeSource probe = new ProbeSource(mobAttack, attacker, attacker);

                        float before = victim.getHealth();
                        victim.hurt(probe, 2.0F);
                        DamageSource recorded = victim.getLastDamageSource();

                        c.note("传入对象命中后的类型=" + probe.type().msgId()
                                + " 目标生命 " + TestKit.fmt(before) + "→" + TestKit.fmt(victim.getHealth()));
                        c.note("目标记录的伤害来源类型="
                                + (recorded == null ? "null" : recorded.type().msgId())
                                + " 类=" + (recorded == null ? "null" : recorded.getClass().getSimpleName()));
                        c.that(probe.is(key(WEEPING_FIRE)),
                                "传入的 DamageSource 必须被原地改成 weeping_fire(旧实现里它会保持原样)",
                                "实测类型: " + probe.type().msgId());
                        c.that(recorded == probe,
                                "目标记录的伤害来源必须就是传入的那个对象(EF 的充能监听器拿到的也是它)",
                                "recorded=" + (recorded == null ? "null" : recorded.type().msgId())
                                        + " 同一对象=" + (recorded == probe));
                        c.that(recorded instanceof ProbeSource,
                                "子类身份必须保留(EF 的 EpicFightDamageSource 同理)",
                                "recorded 类=" + (recorded == null ? "null" : recorded.getClass().getSimpleName()));
                        if (recorded instanceof ProbeSource ps) {
                            c.eq("子类私有状态必须随对象保留", ps.marker, "probe-state");
                        }
                        c.that(victim.getHealth() < before, "转换后的伤害必须真的打进去",
                                "生命 " + TestKit.fmt(victim.getHealth()) + " 应 < " + TestKit.fmt(before));
                    }
                });

        // ------------------------------------------------------------------
        // 修法的"不回归"面: 投射物路径与"谁打的"口径必须与修复前逐字一致
        // (用户要求: 确保其他功能实现的效果不变。原地改写只换 type/directEntity/causingEntity
        //  三个字段, 取值与旧实现 new DamageSource(holder, attacker, attacker) 完全相同。)
        // ------------------------------------------------------------------
        suite.addWorld("projectile_conversion_still_works",
                "回归: 射手的箭命中仍必须转成 weeping_fire(投射物分支不许被原地改写改坏), "
                        + "且转换后的归属口径与修复前一致(直接实体/造成者都是射手)",
                "哭泣之子持有者射出的箭命中 → 目标被点燃; 转换后 source.getDirectEntity()==getEntity()==射手",
                c -> {
                    try (TestWorld world = new TestWorld(level, anchor(level))) {
                        Zombie shooter = world.zombie();
                        Cow victim = world.cow(20.0D);
                        TestWorld.mainHand(shooter, enchanted(ModEnchantments.WEEPING_CHILD, 1));

                        net.minecraft.world.entity.projectile.Arrow arrow =
                                world.spawn(net.minecraft.world.entity.EntityType.ARROW);
                        arrow.setOwner(shooter);

                        TestWorld.extinguish(victim);
                        DamageSource arrowSource = level.damageSources().arrow(arrow, shooter);
                        float before = victim.getHealth();
                        victim.hurt(arrowSource, 2.0F);

                        c.note("箭命中后: 类型=" + arrowSource.type().msgId()
                                + " 燃烧 tick=" + victim.getRemainingFireTicks()
                                + " 生命 " + TestKit.fmt(before) + "→" + TestKit.fmt(victim.getHealth()));
                        c.that(arrowSource.is(key(WEEPING_FIRE)), "箭矢必须照旧被转成 weeping_fire",
                                "实测类型: " + arrowSource.type().msgId());
                        c.that(victim.getRemainingFireTicks() > 0, "哭泣之火必须真的点燃目标",
                                "燃烧 tick=" + victim.getRemainingFireTicks());
                        c.that(arrowSource.getDirectEntity() == shooter && arrowSource.getEntity() == shooter,
                                "归属口径必须与修复前一致(直接实体与造成者都归到射手)",
                                "direct=" + arrowSource.getDirectEntity() + " causing=" + arrowSource.getEntity());
                    }
                });

        return List.of(suite);
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    private static ItemStack enchanted(ResourceKey<Enchantment> key, int level) {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        stack.enchant(ModEnchantments.getHolder(key), level);
        return stack;
    }

    /** 直接设护甲属性 —— 比"给实体穿甲"可靠: 装备的护甲修饰符要等一次 aiStep 才生效。 */
    private static void setArmor(LivingEntity entity, double armor) {
        var instance = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        if (instance != null) {
            instance.setBaseValue(armor);
        }
    }

    /** 施加一次 mobAttack 并返回掉血量。 */
    private static double healthLoss(LivingEntity victim, LivingEntity attacker, float amount) {
        float before = victim.getHealth();
        victim.hurt(victim.level().damageSources().mobAttack(attacker), amount);
        return before - victim.getHealth();
    }

    private static Registry<DamageType> registry(ServerLevel level) {
        return level.getServer().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
    }

    private static ResourceKey<DamageType> key(String id) {
        return ResourceKey.create(Registries.DAMAGE_TYPE,
                ResourceLocation.fromNamespaceAndPath(CommonConstants.MODID, id));
    }

    private static DamageType type(Registry<DamageType> registry, String id) {
        return registry.getHolder(key(id)).map(Holder::value).orElse(null);
    }

    private static DamageSource source(Registry<DamageType> registry, String id) {
        Holder.Reference<DamageType> holder = registry.getHolder(key(id)).orElse(null);
        return holder == null ? null : new DamageSource(holder);
    }

    private static net.minecraft.core.BlockPos anchor(ServerLevel level) {
        return level.getSharedSpawnPos();
    }

    /**
     * 从 classpath 读本模组 lang 文件并取某个键的文案。
     * 不走 server ResourceManager —— 专用服务器只加载 data/, 看不到 assets/lang。
     */
    private static String langValue(String relativePath, String key) {
        String resource = "/assets/" + CommonConstants.MODID + "/" + relativePath;
        try (java.io.InputStream in = DamageTypeSuites.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))) {
                com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseReader(reader);
                if (parsed.isJsonObject() && parsed.getAsJsonObject().has(key)) {
                    return parsed.getAsJsonObject().get(key).getAsString();
                }
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }
}
