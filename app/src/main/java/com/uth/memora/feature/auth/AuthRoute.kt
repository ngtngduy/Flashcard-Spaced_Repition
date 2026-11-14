package com.uth.memora.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.uth.memora.feature.common.PlaceholderScreen

@Composable
fun AuthRoute(
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlaceholderScreen(
        title = "Đăng nhập & Tài khoản",
        owner = "Người 3 – AI, Cloud & Tài khoản",
        description = "Đăng nhập Google One-tap, tiếp tục với tư cách Khách và đồng bộ hợp nhất dữ liệu đám mây.",
        modifier = modifier
    )
}
