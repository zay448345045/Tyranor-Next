package com.tyranor.next.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RpgMakerModLaunchPolicyTest {
    @Test
    fun followsGlobalDefaultForRpgMaker() {
        assertTrue(effectiveRpgMakerModEnabled(EngineType.RPG_MV, null, true))
        assertFalse(effectiveRpgMakerModEnabled(EngineType.RPG_MZ, null, false))
    }

    @Test
    fun perGameOverrideWins() {
        assertFalse(effectiveRpgMakerModEnabled(EngineType.RPG_MV, false, true))
        assertTrue(effectiveRpgMakerModEnabled(EngineType.RPG_MZ, true, false))
    }

    @Test
    fun neverEnablesForOtherWebEngines() {
        assertFalse(effectiveRpgMakerModEnabled(EngineType.TYRANO, true, true))
        assertFalse(effectiveRpgMakerModEnabled(EngineType.WEB_OTHER, true, true))
    }
}
