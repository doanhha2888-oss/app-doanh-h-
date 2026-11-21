# Dự Án Room Monitor Android (Giao Diện Tự Chọn)

Ứng dụng giám sát đa trình duyệt WebView cho Android, tích hợp bộ chọn giao diện tùy chỉnh và bộ bắt token / phòng tự động.

## 1. Cấu Trúc Dự Án
```
RoomMonitor/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/vn/roommonitor/android/
│   │   │   ├── MainActivity.java      (Đã tích hợp bộ chọn giao diện động)
│   │   │   └── RoomConnection.java    (Xử lý kết nối phòng & dữ liệu)
│   │   └── res/values/
│   │       ├── strings.xml
│   │       └── styles.xml
│   ├── build.gradle
│   └── proguard-rules.pro
├── .github/workflows/build.yml        (Workflow tự động build APK trên GitHub)
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## 2. Tính Năng Giao Diện Tự Chọn Mới Bổ Sung
Trong tab **Cấu hình**, bạn sẽ thấy mục **"Giao diện ứng dụng"**:
- **Theo hệ thống**: Tự động chuyển Light / Dark theo cài đặt máy.
- **Cyber Dark**: Phối màu xanh đêm hiện đại dịu mắt.
- **Clean Light**: Phong cách tối giản, sáng sủa, độ tương phản cao.
- **OLED Pitch Black**: Nền đen tuyệt đối `#000000` giúp tiết kiệm pin cho màn hình AMOLED.
- **Emerald Forest**: Tông xanh ngọc thiên nhiên dịu mắt khi theo dõi lâu.
- **Royal Violet**: Tông tím hoàng gia cá tính.
*Cấu hình giao diện và token sẽ được lưu tự động vào SharedPreferences.*

## 3. Cách Build Thành File APK

### Cách 1: Sử dụng Android Studio (Khuyên dùng)
1. Mở **Android Studio**.
2. Chọn **Open** -> Trỏ tới thư mục: `C:\Users\Administrator\.gemini\antigravity\scratch\RoomMonitor`.
3. Chờ Gradle tải thư viện và đồng bộ (Sync Project).
4. Trên thanh menu, chọn:
   **Build** > **Build Bundle(s) / APK(s)** > **Build APK(s)**.
5. Sau khi build xong, nhấn **locate** để lấy file `app-debug.apk` cài vào điện thoại hoặc giả lập (LDPlayer).

### Cách 2: Tự Động Build Online Bằng GitHub (Không cần cài Android Studio)
1. Tạo một repository mới trên GitHub (Private hoặc Public).
2. Đẩy toàn bộ thư mục `RoomMonitor` này lên repository.
3. Vào tab **Actions** trên GitHub, workflow **Build Android APK** sẽ tự động chạy trong khoảng 2 phút.
4. Sau khi hoàn tất, tải file `RoomMonitor-Debug-APK.zip` ở phần Artifacts về và giải nén là có ngay file `.apk`.
