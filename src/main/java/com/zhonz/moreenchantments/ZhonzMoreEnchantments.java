package com.zhonz.moreenchantments;

import com.zhonz.moreenchantments.event.ModEventHandlers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(ZhonzMoreEnchantments.MODID)
public class ZhonzMoreEnchantments {
    public static final String MODID = "zhonz_more_enchantments";

    public ZhonzMoreEnchantments(IEventBus modEventBus) {
        // Enchantments are data-driven in 1.21.1 (defined via JSON), so no
        // code-side registration is needed. Only event handlers are wired up.
        ModEventHandlers.register();
    }
}
