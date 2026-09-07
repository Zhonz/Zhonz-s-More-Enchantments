package com.zhonz.moreenchantments.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;

/**
 * 慈悲(信标均分)的持久化工具: 读写信标 BlockEntity 持久数据中的慈悲等级。
 * 等级由手持带慈悲附魔的物品右键信标时写入(见 ModEventHandlers 的右键处理),
 * 消耗的是物品上的慈悲附魔, 信标本身因此“获得效果”。
 */
public final class BeaconMercyHelper {

    public static final String KEY_MERCY_LEVEL = "zhonz_mercy_level";

    private BeaconMercyHelper() {
    }

    /** 把慈悲等级绑定进信标(右键时调用)。返回 false 表示已绑定/无法绑定。 */
    public static boolean bindMercy(BeaconBlockEntity beacon, int level) {
        CompoundTag data = beacon.getPersistentData();
        if (data.getInt(KEY_MERCY_LEVEL) > 0) return false; // 已绑定
        data.putInt(KEY_MERCY_LEVEL, level);
        beacon.setChanged();
        return true;
    }

    /** 读取信标慈悲等级(>0 表示已绑定)。 */
    public static int getMercyLevel(BeaconBlockEntity beacon) {
        return beacon.getPersistentData().getInt(KEY_MERCY_LEVEL);
    }
}
