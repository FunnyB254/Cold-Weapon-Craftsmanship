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

---

## BUG-004 — 双持短剑时左右手挥动动画不同时播放

**状态：** 搁置（2026-09-15）——**非本模组缺陷，是原版实体动画模型的限制**，修法是大工程，暂不实施

**位置：** 双持（主手 CWC 武器 + 副手短刀）时的挥动动画

**现象：** 左右手挥动动画有时不会同时播放，同一时刻往往只有一只手在动

**根因：** `LivingEntity` 的挥动状态 `swinging` / `swingTime` / `swingingArm` 是**全局单份字段**，不是每只手各一份。`LivingEntity.swing(InteractionHand, boolean)` 里有一道门：

```java
if (!this.swinging || this.swingTime >= this.getCurrentSwingDuration() / 2 || this.swingTime < 0) {
    this.swingTime = -1;
    this.swinging = true;
    this.swingingArm = hand;    // ← 覆盖：新手把旧手顶掉
    ...
}
```

- 挥动总时长 `getCurrentSwingDuration()` = **6 tick**，所以**前半程是 3 tick**
- 在这 3 tick 内触发的第二次挥动**被静默丢弃**——`if` 体根本不执行，不报错也不补播
- 即使触发成功，`swingingArm` 也是**被覆盖**的：新手顶掉旧手，而非两手各播各的

⇒ **两只手物理上不可能同时挥动。**

**次要因素（让"同时"更难发生）：** 两只手的输入路径不对称，天然错相——

| | 主手 | 副手 |
|---|---|---|
| 输入 | 左键 | 右键 |
| 节流 | 无 | `rightClickDelay = 4`（用一次锁 4 tick，`Minecraft.java:1714/2043`） |
| 额外门控 | 无 | `!player.isUsingItem()` |
| 冷却 | 原版攻速条 | 独立物品冷却键 `OFFHAND_COOLDOWN` |

两条冷却互相独立、几次之后必然漂移，所以是"有时"而非"总是"。

**已排除：** 客户端副手路径**没有**误发 `ServerboundSwingPacket`。`CwcClientEvents.onUseKey` 已正确使用 `event.setSwingHand(false)` + 双参 `mc.player.swing(InteractionHand.OFF_HAND, false)`，不存在"副手挥动经 `LocalPlayer.swing(hand)` 发包 → 服务端 `handleAnimate` → `ServerPlayer.swing` → `resetAttackStrengthTicker()` 把主手攻速条清零"的问题。**这条不用改。**

**可行修法（未实施）：** 恢复并扩写 `stash@{0}` 的第一人称武器动画系统，由该系统自行维护副手的挥动进度，经 `IClientItemExtensions.applyForgeHandTransform(...)` 独立驱动副手——该钩子**按手调用**（签名含 `HumanoidArm arm`），天然支持两只手各算各的。

**范围限制：** 上述修法**只对第一人称有效**；第三人称仍是原版限制，除非自建玩家模型。

**实施风险：** stash 里的 `CwcClientEvents.java` 是**改动前**的版本，而该文件此后被改过（`Layouts` 搬包、副手挥动、bus 注解），`git stash pop` 会冲突，需要手工合并。

---

## BUG-005 — 裸刃/子装配体可以直接当武器打人

**状态：** 已修复

**位置：** `WeaponStats.apply` 与装配台底座槽

**现象：** 装配台里"刃 + 镡"拼成子装配体后取出，拿在主手左键能打出武器伤害

**根因：** 装配台底座槽接受任意带 `PART_IDENTITY` 的零件（为拼子装配体而放开），于是 `WeaponStats.apply` 会把 `ATTRIBUTE_MODIFIERS` / `MAX_DAMAGE` / `BLOCK_VALUE` **写到那把刃身上**。

写入时用的是 `EquipmentSlotGroup.MAINHAND`，而**原版属性系统会把主手物品的 `ATTRIBUTE_MODIFIERS` 无条件计入玩家属性——它不检查这物品是不是武器**。主手攻击路径 `CwcCombatEvents.onAttack` 只拦 `HANDLE_PART`、非底座一律放行原版，于是这把"数据上不是武器、属性上已经是"的刃就经原版 `Player.attack` 打出了伤害。

**修复：** `WeaponStats.apply` 只对 `HANDLE_PART` 写入；非手柄底座调 `clearStats` 清掉残留组件。递归聚合读的是 `PartRegistry` 里的 `PartDef.data()`，与栈上有没有组件无关，所以子装配体插进手柄后数值一分不少。

**附带：** 已被污染的旧物品放回装配台底座槽走一次 `apply` 即可清除。

---

## BUG-006 — CWC 武器挖不了方块

**状态：** 已修复

**位置：** `CwcClientEvents.onAttackKey`

**现象：** 拿本模组武器挖不动任何方块

**根因：** 该事件有**两个 post 来源**，且命中方块时两者无法从事件本身区分：

| 来源 | 时机 |
|---|---|
| `Minecraft.startAttack()` `:1680` | 每次左键**点击** |
| `Minecraft.continueAttack()` `:1642` | 按住左键 **且准星命中非空气方块**，**每 tick 一次** |

原版把挖方块的 `startDestroyBlock` / `continueDestroyBlock` 都放在 `if (!inputEvent.isCanceled())` **之内**，而 `onAttackKey` 无条件 `setCanceled(true)` → 挖方块分支永不执行。

**修复：** 准星命中非空气方块时直接放行（不取消也不出刀），语义回到"指方块 = 挖掘，否则 = 攻击"。判定用 `isMiningTarget(mc)`。

**已知取舍：** 准星指着方块时不能挥击/横扫（但那时本来也打不到实体）。

---

## BUG-007 — 换武器后第一次攻击空挥（有动画无伤害）

**状态：** 已修复

**位置：** `CwcClientEvents.tryMainHandAttack` / `CwcCombat.isServerCooldownReady`

**现象：** 换武器后立刻攻击，第一下只播动画、没有伤害；第二下起正常。**从普通物品切到模组武器时同样触发**（不限于两把模组武器互换）。

**根因：** 客户端与服务端的 `attackStrengthTicker` 存在**固定相位差**——客户端的复位（攻击后、换武器时的装备变更检测）是本地的、立刻生效；服务端要等包到达、在它自己的 tick 里处理才算数。换武器那一刻两边同时归零，但**服务端晚约 2 tick**，于是客户端先到满格，它的第一次出手正好落在服务端还没就绪的窗口里，整个包被 `performMainHandAttack` 丢弃。

客户端并不知道被拒，照常复位自己的攻速条，于是要等满一个冷却才能再试——所以**只有第一下**受害。

**实测数据**（临时诊断日志）：同一刻客户端 `scale=1.0`（认为就绪），服务端 `scale=0.945`（即 `18/19.0476`）。

**修复：**
- 服务端 `isServerCooldownReady` 用 `getAttackStrengthScale(3.0f)`，即门槛放宽 3 tick；门槛取客户端**手动**那一档（0.9），因为包不带"点击还是自动"的信息
- 客户端 `mainHandSettled` 在换武器后等 2 tick 再出手（`WEAPON_SETTLE_TICKS`）

**为什么容差取 3 而不是实测的 2：** 取 1 时门槛落在 `19/19.0476 = 0.9975`，**差 0.0025 没够**；取正好 2 又会坐在边界上，浮点末位或网络抖动一变就复发。多留 1 tick 让服务端门槛明显低于客户端。

**通用性：** 该相位差对**每一次**攻击都存在（日志中服务端以 `scale=0.81` 接受命中即为证据），不只是换武器之后。

**根治方案（未实施）：** 服务端自己记录"上次攻击的 tick"来限速，完全不依赖会错位的原版 ticker。能一次性消掉这一整类问题。

---

## BUG-008 — 放置方块时副手短刀仍然出刀

**状态：** 已修复（**回归**：由"按住右键自动攻击"的 tick 驱动引入）

**位置：** `CwcClientEvents.onClientTick`

**根因：** 原版 `startUseItem` 是 `MAIN_HAND → OFF_HAND` 的顺序循环，**主手 `consumesAction` 时会直接 `return`，副手那一轮根本不会发生**。tick 驱动直接调 `tryOffhandAttack`，绕过了这个判断。

**修复：** `onUseKey` 记录本 tick `startUseItem` 走到了哪一轮，tick 驱动复刻同一个判断：

| 本 tick 观察到 | 含义 | 动作 |
|---|---|---|
| 主手迭代有、副手迭代也有 | 主手没消费（`pass`） | 恢复副手自动攻击 |
| 主手迭代有、副手迭代没有 | 主手消费了（放方块/交互） | **挂起**副手自动攻击 |

挂起状态是**粘性**的——放置之后的几 tick `startUseItem` 被 `rightClickDelay` 挡住不跑、旗子不动，挂起一直保持到松开右键或某次真的走到副手迭代。

---

## BUG-009 — 格挡减伤"有镡"与"无镡"看不出差别

**状态：** 已修复

**位置：** `CwcCombatEvents.onHurt` + `parts/cwc/light_guard/*.json`

**现象：** 双手武器格挡时，装不装镡感受不到差别

**根因（两重）：**
1. **数据缺失**：7 种镡材质里只有 `iron` 写了 `block` 字段，其余 6 种完全没有 → `attr(def,"block")` 返回 0，对多数材质"有镡"这件事根本不存在
2. **差值太小**：即使用铁镡，也只是 50% → 60%，10 个百分点经护甲削完落到血量差只有零点几

**修复：**
- 基础减伤具名常量化：`BASE_BLOCK_REDUCTION = 0.25f`（原为行内字面量 `0.5f`）
- 7 种镡统一 `"block": 0.25`
- 结果：无镡 25%、有镡 50%，**差一倍**，一眼可辨

**上下限保持不变：** `Mth.clamp(reduction, 0f, 0.95f)` 防的是负 `BLOCK_VALUE` 反而放大伤害，与本次调值无关。
