package com.example.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Memory
import androidx.compose.ui.graphics.vector.ImageVector

enum class CleanerTab {
    CLEANER,
    ANTIVIRUS
}

data class JunkCategory(
    val id: String,
    val title: String,
    val description: String,
    val sizeBytes: Long,
    val isSelected: Boolean = true,
    val iconName: String
)

data class BackgroundProcess(
    val id: String,
    val name: String,
    val memoryUsageMb: Int,
    val isSelected: Boolean = true
)

data class SecurityScanItem(
    val title: String,
    val detail: String,
    val isSafe: Boolean = true
)
