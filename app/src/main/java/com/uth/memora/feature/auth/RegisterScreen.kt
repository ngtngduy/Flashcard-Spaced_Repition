package com.uth.memora.feature.auth

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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uth.memora.feature.common.MemoraPrimaryButton
import com.uth.memora.feature.theme.MemoraTheme

/**
 * Màn hình Đăng ký tài khoản stateless chuẩn Figma Node 17:449 (Mục 6.2 & 11).
 *
 * Gồm nút quay lại Đăng nhập, logo Memora, ô Tên hiển thị (`displayName`), Email,
 * Mật khẩu (`>= 8` ký tự), Xác nhận mật khẩu (hiển thị lỗi inline tiếng Việt tức thì),
 * nút "Đăng ký", liên kết quay lại Đăng nhập và điều khoản sử dụng.
 */
@Composable
fun RegisterScreen(
    uiState: AuthUiState,
    onDisplayNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onToggleConfirmPasswordVisibility: () -> Unit,
    onRegisterClick: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val isBusy = uiState.isLoading || uiState.isMergingGuestData

    // Kiểm tra lỗi inline tiếng Việt cho mật khẩu < 8 ký tự và xác nhận mật khẩu chưa khớp (Mục 6.2 & 11)
    val inlinePasswordError = uiState.passwordError
        ?: if (uiState.password.isNotEmpty() && uiState.password.length < AuthViewModel.MIN_PASSWORD_LENGTH) {
            AuthViewModel.ERROR_SHORT_PASSWORD_VI
        } else {
            null
        }
    val inlineConfirmPasswordError = uiState.confirmPasswordError
        ?: if (uiState.confirmPassword.isNotEmpty() && uiState.confirmPassword != uiState.password) {
            AuthViewModel.ERROR_PASSWORD_MISMATCH_VI
        } else {
            null
        }

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
            // Nút quay lại màn Đăng nhập góc trên trái (Figma 17:481)
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

            // Tiêu đề & mô tả màn Tạo tài khoản (Figma 17:482, 17:462..463)
            Text(
                text = "Tạo tài khoản",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tham gia Memora để học hiệu quả và ghi nhớ lâu dài.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Ô Tên hiển thị / Họ và tên (Figma 17:464..468)
            Text(
                text = "Họ và tên",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.displayName,
                onValueChange = onDisplayNameChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy,
                singleLine = true,
                placeholder = {
                    Text(
                        text = "Nhập họ và tên",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                isError = uiState.displayNameError != null,
                supportingText = uiState.displayNameError?.let { errorText ->
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
                    capitalization = KeyboardCapitalization.Words,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Ô Email (Figma 17:469..473)
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
                        text = "Nhập địa chỉ email",
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
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Ô Mật khẩu tối thiểu 8 ký tự (Figma 17:474..478 & Mục 11)
            Text(
                text = "Mật khẩu (tối thiểu 8 ký tự)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.password,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy,
                singleLine = true,
                placeholder = {
                    Text(
                        text = "Tạo mật khẩu",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                visualTransformation = if (uiState.isPasswordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(
                        onClick = onTogglePasswordVisibility,
                        enabled = !isBusy
                    ) {
                        Icon(
                            imageVector = if (uiState.isPasswordVisible) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = if (uiState.isPasswordVisible) {
                                "Ẩn mật khẩu"
                            } else {
                                "Hiện mật khẩu"
                            },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                isError = inlinePasswordError != null,
                supportingText = inlinePasswordError?.let { errorText ->
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
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Ô Xác nhận mật khẩu (Mục 6.2 & 11)
            Text(
                text = "Xác nhận mật khẩu",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.confirmPassword,
                onValueChange = onConfirmPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy,
                singleLine = true,
                placeholder = {
                    Text(
                        text = "Nhập lại mật khẩu",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                visualTransformation = if (uiState.isConfirmPasswordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(
                        onClick = onToggleConfirmPasswordVisibility,
                        enabled = !isBusy
                    ) {
                        Icon(
                            imageVector = if (uiState.isConfirmPasswordVisible) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = if (uiState.isConfirmPasswordVisible) {
                                "Ẩn mật khẩu xác nhận"
                            } else {
                                "Hiện mật khẩu xác nhận"
                            },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                isError = inlineConfirmPasswordError != null,
                supportingText = inlineConfirmPasswordError?.let { errorText ->
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
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        onRegisterClick()
                    }
                )
            )

            // Banner hiển thị thông báo lỗi chung tiếng Việt từ Firebase (Mục 6.2 & 9.5)
            if (!uiState.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                AuthErrorBanner(
                    message = uiState.errorMessage,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Nút hành động chính "Đăng ký" (Figma 17:458..460)
            MemoraPrimaryButton(
                text = "Đăng ký",
                onClick = {
                    focusManager.clearFocus()
                    onRegisterClick()
                },
                enabled = !isBusy,
                isLoading = uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Liên kết quay lại màn Đăng nhập (Figma 17:479)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Đã có tài khoản? ",
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

            Spacer(modifier = Modifier.height(24.dp))

            // Lưu ý điều khoản dịch vụ & chính sách quyền riêng tư (Figma 17:480)
            Text(
                text = "Khi tạo tài khoản, bạn đồng ý với Điều khoản dịch vụ và Chính sách quyền riêng tư của chúng tôi.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Preview(name = "RegisterScreen - Dark (Figma 17:449)", showBackground = true, heightDp = 920)
@Composable
private fun RegisterScreenDarkPreview() {
    MemoraTheme(darkTheme = true) {
        RegisterScreen(
            uiState = AuthUiState(currentMode = AuthMode.REGISTER),
            onDisplayNameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onTogglePasswordVisibility = {},
            onToggleConfirmPasswordVisibility = {},
            onRegisterClick = {},
            onNavigateToLogin = {}
        )
    }
}

@Preview(name = "RegisterScreen - Light (Inline Errors)", showBackground = true, heightDp = 960)
@Composable
private fun RegisterScreenLightErrorsPreview() {
    MemoraTheme(darkTheme = false) {
        RegisterScreen(
            uiState = AuthUiState(
                currentMode = AuthMode.REGISTER,
                displayName = "Nguyễn Văn A",
                email = "hocvien@uth.edu.vn",
                password = "12345",
                confirmPassword = "123456"
            ),
            onDisplayNameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onTogglePasswordVisibility = {},
            onToggleConfirmPasswordVisibility = {},
            onRegisterClick = {},
            onNavigateToLogin = {}
        )
    }
}
