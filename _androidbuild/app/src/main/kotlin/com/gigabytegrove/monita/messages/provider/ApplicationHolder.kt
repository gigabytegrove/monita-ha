package com.gigabytegrove.monita.messages.provider

import android.app.Activity
import com.gigabytegrove.monita.Utils
import com.gigabytegrove.monita.api.Callback
import com.gigabytegrove.monita.client.ApiClient
import com.gigabytegrove.monita.client.api.ApplicationApi
import com.gigabytegrove.monita.client.model.Application

internal class ApplicationHolder(private val activity: Activity, private val client: ApiClient) {
    private var state = listOf<Application>()
    private var onUpdate: Runnable? = null
    private var onUpdateFailed: Runnable? = null

    fun wasRequested() = state.isNotEmpty()

    fun request() {
        client.createService(ApplicationApi::class.java)
            .apps
            .enqueue(
                Callback.callInUI(
                    activity,
                    onSuccess = Callback.SuccessBody { apps -> onReceiveApps(apps) },
                    onError = { onFailedApps() }
                )
            )
    }

    private fun onReceiveApps(apps: List<Application>) {
        state = apps
        if (onUpdate != null) onUpdate!!.run()
    }

    private fun onFailedApps() {
        Utils.showSnackBar(activity, "Could not request applications, see logs.")
        if (onUpdateFailed != null) onUpdateFailed!!.run()
    }

    fun get() = state

    fun onUpdate(runnable: Runnable?) {
        onUpdate = runnable
    }

    fun onUpdateFailed(runnable: Runnable?) {
        onUpdateFailed = runnable
    }
}
