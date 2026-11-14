package com.uth.memora.feature.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.uth.memora.core.model.StudyMode
import com.uth.memora.feature.analytics.AnalyticsRoute
import com.uth.memora.feature.auth.AuthRoute
import com.uth.memora.feature.cardeditor.CardEditorRoute
import com.uth.memora.feature.decks.DeckDetailRoute
import com.uth.memora.feature.decks.DecksRoute
import com.uth.memora.feature.home.HomeRoute
import com.uth.memora.feature.onboarding.OnboardingRoute
import com.uth.memora.feature.practice.PracticeRoute
import com.uth.memora.feature.settings.SettingsRoute
import com.uth.memora.feature.study.IntermissionRoute
import com.uth.memora.feature.study.StudyRoute

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

@Composable
fun MemoraApp(
    navController: NavHostController = rememberNavController(),
    startDestination: String = MemoraRoutes.HOME
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Chỉ hiển thị Bottom Navigation ở 5 tab chính
    val isBottomBarVisible = topLevelTabs.any { it.route == currentRoute }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (isBottomBarVisible) {
                NavigationBar {
                    topLevelTabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            // Khởi động
            composable(MemoraRoutes.ONBOARDING) {
                OnboardingRoute(
                    onFinishOnboarding = {
                        navController.navigate(MemoraRoutes.HOME) {
                            popUpTo(MemoraRoutes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }

            // 5 Tab chính
            composable(MemoraRoutes.HOME) {
                HomeRoute(
                    onNavigateToStudy = { deckId, mode ->
                        navController.navigate(MemoraRoutes.study(deckId, mode))
                    },
                    onNavigateToDeckDetail = { deckId ->
                        navController.navigate(MemoraRoutes.deckDetail(deckId))
                    },
                    onNavigateToAddCard = {
                        navController.navigate(MemoraRoutes.cardEditor("deck_everyday"))
                    },
                    onNavigateToAuth = {
                        navController.navigate(MemoraRoutes.AUTH)
                    }
                )
            }

            composable(MemoraRoutes.DECKS) {
                DecksRoute(
                    onDeckClick = { deckId -> navController.navigate(MemoraRoutes.deckDetail(deckId)) },
                    // Mở dialog Tạo bộ từ trực tiếp trên DeckListScreen thay vì chuyển sang màn thêm thẻ
                    onCreateDeckClick = {}
                )
            }

            composable(MemoraRoutes.PRACTICE) {
                PracticeRoute(
                    onNavigateToAuth = { navController.navigate(MemoraRoutes.AUTH) }
                )
            }

            composable(MemoraRoutes.ANALYTICS) {
                AnalyticsRoute()
            }

            composable(MemoraRoutes.SETTINGS) {
                SettingsRoute(
                    onNavigateToAuth = { navController.navigate(MemoraRoutes.AUTH) }
                )
            }

            // Màn hình chi tiết bộ từ & thẻ
            composable(
                route = MemoraRoutes.DECK_DETAIL,
                arguments = listOf(navArgument("deckId") { type = NavType.StringType })
            ) { backStackEntry ->
                val deckId = backStackEntry.arguments?.getString("deckId") ?: ""
                DeckDetailRoute(
                    deckId = deckId,
                    onStartStudy = { navController.navigate(MemoraRoutes.study(deckId)) },
                    onAddCard = { navController.navigate(MemoraRoutes.cardEditor(deckId)) },
                    onEditCard = { dId, cId -> navController.navigate(MemoraRoutes.cardEditor(dId, cId)) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = MemoraRoutes.CARD_EDITOR,
                arguments = listOf(
                    navArgument("deckId") { type = NavType.StringType },
                    navArgument("cardId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val deckId = backStackEntry.arguments?.getString("deckId") ?: ""
                val cardId = backStackEntry.arguments?.getString("cardId")
                CardEditorRoute(
                    deckId = deckId,
                    cardId = cardId,
                    onCardSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }

            // Phiên học SM-2 & Nghỉ giữa chặng
            composable(
                route = MemoraRoutes.STUDY,
                arguments = listOf(
                    navArgument("deckId") { type = NavType.StringType },
                    navArgument("mode") {
                        type = NavType.StringType
                        defaultValue = StudyMode.DUE_REVIEW.name
                    }
                )
            ) { backStackEntry ->
                val deckId = backStackEntry.arguments?.getString("deckId") ?: ""
                val modeName = backStackEntry.arguments?.getString("mode") ?: StudyMode.DUE_REVIEW.name
                val studyMode = runCatching { StudyMode.valueOf(modeName) }.getOrDefault(StudyMode.DUE_REVIEW)
                StudyRoute(
                    deckId = deckId,
                    studyMode = studyMode,
                    onIntermission = { navController.navigate(MemoraRoutes.intermission(deckId)) },
                    onFinishSession = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = MemoraRoutes.INTERMISSION,
                arguments = listOf(navArgument("deckId") { type = NavType.StringType })
            ) { backStackEntry ->
                val deckId = backStackEntry.arguments?.getString("deckId") ?: ""
                IntermissionRoute(
                    deckId = deckId,
                    onResumeStudy = { navController.popBackStack() },
                    onFinishEarly = {
                        navController.popBackStack(MemoraRoutes.HOME, inclusive = false)
                    }
                )
            }

            // Đăng nhập
            composable(MemoraRoutes.AUTH) {
                AuthRoute(
                    onAuthSuccess = { navController.popBackStack() }
                )
            }
        }
    }
}
