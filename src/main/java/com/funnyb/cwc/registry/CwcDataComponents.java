package com.funnyb.cwc.registry;

import com.funnyb.cwc.ColdWeaponCraftsmanship;
import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * CWC 自定义 DataComponent 注册中心。
 * 所有零件身份和属性通过 DataComponent 存储在 ItemStack 上。
 */
public class CwcDataComponents {

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ColdWeaponCraftsmanship.MODID);

    /** 零件完整标识，如 "cwc.standard_blade.iron" */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> PART_IDENTITY =
            COMPONENTS.register("part_identity",
                    () -> DataComponentType.<String>builder()
                            .persistent(Codec.STRING)
                            .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                            .build());

    public static void init(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
