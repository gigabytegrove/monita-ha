package com.gigabytegrove.monita.messages.provider

import com.gigabytegrove.monita.api.Api
import com.gigabytegrove.monita.api.ApiException
import com.gigabytegrove.monita.api.Callback
import com.gigabytegrove.monita.client.api.MessageApi
import com.gigabytegrove.monita.client.model.Message
import com.gigabytegrove.monita.client.model.PagedMessages
import org.tinylog.kotlin.Logger

internal class MessageRequester(private val messageApi: MessageApi) {
    fun loadMore(state: MessageState): PagedMessages? {
        return try {
            Logger.info("Loading more messages for ${state.appId}")
            if (MessageState.ALL_MESSAGES == state.appId) {
                Api.execute(messageApi.getMessages(LIMIT, state.nextSince))
            } else {
                Api.execute(messageApi.getAppMessages(state.appId, LIMIT, state.nextSince))
            }
        } catch (apiException: ApiException) {
            Logger.error(apiException, "failed requesting messages")
            null
        }
    }

    fun asyncRemoveMessage(message: Message) {
        Logger.info("Removing message with id ${message.id}")
        messageApi.deleteMessage(message.id).enqueue(Callback.call())
    }

    fun deleteAll(appId: Long): Boolean {
        return try {
            Logger.info("Deleting all messages for $appId")
            if (MessageState.ALL_MESSAGES == appId) {
                Api.execute(messageApi.deleteMessages())
            } else {
                Api.execute(messageApi.deleteAppMessages(appId))
            }
            true
        } catch (e: ApiException) {
            Logger.error(e, "Could not delete messages")
            false
        }
    }

    companion object {
        private const val LIMIT = 100
    }
}
