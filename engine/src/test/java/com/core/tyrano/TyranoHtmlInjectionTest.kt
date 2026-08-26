package com.core.tyrano

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TyranoHtmlInjectionTest {
    @Test
    fun injectsCompatibilityHookAndModResourcesBeforeBody() {
        val html = "<html><head></head><body><canvas></canvas></body></html>"
        val result = String(
            buildInjectedHtml(
                html,
                "window.compat=true;".toByteArray(),
                "<script src='/__tyranor__/mod.js'></script>",
                beforeBody = true,
            ),
            StandardCharsets.UTF_8,
        )

        assertTrue(result.indexOf("window.compat=true") < result.indexOf("/__tyranor__/mod.js"))
        assertTrue(result.indexOf("/__tyranor__/mod.js") < result.indexOf("</body>"))
    }

    @Test
    fun leavesDocumentUnchangedWhenNoInjectionIsConfigured() {
        val html = "<html><body>game</body></html>"
        assertEquals(html, String(buildInjectedHtml(html, ByteArray(0), "", true)))
    }
}
