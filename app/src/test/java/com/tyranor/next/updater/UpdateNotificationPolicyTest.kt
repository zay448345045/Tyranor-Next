package com.tyranor.next.updater

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateNotificationPolicyTest {
    @Test
    fun firstNewVersionShouldNotify() {
        assertTrue(shouldNotifyUpdate("1.18", null))
    }

    @Test
    fun sameVersionDoesNotNotifyTwice() {
        assertFalse(shouldNotifyUpdate("1.18", "1.18"))
    }

    @Test
    fun laterVersionCanNotifyAgain() {
        assertTrue(shouldNotifyUpdate("1.19", "1.18"))
    }

    @Test
    fun blankVersionNeverNotifies() {
        assertFalse(shouldNotifyUpdate("", null))
    }
}
