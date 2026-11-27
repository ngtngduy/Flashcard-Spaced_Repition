package com.uth.memora.feature.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uth.memora.feature.common.ConfirmDialog
import com.uth.memora.feature.theme.MemoraTheme
import com.uth.memora.integration.auth.GoogleSignInHelper
import kotlinx.coroutines.launch

/**
 * Điểm vào điều hướng cho luồng Xác thực tài khoản (Figma 17:484, 17:449 & 2193:2 · Mục 6.2 & 10.1).
 *
 * Kết nối [AuthViewModel] qua `memoraViewModelFactory` ([AuthViewModel.Factory]), [GoogleSignInHelper],
 * điều phối giữa 3 sub-screen (Login / Register / ForgotPassword), hiển thị hộp thoại gộp dữ liệu Khách
 * khi cần và gọi [onAuthSuccess] khi xác thực hoàn tất.
 */
@Composable
fun AuthRoute(
    onAuthSuccess: () -> Unit,
    onNavigateBack: () -> Unit = onAuthSuccess,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val googleSignInHelper = remember { GoogleSignInHelper() }

    val viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Gọi onAuthSuccess() một lần duy nhất khi xác thực (và gộp dữ liệu khách nếu có) hoàn tất (Mục 6.2)
    LaunchedEffect(uiState.isAuthSuccess) {
        if (uiState.isAuthSuccess) {
            viewModel.consumeAuthSuccess()
            onAuthSuccess()
        }
    }

    // Nhấn nút Back hệ thống ở Register / ForgotPassword sẽ quay về màn Đăng nhập thay vì thoát luồng Auth
    BackHandler(enabled = uiState.currentMode != AuthMode.LOGIN && !uiState.isLoading && !uiState.isGuestLoading) {
        viewModel.switchMode(AuthMode.LOGIN)
    }

    AuthRouteContent(
        uiState = uiState,
        onDisplayNameChange = viewModel::onDisplayNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
        onTogglePasswordVisibility = viewModel::togglePasswordVisibility,
        onToggleConfirmPasswordVisibility = viewModel::toggleConfirmPasswordVisibility,
        onLoginClick = viewModel::submitLogin,
        onRegisterClick = viewModel::submitRegister,
        onSendResetLinkClick = viewModel::submitForgotPassword,
        onGoogleSignInClick = {
            if (!uiState.isLoading && !uiState.isGuestLoading && !uiState.isMergingGuestData) {
                coroutineScope.launch {
                    googleSignInHelper.getGoogleIdToken(context)
                        .onSuccess { idToken -> viewModel.signInWithGoogle(idToken) }
                        .onFailure { error -> viewModel.onGoogleSignInError(error) }
                }
            }
        },
        onSwitchMode = viewModel::switchMode,
        onContinueAsGuestClick = viewModel::continueAsGuest,
        onNavigateBack = onNavigateBack,
        onResolveGuestMerge = viewModel::resolveGuestDataMerge,
        modifier = modifier
    )
}

@Composable
private fun AuthRouteContent(
    uiState: AuthUiState,
    onDisplayNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onToggleConfirmPasswordVisibility: () -> Unit,
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onSendResetLinkClick: () -> Unit,
    onGoogleSignInClick: () -> Unit,
    onSwitchMode: (AuthMode) -> Unit,
    onContinueAsGuestClick: () -> Unit,
    onNavigateBack: () -> Unit,
    onResolveGuestMerge: (discardGuestData: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Crossfade(
        targetState = uiState.currentMode,
        modifier = modifier.fillMaxSize(),
        label = "auth_mode_crossfade"
    ) { mode ->
        when (mode) {
            AuthMode.LOGIN -> {
                LoginScreen(
                    uiState = uiState,
                    onEmailChange = onEmailChange,
                    onPasswordChange = onPasswordChange,
                    onTogglePasswordVisibility = onTogglePasswordVisibility,
                    onLoginClick = onLoginClick,
                    onGoogleSignInClick = onGoogleSignInClick,
                    onForgotPasswordClick = { onSwitchMode(AuthMode.FORGOT_PASSWORD) },
                    onNavigateToRegister = { onSwitchMode(AuthMode.REGISTER) },
                    onContinueAsGuestClick = onContinueAsGuestClick,
                    onBackClick = onNavigateBack
                )
            }

            AuthMode.REGISTER -> {
                RegisterScreen(
                    uiState = uiState,
                    onDisplayNameChange = onDisplayNameChange,
                    onEmailChange = onEmailChange,
                    onPasswordChange = onPasswordChange,
                    onConfirmPasswordChange = onConfirmPasswordChange,
                    onTogglePasswordVisibility = onTogglePasswordVisibility,
                    onToggleConfirmPasswordVisibility = onToggleConfirmPasswordVisibility,
                    onRegisterClick = onRegisterClick,
                    onNavigateToLogin = { onSwitchMode(AuthMode.LOGIN) }
                )
            }

            AuthMode.FORGOT_PASSWORD -> {
                ForgotPasswordScreen(
                    uiState = uiState,
                    onEmailChange = onEmailChange,
                    onSendResetLinkClick = onSendResetLinkClick,
                    onNavigateToLogin = { onSwitchMode(AuthMode.LOGIN) }
                )
            }
        }
    }

    // Hộp thoại xử lý gộp dữ liệu Khách khi đang có dữ liệu cục bộ (Mục 6.2 & 10.1)
    if (uiState.showMergeGuestDialog) {
        ConfirmDialog(
            title = "Gộp dữ liệu khách?",
            message = "Thiết bị đang có dữ liệu học tập ở chế độ Khách. Bạn muốn gộp vào tài khoản này hay bỏ dữ liệu khách?",
            confirmText = "Gộp dữ liệu",
            dismissText = "Bỏ dữ liệu khách",
            isDestructive = false,
            onConfirm = { onResolveGuestMerge(false) },
            onDismiss = { onResolveGuestMerge(true) }
        )
    }
}

@Preview(name = "AuthRoute - Dark", showBackground = true, heightDp = 900)
@Composable
private fun AuthRouteDarkPreview() {
    MemoraTheme(darkTheme = true) {
        AuthRouteContent(
            uiState = AuthUiState(currentMode = AuthMode.LOGIN),
            onDisplayNameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onTogglePasswordVisibility = {},
            onToggleConfirmPasswordVisibility = {},
            onLoginClick = {},
            onRegisterClick = {},
            onSendResetLinkClick = {},
            onGoogleSignInClick = {},
            onSwitchMode = {},
            onContinueAsGuestClick = {},
            onNavigateBack = {},
            onResolveGuestMerge = {}
        )
    }
}
