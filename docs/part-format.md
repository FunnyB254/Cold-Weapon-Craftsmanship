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
  "combat": { "reach": 0.0, "knockback": 0.0, "style": "sweep" },
  "slots": [
    {
      "name": "slot.cwc.guard",
      "constraint": { "type": ["guard"], "weight": ["light", "middle"] },
      "position": { "x": 6, "y": 9 }
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
| `combat` | 否 | `reach`（交互距离加成）/ `knockback` / `style`（`normal`/`sweep`/`critical`） |
| `twoHanded` | 否 | 双手武器：占用主手右键（格挡），屏蔽副手交互。默认 false |
| `offset` | 否 | 整体贴图平移（像素），改变握持位置 |
| `offhandAttack` | 否 | 可放在副手右键出刀（短刀就是这个） |

**类型只有一种数据形状**，所以没有 `parser` 分派：`data` 是自由键值对，加语义键直接加即可，不需要新 codec。

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
| `scale` | 属性加权系数。`{"damage": 1.0, "speed": 0.3}` → 伤害全量计入、速度只计 30%。未声明 `scale` 或缺失某属性时默认 **1（全量）** |
| `position` | 该槽位在**父件**贴图上的安装点，默认 `(0,0)` |

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
| `speed` | 攻击速度贡献（装配时叠加基础 `-2.4`，与原版剑一致） |
| `durability` | 耐久贡献 |
| `block` | 格挡减伤加成（镡提供）。**不走槽位权重**，裸加；见下方说明 |

> **`block` 的两点特殊语义（容易误解）：**
>
> 1. **只有双手武器格挡时才被读取。** 消费它的 `CwcCombatEvents.onHurt` 要求
>    `player.isUsingItem()` **且** 主手是**双手**底座（`isTwoHandedStack`）。所以装在**单手**武器
>    （手半剑/标准刃/短刃/长刃）上的镡，`block` 会被聚合算出来但**没有任何代码读它**——纯装饰。
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

## 相关

- 零件装在哪、谁接受谁：`AssemblyTree` 与 `PartTypeDef.SlotDef.accepts`
- 数值/材料的设计缺口（两个乘数为何不生效）：`docs/bugs.md` 的「数据设计缺口」一节
