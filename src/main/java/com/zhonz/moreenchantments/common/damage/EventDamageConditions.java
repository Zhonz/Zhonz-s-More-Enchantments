package com.zhonz.moreenchantments.common.damage;

import com.zhonz.moreenchantments.common.enchant.EnchantIds;
import com.zhonz.moreenchantments.common.storage.EntityDataStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.item.Items;

import java.util.Random;

/**
 * 攻击事件内"条件判定规则"(stage2 下沉): 乘伤乘积 / 加伤百分比 的纯计算。
 *
 * 平台差异点(等级查询、时序状态键、血路击杀 NBT)全部经 {@link EventDamageContext}
 * 注入/内聚, 本类不 import 任何 NeoForge/Forge 平台 API 与 1.21 专属物品组件 API。
 *
 * 语义(与事件层委托前一致):
 * <ul>
 *   <li>computeConditionalMultiplier — 事件条件型乘伤总乘积(liberator/titan/fools_mask/
 *       lonely_noon/weeping_child/rhythm/snow_wound), 返回 1.0 = 无贡献。</li>
 *   <li>computeBonusPercent — 事件条件型加伤总百分比(charger/my_sea_domain/blood_weep/
 *       army_breaker/blood_path/halt/pale_midnight/ceaseless_hunt), 返回 0.0 = 无贡献。</li>
 * </ul>
 */
public final class EventDamageConditions {

    private static final Random RANDOM = new Random();

    private EventDamageConditions() {
    }

    // ===== 事件条件型乘伤总乘积 =====

    /** 事件条件型乘伤总乘积(本事件内判定)。返回 1.0 = 无贡献。 */
    public static double computeConditionalMultiplier(EventDamageContext ctx,
                                                      LivingEntity attacker, LivingEntity defender) {
        double product = 1.0D;
        // 9. 解放者(Liberator): 距上次攻击越久倍率越高 ×(0.1~20), 乘伤通道(Q1 允许 <1)
        // 需在事件内更新 last-attack 计时(原 applyLiberator 副作用), 否则连续攻击计时不重置
        int liberatorLevel = ctx.levels.mainHand(attacker, EnchantIds.LIBERATOR);
        if (liberatorLevel > 0) {
            CompoundTag data = EntityDataStorage.getData(attacker);
            long currentTick = attacker.level().getGameTime();
            long lastAttackTick = data.getLong(ctx.keyLiberatorLastAttack);
            long elapsedTicks = lastAttackTick == 0 ? 0 : currentTick - lastAttackTick;
            double elapsedSeconds = elapsedTicks / 20.0;
            double multiplier;
            if (elapsedSeconds <= 5.0) {
                multiplier = 0.1;
            } else if (elapsedSeconds >= 400.0) {
                multiplier = 20.0;
            } else {
                multiplier = 0.1 + (20.0 - 0.1) * ((elapsedSeconds - 5.0) / (400.0 - 5.0));
            }
            data.putLong(ctx.keyLiberatorLastAttack, currentTick);
            product *= multiplier;
        }
        // 76. 泰坦(Titan): 对精英/BOSS 额外一次等额伤害 → ×2
        if (ctx.levels.mainHand(attacker, EnchantIds.TITAN) > 0 && isEliteOrBoss(defender, ctx.eliteTag)) {
            product *= 2.0D;
        }
        // 假面的愚者(Fools Mask, 头上): 随机乘伤 ×(1~3) 幸运 或 ×(0.01~1) 不幸(Q1 允许 <1)。
        // buff/debuff 副作用保留链上; 此处只按同一天平掷乘数。
        if (ctx.levels.slot(attacker, EnchantIds.FOOLS_MASK, EquipmentSlot.HEAD) > 0) {
            CompoundTag maskData = EntityDataStorage.getData(attacker);
            if (maskData.getBoolean(ctx.keyFoolsMaskLucky)) {
                product *= 1.0D + RANDOM.nextDouble() * RANDOM.nextDouble() * 2.0D;
            } else {
                product *= Math.max(0.01D, 1.0D - RANDOM.nextDouble() * RANDOM.nextDouble() * 0.99D);
            }
        }
        // 42. 孤独的正午(Lonely Noon): 对着火目标 ×1.5; 同持燃烧的黄昏 → ×2
        if (ctx.levels.mainHand(attacker, EnchantIds.LONELY_NOON) > 0 && defender.isOnFire()) {
            boolean dusk = ctx.levels.mainHand(attacker, EnchantIds.BURNING_DUSK) > 0;
            product *= dusk ? 2.0D : 1.5D;
        }
        // 44. 哭泣之子(Weeping Child): 点燃双方后, 自身燃烧 → ×3(Q2 乘伤);
        // 同时附魔孤独的正午+燃烧的黄昏 → 再 ×2(原逻辑, 点燃副作用在链上已先执行)
        if (ctx.levels.mainHand(attacker, EnchantIds.WEEPING_CHILD) > 0 && attacker.isOnFire()) {
            double wc = 3.0D;
            if (ctx.levels.mainHand(attacker, EnchantIds.LONELY_NOON) > 0
                    && ctx.levels.mainHand(attacker, EnchantIds.BURNING_DUSK) > 0) {
                wc *= 2.0D;
            }
            product *= wc;
        }
        // 41. 节奏(Rhythm): 攻击间隔命中(+50% ×1.5)标志由链上副作用写入, 读取后清除
        CompoundTag rhythmData = EntityDataStorage.getEntityData(attacker);
        if (rhythmData.getBoolean(ctx.keyRhythmHit)) {
            rhythmData.remove(ctx.keyRhythmHit);
            product *= 1.5D;
        }
        // 73. 雪的伤(Snow Wound): 雪天 → ×1.5; 同持雪的殇 → 再 ×1.5(乘叠 ×2.25)
        if (ctx.levels.mainHand(attacker, EnchantIds.SNOW_WOUND) > 0) {
            if (isSnowWeather(attacker)) product *= 1.5D;
            if (ctx.levels.mainHand(attacker, EnchantIds.SNOW_SORROW) > 0) product *= 1.5D;
        }
        return product;
    }

    // ===== 事件条件型加伤总百分比 =====

    /**
     * 事件条件型加伤总百分比(本事件内判定, 与 tick 加伤严格累加进 bonus_damage)。
     * 返回 0.0 = 无贡献。迁入项均为文档 "+X%" 加伤语义:
     * 7. 冲锋手(速度×等级×0.5)、11. 我的海疆(三叉戟 +60%)、16. 血泣(+20/30/45%)、
     * 19. 破军(目标低血 +10/20/30%)、34. 血路(同种击杀数×0.1%)、54. 止步(目标不动 +40%)、
     * 56. 惨白的午夜(+50%)、49. 不停狩(叠层 +5%/层)。
     * 自伤/标记等副作用保留在平台层链函数, 此处只累计加成部分。
     */
    public static double computeBonusPercent(EventDamageContext ctx,
                                             LivingEntity attacker, LivingEntity defender) {
        double percent = 0.0D;
        // 7. 冲锋手: 速度越快伤害越高(移动速度加成 0.1 格/tick ≈ 走路基准, ×等级×0.5)
        int chargerLevel = ctx.levels.mainHand(attacker, EnchantIds.CHARGER);
        if (chargerLevel > 0) {
            double speed = attacker.getDeltaMovement().horizontalDistance() / 0.1;
            percent += speed * chargerLevel * 0.5;
        }
        // 11. 我的海疆: 三叉戟攻击额外 +60%
        if (ctx.levels.mainHand(attacker, EnchantIds.MY_SEA_DOMAIN) > 0
                && attacker.getMainHandItem().getItem() == Items.TRIDENT) {
            percent += 0.60;
        }
        // 16. 血泣: +20/30/45%(自伤扣血副作用保留链上)
        int bloodWeepLevel = ctx.levels.mainHand(attacker, EnchantIds.BLOOD_WEEP);
        if (bloodWeepLevel > 0) {
            percent += bloodWeepLevel == 1 ? 0.20 : (bloodWeepLevel == 2 ? 0.30 : 0.45);
        }
        // 19. 破军: 目标血量低于阈值 → +10/20/30%
        int armyBreakerLevel = ctx.levels.mainHand(attacker, EnchantIds.ARMY_BREAKER);
        if (armyBreakerLevel > 0) {
            float threshold = armyBreakerLevel == 1 ? 0.30f : (armyBreakerLevel == 2 ? 0.40f : 0.50f);
            float bonus = armyBreakerLevel == 1 ? 0.10f : (armyBreakerLevel == 2 ? 0.20f : 0.30f);
            if (defender.getHealth() <= defender.getMaxHealth() * threshold) {
                percent += bonus;
            }
        }
        // 34. 血路: 对同种生物每击杀 +0.1%(击杀计数经 ctx 平台实现读取, 避免 common 依赖 1.21 NBT API)
        if (ctx.levels.mainHand(attacker, EnchantIds.BLOOD_PATH) > 0) {
            int kills = ctx.bloodPathKills.countOf(attacker, defender);
            percent += kills * 0.001;
        }
        // 54. 止步(Halt): 目标当前不移动 → 伤害 +40%(减速副作用保留链上)
        if (ctx.levels.mainHand(attacker, EnchantIds.HALT) > 0) {
            double hSpeed = Math.sqrt(defender.getDeltaMovement().x * defender.getDeltaMovement().x
                    + defender.getDeltaMovement().z * defender.getDeltaMovement().z);
            if (hSpeed < 0.01) {
                percent += 0.40;
            }
        }
        // 56. 惨白的午夜(Pale Midnight, 头盔): 攻击伤害 +50%(无条件, 发光/易伤副作用保留链上)
        if (ctx.levels.slot(attacker, EnchantIds.PALE_MIDNIGHT, EquipmentSlot.HEAD) > 0) {
            percent += 0.50;
        }
        // 49. 不停狩(Ceaseless Hunt): 连续战斗叠层, 每层伤害 +5%(叠层副作用保留平台层链函数)
        if (ctx.levels.mainHand(attacker, EnchantIds.CEASELESS_HUNT) > 0) {
            int stacks = EntityDataStorage.getData(attacker).getInt(ctx.keyCeaselessStacks);
            percent += 0.05 * stacks;
        }
        return percent;
    }

    // ===== tick 可判定的乘伤聚合 =====

    /**
     * tick/事件兜底刷新用"乘伤聚合乘积"(无条件自身状态类乘伤附魔的总乘积)。
     * 目前: bone_break(主手无条件 ×6)。事件条件型(泰坦等)不在此(见
     * computeConditionalMultiplier)。返回 1.0 = 无贡献。
     */
    public static double tickMultiplierProduct(EventDamageContext ctx, LivingEntity attacker) {
        double product = 1.0D;
        // 30. 骨碎(Bone Break): 主手持该武器攻击 ×6(无条件自身状态乘伤)
        if (ctx.levels.mainHand(attacker, EnchantIds.BONE_BREAK) > 0) {
            product *= 6.0D;
        }
        return product;
    }

    // ===== 纯 MC 辅助 =====

    /** 是否"雪天/雨天"判定(任意群系下雨视作下雪)。 */
    private static boolean isSnowWeather(LivingEntity entity) {
        return entity.level().isRaining();
    }

    /** 是否为精英/BOSS(泰坦判定): 龙/凋灵或带精英标记(键与 /tag 一致)。 */
    private static boolean isEliteOrBoss(LivingEntity entity, String eliteTag) {
        return entity instanceof EnderDragon || entity instanceof WitherBoss
                || entity.getPersistentData().getBoolean(eliteTag)
                // 兼容 /tag @s add zhonz_elite 指令标记
                || entity.getTags().contains(eliteTag);
    }
}
