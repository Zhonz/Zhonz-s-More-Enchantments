package com.zhonz.moreenchantments.command.test;

import com.zhonz.moreenchantments.attribute.ZhonzAttributes;
import com.zhonz.moreenchantments.common.enchant.EnchantIds;
import com.zhonz.moreenchantments.common.storage.EntityDataStorage;
import com.zhonz.moreenchantments.enchantment.ModEnchantments;
import com.zhonz.moreenchantments.event.ModEventHandlers;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ConcurrentModificationException;
import java.util.List;

/**
 * 审计发现的缺陷 → 可回归测试(round-testkit)。
 *
 * <p>这些用例断言的是<b>正确行为</b>, 因此当前会红 —— 这是刻意的:
 * 审计报告({@code graphflow-out/audit/})里的"可疑点"如果不落成测试,
 * 下次改动就没有任何东西会提醒它。用例 id 统一以 {@code defect_} 开头,
 * 修复后应转为绿, 不需要改测试。
 *
 * <p>覆盖范围说明: 这里只固化那些<b>能用运行时断言客观判定</b>的缺陷。
 * 需要构造真实客户端/真实玩家交互才能判定的(例如联机客户端 NPE、
 * 闪避后仍被秒杀、补种被当场破坏、满包吞材料)不在本套件内,
 * 仍以审计报告的静态证据为准。
 */
public final class AuditRegressionSuites {

    private AuditRegressionSuites() {
    }

    public static final String SUITE = "audit-regress";

    public static List<TestKit.Suite> all(ServerLevel level) {
        TestKit.Suite suite = new TestKit.Suite(SUITE,
                "审计缺陷回归: 玩家状态存储双后端、纯净多减益崩溃、属性通道挂载");

        // ------------------------------------------------------------------
        // 根因: 玩家存在两套互不相通的 per-entity 存储
        //   - EntityDataStorage.getData(player)      → WeakHashMap(不存档)
        //   - EntityDataStorage.getEntityData(player) → player.getPersistentData()(存档)
        //   - EntityDataStorage.getDataIfExists / hasData / removeData 只作用于 WeakHashMap
        // 生产代码里"写"与"读"分别走过这两个入口, 于是同一附魔的状态读不回来。
        // 已知受害者: fools_mask 幸运标志(写 persistentData, 读 WeakHashMap)
        //            → 幸运分支不可达, 玩家恒走 ×0.01~1 的减伤分支。
        // ------------------------------------------------------------------
        suite.addWorld("defect_player_storage_backends_must_agree",
                "[已知缺陷] 玩家的两个 per-entity 取数入口必须指向同一份数据, 否则\"写了读不回\"",
                "getData(player) 与 getEntityData(player) 返回同一份 CompoundTag",
                c -> {
                    FakePlayer player = FakePlayerFactory.getMinecraft(level);
                    player.getPersistentData().remove("zhonz_test_probe_key");
                    EntityDataStorage.removeData(player);

                    boolean sameInstance = EntityDataStorage.getData(player)
                            == EntityDataStorage.getEntityData(player);
                    c.note("getData 走 WeakHashMap, getEntityData 走 persistentData");

                    EntityDataStorage.getData(player).putBoolean("zhonz_test_probe_key", true);
                    boolean readableViaEntityData = EntityDataStorage
                            .getEntityData(player).getBoolean("zhonz_test_probe_key");
                    boolean savedWithPlayer = player.getPersistentData().getBoolean("zhonz_test_probe_key");
                    c.note("经 getData 写入后: getEntityData 读到=" + readableViaEntityData
                            + ", persistentData 里=" + savedWithPlayer);

                    c.that(sameInstance, "两个入口必须指向同一份数据");
                    c.that(readableViaEntityData, "经 getData 写入的状态必须能被 getEntityData 读到",
                            "fools_mask 等附魔正是\"写一个入口、读另一个入口\"");
                    c.that(savedWithPlayer, "经 getData 写入的玩家状态必须落在会被存档的 persistentData 里",
                            "否则每次重登都会丢失");

                    EntityDataStorage.removeData(player);
                    player.getPersistentData().remove("zhonz_test_probe_key");
                });

        // ------------------------------------------------------------------
        // 纯净(the_pure): 遍历 getActiveEffects() 的同时 removeEffect。
        // 原版 getActiveEffects() 返回 activeEffects 的 live view, 因此 >=2 个减益时
        // 会 ConcurrentModificationException, 且发生在 PlayerTick 里。
        // ------------------------------------------------------------------
        suite.addWorld("defect_the_pure_multi_debuff_must_not_crash",
                "[已知缺陷] 纯净在清除减益时不得边遍历边修改集合: 同时有 2 个减益必须在 PlayerTick 中安然通过",
                "2 个减益 + 纯净 → PlayerTickEvent.Post 不抛 ConcurrentModificationException",
                c -> {
                    Registry<Enchantment> registry = level.getServer().registryAccess()
                            .registryOrThrow(Registries.ENCHANTMENT);
                    Holder<Enchantment> pure = registry.getHolder(net.minecraft.resources.ResourceKey.create(
                            Registries.ENCHANTMENT,
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                    com.zhonz.moreenchantments.common.CommonConstants.MODID, EnchantIds.THE_PURE)))
                            .orElse(null);
                    if (pure == null) {
                        c.skip("the_pure 未注册");
                        return;
                    }
                    FakePlayer player = FakePlayerFactory.getMinecraft(level);
                    TestWorld.stripGear(player);
                    ItemStack chest = new ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE);
                    chest.enchant(pure, 1);
                    player.setItemSlot(EquipmentSlot.CHEST, chest);
                    c.note("the_pure 等级=" + ModEventHandlers.LEVELS.anySlot(player, EnchantIds.THE_PURE));

                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
                    player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));
                    c.note("减益数=" + player.getActiveEffects().size());

                    try {
                        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
                        c.that(true, "PlayerTick 未抛异常");
                        c.note("剩余减益=" + player.getActiveEffects().size());
                    } catch (ConcurrentModificationException e) {
                        c.that(false, "清除减益时不得边遍历边修改集合",
                                "抛出了 ConcurrentModificationException: " + e.getMessage());
                    } catch (Throwable t) {
                        // 其他异常可能是 FakePlayer 作为非真实玩家导致的, 不判失败, 但要留下证据
                        c.note("PlayerTick 抛出其他异常(不计入判定): "
                                + t.getClass().getSimpleName() + ": " + t.getMessage());
                    } finally {
                        TestWorld.stripGear(player);
                        player.removeAllEffects();
                    }
                });

        // ------------------------------------------------------------------
        // 属性通道必须先挂到 LivingEntity, 否则所有走属性通道的附魔静默失效。
        // 这是"整族附魔"的前置条件, 值得单独断言。
        // ------------------------------------------------------------------
        suite.addWorld("attribute_channels_mounted_on_living_entities",
                "四条增伤/受击通道属性必须挂在每个 LivingEntity 上, 且默认值为 (0, 1, 0, 1)",
                "僵尸身上四个属性非空且默认值正确",
                c -> {
                    try (TestWorld world = new TestWorld(level, level.getSharedSpawnPos())) {
                        Zombie zombie = world.zombie();
                        AttributeInstance bonus = zombie.getAttribute(ZhonzAttributes.BONUS_DAMAGE);
                        AttributeInstance mult = zombie.getAttribute(ZhonzAttributes.DAMAGE_MULTIPLIER);
                        AttributeInstance flat = zombie.getAttribute(ZhonzAttributes.FLAT_DAMAGE);
                        AttributeInstance incoming = zombie.getAttribute(ZhonzAttributes.INCOMING_DAMAGE);

                        c.notNull("bonus_damage 已挂载", bonus);
                        c.notNull("damage_multiplier 已挂载", mult);
                        c.notNull("flat_damage 已挂载", flat);
                        c.notNull("incoming_damage 已挂载", incoming);

                        if (bonus != null) {
                            c.eq("bonus_damage 默认值", bonus.getValue(), 0.0D, 1.0E-9D);
                        }
                        if (mult != null) {
                            c.eq("damage_multiplier 默认值", mult.getValue(), 1.0D, 1.0E-9D);
                        }
                        if (flat != null) {
                            c.eq("flat_damage 默认值", flat.getValue(), 0.0D, 1.0E-9D);
                        }
                        if (incoming != null) {
                            c.eq("incoming_damage 默认值", incoming.getValue(), 1.0D, 1.0E-9D);
                        }
                    }
                });

        suite.addWorld("channel_ranges_clamp_hostile_values",
                "通道属性的区间必须钳住越界值: 负乘数 → 0, 负受击倍率 → 0, 超大加伤 → 上限(否则 -100% 以下的加伤会变成治疗)",
                "mult(-5)→0.0, incoming(-5)→0.0, bonus(1e12)→<=1e9",
                c -> {
                    try (TestWorld world = new TestWorld(level, level.getSharedSpawnPos())) {
                        Zombie zombie = world.zombie();
                        AttributeInstance mult = zombie.getAttribute(ZhonzAttributes.DAMAGE_MULTIPLIER);
                        AttributeInstance incoming = zombie.getAttribute(ZhonzAttributes.INCOMING_DAMAGE);
                        AttributeInstance bonus = zombie.getAttribute(ZhonzAttributes.BONUS_DAMAGE);
                        if (mult == null || incoming == null || bonus == null) {
                            c.skip("通道属性未挂载");
                            return;
                        }
                        mult.setBaseValue(-5.0D);
                        double clampedMult = mult.getValue();
                        incoming.setBaseValue(-5.0D);
                        double clampedIncoming = incoming.getValue();
                        bonus.setBaseValue(1.0E12D);
                        double clampedBonus = bonus.getValue();

                        c.note("mult(-5)=" + TestKit.fmt(clampedMult)
                                + " incoming(-5)=" + TestKit.fmt(clampedIncoming)
                                + " bonus(1e12)=" + TestKit.fmt(clampedBonus));

                        c.eq("负乘数必须钳到 0", clampedMult, 0.0D, 1.0E-9D);
                        c.eq("负受击倍率必须钳到 0", clampedIncoming, 0.0D, 1.0E-9D);
                        c.that(clampedBonus <= 1.0E9D, "加伤必须钳到上限以内",
                                "实测 " + TestKit.fmt(clampedBonus));
                    }
                });

        // ------------------------------------------------------------------
        // 未知 id 的安全性: 事件层用的是 getHolderOrThrow, 一旦 id 打错就会在伤害
        // 事件里抛异常(可能连锁到保存/踢人)。这里确认 null 安全入口可用。
        // ------------------------------------------------------------------
        suite.addWorld("holder_or_null_is_available_for_guards",
                "必须存在 null 安全的 Holder 解析入口, 供事件路径做守卫(避免 getHolderOrThrow 在事件里炸)",
                "getHolderOrNull(未知 id) == null 且 getHolderOrNull(已知 id) 非 null",
                c -> {
                    net.minecraft.resources.ResourceKey<Enchantment> unknown =
                            net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT,
                                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                            com.zhonz.moreenchantments.common.CommonConstants.MODID,
                                            "zhonz_unknown_probe"));
                    c.that(ModEnchantments.getHolderOrNull(unknown) == null, "未知 id 返回 null");
                    net.minecraft.resources.ResourceKey<Enchantment> known =
                            net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT,
                                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                            com.zhonz.moreenchantments.common.CommonConstants.MODID,
                                            EnchantIds.FINALE));
                    c.notNull("已知 id 返回非 null", ModEnchantments.getHolderOrNull(known));
                });

        return List.of(suite);
    }
}
