export default function CopyOverlays() {
  return (
    <>
      <div className="copy-toast" id="copyToast" role="status" aria-live="polite" />
      <div className="copy-modal" id="copyModal" aria-hidden="true">
        <div className="copy-dialog">
          <h3>Sao chép thủ công</h3>
          <p>
            Bộ nhớ tạm bị chặn. Chọn toàn bộ nội dung bên dưới rồi sao chép.
          </p>
          <textarea className="textarea mono manual-copy" id="manualCopy" />
          <div className="actionrow">
            <button className="btn primary" id="selectAllCopy" type="button">
              Chọn toàn bộ
            </button>
            <button className="btn" id="closeCopy" type="button">
              Đóng
            </button>
          </div>
        </div>
      </div>
    </>
  );
}
