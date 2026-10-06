package com.gigabytegrove.monita.messages

import android.app.Activity
import androidx.lifecycle.ViewModel
import coil.target.Target
import com.gigabytegrove.monita.Settings
import com.gigabytegrove.monita.api.Api
import com.gigabytegrove.monita.api.ApiException
import com.gigabytegrove.monita.api.ClientFactory
import com.gigabytegrove.monita.api.MonitaApi
import com.gigabytegrove.monita.api.MonitaCapabilities
import com.gigabytegrove.monita.client.api.MessageApi
import com.gigabytegrove.monita.client.model.Application
import com.gigabytegrove.monita.messages.provider.ApplicationHolder
import com.gigabytegrove.monita.messages.provider.MessageFacade
import com.gigabytegrove.monita.messages.provider.MessageState
import org.tinylog.kotlin.Logger

internal class MessagesModel(parentView: Activity) : ViewModel() {
    val settings = Settings(parentView)
    val client = ClientFactory.clientToken(settings)
    val appsHolder = ApplicationHolder(parentView, client)
    val messageApi = client.createService(MessageApi::class.java)
    val messages = MessageFacade(messageApi, appsHolder)
    val monitaApi = client.createService(MonitaApi::class.java)

    @Volatile
    var monitaCapabilities: MonitaCapabilities? = null
        private set

    @Volatile
    var monitaCapabilitiesChecked = false
        private set

    fun detectMonitaCapabilities(): MonitaCapabilities? {
        if (monitaCapabilitiesChecked) return monitaCapabilities
        monitaCapabilities = try {
            Api.execute(monitaApi.capabilities())
        } catch (e: ApiException) {
            if (e.code != 404) {
                Logger.warn(e, "Could not query Monita capabilities")
            }
            null
        } finally {
            monitaCapabilitiesChecked = true
        }
        return monitaCapabilities
    }

    fun currentApplication(): Application? =
        appsHolder.get().firstOrNull { it.id == appId }

    fun isMonitaServer(): Boolean =
        monitaCapabilities != null ||
            appsHolder.get().any {
                it.ownerId != null ||
                    it.autoAssign != null ||
                    it.allowMemberPost != null ||
                    it.receiveNotifications != null ||
                    it.channelType != null
            }

    // we need to keep the target references otherwise they get gc'ed before they can be called.
    val targetReferences = mutableListOf<Target>()

    var appId = MessageState.ALL_MESSAGES
}
