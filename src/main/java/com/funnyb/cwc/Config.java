package com.funnyb.cwc;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 模组配置。
 * <p>
 * 类型是 <b>CLIENT</b>（见 {@code ColdWeaponCraftsmanship} 的注册）：这里两项都只影响客户端渲染，
 * 而 CLIENT 配置只在客户端加载、也永不参与同步，所以专用服务器上不会多出一个用不到的文件。
 * 落在 {@code config/coldweaponcraftsmanship-client.toml}。
 * <p>
 * 注意"注册"与"加载"是两件事：注册两边都会走一遍，**加载**由 NeoForge 按 dist（{@code CommonModLoader}）
 * 把关——所以这个类在服务端是安全的，但它里面只有客户端会读的值。
 * <p>
 * 值的路径（{@code *_PATH}）也导出成常量，是因为设置界面那边要按路径查"步进 / 单位"，
 * 写死字符串的话两边会漂。
 */
public class Config {

    /** {@code dwell_seconds} 的路径 */
    public static final String DWELL_SECONDS_PATH = "dwell_seconds";
    /** {@code scroll_pixels_per_second} 的路径 */
    public static final String SCROLL_SPEED_PATH = "scroll_pixels_per_second";

    private static final double DWELL_SECONDS_DEFAULT = 1.0;
    private static final double SCROLL_SPEED_DEFAULT = 17.0;

    /** 放不下的滚动文字出现后，先停多久才开滚（秒）。0 = 不停顿，设置界面那一行会显示"关"。 */
    public static final ModConfigSpec.DoubleValue DWELL_SECONDS;
    /** 放不下的滚动文字的循环滚动速度（像素/秒）。 */
    public static final ModConfigSpec.DoubleValue SCROLL_PIXELS_PER_SECOND;

    static final ModConfigSpec SPEC;

    static {
        final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        DWELL_SECONDS = builder
                .comment("滚动文字出现后先停多久才开滚，单位秒。0 = 不停顿。")
                .defineInRange(DWELL_SECONDS_PATH, DWELL_SECONDS_DEFAULT, 0.0, 5.0);
        SCROLL_PIXELS_PER_SECOND = builder
                .comment("滚动文字放不下时的循环滚动速度，单位像素/秒。")
                .defineInRange(SCROLL_SPEED_PATH, SCROLL_SPEED_DEFAULT, 4.0, 60.0);
        SPEC = builder.build();
    }

    private Config() {}

    /**
     * 浮窗的停顿（秒）。
     * <p>
     * 配置尚未加载时回落到默认值：{@code ConfigValue.get()} 在未加载时返回 {@code null}，
     * 直接拆箱就是 NPE，而这个值的调用点在**每帧的渲染路径**上——崩在这里是整屏黑。
     */
    public static double dwellSeconds() {
        return SPEC.isLoaded() ? DWELL_SECONDS.getAsDouble() : DWELL_SECONDS_DEFAULT;
    }

    /** 浮窗的滚动速度（像素/秒）。未加载时的回落理由同 {@link #dwellSeconds()}。 */
    public static double scrollPixelsPerSecond() {
        return SPEC.isLoaded() ? SCROLL_PIXELS_PER_SECOND.getAsDouble() : SCROLL_SPEED_DEFAULT;
    }
}
