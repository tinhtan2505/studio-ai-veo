import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Studio AI · VEO Production Workspace",
  description:
    "Không gian sản xuất AI cho quy trình kịch bản, cảnh quay, phân đoạn và VEO.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="vi">
      <body suppressHydrationWarning>{children}</body>
    </html>
  );
}
