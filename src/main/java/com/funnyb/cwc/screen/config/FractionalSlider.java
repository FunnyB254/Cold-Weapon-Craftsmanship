package com.funnyb.cwc.screen.config;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.util.function.Consumer;

/**
 * 带小数步进的滑条。
 * <p>
 * 为什么不用原版的：NeoForge 的设置界面里 {@code createSlider} 走的是
 * {@code OptionInstance.IntRange}，**步长恒为 1**，而且小数项根本不走滑条那条路
 * （{@code createDoubleValue} 一律给数字框）。想要 "1.2 秒" 这种读数就得自己接一个。
 * <p>
 * 旋钮位置 {@link AbstractSliderButton#value} 是 0..1 的连续量，步进靠
 * {@link #applyValue()} 里把它**吸附**回去实现——原版的 {@code setValue} 是 private，
 * 拦不住写入口，只能在写完之后把值掰回来（旋钮每帧从 {@code value} 重画，所以看起来是吸附的）。
 */
final class FractionalSlider extends AbstractSliderButton {

    /** 读数格式：{@code 1.0 → "1"}、{@code 1.2 → "1.2"}、{@code 17.0 → "17"}（尾随的零不显示） */
    private static final DecimalFormat VALUE_FORMAT = new DecimalFormat("#.#");

    private final double min;
    private final double max;
    private final double step;
    /** 单位语言键，形如 {@code "%s 秒"}——单位随项不同，所以不在这个控件里写死 */
    private final String unitKey;
    /** 值为 0 时改显示的键（{@code options.off}）；不需要这项的就传 null */
    @Nullable
    private final String offKey;
    private final Consumer<Double> onChanged;

    /**
     * @param initial   初始值（真实值，不是 0..1 的分数）
     * @param onChanged 吸附后的真实值变化时回调
     */
    FractionalSlider(int width, int height, double min, double max, double step,
                     String unitKey, @Nullable String offKey, double initial, Consumer<Double> onChanged) {
        super(0, 0, width, height, Component.empty(), fractionOf(initial, min, max));
        this.min = min;
        this.max = max;
        this.step = step;
        this.unitKey = unitKey;
        this.offKey = offKey;
        this.onChanged = onChanged;
        updateMessage();
    }

    /**
     * 当前值吸附到步进上之后的结果。
     * <p>
     * 以 {@code min} 为原点取整，而不是以 0 为原点——这样档位与范围的起点对齐
     * （范围是 4.0–60.0 时，档位就该是 4、5、6…，而不是 4.0、4.1…）。
     */
    private double snappedValue() {
        double raw = min + (max - min) * this.value;
        double snapped = min + Math.round((raw - min) / step) * step;
        return Math.min(max, Math.max(min, snapped));
    }

    private static double fractionOf(double value, double min, double max) {
        if (max <= min) return 0.0;
        return Math.min(1.0, Math.max(0.0, (value - min) / (max - min)));
    }

    @Override
    protected void updateMessage() {
        double value = snappedValue();
        if (offKey != null && value == 0.0) {
            // 0 就是"关"：停顿拉到 0 是不停顿，不是"停 0.0 秒"。
            // 判 0 用吸附后的值——它是 n*step 的乘积，0 是精确可表示的，`== 0.0` 稳。
            setMessage(Component.translatable(offKey));
        } else {
            setMessage(Component.translatable(unitKey, VALUE_FORMAT.format(value)));
        }
    }

    @Override
    protected void applyValue() {
        double value = snappedValue();
        // 把旋钮也拽到吸附后的位置（见类注释：setValue 是 private，只能在这儿往回写）
        this.value = fractionOf(value, min, max);
        // 原版 setValue 在调 applyValue 之前已经用**没吸附**的那个值算过一次读数和旋钮位置了，
        // 所以这里得再算一次，否则读数会显示 "1.23" 而旋钮停在 1.2
        updateMessage();
        onChanged.accept(value);
    }
}
