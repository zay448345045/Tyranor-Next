package com.tyranor.next.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverScrapeMergeTest {
    private val original = ScanGame(
        title = "原始标题",
        uri = "content://games/first",
        engine = EngineType.KIRIKIRI,
        launchTarget = "data.xp3",
    )

    @Test
    fun acceptsScrapedCoverAndPreservesConcurrentNonCoverFields() {
        val current = original.copy(title = "用户重命名")
        val scraped = original.copy(coverUri = "file:///new.jpg", coverSource = "vndb")

        val merged = mergeScrapedCover(current, original, scraped)

        assertEquals("用户重命名", merged.title)
        assertEquals("file:///new.jpg", merged.coverUri)
        assertEquals("vndb", merged.coverSource)
    }

    @Test
    fun rejectsScrapedCoverWhenUserChangedCoverMeanwhile() {
        val current = original.copy(coverUri = "file:///custom.png", coverSource = "custom")
        val scraped = original.copy(coverUri = "file:///remote.jpg", coverSource = "vndb")

        val merged = mergeScrapedCover(current, original, scraped)

        assertEquals("file:///custom.png", merged.coverUri)
        assertEquals("custom", merged.coverSource)
    }
}
