package com.gigabytegrove.monita.messages.provider

import com.gigabytegrove.monita.client.model.Message

internal class MessageDeletion(val message: Message, val allPosition: Int, val appPosition: Int)
