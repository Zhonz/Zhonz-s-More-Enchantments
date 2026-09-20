package com.zhonz.moreenchantments.command.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zhonz.moreenchantments.common.CommonConstants;
import com.zhonz.moreenchantments.common.enchant.EnchantIds;
import com.zhonz.moreenchantments.enchantment.ModEnchantments;
import com.zhonz.moreenchantments.event.ModEventHandlers;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.io.BufferedReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * 注册表 / 元数据 / 接线 测试(round-testkit)。
 *
 * <p>这三样是"全量覆盖"的正确切入点。旧套件对 91 个附魔做的是
 * "赋予一次、hurt 一次、不崩就算过"的烟雾测试(见
 * {@code graphflow-out/audit/04-existing-tests.md} 结论), 它无法发现:
 * <ul>
 *   <li>某个 id 在 Java 常量表里有、却没有对应的 JSON(或反过来) —— 漂移;</li>
 *   <li>某个 id 的 max_level / 花费表被写反;</li>
 *   <li>某个 id 的 description 翻译键拼错(客户端显示成原始键名);</li>
 *   <li>给物品附上某 id 后, 事件层的 id→等级 查询其实读不到(接线断了);</li>
 *   <li>JSON 里写了 1.21.1 根本不认识的键(死数据, 静默无效)。</li>
 * </ul>
 *
 * <p>本套件的口径是<b>以仓库自身为基准做自洽性检查</b>: id 词表从随包发布的
 * JSON 目录枚举, 与 Java 常量表(反射取得)双向比对, 再与运行时注册表比对。
 * 三方一致 + 每条的元数据合法 + 接线可读, 才算"91 个附魔真的存在且能用"。
 */
public final class MetaSuites {

    private MetaSuites() {
    }

    public static final String SUITE_REGISTRY = "meta-registry";
    public static final String SUITE_WIRING = "meta-wiring";
    public static final String SUITE_HYGIENE = "meta-hygiene";

    /**
     * 1.21.1 原版附魔 JSON 的合法顶层键(以 neoforge-21.1.235 资源包内 42 个
     * 原版附魔文件实测的键并集为准)。不在集合内的键会被 codec 静默忽略 ——
     * 也就是"写了但完全没用"的死数据。
     */
    private static final Set<String> VALID_JSON_KEYS = Set.of(
            "description", "supported_items", "primary_items", "weight", "max_level",
            "min_cost", "max_cost", "anvil_cost", "slots", "effects", "exclusive_set");

    public static List<TestKit.Suite> all(ServerLevel level) {
        return List.of(registrySuite(level), wiringSuite(level), hygieneSuite(level));
    }

    /** 供其他套件复用: Java 常量表里的全部 id(排序后), 避免各处重复反射。 */
    public static List<String> allIds() {
        return codeIds();
    }

    // ==================================================================
    // 套件一: 注册表自洽
    // ==================================================================

    private static TestKit.Suite registrySuite(ServerLevel level) {
        TestKit.Suite suite = new TestKit.Suite(SUITE_REGISTRY,
                "注册表/元数据: id 三方一致、定义合法、JSON 无死键、标签约束成立");

        suite.addWorld("data_json_count_is_91",
                "随包发布的附魔 JSON 数量必须与文档声明的 91 一致",
                "data/zhonz_more_enchantments/enchantment/*.json 恰好 91 个",
                c -> {
                    List<String> ids = dataIds(level);
                    c.note("实测 JSON 数 " + ids.size());
                    c.eq("附魔 JSON 数量", ids.size(), 91);
                });

        suite.addWorld("code_constants_count_is_91",
                "Java 常量表(EnchantIds 的 public static final String)必须有 91 个且互不重复",
                "EnchantIds 恰好 91 个 String 常量, 无重复",
                c -> {
                    List<String> ids = codeIds();
                    c.note("实测常量数 " + ids.size());
                    c.eq("EnchantIds 常量数", ids.size(), 91);
                    c.eq("去重后数量", new LinkedHashSet<>(ids).size(), ids.size());
                });

        suite.addWorld("data_and_code_ids_aligned",
                "JSON 文件名集合与 Java 常量集合必须双向一致(既不能有孤儿 JSON, 也不能有指向不存在附魔的常量)",
                "两侧集合完全相等",
                c -> {
                    Set<String> fromData = new TreeSet<>(dataIds(level));
                    Set<String> fromCode = new TreeSet<>(codeIds());
                    Set<String> onlyData = new TreeSet<>(fromData);
                    onlyData.removeAll(fromCode);
                    Set<String> onlyCode = new TreeSet<>(fromCode);
                    onlyCode.removeAll(fromData);
                    c.note("仅 JSON 有 " + onlyData.size() + " 个, 仅代码有 " + onlyCode.size() + " 个");
                    c.that(onlyData.isEmpty(), "不存在只有 JSON 没有常量的 id", String.join(", ", onlyData));
                    c.that(onlyCode.isEmpty(), "不存在只有常量没有 JSON 的 id", String.join(", ", onlyCode));
                });

        suite.addWorld("all_ids_resolvable_in_registry",
                "每个 id 都必须能在运行时附魔注册表里解析出 Holder(否则事件层 getHolderOrThrow 会抛异常)",
                "91 个 id 全部 getHolder().isPresent()",
                c -> {
                    Registry<Enchantment> registry = registry(level);
                    List<String> missing = new ArrayList<>();
                    for (String id : codeIds()) {
                        if (registry.getHolder(key(id)).isEmpty()) {
                            missing.add(id);
                        }
                    }
                    c.note("注册表内本命名空间附魔 " + namespaceCount(registry) + " 个");
                    report(c, "全部 id 可解析", missing, codeIds().size());
                });

        suite.addWorld("registry_namespace_count_is_91",
                "本命名空间在注册表里的条目数必须正好 91(多出来说明有残留/幽灵注册)",
                "registry 内 zhonz_more_enchantments:* 恰好 91 个",
                c -> {
                    int count = namespaceCount(registry(level));
                    c.note("实测 " + count);
                    c.eq("注册表条目数", count, 91);
                });

        suite.addWorld("json_has_no_unknown_keys",
                "附魔 JSON 不得包含 1.21.1 不认识的自造键(会被 codec 静默忽略, 形成'写了没生效'的死数据)",
                "每个 JSON 的顶层键都属于原版键集",
                c -> {
                    List<String> violations = new ArrayList<>();
                    for (String id : dataIds(level)) {
                        JsonObject json = jsonOf(level, id);
                        if (json == null) {
                            violations.add(id + "(读取失败)");
                            continue;
                        }
                        List<String> unknown = new ArrayList<>();
                        for (String k : json.keySet()) {
                            if (!VALID_JSON_KEYS.contains(k)) {
                                unknown.add(k);
                            }
                        }
                        if (!unknown.isEmpty()) {
                            violations.add(id + "→" + unknown);
                        }
                    }
                    report(c, "JSON 无未知键", violations, dataIds(level).size());
                });

        suite.addWorld("definition_sane_per_id",
                "每个附魔的定义必须自洽: 等级>=1、有可附魔物品、有槽位、花费区间不反向",
                "91 条全部满足 (max_level>=1, supported_items 非空, slots 非空, min_cost<=max_cost)",
                c -> {
                    Registry<Enchantment> registry = registry(level);
                    List<String> violations = new ArrayList<>();
                    for (String id : dataIds(level)) {
                        Enchantment ench = orNull(registry, id);
                        if (ench == null) {
                            violations.add(id + "(不可解析)");
                            continue;
                        }
                        List<String> bad = new ArrayList<>();
                        if (ench.getMaxLevel() < 1) {
                            bad.add("max_level=" + ench.getMaxLevel());
                        }
                        if (ench.getSupportedItems().size() <= 0) {
                            bad.add("supported_items 为空");
                        }
                        if (!matchesAnySlot(ench)) {
                            bad.add("slots 为空(没有任何 EquipmentSlot 匹配)");
                        }
                        if (ench.getMinCost(1) > ench.getMaxCost(1)) {
                            bad.add("min_cost(1)>max_cost(1)");
                        }
                        if (ench.getAnvilCost() < 0) {
                            bad.add("anvil_cost<0");
                        }
                        if (ench.getWeight() < 1) {
                            bad.add("weight<1");
                        }
                        if (!bad.isEmpty()) {
                            violations.add(id + "→" + bad);
                        }
                    }
                    report(c, "定义自洽", violations, dataIds(level).size());
                });

        suite.addWorld("cost_monotonic_per_level",
                "花费表必须随等级单调不减(写反会导致高等级比低等级'便宜')",
                "对 l in 1..max_level: min_cost(l)<=max_cost(l) 且 max_cost(l) 单调不减",
                c -> {
                    Registry<Enchantment> registry = registry(level);
                    List<String> violations = new ArrayList<>();
                    for (String id : dataIds(level)) {
                        Enchantment ench = orNull(registry, id);
                        if (ench == null) {
                            continue;
                        }
                        int previousMax = Integer.MIN_VALUE;
                        for (int l = 1; l <= ench.getMaxLevel(); l++) {
                            int lo = ench.getMinCost(l);
                            int hi = ench.getMaxCost(l);
                            if (lo > hi) {
                                violations.add(id + "[lvl" + l + "] min>max");
                                break;
                            }
                            if (hi < previousMax) {
                                violations.add(id + "[lvl" + l + "] max_cost 倒退");
                                break;
                            }
                            previousMax = hi;
                        }
                    }
                    report(c, "花费表单调", violations, dataIds(level).size());
                });

        suite.addWorld("description_translation_key_per_id",
                "每个附魔的 description 必须是 TranslatableContents 且键名正是 enchantment.<modid>.<id>(拼错则客户端直接显示原始键名)",
                "91 条全部匹配 enchantment.zhonz_more_enchantments.<id>",
                c -> {
                    Registry<Enchantment> registry = registry(level);
                    List<String> violations = new ArrayList<>();
                    for (String id : dataIds(level)) {
                        Enchantment ench = orNull(registry, id);
                        if (ench == null) {
                            continue;
                        }
                        String expectedKey = "enchantment." + CommonConstants.MODID + "." + id;
                        if (!(ench.description().getContents() instanceof TranslatableContents translatable)) {
                            violations.add(id + "→非可翻译组件");
                        } else if (!expectedKey.equals(translatable.getKey())) {
                            violations.add(id + "→" + translatable.getKey() + " 期望 " + expectedKey);
                        }
                    }
                    report(c, "description 翻译键", violations, dataIds(level).size());
                });

        suite.addWorld("treasure_and_nontreasure_not_both",
                "同一个附魔不能同时出现在 #minecraft:treasure 与 #minecraft:non_treasure(互斥分类), 并报告两标签的覆盖数",
                "无附魔同时属于两个标签",
                c -> {
                    Registry<Enchantment> registry = registry(level);
                    List<String> violations = new ArrayList<>();
                    int treasure = 0;
                    int nonTreasure = 0;
                    int neither = 0;
                    for (String id : dataIds(level)) {
                        Holder<Enchantment> holder = registry.getHolder(key(id)).orElse(null);
                        if (holder == null) {
                            continue;
                        }
                        boolean isTreasure = holder.is(EnchantmentTags.TREASURE);
                        boolean isNonTreasure = holder.is(EnchantmentTags.NON_TREASURE);
                        if (isTreasure && isNonTreasure) {
                            violations.add(id);
                        }
                        if (isTreasure) {
                            treasure++;
                        } else if (isNonTreasure) {
                            nonTreasure++;
                        } else {
                            neither++;
                        }
                    }
                    c.note("treasure=" + treasure + " non_treasure=" + nonTreasure + " 两者皆无=" + neither);
                    report(c, "宝藏分类不冲突", violations, dataIds(level).size());
                });

        suite.addWorld("curse_tag_roundtrip",
                "minecraft:enchantment/curse.json 里声明的每个附魔都必须在运行时被判定为诅咒(标签与行为一致)",
                "诅咒标签内 3 条全部 holder.is(CURSE)",
                c -> {
                    Registry<Enchantment> registry = registry(level);
                    List<String> declared = tagValues(level, "minecraft", "enchantment/curse");
                    List<String> violations = new ArrayList<>();
                    for (String raw : declared) {
                        String path = raw.contains(":") ? raw.substring(raw.indexOf(':') + 1) : raw;
                        Holder<Enchantment> holder = registry.getHolder(key(path)).orElse(null);
                        if (holder == null) {
                            violations.add(raw + "(无法解析)");
                        } else if (!holder.is(EnchantmentTags.CURSE)) {
                            violations.add(raw + "(未被判定为诅咒)");
                        }
                    }
                    c.note("标签声明 " + declared);
                    report(c, "诅咒标签往返一致", violations, declared.size());
                });

        // ---- 宝藏: 1.21.1 的"宝藏"只能由 minecraft:treasure 标签表达 ─---
        // 背景: JSON 里写 is_treasure 是死键(1.21.1 无该字段、无代码读取);
        // 而 #minecraft:in_enchanting_table = #minecraft:non_treasure,
        // 所以"不在 non_treasure 里"就等于"永远拿不到"。
        suite.addWorld("treasure_tag_roundtrip",
                "treasure 标签里的每个附魔都必须在运行时被判为宝藏, 且不得同时落在 non_treasure 里",
                "标签内容与 holder.is(TREASURE) 完全一致, 两组无交集",
                c -> {
                    Registry<Enchantment> registry = registry(level);
                    List<String> declared = tagValues(level, "minecraft", "enchantment/treasure");
                    List<String> nonTreasure = tagValues(level, "minecraft", "enchantment/non_treasure");
                    List<String> violations = new ArrayList<>();
                    Set<String> paths = new TreeSet<>();
                    for (String raw : declared) {
                        String path = raw.contains(":") ? raw.substring(raw.indexOf(':') + 1) : raw;
                        paths.add(path);
                        Holder<Enchantment> holder = registry.getHolder(key(path)).orElse(null);
                        if (holder == null) {
                            violations.add(raw + "(无法解析)");
                        } else if (!holder.is(EnchantmentTags.TREASURE)) {
                            violations.add(raw + "(未被判为宝藏)");
                        } else if (holder.is(EnchantmentTags.NON_TREASURE)) {
                            violations.add(raw + "(同时属于 non_treasure)");
                        }
                    }
                    c.note("treasure 标签 " + paths.size() + " 条; non_treasure 标签 " + nonTreasure.size() + " 条");
                    report(c, "宝藏标签往返一致", violations, paths.size());
                });

        suite.addWorld("documented_normal_enchantments_reach_enchanting_table",
                "文档声明为「普通附魔」的 7 个必须真的能在附魔台出现 —— 附魔台只认 #minecraft:in_enchanting_table(= #non_treasure), 漏标就等于永久拿不到",
                "scavenger/charger/self_doubt/suppression/rhythm/primal_suffering/titan 全部 is(in_enchanting_table)",
                c -> {
                    Registry<Enchantment> registry = registry(level);
                    // 名单来自 ENCHANTMENTS.md 的「稀有度:普通附魔」条目(scavenger L25 / charger L53 /
                    // self_doubt L93 / suppression L134 / rhythm L250 / primal_suffering L381 / titan L498)
                    List<String> documentedNormal = List.of(
                            "scavenger", "charger", "self_doubt", "suppression",
                            "rhythm", "primal_suffering", "titan");
                    List<String> violations = new ArrayList<>();
                    for (String id : documentedNormal) {
                        Holder<Enchantment> holder = registry.getHolder(key(id)).orElse(null);
                        if (holder == null) {
                            violations.add(id + "(未注册)");
                        } else if (!holder.is(EnchantmentTags.IN_ENCHANTING_TABLE)) {
                            violations.add(id + "(不在 in_enchanting_table → 附魔台拿不到)");
                        }
                    }
                    report(c, "普通附魔可在附魔台获得", violations, documentedNormal.size());
                });

        suite.addWorld("curses_are_classified_as_treasure",
                "本模组的 3 个诅咒附魔必须与同原版一致地归入 treasure(原版 binding/vanishing_curse 同在 #minecraft:treasure)",
                "divine_curse / self_bound / perfunctory 均 is(TREASURE)",
                c -> {
                    Registry<Enchantment> registry = registry(level);
                    List<String> violations = new ArrayList<>();
                    for (String id : List.of("divine_curse", "self_bound", "perfunctory")) {
                        Holder<Enchantment> holder = registry.getHolder(key(id)).orElse(null);
                        if (holder == null) {
                            violations.add(id + "(未注册)");
                        } else if (!holder.is(EnchantmentTags.TREASURE)) {
                            violations.add(id + "(未归入 treasure)");
                        }
                    }
                    report(c, "诅咒归入宝藏", violations, 3);
                });

        suite.addWorld("exclusive_set_light_is_bidirectional",
                "互斥标签 exclusive_set/light 必须恰好含 photophile 与 photophobe, 且两者都能解析",
                "标签内容 == [photophile, photophobe]",
                c -> {
                    List<String> declared = tagValues(level, CommonConstants.MODID, "enchantment/exclusive_set/light");
                    Set<String> paths = new TreeSet<>();
                    for (String raw : declared) {
                        paths.add(raw.contains(":") ? raw.substring(raw.indexOf(':') + 1) : raw);
                    }
                    c.note("实测 " + paths);
                    c.eq("互斥标签成员", paths, new TreeSet<>(Set.of("photophile", "photophobe")));
                    Registry<Enchantment> registry = registry(level);
                    Holder<Enchantment> photophile = registry.getHolder(key("photophile")).orElse(null);
                    Holder<Enchantment> photophobe = registry.getHolder(key("photophobe")).orElse(null);
                    c.notNull("photophile 可解析", photophile);
                    c.notNull("photophobe 可解析", photophobe);
                    // 行为层往返: 两者的 exclusiveSet() 必须互相包含(仅靠标签文件一致说明不了运行时会互斥)
                    if (photophile != null && photophobe != null) {
                        c.that(photophile.value().exclusiveSet().contains(photophobe),
                                "photophile.exclusiveSet() 含 photophobe");
                        c.that(photophobe.value().exclusiveSet().contains(photophile),
                                "photophobe.exclusiveSet() 含 photophile");
                    }
                });

        return suite;
    }

    // ==================================================================
    // 套件二: 接线(id → 物品 → 事件层读取)
    // ==================================================================

    private static TestKit.Suite wiringSuite(ServerLevel level) {
        TestKit.Suite suite = new TestKit.Suite(SUITE_WIRING,
                "接线: 给物品附上 id 后, 事件层的 id→等级 查询是否真的读得到(以及读不到时的行为)");

        suite.addWorld("level_roundtrip_all_ids",
                "对全部 91 个 id: 用各自 supported_items 里的真实载体附到 level=2, 主手/任意槽查询都必须读回 2; 移除后必须回到 0(附魔书专用附魔单独由下一用例负责)",
                "非书附魔 100% 读回 2 → 0",
                c -> {
                    Registry<Enchantment> registry = registry(level);
                    List<String> violations = new ArrayList<>();
                    List<String> bookOnlyIds = new ArrayList<>();
                    int checked = 0;
                    try (TestWorld world = new TestWorld(level, anchor(level))) {
                        for (String id : dataIds(level)) {
                            Holder<Enchantment> holder = registry.getHolder(key(id)).orElse(null);
                            if (holder == null) {
                                violations.add(id + "(未注册)");
                                continue;
                            }
                            if (bookOnly(holder)) {
                                bookOnlyIds.add(id);
                                continue;
                            }
                            ItemStack carrier = carrierFor(holder);
                            if (carrier.isEmpty()) {
                                violations.add(id + "(无可附魔载体)");
                                continue;
                            }
                            applyEnchant(carrier, holder, 2);
                            if (readCarrierLevel(carrier, holder) != 2) {
                                violations.add(id + "(载体写入失败)");
                                continue;
                            }
                            checked++;
                            Zombie probe = world.spawn(net.minecraft.world.entity.EntityType.ZOMBIE);
                            TestWorld.stripGear(probe);
                            TestWorld.mainHand(probe, carrier);
                            int viaMainHand = ModEventHandlers.LEVELS.mainHand(probe, id);
                            int viaAnySlot = ModEventHandlers.LEVELS.anySlot(probe, id);
                            TestWorld.mainHand(probe, ItemStack.EMPTY);
                            int afterRemoval = ModEventHandlers.LEVELS.anySlot(probe, id);
                            if (viaMainHand != 2 || viaAnySlot != 2 || afterRemoval != 0) {
                                violations.add(id + "(主手=" + viaMainHand + " 任意=" + viaAnySlot
                                        + " 移除后=" + afterRemoval + ")");
                            }
                        }
                    }
                    c.note("附魔书专用(不在本用例口径内): " + bookOnlyIds);
                    report(c, "id→等级 roundtrip", violations, checked);
                });

        suite.addWorld("book_only_ids_use_stored_enchantments",
                "只可附在附魔书上的附魔, 其等级必须写在 STORED_ENCHANTMENTS 组件并读得回来(写错组件会导致'书看起来没附魔')",
                "每个书专用 id 都能从 STORED_ENCHANTMENTS 读回 2",
                c -> {
                    Registry<Enchantment> registry = registry(level);
                    List<String> violations = new ArrayList<>();
                    List<String> bookOnlyIds = new ArrayList<>();
                    for (String id : dataIds(level)) {
                        Holder<Enchantment> holder = registry.getHolder(key(id)).orElse(null);
                        if (holder == null || !bookOnly(holder)) {
                            continue;
                        }
                        bookOnlyIds.add(id);
                        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
                        applyEnchant(book, holder, 2);
                        int read = readCarrierLevel(book, holder);
                        if (read != 2) {
                            violations.add(id + "(读取 " + read + ")");
                        }
                        // 记录生产查询在该载体上的表现(供报告核对, 不作为判据)
                        c.note(id + ": STORED=" + read);
                    }
                    report(c, "附魔书专用附魔", violations, bookOnlyIds.size());
                });

        suite.addWorld("slot_query_matches_declared_slot",
                "把附魔放到它自己声明的槽位上时, 该槽位的精确查询必须读回等级(验证槽位参数真的被使用)",
                "非书附魔按其声明槽位装备后 slot() 读回 1",
                c -> {
                    Registry<Enchantment> registry = registry(level);
                    List<String> violations = new ArrayList<>();
                    int checked = 0;
                    try (TestWorld world = new TestWorld(level, anchor(level))) {
                        for (String id : dataIds(level)) {
                            Holder<Enchantment> holder = registry.getHolder(key(id)).orElse(null);
                            if (holder == null || bookOnly(holder)) {
                                continue;
                            }
                            EquipmentSlot target = firstMatchingSlot(holder.value());
                            if (target == null) {
                                continue;
                            }
                            ItemStack carrier = carrierFor(holder);
                            if (carrier.isEmpty()) {
                                continue;
                            }
                            applyEnchant(carrier, holder, 1);
                            if (readCarrierLevel(carrier, holder) != 1) {
                                continue;
                            }
                            checked++;
                            Zombie probe = world.spawn(net.minecraft.world.entity.EntityType.ZOMBIE);
                            TestWorld.stripGear(probe);
                            TestWorld.equip(probe, target, carrier);
                            int read = ModEventHandlers.LEVELS.slot(probe, id, target);
                            if (read != 1) {
                                violations.add(id + "@" + target + "=" + read);
                            }
                            TestWorld.stripGear(probe);
                        }
                    }
                    report(c, "槽位精确查询", violations, checked);
                });

        suite.addWorld("slot_query_isolated_to_its_own_slot",
                "把附魔放在 A 槽时, B 槽的精确查询必须是 0(否则说明槽位参数被忽略, 护甲附魔会互相串位)",
                "装备到 head 后, chest/feet/mainhand 查询均为 0",
                c -> {
                    Registry<Enchantment> registry = registry(level);
                    List<String> violations = new ArrayList<>();
                    try (TestWorld world = new TestWorld(level, anchor(level))) {
                        for (String id : dataIds(level)) {
                            Holder<Enchantment> holder = registry.getHolder(key(id)).orElse(null);
                            if (holder == null || bookOnly(holder)) {
                                continue;
                            }
                            EquipmentSlot declared = firstMatchingSlot(holder.value());
                            if (declared == null || declared == EquipmentSlot.MAINHAND) {
                                continue;
                            }
                            ItemStack carrier = carrierFor(holder);
                            if (carrier.isEmpty()) {
                                continue;
                            }
                            applyEnchant(carrier, holder, 1);
                            Zombie probe = world.spawn(net.minecraft.world.entity.EntityType.ZOMBIE);
                            TestWorld.stripGear(probe);
                            TestWorld.equip(probe, declared, carrier);
                            for (EquipmentSlot other : EquipmentSlot.values()) {
                                if (other == declared) {
                                    continue;
                                }
                                int leaked = ModEventHandlers.LEVELS.slot(probe, id, other);
                                if (leaked != 0) {
                                    violations.add(id + " 在 " + declared + " 却在 " + other + " 读到 " + leaked);
                                }
                            }
                            TestWorld.stripGear(probe);
                        }
                    }
                    report(c, "槽位隔离", violations, 1);
                });

        suite.addWorld("no_crosstalk_between_ids",
                "给主手附上某个 id 后, 其他 id 的查询必须仍然是 0(否则说明 id→附魔 的映射串了)",
                "抽样的交叉查询全部为 0",
                c -> {
                    Registry<Enchantment> registry = registry(level);
                    List<String> ids = dataIds(level);
                    List<String> violations = new ArrayList<>();
                    try (TestWorld world = new TestWorld(level, anchor(level))) {
                        int samples = 0;
                        for (int i = 0; i < ids.size(); i += 7) {
                            String applied = ids.get(i);
                            Holder<Enchantment> holder = registry.getHolder(key(applied)).orElse(null);
                            if (holder == null) {
                                continue;
                            }
                            ItemStack carrier = carrierFor(holder);
                            if (carrier.isEmpty()) {
                                continue;
                            }
                            carrier.enchant(holder, 3);
                            Zombie probe = world.spawn(net.minecraft.world.entity.EntityType.ZOMBIE);
                            TestWorld.stripGear(probe);
                            TestWorld.mainHand(probe, carrier);
                            for (int j = 0; j < ids.size(); j += 11) {
                                String other = ids.get(j);
                                if (other.equals(applied)) {
                                    continue;
                                }
                                samples++;
                                int leaked = ModEventHandlers.LEVELS.anySlot(probe, other);
                                if (leaked != 0) {
                                    violations.add("附 " + applied + " 却读到 " + other + "=" + leaked);
                                }
                            }
                            TestWorld.stripGear(probe);
                        }
                        c.note("采样交叉查询 " + samples + " 次");
                    }
                    report(c, "无 id 串扰", violations, 1);
                });

        suite.addWorld("unknown_id_is_handled_without_level",
                "未登记的 id: 事件路径用的 getHolderOrThrow 会抛异常, 因此必须存在 getHolderOrNull 供守卫使用",
                "getHolderOrNull(未知 id) == null",
                c -> {
                    ResourceKey<Enchantment> bogus = key("zhonz_definitely_not_an_enchantment");
                    c.that(ModEnchantments.getHolderOrNull(bogus) == null, "未知 id 的 null 安全查询返回 null");
                });

        suite.addWorld("carrier_present_in_supported_items",
                "从 supported_items 取出的第一个物品必须真的属于该附魔的可附魔集合(保证测试载体不是'碰巧能附')",
                "91 条的载体都包含在 supported_items 内",
                c -> {
                    Registry<Enchantment> registry = registry(level);
                    List<String> violations = new ArrayList<>();
                    for (String id : dataIds(level)) {
                        Holder<Enchantment> holder = registry.getHolder(key(id)).orElse(null);
                        if (holder == null) {
                            continue;
                        }
                        HolderSet<Item> supported = holder.value().getSupportedItems();
                        Item first = firstItem(supported);
                        if (first == null) {
                            violations.add(id + "(supported_items 无具体物品)");
                        } else if (!supported.contains(first.builtInRegistryHolder())) {
                            violations.add(id + "(载体不在集合内)");
                        }
                    }
                    report(c, "载体合法", violations, dataIds(level).size());
                });

        return suite;
    }

    // ==================================================================
    // 套件三: 数据卫生(命名/资源/本地化)
    // ==================================================================

    private static TestKit.Suite hygieneSuite(ServerLevel level) {
        TestKit.Suite suite = new TestKit.Suite(SUITE_HYGIENE,
                "数据卫生: id 命名规范、modifier id 唯一、本地化键齐备");

        suite.addWorld("id_naming_convention",
                "所有 id 必须是小写字母/数字/下划线(大写或空格会导致注册名异常、且与数据包文件名不一致)",
                "91 个 id 全部匹配 ^[a-z0-9_]+$",
                c -> {
                    List<String> violations = new ArrayList<>();
                    for (String id : codeIds()) {
                        if (!id.matches("^[a-z0-9_]+$")) {
                            violations.add(id);
                        }
                    }
                    report(c, "id 命名规范", violations, codeIds().size());
                });

        suite.addWorld("modifier_ids_unique_per_enchant",
                "每个附魔派生的属性 modifier id 必须唯一且带本模组命名空间(重复会让两附魔互相覆盖)",
                "每个 bonus_/mult_<id> 唯一且命名空间正确",
                c -> {
                    List<String> violations = new ArrayList<>();
                    Set<String> seen = new LinkedHashSet<>();
                    for (String id : codeIds()) {
                        String bonus = com.zhonz.moreenchantments.attribute.ZhonzAttributes
                                .bonusModifier(id).toString();
                        String mult = com.zhonz.moreenchantments.attribute.ZhonzAttributes
                                .multModifier(id).toString();
                        if (!bonus.startsWith(CommonConstants.MODID + ":")) {
                            violations.add(id + " bonus 命名空间错误");
                        }
                        if (!seen.add(bonus)) {
                            violations.add(id + " bonus modifier 重复");
                        }
                        if (!seen.add(mult)) {
                            violations.add(id + " mult modifier 重复");
                        }
                    }
                    report(c, "modifier id 唯一", violations, codeIds().size());
                });

        suite.addWorld("lang_keys_present_for_all_ids",
                "assets/<modid>/lang/zh_cn.json 与 en_us.json 必须为每个 id 提供 enchantment.<modid>.<id> 键(缺一个客户端就会显示原始键名)",
                "zh_cn 与 en_us 各含 91 个附魔名键",
                c -> {
                    Optional<JsonObject> zh = langOf(level, "lang/zh_cn.json");
                    Optional<JsonObject> en = langOf(level, "lang/en_us.json");
                    if (zh.isEmpty() || en.isEmpty()) {
                        c.skip("classpath 上找不到 assets/<modid>/lang/*.json(构建产物缺 lang)");
                        return;
                    }
                    List<String> violations = new ArrayList<>();
                    for (String id : codeIds()) {
                        String translationKey = "enchantment." + CommonConstants.MODID + "." + id;
                        if (!zh.get().has(translationKey)) {
                            violations.add("zh_cn 缺 " + id);
                        }
                        if (!en.get().has(translationKey)) {
                            violations.add("en_us 缺 " + id);
                        }
                    }
                    c.note("zh_cn 键数 " + zh.get().size() + ", en_us 键数 " + en.get().size());
                    report(c, "本地化键齐备", violations, codeIds().size());
                });

        suite.addWorld("lang_keys_have_no_orphans",
                "lang 文件里不得残留已删除附魔的 enchantment.<modid>.* 键(孤儿键会让维护者误以为该附魔仍在)",
                "lang 中 enchantment.<modid>. 前缀的键都属于当前 91 个 id",
                c -> {
                    Optional<JsonObject> zh = langOf(level, "lang/zh_cn.json");
                    if (zh.isEmpty()) {
                        c.skip("classpath 上找不到 assets/<modid>/lang/zh_cn.json");
                        return;
                    }
                    String prefix = "enchantment." + CommonConstants.MODID + ".";
                    Set<String> known = new LinkedHashSet<>(codeIds());
                    List<String> violations = new ArrayList<>();
                    for (String translationKey : zh.get().keySet()) {
                        if (translationKey.startsWith(prefix)) {
                            String rest = translationKey.substring(prefix.length());
                            // 允许 <id>.<后缀>(例如 finale.desc 由 enchdesc 可选前置消费)
                            String baseId = rest.contains(".") ? rest.substring(0, rest.indexOf('.')) : rest;
                            if (!known.contains(baseId)) {
                                violations.add(translationKey);
                            }
                        }
                    }
                    c.note("lang 内附魔名前缀键数 " + zh.get().keySet().stream()
                            .filter(k -> k.startsWith(prefix)).count());
                    report(c, "无孤儿本地化键", violations, 1);
                });

        return suite;
    }

    // ==================================================================
    // 内部工具
    // ==================================================================

    /** 该附魔是否匹配任意装备槽(1.21 无 getSlots(), 用 matchingSlot 逐槽探测)。 */
    private static boolean matchesAnySlot(Enchantment enchantment) {
        return firstMatchingSlot(enchantment) != null;
    }

    /** 该附魔声明的第一个装备槽; 无匹配返回 null。 */
    private static EquipmentSlot firstMatchingSlot(Enchantment enchantment) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (enchantment.matchingSlot(slot)) {
                return slot;
            }
        }
        return null;
    }

    /** 可附魔物品集合里的第一个具体物品。 */
    private static Item firstItem(HolderSet<Item> set) {
        for (Holder<Item> holder : set) {
            return holder.value();
        }
        return null;
    }

    /** 用附魔自己声明的可附魔物品构造载体(避免"测试挑了一个刚好能附的物品")。 */
    private static ItemStack carrierFor(Holder<Enchantment> holder) {
        Item item = firstItem(holder.value().getSupportedItems());
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    /**
     * 把附魔写到载体上, 并按载体类型走正确的组件。
     *
     * <p>关键点: 附魔书(enchanted_book)的附魔不在 {@code ENCHANTMENTS} 组件里, 而在
     * {@code STORED_ENCHANTMENTS}。直接对书调用 {@code ItemStack.enchant} 会写错组件,
     * 于是"书上的附魔"读不出来。旧套件正是在这一点上对 mercy_equal 用了错误写法
     * (见 graphflow-out/audit/04-existing-tests.md 结论 4)。
     *
     * @return 实际写入的组件名("enchantments" / "stored_enchantments")
     */
    private static String applyEnchant(ItemStack stack, Holder<Enchantment> holder, int level) {
        if (stack.is(Items.ENCHANTED_BOOK)) {
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            mutable.set(holder, level);
            stack.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
            return "stored_enchantments";
        }
        stack.enchant(holder, level);
        return "enchantments";
    }

    /** 读取载体上该附魔的等级(自动区分两种组件)。 */
    private static int readCarrierLevel(ItemStack stack, Holder<Enchantment> holder) {
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        int direct = enchantments.getLevel(holder);
        if (direct > 0) {
            return direct;
        }
        return stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(holder);
    }

    /** 该附魔是否只能由附魔书承载(其 supported_items 只有附魔书)。 */
    private static boolean bookOnly(Holder<Enchantment> holder) {
        for (Holder<Item> item : holder.value().getSupportedItems()) {
            if (!item.value().equals(Items.ENCHANTED_BOOK)) {
                return false;
            }
        }
        return true;
    }

    private static void report(TestKit.Check c, String label, List<String> violations, int total) {
        if (violations.isEmpty()) {
            c.that(true, label + " (" + total + " 项)");
            return;
        }
        int show = Math.min(12, violations.size());
        String detail = String.join("; ", violations.subList(0, show))
                + (violations.size() > show ? "; …另 " + (violations.size() - show) + " 项" : "");
        c.that(false, label + "(" + total + " 项中 " + violations.size() + " 项违规)", detail);
    }

    private static Registry<Enchantment> registry(ServerLevel level) {
        return level.getServer().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
    }

    private static ResourceKey<Enchantment> key(String id) {
        return ResourceKey.create(Registries.ENCHANTMENT,
                ResourceLocation.fromNamespaceAndPath(CommonConstants.MODID, id));
    }

    private static Enchantment orNull(Registry<Enchantment> registry, String id) {
        return registry.getHolder(key(id)).map(Holder::value).orElse(null);
    }

    private static int namespaceCount(Registry<Enchantment> registry) {
        int count = 0;
        for (ResourceLocation id : registry.keySet()) {
            if (id.getNamespace().equals(CommonConstants.MODID)) {
                count++;
            }
        }
        return count;
    }

    /** 从随包发布的资源里枚举附魔 JSON 的 basename = 权威 id 词表。 */
    private static List<String> dataIds(ServerLevel level) {
        Map<ResourceLocation, Resource> found = level.getServer().getResourceManager()
                .listResources("enchantment", rl -> rl.getNamespace().equals(CommonConstants.MODID));
        List<String> ids = new ArrayList<>();
        for (ResourceLocation rl : found.keySet()) {
            String path = rl.getPath();
            if (path.startsWith("enchantment/") && path.endsWith(".json")) {
                ids.add(path.substring("enchantment/".length(), path.length() - ".json".length()));
            }
        }
        ids.sort(String::compareTo);
        return ids;
    }

    /** 反射取 EnchantIds 的 String 常量 —— 不手写列表, 从根上杜绝"表项数与声明数漂移"。 */
    private static List<String> codeIds() {        List<String> ids = new ArrayList<>();
        for (Field field : EnchantIds.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                try {
                    Object value = field.get(null);
                    if (value instanceof String text) {
                        ids.add(text);
                    }
                } catch (IllegalAccessException ignored) {
                    // public static final, 正常不会发生
                }
            }
        }
        ids.sort(String::compareTo);
        return ids;
    }

    private static JsonObject jsonOf(ServerLevel level, String id) {
        Optional<Resource> resource = level.getServer().getResourceManager().getResource(
                ResourceLocation.fromNamespaceAndPath(CommonConstants.MODID, "enchantment/" + id + ".json"));
        if (resource.isEmpty()) {
            return null;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            JsonElement parsed = JsonParser.parseReader(reader);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 读本模组自己的 lang 文件。
     *
     * <p>走 classpath 而不是 {@code server.getResourceManager()}: 专用服务器的资源管理器
     * 只加载 {@code data/}, 看不到 {@code assets/lang}, 而模组自身的 jar/输出目录
     * 一定在类加载器上 —— 这样本地化用例在服务端也能跑, 不必依赖客户端。
     */
    private static Optional<JsonObject> langOf(ServerLevel level, String path) {
        String resource = "/assets/" + CommonConstants.MODID + "/" + path;
        try (java.io.InputStream in = MetaSuites.class.getResourceAsStream(resource)) {
            if (in == null) {
                return Optional.empty();
            }
            try (BufferedReader reader = new BufferedReader(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))) {
                JsonElement parsed = JsonParser.parseReader(reader);
                return parsed.isJsonObject() ? Optional.of(parsed.getAsJsonObject()) : Optional.empty();
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** 读一个标签 JSON 的 values 列表(仅支持平铺字符串列表; 用于本模组自有的两个标签)。 */
    private static List<String> tagValues(ServerLevel level, String namespace, String path) {
        List<String> values = new ArrayList<>();
        Optional<Resource> resource = level.getServer().getResourceManager().getResource(
                ResourceLocation.fromNamespaceAndPath(namespace, "tags/" + path + ".json"));
        if (resource.isEmpty()) {
            return values;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed.isJsonObject()) {
                JsonElement list = parsed.getAsJsonObject().get("values");
                if (list != null && list.isJsonArray()) {
                    for (JsonElement element : list.getAsJsonArray()) {
                        if (element.isJsonPrimitive()) {
                            values.add(element.getAsString());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // 读取失败按空处理, 由调用方的断言给出失败
        }
        return values;
    }

    private static net.minecraft.core.BlockPos anchor(ServerLevel level) {
        return level.getSharedSpawnPos();
    }
}
