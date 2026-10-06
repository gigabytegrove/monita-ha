package com.gigabytegrove.monita.service

import android.os.Handler
import android.os.Looper
import com.gigabytegrove.monita.SSLSettings
import com.gigabytegrove.monita.api.CertUtils
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.tinylog.kotlin.Logger

internal class MonitaEventConnection(
    private val baseUrl: String,
    settings: SSLSettings,
    private val tokenProvider: () -> String?,
    private val onEvent: (String) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val client: OkHttpClient
    private var socket: WebSocket? = null
    private var stopped = true
    private val reconnect = Runnable { if (!stopped) connect() }

    init {
        val builder =
            OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
        CertUtils.applySslSettings(builder, settings)
        client = builder.build()
    }

    fun start() {
        if (tokenProvider().isNullOrBlank()) return
        stopped = false
        if (socket == null) connect()
    }

    fun close() {
        stopped = true
        handler.removeCallbacks(reconnect)
        socket?.close(1000, "activity closed")
        socket = null
    }

    private fun connect() {
        handler.removeCallbacks(reconnect)
        if (stopped || socket != null) return
        val httpUrl = baseUrl.toHttpUrlOrNull() ?: return
        val url =
            httpUrl.newBuilder()
                .addPathSegments("api/mu/v1/events")
                .build()
        Logger.info("MU event stream: connecting")
        val token = tokenProvider()?.trim()
        if (token.isNullOrBlank()) {
            Logger.warn("Monita event stream has no client token; stopping reconnect.")
            stopped = true
            return
        }
        val request =
            Request.Builder()
                .url(url)
                .header("X-Monita-Key", token)
                .get()
                .build()
        socket = client.newWebSocket(request, Listener())
    }

    private fun scheduleReconnect() {
        socket = null
        if (!stopped) {
            handler.removeCallbacks(reconnect)
            handler.postDelayed(reconnect, 5000)
        }
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Logger.info("MU event stream: connected")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            onEvent(text)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Logger.info("MU event stream: closed")
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (response?.code == 401) {
                Logger.warn("Monita event stream authentication rejected; waiting for app reauthentication.")
                socket = null
                stopped = true
                return
            }
            Logger.warn(t, "Monita event stream unavailable")
            scheduleReconnect()
        }
    }
}
