# Memora – Ứng dụng học từ vựng Flashcard (SM-2) & Luyện đặt câu với Chatbot AI

**Bài tập lớn nhóm – Môn Lập trình thiết bị di động – Đại học Giao thông Vận tải TP.HCM (UTH)**

Tài liệu đặc tả nghiệp vụ chi tiết nằm trong file [`MEMORA – NGHIỆP VỤ VÀ HƯỚNG TRIỂN KHAI.docx`](./MEMORA%20–%20NGHIỆP%20VỤ%20VÀ%20HƯỚNG%20TRIỂN%20KHAI.docx).

---

## 1. Kiến trúc thư mục & Ranh giới làm việc (3 Thành viên)

Dự án đã được thiết lập sẵn **Clean Architecture + Hợp đồng Interface (`Repositories.kt`) + Kho dữ liệu giả lập (`FakeMemoraRepository.kt`)**. Bất kỳ ai clone về mở bằng Android Studio bấm **Run** là ứng dụng chạy lên ngay lập tức với dữ liệu mẫu.

```text
app/src/main/java/com/uth/memora/
├── core/
│   ├── model/         # [ĐÃ CHỐT CỨNG] Data classes: Card, Deck, ReviewLog, PracticeSentence, 6 RetentionStages
│   ├── domain/        # [ĐÃ CHỐT CỨNG] Interfaces: Repositories.kt & SentenceLocalValidator.kt
│   ├── sm2/           # [THÀNH VIÊN 1] Thuật toán SM-2 Engine & LearningDayCalculator (04:00 AM)
│   ├── fake/          # [DÙNG CHUNG] FakeMemoraRepository & FakeAiGradingService (chạy thử UI ngay không cần chờ DB)
│   └── database/      # [THÀNH VIÊN 1 LÀM TIẾP] Room Entities, DAOs, RoomMemoraRepository
│
├── feature/           # [THÀNH VIÊN 2 LÀM CHỦ] Toàn bộ màn hình Jetpack Compose & ViewModels
│   ├── common/shimmer/# Đã có sẵn Modifier.shimmerEffect() (chu kỳ 1.2s) & AiResultShimmerSkeleton()
│   └── theme/         # Giao diện Sáng / Tối Material 3
│
└── integration/       # [THÀNH VIÊN 3 LÀM CHỦ] AI Gemini SDK, Offline Queue WorkManager, Firebase Sync
    ├── ai/            # GeminiAiGradingService.kt (đã có sẵn Prompt JSON chuẩn)
    └── worker/        # SentenceGradingWorker.kt (Exponential Backoff 15s -> 60s -> 5m)
```

---

## 2. Phân công 3 người & Câu lệnh mẫu (Prompt) cho Google AI Pro

Để AI của cả 3 máy không tự bịa tên hàm gây xung đột, khi nhờ AI viết code, hãy **copy-paste câu lệnh mẫu** tương ứng với vai trò của mình:

### 👤 Thành viên 1: Lead Architecture, SM-2 Engine & Room Database
* **Phạm vi file:** Chỉ làm việc trong `core/database/` và `core/sm2/`.
* **Prompt mẫu cho AI:**
  > "Hãy đọc các model trong `core/model/` và các interface trong `core/domain/Repositories.kt`. Hãy viết Room Entities, DAOs và class `RoomMemoraRepository` trong package `com.uth.memora.core.database` implement đúng các interface đó, giữ nguyên 100% tên hàm và sử dụng `Sm2Engine` có sẵn."

### 👤 Thành viên 2: Mobile UI/UX, Jetpack Compose & TTS
* **Phạm vi file:** Chỉ làm việc trong `feature/`.
* **Prompt mẫu cho AI:**
  > "Hãy đọc `core/domain/Repositories.kt`, `core/model/` và `feature/common/shimmer/ShimmerModifier.kt`. Hãy viết các màn hình Jetpack Compose (Onboarding, Home, Quản lý Bộ từ, Ôn tập Flashcard lật thẻ 3D, Thống kê 6 cấp độ) trong package `com.uth.memora.feature` kết nối qua các hàm của interface trong `Repositories.kt` và dùng `FakeMemoraRepository` để chạy thử."

### 👤 Thành viên 3: Gemini AI, Offline Queue WorkManager & Firebase Cloud
* **Phạm vi file:** Chỉ làm việc trong `integration/`.
* **Prompt mẫu cho AI:**
  > "Hãy đọc `core/domain/Repositories.kt`, `integration/ai/GeminiAiGradingService.kt` và `integration/worker/SentenceGradingWorker.kt`. Hãy hoàn thiện kết nối Google Gemini SDK trả về `AiEvaluationResult`, hàng đợi WorkManager khi mất mạng và đồng bộ Firebase Firestore theo đúng interface đã chốt."

---

## 3. Quy trình Git nhóm (Không bao giờ bị Conflict)

1. **Clone dự án về máy:**
   ```bash
   git clone https://github.com/ngtngduy/Flashcard-Spaced_Repition.git
   cd Flashcard-Spaced_Repition
   git checkout develop
   ```
2. **Tạo nhánh riêng theo vai trò trước khi code:**
   * Thành viên 1: `git checkout -b feature/room-database`
   * Thành viên 2: `git checkout -b feature/compose-ui`
   * Thành viên 3: `git checkout -b feature/gemini-ai-cloud`
3. **Đẩy code của mình lên và tạo Pull Request (PR) vào nhánh `develop`:**
   ```bash
   git add .
   git commit -m "feat: mô tả ngắn gọn phần vừa làm"
   git push -u origin <tên-nhánh-của-bạn>
   ```
