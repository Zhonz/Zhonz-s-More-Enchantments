package com.zhonz.moreenchantments.neoforge1201.mixin;

import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 投掷三叉戟的"武器物品"访问器(1.20.1 平台补齐)。
 *
 * <p><b>为什么需要它</b>: 三叉戟掷出后抬手已空, 附魔在三叉戟<b>自身物品</b>上。
 * 1.21 的 {@code ThrownTrident.getWeaponItem()} 是 public, 但 1.20.1 只有
 * {@code protected getPickupItem()}, 外部读不到 → 哭泣之子的"投掷三叉戟"分支无法判定。
 * 本类用 {@code @Accessor} 直读 private 字段 {@code tridentItem}(经 1.20.1 mojmap 核实)。
 *
 * <p><b>对应 1.21 源</b>: {@code WeepingFireHelper.findWielderType} 中的
 * {@code direct instanceof ThrownTrident tt && tt.getWeaponItem()...} 分支;
 * 由 {@link WeepingFireMixin#zhonz$findWeepingAttacker} 调用。
 */
@Mixin(ThrownTrident.class)
public interface ThrownTridentAccessor {

    /** 读取投掷三叉戟保留的物品实体(即掷出前手中的三叉戟)。 */
    @Accessor("tridentItem")
    ItemStack zhonz$getTridentItem();
}
