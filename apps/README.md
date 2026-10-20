# Studio AI VEO Frontend

Frontend Next.js App Router dùng JavaScript và React component, chạy tại
`http://localhost:6660`.

## Chạy local

```bash
npm install
npm run dev
```

Backend VEO mặc định được proxy tới `http://localhost:6661`. Có thể đổi bằng
biến môi trường `STUDIO_BACKEND_URL`.

## Cấu trúc chính

- `app/page.js`: route trang chủ.
- `app/_components/`: các component giao diện theo từng khu vực nghiệp vụ.
- `app/_data/reference-content.js`: tài liệu vận hành tĩnh.
- `app/studio.css`: giao diện responsive của workspace.
- `public/studio-runtime.js`: parser và luồng nghiệp vụ Phase 5 / VEO.
- `public/samples/`: gói dữ liệu mẫu dùng để kiểm thử nhanh.

## Kiểm tra

```bash
npm run lint
npm run build
```
