package com.uth.memora.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uth.memora.core.model.ThemeMode
import com.uth.memora.core.model.UserPreferences
import com.uth.memora.feature.theme.KickerBlue
import com.uth.memora.feature.theme.MemoraTheme
import com.uth.memora.feature.theme.PrimaryBlue
import com.uth.memora.feature.theme.StatusError
import com.uth.memora.feature.theme.StatusSuccess
import com.uth.memora.feature.theme.StatusWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Màn hình Cài đặt ứng dụng (Figma Node 13:2 · Mục 6.2, 6.10, 11 & 13).
 */
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onNavigateToAuth: () -> Unit = {},
    onRequestSignOut: () -> Unit = {},
    onConfirmSignOut: () -> Unit = {},
    onDismissSignOutWarning: () -> Unit = {},
    onSelectThemeMode: (ThemeMode) -> Unit = {},
    onDailyNewCardsLimitChange: (Int) -> Unit = {},
    onToggleReminder: (Boolean) -> Unit = {},
    onOpenTimePicker: () -> Unit = {},
    onDismissTimePicker: () -> Unit = {},
    onConfirmReminderTime: (Int, Int) -> Unit = { _, _ -> },
    onOpenSystemNotificationSettings: () -> Unit = {},
    onToggleSound: (Boolean) -> Unit = {},
    onSyncNowClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
            }
            return@Surface
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "settings_header") {
                SettingsHeader()
            }

            uiState.feedbackMessage?.let { feedback ->
                item(key = "settings_feedback") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = feedback,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            uiState.errorMessage?.let { err ->
                item(key = "settings_error") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            item(key = "settings_account_section") {
                AccountSettingsSection(
                    uiState = uiState,
                    onNavigateToAuth = onNavigateToAuth,
                    onRequestSignOut = onRequestSignOut
                )
            }

            item(key = "settings_theme_section") {
                ThemeSelectorSection(
                    selectedMode = uiState.themeMode,
                    onSelectThemeMode = onSelectThemeMode
                )
            }

            item(key = "settings_daily_limit_section") {
                DailyNewCardsLimitSection(
                    dailyLimit = uiState.dailyNewCardsLimit,
                    onDailyLimitChange = onDailyNewCardsLimitChange
                )
            }

            item(key = "settings_reminder_sound_section") {
                ReminderAndSoundSection(
                    isReminderEnabled = uiState.isReminderEnabled,
                    formattedReminderTime = uiState.formattedReminderTime,
                    isNotificationPermissionGuideVisible = uiState.isNotificationPermissionGuideVisible,
                    isSoundEnabled = uiState.isSoundEnabled,
                    onToggleReminder = onToggleReminder,
                    onOpenTimePicker = onOpenTimePicker,
                    onOpenSystemNotificationSettings = onOpenSystemNotificationSettings,
                    onToggleSound = onToggleSound
                )
            }

            item(key = "settings_cloud_sync_section") {
                CloudSyncStatusSection(
                    uiState = uiState,
                    onSyncNowClick = onSyncNowClick
                )
            }
        }

        if (uiState.isTimePickerVisible) {
            ReminderTimePickerDialog(
                initialHour = uiState.reminderHour,
                initialMinute = uiState.reminderMinute,
                onDismiss = onDismissTimePicker,
                onConfirm = onConfirmReminderTime
            )
        }

        if (uiState.isSignOutWarningDialogVisible) {
            UnsyncedSignOutWarningDialog(
                pendingChangesCount = uiState.syncSummary.pendingChangesCount.coerceAtLeast(1),
                onConfirmSignOut = onConfirmSignOut,
                onDismiss = onDismissSignOutWarning
            )
        }
    }
}

/**
 * Khối Tài khoản người dùng ở đầu màn Cài đặt (Figma Node 13:2 · Mục 6.2 & 13).
 */
@Composable
fun AccountSettingsSection(
    uiState: SettingsUiState,
    onNavigateToAuth: () -> Unit,
    onRequestSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val user = uiState.currentUser
    val isSignedIn = uiState.isSignedInAccount

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlue.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Tài khoản người dùng",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (isSignedIn) "TÀI KHOẢN ĐÃ ĐĂNG NHẬP" else "CHẾ ĐỘ KHÁCH NGOẠI TUYẾN",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = KickerBlue
                    )
                    Text(
                        text = if (isSignedIn) {
                            user?.displayName?.takeIf { it.isNotBlank() }
                                ?: user?.email?.takeIf { it.isNotBlank() }
                                ?: "Học viên Memora"
                        } else {
                            "Học viên Khách (Trên thiết bị)"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isSignedIn) {
                            user?.email?.takeIf { it.isNotBlank() } ?: "UID: ${user?.uid.orEmpty()}"
                        } else {
                            "Đăng nhập để sao lưu tiến độ SM-2 và đồng bộ đa thiết bị"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isSignedIn) {
                OutlinedButton(
                    onClick = onRequestSignOut,
                    enabled = !uiState.isSigningOut,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Đăng xuất",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.isSigningOut) "Đang đăng xuất..." else "Đăng xuất",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            } else {
                Button(
                    onClick = onNavigateToAuth,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Login,
                        contentDescription = "Đăng nhập hoặc Đồng bộ tài khoản",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Đăng nhập / Đồng bộ tài khoản",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun UnsyncedSignOutWarningDialog(
    pendingChangesCount: Int,
    onConfirmSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = "Cảnh báo chưa đồng bộ",
                tint = StatusWarning
            )
        },
        title = {
            Text(
                text = "Cảnh báo dữ liệu chưa đồng bộ",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Text(
                text = "Bạn còn $pendingChangesCount thay đổi chưa đồng bộ lên đám mây, đăng xuất sẽ xóa dữ liệu tài khoản này khỏi máy. Vẫn đăng xuất?",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirmSignOut) {
                Text(
                    text = "Vẫn đăng xuất",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Ở lại & Đồng bộ trước")
            }
        }
    )
}

/**
 * Khối Nhắc nhở học tập hằng ngày (kèm hướng dẫn cấp quyền thông báo Android 13+)
 * và công tắc Hiệu ứng âm thanh & phát âm (Figma Node 13:2 · Mục 6.10 & 13).
 */
@Composable
fun ReminderAndSoundSection(
    isReminderEnabled: Boolean,
    formattedReminderTime: String,
    isNotificationPermissionGuideVisible: Boolean,
    isSoundEnabled: Boolean,
    onToggleReminder: (Boolean) -> Unit,
    onOpenTimePicker: () -> Unit,
    onOpenSystemNotificationSettings: () -> Unit,
    onToggleSound: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "NHẮC HỌC & ÂM THANH",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = KickerBlue
            )

            // 1. Công tắc Nhắc nhở học tập
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Nhắc nhở học tập",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Nhắc nhở học tập hằng ngày",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Chỉ gửi thông báo khi bạn còn thẻ đến hạn ôn trong ngày",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = isReminderEnabled,
                    onCheckedChange = onToggleReminder
                )
            }

            // 2. Chọn khung giờ nhắc học
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = isReminderEnabled, onClick = onOpenTimePicker),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = if (isReminderEnabled) 0.7f else 0.35f
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = "Giờ nhắc học",
                            tint = if (isReminderEnabled) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Giờ nhắc học cố định",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isReminderEnabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    Text(
                        text = formattedReminderTime,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isReminderEnabled) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Hướng dẫn mở lại quyền thông báo trên Android 13+ khi bị từ chối (Mục 6.10)
            if (isNotificationPermissionGuideVisible) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Quyền thông báo (POST_NOTIFICATIONS) đang bị từ chối trên thiết bị. Vui lòng bật lại quyền Thông báo cho Memora trong Cài đặt hệ thống để nhận lời nhắc ôn tập.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(onClick = onOpenSystemNotificationSettings) {
                            Text(
                                text = "Mở Cài đặt thông báo của máy",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            // 3. Công tắc Hiệu ứng âm thanh & phát âm
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Hiệu ứng âm thanh và phát âm",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Hiệu ứng âm thanh & phát âm",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Bật đọc phát âm tiếng Anh (TTS) và âm phản hồi khi lật thẻ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = isSoundEnabled,
                    onCheckedChange = onToggleSound
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour.coerceIn(0, 23),
        initialMinute = initialMinute.coerceIn(0, 59),
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Chọn giờ nhắc học hằng ngày",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TimePicker(state = timePickerState)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(timePickerState.hour, timePickerState.minute) }
            ) {
                Text("Lưu giờ nhắc")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}

/**
 * Khối Trạng thái Đồng bộ đám mây (Figma Node 13:2 · Mục 8.3, 10.1 & 13).
 *
 * Hiển thị biểu tượng trạng thái (Đang đồng bộ / Đã đồng bộ / Chờ đồng bộ / Lỗi),
 * thời gian đồng bộ gần nhất, số thay đổi đang chờ (`pendingChangesCount`), và nút "Đồng bộ ngay".
 */
@Composable
fun CloudSyncStatusSection(
    uiState: SettingsUiState,
    onSyncNowClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val badgeState = uiState.syncBadgeState
    val pendingCount = uiState.syncSummary.pendingChangesCount.coerceAtLeast(0)
    val lastSyncedText = formatLastSyncedAtVi(uiState.effectiveLastSyncedAtMillis)

    val (statusLabel, statusColor, statusIcon) = when (badgeState) {
        SyncBadgeStateUi.SYNCING -> Triple("Đang đồng bộ...", PrimaryBlue, Icons.Default.CloudSync)
        SyncBadgeStateUi.ERROR -> Triple("Lỗi đồng bộ", StatusError, Icons.Default.CloudOff)
        SyncBadgeStateUi.PENDING -> Triple("Chờ đồng bộ ($pendingCount)", StatusWarning, Icons.Default.CloudUpload)
        SyncBadgeStateUi.SYNCED -> Triple("Đã đồng bộ", StatusSuccess, Icons.Default.CloudDone)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = statusLabel,
                            tint = statusColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "ĐỒNG BỘ ĐÁM MÂY · LWW",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = KickerBlue
                        )
                        Text(
                            text = "Sao lưu & Đồng bộ",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = statusColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Đồng bộ gần nhất:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = lastSyncedText,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Thay đổi đang chờ:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$pendingCount bản ghi",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (pendingCount > 0) StatusWarning else StatusSuccess
                        )
                    }
                }
            }

            uiState.syncSummary.syncErrorMessage?.takeIf { it.isNotBlank() }?.let { syncErr ->
                Text(
                    text = syncErr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = onSyncNowClick,
                enabled = badgeState != SyncBadgeStateUi.SYNCING,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlue
                )
            ) {
                if (badgeState == SyncBadgeStateUi.SYNCING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Đồng bộ ngay",
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (badgeState == SyncBadgeStateUi.SYNCING) "Đang đồng bộ..." else "Đồng bộ ngay",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

private fun formatLastSyncedAtVi(timestampMillis: Long?): String {
    if (timestampMillis == null || timestampMillis <= 0L) {
        return "Chưa đồng bộ"
    }
    return runCatching {
        val formatter = SimpleDateFormat("HH:mm · dd/MM/yyyy", Locale.getDefault())
        formatter.format(Date(timestampMillis))
    }.getOrDefault("Vừa xong")
}

/**
 * Thanh trượt điều chỉnh mục tiêu thẻ mới mỗi ngày từ 5 đến 50 thẻ (Figma Node 13:2 & Mục 11).
 */
@Composable
fun DailyNewCardsLimitSection(
    dailyLimit: Int,
    onDailyLimitChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val clampedLimit = dailyLimit.coerceIn(
        UserPreferences.MIN_DAILY_NEW_CARDS,
        UserPreferences.MAX_DAILY_NEW_CARDS
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "HẠN MỨC HỌC TẬP",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = KickerBlue
                    )
                    Text(
                        text = "Mục tiêu thẻ mới mỗi ngày",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = PrimaryBlue.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "$clampedLimit thẻ / ngày",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = PrimaryBlue,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Slider(
                value = clampedLimit.toFloat(),
                onValueChange = { rawValue ->
                    onDailyLimitChange(
                        rawValue.roundToInt().coerceIn(
                            UserPreferences.MIN_DAILY_NEW_CARDS,
                            UserPreferences.MAX_DAILY_NEW_CARDS
                        )
                    )
                },
                valueRange = UserPreferences.MIN_DAILY_NEW_CARDS.toFloat()..UserPreferences.MAX_DAILY_NEW_CARDS.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = PrimaryBlue,
                    activeTrackColor = PrimaryBlue,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Tối thiểu: ${UserPreferences.MIN_DAILY_NEW_CARDS} thẻ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Khuyến nghị: ${UserPreferences.DEFAULT_DAILY_NEW_CARDS} thẻ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Tối đa: ${UserPreferences.MAX_DAILY_NEW_CARDS} thẻ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsHeader(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "TÙY CHỈNH & TÀI KHOẢN",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = KickerBlue
        )
        Text(
            text = "Cài đặt",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Cá nhân hóa giao diện, mục tiêu học hằng ngày và đồng bộ đám mây",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Bộ chọn Giao diện Hệ thống / Sáng / Tối (Figma Node 13:2 & Mục 13).
 */
@Composable
fun ThemeSelectorSection(
    selectedMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "GIAO DIỆN HIỂN THỊ",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = KickerBlue
                )
                Text(
                    text = "Chế độ màu sắc",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Áp dụng tức thì cho toàn bộ màn hình ứng dụng",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ThemeMode.entries.forEach { mode ->
                    val isSelected = mode == selectedMode
                    ThemeModeOptionChip(
                        mode = mode,
                        isSelected = isSelected,
                        onClick = { onSelectThemeMode(mode) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeModeOptionChip(
    mode: ThemeMode,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon: ImageVector = when (mode) {
        ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
        ThemeMode.LIGHT -> Icons.Default.LightMode
        ThemeMode.DARK -> Icons.Default.DarkMode
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            PrimaryBlue.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        },
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) PrimaryBlue else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = mode.labelVi,
                tint = if (isSelected) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = mode.labelVi,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isSelected) PrimaryBlue else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Preview(showBackground = true, name = "Settings Theme Selector - Dark")
@Composable
private fun SettingsThemeDarkPreview() {
    MemoraTheme(darkTheme = true) {
        SettingsScreen(
            uiState = SettingsUiState(
                isLoading = false,
                themeMode = ThemeMode.DARK
            )
        )
    }
}
