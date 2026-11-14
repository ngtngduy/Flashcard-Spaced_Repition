## 📋 Mô tả công việc (Summary)
<!-- Tóm tắt ngắn gọn tính năng hoặc lỗi đã xử lý trong PR này -->
- **Task ID:** 
- **Người thực hiện:** 
  - [ ] Người 1 – Trưởng nhóm · Data & Logic
  - [ ] Người 2 – UI & Luồng học
  - [ ] Người 3 – AI, Cloud & Tài khoản
- **Loại thay đổi:**
  - [ ] `feat`: Tính năng mới
  - [ ] `fix`: Sửa lỗi
  - [ ] `refactor`: Tái cấu trúc code (không đổi tính năng)
  - [ ] `style`: Chỉnh giao diện/spacing theo Figma
  - [ ] `chore`: Cấu hình / thư viện / tài liệu

---

## 🔍 Danh sách Commit
<!-- Liệt kê các commit trong PR theo chuẩn Conventional Commits -->
- 

---

## ✅ Bảng kiểm tra trước khi Merge (PR Checklist)
- [ ] **Đúng phạm vi thư mục:** Chỉ sửa file trong thư mục mình sở hữu, không sửa file của thành viên khác.
- [ ] **Không chạm file khóa:** Tuyệt đối không tự ý sửa `core/model/**`, `core/domain/Repositories.kt`, `build.gradle.kts`, `AndroidManifest.xml` (trừ Người 1).
- [ ] **Build & Chạy thử:** Đã Build và Chạy thử trên Android Studio thành công (không có cảnh báo đỏ).
- [ ] **UI Preview (Người 2):** Tất cả Composable mới đều có `@Preview` chạy được với `FakeMemoraRepository`.
- [ ] **Bảo mật & Fallback (Người 3):** Không hardcode API Key; có cơ chế xử lý lỗi/offline khi gọi Gemini hoặc Firebase.
