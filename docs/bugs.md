# Bug 清单

## 验证记录（已实测通过）

> 这些项目的**验收项原先记在 `tmp/TEST-CHECKLIST.md`，跑过之后已从那里删除、归档到这里**
> （那份清单现在只保留尚未在游戏里跑过的项）。逐条的验收过程不再保留——要复跑时看各自的「状态」行与对应条目；
> **回归风险高的那几条仍在清单的 E 节，每批改完重跑一遍。**

| 项目 | 验证日期 | 方式 |
|---|---|---|
| **零件定义迁移为 datapack registry**（含专用服务器 + 第三方命名空间） | 2026-09-18 | 服务端实测：日志 `Loaded 8 part types / 38 parts`、干净客户端在**配置阶段**连入成功（拿不到注册表就进不去）、`/reload` 后不失效；离线用真实 DFU codec 全量解析 + NBT 往返；探测数据包（`data/testpack/…`）使 38 → 39 parts。同批还验了属性 tooltip 新格式 |
| **批次 B 战斗权威**（无敌帧收窄 + 友军统一 + 目标判定合并） | 2026-09-19 | 客户端实测：连续命中每下都结算、目标刚被别的来源（摔落/火焰）打过时不再抹掉那段保护、横扫友伤边界（自己的宠物/坐骑不卷、准星正对则照打、队友看队伍 `friendlyFire`） |
| BUG-027 耐久计费口径（横扫只有副目标吃到伤害时也扣 1） | 2026-09-18 | 客户端实测 |
| BUG-028 服务端主手冷却改自持时钟 | 2026-09-18 | 客户端实测：打完杂草立刻攻击不再空挥、连击不丢、换武器首击仍生效 |
| BUG-029 横扫不打自己的宠物 / 坐骑 | 2026-09-19 | 客户端实测 |
| BUG-030 划船时按移动键仍能攻击（`handsBusy`） | 2026-09-19 | 客户端实测：点击与按住都不出刀、松开即恢复、挖掘与矿车不受影响 |
| BUG-031 骑乘链保护（骑猪不再打自己的猪） | 2026-09-19 | 客户端实测 |
| BUG-010 连续攻击不丢（功能面） | 2026-09-19 | 客户端实测：连续命中 20 次以上每次都掉血 |
| BUG-011 快捷栏有 CWC 物品时方块物品光照变平 | 2026-09-19 | 客户端实测：开关物品栏不再重复掏出、方块物品光照正常 |
| BUG-019 / BUG-020 装配台交互（缩放后选择态脱节 / 滚不动也发包） | 2026-09-19 | 客户端实测（含拖窗口、全屏切换、滚轮被列表吞掉） |
| **GUI 会话整轮**（版面尺寸 / 标题 / 帮助按钮 / 详情热区 / 空行 / 命名框 / JEI 避让） | 2026-09-19 | 作者实测：45 项通过，2 项标 `N/A · 条件未到`（列表当前滚不动，等槽位变多再补验） |
| 准星抽类 + 界面隐藏 HUD/手持物品 + 主副手点选合并 | 2026-09-24 | 作者确认无问题（`5f622a5` / `effaaff` / `a6b3929`） |

**「刻意不做」那几条**（不给改包客户端补防护、不收紧服务端冷却容差）**不是遗漏**——各自的理由与
"为什么收益小于风险"写在 BUG-012 / BUG-013 / BUG-014 里，别当漏洞再修一遍。

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

---
---

# 2026-09-17 全项目审计

对全部 5.3k 行源码做了一次完整审计（对着反编译原版与 NeoForge 源码逐条核实机制），发现的问题按**根因**分组修复。
下表是完整登记，含**决定不修**的项与理由——不修的项同样重要，否则下次会被当成漏洞重查一遍。

## BUG-010 — 开/关物品栏时武器重复"掏出"；每次掉耐久的命中后可能白丢一次攻击

**状态：** 已修复并验证（2026-09-19 客户端实测：连续命中 20 次以上每次都掉血；开关物品栏不再重复掏出）（根治数据模型）

**位置：** `registry/CwcDataComponents.ASSEMBLED_SLOTS` 的值类型

**根因（一个根因，三个症状）：**

`ASSEMBLED_SLOTS` 的值是 `Map<String, ItemStack>`，而 **`ItemStack` 不覆写 `equals`（是身份比较）**。
于是同一把武器经过一次网络反序列化后，内层 `ItemStack` 全是新实例 →
`ItemStack.matches` → `isSameItemSameComponents` → `PatchedDataComponentMap.equals` → `Map.equals`
→ 内层身份比较 → **恒返回 false**。

这一条假"变化"分成三条岔路：

| 判定处 | 门槛 | 症状 |
|---|---|---|
| `ItemInHandRenderer:565` | `matches` 为真才"认领"新实例 → 没认领则 `:578` 的 `!oldStack.equals(newStack)`（**身份**）为真 | **重播掏出动画** |
| `Player.tick():321-328` | 需要 `matches` 假**且** `isSameItem` 假才重置 ticker | `isSameItem` 为真 → **不重置 → 冷却不受影响**（这解释了为什么"只影响视觉、不影响冷却"） |
| `CwcClientEvents.mainHandSettled` | `Objects.equals(Map<String,ItemStack>, …)` | **每次掉耐久**（服务端重发整个栈）都判成"刚换武器" → 丢掉那次点击 + 随后 2 tick 不能出手 → **白丢攻击** |

触发时机：开/关物品栏时服务端发 `ClientboundContainerSetContentPacket`，
`AbstractContainerMenu.initializeContents:611` **无条件** `set(items.get(i))`，把手持栈换成新解码实例。

**修复：** 值类型改为 `PartNode`（record：`id` + 递归 `children`）。record 的 `equals` 逐字段成立，
`Map<String,PartNode>` 恢复正确的值语义，三个症状一并消失。

**为什么必须是递归结构而不是扁平的"槽位名 → id"映射：** 装配树本来就允许嵌套（装配台底座槽接受任意零件，
正是为了拼出"刃+镡"这类子装配体再整体插进手柄的 blade 槽）。扁平映射没有地方放子件的子树。

**顺带修好（同一根因）：** `AssemblingMenu` 的零件变更检测原先用 `ItemStack.matches(prev, copy)` 比两个
不同实例，**恒判为变** → 每次容器变化都全量重算 + 多发同步包；现在用 `PartNode.equals`，只在真变时为真。

---

## BUG-011 — 快捷栏里有 CWC 物品时，物品栏里方块类物品光照变平

**状态：** 已修复并验证（2026-09-19 客户端实测）

**根因：** `part.json` / `handle_part.json` 的父模型是 `builtin/entity`，而 `ModelBakery:76` 把它硬编码为
`BlockModel.fromString("{\"gui_light\": \"side\"}")` → `lightLikeBlock()` = true → **`usesBlockLight()` = true**。

于是 `GuiGraphics.renderItem` 里 `boolean flag = !bakedmodel.usesBlockLight()` 为 **false** → 既不预设也不恢复
光照；而 `AssembledWeaponRenderer.renderByItem` 在 GUI 上下文**自己调了** `Lighting.setupForFlatItems()`
→ **flat 光照泄漏且永不恢复**。下一个方块类物品 `flag` 同样为 false、也不自己设光照 → 被污染。
普通平面物品 `flag` 为 true、自己会设扁平光照 → 不受影响。**这就是"只影响方块类物品"的原因。**

**修复：** 两个模型各加 `"gui_light": "front"`，让 `usesBlockLight()` 变 false，光照交回 `GuiGraphics` 管
（它会 setup + 恢复）；同时删掉 `AssembledWeaponRenderer` 里那句泄漏的调用。两处必须同进同退——
只删 Java 不加 JSON 会让武器变成 3D 光照、左右渐变。

---

## BUG-012 — （原记：改包客户端可隔墙命中）**决定不修**

原以为服务端单体路径缺视线校验是可利用的漏洞。但正常客户端的 `mc.hitResult` 是**带方块遮挡的射线**，
命中实体即天然满足视线；所以这一关只对**改包客户端**有意义。本项目当前不以防作弊为目标，
而加上它反而有让正常战斗出现"贴墙/露头打不到"的手感回归风险。

**保留原状。** 若将来要认真对待多人作弊，`CwcCombat.resolveValidTarget` 就是该补闸门的地方（已在该方法注释中标注）。

---

## BUG-013 — （原记：服务端冷却门槛过松，攻速快两三成）**原估算是错的，不改**

**更正：** 原估算拿"服务端门槛（以**服务端** tick 计）"去比"0.9×delay（以**客户端** tick 计）"，
**量纲不一致**，把固有相位差当成了额外余量。

正确核算（`getAttackStrengthScale(a) = (ticker + a)/D`，`D = 20/攻速`）：

- 服务端门槛 `scale(3) ≥ 0.9` ⟺ 服务端 `ticker_s ≥ 0.9D − 3`
- 正常客户端手动出手 ⟺ 客户端 `ticker_c ≥ 0.9D`；客户端 ticker **领先约 2 tick**
  → 换算到服务端即 `ticker_s ≈ 0.9D − 2`

**两者只差 1 tick** —— 改包客户端至多比正常玩家快约 1 tick（≈9%），不是"两三成"。

**这 1 tick 无法消除：** 服务端为了不误拒正常玩家，必须给出不小于相位差的余量，而这份余量对作弊者同等可用。
把门槛收到 `0.9D − 2` 会正好坐在边界上，浮点末位或网络抖动一变就复发 BUG-007。
唯一的根治是让服务端独占节奏 + 回包告知客户端，代价是牺牲跟手性——为 1 tick 不值得。

**保留原状。** 已在 `CwcCombat.isServerCooldownReady` 的注释里完整记录，防止以后有人再来"优化"一遍。

---

## BUG-014 — （原记：格挡中/旁观者仍可出手）**决定不修**

同 BUG-012：原版输入层已经丢弃那些状态下的左键，这一关同样只对改包客户端有意义。保留原状。

---

## BUG-015 — 无视无敌帧连带抹掉目标刚受的**任何来源**保护

**状态：** 已修复（收窄）

**根因：** `bypassInvulnerability` 命中前**无条件**清零目标的 `invulnerableTime`（public 字段）。
设计意图是让本模组武器自己的快速连击不被原版的 10 tick 受击保护吞掉（双持快节奏依赖它），
但无条件清零意味着目标刚从**摔落、火焰、其他怪**那里得到的保护也被一并抹掉——那不是这个特性的本意。

**修复：** 改为按 `(攻击者 → 上次命中的目标 + tick)` 记录，**只在同一攻击者连续命中同一目标**
（20 tick 窗口内）时清零。第一次命中永远不清零——这不是缺陷：目标若没被近期打过，
`invulnerableTime` 本来就是 0，`hurt` 的 i-frame 检查（`> 10` 才只结算超出部分）不生效，照常全额结算。

---

## BUG-016 — 横扫主目标可打友军、副目标不可；且与客户端指示器不一致

**状态：** 已修复

**根因：** `applySweep` 的副目标循环有 `player.isAlliedTo(e)` 过滤，主目标复核**没有**；
而客户端的 `canHitTarget`（指示器 + 点选）对横扫**有**友军过滤。于是准星正对的友军会被打中，
而指示器判它"打不到"，两边规则不一致。

**修复：** 主目标复核与副目标走同一套规则（几何 + 视线 + 非友军）；客户端点选改走 `canHitTarget`，
与服务端结算同源。

> **后续调整（2026-09-19，见 BUG-029）：** 「非友军」这一条后来按目标类型**故意拆开了**——
> 副目标拦"我自己的宠物/坐骑"，主目标不拦。几何与视线两项仍然共用同一套。
>
> **再调整（2026-09-19，队友部分，作者要求）：** **主目标（准星正对）**对队友不再无条件拦，
> 改成交回队伍设置——**友伤开着就能打中队友，关掉就打不到**（`canHarmAlly`）。理由是与单体路径一致
> （单体本来就放行、由原版 `Player.hurt` 的 `canHarmPlayer` 裁定）。
> **副目标（横扫范围）仍无条件拦队友**（不分友伤开关）：作者要的是"友伤开着时，队友**只有准星直接对准**
> 才打得到"——顺手扫到不算。宠物那半不受影响，仍是 BUG-029 的规则。
> **2026-09-19 客户端实测通过**（三条用例见 `tmp/TEST-CHECKLIST.md` 的 B-1 友伤边界）。

---

## BUG-017 — 创造栏给出的 `PART`/`HANDLE_PART` 是空白物品

**状态：** 已修复

**根因：** `CwcCreativeTabs` 直接 `output.accept(CwcItems.PART.get())` / `accept(HANDLE_PART.get())`，
那是两件**连 `PART_IDENTITY` 都没有**的空物品：渲染不出任何东西（`renderByItem` 拿不到 id 直接 return），
也不能当装配底座用（底座槽要求带身份）。而这又是 ARCH-1（属性只在经过装配台后产生）的连带后果——
即便有身份，属性也是 0。

**修复：** 改为遍历 `PartRegistry.getAllParts()` 逐个给出带真实身份与贴图的零件，
承载物品（`HANDLE_PART`/`PART`）由 `PartStacks.itemFor` 按类型 `role()` 推导。

---

## BUG-018 — 非装配台路径产出的带槽武器属性全 0，且不报错

**状态：** 已修复（架构层根治，见 ARCH-1）

**根因：** 属性物化的唯一写点只有 `AssemblingMenu` 的两处。于是物品的属性成了
"**有没有经过装配台**"的函数，而不是"它里面装了什么"的函数——`/give`、其他模组、创造栏、
旧存档产出的带槽武器全是 0，且**不报错、不掉日志**。

**修复：** 属性不再物化，改为在 `CwcWeapon.getDefaultAttributeModifiers(ItemStack)` 里从装配树**现算**
（详见 ARCH-1）。

---

## BUG-019 / BUG-020 — （零件选择态脱节 / 滚动无变化也发包）

**状态：** 已修复并验证（2026-09-19 客户端实测：拖窗口/全屏切换后选择态保持、滚轮不再无意义发包）（客户端交互层，2026-09-17）

**根因（BUG-019）：** 缩放窗口 / 切全屏会重跑 `CraftingScreen.init()`，里面每次都调
`partGrid.setItems(...)`。而 `IconGrid.setItems` 是"换一份列表"的语义——顺带把 `selectedIndex`
清成 -1，**且不触发** `onSelectionChanged`；材料列恰恰只在那回调里被填充。于是重建后右侧高亮消失、
左侧留着旧内容，而服务端 `currentPartId` 并没有变、输出槽里的成品还在。点一下材料才对得上。

**修复（BUG-019）：** `CraftingScreen` 自己记住 `selectedTypeId` / `selectedPartId`，`init()` 末尾
调 `restoreSelection()` 按 id 把两侧高亮重新定位。为此给 `IconGrid` 加了
`selectWithoutNotify(Predicate)`：**只改本地选中索引、不发包**——服务端本来就是对的，重建时重发包
反而会把 `currentRecipeIndex` 打回起点（这正是没采用"让 setItems 触发回调"那条路的原因）。

**根因（BUG-020）：** `SlotList.requestScroll` 只把值夹到范围、不与当前值比较，滚轮在列表主体内又
无条件返回 true → 每格滚轮发一次 `SetScrollPacket`，服务端跟着跑 `refreshDisplay()` +
`broadcastChanges()`。当前数据下手柄 2 槽、刃 1 槽、可见 3 行，`maxScrollRows()` 恒为 0，全是白发的包。

**修复（BUG-020）：** 客户端 `requestScroll` 加"`clamped == scrollRows` 即返回"；服务端
`AssemblingMenu.setScrollRows` 那一半（值未变即返回）已由数据层会话先修。顺带：拖滑块在同一行内
来回移动也不再重复发包。

**故意没改的：** `SlotList.mouseScrolled` 在列表主体内仍返回 true（滚不动也吞掉滚轮事件）。改成
false 会让滚轮在面板上直接切快捷栏——那是行为变化，不是本次要修的 bug。

---

## BUG-021 — 左键模式锁用的是**上一 tick** 的准星结果

**状态：** 已修复（**待游戏内验证**——清单 C-3）

**根因：** 落锁挂在 `ClientTickEvent.Pre`，而 `Pre` 早于 tick 开头的 `gameRenderer.pick()`
→ 读到的是**上一 tick** 刷新出来的 `hitResult`。快速甩视角同时按下会锁错模式。

**修复：** 落锁挪进 `onAttackKey`（该回调在 `handleKeybinds` 内、**晚于** `pick()`），
拿到按下那一刻刚刷新的准星。解锁仍留在 `onClientTickPre`（松开左键不产生任何事件，需要每 tick 都跑的钩子；
解锁与 tick 内相位无关，无精度问题）。

**一个有用的细节：** `Minecraft.continueAttack` **只在准星指着非空气方块时**才每 tick post 该事件，
所以按住打空气时事件只在点击那一下来——而落锁本来就只需要"按下那一刻"判一次，正合适。

---

## BUG-022 — 走远或拆掉方块，界面不关，服务端继续持有底座物品

**状态：** 已修复（**待游戏内验证**——清单 C-5）

**根因：** 两个菜单的 `stillValid` 恒返回 `true`。而原版工作台用的是
`stillValid(access, player, Blocks.CRAFTING_TABLE)`，走远即关。

对本模组尤其要紧：**装配台的底座武器放在菜单自己的容器里**，只有 `removed(Player)` 才归还——
界面永不自动关闭意味着走远之后它一直挂在服务端，异常退出就随菜单一起丢。

**修复：** 坐标经 NeoForge 的 `openMenu(provider, pos)` 传进菜单（菜单构造函数签名相应改为
`(int, Inventory, BlockPos)`），`stillValid` 复用原版的静态判定 `stillValid(ContainerLevelAccess, player, block)`
——它内部用 `player.canInteractWithBlock(pos, 4.0)`（按交互距离属性算，不是写死的 64）。

---

## BUG-023 — 制造台用 `typeId().contains("handle")` 决定产出物品

**状态：** 已修复

**根因：** 这条字符串判据与 `WeaponStats`／`CwcWeapon` 用的 `getItem() == HANDLE_PART` **不是同一套**。
今天恰好一致（只有两个类型含 "handle"），但只要加一个 `cwc.handle_guard` 之类，制造台就会产出
"HANDLE_PART 物品 + 非手柄 PART_IDENTITY"的怪东西——被当底座写属性，行为却由 PART_IDENTITY 决定。
改 BUG-010 时它成了必改项（新格式要按 id 反推承载物品）。

**修复：** 统一到 `PartStacks.itemFor(partId)`，判据改为查类型的 `role()`（`data` 为空即 `handle_part`）。

---

## BUG-024 — 装配台零件变更检测恒为真

**状态：** 已修复（随 BUG-010 的值类型变更自动修好）

**根因：** `ItemStack.matches(prev, copy)` 比的是两个 `copy()` 出来的不同实例，
而 `ItemStack` 没有值语义 `equals` → 恒判为变 → 每次容器变化都全量 `WeaponStats.apply` + `broadcastChanges`
（幂等，不会刷数据，但白算白发）。
**修复：** 改用 `PartNode.equals`。

---

## BUG-025 — 子装配体若被改名，嵌套后名字丢失（**无可观察影响**，记录备查）

**状态：** 已知并接受

改 id-only 存储后，子装配体的 `CUSTOM_NAME` 不再随树保存。这个名字**在游戏里从不显示**
（`AssembledWeaponRenderer` 不画名字，装配武器的 tooltip 也不枚举子件），所以无可观察影响。
若要杜绝来源，可把 `AssemblingMenu.renameItem` 限制为底座是 `HANDLE_PART` 时才生效。

---

# 架构债登记（本轮的处理与未处理）

| 编号 | 问题 | 本轮 |
|---|---|---|
| ARCH-1 | **派生数据被物化到物品上，缓存无所有者**：真相是装配树，属性却写成组件；写点只有装配台两处。BUG-005（裸刃打人）、BUG-017、BUG-018 都是它的产物 | **已根治**：属性改为在 `CwcWeapon.getDefaultAttributeModifiers(ItemStack)` 里现算，不再写 `ATTRIBUTE_MODIFIERS` 组件。NeoForge 的 `ItemStack.getAttributeModifiers()` 在组件为空时正好回落到这个重载，所以无需自己写分发；tooltip 也走同一条路径，`+N 攻击伤害` 那几行不会丢（`ItemAttributeModifiers.EMPTY.showInTooltip()` 为 true）。**BUG-005 由此从结构上不可能再发生。** 物化量只剩耐久上限（`ItemStack.getMaxDamage()` 只读组件，`Item` 拦不住） |
| ARCH-2 | **装配树被遍历四遍，且顺序不一致**（`WeaponStats` 迭代 map，另三处迭代 `type.slots()`） | **已根治**：新增 `crafting/AssemblyTree`——一次 DFS、**槽位声明序**，属性/普攻方式/副手标记/渲染项全部由它派生。**注意：目前每种类型最多一个可装零件的槽，所以两种顺序看不出差别；但一旦出现第二个同类槽（如"副刃"），旧实现会让渲染画出的刃与贡献数值的刃不是同一把** |
| ARCH-3 | **“能不能打中”无单一权威**：几何、冷却、目标合法性在两端各实现一遍 | **部分解决**：主副手合并到 `CwcCombat.resolveValidTarget`；客户端点选改走 `canHitTarget`（与服务端结算同源）。冷却仍有两档（客户端手动 0.9 / 自动 1.0，服务端含容差），那是 BUG-013 讨论的固有代价 |
| ARCH-4 | 三套数据生命周期并存（DeferredRegister / `PartRegistry` 静态单例 / `Layouts` 客户端缓存，服务端拿硬编码默认值） | **未处理**——取决于仍未决的"是否支持多人"。专用服务器上客户端 `PartRegistry` 为空，制造台列表空、装配台 0 行、武器贴图只画出底座 |
| ARCH-5 | `CwcClientEvents` 是 438 行上帝类，握 8 个无重置入口的静态可变字段 | **部分处理**。风险 > 收益：剩下的是好几个刚调好的手感和经验常数。已削掉的职责：`mainHandSettled` 的比较语义修好、模式锁的落锁时机理清（2026-09-17）；删掉"界面打开时隐藏 HUD 与手持物品"——原版本就不隐藏（`GameRenderer.java:1079` 无条件调 `gui.render`；手部渲染的守卫在 `:951-954`，只判第一人称/睡觉/`hideGui`/旁观，**两处都没有 screen 判断**），原版容器界面之所以看起来没有 HUD 是被自己的全屏模糊背板盖住的（2026-09-24，连带删掉只服务于这两个 handler 的 `screen/CwcScreen`）；**抽出准星与攻击指示器**到 `client/renderer/CrosshairIndicators`（595 → 460）、**主副手点选合并**为 `CwcCombat.pickAttackTargetId`（460 → 438）。**剩余**：三台状态机（模式锁 / 主手身份 / 副手挂起）各自成类 + 聚合式重置（整体替换 `Machines`，让"忘记加进 reset"在构造上不可能）——设计与 **12 条搬运陷阱**见 `tmp/SPLIT-DESIGN.md`。**2026-09-25 补充**：`SPLIT-DESIGN` 里原定的"两个出手驱动抽类（`OffhandAutoAttack` / `AttackDispatch`）"**已作废**——右键路径改由装配树驱动的行为架构接管（`BehaviorDispatch` + `SwingBehavior`，见 `docs/part-format.md` 的「行为与 HUD」），副手那条的冷却/目标点选/发包都搬进了行为与其分派层，不再是本类的职责。本类现 465 行，`CrosshairIndicators` 由 460 缩到 143（绘制拆给 `hud/` 与 `CrosshairBar`）|
| ARCH-6 | 客户端**模拟原版/服务端状态机**并用魔数兜底（`WEAPON_SETTLE_TICKS`、`SERVER_COOLDOWN_TOLERANCE_TICKS`；`offhandAutoSuspended` 复刻 `startUseItem` 内部状态机） | **部分**：已给 `offhandAutoSuspended` 加显著注释标明这是对原版实现细节的依赖。两个魔数保留（见 BUG-013） |
| ARCH-7 | 战斗数值全硬编码（`Config` 是空 builder），与"数值交给数据"的 JSON 哲学自相矛盾 | **未处理**——按"当前主要目标是手感打磨与 bug 修复"的判断推迟：它既不是手感也不是 bug |

## 2026-09-17 技术债收尾（作者指定"必要的"四条）

| 项 | 问题 | 处理 |
|---|---|---|
| 耐久仍物化、唯一写点是装配台 | 属性改成按需推导后，**耐久是唯一推不出来的量**（`ItemStack.getMaxDamage()` 只读组件、`Item` 拦不住），于是非装配台路径产出的武器**完全没有耐久**（不可损坏、无耐久条、`hurtAndBreak` 空转）——**BUG-018 的残留面** | **已修**：`WeaponStats.syncDurability` 做成"值相等就不碰组件"的幂等同步，由 `CwcWeapon.inventoryTick` 每 tick 巡检自愈（无 `ASSEMBLED_SLOTS` 时早退，省一次树遍历）。拆零件导致上限低于已累积磨损时把 `DAMAGE` 夹到上限——不夹的话 `getBarWidth()` 会算出负宽度 |
| 客户端静态状态无重置入口 | `CwcClientEvents` 的模式锁 / 主手武器身份 / 副手挂起旗子全是跨 tick 记忆，不清就残留到下一个世界。此前"恰好无害"（下次比较自然会判定"变了"）是**巧合而非保证** | **已修**：`ClientPlayerNetworkEvent.LoggingOut` → `resetTransientState()`，并注明"新增静态可变字段时记得加进来"。哨兵值 `-1000` 提为常量 `NO_WEAPON_CHANGE` |
| `PartRegistry.scan` 丢命名空间 | id 不带命名空间前缀（既有设计，为让 id 与 lang key 简短），代价是不同命名空间的同名路径撞成同一 id，而**胜负取决于集合迭代顺序、静默不确定**。另外外层按命名空间循环是多余的（`listResources` 本身已返回全部命名空间的结果），还把循环变量当成了资源自己的命名空间 | **已修**：去掉多余循环，id 改在 `scan` 内算好；冲突改为**记 ERROR 并保留先出现的一份**——显式且确定 |
| `Layouts` 服务端回落硬编码默认值 | 把"双端可能不一致"制度化了 | **已加固（文档 + 诊断，非结构改动）**：类文档写明调用方不变式（布局值只能影响客户端渲染/交互，不能进服务端逻辑），服务端回落分支加每路径一次的 DEBUG 日志。**没有重构**——当前风险是"按构造即无害"（槽位坐标不上网络，两端各自构造菜单、只有客户端那份用于渲染），为它重构属过度设计 |

**仍未处理**：`libs/` 与 `build.gradle` 不一致（新克隆跑不了 `runClient`；`libs/` 里有 sodium/lithium 却未被 build 引用）、`CwcClientEvents` 上帝类拆分（**已做一半**，剩余项与设计见 ARCH-5 与 `tmp/SPLIT-DESIGN.md`）、包结构按名词分包、两种事件注册风格并存、JavaDoc 路径笔误、`Config` 配置化。**前四条里，除 `libs/` 外都判定收益 < 风险**，留作规模上来再说。

## 2026-09-17 零件定义迁移为 datapack registry（不是 bug，是结构性变更）

**状态：** 已完成并验证（2026-09-18：注册表加载 + 单人回归 + 专用服务器三步全部实测通过；
第四步"第三方扩展"**服务端侧已验**，客户端侧的显示效果仍待测）。详见顶部「验证记录」。

零件/类型定义从"自己扫 `data/**/types`、`data/**/parts`"改为**原版 datapack registry**
（`cwc/part_type` + `cwc/part`）。动机有两条，缺一这次迁移就不值得做：

1. **支持专用服务器**：注册表由原版在配置阶段同步到客户端，所以两端看到同一份数据。此前专用服务器上
   客户端那张表恒为空，表现为**制造台列表空、装配台 0 行、背包里武器贴图错**——而且毫无线索地默默坏掉。
2. **对第三方开放**：注册表是公开可查的（`registryOrThrow(CwcRegistries.PART)`），
   且 **id 的命名空间取自文件所在的命名空间**，扩展包放进 `data/他们的命名空间/cwc/part/**.json`
   就天然不撞车。

**契约（id 规则、目录、`type` 字段、lang key、贴图路径、第三方扩展方式）见 `docs/part-format.md`**——
发布后即冻结，改起来代价高。

顺带解决的旧问题：
- `type` 由"去掉 id 最后一个点号"推导改为**显式字段**——旧做法等于把目录结构变成语义，第三方无法自由布局。
- 解析不再碰 IO（codec 是纯函数），`parseType/parsePart` 签名里的 `ResourceManager` 依赖随之消失。
- 槽位约束匹配原先在装配台与注册表里**各写了一遍**，抽成 `PartTypeDef.SlotDef.accepts` 一处。
- 类型图环检测保留（只报不拦），改沿显式 `type` 关系建图。

**代价（一次性）**：46 份 JSON 挪位并重编码、102 条 lang key 改写、4 个 parser 改写为 codec、
`PartDef`/`PartTypeDef` 去掉 `id` 字段（id 是注册表键）、贴图路径推导重写。
**没有写存档迁移**（作者确认首个公开版前不需要），所以旧存档里的装配武器会因 id 换形而查不到定义、
退化成空装配——按已知限制处理。

**文档合并**：原来的 `docs/part-json-spec.md` 描述的是**迁移前**的目录与 id 形式，且有几处已经写错
（"解析时读取材料的 hardness/toughness 代入公式"——根本没这个数据，只有 `base` 生效；
"聚合写入 `ATTRIBUTE_MODIFIERS`"——属性已改为按需推导；"右键 CreativePartStar 打开制造界面"——该物品早已删除），
而它的文件名比新文档更像"规格"，最容易被找格式的人先点开。已把其中仍然有效的内容
（锚点对齐规则、`scale` 加权语义、`layer`、空数组与空约束的区别、装配链示例、渲染说明）
并入 `docs/part-format.md` 后**删除**该文件——**一种格式只留一份规格**。`docs/` 现在只有
`bugs.md`（bug 与决策记录）与 `part-format.md`（唯一格式规格）。

## BUG-026 — 装配台槽位详情的"类型"类别取值显示成原始语言键

**状态：** 已修复

**位置：** `assets/coldweaponcraftsmanship/lang/*.json`（零件定义迁移的副作用）

**现象：** 装配台里悬停槽位详情图标，约束列表里"类型"那一行的取值显示成 `type.cwc.attack` 这样的原始键，
而不是"攻击"。（用户 2026-09-18 客户端实测发现。）

**根因：** 迁移时改写了语言键（`part.cwc.*` → `part.coldweaponcraftsmanship.*`，`type.cwc.*` 同理），
但**槽位约束的"取值标签"恰好共用 `type.cwc.` 这个前缀**——`SlotList.infoTooltip` 查的是
`<约束键>.cwc.<值>`（如 `type.cwc.attack`），于是它们被一起改成了 `type.coldweaponcraftsmanship.attack`，
而查询侧没变 → 查不到 → 显示原始键。

`mount.cwc.*`、`material.cwc.*`、`grid.cwc.*` 等家族没受影响，因为改写规则只匹配 `part.`/`type.` 两个前缀。

**修复：** 把 `type.coldweaponcraftsmanship.{attack,guard,ornament,pommel}` 改回 `type.cwc.*`（4 键 × 2 语言）。
真实的 8 个**类型显示名**（`type.coldweaponcraftsmanship.standard_blade` 等）保持新格式——
判据是"后缀是不是一个真实存在的类型 id"，用数据目录里的文件名核对。

**为什么当时没发现：** 改写时只核对了**改写条数**（50 条），没核对**改的是哪些键**。
正确做法是比对前后的**键集**。事后补做：新增 46 / 移除 46，正好等于 38 零件 + 8 类型，
每个新增键都对应真实 id、每个移除键都有对应新键。

**顺带补的一项检查：** 把数据里实际用到的全部约束键与取值（13 个 lang key）逐个查两个语言文件，确认无缺失。
**这类"数据 ↔ 界面文案"的对应关系不在 codec 的管辖范围内**——离线 codec 检查器（见 `tmp/codeccheck/`）
查不出来，只能这样单独扫。

**另一条同批发现（不是 bug）：** "创造模式物品栏拿出的零件装配出的武器不掉耐久"——
`ItemStack:467` 的 `hasInfiniteMaterials()` 门使**创造模式下所有物品都不掉耐久**（原版工具同理）。
要在生存模式验。

## BUG-027 — 横扫只有副目标吃到伤害时，一个耐久都不扣

**状态：** 已修复并验证（2026-09-18 客户端实测）

**位置：** `CwcCombat.applySweep`

**现象：** 拿横扫型武器（`standard_blade` / `half_sword` / `long_blade`）**准星不指生物**（打地面、打空气），
贴身球与周围锥体里的一圈敌人照样吃到全额伤害，但武器**耐久一点不掉**。
准星正好指着某个生物时才掉。（用户 2026-09-18 客户端实测发现。）

**根因：** 耐久扣费写死在 `applyDamage` 的 `if (primary)` 分支里，而横扫的**副目标**走
`applyDamage(..., primary=false)` —— 也就是说"打到东西"和"扣耐久"被绑在了两个不同的条件上：

| | 扣耐久 |
|---|---|
| 主目标命中（`primary=true`） | 1 ✓ |
| 副目标命中（`primary=false`） | **0** ✗ |
| 全空 | 0 ✓（设计如此） |

而主目标要过服务端复核（几何 + 视线 + 非友军），**准星不指生物时 `mainTarget` 就是 null，复核整段跳过**
→ 只有副目标吃伤害的那次挥击，一次耐久都不扣。等于**扫场永远免费**。

**修复：** 耐久的口径改成"**这一挥有没有打到东西**"，而不是"主目标有没有打到"：
记录副目标是否命中过、主目标是否命中（主目标命中时其 `primary` 分支内部已扣过 1），
**扫到了但没走 primary 时补扣 1 点**。

**量的口径：一次挥击最多扣 1，扫到几个都只扣 1**（与原版横扫量级一致），全空仍然不扣。

**为什么之前没发现：** 只验证了"单体命中扣耐久"这一条路径，没有覆盖"横扫命中副目标"这条——
而它恰好是本模组**特有**的设计（原版横扫只在主目标命中后才触发范围伤害，
所以原版不存在"范围伤害没扣耐久"这个状态）。

## BUG-028 — 打掉杂草（秒破方块）后立刻攻击会空挥

**状态：** 已修复并验证（2026-09-18 客户端实测）

**位置：** `CwcCombat.isServerCooldownReady`（服务端主手冷却判定）

**现象：** 拿本模组武器**打掉一株杂草**（或任何秒破方块）后**立刻**攻击 → 客户端出了刀、进了冷却、
却没有任何伤害，得等满一个冷却才能再打。
（用户 2026-09-18 实测发现，猜测"破坏杂草重置了冷却"——**猜对了，被重置的是服务端那根。**）

**根因：** 这是**原版已确认的 bug 被本模组放大**，不是本模组引入的。

原版有两处各自独立的攻速条归零，**不对称**：

| | 服务端 `attackStrengthTicker` | 客户端 `attackStrengthTicker` |
|---|---|---|
| 挖方块时 | **每 tick 被归零** | **不动** |

- **服务端**：`Minecraft.startAttack` / `continueAttack` 每次挥手都调 `player.swing(MAIN_HAND)`；
  在 `LocalPlayer` 上这一句等于"放动画 **+ 发 `ServerboundSwingPacket`**"，而服务端 `handleAnimate` →
  `ServerPlayer.swing(hand)`（**单参重载，末尾多一句 `resetAttackStrengthTicker()`**——
  双参重载没有，这正是原版区分"要归零/不要归零的挥手"的方式）。挖掘期间每 tick 发一次，
  于是服务端那根条一直是 0。
- **客户端**：唯一的对应归零在 `MultiPlayerGameMode.stopDestroyBlock()`，而它整段包在 `if (this.isDestroying)` 里。
  而 `isDestroying` 只在"需要挖一会儿"的分支被置位——**秒破方块走的是另一个分支**，
  进去就直接 `destroyBlock()` 返回，`isDestroying` 全程 false。所以客户端那半句归零根本不执行。

结果（打完杂草的那一刻）：

| | 攻速条 | 后果 |
|---|---|---|
| 客户端 | **满**（没被动过） | 本地门槛放行 → 发包含 → 本地挥刀 → 归零自己的条 |
| 服务端 | **0**（被 swing 包归零） | `getAttackStrengthScale(3f) ≈ 3/19 ≈ 0.16 < 0.9` → **拒收** |

于是整刀被静默丢弃，而客户端已经消耗掉了自己的冷却——白挥一刀 + 白等一个冷却。

**为什么原版没这么严重：** 原版拿这根条当**系数**（`Player.attack` 里 `f *= 0.2 + 0.8 × f2²`），
失同步只表现为"软一刀"（约两成伤害、不暴击、不横扫）；本模组拿它当**开关**（放行/拒收），
同一个失同步就被放大成"整刀消失"。

**顺带一个原版自己的矛盾：** HUD 的攻击指示条（`Gui.java`）读的是**客户端**那根条，
所以挖方块时原版 HUD 显示"已充满"，而服务端算的是两成伤害——原版玩家看到的是"莫名其妙软了一刀"。

**官方工单（全部 Confirmed，没有一条被当作特性关掉）：**

| 工单 | 内容 | 状态 |
|---|---|---|
| MC-255058 | 「AttackStrengthTicker desynchronization when swinging」——机制本身 | Open，优先级 Normal |
| MC-116510 | 「Attack indicator doesn't indicate ... breaking instantly-mineable blocks resets your attack cooldown」——**本 bug 的同一场景**，列全了所有秒破方块 | Open，优先级 Low，被归为 **UI** |
| MC-118740 | 右键 / 使用物品触发同一现象 | Reopened，优先级 Low |
| MC-310957 | 举盾后无法横扫（同族），**已在 26.3 Pre-Release 1 修复** | Resolved · Fixed |

MC-255058 里社区给的修复建议是：**让客户端打空时单独发一个 `ServerboundMissPacket`，只由它归零攻速条**
——即把"冷却语义"从"挥手包"里拆出去。官方的实际倾向也印证了这点：同族里唯一被修的是
**表现为"战斗结果明显不对"**的 MC-310957（优先级 Important），而被归为 UI 的 MC-116510 挂了九年。

**修复：** 本模组照同一方向做，但更简单——它**本来就有一个明确的信号**：`CwcMainHandAttackPacket`。
服务端主手冷却不再读原版攻速条，改为**记录本模组自己上次出手的 tick**（`LAST_MAIN_ATTACK_TICK`，
WeakHashMap，与 `LAST_HIT` 同款），门槛 = `ceil(0.9 × D) − SERVER_COOLDOWN_SLACK_TICKS`。
于是**任何**与攻击无关的挥手（挖方块、右键、丢物品、举盾释放）都不再影响冷却——整个工单家族一次性覆盖，
不必跟原版打地鼠。

**顺带：** BUG-007 当初也是同一个根（原版在装备变更时重置那根条），当时靠 +3 tick 容差绕过去；
服务端自持时钟后，那道容差不必再为它留余量。

**副作用与已知残留：**

- 服务端门槛的作弊窗口由 1 tick 变成 `SERVER_COOLDOWN_SLACK_TICKS`（3 tick，D=12.5 时约快 25%）。
  按作者指示，作弊者不在当前考虑范围。
- **客户端仍读原版攻速条**（保守），所以"挖一会儿**慢速**方块 → 扫开准星 → 立刻攻击"时，
  客户端那根条被 `stopDestroyBlock` 清掉，会**点了不出刀**。此现象**改前改后相同**
  （改前客户端同样会拦下），且此时 HUD 也显示未蓄满，玩家看到的是自洽的——
  属 MC-116510 那类"显示问题"，留给原版。

## BUG-029 — 横扫会顺手扫死自己的宠物

**状态：** 已修复并验证（2026-09-19 客户端实测）

**位置：** `CwcCombat.isSweepFriendly`（新增）、`applySweep` 副目标循环、`hasSweepTarget`（客户端指示器）

**现象：** 拿着武器打架时，站在旁边的**自己的**狗/猫会被横扫卷进去掉血——不需要准星指着它，
甚至不需要打着怪（本模组空挥也算横扫）。用户 2026-09-19 发现并提问。

**根因：** 不是本模组写错了过滤，而是**照抄了原版那行过滤，而原版的过滤在这里本来就不生效**。

原版横扫循环里的友军判断是 `player.isAlliedTo(宠物)`，`this` 是**玩家**。而 `Player`（以及
`LivingEntity`）从不重写 `isAlliedTo`，所以这行退化成纯粹的**计分板队伍**判断。真正写着
"驯服动物认主人为盟友"的 `TamableAnimal.isAlliedTo`（`owner == 对方` 就返回 true）**只有从宠物那一侧
问才会命中，横扫这一侧从来不问**。结论：**原版拿剑扫自己的狗，狗也会掉血**——同样是全队同队才拦。

**但原版误伤是极罕见的，本模组是常态**，两个结构性差异：

| | 判定盒挂在谁身上 | 何时触发 |
|---|---|---|
| 原版 | **被打中的那个目标**：`target.inflate(1.0, 0.25, 1.0)` | 满蓄力 + 真命中 + 不冲刺 + 在地面 |
| 本模组 | **玩家自己**：±75° 锥体 + 半径 reach/2 的贴身球 + 准星射线 | **每次挥**（空挥也结算） |

原版宠物得贴着"你正在砍的那只怪"才吃得到；本模组宠物站在你身边就中，面积大一个数量级、
触发从"命中时"变成"每次挥"。**照抄原版那行代码抄不到原版的体感**——这是判定要改写而不是"照原版"的理由。

**修复：** 副目标过滤补上"宠物那一侧"的判断：

- **拦**：`e instanceof OwnableEntity && getOwner() == 我`（"我的"宠物与坐骑）；
- **拦**：**队友**——横扫的**副目标**一律不卷（不分友伤开关；队友只能被准星正面打中，
  那条走主目标、由 `canHarmAlly` 按队伍的 `friendlyFire` 裁定，见 BUG-016 的再调整说明）；
- **不拦**：别人（**含队友**）的**宠物**——与原版一致（原版打宠物从不看队伍）。但**队友的坐骑**是例外，
  见下一条；
- **拦**：**队友的坐骑**（2026-09-27 补，作者要求）——队友可以把马停在战场边上，顺手扫死它比误伤队友
  更难受。判据是"主人的队伍和我同队"，两端同源，见下方"后续"；
- **主目标路径一个字不动**：准星正对自己的宠物照打中——原版行为"宠物主人可以直接攻击宠物"，
  而且这是玩家明确瞄准的东西，不属于"顺手卷进来"。所以 `isSweepFriendly` **不得**用在主目标判定上
  （这是 BUG-016 那次"主副目标统一"的**有意例外**）。

覆盖范围：`OwnableEntity` = `TamableAnimal`（狼/猫/鹦鹉等）+ `AbstractHorse`（马/驴/骡/羊驼/骆驼等）。
用 `OwnableEntity` 而不是 `TamableAnimal` 是因为**马不是 TamableAnimal**（`AbstractHorse` 只 implements
`OwnableEntity`），只判 `TamableAnimal` 的话自己的马会被扫。

**后续（2026-09-27）：** 这一节原先记着一条"已知残留"——马的 owner UUID 只写在 NBT 里、不同步到
客户端，所以客户端的 `hasSweepTarget` 认不出"这是我的马"，会多算一个候选（指示器亮而服务端不结算）。
**已修**：owner 现在由附件 `cwc:mount_ownership` 同步到客户端（服务端每 tick 对账、客户端读同步副本，
见 `CwcOwnership` / `CwcAttachments`），客户端认得出"这是我的马"。
同一次改动把**主人的队伍名**一起装进了附件——判"队友的坐骑"不能靠"主人的玩家实体"（客户端只有附近
那些玩家实体，队友骑马跑远、马留在你身边时就解析不出来，那又会变成"指示器亮而不结算"），
比队伍名这个字符串则两端永远一致。主人**离线**也照样判得出（服务端从用户名缓存取名字再查计分板，
队伍成员表是存档数据，与在线无关）。

---

## BUG-030 — 划船时按着移动键，CWC 武器仍然出刀

**状态：** 已修复并验证（2026-09-19 客户端实测）

**位置：** `CwcClientEvents.handsBusy`（新增）、`tryMainHandAttack`、`tryOffhandAttack`

**现象：** 坐在船的驾驶位、按着任一移动键（前后左右）时，CWC 武器照样出刀；原版此时禁止攻击与交互。
用户 2026-09-19 实测发现。

**根因：** 原版有一道**只在船里生效**的"双手忙"闸：

- `LocalPlayer.rideTick` 里，若 `getControlledVehicle() instanceof Boat`，就把移动键状态喂给船，
  并置 `handsBusy = (left || right || up || down)`（**只有船**，矿车不走这条路）；
- `Minecraft.startAttack` 与 `startUseItem` 都据此**直接返回**——不再发射
  `InteractionKeyMappingTriggered` 事件，`ItemInHandRenderer` 还会因此不渲染手中物品。

本模组有两条出手路径，恰好只被挡住了半条：

| 路径 | 来源 | 原版的闸 |
|---|---|---|
| 点击 | `onAttackKey`（监听 `InteractionKeyMappingTriggered`） | **有效**——事件由 `startAttack` 内部发出，早被 handsBusy 挡住 |
| 按住 | `onClientTick` 每 tick 驱动 | **无效**——它绕过了 `handleKeybinds` 和 `startAttack` |

于是表现为"划船时点左键没反应、**按住却照打**"。

**修复：** 在 `tryMainHandAttack` 与 `tryOffhandAttack` 的入口补 `handsBusy(player)` 闸门
（`player instanceof LocalPlayer local && local.isHandsBusy()`）。放在这两个共用入口上，
点击与按住两条路径一次性覆盖。

**刻意不拦挖掘：** 原版 `continueAttack` 本来就没有这道闸——被拒的只有 `startAttack` 里那次
"开始挖掘"，持续挖掘在划船时是允许的。所以挖掘路径保持原样。

---

## BUG-031 — 骑乘时横扫会打到自己骑的生物（骑猪攻击，猪跟着掉血）

**状态：** 已修复并验证（2026-09-19 客户端实测）

**位置：** `CwcCombat.isRideChainDown` / `isSweepCandidate`（新增）+ 5 处调用

**现象：** 骑着猪攻击时，**猪自己也掉血**；骑在五层猪塔顶上时，整座塔都能被自己打到。
用户 2026-09-19 实测发现。

**根因：** 两个原因叠加。

1. **几何上必然命中。** 骑乘链上的实体与玩家同处一个碰撞位置，永远落在横扫的**贴身球**
   （球心 = 玩家碰撞箱中心、半径 = `reach / 2`）里。于是这不是"偶尔扫到"，而是**每次挥击都卷进来**。
2. **BUG-029 的过滤覆盖不到猪。** 那次用的是 `OwnableEntity`——而**猪不是 `OwnableEntity`**
   （只有 `TamableAnimal` 与 `AbstractHorse` 是）。所以"我的坐骑"这一类里，猪从缝里漏了出去。

**原版对照：** 原版横扫同样**不排除**自己的坐骑或乘客——它只查 `player.isAlliedTo`（同计分板队伍）。
原版暴露得少，还是那两条老原因：判定盒挂在被命中的目标身上、且要求满蓄力真命中。
本模组面积大一个数量级、空挥也结算，于是"骑着猪打架，猪先死"变成常态。
（判断依据与 BUG-029 相同：`Player` 从不重写 `isAlliedTo`，退化成纯队伍判断。）

**规则（作者定，2026-09-19；当日来回调整过三次，以下是终版）：**

| 目标 | 主目标（准星正对） | 横扫副目标 |
|---|---|---|
| **向下的骑乘链**（我骑的载具、载具的载具…直到链首） | **不可打** | **不卷进来** |
| **向上的骑乘链**（骑在我身上的，任意深度） | **可打**（需本模组补的点选，见下） | **不卷进来** |
| **同乘的乘客**（船/骆驼上并排的另一个玩家） | **不可打**（保持原版，见下） | **不卷进来** |
| **船与矿车** | 同"向下的链"（骑着的打不到，下船能打） | **算候选**（能吃范围伤害，见下） |

两条判定各管一半：

- **横扫**用 `isInPlayerVehicleGroup`（根载具相等）——**整条骑乘组都不自动卷进来**。
  骑在五层猪塔顶上时"向下的链"就是整座塔（顶猪 → 第 4 层 → … → 底猪），所以**整塔都打不到**；
  骑猪时猪不掉血；**头上骑的人也不会被每次挥击扫到**。
- **主目标**用 `isRideChainDown`（逐级走 `getVehicle()`）——只挡向下的链。
  向上链**不在**这个过滤里，**准星明确对准就能打**（靠下面那条补的点选）。
  **同乘的乘客也不在这个过滤里，但打不到**——原因在拾取那一层，见下。

**同乘的乘客为什么打不到（原版如此，不是漏改）：** 官方工单
**MC-236517「You cannot attack players riding the same boat or camel as you」**——原版里坐在同一条船/同一只骆驼上的两个玩家**互相打不到**，
而且 Mojang 在 2025-07-03 把它关成了 **Works As Intended**（影响版本含 1.21 / 1.21.4）。
所以这里保持原版：本模组的补点选**只收"骑在我身上的"**（`hasIndirectPassenger`），
同乘的兄弟节点不收——想打同船的人，先让其中一个下船。

**为什么"准星对准能打上面"需要额外写代码：** 原版的准星拾取把**整条**骑乘链都过滤掉了
（见下方"与原版的关系"），所以"骑在我头上的"永远成不了 `mc.hitResult`。不补的话，
「下面的人能打到上面的人」这条规则根本落不了地——而它正是**防"赖在别人头上"**的手段：
只能准星对准才打得到，就不会误伤，但挂机位也不再安全。
补法是在客户端加一次**只针对向上链**的射线点选（`CwcCombat.pickUpperRideChain`，主副手各接一处）；
服务端本来就放行（`isRideChainDown` 只管向下链），不需要改。

> **改版记录（同日两次反转，供回溯）：** 初版把"向上链"排除在横扫之外，理由是防"有人骑在别人
> 身上打架"；随后改成"横扫也打得到头上的人"，理由是防赖在头上；最终定为**横扫不碰、准星可打**——
> 横扫自动卷到会变成"每次挥击都在打头上的人"，而完全打不到又会让挂机位安全。两个极端都不对。

**船与矿车（同日第二阶段改定）：** 它们**两头都要**，规则是两个方向的组合：

- **受同一条骑乘链保护。** 骑在船上时打不到自己这条船，准星正对着也不行——**下船才能打**。
- **但横扫的范围伤害要包含它们。** 候选集原本只遍历 `LivingEntity`（载具不是活体，天然不在内），
  现在显式把 `Boat` 与 `AbstractMinecart` 加进去（`isSweepCandidate`）。站在船/矿车**旁边**挥击
  即可打到它，走 `VehicleEntity.hurt`（累计伤害 > 40 报销），与主目标路径同一条。
  扫到载具同样只扣 1 点耐久（沿用 BUG-027 的口径）。

**与原版的关系：** 这条规则**一半是原版本来的行为**，另一半是本模组新加的。

原版的**准星拾取**里就有一道骑乘保护，位置在 `ProjectileUtil.getEntityHitResult`
（`GameRenderer.pick` 调用的就是它，参数是 `Entity shooter` 的那个重载）：

```java
if (entity1.getRootVehicle() == shooter.getRootVehicle() && !entity1.canRiderInteract()) {
    // 命中但不更新"最近距离" → 选不中
}
```

三个要点：

- 判定用的是 **`getRootVehicle()` 相等**——**整条骑乘链**（向下的载具 + 同乘的 + 骑在我身上的）
  都选不中，比本模组的"向下链"**还宽**；
- `canRiderInteract()` 是 NeoForge 的扩展点（`IEntityExtension`，默认 false），
  **原版没有任何实体重写它**（全仓库只有那一处默认实现）——所以这道闸实际永远生效。
  这就是"坐在船里打不到自己的船"的**真正原因**（`VehicleEntity` 同样没重写）；
- **它只作用于准星拾取。** 原版的横扫循环（`Player.attack`）里**没有任何骑乘判断**，
  只有 `!= this`、`!= target`、`!isAlliedTo` 三条——所以原版横扫**会**打到自己骑的猪，
  这正是骑猪攻击时猪掉血的来源。

**因此本模组与原版的对照：**

| | 原版 | 本模组 |
|---|---|---|
| 向下链（骑的船/猪）· 准星 | 选不中 | 打不到 ✓ 相同（本模组也拦，双保险） |
| 向下链 · 横扫 | **没有保护，会打到** | 不卷进来（**新增**，骑猪不再掉血） |
| 向上链 / 同乘 · 横扫 | **会打到** | 不卷进来（**新增**，别每次挥击都打头上的人） |
| 向上链（骑在我身上）· 准星 | **选不中** | **打得到**（**故意偏离**：补了点选绕开原版那道闸） |
| 同乘乘客（船/骆驼）· 准星 | **选不中**（MC-236517，Works As Intended） | **也选不中** ✓ 相同 |

一句话：**横扫这一路比原版严（护住整个骑乘组），准星那一路对"向上链"比原版松（原版打不到的，本模组打得到）**，
而"向下链"两边一致。松的那一处是刻意的——见上面"为什么需要额外写代码"。

**防"赖在头上"的效果：** 甲骑在乙头上时——甲打不到乙（乙是甲的向下链，横扫与准星都不行），
乙**准星对准甲就能打到甲**，但横扫不会自动卷到。所以挂机位不安全，而正常战斗不会误伤头上的人。

---

## BUG-032 — 打假玩家没有击退（**不是本模组的 bug**，已用对照实验证伪）

**状态：** 不改（原版设计 + 假玩家没有客户端）

**现象：** 用 CWC 武器打 SiliconeDolls 的假玩家（`/player` 生成的 `ServerPlayer`）**完全没有击退**；
而用**怪物 / 爆炸**这类来源打同一个假人，它会飞。

**根因：** 原版对**玩家**目标的击退是"**客户端执行**"的——`Player.attack:1289` 与 `:1341-1345`：

```java
Vec3 vec3 = target.getDeltaMovement();     // ① 打之前记下速度
...target.hurt(...);                       // ② 打（击退速度被加上）
if (target instanceof ServerPlayer && target.hurtMarked) {
    connection.send(new ClientboundSetEntityMotionPacket(target));   // ③ 通知目标客户端
    target.setDeltaMovement(vec3);                                   // ④ 把服务端那份还原
}
```

- **真实玩家**：客户端收到包 → 自己位移 ✓ → 正常
- **怪物**：不走这条分支（`hurt` 里那道闸要求受害者是 `ServerPlayer`）→ 服务端那份留着 → 飞 ✓
- **假玩家**：是 `ServerPlayer`（所以走进 ③④、速度被还原）却**没有客户端**（所以没人执行 ③）→ **纹丝不动** ✓

本模组 `applyDamage` 的这段是**逐行照抄原版**的（连 `preMove` → `setDeltaMovement` 的还原都在），
所以症状只出在"没有客户端的玩家"这个组合上，**不是我们引入的**。

**对照实验（2026-09-19 实测，两次）：** 给假人设 `attack_knockback = 3`，让它对准并攻击真实玩家 →
**玩家有击退** ✓。真实玩家路径正常 ✓。

**顺带记录一个**真**漏项（独立于上面这条，见 BUG-033）：** `applyDamage` 的击退只读了
`ATTACK_KNOCKBACK` 属性，漏了原版的"冲刺满蓄力 +1"（`Player.attack:1292`）和击退附魔
（`LivingEntity.getKnockback:1504` 会过 `EnchantmentHelper.modifyKnockback`）。

---

## BUG-033 — 击退少了"冲刺 +1"和击退附魔

**状态：** 已修复（2026-09-20，待游戏内实测）

**位置：** `CwcCombat.applyDamage`（击退那一段）

**现象：** 站着打人/怪都没击退（**这一点与原版一致**：`ATTACK_KNOCKBACK` 玩家基础值就是 0）；
但**冲刺满蓄力攻击**在原本会把目标打退（`+1`，还带一声 `PLAYER_ATTACK_KNOCKBACK`），本模组不会；
**击退附魔**也不生效。

**原版的合成（`Player.attack:1292` + `LivingEntity.getKnockback:1504`）：**

```java
float f4 = this.getKnockback(target, damagesource) + (flag ? 1.0F : 0.0F);
//        └─ 属性 + 击退附魔（EnchantmentHelper.modifyKnockback）   └─ 冲刺 + 满蓄力（f2 > 0.9）
```

**修复（按作者 2026-09-20 的决定）：**

- **主手** = 玩家 `ATTACK_KNOCKBACK` 属性（已含主手武器自身修正）+ `EnchantmentHelper.modifyKnockback`
  （主手那把武器的击退附魔）+ 冲刺满蓄力 `+1.0`。冲刺那一档与原版一样**在 `hurt` 之前**播
  `PLAYER_ATTACK_KNOCKBACK`（被无敌帧吃掉的攻击原版也会响）。
- **副手** = 副手短刀自身修饰符（**不读玩家属性**，与 `resolveReach` 同思路）+ **副手那把刀自己**的击退附魔。
- **附魔取"造成这次伤害的那件物品"**（原版语义）：主手刀走主手、短刀走副手，两者不互相借用——
  否则会变成"主手附个击退、副手短刀白嫖"，既说不通也可刷。
- **冲刺 +1 主副手都给**（作者 2026-09-20 定）：主手照原版"冲刺 + 攻速条 >0.9"；
  副手没有原版语义可对齐，且它的"充能"本来就由短刀自己的冷却（`cwc:offhand_cooldown`）把关，
  所以**只判冲刺、不读主手那根攻速条**——否则刚砍完主手就出副手刀时拿不到这 +1，
  副手手感会被主手节奏牵连（副手整套设计的初衷就是两把刀各走各的冷却）。

**验证（待做）：** 冲刺满蓄力打怪 → 怪被打退且有击退音效；给武器附击退附魔 → 退得更远；
副手短刀附击退附魔 → 只吃它自己那一份；**冲刺时副手出刀** → 目标同样被 +1 打退。

---

## BUG-034 — 水下挥砍完全不出手（瞄准水/草丛也会）

**状态：** 已修复（2026-09-19，待实测）

**位置：** `CwcClientEvents.isMiningTarget`（左键按住"模式锁"的落锁依据）

**现象：** 手持 CWC 武器在水下挥砍：**不出手、不挥动、无音效、无伤害**——注意不只是"不横扫"，
是整次左键都没反应。当时已排除横扫本身的嫌疑（横扫路径里没有任何"在水里/是否着地"的判断，
视线检测走 `ClipContext.Fluid.NONE`，水不挡视线）。

**根因：模式锁把"瞄准水里"误判成了"瞄准方块"**

1. 原版准星方块拾取链：`GameRenderer.pick` → `Entity.pick(d, partial, false)` → `ClipContext.Fluid.NONE`，
   **射线穿过水**；端点是 `眼睛 + 视线 × 手长`。
2. 没打中任何方块时，`BlockGetter.clip` 返回
   `BlockHitResult.miss(端点, ..., BlockPos.containing(端点))`——**"空挥"也是一个 `BlockHitResult`**：
   类型是 `MISS`，但里面揣着**射线末端那个方块的坐标**。
3. 而 `isMiningTarget` 原本只判 `instanceof BlockHitResult` + `该方块不是空气`。水下挥砍时端点必然泡在水里，
   水的方块状态又不是空气 → 判定为"指着方块"。
4. 于是按下那一刻落锁成**"本次按住只挖掘"**：点击那条路（`onAttackKey`）直接 `return`——不取消事件，
   交给原版，而原版此刻是 MISS，只设 `missTime = 10`、不挥动；按住那条每 tick 驱动（`onClientTick`）
   被 `isMiningLocked` 挡掉。接下来 10 tick 内的点击还会被原版的 `missTime` 继续吞掉。

**同一个根的其他症状（理论预测，可一并验）：** 站在岸上对着水面挥、对着草丛/花/藤蔓这类
没有碰撞箱、射线会穿过去的非空气方块挥——端点落在它们里面，同样出不了手。

**修复：** 落锁依据补一条**"必须是真正的方块命中"**（`hitResult.getType() == HitResult.Type.BLOCK`），
与 `Minecraft.startAttack` / `continueAttack` 的原版口径一致；原来的"非空气"判断保留。
（`CwcCombat` 的视线检测那处本来就按 `getType() == MISS` 判，两处口径现在统一了。）

**验证（待做）：** 水下挥砍能正常出手（有横扫音效、周围怪掉血）；对照：站在岸上对着水面挥、
对着草丛挥也应能出手；原有的模式锁行为不回归（对着方块按下并按住 → 全程只挖不砍）。

---

## 数据设计缺口（**不是死数据**，勿删）

`Parts/cwc/**` 里的 `hardnessMultiplier` / `toughnessMultiplier` **当前完全不参与计算**：
`MetalParser.Formula` 声明并解析了它们，10 份金属零件 JSON 也都写了，但除字段声明外**零处读取**
（`AssemblyTree.attr()` 只取 `base`）。**改这两个字段不会改变任何数值，也不会报错。**

**它们是已设计、未实现的参数，归属尚未实现的「锻造」系统**（作者确认）：所有材料的**平均值与范围相同**，
材料之间的差异由锻造的**系数与常数**来表达。因此数据里"38 份零件乘数完全一致"是**刻意的**，
不是复制粘贴痕迹——`damage` 恒为 `h=0.5`、`durability` 恒为 `h=5, t=2`、`speed` 恒为 `0/0`
（速度取决于武器几何而非材料，与设计自洽）。

**不要删这两个字段**——删掉等于抹掉设计意图，将来要从 JSON 历史里考古。

另一处独立的设计空白：`light_guard`（镡）7 种材质的 data **一字不差**（只有 `weight` + `block: 0.25`），
所以**镡选什么材质对战斗零影响**。要让镡的材质有意义，得先定义"它该影响什么"（减伤 / 重量 / 攻速惩罚），
属设计输入，不是 bug。
