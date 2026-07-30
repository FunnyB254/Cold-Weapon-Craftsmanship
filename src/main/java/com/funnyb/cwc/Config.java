package com.funnyb.cwc;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 模组通用配置。
 * 使用 NeoForge ModConfigSpec 定义配置项，自动生成 config/coldweaponcraftsmanship-common.toml。
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static final ModConfigSpec SPEC = BUILDER.build();
}
