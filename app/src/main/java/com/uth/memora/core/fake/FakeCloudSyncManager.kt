package com.uth.memora.core.fake

import com.uth.memora.core.domain.CloudSyncManager

/**
 * Bản giả lập đồng bộ đám mây và gộp dữ liệu khách (In-Memory).
 */
class FakeCloudSyncManager : CloudSyncManager {
    override suspend fun syncPendingChanges(): Result<Int> = Result.success(0)

    override suspend fun mergeGuestDataIntoAccount(
        accountId: String,
        discardGuestData: Boolean
    ): Result<Unit> = Result.success(Unit)
}
