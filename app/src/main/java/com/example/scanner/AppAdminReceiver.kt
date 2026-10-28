package com.example.scanner

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class AppAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        try {
            val lockIntent = Intent(context, UninstallLockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("is_admin_disable", true)
            }
            context.startActivity(lockIntent)
        } catch (_: Exception) {}
        return "⚠️ Security PIN required to deactivate Device Administrator protection!"
    }

    companion object {
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, AppAdminReceiver::class.java)
        }

        fun isActive(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            return dpm?.isAdminActive(getComponentName(context)) == true
        }

        fun createActivationIntent(context: Context): Intent {
            return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context))
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Activate Device Administrator to protect Nova Cleaner from unauthorized uninstallation and system tampering."
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        fun requestActivation(context: Context): Boolean {
            // Attempt 1: Standard ADD_DEVICE_ADMIN
            try {
                val intent = createActivationIntent(context)
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                android.util.Log.w("AppAdmin", "Direct ACTION_ADD_DEVICE_ADMIN failed: ${e.message}")
            }

            // Attempt 2: Settings DEVICE_ADMIN_SETTINGS
            try {
                val intent = Intent("android.settings.DEVICE_ADMIN_SETTINGS").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (_: Exception) {}

            // Attempt 3: Settings SECURITY_SETTINGS
            try {
                val intent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (_: Exception) {}

            // Attempt 4: General Settings
            try {
                val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (_: Exception) {}

            return false
        }
    }
}
