<a name="zh"></a>

# Cold Weapon Craftsmanship · 冷兵器工艺

**简体中文** | [English](#en)

在装配台上把零件拼成一把冷兵器。零件（刃、镡、配重、手柄）与材质（木、石、铜、铁、金、钻石、下界合金）可以自由组合，装出来的武器拥有自己的伤害、攻速与耐久；它能在战斗中做什么——格挡、横扫、直击、副手短刀——同样由数据声明。零件定义、类型定义与贴图都是数据包驱动的，扩展包可以添加自己的零件、类型乃至新的战斗行为，而不必碰 Java 代码。

## AI 披露

本项目的代码**大批量由 AI 生成**。

作者为本项目做出的贡献：

- 设计了项目的架构与玩法。
- 绘制了精灵图的形状模板，AI 在其基础上着色。
- 负责了游戏内测试。

作者承担了全部 API 开销，并对项目的代码与文档质量负责。

## 支持版本

### 当前支持

| Minecraft | 加载器 | 状态 |
|---|---|---|
| 1.21.1 | NeoForge 21.1.241 或更高 | 早期开发中（`0.1.0-alpha.1`） |

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

从 [Releases](https://github.com/FunnyB254/Cold-Weapon-Craftsmanship/releases) 页面获取最新版本，
文件名形如 `coldweaponcraftsmanship-0.1.0-alpha.1.jar`。

> ⚠ 这是**早期开发版本**（`0.1.0-alpha.1`）：内容仍在填充，公开的数据格式与玩法还会变动，
> 不建议用于长期存档。

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

参与开发前请先读 [.github/CONTRIBUTING.md](.github/CONTRIBUTING.md)。以下行为**必须**遵从其中的约定：

- 提交 Pull Request 时的代码风格与提交信息格式
- 数据定义（零件、类型、贴图）的写法
- 改动完成后如何验证

> 🚧 **尚未完成**：CONTRIBUTING.md 目前是空文件，上述约定待写；在那之前请以现有代码与 [docs/part-format.md](docs/part-format.md) 为准。

作为扩展作者添加零件、类型或行为时，格式规格见 [docs/part-format.md](docs/part-format.md)。

## 许可证

本模组采用**混合许可证**，整体 SPDX 表达式为 `MIT AND LicenseRef-All-Rights-Reserved`：

| 部分 | 许可证 | 全文 |
|---|---|---|
| 精灵图与调色板（[sprite](src/main/resources/assets/coldweaponcraftsmanship/cwc/sprite/)、[palette](src/main/resources/assets/coldweaponcraftsmanship/cwc/palette/)） | All Rights Reserved | [LICENSES/LicenseRef-All-Rights-Reserved.txt](LICENSES/LicenseRef-All-Rights-Reserved.txt) |
| 其余部分（代码、数据定义、语言文件等） | MIT | [LICENSES/MIT.txt](LICENSES/MIT.txt) |

许可证全文按 [REUSE 规范](https://reuse.software/)以 SPDX 标识符命名，存放在 [LICENSES/](LICENSES/) 目录下。

**严禁二次分发精灵图与调色板。** 这些资产不允许被重新上传、打包进其他项目，或以任何形式再分发。

本项目使用 **Mojang 的官方映射**。精灵图与调色板中的大多数**重度基于 Minecraft 自身的资产**——在 Minecraft 模组开发环境之外使用其中任何内容是一个危险的想法，请不要这么做。

本项目建于 [NeoForge MDK](https://github.com/NeoForged/MDK) 之上，MDK 提供的模板文件同样以 MIT 许可证授权。

## 反馈与社区

| 渠道 | 地址 |
|---|---|
| GitHub Issues | https://github.com/FunnyB254/Cold-Weapon-Craftsmanship/issues |
| Bilibili | https://space.bilibili.com/1159006393 |
| Modrinth | 尚未注册 |
| CurseForge | 尚未注册 |

---

<a name="en"></a>

# Cold Weapon Craftsmanship

[简体中文](#zh) | **English**

Assemble a cold weapon from parts at the assembly table. Parts (blade, guard, pommel, handle) and materials (wood, stone, copper, iron, gold, diamond, netherite) combine freely, and the weapon you build gets its own damage, attack speed and durability. What it can do in combat — block, sweep, strike, offhand knife — is declared in data as well. Part definitions, type definitions and textures are all datapack-driven, so an add-on can add its own parts, types, or even new combat behaviors without touching Java code.

## AI disclosure

**The code in this project is largely AI-generated.**

What the author contributed to the project:

- Designed the architecture and gameplay.
- Drew the shape templates for the sprites; the AI colored them in.
- Handled the in-game testing.

The author covers all API costs and takes responsibility for the quality of the code and documentation.

## Supported versions

### Currently supported

| Minecraft | Loader | Status |
|---|---|---|
| 1.21.1 | NeoForge 21.1.241 or newer | Early development (`0.1.0-alpha.1`) |

### Planned

There is **no schedule** for the versions below — they depend on how content work progresses. The order does not imply a commitment to sequence.

| Minecraft | Loader | Status |
|---|---|---|
| 1.21.1 | Fabric | Planned |
| 26.1.2 | NeoForge / Fabric | Planned |
| 1.20.1 | Forge / Fabric | Planned |

**Versions below 1.20.1 will not be supported.**

## Dependencies

### Required

| Dependency | Version |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.241 or newer |

### Optional

| Dependency | Version | Purpose |
|---|---|---|
| JEI | 19 or newer | Lets JEI's screen properly avoid this mod's slot areas. Works fine without it |

## Client and server

**Both sides must have this mod installed.** Blocks, menus and network packets are registered server-side; rendering, HUD and configuration are client-side. Installing it on only one side causes connection failures or display problems.

## Compatibility

| Mod | Status |
|---|---|
| JEI | ✅ Supported (as an optional dependency, for screen-layout interop) |
| Jade | ✅ Compatible |
| Sodium | ✅ Compatible |
| Lithium | ✅ Compatible |
| OptiFine | ❌ Not supported |

This mod **does not support OptiFine** — use a modern rendering optimization mod such as Sodium instead.

## Installation

1. Install Minecraft 1.21.1 and NeoForge 21.1.241 or newer.
2. Drop the mod jar into your `mods/` folder.
3. Install it on **both** the client and the server.

## Download

Get the latest version from the [Releases](https://github.com/FunnyB254/Cold-Weapon-Craftsmanship/releases) page. The file is named like `coldweaponcraftsmanship-0.1.0-alpha.1.jar`.

> ⚠ This is an **early development build** (`0.1.0-alpha.1`): content is still being filled in, and the public data format and gameplay will keep changing. Not recommended for long-term worlds.

## Wiki

> 🚧 **Not yet available**: the Wiki has not been set up; the link is a placeholder for now: https://github.com/FunnyB254/Cold-Weapon-Craftsmanship/wiki

## Configuration

This mod has a single **client-side** config at `config/coldweaponcraftsmanship-client.toml`. You can also edit it in-game under "Mods → Cold Weapon Craftsmanship → Config".

| Option | Range | Default | Description |
|---|---|---|---|
| Dwell | 0–5 seconds | 1.0 | How long text waits before it starts scrolling. 0 = no dwell |
| Scroll speed | 4–60 pixels/second | 17.0 | How fast overflowing text scrolls |

These two options only affect long text that does not fit — in the **part stat tooltip** and the **assembly table slot names**. They do not change any combat values.

## Modpacks

You are **permitted and encouraged** to include this mod in modpacks, with no need to ask first. If you want to credit it in the pack description, just link back to this repository.

## Contributing

Please read [.github/CONTRIBUTING.md](.github/CONTRIBUTING.md) before contributing. The following **must** follow the conventions in it:

- Code style and commit message format for pull requests
- How data definitions (parts, types, textures) are written
- How to verify a change

> 🚧 **Not yet available**: CONTRIBUTING.md is currently empty; those conventions are still to be written. Until then, go by the existing code and [docs/part-format.md](docs/part-format.md).

If you are an add-on author adding parts, types or behaviors, the format specification is in [docs/part-format.md](docs/part-format.md) — **written in Chinese**.

## License

This mod uses a **split license**, expressed in SPDX as `MIT AND LicenseRef-All-Rights-Reserved`:

| Portion | License | Full text |
|---|---|---|
| Sprites and palettes ([sprite](src/main/resources/assets/coldweaponcraftsmanship/cwc/sprite/), [palette](src/main/resources/assets/coldweaponcraftsmanship/cwc/palette/)) | All Rights Reserved | [LICENSES/LicenseRef-All-Rights-Reserved.txt](LICENSES/LicenseRef-All-Rights-Reserved.txt) |
| Everything else (code, data definitions, language files, ...) | MIT | [LICENSES/MIT.txt](LICENSES/MIT.txt) |

License texts live under the [LICENSES/](LICENSES/) directory, named after their SPDX identifiers per the [REUSE specification](https://reuse.software/).

**Redistributing the sprites and palettes is strictly forbidden.** These assets may not be reuploaded, bundled into other projects, or redistributed in any form.

This project uses **Mojang's official mappings**. Most of the sprites and palettes are **heavily derived from Minecraft's own assets** — using any of it outside of a Minecraft mod development context is a bad idea. Please don't.

This project is built on the [NeoForge MDK](https://github.com/NeoForged/MDK); the template files it provides are likewise licensed under MIT.

## Feedback and community

| Channel | Address |
|---|---|
| GitHub Issues | https://github.com/FunnyB254/Cold-Weapon-Craftsmanship/issues |
| Bilibili | https://space.bilibili.com/1159006393 |
| Modrinth | Not registered yet |
| CurseForge | Not registered yet |
