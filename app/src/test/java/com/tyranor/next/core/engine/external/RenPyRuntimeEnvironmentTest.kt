package com.tyranor.next.core.engine.external

import com.tyranor.next.core.settings.EngineSettingsStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenPyRuntimeEnvironmentTest {

    @Test
    fun applyManagedKeysWritesAllFlatKeys() {
        val json = JSONObject()
        val settings = EngineSettingsStore.RenPy(
            cheats = false,
            hwVideo = false,
            autosave = true,
            phoneSmallVariant = true,
            vsync = true,
            lessMemory = true,
            lessUpdates = true,
            dontUseGl2 = true,
            recompile = true,
        )

        RenPyRuntimeEnvironment.applyManagedKeys(json, settings)

        assertEquals(false, json.getBoolean("cheats"))
        assertEquals(false, json.getBoolean("renpy_hw_video"))
        assertTrue(json.getBoolean("renpy_autosave"))
        assertTrue(json.getBoolean("renpy_phonesmallvariant"))
        assertTrue(json.getBoolean("renpy_vsync"))
        assertTrue(json.getBoolean("renpy_less_memory"))
        assertTrue(json.getBoolean("renpy_less_updates"))
        assertTrue(json.getBoolean("renpy_dont_use_gl2"))
        assertTrue(json.getBoolean("renpy_recompile"))
    }

    @Test
    fun applyManagedKeysOverwritesConflictsAndPreservesUnknownKeys() {
        val json = JSONObject()
            .put("renpy_autosave", false)
            .put("customUnmanagedKey", "keep-me")

        RenPyRuntimeEnvironment.applyManagedKeys(
            json,
            EngineSettingsStore.RenPy(autosave = true),
        )

        assertTrue(json.getBoolean("renpy_autosave"))
        assertEquals("keep-me", json.getString("customUnmanagedKey"))
    }

    @Test
    fun syncConfigurationFileSkipsMissingOrInvalidFile() {
        val missing = java.io.File("/definitely/not/here/configuration.json")
        assertFalse(RenPyRuntimeEnvironment.syncConfigurationFile(missing, EngineSettingsStore.RenPy()))
    }
}
