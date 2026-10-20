export default function SegmentsPanel() {
  return (
    <section className="panel" id="panel-segments">
      <div className="section-title">
        <div>
          <h2>Chuỗi kiểm định / phê duyệt phân đoạn</h2>
          <p>
            Chạy Giai đoạn 6 và 7 theo thứ tự; SEGCOMMIT đã chấp nhận luôn thắng
            trạng thái dự kiến.
          </p>
        </div>
        <div className="toolbar">
          <select id="transferMode" className="select" defaultValue="inline">
            <option value="inline">Chèn trực tiếp SEGMENT_BLOCK</option>
            <option value="attached">Tham chiếu tệp Giai đoạn 5</option>
          </select>
          <label className="small">
            <input type="checkbox" id="manualUnlock" /> mở gate thủ công
          </label>
        </div>
      </div>

      <div className="seg-layout">
        <aside className="card">
          <div className="toolbar">
            <input
              id="segSearch"
              className="input full-width"
              placeholder="Tìm SEG / SHOT / scene..."
            />
          </div>
          <div className="seg-list" id="segmentList">
            <div className="notice">Nạp gói để tạo hàng đợi.</div>
          </div>
        </aside>
        <section className="card workspace" id="segmentWorkspace">
          <div className="notice">Chọn một phân đoạn sau khi nạp gói.</div>
        </section>
      </div>

      <div className="card finalbox" id="finalBox">
        <div className="section-title">
          <div>
            <h2>Hợp nhất cuối</h2>
            <p>
              Giai đoạn 6 cuối → READY_FOR_FINAL_JUDGE → Giai đoạn 7 cuối →
              STUDIO_GREENLIGHT_90.
            </p>
          </div>
        </div>
        <div id="finalGate" className="notice warn">
          Chưa đủ SEGMENT_GREENLIGHT để hợp nhất cuối.
        </div>
        <div className="final-grid final-grid-spaced">
          <div className="step">
            <h4>Giai đoạn 6 · FINAL_CONSOLIDATION</h4>
            <div className="actionrow">
              <button className="btn primary" id="copyP10Final" type="button" disabled>
                Sao chép Giai đoạn 6 cuối
              </button>
              <button className="btn" id="downloadChainRecords" type="button">
                Tải Chain Records
              </button>
            </div>
            <textarea
              className="textarea mono"
              id="p10FinalOutput"
              placeholder="Dán output Giai đoạn 6 FINAL_CONSOLIDATION..."
            />
            <div className="actionrow">
              <button className="btn" id="analyzeP10Final" type="button">
                Phân tích Giai đoạn 6 cuối
              </button>
            </div>
            <div className="resultline" id="p10FinalStatus">
              NOT_RUN
            </div>
          </div>

          <div className="step">
            <h4>Giai đoạn 7 · FINAL_CONSOLIDATION</h4>
            <div className="actionrow">
              <button className="btn secondary" id="copyP11Final" type="button" disabled>
                Sao chép Giai đoạn 7 cuối
              </button>
            </div>
            <textarea
              className="textarea mono"
              id="p11FinalOutput"
              placeholder="Dán output Giai đoạn 7 FINAL_CONSOLIDATION..."
            />
            <div className="actionrow">
              <button className="btn" id="analyzeP11Final" type="button">
                Phân tích Giai đoạn 7 cuối
              </button>
            </div>
            <div className="resultline" id="p11FinalStatus">
              NOT_RUN
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
