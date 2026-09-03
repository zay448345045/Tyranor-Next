package com.tyranor.next.core.game.launch

import bridge.KrSafMirror
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KrSafMirrorPathTest {
    @Test
    fun removableStoragePathBecomesDocumentId() {
        assertEquals(
            "9C33-6BBD:Download/Tyranor",
            KrSafMirror.storagePathToDocumentId("/storage/9C33-6BBD/Download/Tyranor/"),
        )
    }

    @Test
    fun primaryStorageAliasesBecomePrimaryDocumentIds() {
        assertEquals("primary:galgame/test", KrSafMirror.storagePathToDocumentId("/storage/emulated/0/galgame/test"))
        assertEquals("primary:galgame/test", KrSafMirror.storagePathToDocumentId("/sdcard/galgame/test"))
    }

    @Test
    fun unrelatedPathIsRejected() {
        assertNull(KrSafMirror.storagePathToDocumentId("/data/user/0/game"))
    }
}
