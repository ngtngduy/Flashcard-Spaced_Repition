package com.uth.memora.feature.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

@Composable
fun MemoraApp(
    navController: NavHostController = rememberNavController(),
    startDestination: String = MemoraRoutes.HOME,
    openStudyFromNotification: Boolean = false
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(openStudyFromNotification) {
        if (openStudyFromNotification && startDestination == MemoraRoutes.HOME) {
            navController.navigate(MemoraRoutes.study("all", StudyMode.DUE_REVIEW)) {
                launchSingleTop = true
            }
        }
    }

    // Bảo đảm Bottom Bar chỉ hiện ở đúng 5 tab chính; ẩn ở Onboarding, DeckDetail, CardEditor, Study, Intermission, Auth (Mục 6)
    val isBottomBarVisible = currentRoute != null && topLevelTabs.any { it.route == currentRoute }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (isBottomBarVisible) {
                MemoraBottomBar(
                    currentRoute = currentRoute,
                    onTabSelected = { tab ->
                        if (currentRoute != tab.route) {
                            navController.navigate(tab.route) {
                                popUpTo(MemoraRoutes.HOME) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                slideInHorizontally(
                    animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                    initialOffsetX = { fullWidth -> fullWidth / 6 }
                ) + fadeIn(animationSpec = tween(durationMillis = 250))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 200))
            },
            popEnterTransition = {
                slideInHorizontally(
                    animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                    initialOffsetX = { fullWidth -> -fullWidth / 6 }
                ) + fadeIn(animationSpec = tween(durationMillis = 250))
            },
            popExitTransition = {
                slideOutHorizontally(
                    animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                    targetOffsetX = { fullWidth -> fullWidth / 6 }
                ) + fadeOut(animationSpec = tween(durationMillis = 200))
            }
        ) {
            // Khởi động
            composable(MemoraRoutes.ONBOARDING) {
                OnboardingRoute(
                    onFinishOnboarding = {
                        navController.navigate(MemoraRoutes.HOME) {
                            popUpTo(MemoraRoutes.ONBOARDING) { inclusive = true }
                            launchSingleTop = true
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
                    onBack = { navController.popBackStack() },
                    onNavigateToPractice = {
                        navController.navigate(MemoraRoutes.PRACTICE) {
                            popUpTo(MemoraRoutes.HOME) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
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
