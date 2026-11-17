package com.uth.memora.feature.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uth.memora.feature.theme.MemoraTheme

/**
 * Thông tin từng tab trên thanh điều hướng dưới (Mục 6 & 11).
 */
data class NavigationTab(
    val route: String,
    val title: String,
    val icon: ImageVector
)

val topLevelTabs = listOf(
    NavigationTab(MemoraRoutes.HOME, "Trang chính", Icons.Default.Home),
    NavigationTab(MemoraRoutes.DECKS, "Bộ từ", Icons.AutoMirrored.Filled.LibraryBooks),
    NavigationTab(MemoraRoutes.PRACTICE, "Luyện tập", Icons.Default.ChatBubble),
    NavigationTab(MemoraRoutes.ANALYTICS, "Thống kê", Icons.Default.BarChart),
    NavigationTab(MemoraRoutes.SETTINGS, "Cài đặt", Icons.Default.Settings)
)

/**
 * Thanh Bottom Bar 5 tab chuẩn Figma (Node 2113:4):
 * - Nền surface (#1E1E1E ở Dark / #FFFFFF ở Light), viền trên mảnh 1.dp
 * - Tab đang chọn nổi bật bằng màu primary (#417DFF) và nền pill mờ 12%
 * - Tab chưa chọn dùng màu onSurfaceVariant (#ACACAC)
 */
@Composable
fun MemoraBottomBar(
    currentRoute: String?,
    onTabSelected: (NavigationTab) -> Unit,
    modifier: Modifier = Modifier,
    tabs: List<NavigationTab> = topLevelTabs
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { onTabSelected(tab) },
                    icon = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title
                        )
                    },
                    label = {
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Preview(name = "BottomBar - Dark", showBackground = true)
@Composable
private fun MemoraBottomBarDarkPreview() {
    MemoraTheme(darkTheme = true) {
        MemoraBottomBar(
            currentRoute = MemoraRoutes.HOME,
            onTabSelected = {}
        )
    }
}

@Preview(name = "BottomBar - Light", showBackground = true)
@Composable
private fun MemoraBottomBarLightPreview() {
    MemoraTheme(darkTheme = false) {
        MemoraBottomBar(
            currentRoute = MemoraRoutes.DECKS,
            onTabSelected = {}
        )
    }
}
