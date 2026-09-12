package com.zhonz.moreenchantments.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zhonz.moreenchantments.enchantment.ModEnchantments;
import com.zhonz.moreenchantments.event.ModEventHandlers;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public class ModTestCommands {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("ZhonzTestCommands");

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("zhonztest")
                        .requires(source -> source.hasPermission(2))

                        // === 伤害测试 ===
                        .then(Commands.literal("damage")
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 100))
                                                .executes(ModTestCommands::testDamage)
                                        )
                                )
                        )

                        // === 给假玩家装备武器并攻击 ===
                        .then(Commands.literal("attack")
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .then(Commands.argument("enchant", ResourceLocationArgument.id())
                                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 3))
                                                        .executes(ModTestCommands::testAttackWithEnchant)
                                                )
                                        )
                                )
                        )

                        // === 装备附魔盔甲 ===
                        .then(Commands.literal("equiparmor")
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .then(Commands.argument("slot", StringArgumentType.string())
                                                .then(Commands.argument("enchant", ResourceLocationArgument.id())
                                                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 3))
                                                                .executes(ModTestCommands::equipArmor)
                                                        )
                                                )
                                        )
                                )
                        )

                        // === 生成测试假人 ===
                        .then(Commands.literal("spawndummy")
                                .executes(ModTestCommands::spawnDummy)
                        )

                        // === 清除所有附近的假人 ===
                        .then(Commands.literal("cleardummies")
                                .executes(ModTestCommands::clearDummies)
                        )

                        // === 查看目标信息 ===
                        .then(Commands.literal("info")
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(ModTestCommands::getEntityInfo)
                                )
                        )

                        // === 附魔武器测试（直接给玩家）===
                        .then(Commands.literal("giveweapon")
                                .then(Commands.argument("enchant", ResourceLocationArgument.id())
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 3))
                                                .executes(ModTestCommands::giveWeapon)
                                        )
                                )
                        )

                        // === 附魔盔甲测试 ===
                        .then(Commands.literal("givearmor")
                                .then(Commands.argument("slot", StringArgumentType.string())
                                        .then(Commands.argument("enchant", ResourceLocationArgument.id())
                                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 3))
                                                        .executes(ModTestCommands::giveArmor)
                                                )
                                        )
                                )
                        )

                        // === 批量测试全部攻击类附魔 ===
                        .then(Commands.literal("testall")
                                .executes(ModTestCommands::testAll)
                        )

                        // === "于此显圣"(manifest) 四条免死路径验证 ===
                        .then(Commands.literal("manifesttest")
                                .executes(ModTestCommands::manifestTest)
                        )

                        // === 90/91 暴击五件套: 溢出治疗→临时生命 / 受治疗吃暴击 ===
                        .then(Commands.literal("settest")
                                .executes(ModTestCommands::setTest)
                        )

                        // === 血路拓成坦途 测试：给主手武器设置指定生物类型的击杀数 ===
                        .then(Commands.literal("bloodpath")
                                .then(Commands.argument("mobType", StringArgumentType.string())
                                        .then(Commands.argument("kills", IntegerArgumentType.integer(0, 1000000))
                                                .executes(ModTestCommands::setBloodPathKills)
                                        )
                                )
                        )
        );

        // === 独立命令: bloodpathtest <mobType> <kills> ===
        dispatcher.register(
                Commands.literal("bloodpathtest")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("mobType", StringArgumentType.string())
                                .then(Commands.argument("kills", IntegerArgumentType.integer(0, 1000000))
                                        .executes(ModTestCommands::setBloodPathKills)
                                )
                        )
        );

        LOGGER.info("[TestCommands] Registered /zhonztest command and /bloodpathtest command");
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    // ============== Helpers ==============

    private static Item getTestWeapon(String enchantPath) {
        if (enchantPath.equals("area_strike")) {
            return Items.BOW; // 远程武器附魔
        }
        if (enchantPath.equals("explosive_dawn")) {
            return Items.CROSSBOW; // 弩附魔
        }
        if (enchantPath.equals("my_sea_domain") || enchantPath.equals("suppression")) {
            return Items.TRIDENT; // 三叉戟附魔
        }
        if (enchantPath.equals("solemn_mourning") || enchantPath.equals("heaven_chain")) {
            return Items.BOW; // 远程武器附魔
        }
        if (enchantPath.equals("thirty_million_turns")) {
            return Items.NETHER_STAR; // 附魔在下界之星上
        }
        if (enchantPath.equals("mercy_equal")) {
            return Items.ENCHANTED_BOOK; // 慈悲携带在附魔书上, 右键信标消耗
        }
        if (enchantPath.equals("city_shield")) {
            return Items.SHIELD; // 铸就全一城盾: 盾牌
        }
        return Items.DIAMOND_SWORD;
    }

    /**
     * 为盔甲部位附魔自动装备对应防具到 FakePlayer,便于服务器端测试。
     */
    private static void equipTestArmor(FakePlayer fakePlayer, String enchantPath) {
        EquipmentSlot slot = switch (enchantPath) {
            case "cornered_beast", "accelerated_future", "pale_midnight", "luxurious_hope", "etiquette", "halo", "photophile", "photophobe" -> EquipmentSlot.HEAD;
            case "violent_pulse", "sorrowful_red" -> EquipmentSlot.CHEST;
            case "new_sun", "self_bound" -> EquipmentSlot.LEGS;
            case "rapid_ascent", "fleet_footsteps", "flower_bed" -> EquipmentSlot.FEET;
            // 73-89 新增
            case "hyperthymesia", "unyielding_fate" -> EquipmentSlot.CHEST; // 仅需任一带附魔盔甲
            case "sojourner", "wayfarer", "eternal_standing" -> EquipmentSlot.FEET;
            default -> null;
        };
        if (slot == null) return;
        // 清理其他部位,避免测试甲累积导致多附魔同时生效
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            fakePlayer.setItemSlot(s, ItemStack.EMPTY);
        }
        Holder<Enchantment> holder = ModEnchantments.getHolderOrNull(
                ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT,
                        ResourceLocation.fromNamespaceAndPath(
                                com.zhonz.moreenchantments.common.CommonConstants.MODID, enchantPath)));
        if (holder == null) return;
        ItemStack armor = switch (slot) {
            case HEAD -> new ItemStack(Items.DIAMOND_HELMET);
            case CHEST -> new ItemStack(Items.DIAMOND_CHESTPLATE);
            case LEGS -> new ItemStack(Items.DIAMOND_LEGGINGS);
            default -> new ItemStack(Items.DIAMOND_BOOTS);
        };
        armor.enchant(holder, 1);
        fakePlayer.setItemSlot(slot, armor);
    }

    /**
     * Parse a slot name string into an {@link EquipmentSlot}.
     * Returns {@code null} if the name is not recognized.
     */
    private static EquipmentSlot parseEquipmentSlot(String slotName) {
        return switch (slotName.toLowerCase()) {
            case "head", "helmet"      -> EquipmentSlot.HEAD;
            case "chest", "chestplate", "body" -> EquipmentSlot.CHEST;
            case "legs", "leggings"    -> EquipmentSlot.LEGS;
            case "feet", "boots"       -> EquipmentSlot.FEET;
            case "offhand", "shield"  -> EquipmentSlot.OFFHAND;
            case "mainhand", "weapon"  -> EquipmentSlot.MAINHAND;
            default                    -> null;
        };
    }

    /**
     * Get the default item stack for an equipment slot (used for test equipment).
     */
    private static ItemStack defaultItemForSlot(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD    -> new ItemStack(Items.DIAMOND_HELMET);
            case CHEST   -> new ItemStack(Items.DIAMOND_CHESTPLATE);
            case LEGS    -> new ItemStack(Items.DIAMOND_LEGGINGS);
            case FEET    -> new ItemStack(Items.DIAMOND_BOOTS);
            case OFFHAND -> new ItemStack(Items.SHIELD);
            default      -> new ItemStack(Items.DIAMOND_SWORD);
        };
    }

    /**
     * Resolve an enchantment holder from the "enchant" command argument.
     * Sends a failure message and returns {@code null} if not found.
     */
    private static Holder<Enchantment> resolveEnchant(CommandContext<CommandSourceStack> context) {
        var enchantId = ResourceLocationArgument.getId(context, "enchant");
        ResourceKey<Enchantment> enchantKey = ResourceKey.create(
                net.minecraft.core.registries.Registries.ENCHANTMENT, enchantId);
        Holder<Enchantment> holder = ModEnchantments.getHolderOrNull(enchantKey);
        if (holder == null) {
            context.getSource().sendFailure(Component.literal("Enchantment not found: " + enchantId));
        }
        return holder;
    }

    // ============== 命令实现 ==============

    private static int testDamage(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Entity target = EntityArgument.getEntity(context, "target");
        int amount = IntegerArgumentType.getInteger(context, "amount");
        if (!(target instanceof LivingEntity livingTarget)) {
            context.getSource().sendFailure(Component.literal("Target must be a living entity!"));
            return 0;
        }
        livingTarget.invulnerableTime = 0;
        livingTarget.hurtTime = 0;
        float oldHealth = livingTarget.getHealth();
        // Use generic damage source to avoid any attacker-based enchantment side-effects
        // from previously-equipped weapons on the singleton FakePlayer.
        ServerLevel level = context.getSource().getLevel();
        boolean result = livingTarget.hurt(level.damageSources().generic(), (float) amount);
        float newHealth = livingTarget.getHealth();
        context.getSource().sendSuccess(() -> Component.literal(
                String.format("Damaged %s: %.1f -> %.1f (took %.1f damage, success=%b)",
                        livingTarget.getName().getString(), oldHealth, newHealth, oldHealth - newHealth, result)
        ), true);
        return 1;
    }

    private static int testAttackWithEnchant(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Entity target = EntityArgument.getEntity(context, "target");
        if (!(target instanceof LivingEntity livingTarget)) {
            context.getSource().sendFailure(Component.literal("Target must be a living entity!"));
            return 0;
        }

        var enchantId = ResourceLocationArgument.getId(context, "enchant");
        String enchantPath = enchantId.getPath();
        int level = IntegerArgumentType.getInteger(context, "level");
        Holder<Enchantment> enchantHolder = resolveEnchant(context);
        if (enchantHolder == null) return 0;

        ServerLevel levelObj = context.getSource().getLevel();
        FakePlayer fakePlayer = FakePlayerFactory.getMinecraft(levelObj);
        fakePlayer.setPos(target.getX(), target.getY(), target.getZ());

        // Clear stale enchantment-related data from the singleton FakePlayer
        // so that time-based enchantments (e.g. Liberator) start from a clean state.
        net.minecraft.nbt.CompoundTag fakeData = fakePlayer.getPersistentData();
        java.util.List<String> keysToRemove = new java.util.ArrayList<>();
        for (String key : fakeData.getAllKeys()) {
            if (key.startsWith("zhonz_")) {
                keysToRemove.add(key);
            }
        }
        for (String key : keysToRemove) {
            fakeData.remove(key);
        }
        // Also clear WeakHashMap data used by some enchantments (e.g. Liberator)
        // For Liberator, we must preserve the last-attack timestamp so that
        // consecutive test calls can measure elapsed time correctly.
        boolean hadData = com.zhonz.moreenchantments.common.storage.EntityDataStorage.hasData(fakePlayer);
        if (!enchantPath.equals("liberator")) {
            com.zhonz.moreenchantments.common.storage.EntityDataStorage.removeData(fakePlayer);
        } else {
            // Preserve liberator timestamp; clear everything else
            net.minecraft.nbt.CompoundTag edData = com.zhonz.moreenchantments.common.storage.EntityDataStorage.getData(fakePlayer);
            long lastAttack = edData.getLong("zhonz_liberator_last_attack");
            java.util.List<String> edKeys = new java.util.ArrayList<>(edData.getAllKeys());
            for (String k : edKeys) {
                if (!k.equals("zhonz_liberator_last_attack")) {
                    edData.remove(k);
                }
            }
            if (lastAttack != 0) {
                edData.putLong("zhonz_liberator_last_attack", lastAttack);
            }
        }
        LOGGER.info("[TestCommand] Cleared EntityDataStorage for FakePlayer: hadData={}, keysRemoved={}", hadData, keysToRemove.size());

        // 根据附魔类型自动选择合适的武器
        Item weaponItem = getTestWeapon(enchantPath);

        ItemStack weapon = new ItemStack(weaponItem);
        weapon.enchant(enchantHolder, level);
        fakePlayer.setItemSlot(EquipmentSlot.MAINHAND, weapon);

        // 为盔甲部位附魔自动装备对应防具(便于服务器端/无玩家测试)
        equipTestArmor(fakePlayer, enchantPath);

        // 生命条件类附魔(困兽之斗/剧烈搏动): 将假玩家生命设为 4/20(25%/50% 阈值之下)
        if (enchantPath.equals("cornered_beast") || enchantPath.equals("violent_pulse")) {
            fakePlayer.setHealth(4.0f);
        }

        // 设置假玩家的攻击伤害属性
        var attr = fakePlayer.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attr != null) {
            attr.setBaseValue(8.0);
        }

        // 验证假玩家装备
        ItemStack mainHand = fakePlayer.getMainHandItem();
        LOGGER.info("[TestCommand] FakePlayer mainHand: {} (enchant level: {}, expected: {})",
                mainHand.getItem(), mainHand.getEnchantmentLevel(enchantHolder), level);

        float oldHealth = livingTarget.getHealth();
        livingTarget.invulnerableTime = 0;
        livingTarget.hurtTime = 0;

        // 直接使用 hurt() 方法造成伤害，以假玩家为伤害来源
        // 这样可以确保附魔事件正常触发，同时绕过攻击冷却
        DamageSource source = levelObj.damageSources().playerAttack(fakePlayer);
        boolean hurtResult = livingTarget.hurt(source, 8.0f);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[TestCommand] Attack: enchant={} Lv{} -> target={} (source=playerAttack, baseDamage=8.0, hurtResult={})",
                    enchantId, level, livingTarget.getName().getString(), hurtResult);
        }

        float newHealth = livingTarget.getHealth();
        float actualDamage = oldHealth - newHealth;

        context.getSource().sendSuccess(() -> Component.literal(
                String.format("FakePlayer with %s Lv.%d attacked %s: %.1f -> %.1f (%.1f dmg)",
                        enchantId, level, livingTarget.getName().getString(),
                        oldHealth, newHealth, actualDamage)
        ), true);
        return 1;
    }

    private static int equipArmor(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Entity target = EntityArgument.getEntity(context, "target");
        if (!(target instanceof LivingEntity livingTarget)) {
            context.getSource().sendFailure(Component.literal("Target must be a living entity!"));
            return 0;
        }

        String slotName = StringArgumentType.getString(context, "slot");
        EquipmentSlot slot = parseEquipmentSlot(slotName);
        if (slot == null) {
            context.getSource().sendFailure(Component.literal("Unknown slot: " + slotName));
            return 0;
        }

        int level = IntegerArgumentType.getInteger(context, "level");
        Holder<Enchantment> enchantHolder = resolveEnchant(context);
        if (enchantHolder == null) return 0;

        ItemStack armorItem = defaultItemForSlot(slot);
        armorItem.enchant(enchantHolder, level);

        livingTarget.setItemSlot(slot, armorItem);
        context.getSource().sendSuccess(() -> Component.literal(
                "Equipped " + slotName + " with " + ResourceLocationArgument.getId(context, "enchant")
                        + " " + level + " on " + livingTarget.getName().getString()
        ), true);
        return 1;
    }

    private static int spawnDummy(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        Entity posEntity = context.getSource().getEntity();
        if (posEntity == null) {
            context.getSource().sendFailure(Component.literal("No position reference!"));
            return 0;
        }

        // 生成一个僵尸作为假人（带发光效果方便识别）
        var zombie = EntityType.ZOMBIE.spawn(level, posEntity.blockPosition().north(2), MobSpawnType.COMMAND);
        if (zombie != null) {
            zombie.setCustomName(Component.literal("测试假人"));
            zombie.setCustomNameVisible(true);
            zombie.addEffect(new MobEffectInstance(MobEffects.GLOWING, 999999, 0, false, false));
            zombie.setPersistenceRequired();
            context.getSource().sendSuccess(() -> Component.literal(
                    "Spawned test dummy at " + zombie.blockPosition()
            ), true);
            return 1;
        }
        context.getSource().sendFailure(Component.literal("Failed to spawn dummy"));
        return 0;
    }

    private static int clearDummies(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        Entity sourceEntity = context.getSource().getEntity();
        if (sourceEntity == null) return 0;

        int count = 0;
        for (var entity : level.getEntitiesOfClass(LivingEntity.class, sourceEntity.getBoundingBox().inflate(30))) {
            if (entity.getType() == EntityType.ZOMBIE && entity.hasCustomName()
                    && entity.getCustomName().getString().contains("测试假人")) {
                entity.discard();
                count++;
            }
        }
        final int finalCount = count;
        context.getSource().sendSuccess(() -> Component.literal("Cleared " + finalCount + " dummies"), true);
        return count;
    }

    private static int getEntityInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Entity target = EntityArgument.getEntity(context, "target");
        if (!(target instanceof LivingEntity livingTarget)) {
            context.getSource().sendFailure(Component.literal("Target must be a living entity!"));
            return 0;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(livingTarget.getName().getString()).append(" ===\n");
        sb.append("Health: ").append(String.format("%.1f / %.1f\n", livingTarget.getHealth(), livingTarget.getMaxHealth()));
        sb.append("Armor: ").append(livingTarget.getArmorValue()).append("\n");

        // 检查装备
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = livingTarget.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                sb.append(slot.name()).append(": ").append(stack.getItem());
                if (stack.isEnchanted()) {
                    sb.append(" [enchanted]");
                }
                if (stack.getDamageValue() > 0) {
                    sb.append(" (dur: ").append(stack.getMaxDamage() - stack.getDamageValue())
                            .append("/").append(stack.getMaxDamage()).append(")");
                }
                sb.append("\n");
            }
        }

        context.getSource().sendSuccess(() -> Component.literal(sb.toString()), true);
        return 1;
    }

    private static int giveWeapon(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var enchantId = ResourceLocationArgument.getId(context, "enchant");
        int level = IntegerArgumentType.getInteger(context, "level");
        Holder<Enchantment> enchantHolder = resolveEnchant(context);
        if (enchantHolder == null) return 0;

        // 按附魔自动选择载体(与 testall 的 getTestWeapon 一致): 三千万转→下界之星、
        // 慈悲→附魔书、铸就全一城盾→盾牌、远程附魔→弓/弩/三叉戟、其余→钻石剑。
        ItemStack weapon = new ItemStack(getTestWeapon(enchantId.getPath()));
        if (weapon.is(Items.ENCHANTED_BOOK)) {
            // 附魔书走 stored_enchantments(与创造模式附魔书一致), 而非直接附魔
            ItemEnchantments.Mutable mut = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            mut.set(enchantHolder, level);
            weapon.set(DataComponents.STORED_ENCHANTMENTS, mut.toImmutable());
        } else {
            weapon.enchant(enchantHolder, level);
        }
        // 载体名必须在 add() 之前取: Inventory.add 会把来源栈搬进背包并清空它
        final Item givenItem = weapon.getItem();
        player.getInventory().add(weapon);

        context.getSource().sendSuccess(() -> Component.literal(
                "Gave " + enchantId + " " + level + " " + givenItem + " to " + player.getName().getString()
        ), true);
        return 1;
    }

    private static int giveArmor(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String slotName = StringArgumentType.getString(context, "slot");
        var enchantId = ResourceLocationArgument.getId(context, "enchant");
        int level = IntegerArgumentType.getInteger(context, "level");

        EquipmentSlot slot = parseEquipmentSlot(slotName);
        if (slot == null) {
            context.getSource().sendFailure(Component.literal("Unknown slot: " + slotName));
            return 0;
        }

        Holder<Enchantment> enchantHolder = resolveEnchant(context);
        if (enchantHolder == null) return 0;

        ItemStack armorItem = defaultItemForSlot(slot);
        armorItem.enchant(enchantHolder, level);
        player.setItemSlot(slot, armorItem);

        context.getSource().sendSuccess(() -> Component.literal(
                "Equipped " + slotName + " with " + enchantId + " " + level + " on " + player.getName().getString()
        ), true);
        return 1;
    }

    // ============== 批量测试 ==============

    /** 攻击类可测附魔(武器/护甲/条件类)。死亡/格挡/背包类附魔需客户端验证。 */
    private static final String[] TEST_ENCHANTS = {
            "finale", "harvest", "sanction", "charger", "liberator", "supreme_art", "my_sea_domain",
            "area_strike", "self_doubt", "blood_weep", "shell_strip", "army_breaker", "suppression",
            "explosive_dawn", "fools_mask", "flipping_coin", "grievous_wound", "blood_path", "bone_break",
            "final_countdown", "erase_me_erase_you", "fleet_footsteps", "rhythm", "lonely_noon",
            "burning_dusk", "weeping_child", "apex", "cornered_beast", "violent_pulse", "ceaseless_hunt",
            "new_sun", "self_bound", "rapid_ascent", "accelerated_future", "divine_curse",
            // 54-72 攻击/属性类
            "halt", "pale_midnight", "sorrowful_red", "primal_suffering", "luxurious_hope", "etiquette",
            "perfunctory", "silence_in_depths", "frenzied_bite", "silenced_heavenfall", "gold_wine_cup",
            // 73-89 新设计(近战/护甲类可测)
            "snow_wound", "snow_sorrow", "unyielding_fate", "titan", "keen_will", "sharpen",
            "hyperthymesia", "eternal_standing", "eye_lamp", "shatter", "sojourner", "wayfarer",
            // 90-91 暴击五件套(受治疗侧, 攻击数值不变; 详细断言见 /zhonztest settest)
            "fervent_sincere_hope", "selfish_clear_sky",
            // 其余(盾/远程/功能性): 只验证数据注册不报错
            "city_shield", "solemn_mourning", "heaven_chain", "thirty_million_turns", "mercy_equal"
    };

    private static LivingEntity spawnTestDummy(ServerLevel level) {
        var dummy = EntityType.COW.spawn(level, new net.minecraft.core.BlockPos(10, 100, 10), MobSpawnType.COMMAND);
        if (dummy == null) return null;
        var maxHp = dummy.getAttribute(Attributes.MAX_HEALTH);
        if (maxHp != null) {
            maxHp.setBaseValue(100000.0);
        }
        dummy.setHealth(100000.0f);
        dummy.setPersistenceRequired();
        return dummy;
    }

    private static int testAll(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        FakePlayer fakePlayer = FakePlayerFactory.getMinecraft(level);
        LivingEntity dummy = spawnTestDummy(level);
        if (dummy == null) {
            context.getSource().sendFailure(Component.literal("Failed to spawn test dummy"));
            return 0;
        }

        int tested = 0;
        int failed = 0;
        for (String path : TEST_ENCHANTS) {
            try {
                if (!dummy.isAlive()) {
                    dummy = spawnTestDummy(level);
                    if (dummy == null) break;
                }
                dummy.setHealth(100000.0f);
                dummy.invulnerableTime = 0;
                dummy.hurtTime = 0;
                // 重置目标环境状态, 避免燃烧易伤/冬痕/火焰跨轮污染(EntityDataStorage 是独立存储)
                dummy.setRemainingFireTicks(0);
                com.zhonz.moreenchantments.common.storage.EntityDataStorage.removeData(dummy);
                dummy.getPersistentData().remove("zhonz_winter_mark_until");
                // 清空 FakePlayer 残留盔甲/叠层, 避免 unyielding_fate 等跨轮生效
                for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    fakePlayer.setItemSlot(s, ItemStack.EMPTY);
                }
                getFakeData(fakePlayer).remove("zhonz_keen_stacks");
                getFakeData(fakePlayer).remove("zhonz_shatter_stacks");
                com.zhonz.moreenchantments.common.storage.EntityDataStorage.removeData(fakePlayer);

                // 装备武器
                Holder<Enchantment> holder = ModEnchantments.getHolderOrNull(
                        ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT,
                                ResourceLocation.fromNamespaceAndPath(
                                        com.zhonz.moreenchantments.common.CommonConstants.MODID, path)));
                if (holder == null) {
                    LOGGER.error("[TestAll] {} NOT FOUND", path);
                    failed++;
                    continue;
                }
                ItemStack weapon = new ItemStack(getTestWeapon(path));
                weapon.enchant(holder, 1);
                fakePlayer.setItemSlot(EquipmentSlot.MAINHAND, weapon);
                equipTestArmor(fakePlayer, path);

                // 条件类附魔准备
                if (path.equals("cornered_beast") || path.equals("violent_pulse")) {
                    fakePlayer.setHealth(4.0f); // 生命 < 25%/< 50%
                }
                if (path.equals("lonely_noon") || path.equals("burning_dusk") || path.equals("weeping_child")) {
                    dummy.setRemainingFireTicks(10000); // 点燃目标
                }
                if (path.equals("harvest") || path.equals("army_breaker")) {
                    dummy.setHealth(100000.0f * 0.15f); // 目标低血
                }
                if (path.equals("blood_path")) {
                    setBloodPathKillsOn(fakePlayer, 100); // 100 击杀 +10%
                }
                if (path.equals("rhythm")) {
                    // 预置记录间隔,使本次攻击直接命中节奏判定
                    getFakeData(fakePlayer).putLong("zhonz_rhythm_last_attack", level.getGameTime() - 40);
                    getFakeData(fakePlayer).putInt("zhonz_rhythm_recorded_interval", 40);
                }
                if (path.equals("keen_will")) {
                    // 预置 1 层锐意, 验证武器基础攻击 +1
                    getFakeData(fakePlayer).putInt("zhonz_keen_stacks", 1);
                    getFakeData(fakePlayer).putInt("zhonz_keen_last_tick", fakePlayer.tickCount);
                }

                // 应用 tick 属性类附魔(顶点/自缚者/加速的未来/目不能追/神咒等)
                com.zhonz.moreenchantments.event.ModEventHandlers.applyTickAttributeEffects(fakePlayer);

                fakePlayer.setPos(dummy.getX(), dummy.getY(), dummy.getZ());
                var attr = fakePlayer.getAttribute(Attributes.ATTACK_DAMAGE);
                if (attr != null) attr.setBaseValue(8.0);
                float base = (float) fakePlayer.getAttributeValue(Attributes.ATTACK_DAMAGE);

                float before = dummy.getHealth();
                dummy.invulnerableTime = 0;
                // 群体打击/爆裂黎明是"投射物落点"效果: 必须用真实投射物伤害源(否则手持近战不触发)
                DamageSource damageSource;
                if (path.equals("area_strike") || path.equals("explosive_dawn")) {
                    var arrow = net.minecraft.world.entity.EntityType.ARROW.create(level);
                    if (arrow != null) {
                        arrow.setOwner(fakePlayer);
                        arrow.setPos(dummy.getX(), dummy.getY(), dummy.getZ());
                        level.addFreshEntity(arrow);
                        damageSource = level.damageSources().arrow(arrow, fakePlayer);
                    } else {
                        damageSource = level.damageSources().playerAttack(fakePlayer);
                    }
                } else {
                    damageSource = level.damageSources().playerAttack(fakePlayer);
                }
                boolean r = dummy.hurt(damageSource, base);
                float after = dummy.getHealth();
                LOGGER.info("[TestAll] {} => dmg={} (base={}, died={}, hurt={})",
                        path, String.format("%.2f", before - after), String.format("%.2f", base),
                        !dummy.isAlive(), r);
                tested++;
            } catch (Exception e) {
                LOGGER.error("[TestAll] {} EXCEPTION: {}", path, e.toString());
                failed++;
                if (!dummy.isAlive()) dummy = spawnTestDummy(level);
            }
        }

        if (dummy != null) dummy.discard();
        final int testedFinal = tested;
        final int failedFinal = failed;
        context.getSource().sendSuccess(() -> Component.literal(
                "TestAll finished: tested=" + testedFinal + ", failed=" + failedFinal), true);
        return tested;
    }

    // ===================================================================
    // "于此显圣"(manifest) 四条免死路径验证
    // ===================================================================

    /** 判定阈值: manifest 给自身的是抗性提升 V(amplifier 4); 神护自己只给 II(amplifier 1), 不会误判。 */
    private static final int MANIFEST_RESISTANCE_AMPLIFIER = 4;

    /** 抗性提升等级(amplifier), 无该效果返回 -1。 */
    private static int resistanceAmplifier(LivingEntity entity) {
        MobEffectInstance inst = entity.getEffect(MobEffects.DAMAGE_RESISTANCE);
        return inst == null ? -1 : inst.getAmplifier();
    }

    /** 移动缓慢等级(amplifier), 无该效果返回 -1。 */
    private static int slowdownAmplifier(LivingEntity entity) {
        MobEffectInstance inst = entity.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        return inst == null ? -1 : inst.getAmplifier();
    }

    /**
     * 把测试僵尸重置成"干净受害者": 清空装备/背包/效果/持久数据。
     * 注意: FakePlayer 不会被 hurt() 伤害(hurt 直接返回 false), 无法走死亡管线,
     * 所以免死路径的验证一律用真实生物(僵尸)。
     */
    private static void resetVictim(LivingEntity victim) {
        for (EquipmentSlot s : EquipmentSlot.values()) {
            victim.setItemSlot(s, ItemStack.EMPTY);
        }
        victim.removeAllEffects();
        victim.invulnerableTime = 0;
        victim.hurtTime = 0;
        victim.setRemainingFireTicks(0);
        victim.setAbsorptionAmount(0.0f);
        victim.setHealth(victim.getMaxHealth());
        com.zhonz.moreenchantments.common.storage.EntityDataStorage.removeData(victim);
        net.minecraft.nbt.CompoundTag data = victim.getPersistentData();
        for (String key : new java.util.ArrayList<>(data.getAllKeys())) {
            if (key.startsWith("zhonz_")) data.remove(key);
        }
    }

    /** 带指定附魔的物品(自动选择合适载体)。 */
    private static ItemStack withEnchant(String enchantPath, Holder<Enchantment> holder) {
        ItemStack stack = new ItemStack(getTestWeapon(enchantPath));
        if (holder != null) stack.enchant(holder, 1);
        return stack;
    }

    /** 在指定位置生成一只静止的观察用僵尸(作为"周围生物"被显圣定身)。 */
    private static LivingEntity spawnWitness(ServerLevel level, double x, double y, double z) {
        LivingEntity witness = EntityType.ZOMBIE.create(level);
        if (witness == null) return null;
        witness.moveTo(x, y, z, 0.0f, 0.0f);
        witness.setInvulnerable(true);
        if (witness instanceof net.minecraft.world.entity.Mob mob) {
            mob.setNoAi(true);
            mob.setPersistenceRequired();
        }
        level.addFreshEntity(witness);
        return witness;
    }

    /**
     * 验证「于此显圣」的四条免死路径都能触发(用真实僵尸走完整死亡管线):
     * <ol>
     *   <li>{@code vanilla} —— 主手不死图腾(走原版 checkTotemDeathProtection, 由 ManifestTotemMixin 钩住)</li>
     *   <li>{@code smart_totem} —— 智能图腾(胸甲附魔 + 背包内的该附魔图腾被消耗)</li>
     *   <li>{@code return_from_hell} —— 靴子附魔, 主手/副手持该附魔图腾</li>
     *   <li>{@code divine_protection} —— 盔甲附魔, 主手/副手持该附魔图腾</li>
     * </ol>
     * 另含两项反例, 证明"必须带「于此显圣」": {@code negative_plain_totem}(普通图腾不触发)、
     * {@code negative_no_totem}(无图腾被杀, 确认击杀本身有效)。
     * 断言: 免死路径 = 存活 + 自身抗性提升 V(amplifier 4) + 旁 2 格僵尸定身(移动缓慢 XI);
     * 反例 = 预期结果(普通图腾存活但无显圣 / 无图腾直接死亡)。
     */
    private static int manifestTest(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        // 固定高台: 远离出生点堆积的实体, 保证普通僵尸(未被 setInvulnerable)确实是"干净"的受害者
        double x = 0.5, y = 200.0, z = 0.5;

        Holder<Enchantment> manifestHolder = ModEnchantments.getHolderOrNull(
                ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT,
                        ResourceLocation.fromNamespaceAndPath(
                                com.zhonz.moreenchantments.common.CommonConstants.MODID, "manifest")));
        if (manifestHolder == null) {
            context.getSource().sendFailure(Component.literal("[ManifestTest] manifest 附魔未找到"));
            return 0;
        }
        // 「于此显圣」附在不死图腾上(1.21 附魔是数据组件, 图腾可以带)
        java.util.function.Supplier<ItemStack> manifestTotem = () -> {
            ItemStack t = new ItemStack(Items.TOTEM_OF_UNDYING);
            t.enchant(manifestHolder, 1);
            return t;
        };

        String[] paths = {"vanilla", "return_from_hell", "divine_protection",
                          "negative_plain_totem", "negative_no_totem"};
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("SKIP smart_totem: 智能图腾分支仅对 Player 生效(僵尸无背包), 需真实玩家验证");
        int passed = 0;

        for (String path : paths) {
            // 观察用僵尸(被显圣定身的那只), 免死路径需要它作为"周围生物"
            LivingEntity witness = spawnWitness(level, x + 2.0, y, z);
            if (witness == null) {
                lines.add(path + ": SKIP (僵尸创建失败)");
                continue;
            }

            // 受害者: 另一只僵尸(真实生物才走死亡管线)
            LivingEntity victim = EntityType.ZOMBIE.create(level);
            if (victim == null) {
                witness.discard();
                lines.add(path + ": SKIP (受害者创建失败)");
                continue;
            }
            victim.moveTo(x, y, z, 0.0f, 0.0f);
            if (victim instanceof net.minecraft.world.entity.Mob mob) {
                mob.setNoAi(true);
                mob.setPersistenceRequired();
            }
            level.addFreshEntity(victim);
            resetVictim(victim);

            switch (path) {
                // 原版路径: 主手不死图腾 -> checkTotemDeathProtection -> mixin 钩 RETURN 触发显圣
                case "vanilla" -> victim.setItemSlot(EquipmentSlot.MAINHAND, manifestTotem.get());
                // 智能图腾路径: 胸甲附魔 + 背包内的该附魔图腾(手上不放, 否则先走原版路径)。
                // 该分支要求 Player(僵尸没有背包), 因此不在本命令覆盖范围内, 见顶部 SKIP 说明。
                // 自地狱中归来: 靴子附魔, 副手持该附魔图腾(主手必须空, 否则被原版图腾路径先接管)
                case "return_from_hell" -> {
                    victim.setItemSlot(EquipmentSlot.FEET,
                            withEnchant("return_from_hell", ModEnchantments.getHolder(ModEnchantments.RETURN_FROM_HELL)));
                    victim.setItemSlot(EquipmentSlot.OFFHAND, manifestTotem.get());
                }
                // 神护: 头盔附魔, 副手持该附魔图腾
                case "divine_protection" -> {
                    victim.setItemSlot(EquipmentSlot.HEAD,
                            withEnchant("divine_protection", ModEnchantments.getHolder(ModEnchantments.DIVINE_PROTECTION)));
                    victim.setItemSlot(EquipmentSlot.OFFHAND, manifestTotem.get());
                }
                // 反例 1: 普通图腾(不带「于此显圣」) -> 应该免死, 但不该有显圣
                case "negative_plain_totem" -> victim.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
                // 反例 2: 什么都不带 -> 应该直接死亡(证明"致死"这件事本身有效, 前面的存活不是假象)
                case "negative_no_totem" -> { }
                default -> { }
            }

            // 致死伤害: 必须用普通伤害源。注意不能用 kill() —— 它内部用 damageSources().genericKill(),
            // 该伤害带 BYPASSES_INVULNERABILITY 标签, 而 checkTotemDeathProtection 对这类伤害直接
            // 返回 false(原版语义: 图腾挡不住 /kill), 会让图腾路径出现假失败。
            victim.hurt(level.damageSources().generic(), 1000.0f);

            boolean alive = victim.isAlive();
            boolean selfBuff = resistanceAmplifier(victim) == MANIFEST_RESISTANCE_AMPLIFIER;
            boolean witnessStunned = slowdownAmplifier(witness) >= 10;

            boolean ok;
            String expect;
            if (path.startsWith("negative_")) {
                ok = path.equals("negative_plain_totem")
                        ? (alive && !selfBuff && !witnessStunned)   // 免死但不显圣
                        : (!alive);                                  // 无免死 -> 死亡
                expect = path.equals("negative_plain_totem") ? "免死但无显圣" : "直接死亡";
            } else {
                ok = alive && selfBuff && witnessStunned;
                expect = "免死+显圣(抗性V/定身)";
            }
            if (ok) passed++;

            String detail = String.format("存活=%b 自身抗性V=%b(%d) 旁观定身=%b(%d)",
                    alive, selfBuff, resistanceAmplifier(victim), witnessStunned, slowdownAmplifier(witness));
            lines.add((ok ? "PASS " : "FAIL ") + path + ": " + detail + " [期望: " + expect + "]");
            LOGGER.info("[ManifestTest] {} => {} (期望 {})", path, detail, expect);

            victim.discard();
            witness.discard();
        }

        final int passedFinal = passed;
        final String report = "ManifestTest: cases=" + paths.length + " passed=" + passedFinal
                + "\n  " + String.join("\n  ", lines);
        context.getSource().sendSuccess(() -> Component.literal(report), true);
        return passedFinal;
    }

    // ===================================================================
    // 90/91 暴击五件套: 溢出治疗→临时生命 / 受治疗吃暴击
    // ===================================================================

    /**
     * 验证 #90 热烈诚挚希望 与 #91 自私澄澈天光:
     * <ol>
     *   <li>{@code fervent_cap} —— 胸甲带 #90, 满血治疗 40 → 临时生命按"最大生命 100%"上限截断(=20)</li>
     *   <li>{@code fervent_nocap} —— 胸甲带 #90 + 头盔带其他套装附魔 → 取消上限, 40 全部转为临时生命</li>
     *   <li>{@code selfish_heal} —— 胸甲带 #91 且暴击率=1、暴击伤害=2 → 治疗量 ×(1+1×2)</li>
     *   <li>{@code selfish_none} —— 不穿 #91 → 治疗量不放大(反例)</li>
     * </ol>
     * 用 FakePlayer(可穿戴, 且 heal 会走 LivingHealEvent/Apothic 的 HEALING_RECEIVED 缩放)。
     */
    private static int setTest(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        java.util.List<String> lines = new java.util.ArrayList<>();
        int passed = 0;

        Holder<Enchantment> fervent = ModEnchantments.getHolderOrNull(ModEnchantments.FERVENT_SINCERE_HOPE);
        Holder<Enchantment> selfish = ModEnchantments.getHolderOrNull(ModEnchantments.SELFISH_CLEAR_SKY);
        Holder<Enchantment> silence = ModEnchantments.getHolderOrNull(ModEnchantments.SILENCE_IN_DEPTHS);
        if (fervent == null || selfish == null || silence == null) {
            context.getSource().sendFailure(Component.literal("[SetTest] 附魔未注册(fervent/selfish/silence)"));
            return 0;
        }

        // ---------- 1/2: #90 溢出治疗(用真实僵尸: FakePlayer 不支持吸收值) ----------
        boolean capOk = runFerventCase(level, fervent, silence, false);
        boolean nocapOk = runFerventCase(level, fervent, silence, true);
        lines.add((capOk ? "PASS " : "FAIL ") + "fervent_cap: 满血治疗40 → 临时生命=20(最大生命100%上限)");
        lines.add((nocapOk ? "PASS " : "FAIL ") + "fervent_nocap: 头盔带其他套装附魔 → 临时生命=40(上限取消)");
        if (capOk) passed++;
        if (nocapOk) passed++;

        // ---------- 3/4: #91 受治疗吃暴击 ----------
        double healedWith = healWithSelfish(player, selfish, level, true);
        double healedWithout = healWithSelfish(player, selfish, level, false);
        // 期望: 暴击率1 × 暴击伤害2 → 治疗量 ×(1+2) = 3 倍
        boolean selfishOk = Math.abs(healedWith - 3.0 * 4.0) < 0.05;
        boolean negativeOk = Math.abs(healedWithout - 4.0) < 0.05;
        lines.add((selfishOk ? "PASS " : "FAIL ") + String.format(
                "selfish_heal: 暴击1.0/暴伤2.0 时治疗4 → %.2f(期望 12.00)", healedWith));
        lines.add((negativeOk ? "PASS " : "FAIL ") + String.format(
                "selfish_none: 不穿#91 时治疗4 → %.2f(期望 4.00)", healedWithout));
        if (selfishOk) passed++;
        if (negativeOk) passed++;

        final int passedFinal = passed;
        final String report = "SetTest: cases=4 passed=" + passedFinal + "\n  " + String.join("\n  ", lines);
        context.getSource().sendSuccess(() -> Component.literal(report), true);
        return passedFinal;
    }

    /**
     * #90 单个场景(真实僵尸): 满血时治疗 40 → 溢出部分转临时生命。
     * withAlly=true 时额外戴一件其他套装附魔(头盔)验证"取消上限"。
     * 注: 必须用真实实体 —— FakePlayer 的 setAbsorptionAmount 不生效(读回恒为 0)。
     */
    private static boolean runFerventCase(ServerLevel level, Holder<Enchantment> fervent,
                                          Holder<Enchantment> ally, boolean withAlly) {
        LivingEntity victim = EntityType.ZOMBIE.create(level);
        if (victim == null) return false;
        victim.moveTo(0.5, 200.0, 0.5, 0.0f, 0.0f);
        if (victim instanceof net.minecraft.world.entity.Mob mob) {
            mob.setNoAi(true);
            mob.setPersistenceRequired();
        }
        level.addFreshEntity(victim);
        resetVictim(victim);
        victim.setAbsorptionAmount(0.0f);
        victim.setHealth(victim.getMaxHealth());

        ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
        chest.enchant(fervent, 1);
        victim.setItemSlot(EquipmentSlot.CHEST, chest);
        if (withAlly) {
            ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
            helmet.enchant(ally, 1);
            victim.setItemSlot(EquipmentSlot.HEAD, helmet);
        }

        float maxHp = victim.getMaxHealth();
        victim.heal(40.0f);
        double absorption = victim.getAbsorptionAmount();
        double expected = withAlly ? 40.0 : maxHp;
        LOGGER.info("[SetTest] fervent withAlly={} -> absorption={} (maxHp={}, expected={})",
                withAlly, absorption, maxHp, expected);
        victim.discard();
        return Math.abs(absorption - expected) < 0.05;
    }

    /** #91 场景: 返回"从半血治疗 4 点"实际生效的治疗量。 */
    private static double healWithSelfish(FakePlayer player, Holder<Enchantment> selfish,
                                          ServerLevel level, boolean withEnchant) {
        for (EquipmentSlot s : EquipmentSlot.values()) player.setItemSlot(s, ItemStack.EMPTY);
        com.zhonz.moreenchantments.common.storage.EntityDataStorage.removeData(player);
        player.setAbsorptionAmount(0.0f);

        if (withEnchant) {
            ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
            chest.enchant(selfish, 1);
            player.setItemSlot(EquipmentSlot.CHEST, chest);
        }
        // 固定暴击参数: 暴击率 1.0(100%)、暴击伤害 2.0 → 期望治疗 ×(1 + 1×2) = 3
        var critChance = player.getAttribute(dev.shadowsoffire.apothic_attributes.api.ALObjects.Attributes.CRIT_CHANCE);
        var critDamage = player.getAttribute(dev.shadowsoffire.apothic_attributes.api.ALObjects.Attributes.CRIT_DAMAGE);
        if (critChance != null) critChance.setBaseValue(1.0);
        if (critDamage != null) critDamage.setBaseValue(2.0);

        // 刷新 91 的 HEALING_RECEIVED 修饰(该逻辑平时由玩家 tick 驱动)
        ModEventHandlers.applyTickAttributeEffects(player);

        player.setHealth(5.0f);
        player.heal(4.0f);
        double healed = player.getHealth() - 5.0f;
        double cv = critChance == null ? -1 : player.getAttributeValue(dev.shadowsoffire.apothic_attributes.api.ALObjects.Attributes.CRIT_CHANCE);
        double dv = critDamage == null ? -1 : player.getAttributeValue(dev.shadowsoffire.apothic_attributes.api.ALObjects.Attributes.CRIT_DAMAGE);
        double hr = player.getAttributeValue(dev.shadowsoffire.apothic_attributes.api.ALObjects.Attributes.HEALING_RECEIVED);
        LOGGER.info("[SetTest] selfish withEnchant={} -> healed={} (crit={}, critDmg={}, healingReceived={})",
                withEnchant, healed, cv, dv, hr);
        return healed;
    }

    private static net.minecraft.nbt.CompoundTag getFakeData(FakePlayer fakePlayer) {
        return fakePlayer.getPersistentData();
    }

    private static void setBloodPathKillsOn(FakePlayer fakePlayer, int kills) {
        ItemStack weapon = fakePlayer.getMainHandItem();
        if (weapon.isEmpty()) return;
        CustomData.update(DataComponents.CUSTOM_DATA, weapon, root -> {
            net.minecraft.nbt.CompoundTag killsTag = new net.minecraft.nbt.CompoundTag();
            killsTag.putInt("minecraft:cow", kills);
            root.put("zhonz_blood_path_kills", killsTag);
        });
    }

    private static int setBloodPathKills(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String mobType = StringArgumentType.getString(context, "mobType");
        int kills = IntegerArgumentType.getInteger(context, "kills");

        ItemStack weapon = player.getMainHandItem();
        if (weapon.isEmpty()) {
            context.getSource().sendFailure(Component.literal("You must hold a weapon in your main hand!"));
            return 0;
        }

        // 如果mobType不含冒号，自动补全 minecraft: 前缀
        String mobKey = mobType.contains(":") ? mobType : "minecraft:" + mobType;

        if (weapon.getEnchantmentLevel(ModEnchantments.getHolder(ModEnchantments.BLOOD_PATH)) <= 0) {
            // 如果武器没有血路附魔，自动加上
            weapon.enchant(ModEnchantments.getHolder(ModEnchantments.BLOOD_PATH), 1);
        }

        // 设置击杀计数
        CustomData.update(DataComponents.CUSTOM_DATA, weapon, root -> {
            final String BLOOD_PATH_TAG = "zhonz_blood_path_kills";
            CompoundTag killsTag;
            if (root.contains(BLOOD_PATH_TAG, CompoundTag.TAG_COMPOUND)) {
                killsTag = root.getCompound(BLOOD_PATH_TAG);
            } else {
                killsTag = new CompoundTag();
                root.put(BLOOD_PATH_TAG, killsTag);
            }
            killsTag.putInt(mobKey, kills);
        });

        int actualCount = ModEventHandlers.getBloodPathKillCount(weapon, mobKey);
        float bonusPct = actualCount * 0.1f;
        final String finalMobKey = mobKey;
        context.getSource().sendSuccess(() -> Component.literal(
                String.format("Set Blood Path kills for %s: %d (%.1f%% damage bonus). Weapon: %s",
                        finalMobKey, actualCount, bonusPct, weapon.getItem())
        ), true);
        return 1;
    }
}
