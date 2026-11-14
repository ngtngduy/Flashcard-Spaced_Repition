package com.uth.memora.core.fake

import com.uth.memora.core.domain.AuthRepository
import com.uth.memora.core.model.UserAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Bản giả lập xác thực người dùng Google / Chế độ khách (In-Memory).
 */
class FakeAuthRepository(
    initialUser: UserAccount? = UserAccount(
        uid = "guest_user_default",
        displayName = "Học viên Memora",
        email = null,
        isAnonymous = true
    )
) : AuthRepository {

    private val _currentUser = MutableStateFlow(initialUser)

    override fun observeCurrentUser(): Flow<UserAccount?> = _currentUser.asStateFlow()

    override suspend fun getCurrentUser(): UserAccount? = _currentUser.value

    override suspend fun signInWithGoogle(idToken: String): Result<UserAccount> {
        val account = UserAccount(
            uid = "google_user_${UUID.randomUUID().toString().take(8)}",
            email = "student@uth.edu.vn",
            displayName = "Sinh viên UTH",
            isAnonymous = false
        )
        _currentUser.value = account
        return Result.success(account)
    }

    override suspend fun signInAnonymously(): Result<UserAccount> {
        val account = UserAccount(
            uid = "guest_${UUID.randomUUID().toString().take(8)}",
            displayName = "Khách ẩn danh",
            email = null,
            isAnonymous = true
        )
        _currentUser.value = account
        return Result.success(account)
    }

    override suspend fun signOut(): Result<Unit> {
        _currentUser.value = null
        return Result.success(Unit)
    }
}
