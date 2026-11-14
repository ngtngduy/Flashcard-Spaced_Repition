package com.uth.memora.core.fake

import com.uth.memora.core.domain.CloudSyncSummary
import com.uth.memora.core.domain.SyncStatusProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bản giả lập trạng thái đồng bộ đám mây cho màn Cài đặt (In-Memory).
 */
class FakeSyncStatusProvider : SyncStatusProvider {
    private val _summary = MutableStateFlow(
        CloudSyncSummary(
            isSyncing = false,
            pendingChangesCount = 0,
            lastSyncedAtMillis = System.currentTimeMillis() - 3600_000L,
            syncErrorMessage = null
        )
    )

    override fun observeSyncSummary(): Flow<CloudSyncSummary> = _summary.asStateFlow()

    override suspend fun triggerImmediateSync(): Result<Int> {
        _summary.value = _summary.value.copy(isSyncing = true)
        _summary.value = CloudSyncSummary(
            isSyncing = false,
            pendingChangesCount = 0,
            lastSyncedAtMillis = System.currentTimeMillis(),
            syncErrorMessage = null
        )
        return Result.success(0)
    }
}
