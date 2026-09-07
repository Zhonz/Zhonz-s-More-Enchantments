package com.zhonz.moreenchantments.common.damage;

import net.minecraft.resources.ResourceLocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 平台无关的统一增伤结算引擎(stage2 抽取的第一簇, round14)。
 *
 * 目标: 把"增伤只在最后一步结算"的纯规则从事件层剥离, 使其不 import 任何
 * NeoForge/Forge 平台 API, 以便 UniMined 双加载器(Forge 1.20.1 + NeoForge 1.21.1)
 * 原样复用; 平台差异点(属性注册、AttributeModifier 构造、事件绑定)全部由调用方注入。
 *
 * 通道语义(与 ZhonzAttributes 一致):
 * <ul>
 *   <li>{@code bonus}(加伤层): 默认 0。多个加伤附魔以独立 ADD_VALUE modifier 累加
 *       百分比; 结算按 ×(1+sum) 作用。</li>
 *   <li>{@code mult}(乘算层): 默认 1。乘伤附魔把总乘积写入单一 modifier(ADD_VALUE
 *       写 (mult-1)); 结算按 ×mult。</li>
 *   <li>{@code flat}(固定点加伤层): 默认 0。结算后直接 +flat(绝对量, 不受 %/× 缩放)。</li>
 * </ul>
 * 结算公式: {@code final = amount × (1+bonus) × mult + flat}。
 *
 * 注意: 本引擎不做任何附魔条件判定 / 等级查询(那是事件层职责); 只负责
 * 属性写入与最终结算的纯规则。
 */
public final class UnifiedDamageEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger("ZhonzUnifiedDamage");

    /**
     * 平台属性通道访问实现(stage 跨版本层)。
     *
     * 1.20.1 与 1.21.1 的 AttributeModifier/AttributeInstance API 不同(构造参数、按 id 移除),
     * 平台层(NeoForge/Forge 各自的 ZhonzAttributes 侧)启动时调用 {@link #install(AttributeChannelAccess)}
     * 注入实现; 本引擎与 common 规则只经该接口操作属性, 不直接触碰版本差异 API。
     * 未注入时写入辅助为空操作(仅 settle 纯函数可用, 保证测试/未挂平台不崩)。
     */
    private static volatile com.zhonz.moreenchantments.common.version.AttributeChannelAccess ACCESS;

    private UnifiedDamageEngine() {
    }

    /** 平台层注入属性通道访问实现(幂等, 后注入覆盖)。 */
    public static void install(com.zhonz.moreenchantments.common.version.AttributeChannelAccess access) {
        ACCESS = access;
    }

    private static com.zhonz.moreenchantments.common.version.AttributeChannelAccess access() {
        return ACCESS;
    }

    private static void removeModifierById(net.minecraft.world.entity.ai.attributes.AttributeInstance inst,
                                           ResourceLocation id) {
        com.zhonz.moreenchantments.common.version.AttributeChannelAccess a = ACCESS;
        if (a == null) return;
        a.removeModifier(inst, id);
    }

    private static void addValueModifier(net.minecraft.world.entity.ai.attributes.AttributeInstance inst,
                                         ResourceLocation id, double amount) {
        com.zhonz.moreenchantments.common.version.AttributeChannelAccess a = ACCESS;
        if (a == null) return;
        inst.addTransientModifier(a.makeAddModifier(id, amount));
    }

    /**
     * 统一结算(纯函数, 跨版本): final = amount × (1+bonus) × mult + flat。
     *
     * 三通道当前值由平台层(按各自版本 API)读取后传入, 使本方法不依赖
     * Holder/Attribute 等跨版本差异类型 → 可被 1.20.1 与 1.21.1 原样复用。
     *
     * @param attackerName 仅用于 debug 日志的实体名
     * @param amount   结算前的基础伤害(经攻击链副作用后的值)
     * @param bonus    加伤层当前值(0.2 = +20%)
     * @param mult     乘算层当前值(1.0 = ×1)
     * @param flat     flat 层当前值(0.0 = 无)
     */
    public static float settle(String attackerName, float amount, double bonus, double mult, double flat) {
        if (bonus == 0.0D && mult == 1.0D && flat == 0.0D) return amount;
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[UnifiedBonus] attacker={} amount {} -> {}*{}*{}+{}={}",
                    attackerName, amount, amount, (1.0 + bonus), mult, flat,
                    amount * (1.0F + (float) bonus) * (float) mult + (float) flat);
        }
        return amount * (1.0F + (float) bonus) * (float) mult + (float) flat;
    }

    /**
     * 收到伤害结算(纯函数, 跨版本): final = amount × incoming。
     *
     * incoming 为受击者 incoming_damage 属性当前值(默认 1, 易伤&gt;1 减伤&lt;1),
     * 由平台层读值传入; 在护甲结算后乘一次(用户 round-incoming 口径)。
     *
     * @param defenderName 仅用于 debug 日志
     * @param amount   经护甲与保护结算后的伤害(攻击侧统一结算之后的值)
     * @param incoming 受击者 incoming_damage 当前值
     */
    public static float settleIncoming(String defenderName, float amount, double incoming) {
        if (incoming == 1.0D) return amount;
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[IncomingDamage] defender={} amount {} -> {}*{}={}",
                    defenderName, amount, amount, incoming, amount * (float) incoming);
        }
        return amount * (float) incoming;
    }

    /**
     * 收到伤害乘数写入: 把总乘积写入 incoming_damage 的单一聚合 modifier
     * (属性默认 1.0, ADD_VALUE 写 (product-1))。受击侧乘数类附魔经此聚合,
     * 结算时读属性值统一乘一次。重复调用覆盖该 id(先 remove 再 add)。
     *
     * @param inst    incoming_damage AttributeInstance(调用方取)
     * @param id      唯一聚合 modifier id(建议 "incoming_&lt;附魔&gt;")
     * @param product 乘积(1.0 = 无影响, 移除该 id)
     */
    public static void setIncomingDamage(net.minecraft.world.entity.ai.attributes.AttributeInstance inst, ResourceLocation id, double product) {
        if (inst == null) return;
        removeModifierById(inst, id);
        double delta = product - 1.0D;
        if (Math.abs(delta) > 0.0001D) {
            addValueModifier(inst, id, delta);
        }
    }

    /**
     * 加伤写入: 以独立 modifier id 累加百分比(ADD_VALUE)。同 id 重复调用 = 覆盖(先 remove 再 add)。
     *
     * @param inst    加伤层 AttributeInstance(调用方取: attacker.getAttribute(bonusAttr))
     * @param id      唯一 modifier id(建议 "bonus_<附魔>")
     * @param percent 增伤百分比(0.2 = +20%); 0 则仅移除
     */
    public static void addPercentBonus(net.minecraft.world.entity.ai.attributes.AttributeInstance inst, ResourceLocation id, double percent) {
        if (inst == null) return;
        removeModifierById(inst, id);
        if (percent != 0) {
            addValueModifier(inst, id, percent);
        }
    }

    /**
     * 乘伤写入: 把总乘积写入 damage_multiplier 的单一 modifier。
     * 属性默认 1.0, ADD_VALUE 只能做加法, 故写入 (multiplier-1), 使属性值 = multiplier。
     *
     * @param inst       乘算层 AttributeInstance(调用方取)
     * @param id         唯一 modifier id(建议 "mult_<附魔>")
     * @param multiplier 总乘积(1.0 = 无加成, 移除该 id)
     */
    public static void setDamageMultiplier(net.minecraft.world.entity.ai.attributes.AttributeInstance inst, ResourceLocation id, double multiplier) {
        if (inst == null) return;
        removeModifierById(inst, id);
        double delta = multiplier - 1.0D;
        if (Math.abs(delta) > 0.0001D) {
            addValueModifier(inst, id, delta);
        }
    }

    /**
     * flat 绝对加伤写入: 固定点数(ADD_VALUE)。
     *
     * @param inst  flat 层 AttributeInstance(调用方取; flatAttr 未接入时可传 null)
     * @param id    唯一 modifier id(建议 "flat_<附魔>")
     * @param value 固定加伤点数(绝对量)
     */
    public static void setFlatDamage(net.minecraft.world.entity.ai.attributes.AttributeInstance inst, ResourceLocation id, double value) {
        if (inst == null) return;
        removeModifierById(inst, id);
        if (Math.abs(value) > 0.0001D) {
            addValueModifier(inst, id, value);
        }
    }

    /**
     * 事件临时乘伤: 在统一结算前把事件乘积乘入 mult 层。
     *
     * 实现: 写入 delta = 当前属性值×(factor-1), 使结算读到"当前值×factor" —
     * 与 tick 聚合/目灯等既有 modifier 保持严格乘积(而非加和)。结算后必须 clear。
     *
     * @param inst   乘算层 AttributeInstance
     * @param id     临时 modifier id(每次事件用同一 id)
     * @param factor 事件乘积(1.0 = 无贡献)
     */
    public static void applyEventMultiplierTemporary(net.minecraft.world.entity.ai.attributes.AttributeInstance inst, ResourceLocation id, double factor) {
        if (inst == null) return;
        if (Math.abs(factor - 1.0D) < 0.0001D) return;
        removeModifierById(inst, id);
        double current = inst.getValue();
        double delta = current * (factor - 1.0D);
        addValueModifier(inst, id, delta);
    }

    /** 统一结算后清除事件临时乘伤(幂等; 无则不动)。 */
    public static void clearEventMultiplierTemporary(net.minecraft.world.entity.ai.attributes.AttributeInstance inst, ResourceLocation id) {
        if (inst == null) return;
        removeModifierById(inst, id);
    }

    /**
     * 事件临时加伤: 结算前把事件加伤百分比累加进 bonus 层(ADD_VALUE 百分比)。
     * 结算后必须 clear。
     */
    public static void applyEventBonusTemporary(net.minecraft.world.entity.ai.attributes.AttributeInstance inst, ResourceLocation id, double percent) {
        if (inst == null) return;
        if (Math.abs(percent) < 0.0001D) return;
        removeModifierById(inst, id);
        addValueModifier(inst, id, percent);
    }

    /** 统一结算后清除事件临时加伤(幂等)。 */
    public static void clearEventBonusTemporary(net.minecraft.world.entity.ai.attributes.AttributeInstance inst, ResourceLocation id) {
        if (inst == null) return;
        removeModifierById(inst, id);
    }
}
