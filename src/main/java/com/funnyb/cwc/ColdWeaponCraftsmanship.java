package com.funnyb.cwc;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.funnyb.cwc.combat.CwcCombatEvents;
import com.funnyb.cwc.combat.CwcOwnership;
import com.funnyb.cwc.crafting.PartRegistry;
import com.funnyb.cwc.registry.CwcAttachments;
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
import net.neoforged.neoforge.event.level.LevelEvent;
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

    /** 方块 DeferredRegister（实际注册的方块见 {@code registry/CwcBlocks}） */
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
        CwcAttachments.init(modEventBus);

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new CwcCombatEvents());
        NeoForge.EVENT_BUS.register(new CwcOwnership());
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
    }

    /** 模组通用初始化完成时打印日志 */
    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Cold Weapon Craftsmanship common setup complete.");
    }

    /**
     * 关卡加载时绑定零件注册表——**客户端与服务端都会触发这一个事件**，所以一个钩子同时覆盖两端。
     * <p>
     * 零件/类型定义现在是数据包注册表（{@link CwcRegistries}），由原版加载并在配置阶段同步到客户端；
     * 这里只是把注册表引用缓存到 {@link PartRegistry}，好让拿不到 {@code RegistryAccess} 的查询点
     * （如 {@code CwcWeapon.getDefaultAttributeModifiers}）也能查到定义。
     */
    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        PartRegistry.bind(event.getLevel().registryAccess());
    }

    /** 关卡卸载时解绑，避免切换世界后残留上一个关卡的注册表引用 */
    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        PartRegistry.unbind();
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
