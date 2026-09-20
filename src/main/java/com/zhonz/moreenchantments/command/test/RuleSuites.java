package com.zhonz.moreenchantments.command.test;

import com.zhonz.moreenchantments.common.damage.EnchantmentLevelLookup;
import com.zhonz.moreenchantments.common.damage.EventDamageConditions;
import com.zhonz.moreenchantments.common.damage.EventDamageContext;
import com.zhonz.moreenchantments.common.damage.TickBonusRules;
import com.zhonz.moreenchantments.common.enchant.EnchantIds;
import com.zhonz.moreenchantments.common.storage.EntityDataStorage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.List;

/**
 * 规则数值测试(round-testkit)—— "数值异常测试"的主体。
 *
 * <p>被测对象是 {@code common} 里那批<b>纯规则函数</b>:
 * {@link TickBonusRules}(每 tick 可判定的自身状态加伤)与
 * {@link EventDamageConditions}(事件条件型加伤 / 乘伤)。
 *
 * <p>关键手法: 附魔等级用 {@link LevelStubs} 声明 —— 规则函数读到什么等级完全由用例指定,
 * 而<b>实体状态是真的</b>(真的僵尸、真的速度、真的血量、真的着火、真的手持物)。
 * 于是可以构造真实物品系统做不出来的输入:
 * <ul>
 *   <li>越界等级 0 / 4 / 负数(命令层 Brigadier 限 1..3, 旧套件永远测不到);</li>
 *   <li>阈值正好命中(生命 == 25% / 30% / 50%)与刚好差一点(30.1%);</li>
 *   <li>速度 0 / 极大 / NaN;</li>
 *   <li>击杀数 0 / 1000 / 1000000;</li>
 *   <li>同一实体同时"带"正午+黄昏+哭泣之子之类的组合。</li>
 * </ul>
 *
 * <p>命名约定: 用例 id 以 {@code defect_} 开头的, 是<b>已知缺陷探针</b> ——
 * 它断言的是"正确行为", 因此当前会红; 这是刻意的, 用于把审计发现的缺陷固化成可回归的测试。
 */
public final class RuleSuites {

    private RuleSuites() {
    }

    public static final String SUITE_TICK = "rule-tick";
    public static final String SUITE_EVENT = "rule-event";

    // 测试自有的状态键 —— 不依赖生产常量, 避免测试与实现耦合;
    // 生产键的一致性由 defect_storage_backend_parity 那条根因用例单独负责。
    private static final String K_LIBERATOR = "zhonz_test_liberator_last_attack";
    private static final String K_FOOLS_LUCKY = "zhonz_test_fools_mask_lucky";
    private static final String K_RHYTHM = "zhonz_test_rhythm_hit";
    private static final String K_CEASELESS = "zhonz_test_ceaseless_stacks";
    private static final String K_ELITE = "zhonz_test_elite";

    /** 浮点容差: 规则里多为 0.1/0.2 级常量, 1e-6 足够且能抓出系数写错。 */
    private static final double TOL = 1.0E-6D;

    public static List<TestKit.Suite> all(ServerLevel level) {
        return List.of(tickSuite(level), eventSuite(level));
    }

    // ==================================================================
    // 一、TickBonusRules
    // ==================================================================

    private static TestKit.Suite tickSuite(ServerLevel level) {
        TestKit.Suite suite = new TestKit.Suite(SUITE_TICK,
                "TickBonusRules: 至高之术/新太阳/困兽之斗/极速攀升/悲伤的红 的数值与边界");

        // ---- 至高之术: +20%/级 ----
        suite.addWorld("supreme_art_level_scaling",
                "至高之术按等级线性加伤 +20%/级",
                "lvl1=0.2, lvl2=0.4, lvl3=0.6",
                c -> {
                    try (TestWorld w = new TestWorld(level, level.getSharedSpawnPos())) {
                        Zombie z = w.zombie();
                        c.eq("lvl1", TickBonusRules.supremeArt(LevelStubs.of(EnchantIds.SUPREME_ART, 1), z), 0.2D, TOL);
                        c.eq("lvl2", TickBonusRules.supremeArt(LevelStubs.of(EnchantIds.SUPREME_ART, 2), z), 0.4D, TOL);
                        c.eq("lvl3", TickBonusRules.supremeArt(LevelStubs.of(EnchantIds.SUPREME_ART, 3), z), 0.6D, TOL);
                    }
                });

        suite.addWorld("supreme_art_level_zero_is_inert",
                "等级 0 必须贡献 0.0(不能因为 level>0 判断写反而变成 0.2)",
                "lvl0=0.0",
                c -> {
                    try (TestWorld w = new TestWorld(level, level.getSharedSpawnPos())) {
                        Zombie z = w.zombie();
                        c.eq("lvl0", TickBonusRules.supremeArt(LevelStubs.of(EnchantIds.SUPREME_ART, 0), z), 0.0D, TOL);
                    }
                });

        suite.addWorld("supreme_art_negative_level_is_inert",
                "负数等级(异常注入)必须贡献 0.0, 不允许产生负加伤",
                "lvl-3=0.0",
                c -> {
                    try (TestWorld w = new TestWorld(level, level.getSharedSpawnPos())) {
                        Zombie z = w.zombie();
                        c.eq("lvl-3", TickBonusRules.supremeArt(LevelStubs.of(EnchantIds.SUPREME_ART, -3), z), 0.0D, TOL);
                    }
                });

        suite.addWorld("supreme_art_is_mainhand_only",
                "至高之术是主手限定: 只声明在任意槽而不在主手时不得生效",
                "仅副手/任意槽 → 0.0",
                c -> {
                    try (TestWorld w = new TestWorld(level, level.getSharedSpawnPos())) {
                        Zombie z = w.zombie();
                        LevelStubs onlyElsewhere = LevelStubs.none()
                                .onlyAnySlot(EnchantIds.SUPREME_ART, 3);
                        c.eq("仅任意槽", TickBonusRules.supremeArt(onlyElsewhere, z), 0.0D, TOL);
                    }
                });

        // ---- 新太阳: (光照/15) * 1.5 ----
        suite.addWorld("new_sun_requires_leggings",
                "新太阳是护腿限定: 不穿护腿时加成必须为 0",
                "无护腿 = 0.0",
                c -> {
                    try (TestWorld w = new TestWorld(level, level.getSharedSpawnPos())) {
                        Zombie z = w.zombie();
                        c.eq("无护腿", TickBonusRules.newSun(LevelStubs.of(EnchantIds.NEW_SUN, 1), z), 0.0D, TOL);
                    }
                });

        suite.addWorld("new_sun_scale_factor_is_light_over_15_times_1_5",
                "新太阳的系数必须是 (光照/15)×1.5, 且结果落在 [0, 1.5](光照 15 时 +150%)",
                "bonus == (light/15)*1.5 且 ∈[0,1.5]",
                c -> {
                    try (TestWorld w = new TestWorld(level, level.getSharedSpawnPos())) {
                        Zombie z = w.zombie();
                        LevelStubs legs = LevelStubs.legs(EnchantIds.NEW_SUN, 1);
                        double actual = TickBonusRules.newSun(legs, z);
                        int light = z.level().getMaxLocalRawBrightness(z.blockPosition());
                        double expected = (light / 15.0D) * 1.5D;
                        c.note("光照=" + light + " 实测=" + TestKit.fmt(actual));
                        c.eq("系数", actual, expected, TOL);
                        c.range("取值域", actual, 0.0D, 1.5D);
                    }
                });

        // ---- 困兽之斗: 生命 < 25% → +60% ----
        suite.addWorld("cornered_beast_threshold_is_strict",
                "困兽之斗的阈值是 **<** 25%(文档/README 写\"生命低于 25%\"): 恰好 25% 不生效, 24% 生效, 30% 不生效",
                "25%→0.0, 24%→0.6, 30%→0.0",
                c -> {
                    try (TestWorld w = new TestWorld(level, level.getSharedSpawnPos())) {
                        Zombie z = w.zombie();
                        LevelStubs head = LevelStubs.helmet(EnchantIds.CORNERED_BEAST, 1);
                        TestWorld.healthAtPercent(z, 0.25F);
                        double at25 = TickBonusRules.corneredBeast(head, z);
                        TestWorld.healthAtPercent(z, 0.24F);
                        double at24 = TickBonusRules.corneredBeast(head, z);
                        TestWorld.healthAtPercent(z, 0.30F);
                        double at30 = TickBonusRules.corneredBeast(head, z);
                        c.note("25%=" + TestKit.fmt(at25) + " 24%=" + TestKit.fmt(at24) + " 30%=" + TestKit.fmt(at30));
                        c.eq("恰好 25%(严格小于, 不生效)", at25, 0.0D, TOL);
                        c.eq("24%", at24, 0.6D, TOL);
                        c.eq("30%", at30, 0.0D, TOL);
                    }
                });

        suite.addWorld("cornered_beast_requires_helmet",
                "困兽之斗是头盔限定: 未声明头盔时不得生效",
                "无头盔 = 0.0",
                c -> {
                    try (TestWorld w = new TestWorld(level, level.getSharedSpawnPos())) {
                        Zombie z = w.zombie();
                        TestWorld.healthAtPercent(z, 0.10F);
                        c.eq("无头盔", TickBonusRules.corneredBeast(LevelStubs.of(EnchantIds.CORNERED_BEAST, 1), z), 0.0D, TOL);
                    }
                });

        // ---- 极速攀升: y>0 → y*0.01 ----
        suite.addWorld("rapid_ascent_y_scaling_and_zero_at_ground",
                "极速攀升: y>0 时按 y×1% 加伤; y<=0 时必须是 0(不能变成负加伤)",
                "y=20→0.2, y=100→1.0, y=0→0.0, y=-10→0.0",
                c -> {
                    try (TestWorld w = new TestWorld(level, level.getSharedSpawnPos())) {
                        Zombie z = w.zombie();
                        LevelStubs feet = LevelStubs.feet(EnchantIds.RAPID_ASCENT, 1);
                        c.eq("y=20", TickBonusRules.rapidAscent(feet, atY(w, z, 20.0D)), 0.2D, TOL);
                        c.eq("y=100", TickBonusRules.rapidAscent(feet, atY(w, z, 100.0D)), 1.0D, TOL);
                        c.eq("y=0", TickBonusRules.rapidAscent(feet, atY(w, z, 0.0D)), 0.0D, TOL);
                        c.eq("y=-10", TickBonusRules.rapidAscent(feet, atY(w, z, -10.0D)), 0.0D, TOL);
                    }
                });

        suite.addWorld("rapid_ascent_magnitude_report",
                "极速攀升在 y>0 区间必须随高度单调不减(文档口径); 同时记录高空的绝对量级 —— 文档未设上限, 因此这里不判失败, 只暴露数值供决策",
                "单调不减; y=2000 的量级写入观测",
                c -> {
                    try (TestWorld w = new TestWorld(level, level.getSharedSpawnPos())) {
                        Zombie z = w.zombie();
                        LevelStubs feet = LevelStubs.feet(EnchantIds.RAPID_ASCENT, 1);
                        double low = TickBonusRules.rapidAscent(feet, atY(w, z, 100.0D));
                        double high = TickBonusRules.rapidAscent(feet, atY(w, z, 2000.0D));
                        c.note("y=100 → +" + TestKit.fmt(low * 100) + "%; y=2000 → +" + TestKit.fmt(high * 100) + "%");
                        c.that(high >= low, "高度越高加伤不得下降", "y=100:" + TestKit.fmt(low) + " y=2000:" + TestKit.fmt(high));
                        c.eq("y=2000 按公式应为 20.0", high, 20.0D, TOL);
                    }
                });

        // ---- 悲伤的红: 背包每格 +10% ----
        suite.addWorld("sorrowful_red_needs_chestplate",
                "悲伤的红是胸甲限定: 未穿胸甲时不得生效",
                "无胸甲 = 0.0",
                c -> {
                    try (TestWorld w = new TestWorld(level, level.getSharedSpawnPos())) {
                        Zombie z = w.zombie();
                        c.eq("无胸甲", TickBonusRules.sorrowfulRed(LevelStubs.of(EnchantIds.SORROWFUL_RED, 1), z), 0.0D, TOL);
                    }
                });

        suite.addWorld("sorrowful_red_ignores_non_players",
                "悲伤的红对非玩家实体必须返回 0(该规则读玩家背包, 非玩家不得因此崩溃或误加伤)",
                "僵尸 = 0.0",
                c -> {
                    try (TestWorld w = new TestWorld(level, level.getSharedSpawnPos())) {
                        Zombie z = w.zombie();
                        LevelStubs chest = LevelStubs.chest(EnchantIds.SORROWFUL_RED, 1);
                        c.eq("非玩家", TickBonusRules.sorrowfulRed(chest, z), 0.0D, TOL);
                    }
                });

        suite.addWorld("sorrowful_red_counts_filled_slots",
                "悲伤的红按\"有物品的格子数\"×10% 线性计算",
                "0 格=0.0, 1 格=0.1, 36 格=3.6",
                c -> {
                    FakePlayer player = freshFakePlayer(level);
                    LevelStubs chest = LevelStubs.chest(EnchantIds.SORROWFUL_RED, 1);
                    int size = player.getInventory().getContainerSize();
                    c.eq("空格", TickBonusRules.sorrowfulRed(chest, player), 0.0D, TOL);
                    player.getInventory().setItem(0, new ItemStack(Items.STONE));
                    c.eq("1 格", TickBonusRules.sorrowfulRed(chest, player), 0.1D, TOL);
                    for (int i = 0; i < size; i++) {
                        player.getInventory().setItem(i, new ItemStack(Items.STONE));
                    }
                    c.note("背包容量 " + size);
                    c.eq("满背包", TickBonusRules.sorrowfulRed(chest, player), 0.10D * size, TOL);
                    clearFakeInventory(player);
                });

        return suite;
    }

    // ==================================================================
    // 二、EventDamageConditions
    // ==================================================================

    private static TestKit.Suite eventSuite(ServerLevel level) {
        TestKit.Suite suite = new TestKit.Suite(SUITE_EVENT,
                "EventDamageConditions: 事件条件型加伤/乘伤 的数值、阈值与无上界风险");

        TestKit.Suite s = suite;

        // ---- 冲锋手 ----
        s.addWorld("charger_stationary_is_zero",
                "冲锋手按速度加成: 完全静止时必须为 0",
                "速度 0 → +0.0",
                c -> inProbe(level, (w, attacker, target) -> {
                    TestWorld.freeze(attacker);
                    double pct = bonusPct(LevelStubs.of(EnchantIds.CHARGER, 1), 0, attacker, target);
                    c.eq("静止", pct, 0.0D, 1.0E-4D);
                }));

        s.addWorld("charger_linear_in_speed_and_level",
                "冲锋手 = 速度/0.1 × 等级 × 0.5, 即 0.1 格/tick 时每级 +50%",
                "0.1 格/tick lvl1→0.5, lvl3→1.5",
                c -> inProbe(level, (w, attacker, target) -> {
                    TestWorld.horizontalSpeed(attacker, 0.1D, 0.0D);
                    c.eq("lvl1", bonusPct(LevelStubs.of(EnchantIds.CHARGER, 1), 0, attacker, target), 0.5D, 1.0E-4D);
                    c.eq("lvl3", bonusPct(LevelStubs.of(EnchantIds.CHARGER, 3), 0, attacker, target), 1.5D, 1.0E-4D);
                }));

        s.addWorld("charger_magnitude_report",
                "冲锋手必须随速度单调不减(文档口径\"速度越快伤害越高\"); 并记录高速下的量级 —— 文档未设上限, 故不判失败",
                "单调不减; 1.0 格/tick 的量级写入观测",
                c -> inProbe(level, (w, attacker, target) -> {
                    LevelStubs charger = LevelStubs.of(EnchantIds.CHARGER, 1);
                    TestWorld.horizontalSpeed(attacker, 0.1D, 0.0D);
                    double slow = bonusPct(charger, 0, attacker, target);
                    TestWorld.horizontalSpeed(attacker, 1.0D, 0.0D);
                    double fast = bonusPct(charger, 0, attacker, target);
                    c.note("0.1 格/tick → +" + TestKit.fmt(slow * 100) + "%; 1.0 格/tick → +"
                            + TestKit.fmt(fast * 100) + "% (×" + TestKit.fmt(1 + fast) + ")");
                    c.that(fast >= slow, "速度越快加伤不得下降");
                    c.eq("1.0 格/tick 按公式应为 +500%", fast, 5.0D, 1.0E-4D);
                }));

        s.addWorld("defect_charger_nan_velocity_propagates_nan",
                "[已知缺陷] 冲锋手不做有限性校验: 速度为 NaN 时加伤变成 NaN, 而 UnifiedDamageEngine 的默认通道早返回判定挡不住 NaN, 最终伤害为 NaN",
                "期望非有限速度被守卫为 0 加伤; 实测 NaN 传播",
                c -> inProbe(level, (w, attacker, target) -> {
                    try {
                        attacker.setDeltaMovement(Double.NaN, 0.0D, 0.0D);
                    } catch (RuntimeException rejected) {
                        // 引擎自己拒绝非有限向量时, 该路径产生不了 NaN 伤害 —— 视为已挡住
                        c.note("引擎拒绝 NaN 速度(" + rejected.getClass().getSimpleName() + "), 无法产生 NaN 伤害");
                        return;
                    }
                    double pct = bonusPct(LevelStubs.of(EnchantIds.CHARGER, 1), 0, attacker, target);
                    c.note("NaN 速度实测 +" + TestKit.fmt(pct));
                    c.finite("冲锋手加伤必须有限", pct);
                    c.eq("NaN 速度应视为 0 加成", pct, 0.0D, 1.0E-6D);
                }));

        // ---- 我的海疆 ----
        s.addWorld("my_sea_domain_requires_trident",
                "我的海疆: 仅手持三叉戟时 +60%, 其他武器为 0",
                "三叉戟→0.6, 钻石剑→0.0",
                c -> inProbe(level, (w, attacker, target) -> {
                    TestWorld.mainHand(attacker, new ItemStack(Items.TRIDENT));
                    c.eq("三叉戟", bonusPct(LevelStubs.of(EnchantIds.MY_SEA_DOMAIN, 1), 0, attacker, target), 0.60D, 1.0E-4D);
                    TestWorld.mainHand(attacker, new ItemStack(Items.DIAMOND_SWORD));
                    c.eq("剑", bonusPct(LevelStubs.of(EnchantIds.MY_SEA_DOMAIN, 1), 0, attacker, target), 0.0D, 1.0E-4D);
                }));

        // ---- 血泣 ----
        s.addWorld("blood_weep_level_table",
                "血泣的等级表必须是 +20%/30%/45%(一级/二级/三级)",
                "lvl1→0.20, lvl2→0.30, lvl3→0.45",
                c -> inProbe(level, (w, attacker, target) -> {
                    c.eq("lvl1", bonusPct(LevelStubs.of(EnchantIds.BLOOD_WEEP, 1), 0, attacker, target), 0.20D, 1.0E-4D);
                    c.eq("lvl2", bonusPct(LevelStubs.of(EnchantIds.BLOOD_WEEP, 2), 0, attacker, target), 0.30D, 1.0E-4D);
                    c.eq("lvl3", bonusPct(LevelStubs.of(EnchantIds.BLOOD_WEEP, 3), 0, attacker, target), 0.45D, 1.0E-4D);
                }));

        s.addWorld("blood_weep_over_max_level_falls_to_top_branch",
                "血泣超过 max_level(4 级)时走 else 分支取 45%: 记录\"超等级不报错、按最高档处理\"的行为",
                "lvl4→0.45(非崩溃、非继续增长)",
                c -> inProbe(level, (w, attacker, target) -> {
                    c.eq("lvl4", bonusPct(LevelStubs.of(EnchantIds.BLOOD_WEEP, 4), 0, attacker, target), 0.45D, 1.0E-4D);
                }));

        s.addWorld("blood_weep_level_zero_and_negative",
                "血泣等级 0 或负数必须为 0(>0 守卫)",
                "lvl0→0.0, lvl-1→0.0",
                c -> inProbe(level, (w, attacker, target) -> {
                    c.eq("lvl0", bonusPct(LevelStubs.of(EnchantIds.BLOOD_WEEP, 0), 0, attacker, target), 0.0D, 1.0E-4D);
                    c.eq("lvl-1", bonusPct(LevelStubs.of(EnchantIds.BLOOD_WEEP, -1), 0, attacker, target), 0.0D, 1.0E-4D);
                }));

        // ---- 破军 ----
        s.addWorld("army_breaker_thresholds_inclusive",
                "破军按目标血量比例判定: lvl1 阈值 30%、lvl2 40%、lvl3 50%, 判定为 <=(边界必须命中)",
                "lvl1 恰好30%→+0.10; lvl3 恰好50%→+0.30",
                c -> inProbe(level, (w, attacker, target) -> {
                    float max = target.getMaxHealth();
                    TestWorld.healthExact(target, max * 0.30F);
                    c.eq("lvl1@30%", bonusPct(LevelStubs.of(EnchantIds.ARMY_BREAKER, 1), 0, attacker, target), 0.10D, 1.0E-4D);
                    TestWorld.healthExact(target, max * 0.50F);
                    c.eq("lvl3@50%", bonusPct(LevelStubs.of(EnchantIds.ARMY_BREAKER, 3), 0, attacker, target), 0.30D, 1.0E-4D);
                }));

        s.addWorld("army_breaker_just_above_threshold_is_zero",
                "破军目标血量刚好高于阈值时必须为 0(验证不是 >= 写成 <= 的镜像错误)",
                "lvl1 目标 30.5% → +0.0",
                c -> inProbe(level, (w, attacker, target) -> {
                    float max = target.getMaxHealth();
                    TestWorld.healthExact(target, max * 0.305F);
                    c.eq("lvl1@30.5%", bonusPct(LevelStubs.of(EnchantIds.ARMY_BREAKER, 1), 0, attacker, target), 0.0D, 1.0E-4D);
                }));

        // ---- 血路 ----
        s.addWorld("blood_path_kills_scaling",
                "血路按同种击杀数 ×0.1% 线性加伤",
                "0→0.0, 1000→1.0, 1000000→1000.0",
                c -> inProbe(level, (w, attacker, target) -> {
                    c.eq("0 杀", bonusPct(LevelStubs.of(EnchantIds.BLOOD_PATH, 1), 0, attacker, target), 0.0D, 1.0E-4D);
                    c.eq("1000 杀", bonusPct(LevelStubs.of(EnchantIds.BLOOD_PATH, 1), 1000, attacker, target), 1.0D, 1.0E-3D);
                    c.eq("1000000 杀", bonusPct(LevelStubs.of(EnchantIds.BLOOD_PATH, 1), 1000000, attacker, target), 1000.0D, 1.0E-1D);
                }));

        s.addWorld("blood_path_magnitude_report",
                "血路按击杀数线性加伤(文档口径\"每击杀 +0.1%\"); 并记录极端击杀数的量级 —— 文档未设上限, 故不判失败",
                "线性; 100 万杀的量级写入观测",
                c -> inProbe(level, (w, attacker, target) -> {
                    LevelStubs path = LevelStubs.of(EnchantIds.BLOOD_PATH, 1);
                    double thousand = bonusPct(path, 1000, attacker, target);
                    double million = bonusPct(path, 1000000, attacker, target);
                    c.note("1000 杀 → +" + TestKit.fmt(thousand * 100) + "%; 100 万杀 → +"
                            + TestKit.fmt(million * 100) + "% (×" + TestKit.fmt(1 + million) + ")");
                    c.that(million > thousand, "击杀越多加伤越高");
                    c.eq("100 万杀按公式应为 +100000%", million, 1000.0D, 1.0E-1D);
                }));

        // ---- 止步 ----
        s.addWorld("halt_motionless_boundary",
                "止步: 目标水平速度 < 0.01 时 +40%; 恰好 0.01 不算静止(严格小于)",
                "0→+0.40, 0.01→+0.0",
                c -> inProbe(level, (w, attacker, target) -> {
                    LevelStubs halt = LevelStubs.of(EnchantIds.HALT, 1);
                    TestWorld.freeze(target);
                    c.eq("完全静止", bonusPct(halt, 0, attacker, target), 0.40D, 1.0E-4D);
                    target.setDeltaMovement(0.01D, 0.0D, 0.0D);
                    c.eq("恰好 0.01", bonusPct(halt, 0, attacker, target), 0.0D, 1.0E-4D);
                }));

        // ---- 惨白的午夜 / 不停狩 ----
        s.addWorld("pale_midnight_is_flat_50_percent_on_head",
                "惨白的午夜: 带头盔时无条件 +50%(与攻击者速度/目标状态无关)",
                "头盔 → +0.50",
                c -> inProbe(level, (w, attacker, target) -> {
                    c.eq("有头盔", bonusPct(LevelStubs.helmet(EnchantIds.PALE_MIDNIGHT, 1), 0, attacker, target), 0.50D, 1.0E-4D);
                    c.eq("无头盔", bonusPct(LevelStubs.of(EnchantIds.PALE_MIDNIGHT, 1), 0, attacker, target), 0.0D, 1.0E-4D);
                }));

        s.addWorld("ceaseless_hunt_stack_scaling",
                "不停狩每层 +5%: 0 层 0.0, 5 层 +25%",
                "0 层→0.0, 5 层→0.25",
                c -> inProbe(level, (w, attacker, target) -> {
                    LevelStubs hunt = LevelStubs.of(EnchantIds.CEASELESS_HUNT, 1);
                    EntityDataStorage.getData(attacker).putInt(K_CEASELESS, 0);
                    c.eq("0 层", bonusPct(hunt, 0, attacker, target), 0.0D, 1.0E-4D);
                    EntityDataStorage.getData(attacker).putInt(K_CEASELESS, 5);
                    c.eq("5 层", bonusPct(hunt, 0, attacker, target), 0.25D, 1.0E-4D);
                }));

        s.addWorld("ceaseless_hunt_magnitude_report",
                "不停狩读侧不做层数封顶(封顶只在叠层写入侧): 记录\"状态被写到 100 层\"时的量级, 供判断是否需要读侧加固",
                "记录 100 层 → 加伤量级",
                c -> inProbe(level, (w, attacker, target) -> {
                    LevelStubs hunt = LevelStubs.of(EnchantIds.CEASELESS_HUNT, 1);
                    EntityDataStorage.getData(attacker).putInt(K_CEASELESS, 5);
                    double at5 = bonusPct(hunt, 0, attacker, target);
                    EntityDataStorage.getData(attacker).putInt(K_CEASELESS, 100);
                    double at100 = bonusPct(hunt, 0, attacker, target);
                    c.note("5 层 → +" + TestKit.fmt(at5 * 100) + "%; 100 层 → +" + TestKit.fmt(at100 * 100) + "%"
                            + "(设计上限为 5 层, 读侧无钳制)");
                    c.eq("5 层按设计应为 +25%", at5, 0.25D, TOL);
                    c.that(at100 > at5, "记录: 读侧确实不封顶");
                }));

        // ---- 解放者(乘伤, 0.1~20) ----
        s.addWorld("liberator_curve_and_caps",
                "解放者: 5 秒内 ×0.1(惩罚), 400 秒后 ×20(上限), 中间线性; 结果必须单调不减",
                "0s→0.1, 5s→0.1, 202.5s→10.05, 400s→20.0, 1000s→20.0",
                c -> inProbe(level, (w, attacker, target) -> {
                    LevelStubs lib = LevelStubs.of(EnchantIds.LIBERATOR, 1);
                    c.eq("0s", mult(lib, attacker, target, 0), 0.1D, 1.0E-4D);
                    c.eq("5s", mult(lib, attacker, target, 100), 0.1D, 1.0E-4D);
                    c.eq("202.5s", mult(lib, attacker, target, 4050), 10.05D, 1.0E-2D);
                    c.eq("400s", mult(lib, attacker, target, 8000), 20.0D, 1.0E-3D);
                    c.eq("1000s 上限", mult(lib, attacker, target, 20000), 20.0D, 1.0E-3D);
                }));

        s.addWorld("liberator_under_5_seconds_is_a_penalty",
                "解放者冷却未到 5 秒时是 ×0.1 惩罚 —— 必须确认它不会变成 0 或负数",
                "1s→0.1 且 >0",
                c -> inProbe(level, (w, attacker, target) -> {
                    double value = mult(LevelStubs.of(EnchantIds.LIBERATOR, 1), attacker, target, 20);
                    c.range("惩罚倍率", value, 0.1D, 0.1001D);
                }));

        // ---- 泰坦 ----
        s.addWorld("titan_doubles_only_against_elite",
                "泰坦: 仅对精英/BOSS 额外一次等额伤害(×2); 普通目标必须 ×1",
                "普通→1.0, 带精英标签→2.0",
                c -> inProbe(level, (w, attacker, target) -> {
                    LevelStubs titan = LevelStubs.of(EnchantIds.TITAN, 1);
                    c.eq("普通目标", mult(titan, attacker, target), 1.0D, 1.0E-6D);
                    target.addTag(K_ELITE);
                    c.eq("精英(标签)", mult(titan, attacker, target), 2.0D, 1.0E-6D);
                    target.removeTag(K_ELITE);
                }));

        // ---- 假面的愚者 ----
        s.addWorld("fools_mask_lucky_branch_range",
                "假面的愚者·幸运: 乘伤落在 [1, 3) 区间(1 + r1*r2*2)",
                "lucky=true → 1.0 <= v < 3.0",
                c -> inProbe(level, (w, attacker, target) -> {
                    LevelStubs mask = LevelStubs.helmet(EnchantIds.FOOLS_MASK, 1);
                    EntityDataStorage.getData(attacker).putBoolean(K_FOOLS_LUCKY, true);
                    for (int i = 0; i < 40; i++) {
                        double v = mult(mask, attacker, target);
                        if (v < 1.0D || v >= 3.0D) {
                            c.that(false, "幸运分支越界", "第 " + i + " 次 " + TestKit.fmt(v));
                            break;
                        }
                    }
                    c.that(true, "幸运分支 40 次采样均在 [1,3)");
                }));

        s.addWorld("fools_mask_unlucky_branch_range",
                "假面的愚者·不幸: 乘伤落在 [0.01, 1) 区间(减伤)",
                "lucky=false → 0.01 <= v < 1.0",
                c -> inProbe(level, (w, attacker, target) -> {
                    LevelStubs mask = LevelStubs.helmet(EnchantIds.FOOLS_MASK, 1);
                    EntityDataStorage.getData(attacker).putBoolean(K_FOOLS_LUCKY, false);
                    boolean ok = true;
                    double min = Double.MAX_VALUE;
                    double max = -Double.MAX_VALUE;
                    for (int i = 0; i < 40; i++) {
                        double v = mult(mask, attacker, target);
                        min = Math.min(min, v);
                        max = Math.max(max, v);
                        if (v < 0.01D || v > 1.0D) {
                            ok = false;
                            break;
                        }
                    }
                    c.note("40 次采样区间 [" + TestKit.fmt(min) + ", " + TestKit.fmt(max) + "]");
                    c.that(ok, "不幸分支必须落在 [0.01, 1]");
                }));

        // ---- 孤独的正午 / 燃烧的黄昏 / 哭泣之子 ----
        s.addWorld("lonely_noon_burning_target_multiplier",
                "孤独的正午: 对面着火目标 ×1.5; 同持燃烧的黄昏 → ×2",
                "未着火→1.0, 着火→1.5, 着火+黄昏→2.0",
                c -> inProbe(level, (w, attacker, target) -> {
                    TestWorld.extinguish(target);
                    c.eq("目标未着火", mult(LevelStubs.of(EnchantIds.LONELY_NOON, 1), attacker, target), 1.0D, 1.0E-6D);
                    TestWorld.ignite(target, 200);
                    c.eq("目标着火", mult(LevelStubs.of(EnchantIds.LONELY_NOON, 1), attacker, target), 1.5D, 1.0E-6D);
                    c.eq("着火+黄昏",
                            mult(LevelStubs.of(EnchantIds.LONELY_NOON, 1).mainHand(EnchantIds.BURNING_DUSK, 1), attacker, target),
                            2.0D, 1.0E-6D);
                }));

        s.addWorld("weeping_child_self_burning_multiplier",
                "哭泣之子: 自身燃烧时 ×3; 同时带孤独的正午+燃烧的黄昏 → ×6",
                "自身未着火→1.0, 着火→3.0, 着火+正午+黄昏→6.0",
                c -> inProbe(level, (w, attacker, target) -> {
                    TestWorld.extinguish(attacker);
                    c.eq("未着火", mult(LevelStubs.of(EnchantIds.WEEPING_CHILD, 1), attacker, target), 1.0D, 1.0E-6D);
                    TestWorld.ignite(attacker, 200);
                    c.eq("自身着火", mult(LevelStubs.of(EnchantIds.WEEPING_CHILD, 1), attacker, target), 3.0D, 1.0E-6D);
                    LevelStubs combo = LevelStubs.of(EnchantIds.WEEPING_CHILD, 1)
                            .mainHand(EnchantIds.LONELY_NOON, 1)
                            .mainHand(EnchantIds.BURNING_DUSK, 1);
                    c.eq("三件套", mult(combo, attacker, target), 6.0D, 1.0E-6D);
                }));

        s.addWorld("rhythm_flag_is_consumed_once",
                "节奏: 命中标志存在时 ×1.5, 且必须被读取后清除(第二次调用不得再乘)",
                "第1次→1.5, 第2次→1.0",
                c -> inProbe(level, (w, attacker, target) -> {
                    LevelStubs any = LevelStubs.none();
                    EntityDataStorage.getEntityData(attacker).putBoolean(K_RHYTHM, true);
                    c.eq("首次命中", mult(any, attacker, target), 1.5D, 1.0E-6D);
                    c.eq("标志已消费", mult(any, attacker, target), 1.0D, 1.0E-6D);
                }));

        s.addWorld("snow_wound_combo_branch_only",
                "雪的伤: 雪天那一半按 README.md:648 改走 Apothic CRIT_DAMAGE(+50%), 事件乘伤通道只剩\"与雪的殇同附魔\"的冰霜增伤 ×1.5",
                "雪天+雪的伤→1.0(乘伤通道无贡献), 雪天+雪的伤+雪的殇→1.5",
                c -> inProbe(level, (w, attacker, target) -> {
                    boolean raining = attacker.level().isRaining();
                    if (!raining) {
                        c.skip("当前非雨天: 需先执行 /weather rain 并等待 rainLevel 上升后重跑本用例");
                        return;
                    }
                    c.eq("雪的伤", mult(LevelStubs.of(EnchantIds.SNOW_WOUND, 1), attacker, target), 1.0D, 1.0E-6D);
                    c.eq("雪的伤+雪的殇",
                            mult(LevelStubs.of(EnchantIds.SNOW_WOUND, 1).mainHand(EnchantIds.SNOW_SORROW, 1), attacker, target),
                            1.5D, 1.0E-6D);
                }));

        s.addWorld("bone_break_is_flat_six_times",
                "骨断: 主手持该武器时无条件 ×6(走 tick 乘伤聚合, 与目标状态无关)",
                "主手 lvl1 → 6.0, 无 → 1.0",
                c -> inProbe(level, (w, attacker, target) -> {
                    LevelStubs none = LevelStubs.none();
                    c.eq("无骨断", EventDamageConditions.tickMultiplierProduct(damageCtx(none, 0), attacker), 1.0D, 1.0E-6D);
                    LevelStubs bone = LevelStubs.of(EnchantIds.BONE_BREAK, 1);
                    c.eq("有骨断", EventDamageConditions.tickMultiplierProduct(damageCtx(bone, 0), attacker), 6.0D, 1.0E-6D);
                }));

        // ---- 无附魔时的恒等性(最重要的负例) ----
        s.addWorld("no_enchantment_is_identity",
                "零附魔时两个规则函数必须是恒等元(加伤 0.0 / 乘伤 1.0) —— 保证规则不会给普通玩家带来任何影响",
                "无附魔 → bonus=0.0, mult=1.0",
                c -> inProbe(level, (w, attacker, target) -> {
                    LevelStubs none = LevelStubs.none();
                    c.eq("加伤恒等", bonusPct(none, 0, attacker, target), 0.0D, 1.0E-6D);
                    c.eq("乘伤恒等", mult(none, attacker, target), 1.0D, 1.0E-6D);
                    c.eq("tick 乘伤恒等", EventDamageConditions.tickMultiplierProduct(damageCtx(none, 0), attacker), 1.0D, 1.0E-6D);
                }));

        s.addWorld("all_rules_finite_for_sane_input",
                "汇总不变量: 对全部 91 个 id 各以 1..3 级喂给两个规则函数, 在正常实体状态下结果必须全部有限且非 NaN",
                "91 id × 3 级 × 2 函数 = 546 次调用全部有限",
                c -> inProbe(level, (w, attacker, target) -> {
                    TestWorld.horizontalSpeed(attacker, 0.1D, 0.0D);
                    int checked = 0;
                    int bad = 0;
                    for (String id : MetaSuites.allIds()) {
                        for (int lvl = 1; lvl <= 3; lvl++) {
                            LevelStubs stub = LevelStubs.of(id, lvl);
                            double bonus = bonusPct(stub, 100, attacker, target);
                            double multiplier = mult(stub, attacker, target);
                            checked += 2;
                            if (!Double.isFinite(bonus) || !Double.isFinite(multiplier)) {
                                bad++;
                            }
                        }
                    }
                    c.note("调用 " + checked + " 次, 非有限 " + bad + " 次");
                    c.eq("非有限结果数", bad, 0);
                }));

        return s;
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    /** 需要"攻击者+目标"两个真实活体的用例体。 */
    private interface Probe {
        void run(TestWorld world, Zombie attacker, Zombie target) throws Exception;
    }

    private static void inProbe(ServerLevel level, Probe probe) throws Exception {
        try (TestWorld world = new TestWorld(level, level.getSharedSpawnPos())) {
            Zombie attacker = world.zombie();
            Zombie target = world.zombie();
            TestWorld.stripGear(attacker);
            TestWorld.stripGear(target);
            EntityDataStorage.removeData(attacker);
            EntityDataStorage.removeData(target);
            probe.run(world, attacker, target);
        }
    }

    private static EventDamageContext damageCtx(EnchantmentLevelLookup levels, int kills) {
        return new EventDamageContext(levels, (a, d) -> kills,
                K_LIBERATOR, K_FOOLS_LUCKY, K_RHYTHM, K_CEASELESS, K_ELITE);
    }

    private static double bonusPct(EnchantmentLevelLookup levels, int kills,
                                   LivingEntity attacker, LivingEntity defender) {
        return EventDamageConditions.computeBonusPercent(damageCtx(levels, kills), attacker, defender);
    }

    private static double mult(EnchantmentLevelLookup levels, LivingEntity attacker, LivingEntity defender) {
        return EventDamageConditions.computeConditionalMultiplier(damageCtx(levels, 0), attacker, defender);
    }

    /** 预置"上次攻击"时间, 使解放者的时间差正好等于 elapsedTicks。 */
    private static double mult(EnchantmentLevelLookup levels, LivingEntity attacker, LivingEntity defender,
                              long elapsedTicks) {
        long now = attacker.level().getGameTime();
        long seed = now - elapsedTicks;
        if (seed == 0L) {
            seed = -1L;
        }
        EntityDataStorage.getData(attacker).putLong(K_LIBERATOR, seed);
        return mult(levels, attacker, defender);
    }

    private static LivingEntity atY(TestWorld world, Zombie zombie, double y) {
        zombie.setPos(zombie.getX(), y, zombie.getZ());
        return zombie;
    }

    /** 与生产实现同源的状态键(仅用于规则用例的自有键, 见类注释)。 */
    private static FakePlayer freshFakePlayer(ServerLevel level) {
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        clearFakeInventory(player);
        // 先快照 key 集合再删: 直接遍历 getAllKeys() 的同时 remove 会 ConcurrentModificationException
        java.util.List<String> stale = new java.util.ArrayList<>(player.getPersistentData().getAllKeys());
        for (String key : stale) {
            if (key.startsWith("zhonz_")) {
                player.getPersistentData().remove(key);
            }
        }
        EntityDataStorage.removeData(player);
        return player;
    }

    private static void clearFakeInventory(FakePlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            player.getInventory().setItem(i, ItemStack.EMPTY);
        }
    }
}
