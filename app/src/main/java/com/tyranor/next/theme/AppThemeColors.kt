package com.tyranor.next.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.tyranor.next.settings.AppSettingsStore

/**
 * 全局主题色：读写 AppSettingsStore，变化时通过 snapshot state 通知所有已组合页面
 * 即时重组（应用设置「色调轮盘」确认后全 App 生效）。
 */
object AppThemeColors {
    private var loaded = false
    var primary by mutableStateOf(Blue40)
        private set

    /** 深色模式（应用设置「外观模式」）；变化时全 App 重组。 */
    var isDark by mutableStateOf(false)
        private set

    /** 色调切换：控制页面背景色与组件色是否互换。 */
    var toneSwitchEnabled by mutableStateOf(AppSettingsStore.DEFAULT_TONE_SWITCH_ENABLED)
        private set

    /** 首次组合时从存储加载（幂等，避免每次重组都读 prefs）；
     *  跟随系统时每次组合都重读，系统深/浅切换（Activity 重建）后能立即拿到新值。 */
    fun ensureLoaded(context: Context) {
        if (!loaded || AppSettingsStore.getThemeMode(context) == AppSettingsStore.THEME_MODE_SYSTEM) {
            loaded = true
            refresh(context)
        }
    }

    /** 从存储重读主题色与外观模式并广播变更（system 模式按系统当前深/浅解析）。 */
    fun refresh(context: Context) {
        primary = parseColorHex(AppSettingsStore.getThemeColorHex(context))
        isDark = AppSettingsStore.isDarkEffective(context)
        toneSwitchEnabled = AppSettingsStore.isToneSwitchEnabled(context)
    }
}

/** 解析 #RRGGBB 为 Compose Color，非法值回退默认蓝。 */
fun parseColorHex(hex: String): Color = try {
    Color(0xFF000000 or hex.removePrefix("#").toLong(16))
} catch (t: Throwable) {
    Blue40
}
