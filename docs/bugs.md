# Bug 清单

## BUG-001 — 零件列表贴图全是铁标准刃

**状态：** 已关闭（2026-08-03 实测确认）

**位置：** 模组 GUI 内的零件列表 / 材料列表

**现象：** 所有类型的选项图标都渲染为铁标准刃贴图

**根因：** 旧 `custom_model_data` 哈希索引模型系统的 **ItemProperty 注册时机错误**——注册未生效导致所有图标回落默认模型（铁标准刃）

**修复：** 渲染架构改为 `AssembledWeaponRenderer`（BEWLR）依据 `PART_IDENTITY` 组件推导贴图路径，完全移除 `custom_model_data`/ItemProperty 依赖，不存在注册时机问题。制造界面列表 4 个类型图标各异，实测无异常。

---

## BUG-002 — 背包内标准刃物品贴图全是铁标准刃

**状态：** 已关闭（2026-08-03 实测确认）

**位置：** 原版 GUI（物品栏 / 创造物品栏）

**现象：** 所有标准刃变体的物品贴图相同

**根因：** 同 BUG-001——ItemProperty 注册时机错误

**修复：** 同 BUG-001，BEWLR 架构下贴图由零件 id 直接推导。实测无异常。

---

## BUG-003 — 悬停 tooltip 文字不显示

**状态：** 已修复

**位置：** 模组 GUI 内所有物品的悬停框

**现象：** tooltip 外框正常、文字不可见

**根因：** IconGrid 在 `super.render()` 之后渲染，`renderItem` 的画层覆盖了 tooltip 文字

**修复：** grid 移到 `super.render()` 之前——tooltip 在 grid 上面层渲染
