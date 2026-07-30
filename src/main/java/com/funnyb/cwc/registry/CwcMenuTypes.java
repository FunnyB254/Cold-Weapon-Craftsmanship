package com.funnyb.cwc.registry;

import com.funnyb.cwc.ColdWeaponCraftsmanship;
import com.funnyb.cwc.menu.CraftingMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * CWC 菜单类型注册中心。
 * 所有自定义容器（AbstractContainerMenu）类型在此声明并注册。
 */
public class CwcMenuTypes {

    /** 菜单类型 DeferredRegister */
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, ColdWeaponCraftsmanship.MODID);

    /** 制造界面容器类型——客户端通过 FriendlyByteBuf 构造，服务端直接构造 */
    public static final DeferredHolder<MenuType<?>, MenuType<CraftingMenu>> CRAFTING =
            MENU_TYPES.register("crafting",
                    () -> IMenuTypeExtension.create(CraftingMenu::new));

    /** 将菜单类型注册器绑定到模组事件总线 */
    public static void init(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }
}
