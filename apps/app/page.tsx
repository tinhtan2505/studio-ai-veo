"use client";

import { useRef, useState } from "react";

const SAMPLE_BUNDLE =
  "/samples/BAY_MANH_NHAN_TAM_EP04_PHASE5_ALL_SEG_RECOMPILED_V9.txt";

export default function Home() {
  const frameRef = useRef<HTMLIFrameElement>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isReady, setIsReady] = useState(false);

  async function loadSampleBundle() {
    const frameDocument = frameRef.current?.contentDocument;
    const fileInput = frameDocument?.querySelector<HTMLInputElement>("#fileInput");

    if (!fileInput) {
      return;
    }

    setIsLoading(true);
    try {
      const response = await fetch(SAMPLE_BUNDLE);
      if (!response.ok) {
        throw new Error(`Không tải được gói mẫu (${response.status})`);
      }

      const file = new File(
        [await response.text()],
        "BAY_MANH_NHAN_TAM_EP04_PHASE5_ALL_SEG_RECOMPILED_V9.txt",
        { type: "text/plain" },
      );
      const transfer = new DataTransfer();
      transfer.items.add(file);
      fileInput.files = transfer.files;
      fileInput.dispatchEvent(new Event("change", { bubbles: true }));
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <main className="studio-shell">
      <div className="studio-toolbar">
        <span className="studio-toolbar-title">Studio AI · VEO production workspace</span>
        <span className="studio-toolbar-status">
          {isReady ? "Studio đã sẵn sàng" : "Đang khởi tạo Studio"}
        </span>
        <button
          className="studio-sample-button"
          type="button"
          onClick={loadSampleBundle}
          disabled={!isReady || isLoading}
        >
          {isLoading ? "Đang nạp gói…" : "Nạp gói Phase 5 mẫu"}
        </button>
      </div>
      <iframe
        ref={frameRef}
        className="studio-frame"
        src="/studio-ai-prompt-system.html"
        title="Studio AI Prompt System"
        allow="clipboard-read; clipboard-write"
        onLoad={() => setIsReady(true)}
      />
    </main>
  );
}
