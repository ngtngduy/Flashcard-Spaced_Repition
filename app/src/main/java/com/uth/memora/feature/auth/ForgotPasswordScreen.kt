package com.uth.memora.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uth.memora.feature.common.MemoraPrimaryButton
import com.uth.memora.feature.theme.MemoraTheme
import com.uth.memora.feature.theme.StatusSuccess
import com.uth.memora.feature.theme.StatusSuccessLight

/**
 * Màn hình Quên mật khẩu stateless chuẩn Figma Node 2193:2 (Mục 6.2).
 *
 * Gồm nút quay lại, logo Memora, hướng dẫn ngắn gọn, ô nhập Email,
 * nút "Gửi liên kết khôi phục", thẻ thông báo thành công khi `resetEmailSent == true`
 * và liên kết quay lại Đăng nhập.
 */
@Composable
fun ForgotPasswordScreen(
    uiState: AuthUiState,
    onEmailChange: (String) -> Unit,
    onSendResetLinkClick: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val isBusy = uiState.isLoading || uiState.isMergingGuestData

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            // Nút quay lại màn Đăng nhập góc trên trái (Figma 2193:21)
            IconButton(
                onClick = onNavigateToLogin,
                enabled = !isBusy
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Quay lại Đăng nhập",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Logo thương hiệu Memora (Mục 6.2)
            MemoraBrandLogo()

            Spacer(modifier = Modifier.height(16.dp))

            // Tiêu đề & hướng dẫn ngắn gọn (Figma 2193:6..8)
            Text(
                text = "Quên mật khẩu",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Nhập địa chỉ email của bạn và chúng tôi sẽ gửi liên kết đặt lại mật khẩu.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Ô nhập Email (Figma 2193:9..13)
            Text(
                text = "Email",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.email,
                onValueChange = onEmailChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy,
                singleLine = true,
                placeholder = {
                    Text(
                        text = "Nhập email của bạn",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                isError = uiState.emailError != null,
                supportingText = uiState.emailError?.let { errorText ->
                    {
                        Text(
                            text = errorText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = authTextFieldColors(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        onSendResetLinkClick()
                    }
                )
            )

            // Banner thông báo lỗi tiếng Việt (Mục 6.2 & 9.5)
            if (!uiState.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                AuthErrorBanner(
                    message = uiState.errorMessage,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Thẻ thông báo thành công khi đã gửi email đặt lại mật khẩu (Mục 6.2)
            if (uiState.resetEmailSent) {
                Spacer(modifier = Modifier.height(16.dp))
                PasswordResetSuccessCard(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Nút hành động chính "Gửi liên kết khôi phục" (Figma 2193:3..5)
            MemoraPrimaryButton(
                text = "Gửi liên kết khôi phục",
                onClick = {
                    focusManager.clearFocus()
                    onSendResetLinkClick()
                },
                enabled = !isBusy,
                isLoading = uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Liên kết quay lại màn Đăng nhập (Figma 2193:19..20)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Nhớ mật khẩu? ",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Đăng nhập",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = !isBusy, onClick = onNavigateToLogin)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Thẻ thông báo gửi email khôi phục mật khẩu thành công (Mục 6.2).
 */
@Composable
private fun PasswordResetSuccessCard(
    modifier: Modifier = Modifier
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accentColor = if (isDarkTheme) StatusSuccess else StatusSuccessLight
    val containerColor = accentColor.copy(alpha = if (isDarkTheme) 0.14f else 0.10f)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MarkEmailRead,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(22.dp)
            )
            Text(
                text = "Đã gửi email đặt lại mật khẩu, hãy kiểm tra hộp thư đến hoặc thư rác",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Preview(name = "ForgotPasswordScreen - Dark (Figma 2193:2)", showBackground = true, heightDp = 820)
@Composable
private fun ForgotPasswordScreenDarkPreview() {
    MemoraTheme(darkTheme = true) {
        ForgotPasswordScreen(
            uiState = AuthUiState(currentMode = AuthMode.FORGOT_PASSWORD),
            onEmailChange = {},
            onSendResetLinkClick = {},
            onNavigateToLogin = {}
        )
    }
}

@Preview(name = "ForgotPasswordScreen - Light (Sent)", showBackground = true, heightDp = 820)
@Composable
private fun ForgotPasswordScreenLightSentPreview() {
    MemoraTheme(darkTheme = false) {
        ForgotPasswordScreen(
            uiState = AuthUiState(
                currentMode = AuthMode.FORGOT_PASSWORD,
                email = "hocvien@uth.edu.vn",
                resetEmailSent = true
            ),
            onEmailChange = {},
            onSendResetLinkClick = {},
            onNavigateToLogin = {}
        )
    }
}
