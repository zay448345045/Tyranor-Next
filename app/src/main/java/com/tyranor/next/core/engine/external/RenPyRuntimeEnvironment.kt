package com.tyranor.next.core.engine.external

import android.os.Environment
import com.tyranor.next.core.settings.EngineSettingsStore
import org.json.JSONObject
import java.io.File

/**
 * Ren'Py 外置模块的 `configuration.json` 管理（策略 A）。
 *
 * 插件读取路径（`PythonSDLActivity.loadConfig`）：
 * - 游戏目录在外部存储根下 → `<gameFolder>/configuration.json`
 * - 否则 → `/sdcard/JoiPlay/games/<gameId>/configuration.json`
 *
 * 插件加载顺序为 intent `settings` 之后且同键优先，因此文件存在时必须以 App 生效值同步，
 * 否则旧值会静默覆盖用户在设置页的选择。策略 A 不创建新文件，保持与 JoiPlay 行为一致。
 */
object RenPyRuntimeEnvironment {

    /** 受管键：与插件 `RenPyConfigurationParser.loadFromFile` 的扁平格式一致。 */
    fun applyManagedKeys(json: JSONObject, settings: EngineSettingsStore.RenPy) {
        json.put("cheats", settings.cheats)
        json.put("renpy_hw_video", settings.hwVideo)
        json.put("renpy_autosave", settings.autosave)
        json.put("renpy_phonesmallvariant", settings.phoneSmallVariant)
        json.put("renpy_vsync", settings.vsync)
        json.put("renpy_less_memory", settings.lessMemory)
        json.put("renpy_less_updates", settings.lessUpdates)
        json.put("renpy_dont_use_gl2", settings.dontUseGl2)
        json.put("renpy_recompile", settings.recompile)
    }

    /** 插件实际会读取的配置文件路径（与插件二选一逻辑一致）。 */
    fun configFileFor(gameFolder: String, gameId: String): File {
        val externalRoot = Environment.getExternalStorageDirectory()
        val folder = gameFolder.trimEnd('/')
        return if (folder.startsWith(externalRoot.absolutePath)) {
            File(folder, "configuration.json")
        } else {
            File(externalRoot, "JoiPlay/games/$gameId/configuration.json")
        }
    }

    /**
     * 文件存在且为 JSON 对象时同步受管键并写回，返回是否发生写入；
     * 文件不存在或非 JSON 时不动（非致命，交由插件按默认值处理）。
     */
    fun syncConfigurationFile(file: File, settings: EngineSettingsStore.RenPy): Boolean {
        if (!file.isFile) return false
        val json = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return false
        applyManagedKeys(json, settings)
        return runCatching { file.writeText(json.toString()) }.isSuccess
    }
}
