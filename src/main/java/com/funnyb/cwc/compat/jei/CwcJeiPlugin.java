package com.funnyb.cwc.compat.jei;

import com.funnyb.cwc.screen.AssemblingScreen;
import com.funnyb.cwc.screen.CraftingScreen;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * JEI 联动——只做一件事：告诉 JEI 我们的界面除了 imageWidth×imageHeight 那块矩形之外，
 * 左右还各占了 {@link #KEEP_OUT} 像素，让它的物品列表别压过来。
 * <p>
 * 为什么需要：JEI 通过原版的 {@code getGuiLeft/getGuiTop/getXSize/getYSize} 读界面矩形，
 * 据此把物品列表放到屏幕右侧没被界面占用的地方。我们的面板只有 176 宽，但面板右缘外还飘着
 * 一个帮助按钮（{@code help_button.x_offset = 178}，落在 imageWidth 之外），JEI 看不到它。
 * 补两条虚拟区域就把这个信息给上了。
 * <p>
 * 两点说明：
 * <ul>
 *   <li>报的是"界面额外占用的空间"，不是画出来的东西——这两条区域是透明的，玩家看不到任何变化，
 *       它们只是让 JEI 让位；</li>
 *   <li>窗口太窄、右侧本来就不够放 JEI 的列表时，JEI 仍会盖上来（把让位区算进去后只会更早发生），
 *       那时它已经无处可让——这是 JEI 的通用行为，不是这里能解决的。</li>
 * </ul>
 * 本类只在客户端被调用：JEI 只在客户端调 {@code registerGuiHandlers}，类里也没有静态的客户端
 * 类型引用（屏幕类只在方法体内出现），所以专用服务器上加载这个类不会出错。
 */
@JeiPlugin
public class CwcJeiPlugin implements IModPlugin {

    /** 插件 id——JEI 用它标识本插件 */
    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath("coldweaponcraftsmanship", "jei");

    /** 界面矩形左右各报多少像素的避让区 */
    private static final int KEEP_OUT = 100;

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(CraftingScreen.class, new KeepOutHandler<CraftingScreen>());
        registration.addGuiContainerHandler(AssemblingScreen.class, new KeepOutHandler<AssemblingScreen>());
    }

    /** 把界面矩形左右各撑出 {@link #KEEP_OUT} 像素报给 JEI */
    private static final class KeepOutHandler<T extends AbstractContainerScreen<?>>
            implements IGuiContainerHandler<T> {

        @Override
        public List<Rect2i> getGuiExtraAreas(T screen) {
            int top = screen.getGuiTop();
            int height = screen.getYSize();
            int left = screen.getGuiLeft();
            return List.of(
                    new Rect2i(left - KEEP_OUT, top, KEEP_OUT, height),
                    new Rect2i(left + screen.getXSize(), top, KEEP_OUT, height));
        }
    }
}
