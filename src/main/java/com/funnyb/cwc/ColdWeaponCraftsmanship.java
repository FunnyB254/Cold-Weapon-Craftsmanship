package com.funnyb.cwc;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.funnyb.cwc.combat.CwcCombatEvents;
import com.funnyb.cwc.crafting.PartRegistry;
import com.funnyb.cwc.registry.CwcCreativeTabs;
import com.funnyb.cwc.registry.CwcDataComponents;
import com.funnyb.cwc.registry.CwcItems;
import com.funnyb.cwc.registry.CwcMenuTypes;
import com.funnyb.cwc.screen.AssemblingScreen;
import com.funnyb.cwc.screen.CraftingScreen;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 冷兵器工艺模组主类——NeoForge 的入口点。
 * 负责注册方块、物品、创造标签页、菜单类型，绑定模组事件和服务端启动事件。
 */
@Mod(ColdWeaponCraftsmanship.MODID)
public class ColdWeaponCraftsmanship {

    /** 模组 ID，所有资源标识符的命名空间 */
    public static final String MODID = "coldweaponcraftsmanship";

    /** SLF4J 日志实例 */
    public static final Logger LOGGER = LogUtils.getLogger();

    /** 方块 DeferredRegister（预留，尚未注册方块） */
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);

    /**
     * 模组构造器，NeoForge 自动注入事件总线和模组容器。
     * 在此注册所有注册器和事件监听器。
     */
    public ColdWeaponCraftsmanship(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        BLOCKS.register(modEventBus);
        CwcItems.init(modEventBus);
        CwcCreativeTabs.init(modEventBus);
        CwcMenuTypes.init(modEventBus);
        CwcDataComponents.init(modEventBus);

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new CwcCombatEvents());
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    /** 模组通用初始化完成时打印日志 */
    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Cold Weapon Craftsmanship common setup complete.");
    }

    /** 服务端启动时加载零件注册表 */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        PartRegistry.reload(event.getServer().getResourceManager());
        LOGGER.info("CWC part registry loaded on server");
    }

    // ──── 客户端事件 ────

    /** 客户端侧事件处理器：注册 Screen 与 MenuType 的绑定 */
    @EventBusSubscriber(modid = ColdWeaponCraftsmanship.MODID, value = Dist.CLIENT)
    static class ClientModEvents {
        @SubscribeEvent
        static void onRegisterScreens(RegisterMenuScreensEvent event) {
            event.register(CwcMenuTypes.CRAFTING.get(), CraftingScreen::new);
            event.register(CwcMenuTypes.ASSEMBLING.get(), AssemblingScreen::new);
        }
    }
}
