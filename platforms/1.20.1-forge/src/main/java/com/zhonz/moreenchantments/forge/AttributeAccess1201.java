package com.zhonz.moreenchantments.forge;

import com.zhonz.moreenchantments.common.damage.UnifiedDamageEngine;
import com.zhonz.moreenchantments.common.version.AttributeChannelAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 1.20.1 Forge 平台 AttributeChannelAccess 实现。
 *
 * 1.20.1 AttributeModifier = (UUID, name, amount, operation): 用 id.toString() 作 name、
 * id 派生稳定 UUID; removeModifier 遍历实例按 name 匹配(1.20.1 无按 ResourceLocation 移除)。
 */
public final class AttributeAccess1201 implements AttributeChannelAccess {

    private static final AttributeAccess1201 INSTANCE = new AttributeAccess1201();

    public static void install() {
        UnifiedDamageEngine.install(INSTANCE);
    }

    private AttributeAccess1201() {
    }

    private static UUID uuidOf(ResourceLocation id) {
        return UUID.nameUUIDFromBytes(("zhonz:" + id.getPath()).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public AttributeModifier makeAddModifier(ResourceLocation id, double amount) {
        return new AttributeModifier(uuidOf(id), id.toString(), amount, AttributeModifier.Operation.ADDITION);
    }

    @Override
    public void removeModifier(AttributeInstance inst, ResourceLocation id) {
        AttributeModifier toRemove = null;
        for (AttributeModifier m : inst.getModifiers()) {
            if (m.getName().equals(id.toString())) {
                toRemove = m;
                break;
            }
        }
        if (toRemove != null) {
            inst.removeModifier(toRemove);
        }
    }
}
