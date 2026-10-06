package com.gigabytegrove.monita.messages.provider

import com.gigabytegrove.monita.client.model.Message

internal class MessageState {
    var appId = 0L
    var loaded = false
    var hasNext = false
    var nextSince = 0L
    var messages = mutableListOf<Message>()

    companion object {
        const val ALL_MESSAGES = -1L
    }
}
