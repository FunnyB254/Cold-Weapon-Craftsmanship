package com.funnyb.cwc.menu;

import com.funnyb.cwc.layout.Layouts;
import com.funnyb.cwc.crafting.PartDef;
import com.funnyb.cwc.crafting.PartRegistry;
import com.funnyb.cwc.crafting.PartTypeDef;
import com.funnyb.cwc.crafting.WeaponStats;
import com.funnyb.cwc.registry.CwcDataComponents;
import com.funnyb.cwc.registry.CwcMenuTypes;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerListener;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * 装配界面容器——服务端权威 + 单向数据流。
 * <p>
 * 数据源：底座武器 ItemStack 上的 ASSEMBLED_SLOTS（槽位名→完整零件 ItemStack）。
 * scrollRows 通过 ContainerData 同步到客户端；partContainer 为显示缓存，内容从 ASSEMBLED_SLOTS 刷新；
 * 玩家拖入/拖出零件经 ContainerListener 同步回 ASSEMBLED_SLOTS，滚动刷新期间用 suppressSync 抑制回写。
 */
public class AssemblingMenu extends AbstractContainerMenu implements ContainerListener {

    public static final Component TITLE = Component.empty();

    /** 可见槽位数量（= 可视行数） */
    public static final int VISIBLE_ROWS = 3;

    public final InventoryLayout inventoryLayout;

    /** 底座槽——放入/取出被改造武器 */
    public final SimpleContainer baseContainer = new SimpleContainer(1);

    /** 3 个可见零件槽容器——仅显示缓存，内容由服务端从 ASSEMBLED_SLOTS 刷新 */
    public final SimpleContainer partContainer = new SimpleContainer(VISIBLE_ROWS);

    /** 滚动行数同步——ContainerData 自动双向同步到客户端 */
    private final SimpleContainerData scrollData = new SimpleContainerData(1);

    /** 玩家背包引用——用于装/取零件 */
    private final Inventory playerInventory;

    /** 滚动刷新 partContainer 期间抑制同步，避免读到中间态 */
    private boolean suppressSync = false;

    public AssemblingMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory);
    }

    public AssemblingMenu(int containerId, Inventory playerInventory) {
        super(CwcMenuTypes.ASSEMBLING.get(), containerId);
        this.playerInventory = playerInventory;
        var layout = Layouts.craftingMenu();
        this.inventoryLayout = new InventoryLayout(
                "textures/gui/assembling.png",
                layout.image_width, layout.image_height,
                layout.tex_width, layout.tex_height,
                layout.inventory_start_x, layout.inventory_start_y);
        inventoryLayout.addSlots(this::addSlot, playerInventory);

        // 底座槽——接受任意 CWC 零件（含自身没有槽位的镡/配重）。
        // 允许刃当底座是为了拼出"刃+镡"这类子装配体，再整体插进手柄的 blade 槽；
        // WeaponStats 现在递归遍历整棵装配树，子装配体里的零件属性都会计入。
        // 底座自身无槽位时界面显示 0 行、没得装，不会出错。
        this.addSlot(new Slot(baseContainer, 0, 51, 74) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.has(CwcDataComponents.PART_IDENTITY.get());
            }
        });

        // 3 个固定零件槽——普通 Slot 支持拖放，mayPlace 校验 constraint（坐标从布局 JSON 计算）
        var sl = Layouts.assemblingScreen().slot_list;
        int frameX = sl.x_offset + sl.scrollbar_width + sl.frame.x_offset;
        int frameY = sl.y_offset + sl.frame.y_offset;
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int visibleIndex = i;
            this.addSlot(new Slot(partContainer, i, frameX, frameY + i * sl.row_height) {
                @Override
                public boolean isActive() {
                    return getScrollRows() + visibleIndex < activePartSlotCount();
                }

                /** 放入校验：零件满足该可见槽位对应的真实槽位 constraint */
                @Override
                public boolean mayPlace(ItemStack stack) {
                    String id = stack.get(CwcDataComponents.PART_IDENTITY.get());
                    if (id == null) return false;
                    PartTypeDef.SlotDef slotDef = getVisibleSlotDef(visibleIndex);
                    if (slotDef == null) return false;
                    return isValidForSlot(slotDef, id);
                }
            });
        }

        // 滚动行数 ContainerData 注册（自动双向同步）
        addDataSlots(scrollData);

        // 注册容器监听——slotsChanged 依赖容器 addListener 触发
        baseContainer.addListener(this);
        partContainer.addListener(this);
    }

    // ──── 滚动控制 ────

    /** 客户端读滚动行数（ContainerData 同步值） */
    public int getScrollRows() {
        return scrollData.get(0);
    }

    /** 服务端设置滚动行数——客户端通过 SetScrollPacket 调用 */
    public void setScrollRows(int rows) {
        scrollData.set(0, Math.max(0, Math.min(rows, maxScrollRows())));
        refreshDisplay();
        broadcastChanges();
    }

    /** 最大滚动行数（总行数 - 可见 3 行） */
    public int maxScrollRows() {
        return Math.max(0, activePartSlotCount() - VISIBLE_ROWS);
    }

    // ──── 槽位查询 ────

    /** 底座武器的实际槽位行数（无武器返回 0） */
    public int activePartSlotCount() {
        PartTypeDef typeDef = baseTypeDef();
        return typeDef == null ? 0 : typeDef.slots().size();
    }

    /** 获取全量行索引对应的 SlotDef，越界返回 null */
    public PartTypeDef.SlotDef getSlotDefAt(int rowIndex) {
        PartTypeDef typeDef = baseTypeDef();
        if (typeDef == null || rowIndex < 0 || rowIndex >= typeDef.slots().size()) return null;
        return typeDef.slots().get(rowIndex);
    }

    /** 获取当前滚动下第 visibleIndex 个可见槽对应的真实 SlotDef */
    public PartTypeDef.SlotDef getVisibleSlotDef(int visibleIndex) {
        return getSlotDefAt(getScrollRows() + visibleIndex);
    }

    /** 读取底座武器 ASSEMBLED_SLOTS 中某槽位已装零件的完整 ItemStack，无则返回 EMPTY */
    public ItemStack getAssembledPartStack(String slotName) {
        ItemStack base = baseContainer.getItem(0);
        if (base.isEmpty()) return ItemStack.EMPTY;
        Map<String, ItemStack> map = base.get(CwcDataComponents.ASSEMBLED_SLOTS.get());
        return map == null ? ItemStack.EMPTY : map.getOrDefault(slotName, ItemStack.EMPTY);
    }

    /** 底座武器的类型定义 */
    public PartTypeDef baseTypeDef() {
        ItemStack base = baseContainer.getItem(0);
        if (base.isEmpty()) return null;
        String identity = base.get(CwcDataComponents.PART_IDENTITY.get());
        if (identity == null) return null;
        PartDef def = PartRegistry.getPartDef(identity);
        if (def == null) return null;
        return PartRegistry.getTypeDef(def.typeId());
    }

    // ──── 装配同步 ────

    /** 校验零件是否满足槽位 constraint */
    private boolean isValidForSlot(PartTypeDef.SlotDef slotDef, String partId) {
        PartDef partDef = PartRegistry.getPartDef(partId);
        if (partDef == null) return false;
        PartTypeDef partType = PartRegistry.getTypeDef(partDef.typeId());
        if (partType == null) return false;
        return matchesConstraint(slotDef, partType.data());
    }

    // ──── 显示刷新 ────

    /**
     * 从 ASSEMBLED_SLOTS 刷新可见 3 个零件槽（仅显示缓存，不改数据源）。
     * 直接放存储零件的副本，不再从 id 重建；填槽期间抑制同步，避免滚动刷新被反向写回。
     */
    public void refreshDisplay() {
        suppressSync = true;
        try {
            for (int i = 0; i < VISIBLE_ROWS; i++) {
                PartTypeDef.SlotDef slotDef = getVisibleSlotDef(i);
                if (slotDef == null) {
                    partContainer.setItem(i, ItemStack.EMPTY);
                    continue;
                }
                ItemStack stored = getAssembledPartStack(slotDef.name());
                partContainer.setItem(i, stored.isEmpty() ? ItemStack.EMPTY : stored.copy());
            }
        } finally {
            suppressSync = false;
        }
    }

    /**
     * 玩家拖入/拖出零件后（containerChanged 触发），把 partContainer 当前内容同步到底座 ASSEMBLED_SLOTS。
     * 每槽存零件完整 stack 的副本 → 写入对应槽位名映射；空槽移除映射。
     */
    private void syncPartSlots() {
        ItemStack base = baseContainer.getItem(0);
        if (base.isEmpty()) return;

        Map<String, ItemStack> map = new HashMap<>(base.getOrDefault(
                CwcDataComponents.ASSEMBLED_SLOTS.get(), Map.of()));
        boolean changed = false;

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            PartTypeDef.SlotDef slotDef = getVisibleSlotDef(i);
            if (slotDef == null) continue;
            ItemStack stack = partContainer.getItem(i);
            String slotName = slotDef.name();
            if (stack.isEmpty()) {
                if (map.remove(slotName) != null) changed = true;
            } else {
                ItemStack copy = stack.copy();
                ItemStack prev = map.put(slotName, copy);
                if (prev == null || !ItemStack.matches(prev, copy)) changed = true;
            }
        }

        if (changed) {
            base.set(CwcDataComponents.ASSEMBLED_SLOTS.get(), map);
            WeaponStats.apply(base);
            broadcastChanges();
        }
    }

    // ──── 容器方法 ────

    /**
     * 容器变化处理：
     * 底座槽变化 → 重置滚动并刷新显示；
     * 零件槽变化 → 玩家拖入/拖出时同步 ASSEMBLED_SLOTS（滚动刷新期间抑制）。
     */
    /** 容器变化监听——容器 addListener(this) 后由 SimpleContainer 触发 */
    @Override
    public void containerChanged(Container container) {
        if (container == baseContainer) {
            scrollData.set(0, 0);
            refreshDisplay();
            // 底座放入/更换时按已存 ASSEMBLED_SLOTS 重算武器属性（幂等，兜底旧物品）
            WeaponStats.apply(baseContainer.getItem(0));
        } else if (container == partContainer && !suppressSync) {
            syncPartSlots();
        }
    }

    /** 校验零件类型 data 是否满足槽位 constraint */
    private boolean matchesConstraint(PartTypeDef.SlotDef slotDef, Map<String, String> partData) {
        Map<String, java.util.List<String>> constraint = slotDef.constraint();
        if (constraint == null || constraint.isEmpty()) return true;
        for (Map.Entry<String, java.util.List<String>> entry : constraint.entrySet()) {
            // JSON 写 "constraint": { "weight": null } 时 GSON 会得到"键存在、值为 null"的条目
            java.util.List<String> allowed = entry.getValue();
            if (allowed == null) continue;                      // 值为 null 视为该键无约束，避免 NPE
            String value = partData.get(entry.getKey());
            if (value == null || !allowed.contains(value)) {
                return false;
            }
        }
        return true;
    }

    /** 名称长度上限——对齐原版铁砧的服务端限制 */
    public static final int MAX_NAME_LENGTH = 50;

    /**
     * 服务端修改底座物品的自定义名称——空名移除 CUSTOM_NAME 回落默认名。
     * 名称来自客户端包，必须在此夹长度：包用 STRING_UTF8（约 32KB），而该组件会同步给
     * 所有玩家并写进存档，不夹会被超长名称放大内存/带宽、拖慢 GUI 与存档。
     */
    public void renameItem(String name) {
        ItemStack stack = baseContainer.getItem(0);
        if (!stack.isEmpty()) {
            String sanitized = name == null ? "" : name.trim();
            if (sanitized.length() > MAX_NAME_LENGTH) {
                sanitized = sanitized.substring(0, MAX_NAME_LENGTH);
            }
            if (sanitized.isEmpty()) {
                stack.remove(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
            } else {
                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                        net.minecraft.network.chat.Component.literal(sanitized));
            }
            broadcastChanges();
        }
    }

    /** 容器关闭时只归还底座武器（零件已消费进武器 ASSEMBLED_SLOTS） */
    @Override
    public void removed(Player player) {
        super.removed(player);
        ItemStack base = baseContainer.removeItem(0, baseContainer.getMaxStackSize());
        if (!base.isEmpty()) {
            player.getInventory().placeItemBackInInventory(base);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
