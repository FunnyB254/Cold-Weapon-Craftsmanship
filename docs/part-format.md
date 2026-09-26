# 零件数据格式（公开契约）

零件与类型定义存放在两个**数据包注册表**里，由原版加载并在配置阶段同步到客户端。
第三方模组与数据包都能读写这两个注册表。

> 这份文档是**面向扩展作者**的契约，也是本格式的**唯一规格**（旧的 `part-json-spec.md` 描述的是迁移前的
> 目录与 id 形式，已删除）。发布后 id 与文件位置就冻结了，改起来代价很高。

## 注册表

| 注册表 | 文件位置 | id 例 |
|---|---|---|
| `cwc:part_type` | `data/<你的命名空间>/cwc/part_type/**.json` | `coldweaponcraftsmanship:standard_blade` |
| `cwc:part` | `data/<你的命名空间>/cwc/part/**.json` | `coldweaponcraftsmanship:standard_blade/iron` |

**id 由注册表键给出，不在 JSON 里写**；且**命名空间取自文件所在的命名空间**（`RegistryDataLoader` 的标准行为）。
所以把你的零件放进 `data/yourmod/cwc/part/standard_blade/mythril.json`，就得到
`yourmod:standard_blade/mythril`——天然不会与本模组或别的扩展包撞车。**这是用注册表而不是自建扫描的核心收益。**

> **目录规则有个坑**：文件目录是 `data/<包命名空间>/<注册表键的命名空间>/<注册表键的路径>/`，
> 也就是**键的命名空间也占一层目录**（只有 `minecraft` 命名空间被特殊处理成不占——
> 这就是原版生物群系在 `data/minecraft/worldgen/biome/` 而不是 `data/minecraft/minecraft/worldgen/biome/` 的原因）。
> 所以本模组的键用短名 `cwc` 而不是模组 id：用模组 id 会得到命名空间重复两层的
> `data/coldweaponcraftsmanship/coldweaponcraftsmanship/cwc/part/`。
> **写扩展时照上表的 `data/<你的命名空间>/cwc/part/` 放就对**，那个 `cwc` 是注册表键的命名空间。
> 文件放错位置**不会报错**，只是条目数为 0——排查时先看日志里的 `Loaded N part types / M parts`。

读取方式（其他模组）：

```java
RegistryAccess access = level.registryAccess();
Registry<PartDef> parts = access.registryOrThrow(CwcRegistries.PART);
PartDef ironBlade = parts.get(ResourceLocation.parse("coldweaponcraftsmanship:standard_blade/iron"));
```

## 类型定义（`part_type`）

```json
{
  "data": { "type": "attack", "mount": "tang", "weight": "middle" },
  "position": { "x": 5, "y": 10 },
  "layer": 900,
  "combat": { "reach": 0.0, "knockback": 0.0 },
  "mainHandUse": { "behavior": "cwc:block_use" },
  "offHandUse":  { "behavior": "cwc:swing_use", "hud": "cwc:offhand_attack" },
  "attack":      { "behavior": "cwc:sweep_attack", "hud": "cwc:attack_indicator" },
  "disableOffHand": false,
  "slots": [
    {
      "name": "slot.cwc.guard",
      "constraint": { "type": ["guard"], "weight": ["light", "middle"] },
      "position": { "x": 6, "y": 9 },
      "priority": { "mainHandUse": 100, "attack": 100 }
    }
  ]
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `data` | 否 | 自由的字符串键值对。**它是槽位约束匹配的对象**，也是 `role()` 的判据 |
| `slots` | 否 | 可安装的子件槽位。空数组 `[]` = 不能接收其他零件 |
| `position` | 否 | 本类型贴图上的安装点，默认 `(0,0)`，见下方「锚点对齐」 |
| `layer` | 否 | 渲染层优先级，**越大越靠上**（底座永远最底），默认 0 |
| `combat` | 否 | 攻击几何：`reach`（交互距离加成）/ `knockback`。普攻方式不在这里，见 `attack` |
| `offset` | 否 | 整体贴图平移（像素），改变握持位置 |
| `mainHandUse` | 否 | **这件物品在主手时**右键做什么，见「行为与 HUD」 |
| `offHandUse` | 否 | **这件物品在副手时**右键做什么 |
| `attack` | 否 | **这件物品的攻击方式**（普攻风格与几何）。在哪只手都读同一份 |
| `disableOffHand` | 否 | 是否屏蔽**另一只手**的动作（双手武器占用右键那条规则）。默认 false |

**类型只有一种数据形状**，所以没有 `parser` 分派：`data` 是自由键值对，加语义键直接加即可，不需要新 codec。

### 行为与 HUD（`mainHandUse` / `offHandUse` / `attack`）

三个字段的取值都是同一个形状：**`behavior` 必填、`hud` 可省**。

```json
"offHandUse": { "behavior": "cwc:swing_use", "hud": "cwc:offhand_attack" }
```

- `behavior` 是**代码侧注册的行为 id**（`BehaviorRegistry`）。本模组内建：

  | id | 用途 | 可挂的字段 |
  |---|---|---|
  | `cwc:block_use` | 右键按住格挡（减伤 = 25% + 镡的 `block`） | `mainHandUse` / `offHandUse` |
  | `cwc:swing_use` | 右键瞬发一次单体攻击，走自己的独立冷却 | `mainHandUse` / `offHandUse` |
  | `cwc:strike_attack` | 普攻：单体全额、稳定直击、无跳劈暴击 | `attack` |
  | `cwc:sweep_attack` | 普攻：范围全额伤害（空挥也扫） | `attack` |
  | `cwc:critical_attack` | 普攻：单体，保留原版跳劈暴击 | `attack` |

  写错 id、或**把行为挂到它不支持的字段上**（例如把 `cwc:sweep_attack` 写进 `mainHandUse`），
  都会让**那一条数据加载失败**并在日志里报错——不会静默失效。第三方模组可以注册自己的行为，
  见下方「扩展方式」。

- `hud` 是**客户端画的 HUD**（`BehaviorHudRegistry`），可省 = 不画。内建两个：`cwc:attack_indicator`
  （攻击指示器那条：未就绪画进度条、就绪且有目标画满格）、`cwc:offhand_attack`（副手出刀那条）。
  `hud` 写错只在客户端首次绘制时 WARN 一次（数据在服务端加载、HUD 注册在客户端，加载期两边不一定都在场）。

- **`behavior` 与 `hud` 是同一笔声明里的两半**：行为按优先级胜出，**HUD 就是胜出那笔自己写的那个**
  （没写 = 不画）。**不从别的零件取**——否则 HUD 会配上一个它没预期的行为。想让两个行为共用一个 HUD，
  在各自的声明里写同一个 hud id 即可。

**"哪一个声明生效"由装配树决定**（一个装配体是一层）：

1. 层内每个字段各自比优先级，最大者赢；
2. **类型自己的声明是本层的默认**（视作优先级 0），槽位给的 ≥1 能压过它；
3. 优先级**绑在槽位上**（见下），子树内部算出什么优先级都**不外传**——嵌套再深也不能越级夺权；
4. 槽位没写某个字段 = **那个槽里的东西不参与**该字段；
5. **并列 = 冲突**：该字段在本层**没有胜者**（不是先到先得）。加载时会对"同一类型两个槽位给同一字段
   同一优先级"报一条 WARN。

**⚠ 子件想说话，必须由它所在的槽位 grant**（第 4 条的直接后果）。子件都在深度 ≥1，它们声明的东西要进到
"整件武器"那一层，只能靠父件槽位的 `priority` 表放行；**槽位不写 = 子件的声明到不了这一层，那一层就
"没人说话"**（该字段没有胜者，不是被子件压掉、也不是被父件的空值覆盖——空值意味着没有候选）。
本模组的实例：**格挡由刃声明**（长剑/手半剑/标准刃），但**只有双手手柄的刀身槽 grant 了
`mainHandUse`**——所以同一把刃插双手手柄会格挡、插单手手柄不会。于是"能不能格挡"由**刃声明与手柄放行
两者相与**决定，不是任何一方单独说了算。

**结果就写在武器自己的 tooltip 上**（装配之后才出现，与"属性只在装配后产生"同一条口径）：
有胜者的字段各占一行（`主手右键：格挡`），没声明的字段不出现，并列的字段用**红字**说明是哪两个槽在抢
（`攻击方式：刃槽 / 副刃槽 的优先级同为 100，该动作不会生效`）。这是"这件武器到底能做什么"以及
"某个动作为什么没反应"的唯一界面入口——装配台底座槽、背包、JEI 看到的是同一份。

**三个旧键已废弃**：`twoHanded`、`offhandAttack`、`combat.style` 写了会让**那一条类型定义加载失败**，
并在日志里报出替代写法（留着它们的唯一目的就是报错——DFU 会静默忽略不认识的键，那意味着旧数据照常加载
却行为消失，比加载失败危险得多）。替代关系：

| 旧键 | 改成 |
|---|---|
| `"twoHanded": true` | `"mainHandUse": {"behavior":"cwc:block_use"}` + `"disableOffHand": true` |
| `"offhandAttack": true` | `"offHandUse": {"behavior":"cwc:swing_use","hud":"cwc:offhand_attack"}` |
| `"combat": {"style": "sweep"}` | `"attack": {"behavior":"cwc:sweep_attack","hud":"cwc:attack_indicator"}`（`normal`/`critical` 同理换成 `cwc:strike_attack` / `cwc:critical_attack`） |

**`disableOffHand` 按层 OR 生效**（根 + 深度 1 的直接子件），子树内部声明的不外传。
（旧 `twoHanded` 同时管"主手右键格挡"和"屏蔽副手"两件事，所以它拆成了上表那两笔。）

### `data` 与角色

- **不空** → 角色 `part`（可被插入别的零件）
- **为空** → 角色 `handle_part`（底座，接收其他零件、属性聚合终点）

判据是**空不空**，不是 id 里有没有 "handle" 字样（旧实现用过字符串包含判断，已废除——加一个
`cwc:handle_guard` 之类就会产出"HANDLE_PART 物品 + 非手柄定义"的怪东西）。

`data` 中的每个键值对用于槽位匹配：候选零件插入槽位时，`constraint` 里声明的每个 key，
零件 `data` 中对应的值必须在该 key 允许的数组内。

### 槽位（`slots[]`）

| 字段 | 说明 |
|---|---|
| `name` | 槽位的语言文件 key，如 `slot.cwc.blade` → "刃槽" |
| `constraint` | 匹配约束。**空 `{}` = 全收**；值必须是字符串数组，**不能写 `null`**（会解析失败并报在日志里） |
| `scale` | 属性加权系数。`{"damage": 1.0, "speed": 0.3}` → 伤害全量计入、速度只计 30%。未声明 `scale` 或缺失某属性时默认 **1（全量）**。只作用于**直接插在这个槽里的那个零件**（它的子树各按自己的槽算；镡/配重不受影响）。<br>本模组的用法：双手手柄的刀身槽 `{"speed": 0.5}`——双手握法对刃的迟滞不那么敏感（刃的 `speed` **整笔**减半）。⚠ 它打在零件的整体贡献上，**包括刃自带的基础迟滞**（约 `-2.4`），所以数值会明显跳：铁长剑 `-3.2 → -1.6`，双手剑攻速 `0.8 → 2.4` |
| `position` | 该槽位在**父件**贴图上的安装点，默认 `(0,0)` |
| `priority` | **装在这个槽里的东西**在本层各行为字段上的话语权，键只能是 `mainHandUse` / `offHandUse` / `attack`。值必须 ≥1；**不写某个字段 = 那个槽里的东西不参与该字段**（写 `0` 或负数会加载失败）。本模组的用法：**双手**手柄的刀身槽给三个字段各 100；**单手**手柄只给 `offHandUse` / `attack`（**不给** `mainHandUse`）→ 单手武器一律不能格挡。刃自己的声明因此总是压过手柄的默认（裸手柄靠手柄自己声明的 `attack` 兜底） |

**约束匹配的是类型的语义值（如 `"type": "attack"`），不是类型 id。** 所以你自己定义的 `attack` 类型
会被现有手柄的刀身槽照常接受——这是有意为之，也是"扩展包能复用现有装配结构"的前提。

### 锚点对齐

`position` 是**贴图像素坐标**（x 向右、y 向下，0–15），用于装配渲染把子件贴图对齐到父件槽位：

```
子件渲染偏移 = 槽位安装点 − 子件类型安装点        （单位：贴图像素，渲染时 ÷16）
```

即"子件类型上的安装点"与"父件槽位上的安装点"重合。子件贴图比父件高时另有高度补偿，使安装点仍对齐。

### 装配链示例

```
柄 ── blade 槽 ──→ 刃 ── guard 槽 ──→ 镡
  └─ pommel 槽 ──→ 配重
```

子件自己也有槽位，所以"刃 + 镡"可以先拼成子装配体，再整体插进手柄的 blade 槽——装配树允许嵌套，
数值聚合与渲染都是递归的。

### 渲染

物品渲染由 `AssembledWeaponRenderer`（BEWLR）完成：**贴图路径由 id 自动推导，无需在 JSON 里声明**
（见下「贴图」）。渲染时把底座 + 所有层级零件按 `layer` 升序**在 CPU 上合成到一张贴图**，
按装配内容缓存为 `DynamicTexture`，实际只画这一张合成贴图的正面/背面/边缘面。
合成缓存的上限与释放见该类常量；F3+T 会清缓存。

## 零件定义（`part`）

```json
{
  "parser": "cwc:metal",
  "type": "coldweaponcraftsmanship:standard_blade",
  "data": {
    "damage":     { "base": 2, "hardnessMultiplier": 0.5, "toughnessMultiplier": 0 },
    "speed":      { "base": 0, "hardnessMultiplier": 0, "toughnessMultiplier": 0 },
    "durability": { "base": 50, "hardnessMultiplier": 5, "toughnessMultiplier": 2 }
  },
  "recipes": [
    { "ingredients": [ {}, { "item": "minecraft:iron_ingot", "count": 2 }, {} ] }
  ]
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `parser` | **是** | 数据形状的分派键，见下表 |
| `type` | **是** | 所属类型的注册表 id。**显式字段**——旧版靠"去掉 id 最后一个点号"推导，那等于把目录结构变成语义，已废除 |
| `data` | 否 | 形状随 `parser` 而变 |
| `recipes` | 否 | 配方列表，每条固定 3 格（制作用 3 个输入槽）。**空对象 `{}` 表示该格无材料要求**（不能写 `null`，DFU 的列表不接受 null 元素） |

### `parser` 与 `data` 的形状

| `parser` | `data` 的值 | 用途 |
|---|---|---|
| `cwc:default` | 数字或字符串 | 通用形状：`weight` 这类语义键用字符串，`block` 这类数值用数字 |
| `cwc:metal` | `{base, hardnessMultiplier, toughnessMultiplier}` | 金属零件 |
| `cwc:handle` | 恒为空 | 手柄零件（自身不贡献属性） |

**⚠ 两个乘数目前完全不参与计算**：数值只取 `base`。它们属于**尚未实现的「锻造」系统**——
所有材料的平均值与范围相同，材料差异由锻造的系数与常数表达，所以各零件的乘数一致是**刻意的**，
不是复制粘贴痕迹（`speed` 与材料无关、取决于武器几何，按设计就填 0）。改它们不会有效果。
详见 `docs/bugs.md` 的「数据设计缺口」。

### 属性键（`data` 里被读作数值的键）

| 键 | 说明 |
|---|---|
| `damage` | 攻击伤害贡献 |
| `speed` | 攻击速度贡献——**绝对量**：最终攻速 = 玩家基础 `4.0` + 各零件的 `speed` 之和。模组**不再**叠加任何固定基准，所以"有刃就比空手慢"这件事由**刃**的数值承担（刃的基础迟滞约 `-2.4`，镡/配重是各自的小增量）。改它 = 直接改手感 |
| `durability` | 耐久贡献 |
| `block` | 格挡减伤加成（镡提供）。**不走槽位权重**，裸加；见下方说明 |

> **`block` 的两点特殊语义（容易误解）：**
>
> 1. **只有装配出来的武器真的能格挡时才读得到它。** 消费它的是格挡行为
>    （`cwc:block_use`，由 `CwcCombatEvents.onHurt` 转发给"玩家正在使用的那件物品"所属的行为），
>    而格挡需要**刃声明 + 双手手柄放行**——所以镡插在**单手**武器上、插在**短刃**武器上、
>    或者插在裸手柄上，`block` 会被聚合算出来但**没有任何代码读它**。
>    换句话说：**只有双手武器才可能格挡**，镡的减伤（`0.25` 基础 + 镡 `block`）只在双手武器上生效。
> 2. **它不会出现在任何 tooltip 上。** `block` 不是原版 attribute，而是一个聚合进装配树的普通数值，
>    不落任何组件、每次受击现算，只在格挡伤害结算里体现。悬停镡看不到属性行是正常的（镡是普通零件物品，
>    属性只在**手柄底座**上推导）。
>
> 另外 `scale` 对 `block` **无效**（只对 `damage`/`speed`/`durability` 生效）。
> 格挡减伤公式 = `0.25`（基础）+ 镡 `block` 之和，最终夹到 `[0, 0.95]`。

- 数值直接使用；金属公式对象只取 `base`；**缺失的属性按 0 计**（如镡只写 `weight` + `block`）
- 聚合时按**该零件所在槽位的 `scale`** 加权累加
- **属性不写进物品组件，而是每次查询时从装配树现算**（`CwcWeapon.getDefaultAttributeModifiers`）。
  唯一被物化的是**耐久上限**（它推导不了：`ItemStack.getMaxDamage()` 只读组件，`Item` 拦不住），
  由装配台写入 + 背包内每 tick 自愈，保证非装配台路径产出的武器也有耐久

## 语言键

| 对象 | 规则 | 例 |
|---|---|---|
| 类型 | `type.<命名空间>.<路径以 . 连接>` | `type.coldweaponcraftsmanship.standard_blade` |
| 零件 | `part.<命名空间>.<路径以 . 连接>` | `part.coldweaponcraftsmanship.standard_blade.iron` |
| 槽位 | 类型 JSON 里 `slots[].name` 直接就是完整 key | `slot.cwc.blade` |

带命名空间是为了扩展包不撞键。**`type.cwc`、`weight.cwc` 这类不是 id 键**——它们是槽位约束键的
显示标签（装配台槽位详情的 tooltip 用 `<约束键>.cwc` 查），保持原样即可；加新约束键时按同样规则补标签。

## 贴图

零件贴图路径 = **id 的路径**拼到 `item/cwc/` 下：

| id | 贴图 |
|---|---|
| `coldweaponcraftsmanship:standard_blade/iron` | `coldweaponcraftsmanship:item/cwc/standard_blade/iron` |
| `yourmod:standard_blade/mythril` | `yourmod:item/cwc/standard_blade/mythril` |

**命名空间跟随 id 的命名空间**，所以扩展包的贴图走自己的资源命名空间，与本模组互不干扰。
类型 id 没有自己的贴图——图标取该类型下 id 最小的零件的贴图作代表。
推导不出贴图时显示原版缺失贴图，不会崩。

## 从加载到装配（代码侧流程）

```
数据包加载
  └─ 原版解析 data/*/cwc/part_type/** 与 cwc/part/**，填入两个注册表
     └─ 注册表在「配置阶段」同步到客户端（早于关卡加载）

关卡加载
  └─ PartRegistry.bind(level.registryAccess())   ← 缓存注册表引用（客户端与服务端都会触发）
     └─ 之后所有零件查询走 PartRegistry 的静态入口（不需要 RegistryAccess）

制造台方块 → CraftingMenu
  └─ 按类型分组列出零件 → 选材质 → 检查背包材料 → 输出槽出成品
     └─ 取出时扣材料，产物带 PART_IDENTITY

装配台方块 → AssemblingMenu
  └─ 底座槽放零件（任意带 PART_IDENTITY 的零件，以便先拼子装配体）
     └─ 按底座类型的 slots 显示槽位行，放入时用 SlotDef.accepts 校验约束
        └─ 变更写回 ASSEMBLED_SLOTS（值类型 PartNode：零件 id + 递归子节点）
           └─ 同步耐久上限；属性在查询时从装配树现算
```

`PartRegistry` 是一层**关卡作用域缓存**而非独立数据表：内容始终是原版注册表本身（不复制、不解析），
这样拿不到 `RegistryAccess` 的查询点（如属性推导）也能查到定义。

## 扩展方式

1. **给现有类型加材质**：放一份 `data/yourmod/cwc/part/<类型>/<材质>.json`，`type` 指向现有类型，
   再放一张 `assets/yourmod/textures/item/cwc/<类型>/<材质>.png`。制造台会自动把它列为该类型的材质变体。
2. **加自己的类型**：放一份 `data/yourmod/cwc/part_type/<名字>.json`。若想让本模组的手柄能装它，
   给它一个与现有零件同语义的 `data`（如 `"type": "attack", "mount": "tang"`），现有槽位约束就会接受它。
3. **加自己的数据形状**：写一个 `MapCodec<? extends PartDef>`，用
   `ParserRegistry.register("yourmod:foo", codec)` 注册，然后让零件 JSON 的 `"parser"` 指向它。
4. **读零件定义**：直接用 `registryOrThrow(CwcRegistries.PART)`。
   `AssemblyTree` 提供"从装配树算全部派生量"的现成入口，`PartDef.data()` 直接可读。
5. **加自己的行为**：实现 `WeaponBehavior`（**无状态单例**），在**任何数据包被解析之前**
   （模组构造器 / 模组总线事件里）用 `BehaviorRegistry.register(...)` 注册，然后让零件/类型的 JSON
   把 `behavior` 指向你的 id。`fields()` 声明它能挂在哪些字段上——挂错字段会让那条数据加载失败。
   需要跨 tick 记忆的动作（蓄力弓那类）返回一个 `BehaviorMachine`。
6. **加自己的 HUD**：实现 `BehaviorHud`（客户端专用，可以自由用 `GuiGraphics`），在客户端初始化阶段
   `BehaviorHudRegistry.register(...)`，让 JSON 的 `hud` 指向它。几何/翻转那类现成的画法在
   `CrosshairBar` 里，直接用，别重写。
7. **加自己的攻击方式**：`attack` 字段指向的行为覆盖 `strikeStyle` / `canHit` / `hasAnyTarget` / `strike`
   四个方法即可，具体零件（目标校验、伤害例程、范围几何、无敌帧豁免）从 `CwcCombat` 的公开入口取。

## 相关

- 零件装在哪、谁接受谁：`AssemblyTree` 与 `PartTypeDef.SlotDef.accepts`
- 行为如何被选出（五条规则）：`BehaviorResolver` 的类注释
- 数值/材料的设计缺口（两个乘数为何不生效）：`docs/bugs.md` 的「数据设计缺口」一节
