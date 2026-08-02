package com.funnyb.cwc.screen;

import com.funnyb.cwc.client.Layouts;
import com.funnyb.cwc.network.serverbound.OpenAssemblingPacket;
import com.funnyb.cwc.network.serverbound.OpenCraftingPacket;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 创造零件之星选择界面——右键 CreativePartStar 后打开的中间界面。
 * <p>
 * 布局：artisan_table.png 背景（256×166），居中对齐。
 * 左侧按钮→"制造零件"（发送网络包打开制造界面），
 * 右侧按钮→"组装零件"（目前仅打日志，功能待实现）。
 * <p>
 * 所有控件坐标和颜色从 assets/cwc/gui/creative_part_star_screen.json 读取，资源包可覆盖。
 */
public class CreativePartStarScreen extends Screen implements CwcScreen {

    /** 背景贴图 */
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("coldweaponcraftsmanship", "textures/gui/artisan_table.png");

    public CreativePartStarScreen() {
        super(Component.translatable("item.coldweaponcraftsmanship.creative_part_star"));
    }

    /**
     * 初始化两个透明按钮：
     * <ul>
     *   <li>左侧——制造零件按钮，发送 OpenCraftingPacket 请求打开制造界面</li>
     *   <li>右侧——组装零件按钮，功能未实现，仅打印日志</li>
     * </ul>
     */
    @Override
    protected void init() {
        var lay = Layouts.creativePartStar();
        int left = (this.width - lay.image_width) / 2;
        int top = (this.height - lay.image_height) / 2;

        this.addRenderableWidget(new ImageButton(
                left + lay.button_craft.x_offset, top + lay.button_craft.y_offset,
                lay.button_craft.width, lay.button_craft.height,
                () -> PacketDistributor.sendToServer(new OpenCraftingPacket())));

        this.addRenderableWidget(new ImageButton(
                left + lay.button_assemble.x_offset, top + lay.button_assemble.y_offset,
                lay.button_assemble.width, lay.button_assemble.height,
                () -> PacketDistributor.sendToServer(new OpenAssemblingPacket())));
    }

    /**
     * 渲染顺序：
     * <ol>
     *   <li>半透明暗色背景遮罩</li>
     *   <li>artisan_table.png 背景贴图</li>
     *   <li>"制造零件" / "组装零件" 文字标签</li>
     *   <li>两个透明按钮的 hover 高亮层</li>
     * </ol>
     */
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        var lay = Layouts.creativePartStar();
        int left = (this.width - lay.image_width) / 2;
        int top = (this.height - lay.image_height) / 2;

        // 背景贴图
        guiGraphics.blit(TEXTURE, left, top, 0, 0,
                lay.image_width, lay.image_height, lay.tex_width, lay.tex_height);

        // 文字标签
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.coldweaponcraftsmanship.craft_parts"),
                left + lay.label_craft.x_offset, top + lay.label_craft.y_offset,
                lay.label_craft.color);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.coldweaponcraftsmanship.assemble_parts"),
                left + lay.label_assemble.x_offset, top + lay.label_assemble.y_offset,
                lay.label_assemble.color);

        // 按钮（hover 高亮叠加层）
        for (var widget : this.renderables) {
            widget.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    /** 半透明暗色遮罩——覆盖整个屏幕，突出前景界面 */
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height,
                Layouts.creativePartStar().background_color);
    }

    /** 非暂停界面——打开时游戏继续运行 */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
