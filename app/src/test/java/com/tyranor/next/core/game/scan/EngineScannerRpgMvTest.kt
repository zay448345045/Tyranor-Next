package com.tyranor.next.core.game.scan

import com.tyranor.next.core.engine.EngineType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EngineScannerRpgMvTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun detectsRpgMakerMvAtGameRootInsteadOfWwwDirectory() {
        val gameRoot = temporaryFolder.newFolder("Actual Game Name")
        val www = gameRoot.resolve("www")
        www.resolve("js").mkdirs()
        www.resolve("index.html").writeText("<html></html>")
        www.resolve("js/rpg_core.js").writeText("// RPG Maker MV")

        assertEquals(EngineType.RPG_MV, EngineScanner.detectEngine(gameRoot).engine)
        assertEquals("Actual Game Name", gameRoot.name)
    }

    @Test
    fun keepsTyranoProjectsDistinctFromRpgMakerMv() {
        val gameRoot = temporaryFolder.newFolder("Tyrano Game")
        gameRoot.resolve("tyrano").mkdirs()
        gameRoot.resolve("index.html").writeText("<html></html>")

        assertEquals(EngineType.TYRANO, EngineScanner.detectEngine(gameRoot).engine)
    }

    @Test
    fun detectsRpgMakerMzAtGameRoot() {
        val gameRoot = temporaryFolder.newFolder("MZ Game")
        val www = gameRoot.resolve("www")
        www.resolve("js").mkdirs()
        www.resolve("index.html").writeText("<html></html>")
        www.resolve("js/rmmz_core.js").writeText("// RPG Maker MZ")

        assertEquals(EngineType.RPG_MZ, EngineScanner.detectEngine(gameRoot).engine)
    }

    @Test
    fun detectsVnWebGameFromGlobalDataMarker() {
        val gameRoot = temporaryFolder.newFolder("VN Game")
        gameRoot.resolve("index.html").writeText("<html></html>")
        gameRoot.resolve("globalData.vndata").writeText("{}")

        assertEquals(EngineType.VN, EngineScanner.detectEngine(gameRoot).engine)
    }

    @Test
    fun treatsPlainIndexAsWebOther() {
        val gameRoot = temporaryFolder.newFolder("Web Game")
        gameRoot.resolve("index.html").writeText("<html></html>")

        assertEquals(EngineType.WEB_OTHER, EngineScanner.detectEngine(gameRoot).engine)
    }
}
