"use client";

import {
  CheckCircleFilled,
  CloudServerOutlined,
  ExpandOutlined,
  FileAddOutlined,
  FolderOpenOutlined,
  InfoCircleOutlined,
  LockOutlined,
  PlayCircleFilled,
  ReloadOutlined,
  RocketOutlined,
} from "@ant-design/icons";
import {
  App as AntApp,
  Button,
  ConfigProvider,
  Drawer,
  Popconfirm,
  Spin,
  Steps,
  Tag,
  Tooltip,
  theme,
} from "antd";
import { useCallback, useEffect, useRef, useState } from "react";

const SAMPLE_BUNDLE =
  "/samples/BAY_MANH_NHAN_TAM_EP04_PHASE5_ALL_SEG_RECOMPILED_V9.txt";
const STUDIO_SOURCE = "/studio-ai-prompt-system.html";
const STUDIO_THEME = "/studio-theme.css";

function StudioWorkspace() {
  const frameRef = useRef<HTMLIFrameElement>(null);
  const workspaceRef = useRef<HTMLElement>(null);
  const [isFrameLoading, setIsFrameLoading] = useState(true);
  const [isSampleLoading, setIsSampleLoading] = useState(false);
  const [isReady, setIsReady] = useState(false);
  const [frameError, setFrameError] = useState<string | null>(null);
  const [helpOpen, setHelpOpen] = useState(false);
  const [frameVersion, setFrameVersion] = useState(0);
  const { message } = AntApp.useApp();

  const getFileInput = useCallback(() => {
    return frameRef.current?.contentDocument?.querySelector<HTMLInputElement>(
      "#fileInput",
    );
  }, []);

  const openBundlePicker = useCallback(() => {
    const fileInput = getFileInput();
    if (!fileInput) {
      message.warning("Studio đang khởi tạo, vui lòng thử lại sau ít giây.");
      return;
    }
    fileInput.click();
  }, [getFileInput, message]);

  const loadSampleBundle = useCallback(async () => {
    const fileInput = getFileInput();
    if (!fileInput) {
      message.warning("Studio chưa sẵn sàng để nhận gói mẫu.");
      return;
    }

    setIsSampleLoading(true);
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
      message.success("Đã nạp gói Phase 5 mẫu vào Studio.");
    } catch (error) {
      const description =
        error instanceof Error ? error.message : "Không thể nạp gói mẫu.";
      message.error(description);
    } finally {
      setIsSampleLoading(false);
    }
  }, [getFileInput, message]);

  const enterFullscreen = useCallback(async () => {
    try {
      await workspaceRef.current?.requestFullscreen();
    } catch {
      message.warning("Trình duyệt không cho phép mở chế độ toàn màn hình.");
    }
  }, [message]);

  const reloadStudio = useCallback(() => {
    setIsReady(false);
    setIsFrameLoading(true);
    setFrameError(null);
    setFrameVersion((version) => version + 1);
  }, []);

  const handleShortcut = useCallback(
    (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      const isTyping =
        target?.matches("input, textarea, select") || target?.isContentEditable;

      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "o") {
        event.preventDefault();
        openBundlePicker();
        return;
      }

      if (
        (event.ctrlKey || event.metaKey) &&
        event.shiftKey &&
        event.key.toLowerCase() === "s"
      ) {
        event.preventDefault();
        void loadSampleBundle();
        return;
      }

      if (!isTyping && event.key === "?") {
        setHelpOpen(true);
      }
    },
    [loadSampleBundle, openBundlePicker],
  );

  useEffect(() => {
    window.addEventListener("keydown", handleShortcut);
    return () => window.removeEventListener("keydown", handleShortcut);
  }, [handleShortcut]);

  const initializeFrame = useCallback(() => {
    const frame = frameRef.current;
    const frameDocument = frame?.contentDocument;
    const frameWindow = frame?.contentWindow;

    if (!frameDocument || !frameWindow || frameDocument.readyState !== "complete") {
      return false;
    }

    try {
      if (frameWindow.location.pathname !== STUDIO_SOURCE) {
        return false;
      }
    } catch {
      return false;
    }

    frameDocument.documentElement.classList.add("studio-embedded");

    if (!frameDocument.querySelector("#studio-modern-theme")) {
      const stylesheet = frameDocument.createElement("link");
      stylesheet.id = "studio-modern-theme";
      stylesheet.rel = "stylesheet";
      stylesheet.href = `${STUDIO_THEME}?v=20261020.2`;
      frameDocument.head.appendChild(stylesheet);
    }

    frameDocument.removeEventListener("keydown", handleShortcut);
    frameDocument.addEventListener("keydown", handleShortcut);

    setFrameError(null);
    setIsReady(true);
    setIsFrameLoading(false);
    return true;
  }, [handleShortcut]);

  useEffect(() => {
    const frame = frameRef.current;
    if (!frame) {
      return;
    }

    const handleNativeLoad = () => {
      initializeFrame();
    };

    frame.addEventListener("load", handleNativeLoad);

    // The iframe may finish before React hydrates and attaches its load handler.
    initializeFrame();

    const timeoutId = window.setTimeout(() => {
      if (!initializeFrame()) {
        setIsReady(false);
        setIsFrameLoading(false);
        setFrameError(
          "Không thể tải công cụ Studio. Hãy kiểm tra kết nối local và thử lại.",
        );
      }
    }, 15_000);

    return () => {
      window.clearTimeout(timeoutId);
      frame.removeEventListener("load", handleNativeLoad);
      frame.contentDocument?.removeEventListener("keydown", handleShortcut);
    };
  }, [frameVersion, handleShortcut, initializeFrame]);

  return (
    <main
      ref={workspaceRef}
      className="relative flex h-dvh min-h-0 flex-col overflow-hidden bg-[#070b14] text-slate-100"
    >
      <div className="pointer-events-none absolute inset-x-0 top-0 h-64 bg-[radial-gradient(circle_at_50%_-20%,rgba(99,102,241,0.2),transparent_68%)]" />

      <header className="relative z-20 flex h-[72px] shrink-0 items-center gap-3 border-b border-white/[0.07] bg-[#090e19]/90 px-3 backdrop-blur-xl sm:px-5">
        <div className="flex min-w-0 items-center gap-3">
          <div className="relative grid size-10 shrink-0 place-items-center overflow-hidden rounded-[14px] border border-indigo-300/20 bg-gradient-to-br from-indigo-500 via-violet-500 to-cyan-400 shadow-[0_8px_30px_rgba(99,102,241,0.28)]">
            <PlayCircleFilled className="text-[20px] text-white" />
            <span className="absolute -right-2 -top-2 size-5 rounded-full bg-white/30 blur-md" />
          </div>
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <h1 className="truncate text-[15px] font-bold tracking-[-0.01em] text-white sm:text-base">
                Studio AI
              </h1>
              <Tag
                variant="filled"
                className="!m-0 !hidden !rounded-full !bg-indigo-400/10 !px-2 !text-[10px] !font-semibold !text-indigo-200 md:!inline-flex"
              >
                VEO 3.1
              </Tag>
            </div>
            <p className="mt-0.5 hidden truncate text-[11px] text-slate-400 sm:block">
              Không gian sản xuất · Quy trình 7 giai đoạn
            </p>
          </div>
        </div>

        <div className="ml-auto flex items-center gap-1.5 sm:gap-2">
          <div
            className="mr-1 hidden items-center gap-2 rounded-full border border-white/[0.08] bg-white/[0.035] px-3 py-1.5 lg:flex"
            role="status"
            aria-live="polite"
          >
            {isReady ? (
              <CheckCircleFilled className="text-emerald-400" />
            ) : (
              <Spin size="small" />
            )}
            <span className="text-[11px] font-medium text-slate-300">
              {isReady ? "Studio đã sẵn sàng" : "Đang khởi tạo Studio"}
            </span>
          </div>

          <Tooltip title="Mở gói sản xuất (Ctrl/⌘ + O)">
            <Button
              aria-label="Mở gói sản xuất"
              icon={<FolderOpenOutlined />}
              onClick={openBundlePicker}
              disabled={!isReady}
              className="studio-action-button"
            >
              <span className="hidden xl:inline">Mở gói</span>
            </Button>
          </Tooltip>

          <Tooltip title="Nạp nhanh dữ liệu Phase 5 mẫu (Ctrl/⌘ + Shift + S)">
            <Button
              type="primary"
              aria-label="Nạp gói Phase 5 mẫu"
              icon={<FileAddOutlined />}
              onClick={() => void loadSampleBundle()}
              disabled={!isReady}
              loading={isSampleLoading}
              className="studio-primary-button"
            >
              <span className="hidden sm:inline">Nạp gói mẫu</span>
            </Button>
          </Tooltip>

          <Tooltip title="Toàn màn hình">
            <Button
              aria-label="Mở toàn màn hình"
              type="text"
              icon={<ExpandOutlined />}
              onClick={() => void enterFullscreen()}
              className="studio-icon-button hidden sm:inline-flex"
            />
          </Tooltip>

          <Popconfirm
            title="Khởi động lại Studio?"
            description="Dữ liệu chưa xuất trong phiên hiện tại sẽ bị xóa."
            okText="Khởi động lại"
            cancelText="Hủy"
            onConfirm={reloadStudio}
          >
            <Tooltip title="Khởi động lại Studio">
              <Button
                aria-label="Khởi động lại Studio"
                type="text"
                icon={<ReloadOutlined />}
                className="studio-icon-button"
              />
            </Tooltip>
          </Popconfirm>

          <Tooltip title="Hướng dẫn và phím tắt (?)">
            <Button
              aria-label="Mở hướng dẫn"
              type="text"
              icon={<InfoCircleOutlined />}
              onClick={() => setHelpOpen(true)}
              className="studio-icon-button"
            />
          </Tooltip>
        </div>
      </header>

      <section className="relative z-10 flex min-h-0 flex-1 flex-col p-2 sm:p-3">
        <div className="mb-2 flex shrink-0 items-center justify-between px-1 sm:px-2">
          <div className="flex min-w-0 items-center gap-2 text-[11px] text-slate-400">
            <CloudServerOutlined className="text-cyan-400" />
              <span className="truncate">
                Workspace cục bộ · Chỉ gửi shot khi bạn bấm Tạo video
              </span>
          </div>
          <div className="flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-[0.12em] text-slate-500">
            <span
              className={`size-1.5 rounded-full ${
                isReady
                  ? "bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.8)]"
                  : "animate-pulse bg-amber-400"
              }`}
            />
            {isReady ? "Online" : "Loading"}
          </div>
        </div>

        <div className="relative min-h-0 flex-1 overflow-hidden rounded-[18px] border border-white/[0.09] bg-[#0b1120] shadow-[0_24px_80px_rgba(0,0,0,0.35)] sm:rounded-[22px]">
          {isFrameLoading && (
            <div className="absolute inset-0 z-10 grid place-items-center bg-[#0b1120]">
              <div className="flex flex-col items-center gap-4 text-center">
                <div className="grid size-14 place-items-center rounded-2xl border border-indigo-400/20 bg-indigo-400/10">
                  <Spin size="large" />
                </div>
                <div>
                  <p className="text-sm font-semibold text-slate-200">
                    Đang chuẩn bị không gian sản xuất
                  </p>
                  <p className="mt-1 text-xs text-slate-500">
                    Đồng bộ công cụ và quy trình 7 giai đoạn…
                  </p>
                </div>
              </div>
            </div>
          )}
          {frameError && !isFrameLoading && (
            <div className="absolute inset-0 z-10 grid place-items-center bg-[#0b1120] p-6">
              <div className="max-w-sm text-center">
                <InfoCircleOutlined className="text-3xl text-amber-400" />
                <p className="mt-4 text-sm font-semibold text-slate-200">
                  Studio chưa tải được
                </p>
                <p className="mt-1 text-xs leading-5 text-slate-500">
                  {frameError}
                </p>
                <Button
                  className="studio-action-button mt-4"
                  icon={<ReloadOutlined />}
                  onClick={reloadStudio}
                >
                  Thử lại
                </Button>
              </div>
            </div>
          )}
          <iframe
            key={frameVersion}
            ref={frameRef}
            className="block h-full w-full border-0 bg-[#080d18]"
            src={STUDIO_SOURCE}
            title="Studio AI Prompt System"
            allow="clipboard-read; clipboard-write"
          />
        </div>
      </section>

      <Drawer
        title={
          <div className="flex items-center gap-2">
            <RocketOutlined className="text-indigo-400" />
            <span>Bắt đầu với Studio AI</span>
          </div>
        }
        placement="right"
        size={420}
        open={helpOpen}
        onClose={() => setHelpOpen(false)}
        className="studio-help-drawer"
      >
        <p className="mb-6 text-sm leading-6 text-slate-400">
          Tải gói sản xuất lên, kiểm tra dữ liệu rồi đi lần lượt qua các giai
          đoạn để duyệt và xuất prompt VEO.
        </p>
        <Steps
          direction="vertical"
          current={-1}
          items={[
            {
              title: "Nạp dữ liệu",
              description:
                "Chọn gói Phase 5 của bạn hoặc dùng gói mẫu để khám phá giao diện.",
            },
            {
              title: "Kiểm định và tinh chỉnh",
              description:
                "Theo dõi cảnh quay, phân đoạn, thoại và các cổng chất lượng.",
            },
            {
              title: "Xuất cho VEO",
              description:
                "Sao chép prompt hoặc tải gói đầu ra khi toàn bộ cổng đã đạt.",
            },
          ]}
        />

        <div className="mt-5 rounded-2xl border border-white/[0.08] bg-white/[0.035] p-4">
          <div className="mb-3 flex items-center gap-2 text-sm font-semibold text-slate-200">
            <LockOutlined className="text-emerald-400" />
            Quyền riêng tư theo phiên
          </div>
          <p className="text-xs leading-5 text-slate-400">
            Tệp bạn mở chỉ được xử lý trong phiên trình duyệt. Prompt và ảnh
            của từng shot chỉ được gửi tới VEO khi bạn chủ động bấm Tạo video.
          </p>
        </div>

        <div className="mt-6">
          <h3 className="mb-3 text-xs font-bold uppercase tracking-[0.14em] text-slate-500">
            Phím tắt
          </h3>
          <div className="space-y-2 text-sm text-slate-300">
            <div className="flex items-center justify-between">
              <span>Mở gói sản xuất</span>
              <kbd>Ctrl / ⌘ + O</kbd>
            </div>
            <div className="flex items-center justify-between">
              <span>Nạp gói mẫu</span>
              <kbd>Ctrl / ⌘ + Shift + S</kbd>
            </div>
            <div className="flex items-center justify-between">
              <span>Mở hướng dẫn</span>
              <kbd>?</kbd>
            </div>
          </div>
        </div>
      </Drawer>
    </main>
  );
}

export default function Home() {
  return (
    <ConfigProvider
      theme={{
        algorithm: theme.darkAlgorithm,
        token: {
          colorPrimary: "#6d71f9",
          colorInfo: "#6d71f9",
          colorSuccess: "#34d399",
          colorWarning: "#fbbf24",
          colorBgBase: "#080d18",
          colorTextBase: "#f1f5f9",
          colorBorder: "rgba(255,255,255,0.1)",
          borderRadius: 12,
          controlHeight: 38,
          fontFamily:
            'Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
        },
        components: {
          Button: {
            fontWeight: 650,
            primaryShadow: "0 8px 24px rgba(99, 102, 241, 0.24)",
          },
          Drawer: {
            colorBgElevated: "#0c1322",
          },
          Message: {
            contentBg: "#111827",
          },
        },
      }}
    >
      <AntApp>
        <StudioWorkspace />
      </AntApp>
    </ConfigProvider>
  );
}
