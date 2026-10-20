import "./globals.css";
import "./studio.css";

export const metadata = {
  title: "Studio AI · VEO Production Workspace",
  description:
    "Không gian sản xuất AI cho quy trình kịch bản, cảnh quay, phân đoạn và VEO.",
};

export default function RootLayout({ children }) {
  return (
    <html lang="vi">
      <body>{children}</body>
    </html>
  );
}
