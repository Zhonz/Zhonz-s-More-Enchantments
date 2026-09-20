package com.zhonz.moreenchantments.forge;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * 1.20.1 Forge 平台 mod 入口。
 *
 * 架构: common 平台无关核心(结算引擎/判定规则/存储)由根工程 srcDir 共享编译;
 * 本包为 Forge 1.20.1 平台壳 —— 属性注册 + 附魔代码注册 + 事件订阅适配。
 */
@Mod(CommonConstants1201.MODID)
public class ZhonzMoreEnchantments1201 {

    public ZhonzMoreEnchantments1201() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 跨版本: 平台属性通道访问注入(引擎写入属性经此接口, 1.20.1 实现)
        AttributeAccess1201.install();

        // 属性注册(bonus/mult/flat 三通道)
        ZhonzAttributes1201.ATTRIBUTES.register(modBus);
        modBus.addListener(ZhonzMoreEnchantments1201::addAttributesToAllLiving);

        // 自定义伤害类型(weeping_fire / frost / true_damage): 与 1.21 一样**纯数据包驱动**,
        // 条目来自 resources/data/zhonz_more_enchantments/damage_type/*.json。
        // 注意: 1.20.1 的 minecraft:damage_type 不在 Forge 的 GameData 中, **不能**用
        // DeferredRegister 注册(会抛 "Unable to find registry with key minecraft:damage_type"
        // 导致服务端启动失败); 键见 DamageTypes1201。

        // 附魔代码注册(1.20.1 为代码注册, 见 ModEnchantments1201)
        ModEnchantments1201.ENCHANTMENTS.register(modBus);

        // 游戏事件(伤害管线等)注册到 Forge 总线
        MinecraftForge.EVENT_BUS.register(ForgeEventHandler1201.class);

        // 移植效果统一接线(批 A tick + 批 C 73-89 事件)
        EnchantWiring1201.register();
    }

    private static void addAttributesToAllLiving(EntityAttributeModificationEvent event) {
        for (EntityType<? extends LivingEntity> type : event.getTypes()) {
            event.add(type, ZhonzAttributes1201.BONUS_DAMAGE.get());
            event.add(type, ZhonzAttributes1201.DAMAGE_MULTIPLIER.get());
            event.add(type, ZhonzAttributes1201.FLAT_DAMAGE.get());
            event.add(type, ZhonzAttributes1201.INCOMING_DAMAGE.get());
        }
    }
}
