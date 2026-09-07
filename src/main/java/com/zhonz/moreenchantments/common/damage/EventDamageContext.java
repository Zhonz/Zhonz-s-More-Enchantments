package com.zhonz.moreenchantments.common.damage;

import net.minecraft.world.entity.LivingEntity;

/**
 * 事件条件判定所需的平台差异依赖(等级查询 + 时序状态键 + 物品NBT访问)。
 *
 * 这些值因平台而异(1.21 数据驱动 vs 1.20.1 代码注册; EntityDataStorage 键由
 * 事件层持有; 物品自定义 NBT 在 1.21 走 DataComponents 组件、1.20.1 走 getOrCreateTag),
 * 由事件层构造注入; common 判定函数本身是纯规则, 不 import 平台 API。
 */
public final class EventDamageContext {

    /** 附魔等级查询(事件层实现)。 */
    public final EnchantmentLevelLookup levels;

    /** 血路击杀计数查询(事件层按平台 NBT API 实现, 返回某生物类型的击杀数)。 */
    public final BloodPathKills bloodPathKills;

    // 时序状态键(EntityDataStorage/PersistentData 中的字符串键, 与事件层 tick 函数共享)
    public final String keyLiberatorLastAttack;
    public final String keyFoolsMaskLucky;
    public final String keyRhythmHit;
    public final String keyCeaselessStacks;
    /** 泰坦精英标记键(实体系 persistent data + tag)。 */
    public final String eliteTag;

    /** 血路击杀计数访问(跨版本, 由平台层实现 NBT 读取)。 */
    public interface BloodPathKills {
        int countOf(net.minecraft.world.entity.LivingEntity attacker, net.minecraft.world.entity.LivingEntity defender);
    }

    public EventDamageContext(EnchantmentLevelLookup levels, BloodPathKills bloodPathKills,
                              String keyLiberatorLastAttack, String keyFoolsMaskLucky,
                              String keyRhythmHit, String keyCeaselessStacks, String eliteTag) {
        this.levels = levels;
        this.bloodPathKills = bloodPathKills;
        this.keyLiberatorLastAttack = keyLiberatorLastAttack;
        this.keyFoolsMaskLucky = keyFoolsMaskLucky;
        this.keyRhythmHit = keyRhythmHit;
        this.keyCeaselessStacks = keyCeaselessStacks;
        this.eliteTag = eliteTag;
    }

    /** 供单测/未注入场景的无击杀上下文。 */
    public static EventDamageContext withoutKills(EnchantmentLevelLookup levels,
                                                  String kLiberator, String kFoolsLucky,
                                                  String kRhythm, String kCeaseless, String eliteTag) {
        return new EventDamageContext(levels, (a, d) -> 0, kLiberator, kFoolsLucky, kRhythm, kCeaseless, eliteTag);
    }
}
