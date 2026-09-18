package com.funnyb.cwc.registry;

import com.funnyb.cwc.ColdWeaponCraftsmanship;
import com.funnyb.cwc.crafting.PartDef;
import com.funnyb.cwc.crafting.PartTypeDef;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * CWC 的数据包注册表——零件类型与零件定义。
 * <p>
 * 用 datapack registry 而不是自己扫 {@code data/} 目录，是为了让**原版负责加载与同步**：
 * 定义会随配置阶段同步到客户端（早于关卡加载），所以服务端与客户端看到的是同一份数据，
 * 专用服务器上不再出现"客户端注册表为空 → 界面空、贴图错"的问题。
 * <p>
 * 另一个同等重要的收益：注册表是**公开可查**的，其他模组可以直接
 * {@code registryOrThrow(CwcRegistries.PART)} 读到零件定义。
 * <p>
 * <b>目录规则（这里有个坑，别按直觉推）：</b>文件目录是
 * {@code data/<包命名空间>/<注册表键的命名空间>/<注册表键的路径>/**.json}
 * —— 见 {@code Registries.elementsDirPath} → {@code CommonHooks.prefixNamespace}：
 * 键的命名空间**也占一层目录**（只有 {@code minecraft} 命名空间被特殊处理成不占）。
 * <p>
 * 所以键用 <b>{@code cwc}</b> 而不是模组 id：用模组 id 会得到
 * {@code data/coldweaponcraftsmanship/coldweaponcraftsmanship/cwc/part/**}
 * 这种命名空间重复两层的目录。现在键是 {@code cwc:part}，目录就是干净的
 * {@code data/<包命名空间>/cwc/part/**}。
 * <p>
 * <b>条目 id 的命名空间取自「文件」所在的命名空间</b>（{@code FileToIdConverter.fileToId}），
 * 与键的命名空间无关。所以：
 * <ul>
 *   <li>本模组的文件在 {@code data/coldweaponcraftsmanship/cwc/part/standard_blade/iron.json}
 *       → id {@code coldweaponcraftsmanship:standard_blade/iron}</li>
 *   <li>第三方把文件放进 {@code data/yourmod/cwc/part/standard_blade/mythril.json}
 *       → id {@code yourmod:standard_blade/mythril}，天然不撞车</li>
 * </ul>
 */
@EventBusSubscriber(modid = ColdWeaponCraftsmanship.MODID)
public final class CwcRegistries {

    /**
     * 数据目录用的命名空间——**刻意用短名 {@code cwc} 而不是模组 id**。
     * <p>
     * 键的命名空间会占一层目录（见类文档），用模组 id 会得到命名空间重复两层的路径。
     * 而条目 id 的命名空间取自文件、与它无关，所以用短名不影响 id。
     */
    public static final String DATA_NAMESPACE = "cwc";

    /** 零件类型注册表——目录 {@code data/<包命名空间>/cwc/part_type/**.json} */
    public static final ResourceKey<Registry<PartTypeDef>> PART_TYPE =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(DATA_NAMESPACE, "part_type"));

    /** 零件定义注册表——目录 {@code data/<包命名空间>/cwc/part/**.json} */
    public static final ResourceKey<Registry<PartDef>> PART =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(DATA_NAMESPACE, "part"));

    private CwcRegistries() {}

    /**
     * 注册两个数据包注册表。
     * <p>
     * 第三个参数（网络 codec）非 null 即触发原版同步——这里直接复用持久化 codec：
     * 数据形状两端一致，没有为网络单独瘦身的必要（46 条数据的体积可忽略）。
     */
    @SubscribeEvent
    public static void onNewRegistry(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(PART_TYPE, PartTypeDef.CODEC, PartTypeDef.CODEC);
        event.dataPackRegistry(PART, PartDef.CODEC, PartDef.CODEC);
    }
}
