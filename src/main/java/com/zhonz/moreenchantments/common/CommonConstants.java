package com.zhonz.moreenchantments.common;

/**
 * 平台无关常量(stage2 下沉)。
 *
 * MODID 等纯字符串常量放这里, 使 common 包不反向依赖平台入口类
 * (NeoForge 的 ZhonzMoreEnchantments 等), 便于 UniMined 双加载器复用。
 */
public final class CommonConstants {

    /** 模组 id(与 resources/META-INF/neoforge.mods.toml 的 mods[0].modId 一致)。 */
    public static final String MODID = "zhonz_more_enchantments";

    private CommonConstants() {
    }
}
