package com.gigabytegrove.monita.init

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.gigabytegrove.monita.R
import com.gigabytegrove.monita.Settings
import com.gigabytegrove.monita.api.ApiException
import com.gigabytegrove.monita.api.Callback
import com.gigabytegrove.monita.api.Callback.SuccessCallback
import com.gigabytegrove.monita.api.ClientFactory
import com.gigabytegrove.monita.client.model.User
import com.gigabytegrove.monita.client.model.VersionInfo
import com.gigabytegrove.monita.login.LoginActivity
import com.gigabytegrove.monita.messages.MessagesActivity
import com.gigabytegrove.monita.service.WebSocketService
import com.gigabytegrove.monita.security.AppUnlockSession
import com.gigabytegrove.monita.security.DeviceAuthenticator
import com.gigabytegrove.monita.security.DeviceAuthenticationException
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.livinglifetechway.quickpermissionskotlin.runWithPermissions
import com.livinglifetechway.quickpermissionskotlin.util.QuickPermissionsOptions
import com.livinglifetechway.quickpermissionskotlin.util.QuickPermissionsRequest
import kotlinx.coroutines.launch
import org.tinylog.kotlin.Logger

internal class InitializationActivity : AppCompatActivity() {

    private lateinit var settings: Settings
    private var splashScreenActive = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        settings = Settings(this)
        Logger.info("Entering ${javaClass.simpleName}")

        installSplashScreen().setKeepOnScreenCondition { splashScreenActive }

        if (settings.tokenExists()) {
            unlockThenAuthenticate()
        } else {
            AppUnlockSession.lock()
            showLogin()
        }
    }

    private fun unlockThenAuthenticate() {
        if (!settings.appLockEnabled || AppUnlockSession.isUnlocked()) {
            AppUnlockSession.unlock()
            runWithPostNotificationsPermission { tryAuthenticate() }
            return
        }

        splashScreenActive = false
        setContentView(R.layout.splash)
        lifecycleScope.launch {
            try {
                val unlocked =
                    DeviceAuthenticator.authenticate(
                        this@InitializationActivity,
                        settings.deviceAuthMode,
                        getString(R.string.security_unlock_app_title),
                        getString(R.string.security_unlock_app_subtitle)
                    )
                if (!unlocked) {
                    finishAndRemoveTask()
                    return@launch
                }
                AppUnlockSession.unlock()
                runWithPostNotificationsPermission { tryAuthenticate() }
            } catch (e: DeviceAuthenticationException) {
                MaterialAlertDialogBuilder(this@InitializationActivity)
                    .setTitle(R.string.security_unlock_unavailable_title)
                    .setMessage(e.message ?: getString(R.string.security_unlock_unavailable_message))
                    .setPositiveButton(R.string.retry) { _, _ -> unlockThenAuthenticate() }
                    .setNegativeButton(R.string.cancel) { _, _ -> finishAndRemoveTask() }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    private fun showLogin() {
        splashScreenActive = false
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun tryAuthenticate() {
        ClientFactory.userApiWithToken(settings)
            .currentUser()
            .enqueue(
                Callback.callInUI(
                    this,
                    onSuccess = Callback.SuccessBody { user -> authenticated(user) },
                    onError = { exception -> failed(exception) }
                )
            )
    }

    private fun failed(exception: ApiException) {
        stopSlashScreen()
        when (exception.code) {
            0 -> {
                dialog(getString(R.string.not_available, settings.url))
                return
            }

            401 -> {
                dialog(getString(R.string.auth_failed))
                return
            }
        }

        var response = exception.body
        response = response.take(200)
        dialog(getString(R.string.other_error, settings.url, exception.code, response))
    }

    private fun dialog(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.oops)
            .setMessage(message)
            .setPositiveButton(R.string.retry) { _, _ -> tryAuthenticate() }
            .setNegativeButton(R.string.logout) { _, _ -> showLogin() }
            .setCancelable(false)
            .show()
    }

    private fun authenticated(user: User) {
        Logger.info("Authenticated as ${user.name}")

        settings.setUser(user.name, user.admin)
        requestVersion {
            splashScreenActive = false
            startActivity(Intent(this, MessagesActivity::class.java))
            finish()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, WebSocketService::class.java))
        } else {
            startService(Intent(this, WebSocketService::class.java))
        }
    }

    private fun requestVersion(runnable: Runnable) {
        requestVersion(
            callback = Callback.SuccessBody { version: VersionInfo ->
                Logger.info("Server version: ${version.version}@${version.buildDate}")
                settings.serverVersion = version.version
                runnable.run()
            },
            errorCallback = { runnable.run() }
        )
    }

    private fun requestVersion(
        callback: SuccessCallback<VersionInfo>,
        errorCallback: Callback.ErrorCallback
    ) {
        ClientFactory.infoApi(settings)
            .version
            .enqueue(Callback.callInUI(this, callback, errorCallback))
    }

    private fun runWithPostNotificationsPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13 and above
            val quickPermissionsOption = QuickPermissionsOptions(
                handleRationale = true,
                handlePermanentlyDenied = true,
                preRationaleAction = { stopSlashScreen() },
                rationaleMethod = { req -> processPermissionRationale(req) },
                permissionsDeniedMethod = { req -> processPermissionRationale(req) },
                permanentDeniedMethod = { req -> processPermissionsPermanentDenied(req) }
            )
            runWithPermissions(
                Manifest.permission.POST_NOTIFICATIONS,
                options = quickPermissionsOption,
                callback = action
            )
        } else {
            // Android 12 and below
            action()
        }
    }

    private fun stopSlashScreen() {
        splashScreenActive = false
        setContentView(R.layout.splash)
    }

    private fun processPermissionRationale(req: QuickPermissionsRequest) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permissions_notification_title)
            .setMessage(getString(R.string.permissions_notification_denied_temp))
            .setPositiveButton(getString(R.string.permissions_dialog_grant)) { _, _ ->
                req.proceed()
            }
            .setCancelable(false)
            .show()
    }

    private fun processPermissionsPermanentDenied(req: QuickPermissionsRequest) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permissions_notification_title)
            .setMessage(getString(R.string.permissions_notification_denied_permanent))
            .setPositiveButton(getString(R.string.permissions_dialog_open_settings)) { _, _ ->
                req.openAppSettings()
            }
            .setCancelable(false)
            .show()
    }
}
