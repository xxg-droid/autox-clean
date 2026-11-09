package com.stardust.app;

import android.content.Context;
import android.content.res.Configuration;

/**
 * 全 App 字号放大一档：把 fontScale 统一设为 1.2。
 * <p>
 * 用绝对值赋值（不是在系统值上乘 1.2），因此在 Application、BaseActivity、
 * MainActivity 多层调用也不会叠加。字号以 sp 为单位的界面都会跟随放大，
 * 包括 Compose 界面（首页、管理、抽屉菜单）、传统 View 界面（设置页等）
 * 和脚本编辑器（EditText.setTextSize 默认单位就是 sp）；dp 布局尺寸
 * （行高、按钮大小）不受 fontScale 影响，仍按系统 density 排布。
 */
public class FontScaleUtil {

    public static final float FONT_SCALE = 1.2f;

    public static Context wrap(Context base) {
        Configuration config = new Configuration(base.getResources().getConfiguration());
        if (config.fontScale == FONT_SCALE) {
            return base;
        }
        config.fontScale = FONT_SCALE;
        return base.createConfigurationContext(config);
    }
}
