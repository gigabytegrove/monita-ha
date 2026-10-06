package com.gigabytegrove.monita.messages.provider

import com.gigabytegrove.monita.client.model.Message

internal data class MessageWithImage(val message: Message, val image: String?)
