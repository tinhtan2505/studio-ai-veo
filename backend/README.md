# Studio AI Gemini Omni backend

Dịch vụ Spring Boot local, một người dùng, dành riêng cho
`gemini-omni-1.1-flash`. Backend dùng Gemini Interactions API và Files API,
chạy tối đa hai job đồng thời, rồi lưu MP4 cùng sidecar JSON ở
`data/omni-output`.

## Chạy local

Yêu cầu Java 21. API key chỉ được đọc từ biến môi trường:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
$env:GEMINI_API_KEY = "your-rotated-key"
.\gradlew.bat bootRun
```

Server mặc định chạy tại `http://localhost:6661`. Không đưa backend này ra
Internet: ứng dụng không có auth, database hoặc CORS công khai.

Nếu repository từng chứa API key/credential, phải thu hồi và tạo key mới. Việc
xóa secret khỏi HEAD không xóa nó khỏi lịch sử Git.

## API

- `GET /api/omni/config`
- `POST /api/omni/media`, `GET/DELETE /api/omni/media/{fileId}`
- `POST /api/omni/jobs`, `GET /api/omni/jobs/{jobId}`
- `GET/DELETE /api/omni/outputs/{interactionId}` (hỗ trợ HTTP Range)
- `DELETE /api/omni/interactions/{interactionId}`

Không có alias tương thích ngược. Media trên Files API có thể hết hạn; output
local chỉ bị xóa khi người dùng yêu cầu.

## Kiểm tra

```powershell
.\gradlew.bat test
```

Test dùng upstream giả lập và không gọi video generation có tính phí.
