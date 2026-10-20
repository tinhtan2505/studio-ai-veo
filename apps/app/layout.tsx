import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Studio AI Prompt System",
  description:
    "Studio AI production prompt system for screenplay, shot and segment workflows.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="vi">
      <body suppressHydrationWarning>{children}</body>
    </html>
  );
}
