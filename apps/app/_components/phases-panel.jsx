export default function PhasesPanel() {
  return (
    <section className="panel active" id="panel-phases">
      <div className="card panel-card">
        <div className="section-title">
          <div>
            <h2>7 giai đoạn · Studio AI cho kịch bản có sẵn</h2>
            <p>
              Giai đoạn 1 nhận kịch bản trực tiếp; Giai đoạn 2–7 nhận
              NEXT_PHASE_PACKAGE.
            </p>
          </div>
          <div className="toolbar compact-toolbar">
            <button
              className="btn secondary"
              id="processPhaseInput"
              type="button"
              title="Giữ từ [APPROVED_ARTIFACT] và bỏ wrapper NEXT_PHASE_PACKAGE"
            >
              Làm sạch package 2–7
            </button>
            <button className="btn" id="downloadAllPhases" type="button">
              Tải 7 prompt gốc
            </button>
            <button className="btn" id="clearPhaseInput" type="button">
              Xóa input
            </button>
          </div>
        </div>
        <div className="phase-input-head">
          <div>
            <strong>Đầu vào cho prompt</strong>
            <div className="small">
              Dán kịch bản cho Giai đoạn 1 hoặc package của giai đoạn trước.
            </div>
          </div>
          <label className="small" htmlFor="rewritePermission">
            Quyền viết lại
            <select id="rewritePermission" className="select" defaultValue="NONE">
              <option value="NONE">NONE · giữ nguyên nội dung</option>
              <option value="FORMAT_ONLY">FORMAT_ONLY · chuẩn hóa trình bày</option>
              <option value="LIMITED">LIMITED · theo phạm vi cho phép</option>
              <option value="FULL">FULL · cho phép đề xuất sửa</option>
            </select>
          </label>
        </div>
        <textarea
          id="phaseInput"
          className="textarea mono phase-input"
          placeholder="Giai đoạn 1: dán toàn bộ kịch bản. Giai đoạn 2–7: dán NEXT_PHASE_PACKAGE của giai đoạn trước."
        />
        <div className="small" id="phaseInputStats">
          0 ký tự
        </div>
      </div>
      <div className="phase-grid" id="phaseGrid" />
    </section>
  );
}
