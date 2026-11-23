package com.uth.memora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uth.memora.core.model.ThemeMode
import com.uth.memora.feature.navigation.MemoraApp
import com.uth.memora.feature.navigation.MemoraRoutes
import com.uth.memora.feature.theme.MemoraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val userPreferencesRepository = (application as MemoraApplication).container.userPreferencesRepository

        setContent {
            // Quan sát cài đặt người dùng từ DataStore / FakeRepository (Mục 11 & 13)
            val userPreferences by userPreferencesRepository
                .observeUserPreferences()
                .collectAsStateWithLifecycle(initialValue = null)

            val systemDarkTheme = isSystemInDarkTheme()
            val darkTheme = when (userPreferences?.themeMode ?: ThemeMode.SYSTEM) {
                ThemeMode.SYSTEM -> systemDarkTheme
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            MemoraTheme(darkTheme = darkTheme) {
                val prefs = userPreferences
                if (prefs == null) {
                    // Tránh nháy sai màn hình trong lúc đợi lần phát đầu tiên của DataStore
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {}
                } else {
                    // Cố định startDestination lúc khởi động phiên để không bị reset NavHost khi cập nhật cờ
                    val initialDestination = rememberSaveable {
                        if (prefs.isOnboardingCompleted) {
                            MemoraRoutes.HOME
                        } else {
                            MemoraRoutes.ONBOARDING
                        }
                    }
                    MemoraApp(startDestination = initialDestination)
                }
            }
        }
    }
}
