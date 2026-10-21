import "./globals.css";
import "./studio.css";

export const metadata = {
  title: "Studio AI · Gemini Omni Production Workspace",
  description:
    "Không gian sản xuất AI cho quy trình kịch bản, cảnh quay, phân đoạn và Gemini Omni.",
};

export default function RootLayout({ children }) {
  return (
    <html lang="vi">
      <body suppressHydrationWarning>{children}</body>
    </html>
  );
}
