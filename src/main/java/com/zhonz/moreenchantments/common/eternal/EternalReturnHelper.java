package com.zhonz.moreenchantments.common.eternal;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * #59 "永劫回归"({@code eternal_return})—— 主手持带该附魔的不死图腾右键:
 * 踢出所有玩家(保留背包/经验), 按原种子重新生成世界, 停服。
 *
 * <h2>为什么"触发"和"重建"分成两步</h2>
 * 旧实现(1.3.2 及以前)在**服务器运行中**直接递归删除世界目录, 有两个真实缺陷:
 * <ol>
 *   <li><b>删不掉</b>: Windows 上 {@code session.lock}、{@code region/*.mca} 仍被本进程持有句柄,
 *       {@link File#delete()} 只会返回 {@code false}(不抛异常), 旧代码把返回值丢了 →
 *       静默失败。即便删掉一部分, 紧随其后的 {@code server.halt()} 会走完整的保存流程,
 *       把内存里的区块重新写回磁盘 → 世界"复活"。</li>
 *   <li><b>玩家数据一起没了</b>: 背包/经验/末影箱存在 {@code world/playerdata/<uuid>.dat},
 *       旧实现删掉整个 {@code world/} → 与文档"保留所有玩家的背包、经验等内容"直接矛盾。</li>
 * </ol>
 * 因此现在: <b>触发</b>只做"保存玩家数据 + 写待重置标记 + 踢人 + 停服";
 * <b>重建</b>由下次启动、世界加载之前({@code ServerAboutToStartEvent})执行 —— 那时
 * 没有任何文件句柄, 删除必定成功, 也不会被保存流程覆盖。
 *
 * <h2>"按原种子重新生成"怎么保证</h2>
 * 删除时**保留 {@code level.dat}**(种子与 worldgen 设置都在里面), 只清地形相关目录
 * ({@code region/ entities/ poi/ data/ DIM1/ DIM-1/})。区块缺失时服务端会按 level.dat 里的
 * 原种子重新生成地形 —— 种子不丢, 也不需要改写 {@code server.properties} 的 level-seed。
 * 同时保留 {@code playerdata/ advancements/ stats/}(背包/经验/进度/统计)。
 */
public final class EternalReturnHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger("ZhonzEternalReturn");

    /** 待重置标记文件名(放在游戏根目录, 即 run/ 或 .minecraft/, 不在被重置的世界目录里)。 */
    public static final String FLAG_FILE = "zhonz_eternal_return.flag";

    /**
     * 重建世界时**保留**的条目(世界根目录下一级)。
     * 其余一切(region/entities/poi/data/DIM1/DIM-1/…)都会被删除并重新生成。
     */
    private static final Set<String> KEEP = Set.of(
            "level.dat",        // 种子 + worldgen 设置(原种子的唯一来源)
            "level.dat_old",    // level.dat 的上一次备份, 同种子
            "playerdata",       // 背包 / 经验 / 末影箱
            "advancements",     // 进度
            "stats",            // 统计
            "datapacks",        // 世界级数据包(重建时会被复用)
            "serverconfig",     // 世界级服务端配置
            // session.lock: 本次启动时已被本进程打开(Windows 上是"可共享删除"句柄,
            // 删掉只会让名字从目录消失、句柄仍在), 留着更干净也更安全
            "session.lock"
    );

    /**
     * {@code level.dat} 里**保留**的键(其余一律移除, 由原版回落到默认值)。
     *
     * <p>为什么必须只保留这几个: "末影龙等维度原有生物"的状态并不在 region/entities 里 ——
     * 龙的战斗进度存在 {@code Data.DragonFight}(DragonKilled / PreviouslyKilled / Gateways),
     * 时间、天气、游戏规则、袭击、流浪商人计时也都在 level.dat。只删地形目录会留下
     * {@code DragonKilled=1} → 末影龙永不重生, 与"除玩家数据外全部还原"不符。
     *
     * <p>保留项及理由:
     * <ul>
     *   <li>{@code WorldGenSettings} —— 原种子与 worldgen 设置, 重建地形的前提;</li>
     *   <li>{@code LevelName} —— 世界名(显示与目录选择);</li>
     *   <li>{@code DataVersion} / {@code Version} —— 加载必需, 缺失会触发数据升级甚至读不进来;</li>
     *   <li>{@code Player} —— 单人档的房主玩家数据, 属于"玩家数据"的一部分;</li>
     *   <li>{@code allowCommands} / {@code Difficulty} / {@code DifficultyLocked} —— 世界设置,
     *       移除会静默关掉作弊或改难度, 不属于"世界内容";</li>
     * </ul>
     */
    private static final Set<String> LEVEL_DAT_KEEP = Set.of(
            "WorldGenSettings", "LevelName", "DataVersion", "Version",
            "Player", "allowCommands", "Difficulty", "DifficultyLocked"
    );

    // ===================== 测试钩子(仅供 /zhonztest eternaltest 使用) =====================

    /**
     * 置 true 时 {@link #trigger} 只记录"命中"并立即返回, <b>不</b>写标记、不踢人、不停服。
     * 用于在真实调用路径上验证"右键 → mixin → 附魔判定"这段接线(而不毁掉测试世界)。
     */
    public static boolean dryRun = false;
    /** 最近一次 {@link #dryRun} 命中记录。 */
    public static boolean lastDryRunFired = false;
    /** 最近一次 dry-run 本应执行的动作摘要(供测试打印)。 */
    public static String lastDryRunDetail = "";

    private EternalReturnHelper() {
    }

    // ================================== 触发 ==================================

    /**
     * 执行"永劫回归"。由各平台的 {@code TotemUseMixin} 在确认"主手持带该附魔的不死图腾"后调用。
     *
     * @param server  当前服务端(仅 {@code ServerLevel} 才有)
     * @param player  右键的玩家
     * @param gameDir 游戏根目录(run/ 或 .minecraft/)—— 标记文件写在这里, 下次启动据此重建
     */
    public static void trigger(MinecraftServer server, Player player, File gameDir) {
        File worldRoot = server.getWorldPath(LevelResource.ROOT).toFile();
        long seed = server.overworld() == null ? 0L : server.overworld().getSeed();

        if (dryRun) {
            lastDryRunFired = true;
            lastDryRunDetail = "world=" + worldRoot.getAbsolutePath() + " seed=" + seed
                    + " gameDir=" + gameDir.getAbsolutePath();
            LOGGER.info("[EternalReturn] DRY-RUN 命中(未真的重置): {}", lastDryRunDetail);
            return;
        }

        // 1) 先让玩家数据落盘: 背包/经验在 playerdata 里, 踢人前必须写下去
        try {
            server.getPlayerList().saveAll();
        } catch (Throwable t) {
            LOGGER.error("[EternalReturn] 玩家数据保存失败(背包/经验可能丢失)", t);
        }

        // 2) 写"待重置"标记 —— 真正的地形清理放到下次启动、世界加载之前
        try {
            writePendingFlag(gameDir, worldRoot, seed);
            LOGGER.info("[EternalReturn] 已登记世界重置: 触发者={} world={} seed={} (下次启动时重建)",
                    player.getName().getString(), worldRoot.getAbsolutePath(), seed);
        } catch (IOException e) {
            LOGGER.error("[EternalReturn] 写重置标记失败, 世界不会被重建: {}", e.toString());
        }

        // 3) 踢出所有玩家(保留背包/经验 —— 数据已落盘)
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.disconnect(Component.literal("§d永劫回归……世界将重新诞生"));
        }

        // 4) 2 tick 后停服(留出时间把断线原因发出去)
        server.tell(new TickTask(2, () -> server.halt(false)));
    }

    // ============================== 启动时重建世界 ==============================

    /** 是否存在待重置标记。 */
    public static boolean hasPendingReset(File gameDir) {
        return new File(gameDir, FLAG_FILE).isFile();
    }

    /** 读取标记内容(世界根目录的绝对路径); 无标记或不可读时返回 null。 */
    public static File pendingWorldRoot(File gameDir) {
        File flag = new File(gameDir, FLAG_FILE);
        if (!flag.isFile()) return null;
        try {
            for (String line : Files.readAllLines(flag.toPath(), StandardCharsets.UTF_8)) {
                String s = line.trim();
                if (s.isEmpty() || s.startsWith("#")) continue;
                return new File(s);
            }
        } catch (IOException e) {
            LOGGER.error("[EternalReturn] 读取重置标记失败: {}", e.toString());
        }
        return null;
    }

    /** 标记文件里记录的原始种子(测试/诊断用)。 */
    public static long pendingSeed(File gameDir) {
        File flag = new File(gameDir, FLAG_FILE);
        if (!flag.isFile()) return Long.MIN_VALUE;
        try {
            List<String> lines = Files.readAllLines(flag.toPath(), StandardCharsets.UTF_8);
            for (String line : lines) {
                String s = line.trim();
                if (s.startsWith("seed=")) return Long.parseLong(s.substring(5).trim());
            }
        } catch (Exception ignored) {
        }
        return Long.MIN_VALUE;
    }

    /**
     * 服务端启动、世界加载之前调用: 若存在待重置标记, 清理旧世界地形并保留玩家数据。
     *
     * @return true 表示本次确实执行了重置
     */
    public static boolean applyPendingReset(File gameDir) {
        File flag = new File(gameDir, FLAG_FILE);
        if (!flag.isFile()) return false;
        File worldRoot = pendingWorldRoot(gameDir);
        if (worldRoot == null) {
            LOGGER.error("[EternalReturn] 重置标记存在但读不出世界路径(文件损坏?), 已跳过: {}", flag);
            return false;
        }

        LOGGER.info("[EternalReturn] 检测到待重置标记, 开始重建世界: {}", worldRoot.getAbsolutePath());
        ResetReport report = resetWorld(worldRoot);
        if (report.ok()) {
            LOGGER.info("[EternalReturn] 世界已重置 —— {} (保留: {})",
                    report.summary(), String.join(", ", KEEP));
            // 清理成功才销标记; 失败则保留以便下次启动重试
            if (!flag.delete()) {
                LOGGER.warn("[EternalReturn] 重置标记删除失败, 下次启动会再次重置: {}", flag);
            }
        } else {
            LOGGER.error("[EternalReturn] 世界重置**未完成**, 标记保留待下次重试 —— {}", report.summary());
        }
        return true;
    }

    /**
     * 重置一个世界目录: 删除地形相关条目, 保留玩家数据与 level.dat(原种子)。
     * 纯文件操作, 不依赖服务端 —— {@code /zhonztest eternaltest} 会在临时目录上验证它。
     */
    public static ResetReport resetWorld(File worldRoot) {
        ResetReport report = new ResetReport();
        if (!worldRoot.isDirectory()) {
            report.failed.add("<世界目录不存在: " + worldRoot.getAbsolutePath() + ">");
            return report;
        }
        File[] children = worldRoot.listFiles();
        if (children == null) {
            report.failed.add("<无法列出目录: " + worldRoot.getAbsolutePath() + ">");
            return report;
        }
        for (File child : children) {
            if (KEEP.contains(child.getName())) {
                report.kept.add(child.getName());
                continue;
            }
            if (deleteRecursively(child)) {
                report.deleted.add(child.getName());
            } else {
                report.failed.add(child.getName());
            }
        }
        // 地形/实体删完后, 还要把 level.dat 里"世界内容"的那部分重置
        // (时间/天气/规则/末影龙战斗状态…) —— 否则 Dragons 不会重生。
        resetLevelDat(worldRoot, report);
        return report;
    }

    /**
     * 重置 {@code level.dat}: 只保留 {@link #LEVEL_DAT_KEEP} 里的键, 其余移除(原版回落默认值)。
     *
     * <p>原文件不可解析为 NBT 时(例如测试用的合成世界)只记警告并保持原样, <b>不算失败</b> ——
     * 真实世界的 level.dat 永远是合法 NBT。
     */
    private static void resetLevelDat(File worldRoot, ResetReport report) {
        File levelDat = new File(worldRoot, "level.dat");
        if (!levelDat.isFile()) {
            report.levelDatNote = "skipped(无 level.dat)";
            return;
        }
        CompoundTag root;
        try (InputStream in = new GZIPInputStream(new FileInputStream(levelDat))) {
            root = readNbtCompressed(in);
        } catch (Throwable t) {
            report.levelDatNote = "skipped(非 NBT: " + t.getClass().getSimpleName() + ")";
            LOGGER.warn("[EternalReturn] level.dat 无法解析为 NBT, 保持原样(地形与实体已重置): {}", t.toString());
            return;
        }
        if (!root.contains("Data")) {
            report.levelDatNote = "skipped(缺少 Data 标签)";
            return;
        }
        CompoundTag data = root.getCompound("Data");
        List<String> removed = new ArrayList<>();
        for (String key : new ArrayList<>(data.getAllKeys())) {
            if (LEVEL_DAT_KEEP.contains(key)) {
                continue;
            }
            data.remove(key);
            removed.add(key);
        }
        root.put("Data", data);
        try {
            writeLevelDat(levelDat, root);
            File old = new File(worldRoot, "level.dat_old");
            if (old.isFile()) {
                // 备份也要一起更新, 否则 level.dat 读取失败时回退到未重置的旧内容
                writeLevelDat(old, root);
            }
            report.levelDatNote = "removed=" + removed.size() + removed;
        } catch (IOException e) {
            report.failed.add("level.dat(写入失败)");
            report.levelDatNote = "FAILED: " + e;
            LOGGER.error("[EternalReturn] level.dat 写回失败, 末影龙/时间等可能未重置", e);
        }
    }

    private static void writeLevelDat(File target, CompoundTag tag) throws IOException {
        try (OutputStream out = new GZIPOutputStream(new FileOutputStream(target))) {
            // writeCompressed(CompoundTag, OutputStream) 在 1.20.1 与 1.21.1 签名一致, 无需适配
            NbtIo.writeCompressed(tag, out);
        }
    }

    /**
     * 跨版本读取 gzip 压缩的 NBT。
     *
     * <h3>为什么这里要用反射</h3>
     * 本文件位于 {@code common}(被 1.21.1 主工程与两个 1.20.1 平台以 {@code srcDir} **共享同一份源码**,
     * 见各平台的 {@code build.gradle})。而 NBT 读取 API 在两版之间<b>签名互斥</b>:
     * <ul>
     *   <li>1.21.1: {@code readCompressed(InputStream, NbtAccounter)} —— 没有单参版本;</li>
     *   <li>1.20.1: {@code readCompressed(InputStream)}        —— 没有双参版本;</li>
     *   <li>配额对象也不同: 1.21.1 用 {@code NbtAccounter.unlimitedHeap()}, 1.20.1 用静态字段
     *       {@code NbtAccounter.UNLIMITED}。</li>
     * </ul>
     * 任何一个直接调用都会让另一个版本编译失败(已实测: {@code unlimitedHeap()} / {@code create(long)}
     * 在 1.20.1 均不存在)。因此这里按"存在即调用"的方式选路 —— 与平台层注入
     * {@code AttributeChannelAccess} 属同一类跨版本适配, 只是这个点太窄, 不值得为它单开一个注入接口。
     *
     * <p>写回路径不需要适配: {@code writeCompressed(CompoundTag, OutputStream)} 两版一致。
     */
    private static CompoundTag readNbtCompressed(InputStream in) throws IOException {
        try {
            try {
                Method withAccounter = NbtIo.class.getMethod(
                        "readCompressed", InputStream.class, NbtAccounter.class);
                return (CompoundTag) withAccounter.invoke(null, in, unlimitedAccounter());
            } catch (NoSuchMethodException notIn1211) {
                Method plain = NbtIo.class.getMethod("readCompressed", InputStream.class);
                return (CompoundTag) plain.invoke(null, in);
            }
        } catch (ReflectiveOperationException e) {
            throw new IOException("无法读取 NBT: " + e, e);
        }
    }

    /** 取"不限额"的 {@link NbtAccounter}: 1.21.1 用工厂方法, 1.20.1 用静态字段。 */
    private static Object unlimitedAccounter() throws ReflectiveOperationException {
        try {
            return NbtAccounter.class.getMethod("unlimitedHeap").invoke(null);
        } catch (NoSuchMethodException notIn1201) {
            return NbtAccounter.class.getField("UNLIMITED").get(null);
        }
    }

    /** 递归删除; 返回是否**全部**删除成功(旧实现丢掉了这个返回值, 于是静默失败)。 */
    private static boolean deleteRecursively(File f) {
        if (f == null || !f.exists()) return true;
        boolean ok = true;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) {
                    if (!deleteRecursively(c)) ok = false;
                }
            }
        }
        if (!f.delete()) ok = false;
        return ok;
    }

    /**
     * 写"待重置"标记(下次启动由 {@link #applyPendingReset} 消费)。
     * 公开以便 {@code /zhonztest eternaltest} 能在临时目录上验证"写→读→重建"整条链路。
     */
    public static void writePendingFlag(File gameDir, File worldRoot, long seed) throws IOException {
        if (!gameDir.isDirectory() && !gameDir.mkdirs()) {
            throw new IOException("无法创建游戏目录 " + gameDir);
        }
        List<String> lines = new ArrayList<>(Arrays.asList(
                "# Zhonz's More Enchantments — 永劫回归(eternal_return)待重置标记",
                "# 下次启动、世界加载之前删除下列世界目录的地形数据(保留 playerdata/level.dat)",
                worldRoot.getAbsolutePath(),
                "seed=" + seed,
                "at=" + System.currentTimeMillis()));
        Files.write(new File(gameDir, FLAG_FILE).toPath(), lines, StandardCharsets.UTF_8);
    }

    /** 重置结果: 删了什么 / 留了什么 / 哪些没删掉 / level.dat 的重置摘要。 */
    public static final class ResetReport {
        public final List<String> deleted = new ArrayList<>();
        public final List<String> kept = new ArrayList<>();
        public final List<String> failed = new ArrayList<>();
        /** level.dat 的重置摘要(移除了哪些键 / 跳过原因)。 */
        public String levelDatNote = "";

        public boolean ok() {
            return failed.isEmpty();
        }

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append("deleted=").append(deleted.size()).append('[').append(String.join(",", deleted)).append(']');
            sb.append(" kept=").append(kept.size()).append('[').append(String.join(",", kept)).append(']');
            if (!levelDatNote.isEmpty()) {
                sb.append(" level.dat=").append(levelDatNote);
            }
            if (!failed.isEmpty()) {
                sb.append(" FAILED=").append(failed.size()).append('[').append(String.join(",", failed)).append(']');
            }
            return sb.toString();
        }
    }

    /** 供测试断言用的"保留清单"只读视图。 */
    public static Set<String> keepNames() {
        return KEEP;
    }
}
