package com.gigabytegrove.monita.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.gigabytegrove.monita.BuildConfig
import com.gigabytegrove.monita.CoilInstance
import com.gigabytegrove.monita.MarkwonFactory
import com.gigabytegrove.monita.MissedMessageUtil
import com.gigabytegrove.monita.NotificationSupport
import com.gigabytegrove.monita.R
import com.gigabytegrove.monita.Settings
import com.gigabytegrove.monita.Utils
import com.gigabytegrove.monita.api.Api
import com.gigabytegrove.monita.api.Callback
import com.gigabytegrove.monita.api.ClientFactory
import com.gigabytegrove.monita.api.MonitaApi
import com.gigabytegrove.monita.client.api.ApplicationApi
import com.gigabytegrove.monita.client.api.MessageApi
import com.gigabytegrove.monita.client.model.Application
import com.gigabytegrove.monita.client.model.Message
import com.gigabytegrove.monita.log.LoggerHelper
import com.gigabytegrove.monita.log.UncaughtExceptionHandler
import com.gigabytegrove.monita.messages.Extras
import com.gigabytegrove.monita.messages.IntentUrlDialogActivity
import com.gigabytegrove.monita.messages.MessagesActivity
import io.noties.markwon.Markwon
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import org.tinylog.kotlin.Logger

internal class WebSocketService : Service() {
    companion object {
        private val castAddition = if (BuildConfig.DEBUG) ".DEBUG" else ""
        val NEW_MESSAGE_BROADCAST = "${WebSocketService::class.java.name}.NEW_MESSAGE$castAddition"
        private const val NOT_LOADED = -2L
        private const val MAX_NOTIFICATION_IMAGE_BYTES = 8 * 1024 * 1024
        private const val MAX_NOTIFICATION_IMAGE_DIMENSION = 1440
    }

    private lateinit var settings: Settings
    private lateinit var monitaApi: MonitaApi
    private var connection: WebSocketConnection? = null
    private var networkCallbackRegistered = false
    private val networkCallback: ConnectivityManager.NetworkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                Logger.info("WebSocket: default network available; rebuilding connection on the active network.")
                connection?.reconnectForNetworkChange()
            }
        }
    private val appIdToApp = ConcurrentHashMap<Long, Application>()
    @Volatile
    private var currentMonitaUserId: Long? = null

    private val lastReceivedMessage = AtomicLong(NOT_LOADED)
    private lateinit var missingMessageUtil: MissedMessageUtil

    private lateinit var markwon: Markwon

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        val client = ClientFactory.clientToken(settings)
        monitaApi = client.createService(MonitaApi::class.java)
        missingMessageUtil = MissedMessageUtil(client.createService(MessageApi::class.java))
        Logger.info("Create ${javaClass.simpleName}")
        markwon = MarkwonFactory.createForNotification(this, CoilInstance.get(this))
    }

    override fun onDestroy() {
        connection?.close()
        if (networkCallbackRegistered && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching {
                (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager)
                    .unregisterNetworkCallback(networkCallback)
            }
            networkCallbackRegistered = false
        }
        super.onDestroy()

        Logger.warn("Destroy ${javaClass.simpleName}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LoggerHelper.init(this)
        UncaughtExceptionHandler.registerCurrentThread()

        connection?.close()
        Logger.info("Starting ${javaClass.simpleName}")
        super.onStartCommand(intent, flags, startId)
        Thread { startPushService() }.start()

        return START_STICKY
    }

    private fun startPushService() {
        UncaughtExceptionHandler.registerCurrentThread()
        showForegroundNotification(getString(R.string.websocket_init))

        if (lastReceivedMessage.get() == NOT_LOADED) {
            missingMessageUtil.lastReceivedMessage { lastReceivedMessage.set(it) }
        }

        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val reconnectDelay =
            sharedPreferences.getString(
                getString(R.string.setting_key_reconnect_delay),
                null
            )?.toIntOrNull()?.toDuration(DurationUnit.SECONDS) ?: 1.minutes

        val exponentialBackoff = sharedPreferences.getBoolean(
            getString(R.string.setting_key_exponential_backoff),
            true
        )

        loadCurrentUserIdentity()
        loadApplicationsForNotifications()

        connection = WebSocketConnection(
            settings.url,
            settings.sslSettings(),
            { settings.token },
            reconnectDelay,
            exponentialBackoff
        )
            .onOpen { onOpen() }
            .onClose { onClose() }
            .onFailure { status, reconnectIn -> onFailure(status, reconnectIn) }
            .onUnauthorized { onUnauthorized() }
            .onMessage { message -> onMessage(message) }
            .onReconnected { notifyMissedNotifications() }
            .start()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !networkCallbackRegistered) {
            cm.registerDefaultNetworkCallback(networkCallback)
            networkCallbackRegistered = true
        }
    }

    private fun loadCurrentUserIdentity() {
        currentMonitaUserId = settings.monitaUserId

        runCatching {
            Api.execute(
                ClientFactory.clientToken(settings)
                    .createService(MonitaApi::class.java)
                    .currentUser()
            )
        }.onSuccess { current ->
            currentMonitaUserId = current.id
            settings.setMonitaIdentity(current.id, current.name)
        }.onFailure {
            Logger.debug("Monita current-user identity unavailable; using persisted identity if present")
        }
    }

    private fun loadApplicationsForNotifications() {
        val apps =
            runCatching {
                Api.execute(
                    ClientFactory.clientToken(settings)
                        .createService(ApplicationApi::class.java)
                        .apps
                )
            }.onFailure {
                Logger.warn(it, "Could not preload channel metadata for notifications")
            }.getOrElse {
                appIdToApp.clear()
                emptyList()
            }

        appIdToApp.clear()
        appIdToApp.putAll(apps.associateBy { it.id })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationSupport.createChannels(
                this,
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager),
                apps
            )
        }
    }

    private fun onClose() {
        showForegroundNotification(
            getString(R.string.websocket_closed),
            getString(R.string.websocket_reconnect)
        )
        ClientFactory.userApiWithToken(settings)
            .currentUser()
            .enqueue(
                Callback.call(
                    onSuccess = { doReconnect() },
                    onError = { exception ->
                        if (exception.code == 401) {
                            showForegroundNotification(
                                getString(R.string.user_action),
                                getString(R.string.websocket_closed_logout)
                            )
                        } else {
                            Logger.info(
                                "WebSocket closed but the user still authenticated, " +
                                    "trying to reconnect"
                            )
                            doReconnect()
                        }
                    }
                )
            )
    }

    private fun doReconnect() {
        connection?.scheduleReconnectNow(15.seconds)
    }

    private fun onUnauthorized() {
        Logger.warn("WebSocket authentication rejected; validating the persisted Monita client token.")

        ClientFactory.userApiWithToken(settings)
            .currentUser()
            .enqueue(
                Callback.call(
                    onSuccess = {
                        Logger.info(
                            "Persisted Monita client token is still valid; retrying WebSocket with fresh state."
                        )
                        connection?.resetBackoffAndReconnect(2.seconds)
                    },
                    onError = { exception ->
                        if (exception.code == 401) {
                            Logger.warn(
                                "Persisted Monita client token is no longer valid; user reauthentication is required."
                            )
                            showForegroundNotification(
                                getString(R.string.user_action),
                                getString(R.string.websocket_closed_logout)
                            )
                        } else {
                            Logger.info(
                                "Could not validate token because the network is still recovering; retrying shortly."
                            )
                            connection?.resetBackoffAndReconnect(5.seconds)
                        }
                    }
                )
            )
    }

    private fun onFailure(status: String, reconnectIn: Duration) {
        val title = getString(R.string.websocket_error, status)
        showForegroundNotification(
            title,
            getString(R.string.websocket_reconnect, reconnectIn.toString())
        )
    }

    private fun onOpen() {
        showForegroundNotification(getString(R.string.websocket_listening))
    }

    private fun notifyMissedNotifications() {
        val messageId = lastReceivedMessage.get()
        if (messageId == NOT_LOADED) {
            return
        }

        val messages = missingMessageUtil.missingMessages(messageId).filterNotNull()
        messages.forEach(::acceptMessage)

        val notifiable = messages.filterNot(::shouldSuppressNotification)
        if (notifiable.size > 5) {
            showGroupedNotification(notifiable)
        } else {
            notifiable.forEach(::showMessageNotification)
        }
    }

    private fun showGroupedNotification(messages: List<Message>) {
        if (messages.isEmpty()) return

        val highestPriority =
            messages.maxOfOrNull { it.priority ?: 0L } ?: 0L
        showNotification(
            NotificationSupport.ID.GROUPED,
            getString(R.string.missed_messages),
            getString(R.string.grouped_message, messages.size),
            highestPriority,
            null
        )
    }

    private fun onMessage(message: Message) {
        acceptMessage(message)
        if (shouldSuppressNotification(message)) {
            Logger.info("Suppressing self notification for message ${message.id}")
            return
        }
        showMessageNotification(message)
    }

    private fun acceptMessage(message: Message) {
        if (lastReceivedMessage.get() < message.id) {
            lastReceivedMessage.set(message.id)
        }
        broadcast(message)
    }

    private fun showMessageNotification(message: Message) {
        val imageAttachment = firstImageAttachment(message)
        val mentioned = isMentionForCurrentUser(message)
        val notificationBody =
            if (message.message.isBlank() && imageAttachment != null) "Photo"
            else message.message
        val chatImage =
            if (imageAttachment != null) loadNotificationImage(message, imageAttachment)
            else null
        val title =
            if (mentioned) {
                val sender = message.senderName?.takeIf { it.isNotBlank() } ?: message.title.orEmpty()
                if (sender.isBlank()) "You were mentioned" else "$sender mentioned you"
            } else {
                message.title ?: ""
            }
        showNotification(
            message.id,
            title,
            notificationBody,
            message.priority ?: 0L,
            message.extras,
            message.appid,
            message.senderName,
            chatImage,
            mentioned
        )
    }

    private fun isMentionForCurrentUser(message: Message): Boolean {
        val currentUserId = currentMonitaUserId ?: settings.monitaUserId ?: return false
        val extras = message.extras ?: return false
        val raw =
            extras["monita::mentionUserIds"]
                ?: return false
        val ids =
            when (raw) {
                is Iterable<*> -> raw
                is Array<*> -> raw.asIterable()
                else -> return false
            }
        return ids.any { value ->
            when (value) {
                is Number -> value.toLong() == currentUserId
                else -> value?.toString()?.toDoubleOrNull()?.toLong() == currentUserId
            }
        }
    }

    private fun firstImageAttachment(message: Message): Long? {
        val collaboration = message.collaboration ?: return null
        val attachments = collaboration["attachments"] as? List<*> ?: return null
        return attachments.firstNotNullOfOrNull { raw ->
            val attachment = raw as? Map<*, *> ?: return@firstNotNullOfOrNull null
            val contentType = attachment["contentType"]?.toString().orEmpty()
            if (!contentType.startsWith("image/", ignoreCase = true)) {
                return@firstNotNullOfOrNull null
            }
            when (val id = attachment["id"]) {
                is Number -> id.toLong()
                else -> id?.toString()?.toDoubleOrNull()?.toLong()
            }
        }
    }

    private fun loadNotificationImage(message: Message, attachmentId: Long): Bitmap? {
        if (settings.isChannelProtected(message.appid)) return null

        return runCatching {
            val body = Api.execute(monitaApi.messageAttachment(message.id, attachmentId, message.appid))
            body.use { responseBody ->
                val length = responseBody.contentLength()
                if (length <= 0 || length > MAX_NOTIFICATION_IMAGE_BYTES) return@use null
                val bytes = responseBody.bytes()
                if (bytes.size > MAX_NOTIFICATION_IMAGE_BYTES) return@use null

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@use null

                var sampleSize = 1
                while (bounds.outWidth / sampleSize > MAX_NOTIFICATION_IMAGE_DIMENSION ||
                    bounds.outHeight / sampleSize > MAX_NOTIFICATION_IMAGE_DIMENSION
                ) {
                    sampleSize *= 2
                }
                val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            }
        }.onFailure {
            Logger.warn(it, "Could not load authenticated Monita image for notification")
        }.getOrNull()
    }

    private fun shouldSuppressNotification(message: Message): Boolean =
        SelfNotificationPolicy.shouldSuppress(
            senderUserId = message.senderUserId,
            currentUserId = currentMonitaUserId ?: settings.monitaUserId,
            extras = message.extras,
            installationId = settings.installationId
        )

    private fun broadcast(message: Message) {
        val payload: Any =
            if (settings.isChannelProtected(message.appid)) {
                mapOf(
                    "id" to message.id,
                    "appid" to message.appid,
                    "protected" to true
                )
            } else {
                message
            }
        val intent =
            Intent(NEW_MESSAGE_BROADCAST)
                .setPackage(packageName)
                .putExtra("message", Utils.JSON.toJson(payload))
        sendBroadcast(intent)
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun showForegroundNotification(title: String, message: String? = null) {
        val notificationIntent = Intent(this, MessagesActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        val notificationBuilder =
            NotificationCompat.Builder(this, NotificationSupport.Channel.FOREGROUND)
        notificationBuilder.setSmallIcon(R.drawable.monita_android_monochrome)
        notificationBuilder.setOngoing(true)
        notificationBuilder.priority = NotificationCompat.PRIORITY_MIN
        notificationBuilder.setShowWhen(false)
        notificationBuilder.setWhen(0)
        notificationBuilder.setContentTitle(title)

        if (message != null) {
            notificationBuilder.setContentText(message)
            notificationBuilder.setStyle(NotificationCompat.BigTextStyle().bigText(message))
        }

        notificationBuilder.setContentIntent(pendingIntent)
        notificationBuilder.color = ContextCompat.getColor(applicationContext, R.color.colorPrimary)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationSupport.ID.FOREGROUND,
                notificationBuilder.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NotificationSupport.ID.FOREGROUND, notificationBuilder.build())
        }
    }

    private fun showNotification(
        id: Int,
        title: String,
        message: String,
        priority: Long,
        extras: Map<String, Any>?
    ) {
        showNotification(id.toLong(), title, message, priority, extras, -1L)
    }

    private fun showNotification(
        id: Long,
        title: String,
        message: String,
        priority: Long,
        extras: Map<String, Any>?,
        appId: Long,
        senderName: String? = null,
        chatImage: Bitmap? = null,
        mentioned: Boolean = false
    ) {
        var intent: Intent

        val app = appIdToApp[appId]
        val isChat = app?.isChatChannel == true
        val isProtected = appId > 0 && settings.isChannelProtected(appId)
        val safeExtras = if (isProtected) null else extras
        val safeTitle =
            if (isProtected) {
                getString(
                    if (isChat) R.string.security_protected_chat_notification_title
                    else R.string.security_protected_channel_notification_title
                )
            } else {
                title
            }
        val safeMessage =
            if (isProtected) getString(R.string.security_protected_notification_body)
            else message

        val intentUrl = Extras.getNestedValue(
            String::class.java,
            safeExtras,
            "android::action",
            "onReceive",
            "intentUrl"
        )

        if (intentUrl != null) {
            val prompt = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
                getString(R.string.setting_key_prompt_onreceive_intent),
                resources.getBoolean(R.bool.prompt_onreceive_intent)
            )
            val onReceiveIntent = if (prompt) {
                Intent(this, IntentUrlDialogActivity::class.java).apply {
                    putExtra(IntentUrlDialogActivity.EXTRA_KEY_URL, intentUrl)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            } else {
                Intent(Intent.ACTION_VIEW).apply {
                    data = intentUrl.toUri()
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
            startActivity(onReceiveIntent)
        }

        val url = Extras.getNestedValue(
            String::class.java,
            safeExtras,
            "client::notification",
            "click",
            "url"
        )

        if (url != null) {
            intent = Intent(Intent.ACTION_VIEW)
            intent.data = url.toUri()
        } else {
            intent = Intent(this, MessagesActivity::class.java).apply {
                if (appId > 0) putExtra("openChannelId", appId)
            }
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            if (appId > 0) Utils.longToInt(appId) else 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId: String
        if (!isProtected &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            NotificationSupport.areAppChannelsRequested(this) &&
            app != null
        ) {
            channelId =
                if (isChat) {
                    NotificationSupport.getChatChannelID(appId.toString())
                } else {
                    NotificationSupport.getChannelID(priority, appId.toString())
                }
            NotificationSupport.createChannelIfNonexistent(this, app, channelId)
        } else {
            channelId =
                if (isChat) {
                    NotificationSupport.Channel.CHAT_MESSAGES
                } else {
                    NotificationSupport.convertPriorityToChannel(priority)
                }
        }

        val b = NotificationCompat.Builder(this, channelId)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            showNotificationGroup(channelId, isChat)
        }

        b.setAutoCancel(true)
            .setDefaults(Notification.DEFAULT_ALL)
            .setWhen(System.currentTimeMillis())
            .setSmallIcon(if (isChat) R.drawable.monita_android_monochrome else R.drawable.monita_android_monochrome)
            .setLargeIcon(if (isProtected) null else CoilInstance.getIcon(this, app))
            .setTicker("${getString(R.string.app_name)} - $safeTitle")
            .setGroup(if (isChat) NotificationSupport.Group.CHATS else NotificationSupport.Group.MESSAGES)
            .setContentTitle(safeTitle)
            .setDefaults(Notification.DEFAULT_LIGHTS or Notification.DEFAULT_SOUND)
            .setLights(Color.rgb(37, 99, 235), 1000, 5000)
            .setColor(ContextCompat.getColor(applicationContext, R.color.colorPrimary))
            .setContentIntent(contentIntent)

        var formattedMessage = safeMessage as CharSequence
        var newMessage: String? = null
        if (Extras.useMarkdown(safeExtras)) {
            formattedMessage = markwon.toMarkdown(safeMessage)
            newMessage = formattedMessage.toString()
        }

        if (isChat) {
            val sender =
                Person.Builder()
                    .setName(if (isProtected) safeTitle else senderName?.takeIf { it.isNotBlank() } ?: safeTitle.ifBlank { app?.name ?: "Chat" })
                    .build()
            val localUser = Person.Builder().setName(getString(R.string.app_name)).build()
            b.setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentTitle(if (!isProtected && mentioned) safeTitle else sender.name)
                .setContentText(newMessage ?: safeMessage)
                .setSubText(if (isProtected) null else app?.name)
                .setStyle(
                    NotificationCompat.MessagingStyle(localUser)
                        .setConversationTitle(if (isProtected) safeTitle else app?.name ?: getString(R.string.app_name))
                        .setGroupConversation(true)
                        .addMessage(formattedMessage, System.currentTimeMillis(), sender)
                )
        } else {
            b.setContentText(newMessage ?: safeMessage)
            b.setStyle(NotificationCompat.BigTextStyle().bigText(formattedMessage))
        }

        val notificationImageUrl = Extras.getNestedValue(
            String::class.java,
            safeExtras,
            "client::notification",
            "bigImageUrl"
        )

        if (chatImage != null && !isProtected) {
            b.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(chatImage)
                    .setSummaryText(newMessage ?: safeMessage)
            )
        } else if (notificationImageUrl != null) {
            try {
                b.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(CoilInstance.getImageFromUrl(this, notificationImageUrl))
                )
            } catch (e: Exception) {
                Logger.error(e, "Error loading bigImageUrl")
            }
        }
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(Utils.longToInt(id), b.build())
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun showNotificationGroup(channelId: String, isChat: Boolean) {
        val intent = Intent(this, MessagesActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(
            this,
            channelId
        )

        val summaryText =
            getString(
                if (isChat) {
                    R.string.grouped_chat_notification_text
                } else {
                    R.string.grouped_notification_text
                }
            )

        builder.setAutoCancel(true)
            .setDefaults(Notification.DEFAULT_ALL)
            .setWhen(System.currentTimeMillis())
            .setSmallIcon(if (isChat) R.drawable.monita_android_monochrome else R.drawable.monita_android_monochrome)
            .setTicker(getString(R.string.app_name))
            .setGroup(if (isChat) NotificationSupport.Group.CHATS else NotificationSupport.Group.MESSAGES)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setContentTitle(summaryText)
            .setGroupSummary(true)
            .setContentText(summaryText)
            .setColor(ContextCompat.getColor(applicationContext, R.color.colorPrimary))
            .setContentIntent(contentIntent)

        val notificationManager = this.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(
            if (isChat) NotificationSupport.ID.CHAT_GROUPED else NotificationSupport.ID.GROUPED,
            builder.build()
        )
    }
}
