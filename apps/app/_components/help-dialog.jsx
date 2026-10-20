export default function HelpDialog({ open, onClose }) {
  if (!open) return null;

  return (
    <div className="help-backdrop" role="presentation" onMouseDown={onClose}>
      <section
        className="help-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="help-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="help-head">
          <div>
            <div className="eyebrow">BẮT ĐẦU NHANH</div>
            <h2 id="help-title">Làm việc với Studio AI</h2>
          </div>
          <button className="btn" type="button" onClick={onClose}>
            Đóng
          </button>
        </div>
        <ol className="help-steps">
          <li>Nạp gói Phase 5 của bạn hoặc dùng gói mẫu.</li>
          <li>Kiểm tra dashboard, cảnh quay và từng phân đoạn.</li>
          <li>Chạy chuỗi Giai đoạn 6 → 7 và xuất prompt VEO.</li>
        </ol>
        <div className="notice">
          Tệp và ảnh được xử lý trong phiên trình duyệt. Dữ liệu chỉ được gửi khi
          bạn chủ động bấm tạo video.
        </div>
        <div className="shortcut-list">
          <span>Mở gói</span><kbd>Ctrl / ⌘ + O</kbd>
          <span>Nạp gói mẫu</span><kbd>Ctrl / ⌘ + Shift + S</kbd>
          <span>Mở hướng dẫn</span><kbd>?</kbd>
        </div>
      </section>
    </div>
  );
}
