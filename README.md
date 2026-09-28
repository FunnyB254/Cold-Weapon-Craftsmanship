# Cold Weapon Craftsmanship · 冷兵器工艺

在装配台上把零件拼成一把冷兵器。零件（刃、镡、配重、手柄）与材质（木、石、铜、铁、金、钻石、下界合金）可以自由组合，装出来的武器拥有自己的伤害、攻速与耐久；它能在战斗中做什么——格挡、横扫、直击、副手短刀——同样由数据声明。零件定义、类型定义与贴图都是数据包驱动的，扩展包可以添加自己的零件、类型乃至新的战斗行为，而不必碰 Java 代码。

## AI 披露

本项目的代码**大批量由 AI 生成**。作者的角色在于设计与判断：

- **架构与玩法由作者设计**——装配树的层级、行为系统的形状、数据包注册表的规则，以及武器手感与战斗数值的取舍，都是作者定的，AI 负责把它们写出来。
- **绘图由作者提供模板**——精灵图的形状模板出自作者之手，AI 在其基础上着色与铺量。
- **测试中 AI 是重要角色**——构造用例、复现问题、核对行为，相当一部分由 AI 承担。

AI 是执行者，设计与取舍在作者。

## 支持版本

### 当前支持

| Minecraft | 加载器 | 状态 |
|---|---|---|
| 1.21.1 | NeoForge 21.1.241 或更高 | 开发中（`0.1.0`） |

### 未来计划

计划中的版本**没有时间表**，取决于内容推进情况；下表顺序不代表先后承诺。

| Minecraft | 加载器 | 状态 |
|---|---|---|
| 1.21.1 | Fabric | 计划中 |
| 26.1.2 | NeoForge / Fabric | 计划中 |
| 1.20.1 | Forge / Fabric | 计划中 |

**1.20.1 以下的版本不会支持。**

## 依赖

### 必须依赖

| 依赖 | 版本 |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.241 或更高 |

### 可选依赖

| 依赖 | 版本 | 作用 |
|---|---|---|
| JEI | 19 或更高 | 让 JEI 界面正确避让本模组的槽位区域。不装照常运行 |

## 客户端与服务端

**两端都必须安装本模组。** 方块、菜单与网络包在服务端注册，渲染、HUD 与配置在客户端；只装一边会出现连接失败或显示异常。

## 兼容性

| 模组 | 状态 |
|---|---|
| JEI | ✅ 支持（作为可选依赖，提供界面避让联动） |
| Jade | ✅ 兼容 |
| Sodium | ✅ 兼容 |
| Lithium | ✅ 兼容 |
| OptiFine | ❌ 不支持 |

本模组**放弃对 OptiFine 的支持**，请改用 Sodium 等现代渲染优化模组。

## 安装

1. 安装 Minecraft 1.21.1 与 NeoForge 21.1.241 或更高版本。
2. 把模组 jar 放进 `mods/` 文件夹。
3. 客户端与服务端**都要放**。

## 下载

从 [Releases](https://github.com/FunnyB254/Cold-Weapon-Craftsmanship/releases) 页面获取最新版本。

> 🚧 **尚未完成**：该页面目前还没有任何发布版本。

## Wiki

> 🚧 **尚未完成**：Wiki 尚未建立，链接先占位：https://github.com/FunnyB254/Cold-Weapon-Craftsmanship/wiki

## 配置

本模组只有一个**客户端**配置，落在 `config/coldweaponcraftsmanship-client.toml`，也可以直接在游戏内「模组列表 → 冷兵器工艺 → 配置」里改。

| 项 | 取值范围 | 默认 | 说明 |
|---|---|---|---|
| 停顿 | 0–5 秒 | 1.0 | 放不下的滚动文字出现后，先停这么久才开始滚。0 = 不停顿 |
| 滚动速度 | 4–60 像素/秒 | 17.0 | 放不下的滚动文字的循环滚动速度 |

这两项只影响**零件数值浮窗**与**装配台槽位名**里放不下的长文字，不改变任何战斗数值。

## 整合包

**允许并鼓励**在整合包中使用本模组，无需事先申请。若要在整合包描述里标注，附上本仓库链接即可。

## 贡献

参与开发前请先读 [CONTRIBUTING.md](CONTRIBUTING.md)。以下行为**必须**遵从其中的约定：

- 提交 Pull Request 时的代码风格与提交信息格式
- 数据定义（零件、类型、贴图）的写法
- 改动完成后如何验证

> 🚧 **尚未完成**：CONTRIBUTING.md 目前是空文件，上述约定待写；在那之前请以现有代码与 [docs/part-format.md](docs/part-format.md) 为准。

作为扩展作者添加零件、类型或行为时，格式规格见 [docs/part-format.md](docs/part-format.md)。

## 许可证

本模组采用**混合许可证**：

| 部分 | 许可证 | 全文 |
|---|---|---|
| 精灵图与调色板（[sprite](src/main/resources/assets/coldweaponcraftsmanship/cwc/sprite/)、[palette](src/main/resources/assets/coldweaponcraftsmanship/cwc/palette/)） | All Rights Reserved | [LICENSE_TEXTURES](LICENSE_TEXTURES) |
| 其余部分（代码、数据定义、语言文件等） | MIT | [LICENSE_CODE](LICENSE_CODE) |

**严禁二次分发精灵图与调色板。** 这些资产不允许被重新上传、打包进其他项目，或以任何形式再分发。

本项目使用 **Mojang 的官方映射**。精灵图与调色板中的大多数**重度基于 Minecraft 自身的资产**——在 Minecraft 模组开发环境之外使用其中任何内容是一个危险的想法，请不要这么做。

## 反馈与社区

| 渠道 | 地址 |
|---|---|
| GitHub Issues | https://github.com/FunnyB254/Cold-Weapon-Craftsmanship/issues |
| Bilibili | https://space.bilibili.com/1159006393 |
| Modrinth | 尚未注册 |
| CurseForge | 尚未注册 |
