package com.tyranor.next.ui.pages

import com.tyranor.next.settings.AppSettingsStore

internal fun coverSourceTitle(source: String): String = when (source) {
    AppSettingsStore.COVER_SOURCE_HIKARINAGI -> "Hikarinagi"
    AppSettingsStore.COVER_SOURCE_BANGUMI -> "Bangumi"
    AppSettingsStore.COVER_SOURCE_STEAM -> "Steam"
    AppSettingsStore.COVER_SOURCE_VNDB -> "VNDB"
    AppSettingsStore.COVER_SOURCE_LOCAL -> "本地封面"
    AppSettingsStore.COVER_SOURCE_CUSTOM -> "自定义封面"
    else -> source
}
