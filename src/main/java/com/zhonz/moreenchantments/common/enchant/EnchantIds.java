package com.zhonz.moreenchantments.common.enchant;

/**
 * 附魔 id 常量(纯字符串, 跨版本稳定; stage 跨版本层)。
 *
 * common 判定规则用这些 id 经 EnchantmentLevelLookup 查询等级, 不直接引用
 * 平台键对象; 各平台把 id 映射到本版本注册(1.21: ResourceKey<Enchantment> +
 * Holder; 1.20.1: 代码注册的 Enchantment 引用)。取值 = 数据包附魔注册名,
 * 与平台层 EnchantmentKeys(ResourceKey 常量)一致, 但此处不依赖 RL 构造 API,
 * 故 1.20.1/1.21.1 均可编译。
 */
public final class EnchantIds {

    public static final String FINALE = "finale";
    public static final String SCAVENGER = "scavenger";
    public static final String HARVEST = "harvest";
    public static final String SANCTION = "sanction";
    public static final String DEEP_SEAS_GRACE = "deep_seas_grace";
    public static final String GEM_UMBRELLA = "gem_umbrella";
    public static final String CHARGER = "charger";
    public static final String FISHBALL = "fishball";
    public static final String LIBERATOR = "liberator";
    public static final String SUPREME_ART = "supreme_art";
    public static final String MY_SEA_DOMAIN = "my_sea_domain";
    public static final String AREA_STRIKE = "area_strike";
    public static final String TOUGHNESS = "toughness";
    public static final String SELF_DOUBT = "self_doubt";
    public static final String THE_PURE = "the_pure";
    public static final String BLOOD_WEEP = "blood_weep";
    public static final String GRIEVOUS_WOUND = "grievous_wound";
    public static final String SHELL_STRIP = "shell_strip";
    public static final String ARMY_BREAKER = "army_breaker";
    public static final String CRIMSON_HELLFIRE = "crimson_hellfire";
    public static final String EMERGENCY_RESCUE = "emergency_rescue";
    public static final String SUPPRESSION = "suppression";
    public static final String PROPHETS_CALL = "prophets_call";
    public static final String RETURN_FROM_HELL = "return_from_hell";
    public static final String EXPLOSIVE_DAWN = "explosive_dawn";
    public static final String FOOLS_MASK = "fools_mask";
    public static final String HANG = "hang";
    public static final String DIVINE_CURSE = "divine_curse";
    public static final String FLEETING_GRACE = "fleeting_grace";
    public static final String DIVINE_PROTECTION = "divine_protection";
    public static final String FLIPPING_COIN = "flipping_coin";
    public static final String MUST_OPEN_PATH = "must_open_path";
    public static final String INCOMPLETE_FOREKNOWLEDGE_EYE = "incomplete_foreknowledge_eye";
    public static final String BLOOD_PATH = "blood_path";
    public static final String SMART_TOTEM = "smart_totem";
    public static final String FLESH_SACRIFICE = "flesh_sacrifice";
    public static final String BONE_BREAK = "bone_break";
    public static final String FINAL_COUNTDOWN = "final_countdown";
    public static final String ERASE_ME_ERASE_YOU = "erase_me_erase_you";
    public static final String FLEET_FOOTSTEPS = "fleet_footsteps";
    public static final String RHYTHM = "rhythm";
    public static final String LONELY_NOON = "lonely_noon";
    public static final String BURNING_DUSK = "burning_dusk";
    public static final String WEEPING_CHILD = "weeping_child";
    public static final String APEX = "apex";
    public static final String CORNERED_BEAST = "cornered_beast";
    public static final String VIOLENT_PULSE = "violent_pulse";
    public static final String PATIENCE = "patience";
    public static final String CEASELESS_HUNT = "ceaseless_hunt";
    public static final String NEW_SUN = "new_sun";
    public static final String SELF_BOUND = "self_bound";
    public static final String RAPID_ASCENT = "rapid_ascent";
    public static final String ACCELERATED_FUTURE = "accelerated_future";
    public static final String HALT = "halt";
    public static final String MANIFEST = "manifest";
    public static final String PALE_MIDNIGHT = "pale_midnight";
    public static final String SORROWFUL_RED = "sorrowful_red";
    public static final String PRIMAL_SUFFERING = "primal_suffering";
    public static final String ETERNAL_RETURN = "eternal_return";
    public static final String LUXURIOUS_HOPE = "luxurious_hope";
    public static final String PHOTOPHILE = "photophile";
    public static final String PHOTOPHOBE = "photophobe";
    public static final String ETIQUETTE = "etiquette";
    public static final String PERFUNCTORY = "perfunctory";
    public static final String SILENCE_IN_DEPTHS = "silence_in_depths";
    public static final String FRENZIED_BITE = "frenzied_bite";
    public static final String SILENCED_HEAVENFALL = "silenced_heavenfall";
    public static final String AUTO_MERGE = "auto_merge";
    public static final String FARMER_OVERSEER = "farmer_overseer";
    public static final String FLOWER_BED = "flower_bed";
    public static final String HALO = "halo";
    public static final String GOLD_WINE_CUP = "gold_wine_cup";
    public static final String SNOW_WOUND = "snow_wound";
    public static final String SNOW_SORROW = "snow_sorrow";
    public static final String UNYIELDING_FATE = "unyielding_fate";
    public static final String TITAN = "titan";
    public static final String KEEN_WILL = "keen_will";
    public static final String SHARPEN = "sharpen";
    public static final String HYPERTHYMESIA = "hyperthymesia";
    public static final String ETERNAL_STANDING = "eternal_standing";
    public static final String CITY_SHIELD = "city_shield";
    public static final String EYE_LAMP = "eye_lamp";
    public static final String SOLEMN_MOURNING = "solemn_mourning";
    public static final String SHATTER = "shatter";
    public static final String SOJOURNER = "sojourner";
    public static final String WAYFARER = "wayfarer";
    public static final String THIRTY_MILLION_TURNS = "thirty_million_turns";
    public static final String HEAVEN_CHAIN = "heaven_chain";
    public static final String MERCY_EQUAL = "mercy_equal";

    private EnchantIds() {
    }
}
