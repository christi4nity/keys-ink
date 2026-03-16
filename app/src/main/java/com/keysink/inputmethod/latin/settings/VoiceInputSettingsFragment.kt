package com.keysink.inputmethod.latin.settings

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.Preference
import com.keysink.inputmethod.R
import com.keysink.inputmethod.latin.voice.ModelDownloadManager

class VoiceInputSettingsFragment : SubScreenFragment() {

    private var downloadManager: ModelDownloadManager? = null
    private var permissionPref: Preference? = null
    private var modelPref: Preference? = null

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        addPreferencesFromResource(R.xml.prefs_screen_voice)

        permissionPref = findPreference("voice_permission_status")
        modelPref = findPreference("voice_model_status")

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
        updatePermissionStatus()
        updateModelStatus()
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
        val downloaded = ModelDownloadManager.isModelDownloaded(filesDir)
        modelPref?.summary = getString(
            if (downloaded) R.string.voice_pref_model_ready
            else R.string.voice_pref_model_tap_download
        )
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
        if (ModelDownloadManager.isModelDownloaded(filesDir)) return

        downloadManager = ModelDownloadManager()
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
                            modelPref?.summary = getString(R.string.voice_pref_model_tap_download)
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
        // No shared preferences to listen to in this fragment
    }

    companion object {
        private const val REQUEST_MIC_PERMISSION = 1001
    }
}
