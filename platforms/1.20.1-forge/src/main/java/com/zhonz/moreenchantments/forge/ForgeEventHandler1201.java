package com.zhonz.moreenchantments.forge;

import com.zhonz.moreenchantments.common.damage.EnchantmentLevelLookup;
import com.zhonz.moreenchantments.common.damage.EventDamageConditions;
import com.zhonz.moreenchantments.common.damage.EventDamageContext;
import com.zhonz.moreenchantments.common.damage.TickBonusRules;
import com.zhonz.moreenchantments.common.damage.UnifiedDamageEngine;
import com.zhonz.moreenchantments.common.storage.EntityDataStorage;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 1.20.1 Forge 平台: 属性通道驱动的伤害结算(基于 common 规则)。
 *
 * - tick: 无条件/自身态加伤(TickBonusRules)写入 bonus 通道
 * - 伤害事件: 攻击者判定(事件条件乘伤/加伤)→ 临时 modifier → 统一 settle → 清除
 * 附魔 id 键: EntityDataStorage 键与 1.21 主工程一致(字符串)。
 */
public final class ForgeEventHandler1201 {

    private static final String KEY_RHYTHM_HIT_ATTACK = "zhonz_rhythm_hit_attack";
    private static final String KEY_FOOLS_MASK_LUCKY = "zhonz_fools_mask_lucky";
    private static final String KEY_LIBERATOR_LAST_ATTACK = "zhonz_liberator_last_attack";
    private static final String KEY_CEASELESS_STACKS = "zhonz_ceaseless_stacks";
    private static final String ELITE_TAG = "zhonz_elite";

    private static final EventDamageContext CTX = new EventDamageContext(
            EnchantmentLookup1201.INSTANCE,
            (a, d) -> bloodPathKills(a, d),
            KEY_LIBERATOR_LAST_ATTACK, KEY_FOOLS_MASK_LUCKY,
            KEY_RHYTHM_HIT_ATTACK, KEY_CEASELESS_STACKS, ELITE_TAG);

    private static int bloodPathKills(LivingEntity attacker, LivingEntity defender) {
        // 1.20.1: 血路击杀存于武器 NBT(zhonz_blood_path_kills), 读取同 mob 类型击杀数
        net.minecraft.nbt.CompoundTag tag = attacker.getMainHandItem().getOrCreateTag();
        if (!tag.contains("zhonz_blood_path_kills", net.minecraft.nbt.CompoundTag.TAG_COMPOUND)) return 0;
        net.minecraft.nbt.CompoundTag kills = tag.getCompound("zhonz_blood_path_kills");
        String mobKey = net.minecraft.world.entity.EntityType.getKey(defender.getType()).toString();
        return kills.contains(mobKey, net.minecraft.nbt.CompoundTag.TAG_INT) ? kills.getInt(mobKey) : 0;
    }

    private ForgeEventHandler1201() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide()) return;
        EnchantmentLevelLookup lv = EnchantmentLookup1201.INSTANCE;

        // tick 加伤(percent 计算下沉 common)
        UnifiedDamageEngine.addPercentBonus(
                player.getAttribute(ZhonzAttributes1201.BONUS_DAMAGE.get()),
                new net.minecraft.resources.ResourceLocation(CommonConstants1201.MODID, "bonus_supreme_art"),
                TickBonusRules.supremeArt(lv, player));
        UnifiedDamageEngine.addPercentBonus(
                player.getAttribute(ZhonzAttributes1201.BONUS_DAMAGE.get()),
                new net.minecraft.resources.ResourceLocation(CommonConstants1201.MODID, "bonus_new_sun"),
                TickBonusRules.newSun(lv, player));
        UnifiedDamageEngine.addPercentBonus(
                player.getAttribute(ZhonzAttributes1201.BONUS_DAMAGE.get()),
                new net.minecraft.resources.ResourceLocation(CommonConstants1201.MODID, "bonus_cornered_beast"),
                TickBonusRules.corneredBeast(lv, player));
        UnifiedDamageEngine.addPercentBonus(
                player.getAttribute(ZhonzAttributes1201.BONUS_DAMAGE.get()),
                new net.minecraft.resources.ResourceLocation(CommonConstants1201.MODID, "bonus_rapid_ascent"),
                TickBonusRules.rapidAscent(lv, player));
        UnifiedDamageEngine.addPercentBonus(
                player.getAttribute(ZhonzAttributes1201.BONUS_DAMAGE.get()),
                new net.minecraft.resources.ResourceLocation(CommonConstants1201.MODID, "bonus_sorrowful_red"),
                TickBonusRules.sorrowfulRed(lv, player));
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity defender = event.getEntity();
        if (defender.level().isClientSide()) return;
        net.minecraft.world.damagesource.DamageSource source = event.getSource();
        float amount = event.getAmount();
        if (amount <= 0.0F) return;

        if (source.getEntity() instanceof LivingEntity attacker) {
            // 乘伤聚合(bone_break ×6 等, 见 common)
            UnifiedDamageEngine.setDamageMultiplier(
                    attacker.getAttribute(ZhonzAttributes1201.DAMAGE_MULTIPLIER.get()),
                    new net.minecraft.resources.ResourceLocation(CommonConstants1201.MODID, "unified_mult_aggregate"),
                    EventDamageConditions.tickMultiplierProduct(CTX, attacker));

            // 事件条件判定
            double eventMult = EventDamageConditions.computeConditionalMultiplier(CTX, attacker, defender);
            UnifiedDamageEngine.applyEventMultiplierTemporary(
                    attacker.getAttribute(ZhonzAttributes1201.DAMAGE_MULTIPLIER.get()),
                    new net.minecraft.resources.ResourceLocation(CommonConstants1201.MODID, "event_mult_temp"),
                    eventMult);
            double eventBonus = EventDamageConditions.computeBonusPercent(CTX, attacker, defender);
            UnifiedDamageEngine.applyEventBonusTemporary(
                    attacker.getAttribute(ZhonzAttributes1201.BONUS_DAMAGE.get()),
                    new net.minecraft.resources.ResourceLocation(CommonConstants1201.MODID, "event_bonus_temp"),
                    eventBonus);

            // 统一结算
            double bonus = attacker.getAttributeValue(ZhonzAttributes1201.BONUS_DAMAGE.get());
            double mult = attacker.getAttributeValue(ZhonzAttributes1201.DAMAGE_MULTIPLIER.get());
            double flat = attacker.getAttributeValue(ZhonzAttributes1201.FLAT_DAMAGE.get());
            amount = UnifiedDamageEngine.settle(attacker.getName().getString(), amount, bonus, mult, flat);

            UnifiedDamageEngine.clearEventMultiplierTemporary(
                    attacker.getAttribute(ZhonzAttributes1201.DAMAGE_MULTIPLIER.get()),
                    new net.minecraft.resources.ResourceLocation(CommonConstants1201.MODID, "event_mult_temp"));
            UnifiedDamageEngine.clearEventBonusTemporary(
                    attacker.getAttribute(ZhonzAttributes1201.BONUS_DAMAGE.get()),
                    new net.minecraft.resources.ResourceLocation(CommonConstants1201.MODID, "event_bonus_temp"));

            // 攻击命中副作用(批 B, 以最终结算后 amount 为基准, 与 1.21 架构一致)
            amount = SideEffectsBatch1.applySanction(attacker, defender, amount);
            SideEffectsBatch1.applyShellStrip(attacker, defender, amount);
            SideEffectsBatch1.applySelfDoubt(attacker, defender);
            SideEffectsBatch1.applyFlippingCoin(attacker, defender);
            SideEffectsBatch1.applyGrievousWound(attacker, defender);
            SideEffectsBatch1.applyAreaStrike(attacker, defender, source, attacker.getMainHandItem(), amount);
            SideEffectsBatch1.applyExplosiveDawn(attacker, defender, source, attacker.getMainHandItem(), amount);
            SideEffectsBatch1.applyWeepingChildIgnite(attacker, defender);
            SideEffectsBatch1.applyBurningDusk(attacker, defender);
            SideEffectsBatch1.applyHaltSlow(attacker, defender);
            SideEffectsBatch1.applyPaleMidnightMark(attacker, defender);
            SideEffectsBatch1.applyFoolsMaskSideEffects(attacker, defender);
            SideEffectsBatch1.applySuppression(attacker, defender);

            event.setAmount(amount);
        }
        // 收到伤害通道(round-incoming): 最终受到伤害 = 护甲后伤害 × defender.incoming_damage
        // (易伤>1 减伤<1; 1.20.1 平台: 效果已由各 Batch 按 1.20.1 语义挂接, 属性注册于 ZhonzAttributes1201)
        double incoming = defender.getAttributeValue(ZhonzAttributes1201.INCOMING_DAMAGE.get());
        amount = UnifiedDamageEngine.settleIncoming(defender.getName().getString(), event.getAmount(), incoming);
        event.setAmount(amount);
    }
}
