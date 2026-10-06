package com.gigabytegrove.monita.messages

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.gigabytegrove.monita.BuildConfig
import com.gigabytegrove.monita.R
import com.gigabytegrove.monita.Settings
import com.gigabytegrove.monita.Utils
import com.gigabytegrove.monita.Utils.launchCoroutine
import com.gigabytegrove.monita.api.Api
import com.gigabytegrove.monita.api.ApiException
import com.gigabytegrove.monita.api.ClientFactory
import com.gigabytegrove.monita.api.MonitaApi
import com.gigabytegrove.monita.api.MonitaAssignmentMutation
import com.gigabytegrove.monita.api.MonitaChannelGroupMutation
import com.gigabytegrove.monita.api.MonitaChannelMemberMutation
import com.gigabytegrove.monita.api.MonitaChannelMutation
import com.gigabytegrove.monita.api.MonitaElevateRequest
import com.gigabytegrove.monita.api.MonitaGroupMemberMutation
import com.gigabytegrove.monita.api.MonitaGroupMutation
import com.gigabytegrove.monita.api.MonitaOwnerMutation
import com.gigabytegrove.monita.api.MonitaStatusMutation
import com.gigabytegrove.monita.api.MonitaToggle
import com.gigabytegrove.monita.api.MonitaTypingRequest
import com.gigabytegrove.monita.api.MonitaUserCreate
import com.gigabytegrove.monita.api.MonitaUserUpdate
import com.gigabytegrove.monita.client.api.ApplicationApi
import com.gigabytegrove.monita.client.api.AuthApi
import com.gigabytegrove.monita.client.api.ClientApi
import com.gigabytegrove.monita.client.api.MessageApi
import com.gigabytegrove.monita.client.model.CreateMessage
import com.gigabytegrove.monita.client.model.Message
import com.gigabytegrove.monita.databinding.ActivityMessagesBinding
import com.gigabytegrove.monita.login.LoginActivity
import com.gigabytegrove.monita.security.AppUnlockSession
import com.gigabytegrove.monita.security.DeviceAuthMode
import com.gigabytegrove.monita.security.DeviceAuthenticationException
import com.gigabytegrove.monita.security.DeviceAuthenticator
import com.gigabytegrove.monita.service.MonitaEventConnection
import com.gigabytegrove.monita.service.WebSocketService
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.tinylog.kotlin.Logger

internal class MessagesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMessagesBinding
    private lateinit var settings: Settings

    private val client by lazy { ClientFactory.clientToken(settings) }
    private val applicationApi by lazy { client.createService(ApplicationApi::class.java) }
    private val messageApi by lazy { client.createService(MessageApi::class.java) }
    private val monitaApi by lazy { client.createService(MonitaApi::class.java) }
    private var monitaEventConnection: MonitaEventConnection? = null
    private var uiStarted = false
    private var appUnlockInProgress = false
    private val unlockedProtectedChannels = mutableSetOf<Long>()
    private var visibleProtectedChannelId: Long? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = filePathCallback ?: return@registerForActivityResult
            filePathCallback = null
            callback.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            )
        }

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val messageJson = intent.getStringExtra("message") ?: return
            emitEvent("message", messageJson)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        settings = Settings(this)
        binding = ActivityMessagesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemInsets()
        Logger.info("Entering " + javaClass.simpleName)

        if (settings.appLockEnabled && !AppUnlockSession.isUnlocked()) {
            binding.monitaWebview.visibility = View.INVISIBLE
        } else {
            startUiIfNeeded()
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    binding.monitaWebview.evaluateJavascript(
                        "window.Monita && window.Monita.back ? window.Monita.back() : false"
                    ) { handled ->
                        if (handled != "true") {
                            finish()
                        }
                    }
                }
            }
        )
    }

    private fun startUiIfNeeded() {
        if (uiStarted) return
        configureWebView(binding.monitaWebview)
        binding.monitaWebview.loadUrl("file:///android_asset/monita/index.html")
        uiStarted = true
    }

    private fun ensureAppUnlocked(afterUnlock: () -> Unit = {}) {
        if (appUnlockInProgress) return
        appUnlockInProgress = true

        val needsAppUnlock = settings.appLockEnabled && !AppUnlockSession.isUnlocked()
        val protectedId = visibleProtectedChannelId
        val needsChannelUnlock =
            protectedId != null &&
                settings.isChannelProtected(protectedId) &&
                protectedId !in unlockedProtectedChannels

        if (needsAppUnlock || needsChannelUnlock) {
            binding.monitaWebview.visibility = View.INVISIBLE
        }

        lifecycleScope.launch {
            try {
                if (needsAppUnlock) {
                    val appUnlocked =
                        DeviceAuthenticator.authenticate(
                            this@MessagesActivity,
                            settings.deviceAuthMode,
                            getString(R.string.security_unlock_app_title),
                            getString(R.string.security_unlock_app_subtitle)
                        )
                    if (!appUnlocked) {
                        finishAndRemoveTask()
                        return@launch
                    }
                    AppUnlockSession.unlock()
                    if (needsChannelUnlock && protectedId != null) {
                        unlockedProtectedChannels.add(protectedId)
                    }
                } else {
                    AppUnlockSession.unlock()
                }

                if (needsChannelUnlock && !needsAppUnlock && protectedId != null) {
                    val channelUnlocked =
                        DeviceAuthenticator.authenticate(
                            this@MessagesActivity,
                            settings.deviceAuthMode,
                            getString(R.string.security_unlock_channel_title),
                            getString(R.string.security_unlock_channel_subtitle)
                        )
                    if (!channelUnlocked) {
                        finishAndRemoveTask()
                        return@launch
                    }
                    unlockedProtectedChannels.add(protectedId)
                }

                binding.monitaWebview.visibility = View.VISIBLE
                startUiIfNeeded()
                afterUnlock()
            } catch (e: DeviceAuthenticationException) {
                Logger.warn(e, "Could not unlock Monita")
                finishAndRemoveTask()
            } finally {
                appUnlockInProgress = false
            }
        }
    }

    private fun applySystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = false
            allowContentAccess = true
            allowFileAccess = true
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            setSupportMultipleWindows(false)
        }

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        webView.addJavascriptInterface(NativeBridge(), "MonitaNative")
        webView.webChromeClient =
            object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    callback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: WebChromeClient.FileChooserParams?
                ): Boolean {
                    if (callback == null || fileChooserParams == null) return false
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = callback
                    return try {
                        fileChooserLauncher.launch(fileChooserParams.createIntent())
                        true
                    } catch (e: ActivityNotFoundException) {
                        Logger.warn(e, "No Android file picker is available")
                        filePathCallback?.onReceiveValue(null)
                        filePathCallback = null
                        false
                    }
                }
            }
        webView.webViewClient =
            object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return true
                    if (uri.scheme == "file") return false
                    if (uri.scheme == "http" || uri.scheme == "https") {
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }
                    return true
                }
            }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val channelId = intent.getLongExtra("openChannelId", -1L)
        if (channelId > 0L && uiStarted) {
            intent.removeExtra("openChannelId")
            emitEvent("openChannel", Utils.JSON.toJson(mapOf("channelId" to channelId)))
        }
    }

    override fun onResume() {
        super.onResume()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancelAll()

        val filter = IntentFilter(WebSocketService.NEW_MESSAGE_BROADCAST)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @SuppressLint("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }

        ensureAppUnlocked {
            if (uiStarted) emitEvent("resume", "{}")
        }
    }

    override fun onUserLeaveHint() {
        unlockedProtectedChannels.clear()
        if (settings.appLockEnabled) {
            AppUnlockSession.lock()
        }
        if (settings.appLockEnabled || visibleProtectedChannelId != null) {
            binding.monitaWebview.visibility = View.INVISIBLE
        }
        super.onUserLeaveHint()
    }

    override fun onPause() {
        runCatching { unregisterReceiver(receiver) }
        super.onPause()
    }

    override fun onDestroy() {
        monitaEventConnection?.close()
        monitaEventConnection = null
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        binding.monitaWebview.removeJavascriptInterface("MonitaNative")
        binding.monitaWebview.destroy()
        super.onDestroy()
    }

    private inner class NativeBridge {
        @JavascriptInterface
        fun request(action: String, payloadJson: String, requestId: String) {
            launchCoroutine {
                try {
                    val payload =
                        if (payloadJson.isBlank()) JsonObject()
                        else JsonParser.parseString(payloadJson).asJsonObject
                    val data = handleRequest(action, payload)
                    sendResult(requestId, true, data, null)
                } catch (e: ApiException) {
                    Logger.warn(e, "Monita mobile request failed: $action")
                    if (e.code == 401 && action != "adminUnlock") {
                        sendResult(
                            requestId,
                            false,
                            null,
                            mapOf(
                                "code" to e.code,
                                "message" to "Your Monita session has expired. Sign in again.",
                                "reauth" to true
                            )
                        )
                        withContext(Dispatchers.Main) {
                            expireSession()
                        }
                        return@launchCoroutine
                    }
                    sendResult(
                        requestId,
                        false,
                        null,
                        mapOf(
                            "code" to e.code,
                            "message" to apiErrorMessage(e),
                            "needsElevation" to (e.code == 403)
                        )
                    )
                } catch (e: Exception) {
                    Logger.error(e, "Monita mobile request failed: $action")
                    sendResult(
                        requestId,
                        false,
                        null,
                        mapOf("code" to 0, "message" to (e.message ?: "Unexpected error"))
                    )
                }
            }
        }

        @JavascriptInterface
        fun openExternal(url: String) {
            runCatching {
                val uri = Uri.parse(url)
                if (uri.scheme == "http" || uri.scheme == "https") {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
            }
        }
    }

    private suspend fun handleRequest(action: String, payload: JsonObject): Any? {
        return when (action) {
            "bootstrap" -> bootstrap()
            "securityState" -> securityState()
            "configureSecurity" -> configureSecurity(payload)
            "unlockChannel" -> unlockChannel(payload.requiredLong("appId"))
            "lockChannel" -> {
                val appId = payload.requiredLong("appId")
                unlockedProtectedChannels.remove(appId)
                if (visibleProtectedChannelId == appId) visibleProtectedChannelId = null
                mapOf("locked" to true)
            }
            "setChannelProtected" ->
                setChannelProtected(
                    payload.requiredLong("appId"),
                    payload.requiredBoolean("protected")
                )
            "messages" -> loadMessages(payload)
            "sendMessage" -> sendMessage(payload)
            "sendChatMessage" -> sendChatMessage(payload)
            "attachmentImage" -> attachmentImage(payload)
            "assignMessage" -> {
                val messageId = payload.requiredLong("messageId")
                val userId = payload.requiredLong("userId")
                Api.execute(monitaApi.assignMessage(messageId, MonitaAssignmentMutation(userId)))
            }
            "setMessageStatus" -> {
                val messageId = payload.requiredLong("messageId")
                val status = payload.requiredString("status")
                Api.execute(monitaApi.setMessageStatus(messageId, MonitaStatusMutation(status)))
            }
            "attachToMessage" -> attachToMessage(payload)
            "deleteMessage" -> {
                val current = Api.execute(monitaApi.currentUser())
                require(current.admin) { "Administrator access is required" }
                Api.execute(monitaApi.deleteMessage(payload.requiredLong("messageId")))
                mapOf("deleted" to true)
            }
            "archiveMessage" -> {
                Api.execute(monitaApi.archiveMessage(payload.requiredLong("messageId")))
                mapOf("archived" to true)
            }
            "restoreMessage" -> {
                Api.execute(monitaApi.restoreMessage(payload.requiredLong("messageId")))
                mapOf("restored" to true)
            }
            "archiveAll" -> {
                val appId = payload.optionalLong("appId")
                if (appId == null) {
                    Api.execute(monitaApi.archiveAll())
                } else {
                    Api.execute(monitaApi.archiveChannel(appId))
                }
                mapOf("archived" to true)
            }
            "restoreAll" -> {
                Api.execute(monitaApi.restoreAll())
                mapOf("restored" to true)
            }
            "setNotifications" -> {
                val appId = payload.requiredLong("appId")
                val enabled = payload.requiredBoolean("enabled")
                Api.execute(monitaApi.setNotifications(appId, MonitaToggle(enabled)))
            }
            "startMonitaEvents" -> {
                startMonitaEvents()
                mapOf("started" to true)
            }
            "mentionableUsers" -> {
                val appId = payload.requiredLong("appId")
                requireChannelUnlocked(appId)
                Api.execute(monitaApi.mentionableUsers(appId))
            }
            "typing" -> {
                val appId = payload.requiredLong("appId")
                requireChannelUnlocked(appId)
                Api.execute(
                    monitaApi.setTyping(
                        appId,
                        MonitaTypingRequest(payload.requiredBoolean("typing"))
                    )
                )
            }
            "adminOverview" -> adminOverview()
            "adminUnlock" -> adminUnlock(payload.requiredString("password"))
            "createUser" ->
                Api.execute(
                    monitaApi.createUser(
                        MonitaUserCreate(
                            name = payload.requiredString("name").trim(),
                            displayName = payload.optionalString("displayName").orEmpty().trim(),
                            admin = payload.requiredBoolean("admin"),
                            pass = payload.requiredString("password")
                        )
                    )
                )
            "updateUser" ->
                Api.execute(
                    monitaApi.updateUser(
                        payload.requiredLong("userId"),
                        MonitaUserUpdate(
                            name = payload.requiredString("name").trim(),
                            displayName = payload.optionalString("displayName").orEmpty().trim(),
                            admin = payload.requiredBoolean("admin"),
                            pass = payload.optionalString("password").orEmpty()
                        )
                    )
                )
            "deleteUser" -> {
                Api.execute(monitaApi.deleteUser(payload.requiredLong("userId")))
                mapOf("deleted" to true)
            }
            "createGroup" ->
                Api.execute(
                    monitaApi.createGroup(
                        MonitaGroupMutation(
                            name = payload.requiredString("name").trim(),
                            description = payload.optionalString("description").orEmpty().trim()
                        )
                    )
                )
            "updateGroup" ->
                Api.execute(
                    monitaApi.updateGroup(
                        payload.requiredLong("groupId"),
                        MonitaGroupMutation(
                            name = payload.requiredString("name").trim(),
                            description = payload.optionalString("description").orEmpty().trim()
                        )
                    )
                )
            "deleteGroup" -> {
                Api.execute(monitaApi.deleteGroup(payload.requiredLong("groupId")))
                mapOf("deleted" to true)
            }
            "groupMembers" ->
                Api.execute(monitaApi.groupMembers(payload.requiredLong("groupId")))
            "addGroupMember" -> {
                Api.execute(
                    monitaApi.addGroupMember(
                        payload.requiredLong("groupId"),
                        MonitaGroupMemberMutation(payload.requiredLong("userId"))
                    )
                )
                mapOf("updated" to true)
            }
            "removeGroupMember" -> {
                Api.execute(
                    monitaApi.removeGroupMember(
                        payload.requiredLong("groupId"),
                        payload.requiredLong("userId")
                    )
                )
                mapOf("updated" to true)
            }
            "createChannel" ->
                Api.execute(
                    monitaApi.createChannel(
                        MonitaChannelMutation(
                            name = payload.requiredString("name").trim(),
                            description = payload.optionalString("description").orEmpty().trim(),
                            defaultPriority = payload.optionalLong("defaultPriority") ?: 0L,
                            autoAssign = payload.optionalBoolean("autoAssign") ?: false,
                            allowMemberPost = payload.optionalBoolean("allowMemberPost") ?: false,
                            channelType = payload.optionalString("channelType") ?: "notification",
                            retentionDays = payload.optionalInt("retentionDays")
                        )
                    )
                )
            "updateChannel" ->
                Api.execute(
                    monitaApi.updateChannel(
                        payload.requiredLong("appId"),
                        MonitaChannelMutation(
                            name = payload.requiredString("name").trim(),
                            description = payload.optionalString("description").orEmpty().trim(),
                            defaultPriority = payload.optionalLong("defaultPriority") ?: 0L,
                            autoAssign = payload.optionalBoolean("autoAssign") ?: false,
                            allowMemberPost = payload.optionalBoolean("allowMemberPost") ?: false,
                            channelType = payload.optionalString("channelType") ?: "notification",
                            retentionDays = payload.optionalInt("retentionDays")
                        )
                    )
                )
            "deleteChannel" -> {
                Api.execute(monitaApi.deleteChannel(payload.requiredLong("appId")))
                mapOf("deleted" to true)
            }
            "uploadChannelImage" -> uploadChannelImage(payload)
            "deleteChannelImage" ->
                Api.execute(monitaApi.deleteChannelImage(payload.requiredLong("appId")))
            "channelMembers" ->
                Api.execute(monitaApi.members(payload.requiredLong("appId")))
            "assignableChannelUsers" ->
                Api.execute(monitaApi.assignableUsers(payload.requiredLong("appId")))
            "upsertChannelMember" ->
                Api.execute(
                    monitaApi.upsertMember(
                        payload.requiredLong("appId"),
                        MonitaChannelMemberMutation(
                            userId = payload.requiredLong("userId"),
                            receiveNotifications = payload.optionalBoolean("receiveNotifications") ?: true,
                            role = payload.optionalString("role") ?: "member"
                        )
                    )
                )
            "deleteChannelMember" -> {
                Api.execute(
                    monitaApi.deleteMember(
                        payload.requiredLong("appId"),
                        payload.requiredLong("userId")
                    )
                )
                mapOf("deleted" to true)
            }
            "transferChannelOwner" ->
                Api.execute(
                    monitaApi.transferOwner(
                        payload.requiredLong("appId"),
                        MonitaOwnerMutation(payload.requiredLong("userId"))
                    )
                )
            "assignableChannelGroups" ->
                Api.execute(monitaApi.assignableGroups(payload.requiredLong("appId")))
            "channelGroups" ->
                Api.execute(monitaApi.channelGroups(payload.requiredLong("appId")))
            "upsertChannelGroup" ->
                Api.execute(
                    monitaApi.upsertChannelGroup(
                        payload.requiredLong("appId"),
                        MonitaChannelGroupMutation(
                            groupId = payload.requiredLong("groupId"),
                            role = payload.optionalString("role") ?: "member",
                            receiveNotifications = payload.optionalBoolean("receiveNotifications") ?: true
                        )
                    )
                )
            "deleteChannelGroup" -> {
                Api.execute(
                    monitaApi.deleteChannelGroup(
                        payload.requiredLong("appId"),
                        payload.requiredLong("groupId")
                    )
                )
                mapOf("deleted" to true)
            }
            "setAutoAssign" ->
                Api.execute(
                    monitaApi.setAutoAssign(
                        payload.requiredLong("appId"),
                        MonitaToggle(payload.requiredBoolean("enabled"))
                    )
                )
            "setMemberPosting" ->
                Api.execute(
                    monitaApi.setMemberPosting(
                        payload.requiredLong("appId"),
                        MonitaToggle(payload.requiredBoolean("enabled"))
                    )
                )
            "logout" -> {
                withContext(Dispatchers.Main) { logout() }
                mapOf("loggedOut" to true)
            }
            else -> error("Unknown action: $action")
        }
    }

    private fun uploadChannelImage(payload: JsonObject): Any {
        val appId = payload.requiredLong("appId")
        val encoded = payload.requiredString("base64")
        require(encoded.length <= 8 * 1024 * 1024) { "Channel image is too large" }

        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        require(bytes.size <= 5 * 1024 * 1024) { "Channel image must be 5 MB or smaller" }

        val originalName =
            payload.requiredString("filename")
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .take(128)
        val extension = originalName.substringAfterLast('.', "").lowercase()
        val mimeType =
            when (extension) {
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "gif" -> "image/gif"
                else -> error("Channel image must be PNG, JPG, JPEG, or GIF")
            }

        val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", originalName, requestBody)
        return Api.execute(monitaApi.uploadChannelImage(appId, part))
    }

    private fun bootstrap(): Map<String, Any?> {
        val currentUser = runCatching { Api.execute(monitaApi.currentUser()) }.getOrNull()
        currentUser?.let { settings.setMonitaIdentity(it.id, it.name) }
        val applications = Api.execute(applicationApi.apps)
        val capabilities = runCatching { Api.execute(monitaApi.capabilities()) }.getOrNull()
        val messages =
            Api.execute(messageApi.getMessages(80, null)).messages
                .map(::redactProtectedMessageForUi)

        val isMonita =
            capabilities != null ||
                applications.any {
                    it.ownerId != null ||
                        it.autoAssign != null ||
                        it.allowMemberPost != null ||
                        it.receiveNotifications != null ||
                        it.channelType != null
                }

        val requestedOpenChannelId =
            intent.getLongExtra("openChannelId", -1L).takeIf { it > 0L }
        if (requestedOpenChannelId != null) {
            intent.removeExtra("openChannelId")
        }

        return mapOf(
            "serverUrl" to settings.url,
            "serverVersion" to settings.serverVersion,
            "uiBuild" to BuildConfig.UI_BUILD_ID,
            "applicationId" to BuildConfig.APPLICATION_ID,
            "versionName" to BuildConfig.VERSION_NAME,
            "user" to currentUser,
            "settingsUser" to settings.user,
            "isMonita" to isMonita,
            "capabilities" to capabilities,
            "channels" to applications,
            "messages" to messages,
            "security" to securityState(),
            "openChannelId" to requestedOpenChannelId
        )
    }

    private fun securityState(): Map<String, Any?> =
        mapOf(
            "appLockEnabled" to settings.appLockEnabled,
            "authMode" to settings.deviceAuthMode,
            "protectedChannelIds" to settings.protectedChannelIds().toList(),
            "availableModes" to
                mapOf(
                    DeviceAuthMode.BIOMETRIC to
                        DeviceAuthenticator.canAuthenticate(this, DeviceAuthMode.BIOMETRIC),
                    DeviceAuthMode.CREDENTIAL to
                        DeviceAuthenticator.canAuthenticate(this, DeviceAuthMode.CREDENTIAL),
                    DeviceAuthMode.EITHER to
                        DeviceAuthenticator.canAuthenticate(this, DeviceAuthMode.EITHER)
                )
        )

    private suspend fun configureSecurity(payload: JsonObject): Map<String, Any?> {
        val mode = payload.requiredString("authMode")
        require(
            mode == DeviceAuthMode.BIOMETRIC ||
                mode == DeviceAuthMode.CREDENTIAL ||
                mode == DeviceAuthMode.EITHER
        ) { "Unsupported authentication method" }

        val authenticated =
            DeviceAuthenticator.authenticate(
                this,
                mode,
                getString(R.string.security_confirm_change_title),
                getString(R.string.security_confirm_change_subtitle)
            )
        if (!authenticated) return mapOf("saved" to false, "cancelled" to true)

        settings.deviceAuthMode = mode
        settings.appLockEnabled = payload.requiredBoolean("appLockEnabled")
        if (settings.appLockEnabled) AppUnlockSession.unlock()

        return securityState() + mapOf("saved" to true)
    }

    private suspend fun unlockChannel(appId: Long): Map<String, Any?> {
        if (!settings.isChannelProtected(appId)) {
            visibleProtectedChannelId = null
            return mapOf("unlocked" to true, "protected" to false)
        }

        val unlocked =
            DeviceAuthenticator.authenticate(
                this,
                settings.deviceAuthMode,
                getString(R.string.security_unlock_channel_title),
                getString(R.string.security_unlock_channel_subtitle)
            )
        if (unlocked) {
            unlockedProtectedChannels.add(appId)
            visibleProtectedChannelId = appId
        }
        return mapOf("unlocked" to unlocked, "protected" to true)
    }

    private suspend fun setChannelProtected(
        appId: Long,
        protected: Boolean
    ): Map<String, Any?> {
        val authenticated =
            DeviceAuthenticator.authenticate(
                this,
                settings.deviceAuthMode,
                if (protected) {
                    getString(R.string.security_protect_channel_title)
                } else {
                    getString(R.string.security_unprotect_channel_title)
                },
                getString(R.string.security_confirm_channel_change_subtitle)
            )
        if (!authenticated) return mapOf("saved" to false, "cancelled" to true)

        settings.setChannelProtected(appId, protected)
        if (protected) {
            unlockedProtectedChannels.add(appId)
            visibleProtectedChannelId = appId
        } else {
            unlockedProtectedChannels.remove(appId)
            if (visibleProtectedChannelId == appId) visibleProtectedChannelId = null
        }
        return securityState() + mapOf("saved" to true)
    }

    private fun requireChannelUnlocked(appId: Long) {
        if (settings.isChannelProtected(appId) && appId !in unlockedProtectedChannels) {
            error("This Channel is locked. Authenticate before accessing it.")
        }
    }

    private fun redactProtectedMessageForUi(message: Message): Any {
        if (!settings.isChannelProtected(message.appid)) return message
        return mapOf(
            "id" to message.id,
            "appid" to message.appid,
            "date" to message.date.toString(),
            "priority" to (message.priority ?: 0L),
            "title" to getString(R.string.security_protected_message_title),
            "message" to getString(R.string.security_protected_message_body),
            "senderName" to null,
            "protected" to true
        )
    }

    private fun loadMessages(payload: JsonObject): Any {
        val archived = payload.get("archived")?.asBoolean ?: false
        val appId = payload.optionalLong("appId")
        val limit = payload.get("limit")?.asInt?.coerceIn(20, 200) ?: 100

        return if (archived) {
            if (appId == null) {
                val paged = Api.execute(monitaApi.archivedMessages(limit, true))
                mapOf("messages" to paged.messages.map(::redactProtectedMessageForUi))
            } else {
                requireChannelUnlocked(appId)
                Api.execute(monitaApi.archivedChannelMessages(appId, limit, true))
            }
        } else if (appId == null) {
            val paged = Api.execute(messageApi.getMessages(limit, null))
            mapOf("messages" to paged.messages.map(::redactProtectedMessageForUi))
        } else {
            requireChannelUnlocked(appId)
            Api.execute(messageApi.getAppMessages(appId, limit, null))
        }
    }

    private fun sendMessage(payload: JsonObject): Message {
        val appId = payload.requiredLong("appId")
        requireChannelUnlocked(appId)
        val text = payload.requiredString("message").trim()
        require(text.isNotEmpty()) { "Message cannot be empty" }

        return Api.execute(
            messageApi.createMessage(
                CreateMessage()
                    .appid(appId)
                    .message(text)
                    .extras(
                        mapOf(
                            "monita::android::origin" to settings.installationId
                        )
                    )
            )
        )
    }

    private fun sendChatMessage(payload: JsonObject): Message {
        val appId = payload.requiredLong("appId")
        requireChannelUnlocked(appId)
        val text = payload.optionalString("message").orEmpty().trim()
        val priority = payload.optionalLong("priority") ?: 0L
        val imageArray = payload.getAsJsonArray("images")
        val images = imageArray?.toList().orEmpty()
        require(text.isNotEmpty() || images.isNotEmpty()) {
            "Message text or at least one image is required"
        }
        require(images.size <= 8) { "A chat message can include at most 8 images" }

        var totalBytes = 0L
        val parts =
            images.map { element ->
                val image = element.asJsonObject
                val filename =
                    image.requiredString("filename")
                        .substringAfterLast('/')
                        .substringAfterLast('\\')
                        .take(128)
                val mimeType = image.requiredString("mimeType").lowercase()
                require(
                    mimeType == "image/jpeg" ||
                        mimeType == "image/png" ||
                        mimeType == "image/gif" ||
                        mimeType == "image/webp"
                ) { "Only JPEG, PNG, GIF, and WebP images are supported" }

                val encoded = image.requiredString("base64")
                require(encoded.length <= 36 * 1024 * 1024) { "Image is too large" }
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                require(bytes.isNotEmpty() && bytes.size <= 25 * 1024 * 1024) {
                    "Each image must be 25 MiB or smaller"
                }
                totalBytes += bytes.size.toLong()
                require(totalBytes <= 50L * 1024 * 1024) {
                    "Images in one chat message may total at most 50 MiB"
                }

                MultipartBody.Part.createFormData(
                    "images",
                    filename,
                    bytes.toRequestBody(mimeType.toMediaTypeOrNull()),
                )
            }

        return Api.execute(
            monitaApi.sendChatMessage(
                appId,
                text.toRequestBody("text/plain; charset=utf-8".toMediaTypeOrNull()),
                priority.toString().toRequestBody("text/plain".toMediaTypeOrNull()),
                Utils.JSON.toJson(
                    mapOf("monita::android::origin" to settings.installationId)
                ).toRequestBody("application/json".toMediaTypeOrNull()),
                parts,
            )
        )
    }

    private fun attachToMessage(payload: JsonObject): Any {
        val messageId = payload.requiredLong("messageId")
        val filename =
            payload.requiredString("filename")
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .take(180)
        require(filename.isNotBlank()) { "Attachment filename is required" }

        val encoded = payload.requiredString("base64")
        require(encoded.length <= 36 * 1024 * 1024) { "Attachment is too large" }
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        require(bytes.isNotEmpty() && bytes.size <= 25 * 1024 * 1024) {
            "Attachment must be between 1 byte and 25 MiB"
        }

        val contentType =
            payload.optionalString("mimeType")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "application/octet-stream"

        val part =
            MultipartBody.Part.createFormData(
                "attachment",
                filename,
                bytes.toRequestBody(contentType.toMediaTypeOrNull()),
            )
        return Api.execute(monitaApi.uploadMessageAttachment(messageId, part))
    }

    private fun attachmentImage(payload: JsonObject): Map<String, String> {
        val appId = payload.requiredLong("appId")
        requireChannelUnlocked(appId)
        val messageId = payload.requiredLong("messageId")
        val attachmentId = payload.requiredLong("attachmentId")

        val body = Api.execute(monitaApi.messageAttachment(messageId, attachmentId, appId))
        return body.use { responseBody ->
            val declaredLength = responseBody.contentLength()
            require(declaredLength < 0 || declaredLength <= 25L * 1024 * 1024) {
                "Image is too large to display"
            }
            val contentType = responseBody.contentType()?.toString()?.substringBefore(';').orEmpty()
            require(
                contentType == "image/jpeg" ||
                    contentType == "image/png" ||
                    contentType == "image/gif" ||
                    contentType == "image/webp"
            ) { "Attachment is not a supported image" }
            val bytes = responseBody.bytes()
            require(bytes.size <= 25 * 1024 * 1024) { "Image is too large to display" }
            mapOf(
                "contentType" to contentType,
                "base64" to Base64.encodeToString(bytes, Base64.NO_WRAP)
            )
        }
    }

    private fun adminOverview(): Map<String, Any?> {
        val current = Api.execute(monitaApi.currentUser())
        require(current.admin) { "Administrator access is required" }

        val users = Api.execute(monitaApi.adminUsers())
        val groups = Api.execute(monitaApi.adminGroups())
        val audit = Api.execute(monitaApi.audit(60))
        val channels = Api.execute(applicationApi.apps)

        return mapOf(
            "user" to current,
            "users" to users,
            "groups" to groups,
            "audit" to audit,
            "channels" to channels,
            "elevated" to !current.elevatedUntil.isNullOrBlank()
        )
    }

    private fun adminUnlock(password: String): Map<String, Any?> {
        val current = Api.execute(monitaApi.currentUser())
        require(current.admin) { "Administrator access is required" }
        val clientId = current.clientId ?: error("This server did not return the current client ID")
        require(password.isNotBlank()) { "Password is required" }

        val basicClient =
            ClientFactory.basicAuth(
                settings,
                settings.sslSettings(),
                current.name,
                password
            )
        val basicMonitaApi = basicClient.createService(MonitaApi::class.java)
        Api.execute(basicMonitaApi.elevateClient(clientId, MonitaElevateRequest(900)))

        return adminOverview()
    }

    private fun startMonitaEvents() {
        if (monitaEventConnection != null) return
        monitaEventConnection =
            MonitaEventConnection(
                settings.url,
                settings.sslSettings(),
                { settings.token }
            ) { eventJson ->
                emitEvent("monitaEvent", eventJson)
            }.also { it.start() }
    }

    private fun sendResult(
        requestId: String,
        ok: Boolean,
        data: Any?,
        error: Any?
    ) {
        val envelope =
            mapOf(
                "ok" to ok,
                "data" to data,
                "error" to error
            )
        val requestJson = Utils.JSON.toJson(requestId)
        val envelopeJson = Utils.JSON.toJson(envelope)
        binding.monitaWebview.post {
            binding.monitaWebview.evaluateJavascript(
                "window.MonitaApp && window.MonitaApp.nativeResult($requestJson, $envelopeJson)",
                null
            )
        }
    }

    private fun emitEvent(name: String, jsonPayload: String) {
        val nameJson = Utils.JSON.toJson(name)
        binding.monitaWebview.post {
            binding.monitaWebview.evaluateJavascript(
                "window.MonitaApp && window.MonitaApp.nativeEvent($nameJson, $jsonPayload)",
                null
            )
        }
    }

    private fun expireSession() {
        stopService(Intent(this, WebSocketService::class.java))
        monitaEventConnection?.close()
        monitaEventConnection = null
        settings.clearAuthentication()
        unlockedProtectedChannels.clear()
        AppUnlockSession.lock()

        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    private fun logout() {
        stopService(Intent(this, WebSocketService::class.java))

        runCatching {
            val authApi = client.createService(AuthApi::class.java)
            Api.execute(authApi.logout())
        }.onFailure {
            runCatching {
                val clients = Api.execute(client.createService(ClientApi::class.java).clients)
                val currentClient = clients.firstOrNull { it.token == settings.token }
                if (currentClient != null) {
                    Api.execute(client.createService(ClientApi::class.java).deleteClient(currentClient.id))
                }
            }
        }

        settings.clear()
        AppUnlockSession.lock()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun apiErrorMessage(e: ApiException): String {
        val body = e.body?.trim().orEmpty()
        if (body.isNotEmpty()) {
            return runCatching {
                val parsed = JsonParser.parseString(body)
                when {
                    parsed.isJsonObject && parsed.asJsonObject.has("error") ->
                        parsed.asJsonObject.get("error").asString
                    parsed.isJsonObject && parsed.asJsonObject.has("message") ->
                        parsed.asJsonObject.get("message").asString
                    else -> body.take(240)
                }
            }.getOrElse { body.take(240) }
        }
        return e.message ?: "Request failed"
    }

    private fun JsonObject.requiredLong(name: String): Long =
        get(name)?.takeUnless { it.isJsonNull }?.asLong
            ?: error("$name is required")

    private fun JsonObject.optionalLong(name: String): Long? =
        get(name)?.takeUnless { it.isJsonNull }?.asLong

    private fun JsonObject.optionalInt(name: String): Int? =
        get(name)?.takeUnless { it.isJsonNull }?.asInt

    private fun JsonObject.optionalBoolean(name: String): Boolean? =
        get(name)?.takeUnless { it.isJsonNull }?.asBoolean

    private fun JsonObject.optionalString(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.asString

    private fun JsonObject.requiredBoolean(name: String): Boolean =
        get(name)?.takeUnless { it.isJsonNull }?.asBoolean
            ?: error("$name is required")

    private fun JsonObject.requiredString(name: String): String =
        get(name)?.takeUnless { it.isJsonNull }?.asString
            ?: error("$name is required")
}
