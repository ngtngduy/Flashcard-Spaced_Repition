package com.uth.memora.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import kotlin.math.roundToInt

/**
 * Màn hình Cài đặt ứng dụng (Figma Node 13:2 · Mục 6.2, 6.10, 11 & 13).
 */
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onSelectThemeMode: (ThemeMode) -> Unit = {},
    onDailyNewCardsLimitChange: (Int) -> Unit = {},
    onToggleReminder: (Boolean) -> Unit = {},
    onOpenTimePicker: () -> Unit = {},
    onDismissTimePicker: () -> Unit = {},
    onConfirmReminderTime: (Int, Int) -> Unit = { _, _ -> },
    onOpenSystemNotificationSettings: () -> Unit = {},
    onToggleSound: (Boolean) -> Unit = {},
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
        }

        if (uiState.isTimePickerVisible) {
            ReminderTimePickerDialog(
                initialHour = uiState.reminderHour,
                initialMinute = uiState.reminderMinute,
                onDismiss = onDismissTimePicker,
                onConfirm = onConfirmReminderTime
            )
        }
    }
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
