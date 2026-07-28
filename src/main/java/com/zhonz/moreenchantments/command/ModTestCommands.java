package com.zhonz.moreenchantments.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zhonz.moreenchantments.enchantment.ModEnchantments;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
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
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
        );

        LOGGER.info("[TestCommands] Registered /zhonztest command");
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    // ============== Helpers ==============

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
        boolean hadData = com.zhonz.moreenchantments.event.EntityDataStorage.hasData(fakePlayer);
        if (!enchantPath.equals("liberator")) {
            com.zhonz.moreenchantments.event.EntityDataStorage.removeData(fakePlayer);
        } else {
            // Preserve liberator timestamp; clear everything else
            net.minecraft.nbt.CompoundTag edData = com.zhonz.moreenchantments.event.EntityDataStorage.getData(fakePlayer);
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
        Item weaponItem = Items.DIAMOND_SWORD;
        if (enchantPath.equals("area_strike") || enchantPath.equals("explosive_dawn") || enchantPath.equals("my_sea_domain")) {
            weaponItem = Items.BOW;
        } else if (enchantPath.equals("suppression")) {
            weaponItem = Items.TRIDENT;
        } else if (enchantPath.equals("finale") || enchantPath.equals("charger") || enchantPath.equals("liberator")
                || enchantPath.equals("supreme_art") || enchantPath.equals("blood_weep") || enchantPath.equals("shell_strip")
                || enchantPath.equals("army_breaker") || enchantPath.equals("harvest") || enchantPath.equals("must_open_path")) {
            weaponItem = Items.DIAMOND_SWORD;
        }

        ItemStack weapon = new ItemStack(weaponItem);
        weapon.enchant(enchantHolder, level);
        fakePlayer.setItemSlot(EquipmentSlot.MAINHAND, weapon);

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

        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(enchantHolder, level);
        player.getInventory().add(sword);

        context.getSource().sendSuccess(() -> Component.literal(
                "Gave " + enchantId + " " + level + " sword to " + player.getName().getString()
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
}
