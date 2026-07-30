# 零件 JSON 格式说明

## 目录结构

```
data/<resource_namespace>/
├── types/<mod_namespace>/<type_name>.json
└── parts/<mod_namespace>/<type_name>/<part_name>.json
```

**id 从文件路径推导，不在 JSON 中存储。**

```
types/cwc/standard_blade.json          → id: cwc.standard_blade
parts/cwc/standard_blade/iron.json     → id: cwc.standard_blade.iron
parts/cwc/one_handed_sword_handle/wood.json → id: cwc.one_handed_sword_handle.wood
```

---

## 类型 JSON

`types/<mod_namespace>/<type_name>.json`

| 字段 | 类型 | 必需 | 说明 |
|---|---|---|---|
| `parser` | string | 是 | 解析器标识。`"cwc:default"` 用默认解析器 |
| `data` | object | 是 | 零件级键值对（均字符串值），决定角色和匹配行为 |
| `position` | object | 否 | 组装渲染偏移 `{"x": 5, "y": -10}`。表示该零件装在父零件上的位置 |
| `slots` | array | 是 | 槽位数组。空数组表示不能接收零件 |

### data 字段

键值对对象。key 和 value 均为字符串。

- **不空** → 零件角色 = `part`（可被插入别的零件）
- **为空** → 零件角色 = `handle_part`（底座，接收其他零件）

data 中的每个键值对用于槽位匹配。候选零件插入槽位时，`constraint` 中声明的每个 key，零件 `data` 中对应的值必须在 `constraint` 的值数组中。

### position 字段

该零件装在父零件上时的像素偏移。x 正值向右，y 正值向下。不写则默认为 `{"x": 0, "y": 0}`。

### slots 数组

每项为一个槽位：

```json
{
  "name": "slot.cwc.blade",
  "constraint": {
    "type": ["attack"],
    "mount": ["tang"],
    "weight": ["light", "middle"]
  },
  "scale": { "damage": 1.0, "speed": 0.3, "durability": 0.5 }
}
```

| 字段 | 说明 |
|---|---|
| `name` | 槽位的语言文件 key，如 `slot.cwc.blade` → "刃槽" |
| `constraint` | 匹配约束。候选零件的 `data` 中每个对应 key 的值必须在数组中 |
| `scale` | 属性加权系数。`{"damage": 1.0, "speed": 0.3}` → 该零件的伤害全量计入，速度只计 30% |

- **空数组** `[]` → 不能接收任何零件
- **空约束** `{}` → 无限制，任意零件都能装

### 示例

```json
// part: 标准刃——铤装，中等重量，有一个 guard 槽接收镡
{
  "parser": "cwc:default",
  "data": {
    "type": "attack",
    "mount": "tang",
    "weight": "middle"
  },
  "position": { "x": 5, "y": -10 },
  "slots": [
    {
      "name": "slot.cwc.guard",
      "constraint": { "type": ["guard"], "weight": ["light", "middle"] },
      "scale": { "damage": 0.0, "speed": 0.0, "durability": 0.1 }
    }
  ]
}

// handle_part: 单手剑柄——底座，两个槽位（刃和柄尾）
{
  "parser": "cwc:default",
  "data": {},
  "slots": [
    {
      "name": "slot.cwc.blade",
      "constraint": {
        "type": ["attack"],
        "mount": ["tang"],
        "weight": ["light", "middle"]
      },
      "scale": { "damage": 1.0, "speed": 0.3, "durability": 0.5 }
    },
    {
      "name": "slot.cwc.pommel",
      "constraint": { "type": ["pommel"], "weight": ["light", "middle", "heavy"] },
      "scale": { "damage": 0.0, "speed": 0.1, "durability": 0.1 }
    }
  ]
}
```

装配链：`柄 → blade槽 → 刃 → guard槽 → 镡`，`柄 → pommel槽 → 柄尾`。

---

## 零件 JSON

`parts/<mod_namespace>/<type_name>/<part_name>.json`

| 字段 | 类型 | 必需 | 说明 |
|---|---|---|---|
| `parser` | string | 是 | 材料解析器。格式取决于解析器 |
| `data` | object | 否 | 解析器输入。`cwc:default` 和 `cwc:metal` 需要，`cwc:handle` 不需要 |

### 可用解析器

| 解析器 | data 必需 | 适用 | 说明 |
|---|---|---|---|
| `cwc:default` | 是 | 通用 | data 直接写数值 |
| `cwc:metal` | 是 | 金属刃 | data 写公式，读取材料 NBT 的 hardness/toughness 代入 |
| `cwc:handle` | 否 | 手柄 | 无 data，属性由类型 + 材质决定 |

#### cwc:handle

无 data 字段：

```json
{
  "parser": "cwc:handle"
}
```

#### cwc:default

data 直接写数值：

```json
{
  "parser": "cwc:default",
  "data": { "speed": 1.0, "durability": 30 }
}
```

#### cwc:metal

data 用二元一次公式 `base + hardness × hardnessMultiplier + toughness × toughnessMultiplier`：

```json
{
  "parser": "cwc:metal",
  "data": {
    "damage": { "base": 2, "hardnessMultiplier": 0.5, "toughnessMultiplier": 0 },
    "durability": { "base": 50, "hardnessMultiplier": 5, "toughnessMultiplier": 2 }
  }
}
```

解析时读取材料的 `hardness`/`toughness` 代入公式，结果为统一数值传给类型解析器。

---

## 程序使用流程

### 一、加载阶段（启动 / F3+T 资源重载）

```
PartRegistry 启动
        │
        ▼
扫描 data/*/types/**/*.json
        │
        ├── 从路径推导 id：types/cwc/standard_blade.json → cwc.standard_blade
        ├── 读 parser 字段 → 调用对应类型解析器的 parseType()
        ├── 产出 PartTypeDef(id, data, slots, position)
        └── 按 id 存入 Map<String, PartTypeDef>
        │
        ▼
扫描 data/*/parts/**/*.json
        │
        ├── 从路径推导 id：parts/cwc/standard_blade/iron.json → cwc.standard_blade.iron
        ├── 读 parser 字段 → 调用对应材料解析器的 parsePart()
        ├── 产出 PartDef(id, parser, data, displayName)
        └── 按 id 存入 Map<String, PartDef>
        │
        ▼
组装器就绪：PartRegistry 持有所有 TypeDef + PartDef
```

### 二、制造阶段（打开工匠台）

```
玩家右键 CreativePartStar → 打开制造界面
        │
        ▼
CraftingMenu 构造 → PartRegistry 提供刃类配方列表
        │
        ├── 输入槽展示配方需求（幽灵物品）
        ├── 检查玩家背包材料 → 充足则输出槽出现成品
        └── 切换按钮 → 轮询配方
        │
        ▼
玩家取出输出槽成品
        │
        ├── 从背包扣除材料
        ├── 创建 ItemStack，存入 PART_IDENTITY 和 CUSTOM_NAME
        └── 放入玩家背包
```

### 三、组装阶段（打开组装界面）

```
玩家打开组装界面
        │
        ▼
放置底座（handle_part）到中心槽
        │
        ├── 读取 PartTypeDef.slots → 遍历每个槽位
        ├── 渲染槽位（名称来自 slot 的 name lang key）
        └── 每个槽位高亮：提示可放入的零件类型
        │
        ▼
玩家点击零件放入槽位
        │
        ├── 读零件 PartTypeDef → 获取 data 键值对
        └── 匹配：零件 data 中 constraint 要求的每个 key 的值都在接受列表中？
                ├── 是 → 放入，以零件 position 偏移渲染
                └── 否 → 拒绝
        │
        ▼
所有槽位填满后，合并属性：
        ├── 各零件属性按 scale 加权累加 → 最终武器属性
        ├── 组合显示名（按语言文件）
        └── 产出最终武器 ItemStack
```
