package com.zhonz.moreenchantments.neoforge1201;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 1.20.1 Forge 平台: 89 附魔代码注册(自动生成自 data JSON supported_items 映射)。
 * 效果逻辑在平台事件层适配(规则复用 common); category 由 supported_items tag 宽松映射。
 */
public final class ModEnchantments1201 {

    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, CommonConstants1201.MODID);

    // ===== 全量附魔(自动生成)=====
    public static final RegistryObject<Enchantment> ACCELERATED_FUTURE = ENCHANTMENTS.register("accelerated_future",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR_HEAD,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> APEX = ENCHANTMENTS.register("apex",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> AREA_STRIKE = ENCHANTMENTS.register("area_strike",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.BOW,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> ARMY_BREAKER = ENCHANTMENTS.register("army_breaker",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> AUTO_MERGE = ENCHANTMENTS.register("auto_merge",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.DIGGER,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> BLOOD_PATH = ENCHANTMENTS.register("blood_path",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> BLOOD_WEEP = ENCHANTMENTS.register("blood_weep",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> BONE_BREAK = ENCHANTMENTS.register("bone_break",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> BURNING_DUSK = ENCHANTMENTS.register("burning_dusk",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> CEASELESS_HUNT = ENCHANTMENTS.register("ceaseless_hunt",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> CHARGER = ENCHANTMENTS.register("charger",
            () -> new Enchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {});
    public static final RegistryObject<Enchantment> CITY_SHIELD = ENCHANTMENTS.register("city_shield",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEARABLE,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> CORNERED_BEAST = ENCHANTMENTS.register("cornered_beast",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR_HEAD,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> CRIMSON_HELLFIRE = ENCHANTMENTS.register("crimson_hellfire",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR_CHEST,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> DEEP_SEAS_GRACE = ENCHANTMENTS.register("deep_seas_grace",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> DIVINE_CURSE = ENCHANTMENTS.register("divine_curse",
            () -> new CurseEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.BREAKABLE,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {});
    public static final RegistryObject<Enchantment> DIVINE_PROTECTION = ENCHANTMENTS.register("divine_protection",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> EMERGENCY_RESCUE = ENCHANTMENTS.register("emergency_rescue",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR_LEGS,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> ERASE_ME_ERASE_YOU = ENCHANTMENTS.register("erase_me_erase_you",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> ETERNAL_RETURN = ENCHANTMENTS.register("eternal_return",
            // 载体限定: 1.21 的 supported_items 在 1.20.1 无对应字段 → 覆写 canEnchant(见 ItemBoundEnchantment)
            () -> new TreasureItemBoundEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEARABLE,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET},
                    stack -> stack.is(Items.TOTEM_OF_UNDYING)));
    public static final RegistryObject<Enchantment> ETERNAL_STANDING = ENCHANTMENTS.register("eternal_standing",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> ETIQUETTE = ENCHANTMENTS.register("etiquette",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> EXPLOSIVE_DAWN = ENCHANTMENTS.register("explosive_dawn",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.CROSSBOW,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> EYE_LAMP = ENCHANTMENTS.register("eye_lamp",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> FARMER_OVERSEER = ENCHANTMENTS.register("farmer_overseer",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.DIGGER,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> FINAL_COUNTDOWN = ENCHANTMENTS.register("final_countdown",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> FINALE = ENCHANTMENTS.register("finale",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> FISHBALL = ENCHANTMENTS.register("fishball",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR_CHEST,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> FLEET_FOOTSTEPS = ENCHANTMENTS.register("fleet_footsteps",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR_FEET,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> FLEETING_GRACE = ENCHANTMENTS.register("fleeting_grace",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> FLESH_SACRIFICE = ENCHANTMENTS.register("flesh_sacrifice",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> FLIPPING_COIN = ENCHANTMENTS.register("flipping_coin",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> FLOWER_BED = ENCHANTMENTS.register("flower_bed",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR_FEET,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> FOOLS_MASK = ENCHANTMENTS.register("fools_mask",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR_HEAD,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> FRENZIED_BITE = ENCHANTMENTS.register("frenzied_bite",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> GEM_UMBRELLA = ENCHANTMENTS.register("gem_umbrella",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR_LEGS,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> GOLD_WINE_CUP = ENCHANTMENTS.register("gold_wine_cup",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.BREAKABLE,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> GRIEVOUS_WOUND = ENCHANTMENTS.register("grievous_wound",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> HALO = ENCHANTMENTS.register("halo",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR_HEAD,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> HALT = ENCHANTMENTS.register("halt",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> HANG = ENCHANTMENTS.register("hang",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.BREAKABLE,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> HARVEST = ENCHANTMENTS.register("harvest",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> HEAVEN_CHAIN = ENCHANTMENTS.register("heaven_chain",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.BOW,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> HYPERTHYMESIA = ENCHANTMENTS.register("hyperthymesia",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> INCOMPLETE_FOREKNOWLEDGE_EYE = ENCHANTMENTS.register("incomplete_foreknowledge_eye",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR_HEAD,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> KEEN_WILL = ENCHANTMENTS.register("keen_will",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> LIBERATOR = ENCHANTMENTS.register("liberator",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> LONELY_NOON = ENCHANTMENTS.register("lonely_noon",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> LUXURIOUS_HOPE = ENCHANTMENTS.register("luxurious_hope",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> MANIFEST = ENCHANTMENTS.register("manifest",
            // 载体限定: 1.21 的 supported_items 在 1.20.1 无对应字段 → 覆写 canEnchant(见 ItemBoundEnchantment)
            () -> new TreasureItemBoundEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEARABLE,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET},
                    stack -> stack.is(Items.TOTEM_OF_UNDYING)));
    public static final RegistryObject<Enchantment> MERCY_EQUAL = ENCHANTMENTS.register("mercy_equal",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.BREAKABLE,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> MUST_OPEN_PATH = ENCHANTMENTS.register("must_open_path",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> MY_SEA_DOMAIN = ENCHANTMENTS.register("my_sea_domain",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.TRIDENT,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> NEW_SUN = ENCHANTMENTS.register("new_sun",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR_LEGS,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> PALE_MIDNIGHT = ENCHANTMENTS.register("pale_midnight",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR_HEAD,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> PATIENCE = ENCHANTMENTS.register("patience",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEARABLE,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> PERFUNCTORY = ENCHANTMENTS.register("perfunctory",
            () -> new CurseEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.BREAKABLE,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {});
    public static final RegistryObject<Enchantment> PHOTOPHILE = ENCHANTMENTS.register("photophile",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR_HEAD,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> PHOTOPHOBE = ENCHANTMENTS.register("photophobe",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR_HEAD,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> PRIMAL_SUFFERING = ENCHANTMENTS.register("primal_suffering",
            () -> new Enchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {});
    public static final RegistryObject<Enchantment> PROPHETS_CALL = ENCHANTMENTS.register("prophets_call",
            // 载体限定: 1.21 的 supported_items 在 1.20.1 无对应字段 → 覆写 canEnchant(见 ItemBoundEnchantment)
            () -> new TreasureItemBoundEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEARABLE,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET},
                    stack -> stack.is(Items.GOAT_HORN)));
    public static final RegistryObject<Enchantment> RAPID_ASCENT = ENCHANTMENTS.register("rapid_ascent",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR_FEET,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> RETURN_FROM_HELL = ENCHANTMENTS.register("return_from_hell",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR_FEET,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> RHYTHM = ENCHANTMENTS.register("rhythm",
            () -> new Enchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {});
    public static final RegistryObject<Enchantment> SANCTION = ENCHANTMENTS.register("sanction",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> SCAVENGER = ENCHANTMENTS.register("scavenger",
            () -> new Enchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR_HEAD,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {});
    public static final RegistryObject<Enchantment> SELF_BOUND = ENCHANTMENTS.register("self_bound",
            () -> new CurseEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR_LEGS,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {});
    public static final RegistryObject<Enchantment> SELF_DOUBT = ENCHANTMENTS.register("self_doubt",
            () -> new Enchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {});
    public static final RegistryObject<Enchantment> SHARPEN = ENCHANTMENTS.register("sharpen",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> SHATTER = ENCHANTMENTS.register("shatter",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> SHELL_STRIP = ENCHANTMENTS.register("shell_strip",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> SILENCE_IN_DEPTHS = ENCHANTMENTS.register("silence_in_depths",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> SILENCED_HEAVENFALL = ENCHANTMENTS.register("silenced_heavenfall",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> SMART_TOTEM = ENCHANTMENTS.register("smart_totem",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR_CHEST,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> SNOW_SORROW = ENCHANTMENTS.register("snow_sorrow",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> SNOW_WOUND = ENCHANTMENTS.register("snow_wound",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> SOJOURNER = ENCHANTMENTS.register("sojourner",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR_FEET,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> SOLEMN_MOURNING = ENCHANTMENTS.register("solemn_mourning",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.BOW,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> SORROWFUL_RED = ENCHANTMENTS.register("sorrowful_red",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR_CHEST,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> SUPPRESSION = ENCHANTMENTS.register("suppression",
            () -> new Enchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.TRIDENT,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {});
    public static final RegistryObject<Enchantment> SUPREME_ART = ENCHANTMENTS.register("supreme_art",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> THE_PURE = ENCHANTMENTS.register("the_pure",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR_LEGS,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> THIRTY_MILLION_TURNS = ENCHANTMENTS.register("thirty_million_turns",
            // 载体限定: 1.21 的 supported_items 在 1.20.1 无对应字段 → 覆写 canEnchant(见 ItemBoundEnchantment)
            () -> new TreasureItemBoundEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEARABLE,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET},
                    stack -> stack.is(Items.NETHER_STAR)));
    public static final RegistryObject<Enchantment> TITAN = ENCHANTMENTS.register("titan",
            () -> new Enchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {});
    public static final RegistryObject<Enchantment> TOUGHNESS = ENCHANTMENTS.register("toughness",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEARABLE,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> UNYIELDING_FATE = ENCHANTMENTS.register("unyielding_fate",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> VIOLENT_PULSE = ENCHANTMENTS.register("violent_pulse",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR_CHEST,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> WAYFARER = ENCHANTMENTS.register("wayfarer",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR_FEET,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> WEEPING_CHILD = ENCHANTMENTS.register("weeping_child",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> FERVENT_SINCERE_HOPE = ENCHANTMENTS.register("fervent_sincere_hope",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR_CHEST,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));
    public static final RegistryObject<Enchantment> SELFISH_CLEAR_SKY = ENCHANTMENTS.register("selfish_clear_sky",
            () -> new TreasureEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR_CHEST,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}));

    private ModEnchantments1201() {
    }
}
