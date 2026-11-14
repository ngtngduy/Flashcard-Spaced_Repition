package com.uth.memora.core.model

/**
 * Thông tin tài khoản người dùng đăng nhập Google hoặc chế độ khách ẩn danh (Mục 13 & Auth).
 */
data class UserAccount(
    val uid: String,
    val email: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val isAnonymous: Boolean = true
)
