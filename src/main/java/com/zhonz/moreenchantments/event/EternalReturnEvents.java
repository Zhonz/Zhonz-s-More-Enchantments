package com.zhonz.moreenchantments.event;

import com.zhonz.moreenchantments.common.eternal.EternalReturnHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/**
 * #59 "永劫回归" 的**重建侧**: 服务端启动、世界加载之前, 若存在待重置标记
 * (上次有人在主手右键了带该附魔的不死图腾), 就在这里清掉旧地形 —— 此刻没有任何
 * 文件句柄被占用, 删除必定成功, 也不会被随后的保存流程覆盖回去。
 *
 * <p>保留 {@code level.dat}(原种子)与 {@code playerdata/ advancements/ stats/}
 * (背包/经验/进度/统计), 于是"按原种子重新生成世界"且"玩家数据不丢"。
 */
public final class EternalReturnEvents {

    private EternalReturnEvents() {
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        EternalReturnHelper.applyPendingReset(
                net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().toFile());
    }
}
