"use client";

import Script from "next/script";
import { useCallback, useEffect, useState } from "react";
import CopyOverlays from "./copy-overlays";
import DashboardPanel from "./dashboard-panel";
import ExportPanel from "./export-panel";
import HelpDialog from "./help-dialog";
import PhasesPanel from "./phases-panel";
import ReferencePanel from "./reference-panel";
import SegmentsPanel from "./segments-panel";
import ShotsPanel from "./shots-panel";
import StudioHeader from "./studio-header";
import StudioNavigation from "./studio-navigation";
import UploadCard from "./upload-card";

const SAMPLE_BUNDLE =
  "/samples/BAY_MANH_NHAN_TAM_EP04_PHASE5_ALL_SEG_RECOMPILED_V9.txt";

export default function StudioShell() {
  const [ready, setReady] = useState(false);
  const [coreReady, setCoreReady] = useState(false);
  const [sampleLoading, setSampleLoading] = useState(false);
  const [helpOpen, setHelpOpen] = useState(false);

  const markReady = useCallback(() => {
    setReady(Boolean(window.StudioAI?.loadFile));
  }, []);

  const openBundle = useCallback(() => {
    document.getElementById("fileInput")?.click();
  }, []);

  const loadSample = useCallback(async () => {
    if (!window.StudioAI?.loadFile) return;

    setSampleLoading(true);
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
      await window.StudioAI.loadFile(file);
    } catch (error) {
      const errorBox = document.getElementById("errorBox");
      if (errorBox) {
        errorBox.textContent =
          error instanceof Error ? error.message : "Không thể nạp gói mẫu.";
        errorBox.classList.add("visible");
      }
    } finally {
      setSampleLoading(false);
    }
  }, []);

  useEffect(() => {
    window.addEventListener("studio-ai:ready", markReady);
    return () => window.removeEventListener("studio-ai:ready", markReady);
  }, [markReady]);

  useEffect(() => {
    const handleShortcut = (event) => {
      const target = event.target;
      const isTyping =
        target instanceof HTMLElement &&
        (target.matches("input, textarea, select") || target.isContentEditable);

      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "o") {
        event.preventDefault();
        openBundle();
      } else if (
        (event.ctrlKey || event.metaKey) &&
        event.shiftKey &&
        event.key.toLowerCase() === "s"
      ) {
        event.preventDefault();
        void loadSample();
      } else if (!isTyping && event.key === "?") {
        setHelpOpen(true);
      } else if (event.key === "Escape") {
        setHelpOpen(false);
      }
    };

    window.addEventListener("keydown", handleShortcut);
    return () => window.removeEventListener("keydown", handleShortcut);
  }, [loadSample, openBundle]);

  return (
    <>
      <main className="app">
        <StudioHeader
          ready={ready}
          sampleLoading={sampleLoading}
          onOpenBundle={openBundle}
          onLoadSample={() => void loadSample()}
          onOpenHelp={() => setHelpOpen(true)}
        />
        <UploadCard />
        <StudioNavigation />
        <DashboardPanel />
        <SegmentsPanel />
        <ShotsPanel />
        <PhasesPanel />
        <ReferencePanel />
        <ExportPanel />
        <CopyOverlays />
        <div className="footer-note">
          Studio AI Prompt System · Next.js App Router · JavaScript components ·
          Dữ liệu phiên được lưu cục bộ
        </div>
      </main>
      <HelpDialog open={helpOpen} onClose={() => setHelpOpen(false)} />
      <Script
        id="studio-omni-core"
        src="/omni-core.js"
        strategy="afterInteractive"
        onLoad={() => setCoreReady(Boolean(window.StudioOmniCore))}
        onReady={() => setCoreReady(Boolean(window.StudioOmniCore))}
        onError={() => setReady(false)}
      />
      {coreReady ? (
        <Script
          id="studio-ai-runtime"
          src="/studio-runtime.js"
          strategy="afterInteractive"
          onLoad={markReady}
          onReady={markReady}
          onError={() => setReady(false)}
        />
      ) : null}
    </>
  );
}
