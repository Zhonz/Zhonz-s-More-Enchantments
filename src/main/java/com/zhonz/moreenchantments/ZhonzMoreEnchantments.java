package com.zhonz.moreenchantments;

import com.zhonz.moreenchantments.enchantment.ModEnchantments;
import com.zhonz.moreenchantments.event.ModEventHandlers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(ZhonzMoreEnchantments.MODID)
public class ZhonzMoreEnchantments {
    public static final String MODID = "zhonz_more_enchantments";

    public ZhonzMoreEnchantments(IEventBus modEventBus) {
        ModEnchantments.register(modEventBus);
        ModEventHandlers.register();
    }
}
