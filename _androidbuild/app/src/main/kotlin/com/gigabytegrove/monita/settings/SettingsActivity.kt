package com.gigabytegrove.monita.settings

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.ListPreferenceDialogFragmentCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.gigabytegrove.monita.NotificationSupport
import com.gigabytegrove.monita.R
import com.gigabytegrove.monita.Settings as MonitaSettings
import com.gigabytegrove.monita.Utils
import com.gigabytegrove.monita.api.Api
import com.gigabytegrove.monita.api.ClientFactory
import com.gigabytegrove.monita.client.api.ApplicationApi
import com.gigabytegrove.monita.client.model.Application
import com.gigabytegrove.monita.databinding.SettingsActivityBinding
import com.gigabytegrove.monita.service.WebSocketService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class SettingsActivity :
    AppCompatActivity(),
    OnSharedPreferenceChangeListener {
    private lateinit var binding: SettingsActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, SettingsFragment())
            .commit()
        setSupportActionBar(binding.appBarDrawer.toolbar)
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setDisplayShowCustomEnabled(true)
        }
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return false
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (sharedPreferences == null) return
        when (key) {
            getString(R.string.setting_key_theme) -> {
                ThemeHelper.setTheme(
                    this,
                    sharedPreferences.getString(key, getString(R.string.theme_default))!!
                )
            }
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        private var pendingSoundChannel: Application? = null
        private val soundPicker =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode != AppCompatActivity.RESULT_OK) return@registerForActivityResult
                val app = pendingSoundChannel ?: return@registerForActivityResult
                pendingSoundChannel = null

                @Suppress("DEPRECATION")
                val picked =
                    result.data?.getParcelableExtra<android.net.Uri>(
                        RingtoneManager.EXTRA_RINGTONE_PICKED_URI
                    )
                val settings = MonitaSettings(requireContext())
                settings.setChannelSound(
                    app.id,
                    picked?.toString() ?: MonitaSettings.CHANNEL_SOUND_SILENT
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val manager =
                        requireContext().getSystemService(
                            android.content.Context.NOTIFICATION_SERVICE
                        ) as android.app.NotificationManager
                    val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    preferences.edit()
                        .putBoolean(getString(R.string.setting_key_notification_channels), true)
                        .apply()
                    NotificationSupport.recreateAppChannels(requireContext(), manager, app)
                }
                requestWebSocketRestart()
                Utils.showSnackBar(requireActivity(), "Notification sound updated for " + app.name)
            }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                findPreference<SwitchPreferenceCompat>(
                    getString(R.string.setting_key_notification_channels)
                )?.isEnabled = true
            } else {
                findPreference<Preference>(
                    getString(R.string.setting_key_channel_sounds)
                )?.apply {
                    isEnabled = false
                    summary = getString(R.string.setting_channel_sounds_requires_android)
                }
            }
            findPreference<androidx.preference.EditTextPreference>(
                getString(R.string.setting_key_reconnect_delay)
            )?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    val value = (newValue as String).trim().toIntOrNull() ?: 60
                    if (value !in 5..1200) {
                        Utils.showSnackBar(
                            requireActivity(),
                            "Please enter a value between 5 and 1200"
                        )
                        return@OnPreferenceChangeListener false
                    }

                    requestWebSocketRestart()
                    true
                }
            findPreference<SwitchPreferenceCompat>(
                getString(R.string.setting_key_exponential_backoff)
            )?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, _ ->
                    requestWebSocketRestart()
                    true
                }

            findPreference<Preference>(
                getString(R.string.setting_key_channel_sounds)
            )?.setOnPreferenceClickListener {
                chooseChannelSound()
                true
            }
        }

        private fun chooseChannelSound() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                Utils.showSnackBar(
                    requireActivity(),
                    getString(R.string.setting_channel_sounds_requires_android)
                )
                return
            }

            lifecycleScope.launch {
                val apps =
                    runCatching {
                        withContext(Dispatchers.IO) {
                            val settings = MonitaSettings(requireContext())
                            val api =
                                ClientFactory.clientToken(settings)
                                    .createService(ApplicationApi::class.java)
                            Api.execute(api.getApps()).sortedBy { it.name.lowercase() }
                        }
                    }.getOrElse {
                        Utils.showSnackBar(
                            requireActivity(),
                            getString(R.string.setting_channel_sounds_load_failed)
                        )
                        return@launch
                    }

                if (apps.isEmpty()) {
                    Utils.showSnackBar(
                        requireActivity(),
                        getString(R.string.setting_channel_sounds_no_channels)
                    )
                    return@launch
                }

                val labels =
                    apps.map { app ->
                        val kind = if (app.isChatChannel) "Chat" else "Notifications"
                        app.name + " · " + kind
                    }.toTypedArray()

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.setting_channel_sound_dialog_title)
                    .setItems(labels) { _, which ->
                        val app = apps[which]
                        pendingSoundChannel = app
                        val currentSound = MonitaSettings(requireContext()).channelSound(app.id)
                        val existingUri =
                            when (currentSound) {
                                null -> android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
                                MonitaSettings.CHANNEL_SOUND_SILENT -> null
                                else -> android.net.Uri.parse(currentSound)
                            }
                        val intent =
                            Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_TYPE,
                                    RingtoneManager.TYPE_NOTIFICATION
                                )
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                                    android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
                                )
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existingUri)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, app.name)
                            }
                        soundPicker.launch(intent)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }

        private fun requestWebSocketRestart() {
            val intent = Intent(requireContext(), WebSocketService::class.java)
            requireContext().startService(intent)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            findPreference<ListPreference>(
                getString(R.string.setting_key_message_layout)
            )?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, _ ->
                    showRestartDialog()
                    true
                }
            findPreference<SwitchPreferenceCompat>(
                getString(R.string.setting_key_notification_channels)
            )?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, _ ->
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        return@OnPreferenceChangeListener false
                    }
                    showRestartDialog()
                    true
                }
            findPreference<SwitchPreferenceCompat>(
                getString(R.string.setting_key_exclude_from_recent)
            )?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, value ->
                    Utils.setExcludeFromRecent(requireContext(), value as Boolean)
                    return@OnPreferenceChangeListener true
                }
            findPreference<SwitchPreferenceCompat>(
                getString(R.string.setting_key_intent_dialog_permission)
            )?.let {
                it.setOnPreferenceChangeListener { _, _ ->
                    openSystemAlertWindowPermissionPage()
                }
            }
            checkSystemAlertWindowPermission()
        }

        override fun onDisplayPreferenceDialog(preference: Preference) {
            if (preference is ListPreference) {
                showListPreferenceDialog(preference)
            } else {
                super.onDisplayPreferenceDialog(preference)
            }
        }

        override fun onResume() {
            super.onResume()
            checkSystemAlertWindowPermission()
        }

        private fun openSystemAlertWindowPermissionPage(): Boolean {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${requireContext().packageName}".toUri()
            ).apply {
                startActivity(this)
            }
            return true
        }

        private fun checkSystemAlertWindowPermission() {
            findPreference<SwitchPreferenceCompat>(
                getString(R.string.setting_key_intent_dialog_permission)
            )?.let {
                val canDrawOverlays = Settings.canDrawOverlays(requireContext())
                it.isChecked = canDrawOverlays
                it.summary = if (canDrawOverlays) {
                    getString(R.string.setting_summary_intent_dialog_permission_granted)
                } else {
                    getString(R.string.setting_summary_intent_dialog_permission)
                }
            }
        }

        private fun showListPreferenceDialog(preference: ListPreference) {
            val dialogFragment = MaterialListPreference()
            dialogFragment.arguments = Bundle(1).apply { putString("key", preference.key) }
            @Suppress("DEPRECATION") // https://issuetracker.google.com/issues/181793702#comment3
            dialogFragment.setTargetFragment(this, 0)
            dialogFragment.show(
                parentFragmentManager,
                "androidx.preference.PreferenceFragment.DIALOG"
            )
        }

        private fun showRestartDialog() {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.setting_restart_dialog_title)
                .setMessage(R.string.setting_restart_dialog_message)
                .setPositiveButton(getString(R.string.setting_restart_dialog_button1)) { _, _ ->
                    restartApp()
                }
                .setNegativeButton(getString(R.string.setting_restart_dialog_button2), null)
                .show()
        }

        private fun restartApp() {
            val packageManager = requireContext().packageManager
            val packageName = requireContext().packageName
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            val componentName = intent!!.component
            val mainIntent = Intent.makeRestartActivityTask(componentName)
            startActivity(mainIntent)
            Runtime.getRuntime().exit(0)
        }
    }

    class MaterialListPreference : ListPreferenceDialogFragmentCompat() {
        private var mWhichButtonClicked = 0

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            mWhichButtonClicked = DialogInterface.BUTTON_NEGATIVE
            val builder = MaterialAlertDialogBuilder(requireActivity())
                .setTitle(preference.dialogTitle)
                .setPositiveButton(preference.positiveButtonText, this)
                .setNegativeButton(preference.negativeButtonText, this)

            val contentView = context?.let { onCreateDialogView(it) }
            if (contentView != null) {
                onBindDialogView(contentView)
                builder.setView(contentView)
            } else {
                builder.setMessage(preference.dialogMessage)
            }
            onPrepareDialogBuilder(builder)
            return builder.create()
        }

        override fun onClick(dialog: DialogInterface, which: Int) {
            mWhichButtonClicked = which
        }

        override fun onDismiss(dialog: DialogInterface) {
            onDialogClosedWasCalledFromOnDismiss = true
            super.onDismiss(dialog)
        }

        private var onDialogClosedWasCalledFromOnDismiss = false

        override fun onDialogClosed(positiveResult: Boolean) {
            if (onDialogClosedWasCalledFromOnDismiss) {
                onDialogClosedWasCalledFromOnDismiss = false
                super.onDialogClosed(mWhichButtonClicked == DialogInterface.BUTTON_POSITIVE)
            } else {
                super.onDialogClosed(positiveResult)
            }
        }
    }
}
