# 零件数据格式（公开契约）

零件与类型定义存放在两个**数据包注册表**里，由原版加载并在配置阶段同步到客户端。
第三方模组与数据包都能读写这两个注册表。

> 这份文档是**面向扩展作者**的契约，也是本格式的**唯一规格**。发布后 id 与文件位置就冻结了，
> 改起来代价很高。

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

## 改完怎么生效

**`/reload` 不会加载新的零件定义。**

这两个注册表属于原版的 **WORLDGEN 层**，在**关卡加载时**由 `RegistryDataLoader` 读取一次；
而 `/reload` 只重建 **RELOADABLE 层**（战利品表那一层）——它既不会重读 `data/**/cwc/**`，
也不会重新触发绑定，数据也不会重新同步给客户端。

| 你的定义放在 | 改完之后要做什么 |
|---|---|
| 世界存档的 `datapacks/` | **退出世界再进**（服务器则重启服务器），不必重启游戏 |
| 模组 jar 内（`data/<命名空间>/cwc/...`） | 重新构建 jar，再重启游戏 / 服务器 |
| 本仓库的 `src/main/resources/data/...` | 本地开发时游戏读的是构建产物（`build/resources/main`），改完要跑一次 `./gradlew processResources` 同步过去，再退出世界重进 |

改完进游戏后，到日志里找这一行确认加载到了：

```
Loaded 8 part types / 56 parts
```

它出自 `PartRegistry.bind()`，触发点是**关卡加载**（服务端每个维度各打一次，客户端打一次）。

> ⚠ **`/reload` 之后数据"没失效"不等于"能加载新数据"**：`/reload` 之后已经装配好的武器照常工作
> （注册表引用仍然有效），但你新加的文件不会被读到。这两件事很容易混。

### 条目数为 0 的排查清单

文件放错位置**不会报错**，只是条目数为 0。按这个顺序查：

1. 目录是不是 `data/<你的命名空间>/cwc/part/...`？那个 `cwc` 是**注册表键的命名空间**，
   不是你的模组 id。写成 `data/yourmod/yourmod/cwc/part/` 就错了。
2. 数据包有没有被启用？世界存档的 `datapacks/` 需要 `pack.mcmeta`，
   且要在「数据包」界面里确认它处于**已启用**状态。
3. 有没有真的重进世界？见上表——`/reload` 不管用。
4. JSON 本身有没有解析错误？解析失败会单独报一条 ERROR，不会让你只看到"条数为 0"。

### 最小可跑的数据包

只加一个零件（mythril 标准刃）的最小数据包，放进世界存档的 `datapacks/`：

```
<世界存档>/datapacks/mythril_blade/
├── pack.mcmeta
└── data/
    └── yourmod/
        └── cwc/
            └── part/
                └── standard_blade/
                    └── mythril.json
```

`pack.mcmeta`（1.21.1 的数据包版本号是 48）：

```json
{
  "pack": {
    "pack_format": 48,
    "description": "Mythril blade"
  }
}
```

`mythril.json`——形状照抄本模组的铁刃，只改数值（材料用紫水晶碎片，免得和内置配方撞车）：

```json
{
  "parser": "cwc:metal",
  "type": "coldweaponcraftsmanship:standard_blade",
  "data": {
    "damage":     { "base": 3,    "hardnessMultiplier": 0.5, "toughnessMultiplier": 0 },
    "speed":      { "base": -2.0, "hardnessMultiplier": 0,   "toughnessMultiplier": 0 },
    "durability": { "base": 80,   "hardnessMultiplier": 5,   "toughnessMultiplier": 2 }
  },
  "recipes": [
    { "ingredients": [ {}, { "item": "minecraft:amethyst_shard", "count": 2 }, {} ] }
  ]
}
```

这样就得到 id `yourmod:standard_blade/mythril`，插进任何接受 `type: attack` + `mount: tang`
的刀身槽即可。**贴图不写也能用**——缺贴图会回落原版缺失贴图并记一条 ERROR，不会崩游戏。

## 类型定义（`part_type`）

本模组真实的两个定义，照抄即可用。**刃** `standard_blade.json`：

```json
{
  "data": {
    "type": "attack",
    "mount": "tang",
    "weight": "middle"
  },
  "position": {
    "x": 5,
    "y": 10
  },
  "layer": 900,
  "combat": {
    "reach": 0.0,
    "knockback": 0.0
  },
  "attack": {
    "behavior": "cwc:sweep_attack",
    "hud": "cwc:crosshair_bar"
  },
  "mainHandUse": {
    "behavior": "cwc:block_use"
  },
  "slots": [
    {
      "name": "slot.cwc.guard",
      "constraint": {
        "type": ["guard"],
        "weight": ["light", "middle"]
      },
      "position": {
        "x": 6,
        "y": 9
      }
    }
  ]
}
```

**双手手柄** `two_handed_sword_handle.json`——注意 `data` 为空（角色 = `handle_part`）、
`disableOffHand` 为 true、刀身槽的 `priority` 给了三个字段各 100，还有 `offset` 与 `scale`：

```json
{
  "data": {},
  "layer": 1000,
  "disableOffHand": true,
  "attack": {
    "behavior": "cwc:strike_attack",
    "hud": "cwc:crosshair_bar"
  },
  "offset": {
    "x": -5,
    "y": -5
  },
  "slots": [
    {
      "name": "slot.cwc.blade",
      "constraint": {
        "type": ["attack"],
        "mount": ["tang"],
        "weight": ["middle", "heavy"]
      },
      "position": {
        "x": 10,
        "y": 5
      },
      "scale": {
        "speed": 0.5
      },
      "priority": {
        "mainHandUse": 100,
        "offHandUse": 100,
        "attack": 100
      }
    },
    {
      "name": "slot.cwc.pommel",
      "constraint": {
        "type": ["pommel"],
        "weight": ["light", "middle", "heavy"]
      },
      "position": {
        "x": 2,
        "y": 13
      }
    }
  ]
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `data` | 否 | 自由的字符串键值对。**它是槽位约束匹配的对象**，也是 `role()` 的判据 |
| `slots` | 否 | 可安装的子件槽位。空数组 `[]` = 不能接收其他零件 |
| `position` | 否 | 本类型贴图上的安装点，默认 `(0,0)`，见下方「锚点对齐」 |
| `layer` | 否 | 渲染层优先级，**升序合成：值越大越后画、越靠上**，默认 0。**底座不特殊**——根节点用的就是自己类型的 `layer`，不会被强制置底。本模组取 900（四种刃）/ 1000（两种手柄）/ 1100（配重）/ 1200（镡），即"刃在最下、镡在最上" |
| `combat` | 否 | 攻击几何：`reach`（交互距离加成）/ `knockback`。普攻方式不在这里，见 `attack` |
| `offset` | 否 | 整体贴图平移（像素），改变握持位置 |
| `mainHandUse` | 否 | **这件物品在主手时**右键做什么，见「行为与 HUD」 |
| `offHandUse` | 否 | **这件物品在副手时**右键做什么 |
| `attack` | 否 | **这件物品的攻击方式**（普攻风格与几何）。在哪只手都读同一份 |
| `disableOffHand` | 否 | 是不是**双手武器**（"需要两只手"那条规则）。**只吃副手，主手永远不受影响**：这把武器无论拿在哪只手上，被禁用的都是副手。**含原版动作**：副手放方块 / 用桶 / 喂食一并挡掉，不只是本模组的副手出刀，副手还会因此**沉下去一半**（与原版物品切换同速的过渡）。默认 false |

**类型只有一种数据形状**，所以没有 `parser` 分派：`data` 是自由键值对，加语义键直接加即可，不需要新 codec。

### 行为与 HUD（`mainHandUse` / `offHandUse` / `attack`）

三个字段的取值都是同一个形状：**`behavior` 必填、`hud` 可省**。

```json
{ "offHandUse": { "behavior": "cwc:swing_use", "hud": "cwc:crosshair_bar" } }
```

**两个键分工不同**：`behavior` 决定**做什么**、并给出 HUD 的**读数**；
`hud` 只决定**画成什么样**（一个样式），**不含来源**。

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

- `hud` 是**样式**（`HudStyleRegistry`，客户端解释），可省 = 不画。内建一个：`cwc:crosshair_bar`
  ——原版攻击指示器那种准星条（未就绪画进度条、就绪且有目标画满格图标）。**两条指示器共用它**：
  **画在哪由手决定**（主手那条在准星下方、副手那条是它的镜像），与样式无关。
  `hud` 写错只在客户端首次绘制时 WARN 一次（数据在服务端加载、样式注册在客户端，加载期两边不一定都在场）
  ——这是"样式 id 留在数据里"的已知代价。

- **读数由行为给、画法由 `hud` 选**：显示什么（进度 + 高亮）由胜出的行为回答
  （`WeaponBehavior#hudReadout`，默认实现就是两条指示器要的读法：主手读玩家攻速条、副手读它自己的
  独立冷却，高亮 = 这只手能不能打到；**主手那条**再 AND 一条原版条件——这件物品的攻速延迟必须 > 5，
  于是空手与空手柄永远不亮满格图标，与原版攻击指示器的判定一致）。所以样式挂在哪个字段上都说得通，
  "挂错字段"不是语义错误。

- **`behavior` 与 `hud` 是同一笔声明里的两个键**：行为按优先级胜出，**样式就是胜出那笔自己写的那个**
  （没写 = 不画）。**不从别的零件取**——否则样式会配上一个它没预期的行为。想让两个行为共用一个样式，
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

**结果就写在武器自己的 tooltip 上**（**裸手柄也照同一条规则显示**——空手柄在每一条游戏路径上都与装配后
同等对待，没有"装没装零件"的分支）：
有胜者的字段各占一行（`主手右键：格挡`），没声明的字段不出现，并列的字段用**红字**说明是哪两个槽在抢
（`攻击方式：刃槽 / 副刃槽 的优先级同为 100，该动作不会生效`）。这是"这件武器到底能做什么"以及
"某个动作为什么没反应"的唯一界面入口——装配台底座槽、背包、JEI 看到的是同一份。

**三个旧键已废弃**：`twoHanded`、`offhandAttack`、`combat.style` 写了会让**那一条类型定义加载失败**，
并在日志里报出替代写法。留着它们的唯一目的就是报错——原版的数据修复层（DataFixerUpper，DFU）
会**静默忽略不认识的键**，那意味着旧数据照常加载、行为却没了，比加载失败危险得多。替代关系：

| 旧键 | 改成 |
|---|---|
| `"twoHanded": true` | `"mainHandUse": {"behavior":"cwc:block_use"}` + `"disableOffHand": true` |
| `"offhandAttack": true` | `"offHandUse": {"behavior":"cwc:swing_use","hud":"cwc:crosshair_bar"}` |
| `"combat": {"style": "sweep"}` | `"attack": {"behavior":"cwc:sweep_attack","hud":"cwc:crosshair_bar"}`（`normal`/`critical` 同理换成 `cwc:strike_attack` / `cwc:critical_attack`） |

**`disableOffHand` 按层 OR 生效**（根 + 深度 1 的直接子件），子树内部声明的不外传；
且**只在"有没有"这个意义上生效**——两只手都拿着双手武器，副手也只被占用一次（下沉一次，不会叠）。
（旧 `twoHanded` 同时管"主手右键格挡"和"屏蔽副手"两件事，所以它拆成了上表那两笔。）

**"只吃副手"是单向的**（有意为之）：双手武器**不论拿在哪只手上**，被禁用的都是
**副手**，主手永远不受影响。所以"副手攥着一把双手柄把主手废掉、且看不出原因"那种情形不存在了。

### `data` 与角色

- **不空** → 角色 `part`（可被插入别的零件）
- **为空** → 角色 `handle_part`（底座，接收其他零件、属性聚合终点）

判据是**空不空**，不是 id 里有没有 "handle" 字样——否则加一个 `cwc:handle_guard` 之类
就会产出"HANDLE_PART 物品 + 非手柄定义"的怪东西。

`data` 中的每个键值对用于槽位匹配：候选零件插入槽位时，`constraint` 里声明的每个 key，
零件 `data` 中对应的值必须在该 key 允许的数组内。

### 槽位（`slots[]`）

| 字段 | 说明 |
|---|---|
| `name` | 槽位的语言文件 key，如 `slot.cwc.blade` → "刃槽" |
| `constraint` | 匹配约束。**空 `{}` = 全收**；值必须是字符串数组，**不能写 `null`**（会解析失败并报在日志里） |
| `scale` | 属性加权系数。`{"damage": 1.0, "speed": 0.3}` → 伤害全量计入、速度只计 30%。未声明 `scale` 或缺失某属性时默认 **1（全量）**。装配台的槽位详情里会以「属性倍率」列出**不为 1** 的那些项（只列 `damage`/`speed`/`durability` 三个——`block` 不走槽位权重，写在这里既不生效也不显示）。只作用于**直接插在这个槽里的那个零件**（它的子树各按自己的槽算；镡/配重不受影响）。<br>本模组的用法：双手手柄的刀身槽 `{"speed": 0.5}`——双手握法对刃的迟滞不那么敏感（刃的 `speed` **整笔**减半）。⚠ 它打在零件的整体贡献上，**包括刃自带的基础迟滞**（约 `-2.4`），所以数值会明显跳：铁长剑 `-3.2 → -1.6`，双手剑攻速 `0.8 → 2.4` |
| `position` | 该槽位在**父件**贴图上的安装点，默认 `(0,0)` |
| `priority` | **装在这个槽里的东西**在本层各行为字段上的话语权，键只能是 `mainHandUse` / `offHandUse` / `attack`。值必须 ≥1；**不写某个字段 = 那个槽里的东西不参与该字段**（写 `0` 或负数会加载失败）。本模组的用法：**双手**手柄的刀身槽给三个字段各 100；**单手**手柄只给 `offHandUse` / `attack`（**不给** `mainHandUse`）→ 单手武器一律不能格挡。刃自己的声明因此总是压过手柄的默认（裸手柄靠手柄自己声明的 `attack` 兜底） |

**约束匹配的是类型的语义值（如 `"type": "attack"`），不是类型 id。** 所以你自己定义的 `attack` 类型
会被现有手柄的刀身槽照常接受——这是有意为之，也是"扩展包能复用现有装配结构"的前提。

### 锚点对齐

`position` 是**贴图像素坐标**（x 向右、y 向下），用于装配渲染把子件贴图对齐到父件槽位。
取值范围**由该类型的精灵图尺寸决定**，不是固定的 0–15——精灵图 16×16 时是 0–15，32×32 时就是 0–31
（本模组的 `half_sword` / `long_blade` 用 32×32，安装点的 y 分别是 26 / 25）。
代码**不做范围校验**：安装点落在自己那张精灵图之外不会报错，只会让贴图错位。

对齐公式：

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

物品渲染由 `AssembledWeaponRenderer`（BlockEntityWithoutLevelRenderer，BEWLR）完成：
**贴图路径由 id 自动推导，无需在 JSON 里声明**（见下「贴图」）。渲染时把底座 + 所有层级零件
按 `layer` 升序**在 CPU 上合成到一张贴图**，按装配内容缓存为 `DynamicTexture`，
实际只画这一张合成贴图的正面/背面/边缘面。

合成缓存是一张按装配内容索引的 LRU 表，上限是 `AssembledWeaponRenderer` 里的
`MAX_COMPOSITES = 256`；超出后淘汰最久未用的并释放其纹理。F3+T 会把合成缓存与零件图缓存一起清空。

## 零件定义（`part`）

本模组的铁标准刃 `standard_blade/iron.json`：

```json
{
  "parser": "cwc:metal",
  "type": "coldweaponcraftsmanship:standard_blade",
  "data": {
    "damage":     { "base": 2, "hardnessMultiplier": 0.5, "toughnessMultiplier": 0 },
    "speed":      { "base": -2.4, "hardnessMultiplier": 0, "toughnessMultiplier": 0 },
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
| `type` | **是** | 所属类型的注册表 id。**显式字段**，不从 id 推导——否则目录结构会变成语义 |
| `data` | 否 | 形状随 `parser` 而变 |
| `recipes` | 否 | 配方列表，每条是一串 ingredient。制造台的 3×3 输入格按**无序**匹配——材料放哪一格都行，只看"够不够"；`count` 算的是**物品总数**（一堆 4 个放一格也算数，不是原版那种一格子一件）。**空对象 `{}` 表示无这项材料要求**（不能写 `null`，DFU 的列表不接受 null 元素） |

### `parser` 与 `data` 的形状

| `parser` | `data` 的值 | 用途 |
|---|---|---|
| `cwc:default` | 数字或字符串 | 通用形状：`weight` 这类语义键用字符串，`block` 这类数值用数字 |
| `cwc:metal` | `{base, hardnessMultiplier, toughnessMultiplier}` | 金属零件 |
| `cwc:handle` | 恒为空 | 手柄零件的常用形状——**是这个形状不携带 data**，不是"手柄不贡献属性"：手柄想自己带 `damage`/`speed`/`durability`，把 `parser` 换成 `cwc:default` / `cwc:metal` 即可（角色判据是**类型**的 data 空不空，与零件的 parser 无关） |

**⚠ 两个乘数目前完全不参与计算**：数值只取 `base`。它们属于**尚未实现的「锻造」系统**——
所有材料的平均值与范围相同，材料差异由锻造的系数与常数表达，所以各零件的乘数一致是**刻意的**，
不是复制粘贴痕迹（`speed` 与材料无关、取决于武器几何，按设计就填 0）。改它们不会有效果。

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
> 2. **原版属性 tooltip 上没有它。** `block` 不是原版 attribute，而是一个聚合进装配树的普通数值，
>    不落任何组件、每次受击现算，只在格挡伤害结算里体现。悬停镡看不到属性行是正常的（镡是普通零件物品，
>    属性只在**手柄底座**上推导）。
>    唯一能看到它的地方是制造台/装配台左侧那个**零件数值浮窗**（它自己列 `block` 一行，
>    不受原版 attribute 机制约束）。
>
> 另外 `scale` 对 `block` **无效**（只对 `damage`/`speed`/`durability` 生效）。
> 格挡减伤公式 = `0.25`（基础）+ 镡 `block` 之和，最终夹到 `[0, 0.95]`。

- 数值直接使用；金属公式对象只取 `base`；**缺失的属性按 0 计**（如镡只写 `weight` + `block`）
- 聚合时按**该零件所在槽位的 `scale`** 加权累加
- **根节点（底座）自身也参与聚合**，权重按 1（它没有"所在槽位"）。所以手柄的零件定义写了数值就直接
  算进这把武器——**没装任何子件时同样生效、也显示在 tooltip 上**（不留"没装零件就没有属性"的特判：
  那种写法会变成"写了不生效、也不报错"）
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

**非手柄零件的物品 tooltip 读的是同一批键**（`item/PartItem`）：`type` / `mount` / `weight` 取自
**类型**的 `data`——与槽位约束匹配同源（`SlotDef.accepts(PartTypeDef)`），所以"tooltip 显示什么"
就等于"槽位收不收它"（**材质不在其中**：它没有字段，只能从 id 路径推，与"槽位收不收它"无关）。
按上面规则补了标签与取值，装配台的槽位详情与零件的物品 tooltip 会一起显示出来；
零件 tooltip 的**数值**不在这里（在装配台左侧的零件数值浮窗）。

## 贴图

零件贴图**不是 PNG**，而是「**精灵图（形状）+ 调色板（配色）**」两份 JSON，运行时逐像素现画
（见 `client/sprite/SpriteTextures`）。同一类型的七个材质形状完全相同、只差几个颜色，
所以形状只画一份、每个材质只写一张配色表。

两份文件的路径都由 **id 的路径**推导，**无需在零件 JSON 里声明任何字段**：

| 文件 | 路径 | 数量 |
|---|---|---|
| 调色板 | `assets/<命名空间>/cwc/palette/<类型>/<材质>.json` | 每（类型 × 材质）一份 |
| 精灵图 | `assets/<命名空间>/cwc/sprite/<类型>/<组>.json` | 每（类型 × 组）一份 |

| id | 调色板 |
|---|---|
| `coldweaponcraftsmanship:standard_blade/iron` | `coldweaponcraftsmanship:cwc/palette/standard_blade/iron.json` |
| `yourmod:standard_blade/mythril` | `yourmod:cwc/palette/standard_blade/mythril.json` |

用哪条精灵图**只写在调色板的 `sprite` 字段里**（精灵图自己不知道有谁在用它），
所以"哪些材质共用一条形状"改数据即可，不必改代码。本模组现在分三组：

| 组 | 材质 | 理由 |
|---|---|---|
| `non_metal` | wood、stone | 木石画法自成一套 |
| `metal` | copper、gold、iron、diamond | 这四种的明暗分区**完全一致**，能共用一条 |
| `alloy` | netherite | 下界合金的明暗比其余材质细（原版 `netherite_sword` 本身就与 `iron_sword` 画法不同），共用会把别的材质撑出重复项 |

分组的唯一硬要求是**同组各材质的"同色分区"必须一致**——即"哪两个像素是同一个颜色"这件事要完全相同。
不一致时只能退到**最细共同分区**：只要有一个材质把那两个像素画成异色，就得把它们拆成两个槽，
于是把这两像素画成同色的那个材质，调色板里会出现重复色值。这是无损表达的必然代价，
不是数据写错了。

**调色板** `cwc/palette/<类型>/<材质>.json`：

```json
{
  "sprite": "coldweaponcraftsmanship:standard_blade/metal",
  "colors": [
    "#00000000",
    "#5F2C1CFF",
    "#803921FF"
  ]
}
```

| 字段 | 说明 |
|---|---|
| `sprite` | 精灵图引用 `<命名空间>:<类型>/<组>`，对应 `cwc/sprite/<类型>/<组>.json` |
| `colors` | RGBA 十六进制串。接受 `#RRGGBBAA` / `RRGGBBAA` / `#RRGGBB`（六位 = 不透明）。**第 0 项必须是透明项**（`alpha = 00`）——精灵图用下标 0 表示背景，第 0 项若不透明会把背景整片涂上色 |

**精灵图** `cwc/sprite/<类型>/<组>.json`：

```json
{
  "pixels": [
    [0,0,0,1,1,0],
    [0,1,2,2,1,0]
  ]
}
```

`pixels[y][x]` 是**调色板下标**，0 = 透明。尺寸由数组自身决定（宽 = 列数、高 = 行数），
可以是 16×16 也可以是 32×32。**每行长度必须一致**——不一致直接报错，该零件回落缺失贴图。

其余约定：

- **命名空间跟随 id 的命名空间**，扩展包的贴图走自己的资源命名空间，与本模组互不干扰。
- 类型 id 没有自己的贴图——图标取该类型下 id 最小的零件的贴图作代表。
- 两份 JSON 任一缺失、格式错、下标越界、颜色串非法，都**记一条 ERROR 并回落原版缺失贴图，不崩游戏**。
- 每个零件画好的图**各留一份缓存**（这份没有上限，与武器合成缓存不同）；
  F3+T 时连同武器合成缓存一起清空，下次用到时重新画。

## 从加载到装配（代码侧流程）

```
数据包加载
  └─ 原版解析 data/*/cwc/part_type/** 与 cwc/part/**，填入两个注册表
     └─ 注册表在「配置阶段」同步到客户端（早于关卡加载）

关卡加载
  └─ PartRegistry.bind(level.registryAccess())   ← 缓存注册表引用（客户端与服务端都会触发）
     └─ 之后所有零件查询走 PartRegistry 的静态入口（不需要 RegistryAccess）

制造台方块 → CraftingMenu（工作台式：左上 3×3 输入格 + 左下产出，右侧选类型）
  └─ 右侧列表按类型分组列出零件 → 选中一类 → 往 3×3 里摆材料
     └─ 在该类型的各材质变体里找**配方被格子满足**的那一条（无序、按物品总数）→ 输出槽出那一件
        └─ 同时满足多条 = 配方冲突 → 左侧轮换按钮点亮，点击在候选间切换
           └─ 取出时从**格子**扣材料，产物带 `coldweaponcraftsmanship:part_identity`
              （记下它是哪个零件）；关界面时格子里的东西还给玩家

装配台方块 → AssemblingMenu
  └─ 底座槽放零件（任意带 `part_identity` 的零件，以便先拼子装配体）
     └─ 按底座类型的 slots 显示槽位行，放入时用 SlotDef.accepts 校验约束
        └─ 变更写回 `coldweaponcraftsmanship:assembled_slots`
           （值类型 PartNode：零件 id + 递归子节点）
           └─ 同步耐久上限；属性在查询时从装配树现算
```

这两个都是**物品数据组件**（Item DataComponent，注册在 `registry/CwcDataComponents`）：
`coldweaponcraftsmanship:part_identity`（字符串）与 `coldweaponcraftsmanship:assembled_slots`
（`Map<String, PartNode>`）。给任意物品写上它们，就会被本模组按零件 / 装配体对待——
这是让自定义物品接入装配系统的入口。

`PartRegistry` 是一层**关卡作用域缓存**而非独立数据表：内容始终是原版注册表本身（不复制、不解析），
这样拿不到 `RegistryAccess` 的查询点（如属性推导）也能查到定义。

## 扩展方式

1. **给现有类型加材质**：放一份 `data/yourmod/cwc/part/<类型>/<材质>.json`，`type` 指向现有类型，
   再放一份 `assets/yourmod/cwc/palette/<类型>/<材质>.json`（`sprite` 指向已有一条精灵图，
   或自带一条 `assets/yourmod/cwc/sprite/<类型>/<组>.json`）。制造台会自动把它列为该类型的材质变体。
2. **加自己的类型**：放一份 `data/yourmod/cwc/part_type/<名字>.json`。若想让本模组的手柄能装它，
   给它一个与现有零件同语义的 `data`（如 `"type": "attack", "mount": "tang"`），现有槽位约束就会接受它。
3. **加自己的数据形状**：写一个 `MapCodec<? extends PartDef>`，用
   `ParserRegistry.register("yourmod:foo", codec)` 注册，然后让零件 JSON 的 `"parser"` 指向它。
4. **读零件定义**：直接用 `registryOrThrow(CwcRegistries.PART)`。
   `AssemblyTree` 提供"从装配树算全部派生量"的现成入口，`PartDef.data()` 直接可读。
5. **加自己的行为**：实现 `WeaponBehavior`（`com.funnyb.cwc.combat.behavior`，**无状态单例**），
   在**任何数据包被解析之前**（模组构造器 / 模组总线事件里）用 `BehaviorRegistry.register(...)` 注册，
   然后让零件/类型的 JSON 把 `behavior` 指向你的 id。`fields()` 声明它能挂在哪些字段上——
   挂错字段会让那条数据加载失败。除 `id()` 与 `fields()` 外的方法都有 `default` 实现，按需覆盖即可。
   ⚠ 接口里还有 `machine()` 与配套的 `BehaviorMachine`，本意是给"跨 tick 记忆"的动作（蓄力弓那类）用，
   但**目前没有任何代码调用它**——现有五个行为都不需要，这条路径尚未接通，别照着它设计。
6. **加自己的 HUD 样式**：实现 `HudStyle`（客户端专用，可以自由用 `GuiGraphics`）——它的入参只有
   **进度**与**高亮**两个数（读数由行为给，样式不自己去解析物品），在客户端初始化阶段
   `HudStyleRegistry.register(...)`，让 JSON 的 `hud` 指向它。几何/翻转那类现成的画法在
   `CrosshairBar` 里，直接用，别重写。
   要改的是"显示什么"而不是"画成什么样"时，覆盖行为的 `hudReadout`（通用侧），别动样式。
7. **加自己的攻击方式**：`attack` 字段指向的行为覆盖 `strikeStyle` / `canHit` / `hasAnyTarget` / `strike`
   这四个**是 `WeaponBehavior` 上的方法**（都是 `default`），默认实现分别转发给 `CwcCombat` 的
   `strikeStyle` / `canHitSingle`·`canHitSweep` / `hasSweepTarget` / `strikeSingle`·`strikeSweep`。
   要自己实现时，目标校验、伤害例程、范围几何、无敌帧豁免都能从 `CwcCombat` 的公开静态入口取；
   它另有 `resolveReach`、`pickAttackTargetId`、`hasAnyAttackableTarget` 等现成件可用。

## 相关

- 零件装在哪、谁接受谁：`AssemblyTree` 与 `PartTypeDef.SlotDef.accepts`
- 行为如何被选出（五条规则）：`BehaviorResolver` 的类注释
