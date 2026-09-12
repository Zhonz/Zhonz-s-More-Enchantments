package com.zhonz.moreenchantments.common.damage;

import com.zhonz.moreenchantments.common.enchant.EnchantIds;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * 暴击五件套(沉默沉入沉渊 / 狂热撕咬光芒 / 噤声击坠天堂 / 热烈诚挚希望 / 自私澄澈天光)的统一判定。
 *
 * <p>口径(用户确认): 在 {@link #SET_SLOTS 六个槽位}(头盔/胸甲/护腿/靴子/主手/副手)中,
 * 只要存在<b>除自己以外</b>的任意一件套装附魔, 即触发各自的"加强档 / 去上限";
 * <b>不要求 5 件齐全</b>。
 *
 * <p>本类保持平台无关: 附魔等级查询通过 {@link SlotLookup} 回调交给平台层
 * (1.21 用 Holder, 1.20.1 用 ForgeRegistries + EnchantmentHelper)。
 */
public final class EnchantSetPieces {

    /** 套装成员(附魔 id)。顺序固定: 沉默 / 狂热 / 噤声 / 热烈 / 自私。 */
    public static final List<String> MEMBERS = List.of(
            EnchantIds.SILENCE_IN_DEPTHS,
            EnchantIds.FRENZIED_BITE,
            EnchantIds.SILENCED_HEAVENFALL,
            EnchantIds.FERVENT_SINCERE_HOPE,
            EnchantIds.SELFISH_CLEAR_SKY);

    /** 参与判定的槽位: 护甲四件 + 正副手。 */
    public static final EquipmentSlot[] SET_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };

    private EnchantSetPieces() {
    }

    /** 平台层提供的"某槽位上某附魔的等级"查询。 */
    @FunctionalInterface
    public interface SlotLookup {
        int level(LivingEntity entity, String enchantId, EquipmentSlot slot);
    }

    /**
     * 该实体身上是否存在<b>除 {@code selfId} 以外</b>的套装附魔。
     *
     * @param selfId 当前附魔 id(沉默/狂热/噤声/热烈/自私 之一); 传 null 表示"任意成员"
     */
    public static boolean hasOtherPiece(LivingEntity entity, String selfId, SlotLookup lookup) {
        for (String member : MEMBERS) {
            if (member.equals(selfId)) continue;
            if (hasAnywhere(entity, member, lookup)) return true;
        }
        return false;
    }

    /** 该实体的六个槽位中是否存在指定套装附魔。 */
    public static boolean hasAnywhere(LivingEntity entity, String enchantId, SlotLookup lookup) {
        for (EquipmentSlot slot : SET_SLOTS) {
            if (lookup.level(entity, enchantId, slot) > 0) return true;
        }
        return false;
    }
}
