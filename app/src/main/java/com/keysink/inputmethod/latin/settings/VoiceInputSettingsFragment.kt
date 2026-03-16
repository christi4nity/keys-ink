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
        permissionPref?.summary = if (granted) "Granted" else "Tap to grant"
    }

    private fun updateModelStatus() {
        val filesDir = activity?.filesDir ?: return
        val downloaded = ModelDownloadManager.isModelDownloaded(filesDir)
        modelPref?.summary = if (downloaded) "Ready" else "Tap to download (~142 MB)"
    }

    private fun requestMicPermission() {
        activity?.requestPermissions(
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
        if (ModelDownloadManager.isModelDownloaded(filesDir)) return

        downloadManager = ModelDownloadManager()
        modelPref?.summary = "Downloading... 0%"

        downloadManager?.download(filesDir, object : ModelDownloadManager.Callback {
            override fun onStateChanged(state: ModelDownloadManager.DownloadState) {
                activity?.runOnUiThread {
                    when (state) {
                        is ModelDownloadManager.DownloadState.Downloading -> {
                            modelPref?.summary = "Downloading... ${state.progress}%"
                        }
                        is ModelDownloadManager.DownloadState.Complete -> {
                            modelPref?.summary = "Ready"
                        }
                        is ModelDownloadManager.DownloadState.Failed -> {
                            modelPref?.summary = "${state.message}. Tap to retry."
                        }
                        is ModelDownloadManager.DownloadState.NotDownloaded -> {
                            modelPref?.summary = "Tap to download (~142 MB)"
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
