package com.uth.memora.feature.practice

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.uth.memora.feature.theme.MemoraTheme

/** Thông điệp giải thích quyền Micro theo nghiệp vụ nhập giọng nói (Mục 6.7 & 9.5). */
const val MIC_PERMISSION_RATIONALE_VI: String = "Cần quyền Micro để đọc câu tiếng Anh"

/**
 * Bộ giữ trạng thái quyền [Manifest.permission.RECORD_AUDIO] cho màn Luyện đặt câu (Mục 6.7).
 * Quản lý việc xin quyền, nhận biết trường hợp từ chối vĩnh viễn và mở Cài đặt ứng dụng.
 */
@Stable
class MicPermissionState internal constructor(
    initialHasPermission: Boolean,
    private val onLaunchRequest: () -> Unit,
    private val onAlreadyGranted: () -> Unit,
    private val onOpenAppSettings: () -> Unit,
    private val checkLivePermission: () -> Boolean
) {
    /** Người dùng đã cấp quyền ghi âm micro hay chưa. */
    var hasPermission: Boolean by mutableStateOf(initialHasPermission)
        internal set

    /** `true` khi người dùng từ chối kèm không hỏi lại và cần bật trong Cài đặt ứng dụng. */
    var isPermanentlyDenied: Boolean by mutableStateOf(false)
        internal set

    /** Điều khiển hiển thị hộp thoại giải thích khi quyền Micro bị từ chối. */
    var showDeniedDialog: Boolean by mutableStateOf(false)
        internal set

    /**
     * Yêu cầu quyền Micro; nếu đã được cấp sẽ thực thi ngay callback `onPermissionGranted` (Mục 6.7).
     */
    fun requestPermission() {
        val grantedNow = checkLivePermission()
        hasPermission = grantedNow
        if (grantedNow) {
            isPermanentlyDenied = false
            showDeniedDialog = false
            onAlreadyGranted()
            return
        }
        onLaunchRequest()
    }

    /** Đóng hộp thoại giải thích quyền Micro. */
    fun dismissDeniedDialog() {
        showDeniedDialog = false
    }

    /** Đóng hộp thoại (nếu đang mở) và điều hướng sang Cài đặt chi tiết của ứng dụng Memora. */
    fun openAppSettings() {
        showDeniedDialog = false
        onOpenAppSettings()
    }

    internal fun onPermissionResult(granted: Boolean, canShowRationale: Boolean) {
        hasPermission = granted
        if (granted) {
            isPermanentlyDenied = false
            showDeniedDialog = false
        } else {
            isPermanentlyDenied = !canShowRationale
            showDeniedDialog = true
        }
    }

    internal fun refreshFromSystem() {
        val grantedNow = checkLivePermission()
        hasPermission = grantedNow
        if (grantedNow) {
            isPermanentlyDenied = false
            showDeniedDialog = false
        }
    }
}

/**
 * Composable helper ghi nhớ trạng thái quyền [Manifest.permission.RECORD_AUDIO] (Mục 6.7 & 9.5).
 *
 * - Dùng [rememberLauncherForActivityResult] với [ActivityResultContracts.RequestPermission].
 * - Tự đồng bộ lại trạng thái quyền ở `ON_RESUME` khi người dùng quay về từ Cài đặt ứng dụng.
 * - Khi bị từ chối, bật `state.showDeniedDialog = true` để hiện [MicPermissionDeniedDialog]
 *   (hoặc gọi [onPermissionDenied] nếu màn hình muốn hiển thị Snackbar).
 */
@Composable
fun rememberMicPermissionState(
    onPermissionGranted: () -> Unit = {},
    onPermissionDenied: ((isPermanentlyDenied: Boolean) -> Unit)? = null
): MicPermissionState {
    val context = LocalContext.current
    val latestOnGrantedState = rememberUpdatedState(onPermissionGranted)
    val latestOnDeniedState = rememberUpdatedState(onPermissionDenied)

    val launchActionHolder = remember {
        object {
            var launch: (() -> Unit)? = null
        }
    }

    val state = remember(context) {
        MicPermissionState(
            initialHasPermission = hasRecordAudioPermission(context),
            onLaunchRequest = { launchActionHolder.launch?.invoke() },
            onAlreadyGranted = { latestOnGrantedState.value.invoke() },
            onOpenAppSettings = { openAppSettings(context) },
            checkLivePermission = { hasRecordAudioPermission(context) }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val activity = context.findActivity()
        val canShowRationale = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(
                it,
                Manifest.permission.RECORD_AUDIO
            )
        } ?: false

        state.onPermissionResult(
            granted = isGranted,
            canShowRationale = canShowRationale
        )

        if (isGranted) {
            latestOnGrantedState.value.invoke()
        } else {
            latestOnDeniedState.value?.invoke(!canShowRationale)
        }
    }

    SideEffect {
        launchActionHolder.launch = { launcher.launch(Manifest.permission.RECORD_AUDIO) }
    }

    // Đồng bộ lại trạng thái quyền khi quay về từ màn Cài đặt ứng dụng (Mục 6.7)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        state.refreshFromSystem()
    }

    return state
}

/**
 * Hộp thoại giải thích quyền Micro gắn trực tiếp với [MicPermissionState] (Mục 6.7 & 9.5).
 * Chỉ hiển thị khi người dùng vừa từ chối quyền ghi âm.
 */
@Composable
fun MicPermissionDeniedDialog(
    state: MicPermissionState,
    modifier: Modifier = Modifier
) {
    if (!state.showDeniedDialog) return

    MicPermissionDeniedDialog(
        isPermanentlyDenied = state.isPermanentlyDenied,
        onRequestAgain = {
            state.dismissDeniedDialog()
            state.requestPermission()
        },
        onOpenSettings = state::openAppSettings,
        onDismiss = state::dismissDeniedDialog,
        modifier = modifier
    )
}

/**
 * Hộp thoại Material 3 không trạng thái giải thích lý do cần quyền Micro (Mục 6.7 & 9.5).
 * Hiển thị nút "Cấp quyền" nếu còn hỏi lại được, hoặc "Mở Cài đặt" nếu bị từ chối vĩnh viễn.
 */
@Composable
fun MicPermissionDeniedDialog(
    isPermanentlyDenied: Boolean,
    onRequestAgain: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val detailMessage = if (isPermanentlyDenied) {
        "$MIC_PERMISSION_RATIONALE_VI. Bạn đã tắt quyền này trước đó, hãy mở Cài đặt ứng dụng để bật lại Micro."
    } else {
        "$MIC_PERMISSION_RATIONALE_VI thay vì gõ phím. Bạn có thể cấp quyền ngay hoặc bật sau trong Cài đặt ứng dụng."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        iconContentColor = MaterialTheme.colorScheme.primary,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        icon = {
            Icon(
                imageVector = Icons.Rounded.MicOff,
                contentDescription = "Chưa có quyền Micro"
            )
        },
        title = {
            Text(
                text = "Cần quyền truy cập Micro",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = detailMessage,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = if (isPermanentlyDenied) onOpenSettings else onRequestAgain,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = if (isPermanentlyDenied) "Mở Cài đặt" else "Cấp quyền",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Để sau",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

/**
 * Tiện ích hiện Snackbar tiếng Việt giải thích quyền Micro kèm nút mở Cài đặt ứng dụng (Mục 6.7 & 9.5).
 */
suspend fun SnackbarHostState.showMicPermissionDeniedSnackbar(
    onOpenSettings: () -> Unit
) {
    val result = showSnackbar(
        message = MIC_PERMISSION_RATIONALE_VI,
        actionLabel = "Cài đặt",
        withDismissAction = true,
        duration = SnackbarDuration.Long
    )
    if (result == SnackbarResult.ActionPerformed) {
        onOpenSettings()
    }
}

/** Kiểm tra ứng dụng hiện đã có quyền [Manifest.permission.RECORD_AUDIO] hay chưa (Mục 6.7). */
fun hasRecordAudioPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

/** Mở trang Cài đặt chi tiết của ứng dụng Memora để người dùng bật lại quyền Micro (Mục 6.7). */
fun openAppSettings(context: Context) {
    val appSettingsIntent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).apply {
        if (context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    runCatching {
        context.startActivity(appSettingsIntent)
    }.onFailure {
        // Dự phòng mở trang Cài đặt chung nếu thiết bị không xử lý được trang chi tiết ứng dụng
        val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        runCatching { context.startActivity(fallbackIntent) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Preview(name = "MicPermissionDeniedDialog - Ask Again (Dark)", showBackground = true)
@Composable
private fun MicPermissionAskAgainDarkPreview() {
    MemoraTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            MicPermissionDeniedDialog(
                isPermanentlyDenied = false,
                onRequestAgain = {},
                onOpenSettings = {},
                onDismiss = {}
            )
        }
    }
}

@Preview(name = "MicPermissionDeniedDialog - Open Settings (Light)", showBackground = true)
@Composable
private fun MicPermissionOpenSettingsLightPreview() {
    MemoraTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            MicPermissionDeniedDialog(
                isPermanentlyDenied = true,
                onRequestAgain = {},
                onOpenSettings = {},
                onDismiss = {}
            )
        }
    }
}
