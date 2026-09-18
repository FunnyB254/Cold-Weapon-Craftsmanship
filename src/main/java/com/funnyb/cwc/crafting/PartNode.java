package com.funnyb.cwc.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 装配树的一个节点——"零件 id + 该零件各槽位装的子节点"。
 * <p>
 * 这是 {@code CwcDataComponents.ASSEMBLED_SLOTS} 的值类型，替代原先直接存 {@link net.minecraft.world.item.ItemStack}
 * 的做法。**换掉的原因是值语义**：{@code ItemStack} 没有覆写 {@code equals}（身份比较），一旦把它塞进组件值，
 * 整份组件的相等性就失效——同一把武器经过一次网络反序列化后内层实例全换，{@code ItemStack.matches} 恒假。
 * 这连锁出三个症状（2026-09-17 修）：
 * <ul>
 *   <li>{@code ItemInHandRenderer} 认领不了新实例 → 开/关物品栏时武器重复"掏出"；</li>
 *   <li>{@code CwcClientEvents.mainHandSettled} 判成"刚换武器" → 每次掉耐久的命中后白丢一次攻击；</li>
 *   <li>{@code AssemblingMenu} 的变更检测恒为真 → 每次容器变化都全量重算 + 多发同步包。</li>
 * </ul>
 * record 的 {@code equals} 逐字段成立（{@code id} 是 String，{@code children} 递归下去也成立），三个症状一并消失。
 * <p>
 * <b>为什么是递归结构而不是扁平的"槽位名 → id"映射：</b>装配树本来就允许嵌套——装配台底座槽接受任意零件，
 * 正是为了拼出"刃+镡"这类子装配体再整体插进手柄的 blade 槽。扁平映射没有地方放子件的子树。
 *
 * @param id       零件完整标识，如 {@code cwc.standard_blade.iron}
 * @param children 槽位名 → 该槽位所装子节点；空表示这个零件上没装东西
 */
public record PartNode(String id, Map<String, PartNode> children) {

    /**
     * 装配树深度上限——**防的是客户端可控数据**。
     * <p>
     * 这个组件会经 {@code ServerboundSetCreativeModeSlotPacket} 从客户端收到任意 ItemStack，
     * 深层嵌套的解码是递归的，不设限可被恶意数据打爆栈。数据包上另配了每节点槽位数上限。
     * 正常玩法拼不出这么深（现有 8 个类型的约束把玩家封在深度 3 以内）。
     */
    public static final int MAX_DEPTH = 16;

    /** 单个节点的槽位数上限——现有类型最多 2 个槽，留足余量即可 */
    public static final int MAX_SLOTS_PER_NODE = 16;

    public PartNode {
        // 不可变 + 保持插入序：不可变防别名（组件值会被多份栈共享），插入序让序列化结果稳定
        children = Collections.unmodifiableMap(new LinkedHashMap<>(children));
    }

    /** 持久化编解码器。{@link Codec#recursive} 是必须的——{@code children} 的值类型就是本类自身 */
    public static final Codec<PartNode> CODEC = Codec.recursive(
            "CwcPartNode",
            self -> RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.fieldOf("id").forGetter(PartNode::id),
                    Codec.unboundedMap(Codec.STRING, self)
                            .optionalFieldOf("children", Map.of()).forGetter(PartNode::children)
            ).apply(instance, PartNode::new)));

    /**
     * 网络编解码器——手写递归而非走 {@code ByteBufCodecs.fromCodec}（NBT 桥接），
     * 因为这里要**自己夹深度与槽位数**：NBT 路径自带 2MB 配额兜底，手写路径没有。
     * 解码时超限直接抛，让上层把这份非法数据当解码失败处理，而不是递归到栈溢出。
     */
    public static final StreamCodec<ByteBuf, PartNode> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PartNode decode(ByteBuf buf) {
            return decode(buf, 0);
        }

        private static PartNode decode(ByteBuf buf, int depth) {
            if (depth > MAX_DEPTH) {
                throw new IllegalArgumentException("装配树深度超过 " + MAX_DEPTH + "，拒绝解码");
            }
            String id = ByteBufCodecs.STRING_UTF8.decode(buf);
            int count = ByteBufCodecs.VAR_INT.decode(buf);
            if (count < 0 || count > MAX_SLOTS_PER_NODE) {
                throw new IllegalArgumentException("槽位数非法：" + count);
            }
            Map<String, PartNode> children = new LinkedHashMap<>(Math.max(4, count * 2));
            for (int i = 0; i < count; i++) {
                children.put(ByteBufCodecs.STRING_UTF8.decode(buf), decode(buf, depth + 1));
            }
            return new PartNode(id, children);
        }

        @Override
        public void encode(ByteBuf buf, PartNode node) {
            ByteBufCodecs.STRING_UTF8.encode(buf, node.id());
            ByteBufCodecs.VAR_INT.encode(buf, node.children().size());
            for (Map.Entry<String, PartNode> entry : node.children().entrySet()) {
                ByteBufCodecs.STRING_UTF8.encode(buf, entry.getKey());
                encode(buf, entry.getValue());
            }
        }
    };

    /** 该节点在某槽位下的子节点，没有则 null */
    public PartNode child(String slotName) {
        return children.get(slotName);
    }

    /** 是否装了任何子节点 */
    public boolean hasChildren() {
        return !children.isEmpty();
    }
}
