# Kho Kim Cương

Ứng dụng Android offline để theo dõi số dư và lịch nhận Kim Cương cho người chơi Free Fire. Không cần tài khoản, không cần máy chủ, không có quảng cáo và không yêu cầu quyền Internet.

## Tính năng

- Ghi lần nạp/nhận và chi Kim Cương.
- Số dư, tổng đã nhận, tổng đã chi và mục tiêu cá nhân.
- Lịch sử lưu cục bộ trên máy.
- Chuỗi ngày sử dụng để tạo cảm giác vui khi duy trì thói quen.
- Giao diện 2.0 tối giản cao cấp: nền graphite, chữ trắng ngà, điểm nhấn xanh băng và icon nét mảnh.
- Theo dõi Thẻ Tuần: nhận ngay 100 KC, sau đó 50 KC/ngày trong 7 ngày.
- Theo dõi Thẻ Tháng: nhận ngay 500 KC, sau đó 70 KC/ngày trong 30 ngày.
- Giao diện Jetpack Compose tối ưu điện thoại, hỗ trợ Android 8.0 trở lên và build với target SDK 35 (chạy Android 14).

> Quyền lợi thẻ trong app là cấu hình tham khảo phổ biến (450/2.600 KC). Garena có thể thay đổi sản phẩm; hãy kiểm tra trực tiếp trong game trước khi mua. Ứng dụng chỉ ghi chép, không nạp KC và không liên kết tài khoản game.

## Build

Yêu cầu JDK 17 và Android SDK 35:

```bash
./gradlew testDebugUnitTest assembleDebug
```

APK được tạo tại `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions cũng tự build và cung cấp APK trong mục Artifacts sau mỗi lần đẩy lên `main`.

Phiên bản hiện tại: **2.0.0**.

## Nguồn mở

Dự án được viết lại gọn theo cảm hứng từ [Compose-Expense](https://github.com/wisnukurniawan/Compose-Expense), một ứng dụng Android Jetpack Compose offline phát hành theo Apache 2.0. Xem `NOTICE` và `LICENSE`.

Kho Kim Cương là sản phẩm cộng đồng độc lập, không thuộc hay được Garena/Free Fire bảo trợ. Dự án không chứa logo hoặc tài sản đồ họa của trò chơi.
