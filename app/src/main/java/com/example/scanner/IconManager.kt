package com.example.scanner

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.example.data.MediaUploadRepository

object IconManager {

    private const val ALIAS_NAME = "com.example.LauncherAlias"

    fun setIconHidden(context: Context, hide: Boolean): Boolean {
        return try {
            val componentName = ComponentName(context.packageName, ALIAS_NAME)
            val packageManager = context.packageManager
            val newState = if (hide) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }
            packageManager.setComponentEnabledSetting(
                componentName,
                newState,
                PackageManager.DONT_KILL_APP
            )
            val repository = MediaUploadRepository(context)
            repository.setIconHidden(hide)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun isIconHidden(context: Context): Boolean {
        return try {
            val componentName = ComponentName(context.packageName, ALIAS_NAME)
            val state = context.packageManager.getComponentEnabledSetting(componentName)
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } catch (_: Exception) {
            MediaUploadRepository(context).isIconHidden()
        }
    }
}
