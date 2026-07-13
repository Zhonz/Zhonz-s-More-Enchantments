package com.zhonz.moreenchantments.enchantment;

import com.zhonz.moreenchantments.ZhonzMoreEnchantments;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCost;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Optional;

public class ModEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(Registries.ENCHANTMENT, ZhonzMoreEnchantments.MODID);

    // ===== Helper Methods =====

    private static ResourceKey<Enchantment> key(String name) {
        return ResourceKey.create(Registries.ENCHANTMENT,
                ResourceLocation.fromNamespaceAndPath(ZhonzMoreEnchantments.MODID, name));
    }

    /** Minecraft vanilla enchantable tag (e.g. "enchantable/weapon") */
    private static TagKey<Item> enchantTag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("minecraft", "enchantable/" + path));
    }

    /** Mod-specific enchantable tag (e.g. "enchantable/shield") */
    private static TagKey<Item> modEnchantTag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(ZhonzMoreEnchantments.MODID, "enchantable/" + path));
    }

    private static HolderSet<Item> tag(TagKey<Item> tagKey) {
        return new HolderSet.Named<>(BuiltInRegistries.ITEM.asLookup(), tagKey);
    }

    private static Optional<HolderSet<Item>> primary(HolderSet<Item> items) {
        return Optional.of(items);
    }

    // ===== Pre-defined Item Sets =====

    // MELEE: swords + axes for weapon = "enchantable/weapon"
    private static final HolderSet<Item> MELEE = tag(enchantTag("weapon"));

    // RANGED: bow + crossbow + trident
    private static final HolderSet<Item> BOWS = tag(enchantTag("bow"));
    private static final HolderSet<Item> CROSSBOWS = tag(enchantTag("crossbow"));
    private static final HolderSet<Item> TRIDENTS = tag(enchantTag("trident"));
    private static final HolderSet<Item> RANGED = HolderSet.union(BOWS, HolderSet.union(CROSSBOWS, TRIDENTS));

    // ALL_WEAPONS: melee + ranged
    private static final HolderSet<Item> ALL_WEAPONS = HolderSet.union(MELEE, RANGED);

    // ARMOR pieces
    private static final HolderSet<Item> ARMOR = tag(enchantTag("armor"));
    private static final HolderSet<Item> ARMOR_HEAD = tag(enchantTag("armor.head"));
    private static final HolderSet<Item> ARMOR_CHEST = tag(enchantTag("armor.chest"));
    private static final HolderSet<Item> ARMOR_LEGS = tag(enchantTag("armor.legs"));
    private static final HolderSet<Item> ARMOR_FEET = tag(enchantTag("armor.feet"));

    // MACE
    private static final HolderSet<Item> MACE = tag(enchantTag("mace"));

    // SHIELD (mod custom tag)
    private static final HolderSet<Item> SHIELD = tag(modEnchantTag("shield"));

    // GOAT_HORN (mod custom tag)
    private static final HolderSet<Item> GOAT_HORN = tag(modEnchantTag("goat_horn"));

    // ARMOR + SHIELD
    private static final HolderSet<Item> ARMOR_AND_SHIELD = HolderSet.union(ARMOR, SHIELD);

    // ARMOR_CHEST + SHIELD
    private static final HolderSet<Item> CHEST_AND_SHIELD = HolderSet.union(ARMOR_CHEST, SHIELD);

    // ANY_ENCHANTABLE: vanishable tag covers all enchantable items
    private static final HolderSet<Item> ANY_ENCHANTABLE = tag(enchantTag("vanishable"));

    // ===== Enchantment Registrations =====

    // 1. 终结 - Finale: Execute effect when target is low HP. Treasure, melee, 1 level.
    public static final DeferredHolder<Enchantment, Enchantment> FINALE = ENCHANTMENTS.register("finale",
            () -> Enchantment.enchantment(new Enchantment.Definition(
                    MELEE, Optional.empty(),
                    1, 1,
                    EnchantmentCost.of(30, 0), EnchantmentCost.of(50, 0),
                    5, EquipmentSlotGroup.MAINHAND
            )).build(key("finale")));

    // 2. 食腐者 - Scavenger: Duplicate loot on kill. Normal, helmet, 1 level.
    public static final DeferredHolder<Enchantment, Enchantment> SCAVENGER = ENCHANTMENTS.register("scavenger",
            () -> Enchantment.enchantment(new Enchantment.Definition(
                    ARMOR_HEAD, primary(ARMOR_HEAD),
                    5, 1,
                    EnchantmentCost.of(10, 0), EnchantmentCost.of(25, 0),
                    3, EquipmentSlotGroup.HEAD
            )).build(key("scavenger")));

    // 3. 收割 - Harvest: Bonus damage based on target missing HP. Treasure, melee, 3 levels.
    public static final DeferredHolder<Enchantment, Enchantment> HARVEST = ENCHANTMENTS.register("harvest",
            () -> Enchantment.enchantment(new Enchantment.Definition(
                    MELEE, Optional.empty(),
                    1, 3,
                    EnchantmentCost.of(30, 8), EnchantmentCost.of(50, 8),
                    5, EquipmentSlotGroup.MAINHAND
            )).build(key("harvest")));

    // 4. 制裁 - Sanction: Bonus damage against illagers. Treasure, all weapons, 3 levels.
    public static final DeferredHolder<Enchantment, Enchantment> SANCTION = ENCHANTMENTS.register("sanction",
            () -> Enchantment.enchantment(new Enchantment.Definition(
                    ALL_WEAPONS, Optional.empty(),
                    1, 3,
                    EnchantmentCost.of(30, 8), EnchantmentCost.of(50, 8),
                    5, EquipmentSlotGroup.MAINHAND
            )).build(key("sanction")));

    // 5. 深海的供养 - Deep Sea's Grace: Underwater healing and speed. Treasure, armor+shield, 3 levels.
    public static final DeferredHolder<Enchantment, Enchantment> DEEP_SEAS_GRACE = ENCHANTMENTS.register("deep_seas_grace",
            () -> Enchantment.enchantment(new Enchantment.Definition(
                    ARMOR_AND_SHIELD, Optional.empty(),
                    1, 3,
                    EnchantmentCost.of(30, 8), EnchantmentCost.of(50, 8),
                    5, EquipmentSlotGroup.ANY
            )).build(key("deep_seas_grace")));

    // 6. 宝石伞 - Gem Umbrella: Reduce projectile damage. Treasure, leggings, 1 level.
    public static final DeferredHolder<Enchantment, Enchantment> GEM_UMBRELLA = ENCHANTMENTS.register("gem_umbrella",
            () -> Enchantment.enchantment(new Enchantment.Definition(
                    ARMOR_LEGS, Optional.empty(),
                    1, 1,
                    EnchantmentCost.of(30, 0), EnchantmentCost.of(50, 0),
                    5, EquipmentSlotGroup.LEGS
            )).build(key("gem_umbrella")));

    // 7. 冲锋手 - Charger: Sprint attack bonus damage. Normal, melee, 1 level.
    public static final DeferredHolder<Enchantment, Enchantment> CHARGER = ENCHANTMENTS.register("charger",
            () -> Enchantment.enchantment(new Enchantment.Definition(
                    MELEE, primary(MELEE),
                    5, 1,
                    EnchantmentCost.of(10, 0), EnchantmentCost.of(25, 0),
                    3, EquipmentSlotGroup.MAINHAND
            )).build(key("charger")));

    // 8. 鱼丸 - Fishball: Better fishing loot. Treasure, chestplate+shield, 1 level.
    public static final DeferredHolder<Enchantment, Enchantment> FISHBALL = ENCHANTMENTS.register("fishball",
            () -> Enchantment.enchantment(new Enchantment.Definition(
                    CHEST_AND_SHIELD, Optional.empty(),
                    1, 1,
                    EnchantmentCost.of(30, 0), EnchantmentCost.of(50, 0),
                    5, EquipmentSlotGroup.BODY
            )).build(key("fishball")));

    // 9. 解放者 - Liberator: Remove debuffs on hit. Treasure, all weapons, 1 level.
    public static final DeferredHolder<Enchantment, Enchantment> LIBERATOR = ENCHANTMENTS.register("liberator",
            () -> Enchantment.enchantment(new Enchantment.Definition(
                    ALL_WEAPONS, Optional.empty(),
                    1, 1,
                    EnchantmentCost.of(30, 0), EnchantmentCost.of(50, 0),
                    5, EquipmentSlotGroup.MAINHAND
            )).build(key("liberator")));

    // 10. 至高之术 - Supreme Art: Chance for triple damage. Treasure, all weapons, 2 levels.
    public static final DeferredHolder<Enchantment, Enchantment> SUPREME_ART = ENCHANTMENTS.register("supreme_art",
            () -> Enchantment.enchantment(new Enchantment.Definition(
                    ALL_WEAPONS, Optional.empty(),
                    1, 2,
                    EnchantmentCost.of(30, 10), EnchantmentCost.of(50, 10),
                    5, EquipmentSlotGroup.MAINHAND
            )).build(key("supreme_art")));

    // 11. 我的海疆 - My Sea Domain: Water damage bonus. Treasure, trident, 1 level.
    public static final DeferredHolder<Enchantment, Enchantment> MY_SEA_DOMAIN = ENCHANTMENTS.register("my_sea_domain",
            () -> Enchantment.enchantment(new Enchantment.Definition(
                    TRIDENTS, Optional.empty(),
                    1, 1,
                    EnchantmentCost.of(30, 0), EnchantmentCost.of(50, 0),
                    5, EquipmentSlotGroup.MAINHAND
            )).build(key("my_sea_domain")));

    // 12. 群体打击 - Area Strike: AOE damage on attack. Treasure, ranged, 3 levels.
    public static final DeferredHolder<Enchantment, Enchantment> AREA_STRIKE = ENCHANTMENTS.register("area_strike",
            () -> Enchantment.enchantment(new Enchantment.Definition(
                    RANGED, Optional.empty(),
                    1, 3,
                    EnchantmentCost.of(30, 8), EnchantmentCost.of(50, 8),
                    5, EquipmentSlotGroup.MAINHAND
            )).build(key("area_strike")));

    // 13. 坚韧 - Toughness: Reduce all incoming damage. Treasure, shield, 1 level.
    public static final DeferredHolder<Enchantment, Enchantment> TOUGHNESS = ENCHANTMENTS.register("toughness",
            () -> Enchantment.enchantment(new Enchantment.Definition(
                    SHIELD, Optional.empty(),
                    1, 1,
                    EnchantmentCost.of(30, 0), EnchantmentCost.of(50, 0),
                    5, EquipmentSlotGroup.MAINHAND
            )).build(key("toughness")));

    // 14. 可是我的自卑胜过了一切爱我的 - Self Doubt: Curse - reduce outgoing damage. Curse, all weapons, 1 level.
    public static final DeferredHolder<Enchantment, Enchantment> SELF_DOUBT = ENCHANTMENTS.register("self_doubt",
            () -> Enchantment.enchantment(new Enchantment.Definition(
                    ALL_WEAPONS, primary(ALL_WEAPONS),
                    1, 1,
                    EnchantmentCost.of(25, 0), EnchantmentCost.of(50, 0),
                    5, EquipmentSlotGroup.MAINHAND
            )).build(key("self_doubt")));

    // 15. 无垢之人 - The Pure: Immune to negative effects. Treasure, leggings, 1 level.
    public static final DeferredHolder<Enchantment, Enchantment> THE_PURE = ENCHANTMENTS.register("the_pure",
            () -> Enchantment.enchantment(new Enchantment.Definition(
                    ARMOR_LEGS, Optional.empty(),
                    1, 1,
                    EnchantmentCost.of(30, 0), EnchantmentCost.of(50, 0),
                    5, EquipmentSlotGroup.LEGS
            )).build(key("the_pure")));

    // 16. 血泣 - Blood Weep: Lifesteal. Treasure, melee, 3 levels.
    public static final DeferredHolder<Enchantment, Enchantment> BLOOD_WEEP = ENCHANTMENTS.register("blood_weep",
            () -> Enchantment.enchantment(new Enchantment.Definition(
                    MELEE, Optional.empty(),
                    1, 3,
                    EnchantmentCost.of(30, 8), EnchantmentCost.of(50, 8),
                    5, EquipmentSlotGroup.MAINHAND
            )).build(key("blood_weep")));

    // 17. 重伤 - Grievous Wound: Apply bleeding on hit. Treasure, all weapons, 1 level.
    public static final DeferredHolder<Enchantment, Enchantment> GRIEVOUS_WOUND = ENCHANTMENTS.register("grievous_wound",
            () -> Enchantment.enchantment(new Enchantment.Definition(
                    ALL_WEAPONS, Optional.empty(),
                    1, 1,
                    EnchantmentCost.of(30, 0), EnchantmentCost.of(50, 0),
                    5, EquipmentSlotGroup.MAINHAND
            )).build(key("grievous_wound")));

    // 18. 剥壳 - Shell Strip: Damage target's armor durability. Treasure, all weapons, 1 level.
    public static final DeferredHolder<Enchantment, Enchantment> SHELL_STRIP = ENCHANTMENTS.register("shell_strip",
            () -> Enchantment.enchantment(new Enchantment.Definition(
                    ALL_WEAPONS, Optional.empty(),
                    1, 1,
                    EnchantmentCost.of(30, 0), EnchantmentCost.of(50, 0),
                    5, EquipmentSlotGroup.MAINHAND
            )).build(key("shell_strip")));

    // 19. 破军 - Army Breaker: Bonus damage per nearby enemy. Treasure, all weapons, 3 levels.
    public static final DeferredHolder<Enchantment, Enchantment> ARMY_BREAKER = ENCHANTMENTS.register("army_breaker",
            () -> Enchantment.enchantment(new Enchantment.Definition(
                    ALL_WEAPONS, Optional.empty(),
                    1, 3,
                    EnchantmentCost.of(30, 8), EnchantmentCost.of(50, 8),
                    5, EquipmentSlotGroup.MAINHAND
            )).build(key("army_breaker")));

    // 20. 红莲业火 - Crimson Hellfire: Extended fire damage. Treasure, chestplate, 1 level.
    public static final DeferredHolder<Enchantment, Enchantment> CRIMSON_HELLFIRE = ENCHANTMENTS.register("crimson_hellfire",
            () -> Enchantment.enchantment(new Enchantment.Definition(
                    ARMOR_CHEST, Optional.empty(),
                    1, 1,
                    EnchantmentCost.of(30, 0), EnchantmentCost.of(50, 0),
                    5, EquipmentSlotGroup.CHEST
            )).build(key("crimson_hellfire")));

    // 21. 紧急救援 - Emergency Rescue: Prevent death once. Treasure, leggings, 1 level.
    public static final DeferredHolder<Enchantment, Enchantment> EMERGENCY_RESCUE = ENCHANTMENTS.register("emergency_rescue",
            () -> Enchantment.enchantment(new Enchantment.Definition(
                    ARMOR_LEGS, Optional.empty(),
                    1, 1,
                    EnchantmentCost.of(30, 0), EnchantmentCost.of(50, 0),
                    5, EquipmentSlotGroup.LEGS
            )).build(key("emergency_rescue")));

    // 22. 压制 - Suppression: Slow target on hit. Normal, trident, 1 level.
    public static final DeferredHolder<Enchantment, Enchantment> SUPPRESSION = ENCHANTMENTS.register("suppression",
            () -> Enchantment.enchantment(new Enchantment.Definition(
                    TRIDENTS, primary(TRIDENTS),
                    5, 1,
                    EnchantmentCost.of(10, 0), EnchantmentCost.of(25, 0),
                    3, EquipmentSlotGroup.MAINHAND
            )).build(key("suppression")));

    // 23. 先知的长鸣 - Prophet's Call: Detect nearby hostiles. Treasure, goat horn, 1 level.
    public static final DeferredHolder<Enchantment, Enchantment> PROPHETS_CALL = ENCHANTMENTS.register("prophets_call",
            () -> Enchantment.enchantment(new Enchantment.Definition(
                    GOAT_HORN, Optional.empty(),
                    1, 1,
                    EnchantmentCost.of(30, 0), EnchantmentCost.of(50, 0),
                    5, EquipmentSlotGroup.MAINHAND
            )).build(key("prophets_call")));

    // 24. 自地狱中归来 - Return from Hell: Revive on death. Treasure, boots, 1 level.
    public static final DeferredHolder<Enchantment, Enchantment> RETURN_FROM_HELL = ENCHANTMENTS.register("return_from_hell",
            () -> Enchantment.enchantment(new Enchantment.Definition(
                    ARMOR_FEET, Optional.empty(),
                    1, 1,
                    EnchantmentCost.of(30, 0), EnchantmentCost.of(50, 0),
                    5, EquipmentSlotGroup.FEET
            )).build(key("return_from_hell")));

    // 25. 爆裂黎明 - Explosive Dawn: Explosive attack. Treasure, crossbow, 1 level.
    public static final DeferredHolder<Enchantment, Enchantment> EXPLOSIVE_DAWN = ENCHANTMENTS.register("explosive_dawn",
            () -> Enchantment.enchantment(new Enchantment.Definition(
                    CROSSBOWS, Optional.empty(),
                    1, 1,
                    EnchantmentCost.of(30, 0), EnchantmentCost.of(50, 0),
                    5, EquipmentSlotGroup.MAINHAND
            )).build(key("explosive_dawn")));

    // 26. 假面的愚者 - Fool's Mask: Reduce mob targeting. Treasure, helmet, 1 level.
    public static final DeferredHolder<Enchantment, Enchantment> FOOLS_MASK = ENCHANTMENTS.register("fools_mask",
            () -> Enchantment.enchantment(new Enchantment.Definition(
                    ARMOR_HEAD, Optional.empty(),
                    1, 1,
                    EnchantmentCost.of(30, 0), EnchantmentCost.of(50, 0),
                    5, EquipmentSlotGroup.HEAD
            )).build(key("fools_mask")));

    // 27. 挂 - Hang: Apply levitation on hit. Treasure, any enchantable, 1 level. (unobtainable)
    public static final DeferredHolder<Enchantment, Enchantment> HANG = ENCHANTMENTS.register("hang",
            () -> Enchantment.enchantment(new Enchantment.Definition(
                    ANY_ENCHANTABLE, Optional.empty(),
                    1, 1,
                    EnchantmentCost.of(30, 0), EnchantmentCost.of(50, 0),
                    5, EquipmentSlotGroup.ANY
            )).build(key("hang")));

    // 28. 神咒 - Divine Curse: Curse - take more damage. Curse, any enchantable, 1 level.
    public static final DeferredHolder<Enchantment, Enchantment> DIVINE_CURSE = ENCHANTMENTS.register("divine_curse",
            () -> Enchantment.enchantment(new Enchantment.Definition(
                    ANY_ENCHANTABLE, primary(ANY_ENCHANTABLE),
                    1, 1,
                    EnchantmentCost.of(25, 0), EnchantmentCost.of(50, 0),
                    5, EquipmentSlotGroup.ANY
            )).build(key("divine_curse")));

    // 29. 倏忽恩赐 - Fleeting Grace: Temporary speed boost. Treasure, armor, 1 level.
    public static final DeferredHolder<Enchantment, Enchantment> FLEETING_GRACE = ENCHANTMENTS.register("fleeting_grace",
            () -> Enchantment.enchantment(new Enchantment.Definition(
                    ARMOR, Optional.empty(),
                    1, 1,
                    EnchantmentCost.of(30, 0), EnchantmentCost.of(50, 0),
                    5, EquipmentSlotGroup.ARMOR
            )).build(key("fleeting_grace")));

    // 30. 神护 - Divine Protection: Chance to nullify damage. Treasure, armor, 1 level.
    public static final DeferredHolder<Enchantment, Enchantment> DIVINE_PROTECTION = ENCHANTMENTS.register("divine_protection",
            () -> Enchantment.enchantment(new Enchantment.Definition(
                    ARMOR, Optional.empty(),
                    1, 1,
                    EnchantmentCost.of(30, 0), EnchantmentCost.of(50, 0),
                    5, EquipmentSlotGroup.ARMOR
            )).build(key("divine_protection")));

    // 31. 翻飞之币 - Flipping Coin: Random damage multiplier. Treasure, all weapons, 1 level.
    public static final DeferredHolder<Enchantment, Enchantment> FLIPPING_COIN = ENCHANTMENTS.register("flipping_coin",
            () -> Enchantment.enchantment(new Enchantment.Definition(
                    ALL_WEAPONS, Optional.empty(),
                    1, 1,
                    EnchantmentCost.of(30, 0), EnchantmentCost.of(50, 0),
                    5, EquipmentSlotGroup.MAINHAND
            )).build(key("flipping_coin")));

    // 32. 必须开辟的通路 - Must Open Path: Extra knockback and pierce shields. Treasure, mace, 1 level.
    public static final DeferredHolder<Enchantment, Enchantment> MUST_OPEN_PATH = ENCHANTMENTS.register("must_open_path",
            () -> Enchantment.enchantment(new Enchantment.Definition(
                    MACE, Optional.empty(),
                    1, 1,
                    EnchantmentCost.of(30, 0), EnchantmentCost.of(50, 0),
                    5, EquipmentSlotGroup.MAINHAND
            )).build(key("must_open_path")));

    // ===== Register Method =====

    public static void register(IEventBus modEventBus) {
        ENCHANTMENTS.register(modEventBus);
    }
}
