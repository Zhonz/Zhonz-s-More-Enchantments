package com.zhonz.moreenchantments.command.test;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试用世界探针(round-testkit)。
 *
 * <p>解决的问题: 旧套件把敌人/攻击者挂在**单例 FakePlayer** 与固定坐标的假人上,
 * 于是必须靠人工清理 {@code zhonz_} 前缀的 NBT、清理 EntityDataStorage 来避免
 * 用例之间互相污染(MTC:327-359、MTC:614-631)。那种写法有两个后果:
 * <ul>
 *   <li>污染一旦漏清, PASS 就可能是"上一条用例的残留"造成的假通过;</li>
 *   <li>FakePlayer 是单例, 无法并行/无法构造"攻击者与目标同时带附魔"的对照。</li>
 * </ul>
 *
 * <p>本类改为 <b>每个用例生成全新实体</b>, 用完 {@link #close()} 统一丢弃:
 * 状态天然隔离, 不需要清理逻辑, 也就不存在"漏清"这一类假通过。
 * 所有实体: 关 AI、关重力、静默、持久化、打 {@link #TEST_TAG} 标签, 便于人工排查。
 *
 * <p>坐标以调用方给的 {@code anchor} 为准(通常取命令执行者所在方块),
 * 保证所在区块已加载。
 */
public final class TestWorld implements AutoCloseable {

    /** 所有测试实体的标记标签, 便于手动 {@code /kill @e[tag=...]} 兜底清理。 */
    public static final String TEST_TAG = "zhonz_test_probe";

    private final ServerLevel level;
    private final BlockPos anchor;
    private final List<Entity> spawned = new ArrayList<>();

    public TestWorld(ServerLevel level, BlockPos anchor) {
        this.level = level;
        this.anchor = anchor;
    }

    public ServerLevel level() {
        return level;
    }

    public BlockPos anchor() {
        return anchor;
    }

    public List<Entity> spawned() {
        return spawned;
    }

    // ==================================================================
    // 目标生成
    // ==================================================================

    /** 标准假人: 僵尸, 关 AI/重力, 用来承受伤害。 */
    public Zombie zombie() {
        Zombie zombie = spawn(EntityType.ZOMBIE);
        zombie.setNoAi(true);
        return zombie;
    }

    /** 高血量假人: 牛, 最大生命可指定 —— 用于"超杀/百分比阈值/大数值"用例。 */
    public Cow cow(double maxHealth) {
        Cow cow = spawn(EntityType.COW);
        var attr = cow.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(maxHealth);
        }
        cow.setHealth((float) maxHealth);
        return cow;
    }

    /** 通用生成: 关 AI(若为 Mob)、关重力、静默、打标签、登记以便回收。 */
    public <T extends Entity> T spawn(EntityType<T> type) {
        T entity = type.spawn(level, anchor.above(), MobSpawnType.COMMAND);
        if (entity == null) {
            throw new IllegalStateException("测试实体生成失败: " + type + " @" + anchor.above());
        }
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setPersistenceRequired();
        }
        entity.setNoGravity(true);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setSilent(true);
        entity.addTag(TEST_TAG);
        spawned.add(entity);
        return entity;
    }

    // ==================================================================
    // 装备 / 状态
    // ==================================================================

    /** 放主手。 */
    public static void mainHand(LivingEntity entity, ItemStack stack) {
        entity.setItemSlot(EquipmentSlot.MAINHAND, stack);
    }

    /** 放副手。 */
    public static void offHand(LivingEntity entity, ItemStack stack) {
        entity.setItemSlot(EquipmentSlot.OFFHAND, stack);
    }

    /** 放指定槽位。 */
    public static void equip(LivingEntity entity, EquipmentSlot slot, ItemStack stack) {
        entity.setItemSlot(slot, stack);
    }

    /** 清空六个装备槽(用例开始前隔离残留)。 */
    public static void stripGear(LivingEntity entity) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            entity.setItemSlot(slot, ItemStack.EMPTY);
        }
    }

    /**
     * 把当前生命设为最大生命的指定比例(0.0~1.0)。
     *
     * <p>边界口径: 生命不允许 <=0(那会直接死亡, 伤害事件就不走了),
     * 因此最小钳到 0.5 点; 0.25/0.5 这类"阈值正好命中"的用例请传精确值,
     * 由 {@link #healthExact} 负责。
     */
    public static void healthAtPercent(LivingEntity entity, float percent) {
        float max = entity.getMaxHealth();
        entity.setHealth(Math.max(0.5F, Math.min(max, max * percent)));
    }

    /** 把生命设为精确值(钳在 (0, maxHealth] 内)。 */
    public static void healthExact(LivingEntity entity, float health) {
        float max = entity.getMaxHealth();
        float clamped = Math.max(0.5F, Math.min(max, health));
        entity.setHealth(clamped);
    }

    /** 点燃(与模组内部同口径: setRemainingFireTicks)。 */
    public static void ignite(LivingEntity entity, int ticks) {
        entity.setRemainingFireTicks(Math.max(entity.getRemainingFireTicks(), ticks));
    }

    /** 扑灭。 */
    public static void extinguish(LivingEntity entity) {
        entity.setRemainingFireTicks(0);
    }

    /** 设置水平速度(用于冲锋手/止步这类"看速度"的判定)。 */
    public static void horizontalSpeed(LivingEntity entity, double blocksPerTick, double y) {
        entity.setDeltaMovement(blocksPerTick, y, 0.0D);
        // 静止判定(halt)读 x/z 分量, 这里用 x 轴承载速度
    }

    /** 完全静止(用于止步的 +40% 命中条件)。 */
    public static void freeze(LivingEntity entity) {
        entity.setDeltaMovement(Vec3.ZERO);
    }

    // ==================================================================
    // 原版命令(构造天气/时间等触发条件)
    // ==================================================================

    /** 静默以权限 2 执行一条原版命令(不产生聊天反馈)。 */
    public void command(String command) {
        var server = level.getServer();
        if (server == null) {
            return;
        }
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput().withPermission(2),
                command);
    }

    // ==================================================================
    // 回收
    // ==================================================================

    /** 丢弃本探针生成的所有实体。 */
    @Override
    public void close() {
        for (Entity entity : spawned) {
            if (entity.isAlive()) {
                entity.discard();
            }
        }
        spawned.clear();
    }
}
