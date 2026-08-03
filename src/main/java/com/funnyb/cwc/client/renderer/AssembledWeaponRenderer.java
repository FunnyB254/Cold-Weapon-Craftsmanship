package com.funnyb.cwc.client.renderer;

import com.funnyb.cwc.crafting.PartDef;
import com.funnyb.cwc.crafting.PartRegistry;
import com.funnyb.cwc.crafting.PartStacks;
import com.funnyb.cwc.crafting.PartTypeDef;
import com.funnyb.cwc.registry.CwcDataComponents;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 装配复合渲染器——把整把武器（底座 + 所有层级零件）在 CPU 上按 layer 顺序合成到一张贴图，
 * 渲染时只画这一张合成贴图的正面/背面/边缘面（与原版平面物品一致）。
 *
 * 为什么这样设计：逐零件共面绘制会引入共面深度 ULP 竞争（闪烁）、零件几何偏移（可见分层/背面反转）、
 * 两趟深度预通道（背面/侧面/接缝的各种问题）。合成到单张贴图后：
 * - 只有一个 quad → 无共面竞争、不闪烁；
 * - 无逐零件几何偏移、无相机运算；
 * - 深度写入保留 → Block Outline 不透过、Fabulous 展示框正常合成；
 * - 正/背两面一致，侧面（边缘面）实心。
 */
public class AssembledWeaponRenderer extends BlockEntityWithoutLevelRenderer {

    /** 薄片正面 z（原版 ItemModelGenerator 实测 8.5/16） */
    private static final float FRONT_Z = 0.53125f;
    /** 薄片背面 z（原版实测 7.5/16）——1px 厚度 */
    private static final float BACK_Z = 0.46875f;

    /** 合成贴图缓存：装配键 → 合成体 */
    private static final Map<String, WeaponComposite> COMPOSITES = new HashMap<>();

    /**
     * 合成体：CPU 图片 + 动态纹理路径 + 渲染类型 + 模型包围盒 + 顶点网格缓存。
     * CACHE: 网格构建（逐像素双循环 + 邻域判断）只在贴图变更时执行一次，渲染时直接遍历缓存顶点。
     */
    private static final class WeaponComposite {
        private final NativeImage image;
        private final ResourceLocation texPath;
        private final RenderType renderType;
        private final float bx0, by0, bx1, by1;

        /** CACHE: 构建好的顶点数据（模型空间坐标，渲染时经姿态矩阵变换） */
        private final List<CachedVertex> cachedVertices = new ArrayList<>();
        /** CACHE: 缓存脏标记——true 表示需要重建网格 */
        private boolean dirty = true;

        WeaponComposite(NativeImage image, ResourceLocation texPath, RenderType renderType,
                        float bx0, float by0, float bx1, float by1) {
            this.image = image;
            this.texPath = texPath;
            this.renderType = renderType;
            this.bx0 = bx0;
            this.by0 = by0;
            this.bx1 = bx1;
            this.by1 = by1;
        }

        RenderType renderType() { return renderType; }
        /** CACHE: 顶点缓存 */
        List<CachedVertex> cachedVertices() { return cachedVertices; }
        /** CACHE: 是否需重建网格 */
        boolean dirty() { return dirty; }

        /** CACHE: 贴图/几何变更时标记缓存失效并清空，下次渲染时重建 */
        void markDirty() {
            dirty = true;
            cachedVertices.clear();
        }

        /**
         * CACHE: 构建网格——把原 renderComposite 的逐像素双循环 + 邻域判断搬到此处，
         * 顶点数据（位置/UV/法线/颜色）一次性算好存入 cachedVertices，数值与原实现完全一致。
         */
        void buildMesh() {
            cachedVertices.clear();
            int w = image.getWidth();
            int h = image.getHeight();
            float pw = (bx1 - bx0) / w;   // 单像素模型宽
            float ph = (by1 - by0) / h;   // 单像素模型高
            float uw = 1f / w;            // 单像素 UV 宽
            float vh = 1f / h;            // 单像素 UV 高

            // 正面（SOUTH 面）：逆时针绕序，法线 +Z，UV 顶 v0 在模型顶部（y=by1）
            add(bx0, by1, FRONT_Z, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f);
            add(bx0, by0, FRONT_Z, 0f, 1f, 0f, 0f, 1f, 1f, 1f, 1f);
            add(bx1, by0, FRONT_Z, 1f, 1f, 0f, 0f, 1f, 1f, 1f, 1f);
            add(bx1, by1, FRONT_Z, 1f, 0f, 0f, 0f, 1f, 1f, 1f, 1f);
            // 背面（NORTH 面）：顺时针绕序，法线 -Z（UV 相同 → 从背面看左右镜像）
            add(bx1, by1, BACK_Z, 1f, 0f, 0f, 0f, -1f, 1f, 1f, 1f);
            add(bx1, by0, BACK_Z, 1f, 1f, 0f, 0f, -1f, 1f, 1f, 1f);
            add(bx0, by0, BACK_Z, 0f, 1f, 0f, 0f, -1f, 1f, 1f, 1f);
            add(bx0, by1, BACK_Z, 0f, 0f, 0f, 0f, -1f, 1f, 1f, 1f);

            // 沿合成贴图 alpha 轮廓逐像素生成边缘面（"体素堆积"观感）
            boolean[][] opaque = buildOpaque(image, w, h);
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    if (!opaque[x][y]) continue;
                    float xL = bx0 + x * pw;
                    float xR = bx0 + (x + 1) * pw;
                    float yT = by1 - y * ph;
                    float yB = by1 - (y + 1) * ph;
                    float pu = x * uw;
                    float pv = y * vh;
                    // 上边界（法线 +Y）
                    if (!isOpaque(opaque, x, y - 1, w, h)) {
                        add(xL, yT, FRONT_Z, pu, pv, 0f, 1f, 0f, 1f, 1f, 1f);
                        add(xR, yT, FRONT_Z, pu + uw, pv, 0f, 1f, 0f, 1f, 1f, 1f);
                        add(xR, yT, BACK_Z, pu + uw, pv + vh, 0f, 1f, 0f, 1f, 1f, 1f);
                        add(xL, yT, BACK_Z, pu, pv + vh, 0f, 1f, 0f, 1f, 1f, 1f);
                    }
                    // 下边界（法线 -Y）
                    if (!isOpaque(opaque, x, y + 1, w, h)) {
                        add(xL, yB, BACK_Z, pu, pv + vh, 0f, -1f, 0f, 1f, 1f, 1f);
                        add(xR, yB, BACK_Z, pu + uw, pv + vh, 0f, -1f, 0f, 1f, 1f, 1f);
                        add(xR, yB, FRONT_Z, pu + uw, pv, 0f, -1f, 0f, 1f, 1f, 1f);
                        add(xL, yB, FRONT_Z, pu, pv, 0f, -1f, 0f, 1f, 1f, 1f);
                    }
                    // 左边界（法线 -X）
                    if (!isOpaque(opaque, x - 1, y, w, h)) {
                        add(xL, yB, BACK_Z, pu, pv + vh, -1f, 0f, 0f, 1f, 1f, 1f);
                        add(xL, yB, FRONT_Z, pu + uw, pv + vh, -1f, 0f, 0f, 1f, 1f, 1f);
                        add(xL, yT, FRONT_Z, pu + uw, pv, -1f, 0f, 0f, 1f, 1f, 1f);
                        add(xL, yT, BACK_Z, pu, pv, -1f, 0f, 0f, 1f, 1f, 1f);
                    }
                    // 右边界（法线 +X）
                    if (!isOpaque(opaque, x + 1, y, w, h)) {
                        add(xR, yB, FRONT_Z, pu + uw, pv + vh, 1f, 0f, 0f, 1f, 1f, 1f);
                        add(xR, yB, BACK_Z, pu, pv + vh, 1f, 0f, 0f, 1f, 1f, 1f);
                        add(xR, yT, BACK_Z, pu, pv, 1f, 0f, 0f, 1f, 1f, 1f);
                        add(xR, yT, FRONT_Z, pu + uw, pv, 1f, 0f, 0f, 1f, 1f, 1f);
                    }
                }
            }
            dirty = false;
        }

        /** CACHE: 追加一个顶点（位置/UV/法线/颜色，模型空间） */
        private void add(float x, float y, float z, float u, float v,
                         float nx, float ny, float nz, float r, float g, float b) {
            cachedVertices.add(new CachedVertex(x, y, z, u, v, nx, ny, nz, r, g, b));
        }
    }

    /** CACHE: 缓存顶点——writeVertex 的固定参数（模型空间坐标，渲染时经姿态矩阵变换） */
    private record CachedVertex(float x, float y, float z, float u, float v,
                                float nx, float ny, float nz, float r, float g, float b) {}

    public AssembledWeaponRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet modelSet) {
        super(dispatcher, modelSet);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context,
                             PoseStack pose, MultiBufferSource buffer, int light, int overlay) {
        String baseId = stack.get(CwcDataComponents.PART_IDENTITY.get());
        if (baseId == null) return;

        if (context == ItemDisplayContext.GUI) {
            Lighting.setupForFlatItems();
        }

        // 收集底座 + 递归全部层级零件，按 layer 升序（底座最先、最底层）
        List<PartRender> parts = new ArrayList<>();
        int[] order = {0};
        PartDef baseDef = PartRegistry.getPartDef(baseId);
        PartTypeDef typeDef = baseDef != null ? PartRegistry.getTypeDef(baseDef.typeId()) : null;
        parts.add(new PartRender(PartStacks.partIcon(baseId), 0f, 0f,
                typeDef != null ? typeDef.layerValue() : 0.0, order[0]++));
        collectChildren(stack, 0f, 0f, parts, order);
        parts.sort(Comparator.comparingDouble(PartRender::layer).thenComparingInt(PartRender::order));

        WeaponComposite comp = getComposite(parts);
        if (comp == null) return;

        pose.pushPose();
        // 只画一张合成贴图：正面/背面/边缘面，深度写、非排序
        renderComposite(comp, pose, buffer, light, overlay);
        pose.popPose();
    }

    /** 取合成体（缓存命中直接返回，未命中则合成并缓存） */
    private static WeaponComposite getComposite(List<PartRender> parts) {
        StringBuilder key = new StringBuilder();
        for (PartRender p : parts) {
            String id = p.stack().get(CwcDataComponents.PART_IDENTITY.get());
            if (id != null) key.append(id).append('|');
        }
        String k = key.toString();
        WeaponComposite comp = COMPOSITES.get(k);
        if (comp == null) {
            comp = buildComposite(parts);
            if (comp != null) COMPOSITES.put(k, comp);
        }
        return comp;
    }

    /** 把各零件贴图按 layer 顺序合成到一张贴图并上传为动态纹理 */
    private static WeaponComposite buildComposite(List<PartRender> parts) {
        float bx0 = Float.MAX_VALUE, by0 = Float.MAX_VALUE;
        float bx1 = -Float.MAX_VALUE, by1 = -Float.MAX_VALUE;
        for (PartRender p : parts) {
            bx0 = Math.min(bx0, p.dx());
            by0 = Math.min(by0, p.dy());
            String id = p.stack().get(CwcDataComponents.PART_IDENTITY.get());
            // 包围盒按零件实际贴图尺寸扩展（支持非 16×16 贴图），默认 16px 兜底
            float pw = 1f, ph = 1f;
            if (id != null) {
                NativeImage src = partSourceImage(id);
                pw = src.getWidth() / 16f;
                ph = src.getHeight() / 16f;
            }
            bx1 = Math.max(bx1, p.dx() + pw);
            by1 = Math.max(by1, p.dy() + ph);
        }
        int cw = Math.round((bx1 - bx0) * 16);
        int ch = Math.round((by1 - by0) * 16);
        if (cw <= 0 || ch <= 0) return null;

        NativeImage canvas = new NativeImage(cw, ch, true);
        for (PartRender p : parts) {
            String id = p.stack().get(CwcDataComponents.PART_IDENTITY.get());
            if (id == null) continue;
            NativeImage src = partSourceImage(id);
            // 合成画布：第 0 行 = 模型顶部（y=by1）；零件贴图行 ty 在模型 y=dy+sh/16-ty/16
            int ox = Math.round((p.dx() - bx0) * 16);
            int oy = Math.round((by1 - p.dy() - src.getHeight() / 16f) * 16);
            int sw = src.getWidth(), sh = src.getHeight();
            for (int ty = 0; ty < sh; ty++) {
                for (int tx = 0; tx < sw; tx++) {
                    int rgba = src.getPixelRGBA(tx, ty);
                    if (((rgba >>> 24) & 0xFF) == 0) continue;
                    int cx = ox + tx;
                    int cy = oy + ty;
                    if (cx < 0 || cy < 0 || cx >= cw || cy >= ch) continue;
                    canvas.setPixelRGBA(cx, cy, rgba);
                }
            }
        }

        ResourceLocation path = ResourceLocation.fromNamespaceAndPath(
                "cwc", "weapon_" + Integer.toHexString(System.identityHashCode(canvas)));
        DynamicTexture texture = new DynamicTexture(canvas);
        texture.upload();
        Minecraft.getInstance().getTextureManager().register(path, texture);

        RenderType rt = RenderType.create(
                "cwc_weapon_" + path.getPath(),
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1536,
                false,   // affectsCrumbling
                false,   // sortOnUpload —— 单 quad，无需排序
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(
                                GameRenderer::getRendertypeItemEntityTranslucentCullShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(path, false, false))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .setOverlayState(RenderStateShard.OVERLAY)
                        .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                        .createCompositeState(true));
        return new WeaponComposite(canvas, path, rt, bx0, by0, bx1, by1);
    }

    /** 清空合成缓存（资源重载后动态纹理被释放，需重建） */
    public static void clearCache() {
        COMPOSITES.clear();
    }

    /** 渲染一张合成贴图：正面/背面/边缘面（深度写、非排序） */
    private static void renderComposite(WeaponComposite comp, PoseStack pose, MultiBufferSource buffer,
                                        int light, int overlay) {
        VertexConsumer consumer = buffer.getBuffer(comp.renderType());
        PoseStack.Pose p = pose.last();
        // CACHE: 网格缓存未构建/已标记脏时重建一次，其余渲染直接复用
        if (comp.dirty()) comp.buildMesh();
        // CACHE: 直接遍历缓存顶点（原逐像素循环和 buildOpaque 已移入 buildMesh）
        for (CachedVertex v : comp.cachedVertices()) {
            writeVertex(consumer, p, v.x(), v.y(), v.z(), v.u(), v.v(),
                    v.nx(), v.ny(), v.nz(), v.r(), v.g(), v.b(), light, overlay);
        }
    }

    /**
     * 递归收集 stack 各槽位已装零件（含嵌套子零件）的渲染项。
     * 子零件偏移 = 父零件偏移 + (槽位安装点 − 子件安装点)/16，逐层累加。
     */
    private void collectChildren(ItemStack stack, float bx, float by,
                                 List<PartRender> out, int[] order) {
        String id = stack.get(CwcDataComponents.PART_IDENTITY.get());
        if (id == null) return;
        PartDef def = PartRegistry.getPartDef(id);
        PartTypeDef type = def != null ? PartRegistry.getTypeDef(def.typeId()) : null;
        if (type == null) return;
        Map<String, ItemStack> children = stack.get(CwcDataComponents.ASSEMBLED_SLOTS.get());
        if (children == null) return;
        for (PartTypeDef.SlotDef slotDef : type.slots()) {
            ItemStack partStack = children.get(slotDef.name());
            if (partStack == null || partStack.isEmpty()) continue;
            String partId = partStack.get(CwcDataComponents.PART_IDENTITY.get());
            PartDef partDef = partId != null ? PartRegistry.getPartDef(partId) : null;
            PartTypeDef partType = partDef != null ? PartRegistry.getTypeDef(partDef.typeId()) : null;
            if (partType == null) continue;
            float dx = (slotDef.positionX() - partType.positionX()) / 16.0f;
            // 贴图 y 向下、模型 y 向上，dy 符号与 dx 相反
            // 高度补偿：子件贴图比父件高时整体下移，使安装点与槽位对齐（等高校时补偿为 0）
            float dy = (partType.positionY() - slotDef.positionY()) / 16.0f
                    + (partSourceImage(id).getHeight() - partSourceImage(partId).getHeight()) / 16f;
            out.add(new PartRender(partStack, bx + dx, by + dy, partType.layerValue(), order[0]++));
            // 嵌套子零件（如刃上的护手）
            collectChildren(partStack, bx + dx, by + dy, out, order);
        }
    }

    /** 取零件 id 对应的合成源贴图（atlas 中的原始图片，非裁剪区域） */
    private static NativeImage partSourceImage(String id) {
        return Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(textureOf(id))
                .contents().getOriginalImage();
    }

    /** 构建贴图不透明掩码（alpha > 0；NativeImage ABGR 格式，alpha 在最高字节） */
    private static boolean[][] buildOpaque(NativeImage img, int w, int h) {
        boolean[][] result = new boolean[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                result[x][y] = ((img.getPixelRGBA(x, y) >>> 24) & 0xFF) > 0;
            }
        }
        return result;
    }

    /** 邻居是否不透明（越界视为透明） */
    private static boolean isOpaque(boolean[][] opaque, int x, int y, int w, int h) {
        return x >= 0 && y >= 0 && x < w && y < h && opaque[x][y];
    }

    /** 写入单个 quad 顶点：位置/法线经姿态矩阵变换 */
    private static void writeVertex(VertexConsumer consumer, PoseStack.Pose p,
                                    float x, float y, float z, float u, float v,
                                    float nx, float ny, float nz,
                                    float r, float g, float b, int light, int overlay) {
        Vector3f pos = new Vector3f(x, y, z);
        pos.mulPosition(p.pose());
        Vector3f normal = new Vector3f(nx, ny, nz);
        normal.mul(p.normal());
        consumer.addVertex(pos.x, pos.y, pos.z)
                .setColor(r, g, b, 1f)
                .setUv(u, v)
                .setLight(light)
                .setOverlay(overlay)
                .setNormal(normal.x, normal.y, normal.z);
    }

    /**
     * 由零件 id 推导贴图路径：cwc.&lt;type&gt;.&lt;material&gt; → coldweaponcraftsmanship:item/cwc/&lt;type&gt;/&lt;material&gt;。
     * 类型 id（无 material）用该类型第一个零件的贴图作代表。
     */
    private static ResourceLocation textureOf(String id) {
        if (id.indexOf('.') == id.lastIndexOf('.')) {
            // 类型 id——取该类型第一个零件（按 id 排序）作代表
            var parts = PartRegistry.getPartsByType(id).stream()
                    .sorted(Comparator.comparing(PartDef::id))
                    .toList();
            if (!parts.isEmpty()) id = parts.get(0).id();
        }
        String rest = id.substring(id.indexOf('.') + 1);
        return ResourceLocation.fromNamespaceAndPath("coldweaponcraftsmanship",
                "item/cwc/" + rest.replace('.', '/'));
    }

    /** 单个渲染项：栈 + 锚点偏移 + 图层优先级 */
    private record PartRender(ItemStack stack, float dx, float dy, double layer, int order) {}
}
