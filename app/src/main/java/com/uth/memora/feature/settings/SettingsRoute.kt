package com.uth.memora.feature.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uth.memora.memoraViewModelFactory

/**
 * Điểm vào điều hướng cho tab Cài đặt ứng dụng (Figma Node 13:2 · Mục 6.2, 6.10, 11 & 13).
 *
 * Giữ nguyên chữ ký `fun SettingsRoute(onNavigateToAuth: () -> Unit = {}, modifier: Modifier = Modifier)`
 * để tương thích tuyệt đối với `MemoraNavHost`.
 */
@Composable
fun SettingsRoute(
    onNavigateToAuth: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val viewModel: SettingsViewModel = viewModel(
        factory = memoraViewModelFactory { container ->
            SettingsViewModel(
                userPreferencesRepository = container.userPreferencesRepository,
                authRepository = container.authRepository,
                syncStatusProvider = container.syncStatusProvider,
                cloudSyncManager = container.cloudSyncManager
            )
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.setDailyReminder(enabled = true)
        } else {
            viewModel.onNotificationPermissionDenied()
        }
    }

    SettingsScreen(
        uiState = uiState,
        onNavigateToAuth = onNavigateToAuth,
        onRequestSignOut = viewModel::requestSignOut,
        onConfirmSignOut = viewModel::signOut,
        onDismissSignOutWarning = viewModel::dismissSignOutWarningDialog,
        onSelectThemeMode = viewModel::setThemeMode,
        onDailyNewCardsLimitChange = viewModel::setDailyNewCardsLimit,
        onToggleReminder = { enabled ->
            if (!enabled) {
                viewModel.setDailyReminder(enabled = false)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val permissionStatus = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                )
                if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
                    viewModel.setDailyReminder(enabled = true)
                } else {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                viewModel.setDailyReminder(enabled = true)
            }
        },
        onOpenTimePicker = viewModel::openTimePicker,
        onDismissTimePicker = viewModel::dismissTimePicker,
        onConfirmReminderTime = { hour, minute ->
            viewModel.setDailyReminder(
                enabled = uiState.isReminderEnabled,
                hour = hour,
                minute = minute
            )
        },
        onOpenSystemNotificationSettings = { openAppNotificationSettings(context) },
        onToggleSound = viewModel::setSoundEnabled,
        onSyncNowClick = viewModel::syncNow,
        modifier = modifier
    )
}

private fun openAppNotificationSettings(context: Context) {
    val notificationIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val fallbackIntent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(notificationIntent) }
        .onFailure { runCatching { context.startActivity(fallbackIntent) } }
}
