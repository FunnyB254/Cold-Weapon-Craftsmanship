package com.funnyb.cwc.crafting;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * 零件解析器抽象基类。
 * id 由 PartRegistry 从文件路径推导后传入，解析器不负责生成 id。
 */
public abstract class BaseParser {

    public abstract String id();

    /** @param id 零件/类型标识，从文件路径推导 */
    public abstract PartTypeDef parseType(String id, ResourceLocation location, ResourceManager rm);

    /** @param id 零件标识，从文件路径推导 */
    public abstract PartDef parsePart(String id, ResourceLocation location, ResourceManager rm);
}
