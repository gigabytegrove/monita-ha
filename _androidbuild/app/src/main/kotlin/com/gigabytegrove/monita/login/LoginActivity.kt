package com.gigabytegrove.monita.login

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gigabytegrove.monita.BuildConfig
import com.gigabytegrove.monita.Settings
import com.gigabytegrove.monita.Utils
import com.gigabytegrove.monita.api.CertUtils
import com.gigabytegrove.monita.databinding.ActivityLoginBinding
import com.gigabytegrove.monita.init.InitializationActivity
import com.gigabytegrove.monita.security.AppUnlockSession
import com.gigabytegrove.monita.log.LogsActivity
import com.gigabytegrove.monita.log.UncaughtExceptionHandler
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.cert.X509Certificate
import kotlinx.coroutines.launch
import org.tinylog.kotlin.Logger

internal class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var settings: Settings
    private val viewModel: LoginViewModel by viewModels { LoginViewModel.Factory(settings) }

    private var checkRequestId: String? = null
    private var loginRequestId: String? = null
    private var oidcRequestId: String? = null
    private var pendingClientName: String = Build.MODEL

    private val caDialogResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            handleCertificateResult(result.resultCode, result.data, true)
        }

    private val clientCertDialogResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            handleCertificateResult(result.resultCode, result.data, false)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        UncaughtExceptionHandler.registerCurrentThread()
        settings = Settings(this)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemInsets()
        Logger.info("Entering " + javaClass.simpleName)

        configureWebView(binding.loginWebview)
        observeLogin()
        binding.loginWebview.loadUrl("file:///android_asset/monita/login.html")
        handleOidcCallback(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOidcCallback(intent)
    }

    private fun applySystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    @Suppress("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = false
            allowContentAccess = false
            allowFileAccess = true
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            setSupportMultipleWindows(false)
        }
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        webView.addJavascriptInterface(LoginBridge(), "MonitaLoginNative")
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

    private fun observeLogin() {
        viewModel.state.observe(this) { loginState ->
            when (loginState) {
                LoginState.UrlInput -> emitState("url")
                LoginState.CheckingUrl -> emitState("checking")
                LoginState.Ready -> {
                    emitState("ready")
                    checkRequestId?.let { id ->
                        checkRequestId = null
                        sendResult(id, true, infoMap(), null)
                    }
                }
                LoginState.LoggingIn,
                LoginState.WaitingForClientName,
                LoginState.CreatingClient -> emitState("loggingIn")
                LoginState.OidcAuthorizing,
                LoginState.OidcWaitingForCallback,
                LoginState.OidcExchangingToken -> emitState("oidc")
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { loginEvent ->
                    when (loginEvent) {
                        is LoginEvent.OpenBrowser -> {
                            oidcRequestId?.let { id ->
                                oidcRequestId = null
                                sendResult(id, true, mapOf("opened" to true), null)
                            }
                            startActivity(Intent(Intent.ACTION_VIEW, loginEvent.url.toUri()))
                        }
                        LoginEvent.LoginSuccess -> {
                            loginRequestId?.let { id ->
                                loginRequestId = null
                                sendResult(id, true, mapOf("authenticated" to true), null)
                            }
                            AppUnlockSession.unlock()
                            startActivity(Intent(this@LoginActivity, InitializationActivity::class.java))
                            finish()
                        }
                        LoginEvent.ShowClientNameDialog -> {
                            viewModel.createClient(pendingClientName.ifBlank { Build.MODEL })
                        }
                        is LoginEvent.VersionError -> {
                            failCheck("Server check failed with HTTP " + loginEvent.code + ".")
                        }
                        is LoginEvent.VersionException -> {
                            failCheck(loginEvent.message.ifBlank { "Could not reach the server." })
                        }
                        LoginEvent.InvalidCredentials -> {
                            failLogin("The username or password is incorrect.")
                        }
                        LoginEvent.ClientCreationFailed -> {
                            failLogin("The device client could not be created.")
                        }
                        LoginEvent.OidcAuthorizeFailed -> {
                            failOidc("Single sign-on could not be started.")
                        }
                        LoginEvent.OidcTokenExchangeFailed -> {
                            failOidc("Single sign-on could not be completed.")
                        }
                    }
                }
            }
        }
    }

    private inner class LoginBridge {
        @JavascriptInterface
        fun request(action: String, payloadJson: String, requestId: String) {
            runOnUiThread {
                try {
                    val payload =
                        if (payloadJson.isBlank()) JsonObject()
                        else JsonParser.parseString(payloadJson).asJsonObject

                    when (action) {
                        "bootstrap" -> sendResult(requestId, true, connectionSettings(), null)
                        "checkUrl" -> {
                            val url = payload.requiredString("url").trim().trimEnd('/')
                            checkRequestId = requestId
                            viewModel.checkUrl(url)
                        }
                        "login" -> {
                            pendingClientName =
                                payload.optionalString("clientName")?.trim().orEmpty().ifBlank { Build.MODEL }
                            loginRequestId = requestId
                            viewModel.login(
                                payload.requiredString("username"),
                                payload.requiredString("password")
                            )
                        }
                        "oidc" -> {
                            pendingClientName =
                                payload.optionalString("clientName")?.trim().orEmpty().ifBlank { Build.MODEL }
                            oidcRequestId = requestId
                            viewModel.startOidcAuthorize(pendingClientName)
                        }
                        "setValidateSsl" -> {
                            settings.validateSSL = payload.requiredBoolean("enabled")
                            viewModel.invalidateUrl()
                            sendResult(requestId, true, connectionSettings(), null)
                        }
                        "selectCa" -> {
                            launchCertificatePicker(caDialogResultLauncher)
                            sendResult(requestId, true, mapOf("launched" to true), null)
                        }
                        "selectClientCert" -> {
                            launchCertificatePicker(clientCertDialogResultLauncher)
                            sendResult(requestId, true, mapOf("launched" to true), null)
                        }
                        "removeCa" -> {
                            settings.caCertPath?.let { runCatching { File(it).delete() } }
                            settings.caCertPath = null
                            viewModel.invalidateUrl()
                            sendResult(requestId, true, connectionSettings(), null)
                            emitConnectionSettings()
                        }
                        "removeClientCert" -> {
                            settings.clientCertPath?.let { runCatching { File(it).delete() } }
                            settings.clientCertPath = null
                            settings.clientCertPassword = null
                            viewModel.invalidateUrl()
                            sendResult(requestId, true, connectionSettings(), null)
                            emitConnectionSettings()
                        }
                        "setClientCertPassword" -> {
                            settings.clientCertPassword =
                                payload.optionalString("password")?.takeIf { it.isNotEmpty() }
                            viewModel.invalidateUrl()
                            sendResult(requestId, true, connectionSettings(), null)
                        }
                        "openLogs" -> {
                            startActivity(Intent(this@LoginActivity, LogsActivity::class.java))
                            sendResult(requestId, true, mapOf("opened" to true), null)
                        }
                        else -> error("Unknown login action: " + action)
                    }
                } catch (e: Exception) {
                    Logger.error(e, "Tailwind login bridge request failed")
                    sendResult(
                        requestId,
                        false,
                        null,
                        mapOf("message" to (e.message ?: "Request failed"))
                    )
                }
            }
        }
    }

    private fun launchCertificatePicker(launcher: ActivityResultLauncher<Intent>) {
        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
        try {
            launcher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            emitError("Install or enable a file browser to select certificates.")
        }
    }

    private fun handleCertificateResult(resultCode: Int, data: Intent?, isCa: Boolean) {
        if (resultCode != Activity.RESULT_OK) return
        try {
            val uri = data?.data ?: error("No certificate file was selected.")
            val input =
                contentResolver.openInputStream(uri)
                    ?: error("The selected file could not be opened.")
            val file =
                if (isCa) File(filesDir, CertUtils.CA_CERT_NAME)
                else File(filesDir, CertUtils.CLIENT_CERT_NAME)

            copyStreamToFile(input, file)

            if (isCa) {
                getNameOfCertContent(file) ?: error("The selected CA certificate is not valid.")
                settings.caCertPath = file.absolutePath
            } else {
                settings.clientCertPath = file.absolutePath
            }
            viewModel.invalidateUrl()
            emitConnectionSettings()
        } catch (e: Exception) {
            Logger.warn(e, "Could not import certificate")
            emitError(e.message ?: "Could not import certificate.")
        }
    }

    private fun infoMap(): Map<String, Any?> {
        val info = viewModel.monitaInfo
        return mapOf(
            "version" to info?.version,
            "oidc" to (info?.oidc ?: false)
        )
    }

    private fun connectionSettings(): Map<String, Any?> =
        mapOf(
            "serverUrl" to settings.url,
            "defaultClientName" to Build.MODEL,
            "validateSsl" to settings.validateSSL,
            "hasCa" to !settings.caCertPath.isNullOrBlank(),
            "hasClientCert" to !settings.clientCertPath.isNullOrBlank(),
            "hasClientCertPassword" to !settings.clientCertPassword.isNullOrBlank(),
            "uiBuild" to BuildConfig.UI_BUILD_ID,
            "applicationId" to BuildConfig.APPLICATION_ID,
            "versionName" to BuildConfig.VERSION_NAME
        )

    private fun failCheck(message: String) {
        checkRequestId?.let { id ->
            checkRequestId = null
            sendResult(id, false, null, mapOf("message" to message))
        }
        emitError(message)
    }

    private fun failLogin(message: String) {
        loginRequestId?.let { id ->
            loginRequestId = null
            sendResult(id, false, null, mapOf("message" to message))
        }
        emitState("ready")
        emitError(message)
    }

    private fun failOidc(message: String) {
        oidcRequestId?.let { id ->
            oidcRequestId = null
            sendResult(id, false, null, mapOf("message" to message))
        }
        emitState("ready")
        emitError(message)
    }

    private fun emitState(state: String) {
        emit(
            "state",
            mapOf(
                "state" to state,
                "info" to if (viewModel.monitaInfo != null) infoMap() else null
            )
        )
    }

    private fun emitConnectionSettings() {
        emit("connectionSettings", connectionSettings())
    }

    private fun emitError(message: String) {
        emit("error", mapOf("message" to message))
    }

    private fun emit(name: String, payload: Any?) {
        val nameJson = Utils.JSON.toJson(name)
        val payloadJson = Utils.JSON.toJson(payload)
        binding.loginWebview.post {
            binding.loginWebview.evaluateJavascript(
                "window.MonitaLogin && window.MonitaLogin.nativeEvent($nameJson, $payloadJson)",
                null
            )
        }
    }

    private fun sendResult(
        requestId: String,
        ok: Boolean,
        data: Any?,
        error: Any?
    ) {
        val requestJson = Utils.JSON.toJson(requestId)
        val envelopeJson =
            Utils.JSON.toJson(
                mapOf(
                    "ok" to ok,
                    "data" to data,
                    "error" to error
                )
            )
        binding.loginWebview.post {
            binding.loginWebview.evaluateJavascript(
                "window.MonitaLogin && window.MonitaLogin.nativeResult($requestJson, $envelopeJson)",
                null
            )
        }
    }

    private fun handleOidcCallback(intent: Intent?) {
        val data = intent?.data ?: return
        if (!data.toString().startsWith(LoginViewModel.OIDC_REDIRECT_URI)) return

        val code = data.getQueryParameter("code")
        val state = data.getQueryParameter("state")
        if (code.isNullOrBlank() || state.isNullOrBlank()) {
            emitError("Single sign-on callback is missing required information.")
            return
        }
        viewModel.handleOidcCallback(code, state)
    }

    private fun getNameOfCertContent(file: File): String? =
        FileInputStream(file).use {
            (CertUtils.parseCertificate(it) as? X509Certificate)?.subjectX500Principal?.name
        }

    private fun copyStreamToFile(inputStream: InputStream, file: File) {
        inputStream.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        }
    }

    private fun JsonObject.requiredString(name: String): String =
        get(name)?.takeUnless { it.isJsonNull }?.asString ?: error(name + " is required")

    private fun JsonObject.optionalString(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.asString

    private fun JsonObject.requiredBoolean(name: String): Boolean =
        get(name)?.takeUnless { it.isJsonNull }?.asBoolean ?: error(name + " is required")

    override fun onDestroy() {
        binding.loginWebview.removeJavascriptInterface("MonitaLoginNative")
        binding.loginWebview.destroy()
        super.onDestroy()
    }
}
