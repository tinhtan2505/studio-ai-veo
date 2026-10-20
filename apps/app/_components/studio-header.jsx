export default function StudioHeader({
  ready,
  sampleLoading,
  onOpenBundle,
  onLoadSample,
  onOpenHelp,
}) {
  return (
    <header className="hero">
      <div className="hero-copy">
        <div className="eyebrow">VEO PRODUCTION WORKSPACE</div>
        <h1>
          Hệ thống nhắc lệnh Studio AI{" "}
          <span className="accent-title">· 7 giai đoạn</span>
        </h1>
        <p>
          Nạp gói sản xuất, kiểm định từng phân đoạn và tạo nhắc lệnh hình ảnh,
          video trực tiếp trong một workspace Next.js thống nhất.
        </p>
      </div>

      <div className="hero-actions">
        <div className={`runtime-status ${ready ? "ready" : "loading"}`}>
          <span className="status-dot" />
          {ready ? "Studio sẵn sàng" : "Đang khởi tạo"}
        </div>
        <button className="btn" type="button" onClick={onOpenHelp}>
          Hướng dẫn
        </button>
        <button
          className="btn"
          type="button"
          onClick={onOpenBundle}
          disabled={!ready}
        >
          Mở gói
        </button>
        <button
          className="btn primary"
          type="button"
          onClick={onLoadSample}
          disabled={!ready || sampleLoading}
        >
          {sampleLoading ? "Đang nạp…" : "Nạp gói mẫu"}
        </button>
      </div>
    </header>
  );
}
