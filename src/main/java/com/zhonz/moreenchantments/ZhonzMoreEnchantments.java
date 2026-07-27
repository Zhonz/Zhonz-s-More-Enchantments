package com.zhonz.moreenchantments;

import com.zhonz.moreenchantments.entity.ThrownMaceEntity;
import com.zhonz.moreenchantments.event.ModEventHandlers;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

@Mod(ZhonzMoreEnchantments.MODID)
public class ZhonzMoreEnchantments {
    public static final String MODID = "zhonz_more_enchantments";

    // 实体类型注册
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    public static final Supplier<EntityType<ThrownMaceEntity>> THROWN_MACE_ENTITY =
            ENTITY_TYPES.register("thrown_mace", () ->
                    EntityType.Builder.<ThrownMaceEntity>of(ThrownMaceEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(4)
                            .updateInterval(20)
                            .build("thrown_mace"));

    public ZhonzMoreEnchantments(IEventBus modEventBus) {
        // 注册实体类型
        ENTITY_TYPES.register(modEventBus);

        // Enchantments are data-driven in 1.21.1 (defined via JSON), so no
        // code-side registration is needed. Only event handlers are wired up.
        ModEventHandlers.register();
    }
}
