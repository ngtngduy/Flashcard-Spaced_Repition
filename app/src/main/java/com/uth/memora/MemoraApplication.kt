package com.uth.memora

import android.app.Application
import com.uth.memora.core.fake.FakeMemoraRepository

class MemoraApplication : Application() {

    /**
     * Kho dữ liệu dùng chung.
     * Hiện tại khởi tạo sẵn bằng FakeMemoraRepository để cả 3 thành viên clone về chạy được ngay.
     * Khi Thành viên 1 hoàn tất Room Database, chỉ cần đổi sang RoomMemoraRepository tại đây.
     */
    val repository: FakeMemoraRepository by lazy {
        FakeMemoraRepository()
    }
}
