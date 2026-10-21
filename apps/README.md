# Studio AI Gemini Omni Frontend

Frontend Next.js App Router cho quy trình Phase/shot và Gemini Omni 1.1 Flash,
chạy tại `http://localhost:6660`.

Mỗi shot có ba luồng Tạo, Chỉnh sửa và Mở rộng; media tray theo vai trò; prompt
tags tự động; output/timeline nhiều lượt và khả năng tải lại MP4 local để tiếp
tục sau khi Files API hết hạn.

## Chạy local

```bash
npm install
npm run dev
```

Proxy `/api/omni/**` mặc định trỏ tới `http://localhost:6661`. Có thể đổi bằng
`STUDIO_BACKEND_URL`. Trình duyệt không lưu API key hay base64 video.

## Cấu trúc chính

- `app/_components/`: React shell và các panel nghiệp vụ.
- `public/omni-core.js`: request builder, role/tag compiler và state reducer thuần.
- `public/studio-runtime.js`: parser Phase, DOM/render shot và lời gọi API.
- `tests/omni-core.test.js`: unit test cho compiler, validation và timeline.

## Kiểm tra

```bash
npm test
npm run test:browser
npm run lint
npm run build
```

Browser acceptance dùng backend giả lập và Chrome hệ thống. Các lệnh này không tạo video thật.
