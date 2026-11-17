package com.uth.memora.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.uth.memora.MemoraApplication
import kotlinx.coroutines.launch

/**
 * Route điều hướng cho màn hình Onboarding (Mục 6.1):
 * Khi người dùng bấm "Bắt đầu ngay" hoặc "Bỏ qua", lưu cờ setOnboardingCompleted(true)
 * vào UserPreferencesRepository trước khi chuyển sang Trang chính.
 */
@Composable
fun OnboardingRoute(
    onFinishOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val userPreferencesRepository = remember(context) {
        (context.applicationContext as MemoraApplication).container.userPreferencesRepository
    }
    val coroutineScope = rememberCoroutineScope()

    OnboardingScreen(
        modifier = modifier,
        onCompleteOnboarding = {
            coroutineScope.launch {
                userPreferencesRepository.setOnboardingCompleted(true)
                onFinishOnboarding()
            }
        }
    )
}
