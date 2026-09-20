package com.zhonz.moreenchantments.command.test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 本模组统一测试装置(round-testkit)。
 *
 * <p>设计目标: 取代原本"每个测试方法自己拼 StringBuilder 报告"的做法,
 * 把「用例定义 / 断言 / 执行 / 汇总」四件事分开, 使:
 * <ul>
 *   <li>用例可以按套件(suite)分组, 支持过滤, 支持逐条 PASS/FAIL/SKIP/ERROR;</li>
 *   <li>断言失败时报告「期望值 vs 实际值」, 而不是只有一句"不匹配";</li>
 *   <li>汇总行机器可解析(供 RCON / 日志脚本抓取): {@code [zhonztest] SUMMARY ...};</li>
 *   <li>不依赖任何 Minecraft/NeoForge 类型 —— 纯规则与注册表用例可在无世界时运行,
 *       需要世界的用例用 {@link Case#needsWorld} 标记, 无世界时自动 SKIP 而非报错。</li>
 * </ul>
 *
 * <p>本类刻意不 import 任何 Minecraft 类型: 跨版本(1.21.1 / 1.20.1)可原样复制复用,
 * 平台相关部分(实体生成、附魔赋予、伤害施加)由调用方在 {@link Case#body} 里自行使用。
 */
public final class TestKit {

    private TestKit() {
    }

    // ==================================================================
    // 状态
    // ==================================================================

    /** 一条用例的终态。 */
    public enum Status {
        /** 全部断言成立。 */
        PASS,
        /** 至少一条断言不成立。 */
        FAIL,
        /** 前置条件不满足, 未执行断言(不计入失败)。 */
        SKIP,
        /** 用例体抛异常 —— 这本身就是缺陷, 单独记, 不与 FAIL 混淆。 */
        ERROR
    }

    // ==================================================================
    // 断言收集器
    // ==================================================================

    /**
     * 断言收集器。一条用例用自己的 {@code Check} 实例记录全部断言,
     * 只要有一条不成立, 该用例判 FAIL; 抛异常判 ERROR; 显式 {@link #skip} 判 SKIP。
     */
    public static final class Check {

        private final List<String> failures = new ArrayList<>();
        private final List<String> notes = new ArrayList<>();
        private int assertions;
        private boolean skipped;
        private String skipReason = "";

        /** 布尔断言。 */
        public Check that(boolean condition, String label) {
            assertions++;
            if (!condition) {
                failures.add(label);
            }
            return this;
        }

        /** 布尔断言, 失败时附带原因。 */
        public Check that(boolean condition, String label, String why) {
            assertions++;
            if (!condition) {
                failures.add(label + " (" + why + ")");
            }
            return this;
        }

        /** 非空断言。 */
        public Check notNull(String label, Object value) {
            return that(value != null, label + " 非空");
        }

        /** 对象相等断言。 */
        public Check eq(String label, Object actual, Object expected) {
            assertions++;
            boolean ok = expected == null ? actual == null : expected.equals(actual);
            if (!ok) {
                failures.add(label + ": 期望 " + expected + ", 实际 " + actual);
            }
            return this;
        }

        /** 浮点相等断言(绝对容差)。 */
        public Check eq(String label, double actual, double expected, double tolerance) {
            assertions++;
            if (Double.isNaN(actual) || Double.isNaN(expected)) {
                failures.add(label + ": 出现 NaN (期望 " + fmt(expected) + ", 实际 " + fmt(actual) + ")");
                return this;
            }
            if (Math.abs(actual - expected) > tolerance) {
                failures.add(label + ": 期望 " + fmt(expected) + "±" + fmt(tolerance)
                        + ", 实际 " + fmt(actual) + " (差 " + fmt(actual - expected) + ")");
            }
            return this;
        }

        /** 相对误差断言(用于乘算链, 允许 1e-4 相对误差以上放宽)。 */
        public Check near(String label, double actual, double expected, double relativeTolerance) {
            assertions++;
            if (!Double.isFinite(actual)) {
                failures.add(label + ": 实际值非有限 (" + fmt(actual) + "), 期望 ≈" + fmt(expected));
                return this;
            }
            double scale = Math.max(Math.abs(expected), 1.0D);
            double tolerance = scale * relativeTolerance;
            if (Math.abs(actual - expected) > tolerance) {
                failures.add(label + ": 期望 ≈" + fmt(expected) + " (相对 ±" + fmt(relativeTolerance)
                        + "), 实际 " + fmt(actual));
            }
            return this;
        }

        /** 区间断言(闭区间)。 */
        public Check range(String label, double actual, double min, double max) {
            assertions++;
            if (Double.isNaN(actual) || actual < min || actual > max) {
                failures.add(label + ": 期望落在 [" + fmt(min) + ", " + fmt(max) + "], 实际 " + fmt(actual));
            }
            return this;
        }

        /** 有限性断言 —— 数值异常测试的常用断言(NaN / ±Infinity 一律判失败)。 */
        public Check finite(String label, double value) {
            assertions++;
            if (!Double.isFinite(value)) {
                failures.add(label + ": 期望有限值, 实际 " + fmt(value));
            }
            return this;
        }

        /** 非负断言。 */
        public Check notNegative(String label, double value) {
            assertions++;
            if (Double.isNaN(value) || value < 0.0D) {
                failures.add(label + ": 期望 >=0, 实际 " + fmt(value));
            }
            return this;
        }

        /** 标记跳过(不再执行后续断言)。 */
        public Check skip(String reason) {
            this.skipped = true;
            this.skipReason = reason;
            return this;
        }

        /** 记录实际观测值, 便于失败时定位(不参与判定)。 */
        public Check note(String message) {
            if (notes.size() < 12) {
                notes.add(message);
            }
            return this;
        }

        public boolean failed() {
            return !failures.isEmpty();
        }

        public boolean skipped() {
            return skipped;
        }

        public List<String> failures() {
            return failures;
        }

        public int assertions() {
            return assertions;
        }

        /** 失败明细 / 跳过原因 / 观测记录, 拼成单行。 */
        public String detail() {
            if (skipped) {
                return "SKIP: " + skipReason;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < failures.size(); i++) {
                if (i > 0) {
                    sb.append(" | ");
                }
                sb.append(failures.get(i));
            }
            if (!notes.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(" | ");
                }
                sb.append("观测: ").append(String.join("; ", notes));
            }
            return sb.toString();
        }
    }

    // ==================================================================
    // 用例 / 套件
    // ==================================================================

    /** 用例体。 */
    @FunctionalInterface
    public interface Body {
        void run(Check check) throws Exception;
    }

    /** 一条用例。 */
    public static final class Case {

        public final String suite;
        public final String id;
        /** 这条用例想证明什么。 */
        public final String desc;
        /** 期望值的文字口径(FAIL 时与实测一起展示)。 */
        public final String expected;
        /** 是否需要服务端世界/实体(tick、伤害、光照等)。 */
        public final boolean needsWorld;
        public final Body body;

        Case(String suite, String id, String desc, String expected, boolean needsWorld, Body body) {
            this.suite = suite;
            this.id = id;
            this.desc = desc;
            this.expected = expected;
            this.needsWorld = needsWorld;
            this.body = body;
        }

        public String fullId() {
            return suite + "/" + id;
        }
    }

    /** 一个测试套件。 */
    public static final class Suite {

        public final String name;
        public final String desc;
        private final List<Case> cases = new ArrayList<>();

        public Suite(String name, String desc) {
            this.name = name;
            this.desc = desc;
        }

        /** 追加一条纯规则用例(无世界也能跑)。 */
        public Suite add(String id, String desc, String expected, Body body) {
            cases.add(new Case(name, id, desc, expected, false, body));
            return this;
        }

        /** 追加一条需要世界的用例(无世界时自动 SKIP)。 */
        public Suite addWorld(String id, String desc, String expected, Body body) {
            cases.add(new Case(name, id, desc, expected, true, body));
            return this;
        }

        public List<Case> cases() {
            return cases;
        }

        public int size() {
            return cases.size();
        }
    }

    // ==================================================================
    // 执行 / 汇总
    // ==================================================================

    /** 汇总计数。 */
    public static final class Summary {

        public int cases;
        public int pass;
        public int fail;
        public int skip;
        public int error;

        public boolean green() {
            return fail == 0 && error == 0;
        }

        /** 机器可解析汇总行(供 RCON / 日志抓取)。 */
        public String line() {
            return "[zhonztest] SUMMARY cases=" + cases + " pass=" + pass + " fail=" + fail
                    + " skip=" + skip + " error=" + error + " RESULT=" + (green() ? "PASS" : "FAIL");
        }
    }

    /** 执行器: 跑套件、逐条输出、汇总。 */
    public static final class Runner {

        private final Consumer<String> sink;
        private final boolean worldAvailable;
        private boolean verbose;

        /**
         * @param sink           报告输出口(命令反馈 / 服务端日志)
         * @param worldAvailable 当前是否处于"有服务端世界"的环境(命令由玩家/控制台执行时为 true)
         */
        public Runner(Consumer<String> sink, boolean worldAvailable) {
            this.sink = sink;
            this.worldAvailable = worldAvailable;
        }

        /** 输出每条用例(含 PASS)。默认只输出 FAIL/SKIP/ERROR + 汇总。 */
        public Runner verbose(boolean value) {
            this.verbose = value;
            return this;
        }

        public boolean verbose() {
            return verbose;
        }

        public Consumer<String> sink() {
            return sink;
        }

        public boolean worldAvailable() {
            return worldAvailable;
        }

        /** 按套件名/用例 id 子串过滤后执行。 */
        public Summary run(List<Suite> suites, String filter) {
            Summary summary = new Summary();
            for (Suite suite : suites) {
                runSuite(suite, filter, summary);
            }
            return summary;
        }

        public Summary runSuite(Suite suite, String filter, Summary summary) {
            int suitePass = 0;
            int suiteFail = 0;
            int suiteSkip = 0;
            int suiteError = 0;
            int suiteCases = 0;

            for (Case testCase : suite.cases()) {
                if (filter != null && !filter.isBlank()) {
                    String needle = filter.toLowerCase();
                    if (!testCase.fullId().toLowerCase().contains(needle)
                            && !testCase.desc.toLowerCase().contains(needle)) {
                        continue;
                    }
                }
                suiteCases++;
                Check check = new Check();
                Status status;
                if (testCase.needsWorld && !worldAvailable) {
                    check.skip("需要服务端世界");
                    status = Status.SKIP;
                } else {
                    try {
                        testCase.body.run(check);
                        if (check.skipped()) {
                            status = Status.SKIP;
                        } else if (check.failed()) {
                            status = Status.FAIL;
                        } else {
                            status = Status.PASS;
                        }
                    } catch (Throwable t) {
                        status = Status.ERROR;
                        check.note(t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
                    }
                }

                summary.cases++;
                switch (status) {
                    case PASS -> {
                        suitePass++;
                        summary.pass++;
                    }
                    case FAIL -> {
                        suiteFail++;
                        summary.fail++;
                    }
                    case SKIP -> {
                        suiteSkip++;
                        summary.skip++;
                    }
                    case ERROR -> {
                        // ERROR 单独计数, 不与 FAIL 混算, 保证 pass+fail+skip+error == cases
                        suiteError++;
                        summary.error++;
                    }
                }

                boolean interesting = status != Status.PASS || verbose;
                if (interesting) {
                    String line = "[zhonztest] " + status + " " + testCase.fullId()
                            + " — " + testCase.desc;
                    if (status == Status.FAIL || status == Status.ERROR) {
                        line += " | 期望: " + testCase.expected + " | " + check.detail();
                    } else if (status == Status.SKIP) {
                        line += " | " + check.detail();
                    }
                    sink.accept(line);
                }
            }

            sink.accept("[zhonztest] SUITE " + suite.name + " cases=" + suiteCases
                    + " pass=" + suitePass + " fail=" + suiteFail
                    + " skip=" + suiteSkip + " error=" + suiteError);
            return summary;
        }
    }

    // ==================================================================
    // 工具
    // ==================================================================

    /** 数值格式化: 去掉多余尾零, NaN/Infinity 直接打印, 便于失败报告阅读。 */
    public static String fmt(double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "+Inf" : "-Inf";
        }
        if (value == Math.rint(value) && Math.abs(value) < 1.0E9D) {
            return String.valueOf((long) value);
        }
        String text = String.format(java.util.Locale.ROOT, "%.4f", value);
        while (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    /** 便捷: 把套件列表按名字索引。 */
    public static Map<String, Suite> index(List<Suite> suites) {
        Map<String, Suite> map = new LinkedHashMap<>();
        for (Suite suite : suites) {
            map.put(suite.name, suite);
        }
        return map;
    }
}
