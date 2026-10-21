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
          <li>Chọn shot, tải media theo vai trò và tạo video bằng Gemini Omni.</li>
          <li>Dùng timeline để chỉnh sửa nhiều lượt hoặc mở rộng một turn cũ.</li>
        </ol>
        <div className="notice">
          Media chỉ được gửi tới backend local rồi Gemini khi bạn chủ động tải
          lên. API key không được lưu trong trình duyệt.
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
