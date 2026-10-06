package com.gigabytegrove.monita.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfNotificationPolicyTest {
    @Test
    fun suppressesMessageTaggedByThisInstallation() {
        assertTrue(
            SelfNotificationPolicy.shouldSuppress(
                senderUserId = null,
                currentUserId = null,
                extras = mapOf(SelfNotificationPolicy.ORIGIN_EXTRA to "device-a"),
                installationId = "device-a"
            )
        )
    }

    @Test
    fun suppressesMessageFromCurrentUser() {
        assertTrue(
            SelfNotificationPolicy.shouldSuppress(
                senderUserId = 42L,
                currentUserId = 42L,
                extras = null,
                installationId = "device-a"
            )
        )
    }

    @Test
    fun allowsMessageFromAnotherUserAndDevice() {
        assertFalse(
            SelfNotificationPolicy.shouldSuppress(
                senderUserId = 43L,
                currentUserId = 42L,
                extras = mapOf(SelfNotificationPolicy.ORIGIN_EXTRA to "device-b"),
                installationId = "device-a"
            )
        )
    }

    @Test
    fun doesNotSuppressUnidentifiedRemoteMessage() {
        assertFalse(
            SelfNotificationPolicy.shouldSuppress(
                senderUserId = null,
                currentUserId = 42L,
                extras = emptyMap(),
                installationId = "device-a"
            )
        )
    }
}
