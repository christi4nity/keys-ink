package com.keysink.inputmethod.latin.settings

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.ListPreference
import android.preference.Preference
import com.keysink.inputmethod.R
import com.keysink.inputmethod.latin.voice.ModelDownloadManager
import com.keysink.inputmethod.latin.voice.WhisperModel

class VoiceInputSettingsFragment : SubScreenFragment() {

    private var downloadManager: ModelDownloadManager? = null
    private var permissionPref: Preference? = null
    private var modelPref: Preference? = null
    private var modelSelectorPref: ListPreference? = null

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        addPreferencesFromResource(R.xml.prefs_screen_voice)

        permissionPref = findPreference("voice_permission_status")
        modelPref = findPreference("voice_model_status")
        modelSelectorPref = findPreference(Settings.PREF_VOICE_MODEL) as? ListPreference

        updateModelSelectorSummary()
        updatePermissionStatus()
        updateModelStatus()

        permissionPref?.setOnPreferenceClickListener {
            requestMicPermission()
            true
        }

        modelPref?.setOnPreferenceClickListener {
            startModelDownload()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        updateModelSelectorSummary()
        updatePermissionStatus()
        updateModelStatus()
    }

    private fun getSelectedModel(): WhisperModel {
        val id = modelSelectorPref?.value ?: WhisperModel.DEFAULT.id
        return WhisperModel.fromId(id)
    }

    private fun updateModelSelectorSummary() {
        val model = getSelectedModel()
        modelSelectorPref?.summary = "${model.displayName} — ${model.displaySize}"
    }

    private fun updatePermissionStatus() {
        val granted = activity?.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        permissionPref?.summary = getString(
            if (granted) R.string.voice_pref_mic_permission_granted
            else R.string.voice_pref_mic_permission_tap
        )
    }

    private fun updateModelStatus() {
        val filesDir = activity?.filesDir ?: return
        val model = getSelectedModel()
        val downloaded = ModelDownloadManager.isModelDownloaded(filesDir, model)
        modelPref?.summary = if (downloaded) {
            getString(R.string.voice_pref_model_ready)
        } else {
            getString(R.string.voice_pref_model_tap_download, model.displaySize)
        }
    }

    private fun requestMicPermission() {
        requestPermissions(
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_MIC_PERMISSION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_MIC_PERMISSION) {
            updatePermissionStatus()
        }
    }

    private fun startModelDownload() {
        val filesDir = activity?.filesDir ?: return
        val ctx = activity ?: return
        val model = getSelectedModel()
        if (ModelDownloadManager.isModelDownloaded(filesDir, model)) return

        downloadManager = ModelDownloadManager(model)
        modelPref?.summary = getString(R.string.voice_pref_model_downloading, 0)

        downloadManager?.download(ctx, filesDir, object : ModelDownloadManager.Callback {
            override fun onStateChanged(state: ModelDownloadManager.DownloadState) {
                activity?.runOnUiThread {
                    when (state) {
                        is ModelDownloadManager.DownloadState.Downloading -> {
                            modelPref?.summary = getString(
                                R.string.voice_pref_model_downloading, state.progress)
                        }
                        is ModelDownloadManager.DownloadState.Complete -> {
                            modelPref?.summary = getString(R.string.voice_pref_model_ready)
                        }
                        is ModelDownloadManager.DownloadState.Failed -> {
                            modelPref?.summary = getString(
                                R.string.voice_pref_model_tap_retry, state.message)
                        }
                        is ModelDownloadManager.DownloadState.NotDownloaded -> {
                            updateModelStatus()
                        }
                    }
                }
            }
        })
    }

    override fun onDestroy() {
        downloadManager?.shutdown()
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        if (key == Settings.PREF_VOICE_MODEL) {
            updateModelSelectorSummary()
            updateModelStatus()
        }
    }

    companion object {
        private const val REQUEST_MIC_PERMISSION = 1001
    }
}
