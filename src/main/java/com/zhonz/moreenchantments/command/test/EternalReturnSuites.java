package com.zhonz.moreenchantments.command.test;

import com.zhonz.moreenchantments.common.eternal.EternalReturnHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 永劫回归(#59 eternal_return)重置语义测试(round-testkit)。
 *
 * <p>重置的文件操作是纯 IO({@link EternalReturnHelper#resetWorld}), 不需要世界,
 * 因此用<b>合成世界目录</b>验证 —— 不会碰真实存档。
 *
 * <p>钉住的新语义(用户 2026-09 确认):
 * <ul>
 *   <li>除玩家数据外<b>全部还原</b>:各维度地形/实体/POI/data 一律删除(白名单式, 连模组维度
 *       {@code dimensions/} 一起);</li>
 *   <li>{@code level.dat} <b>只保留原种子与加载必需字段</b> —— 时间/天气/游戏规则/
 *       <b>末影龙战斗状态 DragonFight</b> 都要清掉, 否则龙永远不会重生;</li>
 *   <li>保留 {@code playerdata/ advancements/ stats/ datapacks/ serverconfig/}。</li>
 * </ul>
 */
public final class EternalReturnSuites {

    private EternalReturnSuites() {
    }

    public static final String SUITE = "eternal-return";

    public static List<TestKit.Suite> all(ServerLevel level) {
        TestKit.Suite suite = new TestKit.Suite(SUITE,
                "永劫回归: 全维度内容重置、level.dat 只留种子、玩家数据保留");

        suite.add("reset_clears_world_content_and_level_dat_but_keeps_player_data",
                "合成一个世界目录(各维度地形/实体/模组维度 + level.dat 里的时间/天气/规则/末影龙状态 + 玩家数据), "
                        + "执行重置后必须: 维度内容被删、level.dat 只剩种子与必需字段、玩家数据完好",
                "region/entities/poi/data/DIM1/DIM-1/dimensions 被删; level.dat 保留 seed 与 LevelName, "
                        + "但 Time/DragonFight/GameRules/天气 消失; playerdata/advancements/stats 保留",
                c -> {
                    File tmp = new File("zhonz_eternal_test_leveldat");
                    deleteTree(tmp);
                    File world = new File(tmp, "world");
                    try {
                        // ---- 造维度内容(全部应被删) ----
                        for (String dir : new String[]{"region", "entities", "poi", "data",
                                "DIM1", "DIM-1", "dimensions/testmod/testdim"}) {
                            writeFile(new File(world, dir + "/probe.mca"), "BLOCK");
                        }
                        // ---- 造玩家数据(全部应保留) ----
                        for (String dir : new String[]{"playerdata", "advancements", "stats",
                                "datapacks", "serverconfig"}) {
                            writeFile(new File(world, dir + "/keep.txt"), "PLAYER-DATA");
                        }
                        // ---- 造 level.dat(合法 NBT) ----
                        long seed = -8165688043454563513L;
                        CompoundTag root = new CompoundTag();
                        CompoundTag data = new CompoundTag();
                        CompoundTag worldGen = new CompoundTag();
                        worldGen.putLong("seed", seed);
                        data.put("WorldGenSettings", worldGen);
                        data.putString("LevelName", "测试世界");
                        data.putInt("DataVersion", 3955);
                        CompoundTag dragon = new CompoundTag();
                        dragon.putByte("DragonKilled", (byte) 1);
                        dragon.putByte("PreviouslyKilled", (byte) 1);
                        dragon.putByte("NeedsStateScanning", (byte) 0);
                        data.put("DragonFight", dragon);
                        data.putLong("Time", 1231337L);
                        data.putLong("DayTime", 1231337L);
                        data.putByte("raining", (byte) 1);
                        CompoundTag rules = new CompoundTag();
                        rules.putString("keepInventory", "false");
                        data.put("GameRules", rules);
                        data.putBoolean("allowCommands", true);
                        root.put("Data", data);
                        writeLevelDat(new File(world, "level.dat"), root);

                        // ---- 执行重置 ----
                        EternalReturnHelper.ResetReport report = EternalReturnHelper.resetWorld(world);
                        c.note("报告: " + report.summary());

                        // 1) 维度内容全删(含模组维度与实体目录)
                        for (String dir : new String[]{"region", "entities", "poi", "data",
                                "DIM1", "DIM-1", "dimensions"}) {
                            c.that(!new File(world, dir).exists(), "维度内容应被删除: " + dir);
                        }
                        // 2) 玩家数据保留
                        for (String dir : new String[]{"playerdata", "advancements", "stats",
                                "datapacks", "serverconfig"}) {
                            c.that(new File(new File(world, dir), "keep.txt").isFile(), "玩家数据应保留: " + dir);
                        }
                        // 3) level.dat: 种子与必需字段保留, 世界内容清空
                        CompoundTag after = readLevelDat(new File(world, "level.dat"));
                        c.notNull("level.dat 仍可解析", after);
                        if (after != null) {
                            CompoundTag afterData = after.getCompound("Data");
                            c.eq("原种子必须保留", afterData.getCompound("WorldGenSettings").getLong("seed"), seed);
                            c.eq("世界名保留", afterData.getString("LevelName"), "测试世界");
                            c.that(afterData.contains("DataVersion"), "DataVersion 保留(加载必需)");
                            c.that(!afterData.contains("DragonFight"),
                                    "末影龙战斗状态必须清掉(否则龙不会重生)");
                            c.that(!afterData.contains("Time"), "时间必须重置");
                            c.that(!afterData.contains("DayTime"), "DayTime 必须重置");
                            c.that(!afterData.contains("raining"), "天气必须重置");
                            c.that(!afterData.contains("GameRules"), "游戏规则必须重置");
                            c.eq("allowCommands 保留(否则单机存档静默关掉作弊)", afterData.getBoolean("allowCommands"), true);
                            c.note("level.dat 保留键: " + afterData.getAllKeys());
                        }
                        // 4) 整体判断
                        c.that(report.ok(), "重置必须全部成功", "失败项: " + report.failed);
                    } finally {
                        deleteTree(tmp);
                    }
                });

        suite.add("reset_is_idempotent_and_safe_on_missing_dir",
                "重置必须是幂等的: 对不存在的世界目录返回失败报告而不抛异常; 对已重置过的世界再跑一次也不报错",
                "不存在目录 → ok()==false 且不抛; 重复重置 → ok()==true",
                c -> {
                    EternalReturnHelper.ResetReport missing =
                            EternalReturnHelper.resetWorld(new File("zhonz_eternal_absent_dir"));
                    c.that(!missing.ok(), "不存在的目录应报告失败而不是抛异常");
                    c.that(missing.failed.size() > 0, "失败原因要能说明");

                    File tmp = new File("zhonz_eternal_test_idem");
                    deleteTree(tmp);
                    File world = new File(tmp, "world");
                    try {
                        writeFile(new File(world, "region/a.mca"), "X");
                        writeFile(new File(world, "playerdata/p.dat"), "P");
                        EternalReturnHelper.ResetReport first = EternalReturnHelper.resetWorld(world);
                        EternalReturnHelper.ResetReport second = EternalReturnHelper.resetWorld(world);
                        c.that(first.ok(), "首次重置成功", first.summary());
                        c.that(second.ok(), "重复重置不应失败", second.summary());
                        c.that(new File(world, "playerdata/p.dat").isFile(), "重复重置后玩家数据仍在");
                    } finally {
                        deleteTree(tmp);
                    }
                });

        return List.of(suite);
    }

    // ===================== 工具 =====================

    private static void writeFile(File f, String content) throws Exception {
        File parent = f.getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeLevelDat(File target, CompoundTag tag) throws Exception {
        try (OutputStream out = new GZIPOutputStream(new FileOutputStream(target))) {
            NbtIo.writeCompressed(tag, out);
        }
    }

    private static CompoundTag readLevelDat(File source) {
        if (!source.isFile()) {
            return null;
        }
        try (InputStream in = new GZIPInputStream(new FileInputStream(source))) {
            return NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        } catch (Exception e) {
            return null;
        }
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                deleteTree(k);
            }
        }
        f.delete();
    }
}
