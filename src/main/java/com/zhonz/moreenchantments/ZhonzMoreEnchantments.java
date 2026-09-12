package com.zhonz.moreenchantments;

import com.zhonz.moreenchantments.attribute.ZhonzAttributes;
import com.zhonz.moreenchantments.entity.ThrownMaceEntity;
import com.zhonz.moreenchantments.event.ModEventHandlers;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

@Mod(ZhonzMoreEnchantments.MODID)
public class ZhonzMoreEnchantments {
    // MODID 唯一来源已下沉 common/CommonConstants(stage2); 此处引用避免双源漂移
    public static final String MODID = com.zhonz.moreenchantments.common.CommonConstants.MODID;

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

        // 注册统一增伤属性(bonus_damage / damage_multiplier / flat_damage), 并挂到全部 LivingEntity
        ZhonzAttributes.ATTRIBUTES.register(modEventBus);
        modEventBus.addListener(ZhonzMoreEnchantments::addAttributesToAllLiving);
        // 投掷重锤的客户端渲染器(缺失会导致 EntityRenderDispatcher NPE 崩溃)。
        // 必须用 dist 守卫: 专用服务端上解析 ClientRenderers 的方法引用会加载
        // net.minecraft.client.* (LocalPlayer), RuntimeDistCleaner 会直接判定加载失败。
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            modEventBus.addListener(ClientRenderers::registerRenderers);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ClientRenderers::selfCheck);
        }

        // Enchantments are data-driven in 1.21.1 (defined via JSON), so no
        // code-side registration is needed. Only event handlers are wired up.
        ModEventHandlers.register();
    }

    /**
     * 客户端渲染器注册。
     *
     * <p>{@code thrown_mace} 是自定义实体类型, 若不注册渲染器, 客户端
     * {@code EntityRenderDispatcher.shouldRender} 取到 null renderer 后直接 NPE
     * (实测: 右键松手投出重锤即崩)。用原版 {@code ThrownItemRenderer} 画出投掷的重锤物品。
     * 该事件仅在客户端触发, 服务端不会加载此内部类引用的客户端类型。
     */
    static final class ClientRenderers {
        private ClientRenderers() {
        }

        /** 客户端启动自检: 渲染器是否真的注册进表里(防再次出现"投掷即崩")。 */
        private static boolean zhonz$checked = false;

        static void registerRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(THROWN_MACE_ENTITY.get(),
                    net.minecraft.client.renderer.entity.ThrownItemRenderer<ThrownMaceEntity>::new);
        }

        /**
         * 客户端进世界后自检一次: 用 {@code EntityRenderDispatcher.getRenderer(实体)} 查一遍
         * (崩溃点就是这里返回 null)。命中 null 时把日志级别提到 ERROR, 便于发现回归。
         */
        static void selfCheck(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
            if (zhonz$checked) return;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level == null || mc.getEntityRenderDispatcher() == null) return;
            zhonz$checked = true;
            ThrownMaceEntity probe = new ThrownMaceEntity(mc.level, mc.player, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MACE));
            Object renderer = mc.getEntityRenderDispatcher().getRenderer(probe);
            if (renderer == null) {
                org.slf4j.LoggerFactory.getLogger("ZhonzMoreEnchantments")
                        .error("[RenderCheck] thrown_mace 没有客户端渲染器 —— 投掷重锤会导致客户端崩溃!");
            } else {
                org.slf4j.LoggerFactory.getLogger("ZhonzMoreEnchantments")
                        .info("[RenderCheck] thrown_mace renderer OK: {}", renderer.getClass().getSimpleName());
            }
        }
    }

    private static void addAttributesToAllLiving(EntityAttributeModificationEvent event) {
        for (EntityType<? extends net.minecraft.world.entity.LivingEntity> type : event.getTypes()) {
            event.add(type, ZhonzAttributes.BONUS_DAMAGE);
            event.add(type, ZhonzAttributes.DAMAGE_MULTIPLIER);
            event.add(type, ZhonzAttributes.FLAT_DAMAGE);
            event.add(type, ZhonzAttributes.INCOMING_DAMAGE);
        }
    }
}
