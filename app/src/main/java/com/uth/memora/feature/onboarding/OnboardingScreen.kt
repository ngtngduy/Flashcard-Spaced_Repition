package com.uth.memora.feature.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uth.memora.feature.common.MemoraPrimaryButton
import com.uth.memora.feature.theme.MemoraTheme
import kotlinx.coroutines.launch

/**
 * Màn hình giới thiệu ứng dụng gồm nút Bỏ qua, HorizontalPager 4 trang,
 * chấm chỉ trang và nút Tiếp theo / Bắt đầu ngay (Figma 2053:63..102 & Mục 6.1).
 */
@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    pages: List<OnboardingPageData> = onboardingPages,
    initialPage: Int = 0,
    onCompleteOnboarding: () -> Unit = {}
) {
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
        pageCount = { pages.size }
    )
    val coroutineScope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage >= pages.lastIndex

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hàng trên cùng chứa nút "Bỏ qua" góc phải, căn lề 24.dp đồng bộ (Figma 2053:75)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCompleteOnboarding) {
                    Text(
                        text = "Bỏ qua",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { pageIndex ->
                OnboardingPage(
                    page = pages[pageIndex],
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OnboardingPageIndicators(
                pageCount = pages.size,
                currentPage = pagerState.currentPage
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Nút hành động dưới cùng: "Tiếp theo" hoặc "Bắt đầu ngay" ở trang cuối (Figma 2053:72 & 2053:111)
            MemoraPrimaryButton(
                text = if (isLastPage) "Bắt đầu ngay" else "Tiếp theo",
                onClick = {
                    if (isLastPage) {
                        onCompleteOnboarding()
                    } else {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Hàng chấm chỉ trang (Page Indicators) nổi bật ở trang hiện tại theo Figma (2053:68..71).
 */
@Composable
fun OnboardingPageIndicators(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isSelected = index == currentPage
            val dotWidth by animateDpAsState(
                targetValue = if (isSelected) 24.dp else 9.dp,
                animationSpec = tween(durationMillis = 250),
                label = "onboarding_indicator_width"
            )
            val dotColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                },
                animationSpec = tween(durationMillis = 250),
                label = "onboarding_indicator_color"
            )

            Box(
                modifier = Modifier
                    .width(dotWidth)
                    .height(9.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
    }
}

@Preview(name = "OnboardingScreen - Dark", showBackground = true, widthDp = 360, heightDp = 740)
@Composable
private fun OnboardingScreenDarkPreview() {
    MemoraTheme(darkTheme = true) {
        OnboardingScreen(initialPage = 0)
    }
}

@Preview(name = "OnboardingScreen - Light", showBackground = true, widthDp = 360, heightDp = 740)
@Composable
private fun OnboardingScreenLightPreview() {
    MemoraTheme(darkTheme = false) {
        OnboardingScreen(initialPage = 3)
    }
}
