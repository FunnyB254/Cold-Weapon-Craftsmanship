package com.funnyb.cwc.registry;

import com.funnyb.cwc.ColdWeaponCraftsmanship;
import com.funnyb.cwc.menu.AssemblingMenu;
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

    /**
     * 制造界面容器类型。
     * <p>
     * 打开时由服务端经 {@code openMenu(provider, pos)} 把方块坐标写进包里，这里读出来交给菜单——
     * 菜单用它判断玩家是否走远/方块是否被拆，从而自动关闭（见 {@code CraftingMenu.stillValid}）。
     */
    public static final DeferredHolder<MenuType<?>, MenuType<CraftingMenu>> CRAFTING =
            MENU_TYPES.register("crafting",
                    () -> IMenuTypeExtension.create((id, inv, buf) -> new CraftingMenu(id, inv, buf.readBlockPos())));

    /** 装配界面容器类型——坐标用途同 {@link #CRAFTING} */
    public static final DeferredHolder<MenuType<?>, MenuType<AssemblingMenu>> ASSEMBLING =
            MENU_TYPES.register("assembling",
                    () -> IMenuTypeExtension.create((id, inv, buf) -> new AssemblingMenu(id, inv, buf.readBlockPos())));

    /** 将菜单类型注册器绑定到模组事件总线 */
    public static void init(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }
}
