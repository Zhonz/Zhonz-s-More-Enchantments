package com.zhonz.moreenchantments.command.test;

import com.zhonz.moreenchantments.common.damage.UnifiedDamageEngine;

/**
 * 纯数值层测试(不需要世界/实体)。
 *
 * <p>被测算式是 {@link UnifiedDamageEngine} 的两个纯函数, 文档口径:
 * <ul>
 *   <li>攻击侧: {@code final = amount × (1 + bonus) × mult + flat}</li>
 *   <li>受击侧: {@code final = amount × incoming}</li>
 * </ul>
 *
 * <p>这里既覆盖正常数值, 也覆盖边界与异常输入: 0 伤害、-100% 加伤、
 * 低于 -100% 的加伤(是否钳制)、NaN / Infinity / 极大值的传播与溢出。
 * 这些用例的价值在于: 一旦有人"顺手"给引擎加了钳制或改了乘算顺序,
 * 报错会精确指出是哪个输入下的哪个公式项。
 */
public final class MathSuites {

    private MathSuites() {
    }

    public static final String SUITE_ATTACK = "math-attack";
    public static final String SUITE_INCOMING = "math-incoming";

    /** 浮点比较容差: 引擎内部把 double 转 float, 放 1e-3 足够。 */
    private static final double TOL = 1.0E-3D;

    // ==================================================================
    // 攻击侧: amount × (1+bonus) × mult + flat
    // ==================================================================

    public static TestKit.Suite attackMath() {
        TestKit.Suite suite = new TestKit.Suite(SUITE_ATTACK,
                "统一增伤结算 settle(): 三通道公式、通道独立性、边界与异常输入");

        suite.add("identity_early_return",
                "三通道全默认(bonus=0, mult=1, flat=0)时必须原值返回, 不允许任何浮点漂移",
                "settle(10, 0, 1, 0) == 10.0 精确相等",
                c -> c.eq("settle(10, 0, 1, 0)", UnifiedDamageEngine.settle("t", 10.0F, 0.0D, 1.0D, 0.0D), 10.0D, 0.0D));

        suite.add("identity_large_amount",
                "默认通道下大数值也必须逐位不变(早返回路径)",
                "settle(123456.789, 0, 1, 0) == 123456.789",
                c -> c.eq("大数值默认通道", UnifiedDamageEngine.settle("t", 123456.789F, 0.0D, 1.0D, 0.0D),
                        123456.789F, TOL));

        suite.add("bonus_only",
                "只有加伤层时: +50% → ×1.5",
                "settle(10, 0.5, 1, 0) == 15.0",
                c -> c.eq("仅 bonus=0.5", UnifiedDamageEngine.settle("t", 10.0F, 0.5D, 1.0D, 0.0D), 15.0D, TOL));

        suite.add("bonus_additive_in_one_channel",
                "加伤层是加法语义: 多个附魔各写 modifier 后属性值 0.2+0.3 → ×1.5 而非 1.2×1.3",
                "settle(100, 0.5, 1, 0) == 150.0 (不是 156.0)",
                c -> c.eq("加伤加法语义", UnifiedDamageEngine.settle("t", 100.0F, 0.5D, 1.0D, 0.0D), 150.0D, TOL));

        suite.add("mult_only",
                "只有乘伤层时: ×2",
                "settle(10, 0, 2, 0) == 20.0",
                c -> c.eq("仅 mult=2", UnifiedDamageEngine.settle("t", 10.0F, 0.0D, 2.0D, 0.0D), 20.0D, TOL));

        suite.add("mult_multiplicative_semantics",
                "乘伤层是乘法语义: 两个 ×1.5 应聚合成 mult=2.25 → ×2.25 而非 ×2.0",
                "settle(100, 0, 2.25, 0) == 225.0",
                c -> c.eq("乘伤乘法语义", UnifiedDamageEngine.settle("t", 100.0F, 0.0D, 2.25D, 0.0D), 225.0D, TOL));

        suite.add("flat_only",
                "只有固定加伤层时: 直接加绝对量",
                "settle(10, 0, 1, 3) == 13.0",
                c -> c.eq("仅 flat=3", UnifiedDamageEngine.settle("t", 10.0F, 0.0D, 1.0D, 3.0D), 13.0D, TOL));

        suite.add("flat_is_last_and_unscaled",
                "公式顺序: flat 必须最后加且不被百分比/乘算放大",
                "settle(10, 1, 2, 3) == 43.0 (若 flat 被乘算则会是 46 或 63)",
                c -> c.eq("flat 不被缩放", UnifiedDamageEngine.settle("t", 10.0F, 1.0D, 2.0D, 3.0D), 43.0D, TOL));

        suite.add("three_channels_combined",
                "三通道同时生效时的复合结果",
                "settle(10, 0.2, 2, 3) == 10×1.2×2+3 == 27.0",
                c -> c.eq("三通道复合", UnifiedDamageEngine.settle("t", 10.0F, 0.2D, 2.0D, 3.0D), 27.0D, TOL));

        suite.add("zero_damage_stays_flat_only",
                "0 基础伤害时, 百分比与乘算都放大不出伤害, 只有 flat 能产生数值",
                "settle(0, 5, 5, 5) == 5.0",
                c -> c.eq("0 伤害 + flat", UnifiedDamageEngine.settle("t", 0.0F, 5.0D, 5.0D, 5.0D), 5.0D, TOL));

        suite.add("negative_base_damage",
                "基础伤害为负(异常输入)时按同一公式线性处理, 不出现 NaN",
                "settle(-10, 0.5, 2, 0) == -30.0",
                c -> {
                    double actual = UnifiedDamageEngine.settle("t", -10.0F, 0.5D, 2.0D, 0.0D);
                    c.eq("负基础伤害", actual, -30.0D, TOL);
                    c.finite("负基础伤害有限性", actual);
                });

        suite.add("curse_minus_90_percent",
                "诅咒类 -90% 加伤(self_bound 口径)后仍为正伤害",
                "settle(10, -0.9, 1, 0) == 1.0",
                c -> c.eq("-90% 加伤", UnifiedDamageEngine.settle("t", 10.0F, -0.9D, 1.0D, 0.0D), 1.0D, TOL));

        suite.add("bonus_exactly_minus_100_percent",
                "-100% 加伤的契约边界: 伤害必须恰为 0, 不允许出现 -0.0 或微小负数",
                "settle(10, -1.0, 1, 0) == 0.0",
                c -> c.eq("-100% 加伤", UnifiedDamageEngine.settle("t", 10.0F, -1.0D, 1.0D, 0.0D), 0.0D, TOL));

        suite.add("bonus_below_minus_100_percent",
                "低于 -100% 的加伤: 记录引擎是否会产生负伤害(负伤害会变成治疗, 属高危)",
                "settle(10, -2.0, 1, 0) 记录实测值, 断言其有限且等于 -10.0(无钳制契约)",
                c -> {
                    double actual = UnifiedDamageEngine.settle("t", 10.0F, -2.0D, 1.0D, 0.0D);
                    c.note("低于 -100% 实测 " + TestKit.fmt(actual));
                    c.eq("无钳制: 线性外推", actual, -10.0D, TOL);
                    c.that(actual < 0.0D, "确认存在负伤害区间", "调用方需自行保证 bonus >= -1");
                });

        suite.add("flat_can_cancel_negative_bonus",
                "flat 是绝对量, 可以在负加伤后把伤害拉回正值",
                "settle(10, -1.0, 1, 5) == 5.0",
                c -> c.eq("flat 抵消负加伤", UnifiedDamageEngine.settle("t", 10.0F, -1.0D, 1.0D, 5.0D), 5.0D, TOL));

        suite.add("nan_bonus_propagates_nan",
                "NaN 加伤必须显式传播为 NaN, 不能被静默吞成 0(否则异常会被掩盖成'没加成')",
                "settle(10, NaN, 1, 0) 结果为 NaN",
                c -> {
                    double actual = UnifiedDamageEngine.settle("t", 10.0F, Double.NaN, 1.0D, 0.0D);
                    c.that(Double.isNaN(actual), "NaN 加伤 → NaN 结果", "实际 " + TestKit.fmt(actual));
                });

        suite.add("nan_flat_propagates_nan",
                "NaN flat 同样必须显式传播",
                "settle(10, 0, 1, NaN) 结果为 NaN",
                c -> c.that(Double.isNaN(UnifiedDamageEngine.settle("t", 10.0F, 0.0D, 1.0D, Double.NaN)),
                        "NaN flat → NaN 结果"));

        suite.add("infinite_base_saturates_not_nan",
                "基础伤害已是 Infinity 时结果保持 Infinity, 不允许变 NaN(Inf×0 会变 NaN)",
                "settle(+Inf, 0, 1, 0) == +Inf",
                c -> {
                    double actual = UnifiedDamageEngine.settle("t", Float.POSITIVE_INFINITY, 0.0D, 1.0D, 0.0D);
                    c.that(Double.isInfinite(actual) && actual > 0, "Infinity 保持 Infinity", "实际 " + TestKit.fmt(actual));
                    c.that(!Double.isNaN(actual), "Infinity 不变 NaN");
                });

        suite.add("float_overflow_documented",
                "极大数值×极大乘数会溢出为 Infinity —— 记录溢出点, 断言不会变成 NaN 或负数",
                "settle(1e30, 1e9, 1, 0) == +Inf(饱和) 且非 NaN",
                c -> {
                    double actual = UnifiedDamageEngine.settle("t", 1.0E30F, 1.0E9D, 1.0D, 0.0D);
                    c.note("1e30×(1+1e9) 实测 " + TestKit.fmt(actual));
                    c.that(!Double.isNaN(actual), "溢出不得变为 NaN");
                    c.that(actual > 0, "溢出后符号必须为正", "实际 " + TestKit.fmt(actual));
                });

        suite.add("finale_scale_still_finite",
                "终结(finale)文档为 100000 倍: 常规武器伤害放大 10 万倍后必须仍为有限值",
                "settle(8, 99999, 1, 0) == 800000 量级且有限",
                c -> {
                    double actual = UnifiedDamageEngine.settle("t", 8.0F, 99999.0D, 1.0D, 0.0D);
                    c.finite("10 万倍放大", actual);
                    c.eq("10 万倍放大数值", actual, 800000.0D, 1.0D);
                });

        suite.add("precision_dirty_float",
                "非整数基础伤害 + 非整数百分比时的浮点精度(容差内)",
                "settle(33.33, 0.1, 1, 0) ≈ 36.663",
                c -> c.eq("脏浮点", UnifiedDamageEngine.settle("t", 33.33F, 0.1D, 1.0D, 0.0D), 36.663D, 5.0E-3D));

        suite.add("tiny_threshold_is_not_identity",
                "引擎的早返回条件是精确 0/1, 不是\"小于某个 epsilon\": 可表示的小额加伤必须生效",
                "settle(100, 0.001, 1, 0) == 100.1",
                c -> c.eq("0.1% 加伤", UnifiedDamageEngine.settle("t", 100.0F, 0.001D, 1.0D, 0.0D), 100.1D, 5.0E-3D));

        suite.add("sub_float_epsilon_bonus_is_absorbed",
                "低于 float 精度的小额加伤会被浮点吸收(记录该行为: 不是引擎吞掉了它, 而是 100f*(1+1e-9) 在 float 下仍等于 100f)",
                "settle(100, 1e-9, 1, 0) == 100.0",
                c -> {
                    double actual = UnifiedDamageEngine.settle("t", 100.0F, 1.0E-9D, 1.0D, 0.0D);
                    c.note("1e-9 加伤实测 " + TestKit.fmt(actual));
                    c.eq("亚精度加伤被吸收", actual, 100.0D, 0.0D);
                });

        return suite;
    }

    // ==================================================================
    // 受击侧: amount × incoming
    // ==================================================================

    public static TestKit.Suite incomingMath() {
        TestKit.Suite suite = new TestKit.Suite(SUITE_INCOMING,
                "受击结算 settleIncoming(): 易伤/减伤通道、边界与异常输入");

        suite.add("identity_early_return",
                "incoming=1(默认)时原值返回, 不允许浮点漂移",
                "settleIncoming(40, 1.0) == 40.0 精确相等",
                c -> c.eq("incoming=1", UnifiedDamageEngine.settleIncoming("d", 40.0F, 1.0D), 40.0D, 0.0D));

        suite.add("halving",
                "减伤 50%(困兽之斗低血口径) → 减半",
                "settleIncoming(40, 0.5) == 20.0",
                c -> c.eq("0.5 减伤", UnifiedDamageEngine.settleIncoming("d", 40.0F, 0.5D), 20.0D, TOL));

        suite.add("vulnerability_270",
                "先知 ×2.7 易伤口径",
                "settleIncoming(40, 2.7) == 108.0",
                c -> c.eq("2.7 易伤", UnifiedDamageEngine.settleIncoming("d", 40.0F, 2.7D), 108.0D, TOL));

        suite.add("zero_incoming_negates_all",
                "incoming=0 时免疫一切伤害",
                "settleIncoming(40, 0.0) == 0.0",
                c -> c.eq("incoming=0 免疫", UnifiedDamageEngine.settleIncoming("d", 40.0F, 0.0D), 0.0D, TOL));

        suite.add("zero_damage_with_huge_incoming",
                "0 伤害在任何易伤倍率下都必须是 0(不会被抬起来)",
                "settleIncoming(0, 1e9) == 0.0",
                c -> c.eq("0 伤害", UnifiedDamageEngine.settleIncoming("d", 0.0F, 1.0E9D), 0.0D, TOL));

        suite.add("negative_incoming_would_heal",
                "负 incoming(属性下限为 0, 属异常注入)会产生负伤害, 记录该行为",
                "settleIncoming(40, -1) == -40.0 且有限",
                c -> {
                    double actual = UnifiedDamageEngine.settleIncoming("d", 40.0F, -1.0D);
                    c.note("负 incoming 实测 " + TestKit.fmt(actual));
                    c.eq("负 incoming 线性处理", actual, -40.0D, TOL);
                });

        suite.add("nan_incoming_propagates",
                "NaN incoming 必须显式传播",
                "settleIncoming(40, NaN) 结果为 NaN",
                c -> c.that(Double.isNaN(UnifiedDamageEngine.settleIncoming("d", 40.0F, Double.NaN)),
                        "NaN incoming → NaN 结果"));

        suite.add("infinite_incoming_saturates",
                "incoming=+Inf 时结果为 Infinity 而不是 NaN(0 伤害除外)",
                "settleIncoming(40, +Inf) == +Inf",
                c -> {
                    double actual = UnifiedDamageEngine.settleIncoming("d", 40.0F, Double.POSITIVE_INFINITY);
                    c.that(Double.isInfinite(actual) && actual > 0, "Infinity 保持 Infinity");
                });

        suite.add("stacked_reduction_is_product_not_sum",
                "减伤聚合是乘积(0.5×0.5)而非加和(0.5+0.5=1 变成免疫)",
                "settleIncoming(40, 0.25) == 10.0",
                c -> c.eq("乘积式减伤", UnifiedDamageEngine.settleIncoming("d", 40.0F, 0.25D), 10.0D, TOL));

        suite.add("armor_preceded_order",
                "文档口径: incoming 必须在护甲/保护结算之后乘一次 —— 纯函数层表现为对「已结算伤害」线性缩放",
                "settleIncoming(7.5, 0.5) == 3.75",
                c -> c.eq("护甲后缩放", UnifiedDamageEngine.settleIncoming("d", 7.5F, 0.5D), 3.75D, TOL));

        return suite;
    }

    /** 本文件提供的全部套件。 */
    public static java.util.List<TestKit.Suite> all() {
        return java.util.List.of(attackMath(), incomingMath());
    }
}
