package com.zhonz.moreenchantments.enchantment;

import com.zhonz.moreenchantments.ZhonzMoreEnchantments;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * In NeoForge 1.21.1 enchantments are data-driven: they are defined by JSON
 * files under {@code data/<modid>/enchantment/} and only need a
 * {@link ResourceKey} reference in code. The actual {@link Enchantment} holder
 * is resolved at runtime from the server registry via {@link #getHolder}.
 */
public class ModEnchantments {

    // ===== Helper Methods =====

    private static ResourceKey<Enchantment> key(String name) {
        return ResourceKey.create(Registries.ENCHANTMENT,
                ResourceLocation.fromNamespaceAndPath(ZhonzMoreEnchantments.MODID, name));
    }

    /**
     * Resolves the runtime {@link Holder} for an enchantment from the server
     * registry. Must be called server-side (e.g. inside event handlers).
     */
    public static Holder<Enchantment> getHolder(ResourceKey<Enchantment> key) {
        return ServerLifecycleHooks.getCurrentServer().registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(key);
    }

    // ===== Enchantment Resource Keys =====

    // 1. 终结 - Finale: Execute effect when target is low HP.
    public static final ResourceKey<Enchantment> FINALE = key("finale");

    // 2. 食腐者 - Scavenger: Duplicate loot on kill.
    public static final ResourceKey<Enchantment> SCAVENGER = key("scavenger");

    // 3. 收割 - Harvest: Bonus damage based on target missing HP.
    public static final ResourceKey<Enchantment> HARVEST = key("harvest");

    // 4. 制裁 - Sanction: Bonus damage against illagers.
    public static final ResourceKey<Enchantment> SANCTION = key("sanction");

    // 5. 深海的供养 - Deep Sea's Grace: Underwater healing and speed.
    public static final ResourceKey<Enchantment> DEEP_SEAS_GRACE = key("deep_seas_grace");

    // 6. 宝石伞 - Gem Umbrella: Reduce projectile damage.
    public static final ResourceKey<Enchantment> GEM_UMBRELLA = key("gem_umbrella");

    // 7. 冲锋手 - Charger: Sprint attack bonus damage.
    public static final ResourceKey<Enchantment> CHARGER = key("charger");

    // 8. 鱼丸 - Fishball: Better fishing loot.
    public static final ResourceKey<Enchantment> FISHBALL = key("fishball");

    // 9. 解放者 - Liberator: Remove debuffs on hit.
    public static final ResourceKey<Enchantment> LIBERATOR = key("liberator");

    // 10. 至高之术 - Supreme Art: Chance for triple damage.
    public static final ResourceKey<Enchantment> SUPREME_ART = key("supreme_art");

    // 11. 我的海疆 - My Sea Domain: Water damage bonus.
    public static final ResourceKey<Enchantment> MY_SEA_DOMAIN = key("my_sea_domain");

    // 12. 群体打击 - Area Strike: AOE damage on attack.
    public static final ResourceKey<Enchantment> AREA_STRIKE = key("area_strike");

    // 13. 坚韧 - Toughness: Reduce all incoming damage.
    public static final ResourceKey<Enchantment> TOUGHNESS = key("toughness");

    // 14. 可是我的自卑胜过了一切爱我的 - Self Doubt: Curse - reduce outgoing damage.
    public static final ResourceKey<Enchantment> SELF_DOUBT = key("self_doubt");

    // 15. 无垢之人 - The Pure: Immune to negative effects.
    public static final ResourceKey<Enchantment> THE_PURE = key("the_pure");

    // 16. 血泣 - Blood Weep: Lifesteal.
    public static final ResourceKey<Enchantment> BLOOD_WEEP = key("blood_weep");

    // 17. 重伤 - Grievous Wound: Apply bleeding on hit.
    public static final ResourceKey<Enchantment> GRIEVOUS_WOUND = key("grievous_wound");

    // 18. 剥壳 - Shell Strip: Damage target's armor durability.
    public static final ResourceKey<Enchantment> SHELL_STRIP = key("shell_strip");

    // 19. 破军 - Army Breaker: Bonus damage per nearby enemy.
    public static final ResourceKey<Enchantment> ARMY_BREAKER = key("army_breaker");

    // 20. 红莲业火 - Crimson Hellfire: Extended fire damage.
    public static final ResourceKey<Enchantment> CRIMSON_HELLFIRE = key("crimson_hellfire");

    // 21. 紧急救援 - Emergency Rescue: Prevent death once.
    public static final ResourceKey<Enchantment> EMERGENCY_RESCUE = key("emergency_rescue");

    // 22. 压制 - Suppression: Slow target on hit.
    public static final ResourceKey<Enchantment> SUPPRESSION = key("suppression");

    // 23. 先知的长鸣 - Prophet's Call: Detect nearby hostiles.
    public static final ResourceKey<Enchantment> PROPHETS_CALL = key("prophets_call");

    // 24. 自地狱中归来 - Return from Hell: Revive on death.
    public static final ResourceKey<Enchantment> RETURN_FROM_HELL = key("return_from_hell");

    // 25. 爆裂黎明 - Explosive Dawn: Explosive attack.
    public static final ResourceKey<Enchantment> EXPLOSIVE_DAWN = key("explosive_dawn");

    // 26. 假面的愚者 - Fool's Mask: Reduce mob targeting.
    public static final ResourceKey<Enchantment> FOOLS_MASK = key("fools_mask");

    // 27. 挂 - Hang: Apply levitation on hit. (unobtainable)
    public static final ResourceKey<Enchantment> HANG = key("hang");

    // 28. 神咒 - Divine Curse: Curse - take more damage.
    public static final ResourceKey<Enchantment> DIVINE_CURSE = key("divine_curse");

    // 29. 倏忽恩赐 - Fleeting Grace: Temporary speed boost.
    public static final ResourceKey<Enchantment> FLEETING_GRACE = key("fleeting_grace");

    // 30. 神护 - Divine Protection: Chance to nullify damage.
    public static final ResourceKey<Enchantment> DIVINE_PROTECTION = key("divine_protection");

    // 31. 翻飞之币 - Flipping Coin: Random damage multiplier.
    public static final ResourceKey<Enchantment> FLIPPING_COIN = key("flipping_coin");

    // 32. 必须开辟的通路 - Must Open Path: Extra knockback and pierce shields.
    public static final ResourceKey<Enchantment> MUST_OPEN_PATH = key("must_open_path");

    // 33. 不完整的预知眼 - Incomplete Foreknowledge Eye: Detect nearby hostiles.
    public static final ResourceKey<Enchantment> INCOMPLETE_FOREKNOWLEDGE_EYE = key("incomplete_foreknowledge_eye");
}
