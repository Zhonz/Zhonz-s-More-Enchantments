package com.zhonz.moreenchantments.common.enchant;

import com.zhonz.moreenchantments.common.CommonConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * 附魔 ResourceKey 常量(平台无关, stage2 下沉)。
 * 1.21 附魔为数据驱动: 只需 ResourceKey 引用; runtime Holder 解析
 * (需服务器 registry + 平台 hook)放在平台层 ModEnchantments(继承本类),
 * 故既有引用点(ModEnchantments.FINALE 等)零改动。
 */
public class EnchantmentKeys {

    protected static ResourceKey<Enchantment> key(String name) {
        return ResourceKey.create(Registries.ENCHANTMENT,
                ResourceLocation.fromNamespaceAndPath(CommonConstants.MODID, name));
    }

    // ===== Enchantment Resource Keys (由 ModEnchantments 键块下沉) =====
    public static final ResourceKey<Enchantment> FINALE = key("finale");
    public static final ResourceKey<Enchantment> SCAVENGER = key("scavenger");
    public static final ResourceKey<Enchantment> HARVEST = key("harvest");
    public static final ResourceKey<Enchantment> SANCTION = key("sanction");
    public static final ResourceKey<Enchantment> DEEP_SEAS_GRACE = key("deep_seas_grace");
    public static final ResourceKey<Enchantment> GEM_UMBRELLA = key("gem_umbrella");
    public static final ResourceKey<Enchantment> CHARGER = key("charger");
    public static final ResourceKey<Enchantment> FISHBALL = key("fishball");
    public static final ResourceKey<Enchantment> LIBERATOR = key("liberator");
    public static final ResourceKey<Enchantment> SUPREME_ART = key("supreme_art");
    public static final ResourceKey<Enchantment> MY_SEA_DOMAIN = key("my_sea_domain");
    public static final ResourceKey<Enchantment> AREA_STRIKE = key("area_strike");
    public static final ResourceKey<Enchantment> TOUGHNESS = key("toughness");
    public static final ResourceKey<Enchantment> SELF_DOUBT = key("self_doubt");
    public static final ResourceKey<Enchantment> THE_PURE = key("the_pure");
    public static final ResourceKey<Enchantment> BLOOD_WEEP = key("blood_weep");
    public static final ResourceKey<Enchantment> GRIEVOUS_WOUND = key("grievous_wound");
    public static final ResourceKey<Enchantment> SHELL_STRIP = key("shell_strip");
    public static final ResourceKey<Enchantment> ARMY_BREAKER = key("army_breaker");
    public static final ResourceKey<Enchantment> CRIMSON_HELLFIRE = key("crimson_hellfire");
    public static final ResourceKey<Enchantment> EMERGENCY_RESCUE = key("emergency_rescue");
    public static final ResourceKey<Enchantment> SUPPRESSION = key("suppression");
    public static final ResourceKey<Enchantment> PROPHETS_CALL = key("prophets_call");
    public static final ResourceKey<Enchantment> RETURN_FROM_HELL = key("return_from_hell");
    public static final ResourceKey<Enchantment> EXPLOSIVE_DAWN = key("explosive_dawn");
    public static final ResourceKey<Enchantment> FOOLS_MASK = key("fools_mask");
    public static final ResourceKey<Enchantment> HANG = key("hang");
    public static final ResourceKey<Enchantment> DIVINE_CURSE = key("divine_curse");
    public static final ResourceKey<Enchantment> FLEETING_GRACE = key("fleeting_grace");
    public static final ResourceKey<Enchantment> DIVINE_PROTECTION = key("divine_protection");
    public static final ResourceKey<Enchantment> FLIPPING_COIN = key("flipping_coin");
    public static final ResourceKey<Enchantment> MUST_OPEN_PATH = key("must_open_path");
    public static final ResourceKey<Enchantment> INCOMPLETE_FOREKNOWLEDGE_EYE = key("incomplete_foreknowledge_eye");
    public static final ResourceKey<Enchantment> BLOOD_PATH = key("blood_path");
    public static final ResourceKey<Enchantment> SMART_TOTEM = key("smart_totem");
    public static final ResourceKey<Enchantment> FLESH_SACRIFICE = key("flesh_sacrifice");
    public static final ResourceKey<Enchantment> BONE_BREAK = key("bone_break");
    public static final ResourceKey<Enchantment> FINAL_COUNTDOWN = key("final_countdown");
    public static final ResourceKey<Enchantment> ERASE_ME_ERASE_YOU = key("erase_me_erase_you");
    public static final ResourceKey<Enchantment> FLEET_FOOTSTEPS = key("fleet_footsteps");
    public static final ResourceKey<Enchantment> RHYTHM = key("rhythm");
    public static final ResourceKey<Enchantment> LONELY_NOON = key("lonely_noon");
    public static final ResourceKey<Enchantment> BURNING_DUSK = key("burning_dusk");
    public static final ResourceKey<Enchantment> WEEPING_CHILD = key("weeping_child");
    public static final ResourceKey<Enchantment> APEX = key("apex");
    public static final ResourceKey<Enchantment> CORNERED_BEAST = key("cornered_beast");
    public static final ResourceKey<Enchantment> VIOLENT_PULSE = key("violent_pulse");
    public static final ResourceKey<Enchantment> PATIENCE = key("patience");
    public static final ResourceKey<Enchantment> CEASELESS_HUNT = key("ceaseless_hunt");
    public static final ResourceKey<Enchantment> NEW_SUN = key("new_sun");
    public static final ResourceKey<Enchantment> SELF_BOUND = key("self_bound");
    public static final ResourceKey<Enchantment> RAPID_ASCENT = key("rapid_ascent");
    public static final ResourceKey<Enchantment> ACCELERATED_FUTURE = key("accelerated_future");
    public static final ResourceKey<Enchantment> HALT = key("halt");
    public static final ResourceKey<Enchantment> MANIFEST = key("manifest");
    public static final ResourceKey<Enchantment> PALE_MIDNIGHT = key("pale_midnight");
    public static final ResourceKey<Enchantment> SORROWFUL_RED = key("sorrowful_red");
    public static final ResourceKey<Enchantment> PRIMAL_SUFFERING = key("primal_suffering");
    public static final ResourceKey<Enchantment> ETERNAL_RETURN = key("eternal_return");
    public static final ResourceKey<Enchantment> LUXURIOUS_HOPE = key("luxurious_hope");
    public static final ResourceKey<Enchantment> PHOTOPHILE = key("photophile");
    public static final ResourceKey<Enchantment> PHOTOPHOBE = key("photophobe");
    public static final ResourceKey<Enchantment> ETIQUETTE = key("etiquette");
    public static final ResourceKey<Enchantment> PERFUNCTORY = key("perfunctory");
    public static final ResourceKey<Enchantment> SILENCE_IN_DEPTHS = key("silence_in_depths");
    public static final ResourceKey<Enchantment> FRENZIED_BITE = key("frenzied_bite");
    public static final ResourceKey<Enchantment> SILENCED_HEAVENFALL = key("silenced_heavenfall");
    public static final ResourceKey<Enchantment> AUTO_MERGE = key("auto_merge");
    public static final ResourceKey<Enchantment> FARMER_OVERSEER = key("farmer_overseer");
    public static final ResourceKey<Enchantment> FLOWER_BED = key("flower_bed");
    public static final ResourceKey<Enchantment> HALO = key("halo");
    public static final ResourceKey<Enchantment> GOLD_WINE_CUP = key("gold_wine_cup");
    public static final ResourceKey<Enchantment> SNOW_WOUND = key("snow_wound");
    public static final ResourceKey<Enchantment> SNOW_SORROW = key("snow_sorrow");
    public static final ResourceKey<Enchantment> UNYIELDING_FATE = key("unyielding_fate");
    public static final ResourceKey<Enchantment> TITAN = key("titan");
    public static final ResourceKey<Enchantment> KEEN_WILL = key("keen_will");
    public static final ResourceKey<Enchantment> SHARPEN = key("sharpen");
    public static final ResourceKey<Enchantment> HYPERTHYMESIA = key("hyperthymesia");
    public static final ResourceKey<Enchantment> ETERNAL_STANDING = key("eternal_standing");
    public static final ResourceKey<Enchantment> CITY_SHIELD = key("city_shield");
    public static final ResourceKey<Enchantment> EYE_LAMP = key("eye_lamp");
    public static final ResourceKey<Enchantment> SOLEMN_MOURNING = key("solemn_mourning");
    public static final ResourceKey<Enchantment> SHATTER = key("shatter");
    public static final ResourceKey<Enchantment> SOJOURNER = key("sojourner");
    public static final ResourceKey<Enchantment> WAYFARER = key("wayfarer");
    public static final ResourceKey<Enchantment> THIRTY_MILLION_TURNS = key("thirty_million_turns");
    public static final ResourceKey<Enchantment> HEAVEN_CHAIN = key("heaven_chain");
    public static final ResourceKey<Enchantment> MERCY_EQUAL = key("mercy_equal");
}
