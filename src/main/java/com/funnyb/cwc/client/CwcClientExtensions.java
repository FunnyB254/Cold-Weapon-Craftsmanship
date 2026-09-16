package com.funnyb.cwc.client;

import com.funnyb.cwc.ColdWeaponCraftsmanship;
import com.funnyb.cwc.client.renderer.AssembledWeaponRenderer;
import com.funnyb.cwc.registry.CwcItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * 客户端物品扩展注册——为 HANDLE_PART 和 PART 挂接复合渲染器（底座 + 已装零件，递归嵌套）。
 * PART 也走复合渲染，使中间状态（如刃上装了护手）单独显示时也渲染子零件。
 */
@EventBusSubscriber(modid = ColdWeaponCraftsmanship.MODID, value = Dist.CLIENT)
public class CwcClientExtensions {

    /** 装配复合渲染器（懒加载单例） */
    private static BlockEntityWithoutLevelRenderer weaponRenderer;

    /** 注册 PART 与 HANDLE_PART 的自定义渲染扩展 */
    @SubscribeEvent
    public static void onRegisterExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (weaponRenderer == null) {
                    weaponRenderer = new AssembledWeaponRenderer(
                            Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                            Minecraft.getInstance().getEntityModels());
                }
                return weaponRenderer;
            }
        }, CwcItems.HANDLE_PART.get(), CwcItems.PART.get());
    }

}
