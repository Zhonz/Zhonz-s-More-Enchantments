package com.zhonz.moreenchantments.command.test;

import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 测试套件登记处(round-testkit)。
 *
 * <p>套件每次执行时现场构建, 因为世界类用例需要拿到当前的 {@link ServerLevel};
 * 纯函数用例(如结算公式)不依赖世界, 无世界时其 {@code needsWorld=false}
 * 会照常执行, 需要世界的用例则自动记 SKIP 而不是报错。
 *
 * <p>新增套件只需在 {@link #all(ServerLevel)} 里加一行 —— 命令层不必改动。
 */
public final class TestRegistry {

    private TestRegistry() {
    }

    /** 全部套件。 */
    public static List<TestKit.Suite> all(ServerLevel level) {
        List<TestKit.Suite> suites = new ArrayList<>();
        suites.addAll(MathSuites.all());
        suites.addAll(MetaSuites.all(level));
        suites.addAll(RuleSuites.all(level));
        suites.addAll(DamageTypeSuites.all(level));
        suites.addAll(EternalReturnSuites.all(level));
        suites.addAll(AuditRegressionSuites.all(level));
        return suites;
    }

    /** "套件名(用例数)" 清单, 供 /zhonztest list 展示。 */
    public static List<String> names(ServerLevel level) {
        List<String> names = new ArrayList<>();
        for (TestKit.Suite suite : all(level)) {
            names.add(suite.name + "(" + suite.size() + ")");
        }
        return names;
    }

    /** 用例总数。 */
    public static int totalCases(ServerLevel level) {
        int total = 0;
        for (TestKit.Suite suite : all(level)) {
            total += suite.size();
        }
        return total;
    }

    /**
     * 按选择器选套件: 先精确匹配套件名, 未命中再按子串匹配。
     * 这样 {@code /zhonztest run meta} 一次跑三个 meta-* 套件, 而
     * {@code run math-attack} 只跑一个。
     */
    public static List<TestKit.Suite> select(ServerLevel level, String selector) {
        List<TestKit.Suite> all = all(level);
        List<TestKit.Suite> exact = new ArrayList<>();
        for (TestKit.Suite suite : all) {
            if (suite.name.equalsIgnoreCase(selector)) {
                exact.add(suite);
            }
        }
        if (!exact.isEmpty()) {
            return exact;
        }
        String needle = selector.toLowerCase(Locale.ROOT);
        List<TestKit.Suite> partial = new ArrayList<>();
        for (TestKit.Suite suite : all) {
            if (suite.name.toLowerCase(Locale.ROOT).contains(needle)) {
                partial.add(suite);
            }
        }
        return partial;
    }
}
