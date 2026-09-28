package com.funnyb.cwc.screen.config;

import com.funnyb.cwc.ColdWeaponCraftsmanship;
import com.funnyb.cwc.Config;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.ModConfigSpec.Range;
import net.neoforged.neoforge.common.ModConfigSpec.ValueSpec;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 把 mod 的设置界面接进原版的 Mods 菜单。
 * <p>
 * <b>本类必须整体是客户端专属的。</b> 它引用了 {@link IConfigScreenFactory} 这类客户端类，
 * 而一旦这些类出现在一个**通用**类的常量池里，专用服务器会在 NeoForge 的 RuntimeDistCleaner
 * 那里直接崩溃——所以这里自带 {@code @EventBusSubscriber(value = Dist.CLIENT)}，
 * 而不在 {@code ColdWeaponCraftsmanship} 的构造器里注册（那是个两端都加载的类）。
 * <p>
 * 容器得自己从 {@link ModList} 取：客户端事件不带 {@code ModContainer}
 * （对比 {@code ColdWeaponCraftsmanship} 里那个嵌套的 {@code ClientModEvents}，
 * 它不需要容器所以拿得到就够了）。注册扩展点的时机不敏感——它只是个查询用的映射，
 * 而读取它的 Mods 菜单要等客户端起来之后才会开。
 */
@EventBusSubscriber(modid = ColdWeaponCraftsmanship.MODID, value = Dist.CLIENT)
public final class CwcConfigScreen {

    /** 与原版其他配置项同样的尺寸——NeoForge 那边是 {@code OptionInstance.createButton} 造出来的 150×20 */
    private static final int SLIDER_WIDTH = 150;
    private static final int SLIDER_HEIGHT = 20;

    /**
     * 一个配置项在设置界面里怎么摆。
     * <p>
     * 步进**不在** ModConfigSpec 里（{@code defineInRange} 只存 min/max，范围与步进是两回事），
     * 所以只能在这儿按路径登记。加一个小数项就得加一行。
     *
     * @param step   档位间隔
     * @param unitKey 单位语言键，形如 {@code "%s 秒"}
     * @param offKey  值为 0 时改显示的键；这一项只有"0 才是不生效"的项才配（见下）
     */
    private record SliderStyle(double step, String unitKey, @Nullable String offKey) {}

    private static final Map<String, SliderStyle> STYLES = Map.of(
            // 停顿拉到 0 是"不停顿"，读数显示"关"更贴切。复用时直接用原版的 options.off（原版布尔按钮用的就是它）
            Config.DWELL_SECONDS_PATH, new SliderStyle(0.1, "cwc.config.unit.seconds", "options.off"),
            // 速度的 0 是"完全不动"而不是"关"（它的下限本来也不是 0），所以这一项不配 offKey
            Config.SCROLL_SPEED_PATH, new SliderStyle(1.0, "cwc.config.unit.pixels_per_second", null));

    private CwcConfigScreen() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ModList.get().getModContainerById(ColdWeaponCraftsmanship.MODID).ifPresent(container ->
                container.registerExtensionPoint(IConfigScreenFactory.class,
                        (mod, parent) -> new ConfigurationScreen(mod, parent, SectionScreen::new)));
    }

    /**
     * 原版那套配置界面，只改一点：**小数项给滑条，而不是数字框**。
     * <p>
     * {@code createDoubleValue} 是 {@code protected} 的，覆写它即可；标签与 tooltip 继续用父类的
     * {@code getTranslationComponent} / {@code getTooltipComponent}（protected），
     * 所以这一行与其它配置项长得一模一样，tooltip 里的范围提示也照样带上。
     * <p>
     * 三参构造器 {@code ConfigurationScreen(mod, parent, QuadFunction)} 的默认实现就是
     * {@code new ConfigurationSectionScreen(a, b, c, d, filter)}，返回子类是它留出来的扩展口。
     */
    private static final class SectionScreen extends ConfigurationScreen.ConfigurationSectionScreen {

        SectionScreen(Screen parent, ModConfig.Type type, ModConfig modConfig, Component title) {
            super(parent, type, modConfig, title);
        }

        @Override
        protected Element createDoubleValue(String key, ValueSpec spec, Supplier<Double> source, Consumer<Double> target) {
            Range<Double> range = spec.getRange();
            SliderStyle style = STYLES.get(key);
            // 没登记步进的项（将来别人加的小数项）退回原版数字框，而不是猜一个步进出来
            if (range == null || style == null) {
                return super.createDoubleValue(key, spec, source, target);
            }
            return new Element(getTranslationComponent(key), getTooltipComponent(key, null),
                    new FractionalSlider(SLIDER_WIDTH, SLIDER_HEIGHT, range.getMin(), range.getMax(),
                            style.step(), style.unitKey(), style.offKey(), source.get(), newValue -> {
                                // 与原版 createSlider 逐字同构：值真的变了才记一步 undo
                                if (!newValue.equals(source.get())) {
                                    undoManager.add(v -> {
                                        target.accept(v);
                                        onChanged(key);
                                    }, newValue, v -> {
                                        target.accept(v);
                                        onChanged(key);
                                    }, source.get());
                                }
                            }));
        }
    }
}
