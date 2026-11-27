package com.uth.memora.feature.auth

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uth.memora.feature.common.MemoraPrimaryButton
import com.uth.memora.feature.theme.MemoraTheme

/**
 * Màn hình Đăng nhập stateless chuẩn Figma Node 17:484 (Mục 6.2 & 11).
 *
 * Gồm nút quay lại, logo Memora, tiêu đề, ô Email, ô Mật khẩu (có nút mắt ẩn/hiện),
 * liên kết "Quên mật khẩu?", nút "Đăng nhập", nút "Tiếp tục với Google",
 * liên kết chuyển sang "Tạo tài khoản mới" và nút dùng thử chế độ Khách.
 */
@Composable
fun LoginScreen(
    uiState: AuthUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onLoginClick: () -> Unit,
    onGoogleSignInClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onContinueAsGuestClick: () -> Unit,
    onBackClick: () -> Unit,
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
            // Nút quay lại góc trên trái (Figma 17:509)
            IconButton(
                onClick = onBackClick,
                enabled = !isBusy,
                modifier = Modifier.padding(start = 0.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Quay lại",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Logo thương hiệu Memora (Mục 6.2)
            MemoraBrandLogo()

            Spacer(modifier = Modifier.height(16.dp))

            // Tiêu đề & mô tả màn hình Đăng nhập (Figma 17:488..490)
            Text(
                text = "Đăng nhập",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Đăng nhập Memora để tiếp tục học tập và ghi nhớ lâu dài.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Khối nhập Email (Figma 17:496..500)
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

            // Khối nhập Mật khẩu có nút mắt ẩn/hiện (Figma 17:501..505 & Mục 11)
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
                        text = "Nhập mật khẩu",
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
                isError = uiState.passwordError != null,
                supportingText = uiState.passwordError?.let { errorText ->
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
                        onLoginClick()
                    }
                )
            )

            // Liên kết "Quên mật khẩu?" căn phải (Figma 2196:2)
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                TextButton(
                    onClick = onForgotPasswordClick,
                    enabled = !isBusy,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Quên mật khẩu?",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Banner hiển thị thông báo lỗi chung tiếng Việt (Mục 6.2 & 9.5)
            if (!uiState.errorMessage.isNullOrBlank()) {
                AuthErrorBanner(
                    message = uiState.errorMessage,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Nút hành động chính "Đăng nhập" (Figma 17:485..487)
            MemoraPrimaryButton(
                text = "Đăng nhập",
                onClick = {
                    focusManager.clearFocus()
                    onLoginClick()
                },
                enabled = !isBusy,
                isLoading = uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Dải phân cách "Hoặc" (Figma 2196:3..6)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Text(
                    text = "Hoặc",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Nút "Tiếp tục với Google" kèm biểu tượng Google 4 màu (Figma 2196:7..13)
            OutlinedButton(
                onClick = onGoogleSignInClick,
                enabled = !isBusy,
                shape = RoundedCornerShape(15.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Icon(
                    imageVector = GoogleBrandIcon,
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Tiếp tục với Google",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Liên kết chuyển sang màn Đăng ký "Tạo tài khoản mới" (Figma 17:506..507)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Chưa có tài khoản? ",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Tạo tài khoản mới",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = !isBusy, onClick = onNavigateToRegister)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Nút dùng thử ở chế độ Khách không cần tài khoản (Figma 2196:14..15 · Mục 6.2)
            OutlinedButton(
                onClick = onContinueAsGuestClick,
                enabled = !isBusy,
                shape = RoundedCornerShape(15.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
            ) {
                Text(
                    text = "Dùng thử không cần tài khoản (Chế độ Khách)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Biểu tượng & tên thương hiệu Memora tái sử dụng trên các màn hình Auth (Mục 6.2).
 */
@Composable
internal fun MemoraBrandLogo(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Style,
                contentDescription = "Logo Memora",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = "Memora",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/**
 * Khung thông báo lỗi chuẩn cho các màn hình Auth (Mục 6.2 & 9.5).
 */
@Composable
internal fun AuthErrorBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
internal fun authTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    disabledContainerColor = MaterialTheme.colorScheme.surface,
    errorContainerColor = MaterialTheme.colorScheme.surface,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    errorBorderColor = MaterialTheme.colorScheme.error,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = MaterialTheme.colorScheme.primary
)

/**
 * Biểu tượng chữ "G" 4 màu chuẩn nhận diện thương hiệu Google trên viewport 24x24 (Figma 2196:8).
 */
internal val GoogleBrandIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "GoogleBrandIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Mảng xanh dương (#4285F4)
        path(fill = SolidColor(Color(0xFF4285F4))) {
            moveTo(22.56f, 12.25f)
            curveTo(22.56f, 11.47f, 22.49f, 10.72f, 22.36f, 10.0f)
            horizontalLineTo(12.0f)
            verticalLineTo(14.26f)
            horizontalLineTo(17.92f)
            curveTo(17.66f, 15.63f, 16.88f, 16.79f, 15.71f, 17.57f)
            verticalLineTo(20.34f)
            horizontalLineTo(19.28f)
            curveTo(21.36f, 18.42f, 22.56f, 15.60f, 22.56f, 12.25f)
            close()
        }
        // Mảng xanh lá (#34A853)
        path(fill = SolidColor(Color(0xFF34A853))) {
            moveTo(12.0f, 23.0f)
            curveTo(14.97f, 23.0f, 17.46f, 22.02f, 19.28f, 20.34f)
            lineTo(15.71f, 17.57f)
            curveTo(14.73f, 18.23f, 13.48f, 18.63f, 12.0f, 18.63f)
            curveTo(9.14f, 18.63f, 6.71f, 16.70f, 5.84f, 14.10f)
            horizontalLineTo(2.18f)
            verticalLineTo(16.94f)
            curveTo(3.99f, 20.53f, 7.70f, 23.0f, 12.0f, 23.0f)
            close()
        }
        // Mảng vàng (#FBBC05)
        path(fill = SolidColor(Color(0xFFFBBC05))) {
            moveTo(5.84f, 14.09f)
            curveTo(5.62f, 13.43f, 5.49f, 12.73f, 5.49f, 12.0f)
            curveTo(5.49f, 11.27f, 5.62f, 10.57f, 5.84f, 9.91f)
            verticalLineTo(7.07f)
            horizontalLineTo(2.18f)
            curveTo(1.43f, 8.55f, 1.0f, 10.22f, 1.0f, 12.0f)
            curveTo(1.0f, 13.78f, 1.43f, 15.45f, 2.18f, 16.93f)
            lineTo(5.84f, 14.09f)
            close()
        }
        // Mảng đỏ (#EA4335)
        path(fill = SolidColor(Color(0xFFEA4335))) {
            moveTo(12.0f, 5.38f)
            curveTo(13.62f, 5.38f, 15.06f, 5.94f, 16.21f, 7.02f)
            lineTo(19.36f, 3.87f)
            curveTo(17.45f, 2.09f, 14.97f, 1.0f, 12.0f, 1.0f)
            curveTo(7.70f, 1.0f, 3.99f, 3.47f, 2.18f, 7.07f)
            lineTo(5.84f, 9.91f)
            curveTo(6.71f, 7.31f, 9.14f, 5.38f, 12.0f, 5.38f)
            close()
        }
    }.build()
}

@Preview(name = "LoginScreen - Dark (Figma 17:484)", showBackground = true, heightDp = 900)
@Composable
private fun LoginScreenDarkPreview() {
    MemoraTheme(darkTheme = true) {
        LoginScreen(
            uiState = AuthUiState(),
            onEmailChange = {},
            onPasswordChange = {},
            onTogglePasswordVisibility = {},
            onLoginClick = {},
            onGoogleSignInClick = {},
            onForgotPasswordClick = {},
            onNavigateToRegister = {},
            onContinueAsGuestClick = {},
            onBackClick = {}
        )
    }
}

@Preview(name = "LoginScreen - Light", showBackground = true, heightDp = 900)
@Composable
private fun LoginScreenLightPreview() {
    MemoraTheme(darkTheme = false) {
        LoginScreen(
            uiState = AuthUiState(
                email = "hocvien@uth.edu.vn",
                password = "password123",
                errorMessage = "Email hoặc mật khẩu không đúng"
            ),
            onEmailChange = {},
            onPasswordChange = {},
            onTogglePasswordVisibility = {},
            onLoginClick = {},
            onGoogleSignInClick = {},
            onForgotPasswordClick = {},
            onNavigateToRegister = {},
            onContinueAsGuestClick = {},
            onBackClick = {}
        )
    }
}
