package com.gigabytegrove.monita.service

internal object SelfNotificationPolicy {
    const val ORIGIN_EXTRA = "monita::android::origin"

    fun shouldSuppress(
        senderUserId: Long?,
        currentUserId: Long?,
        extras: Map<String, Any>?,
        installationId: String
    ): Boolean {
        val origin = extras?.get(ORIGIN_EXTRA)?.toString()
        if (!origin.isNullOrBlank() && origin == installationId) {
            return true
        }

        return senderUserId != null &&
            currentUserId != null &&
            senderUserId == currentUserId
    }
}
