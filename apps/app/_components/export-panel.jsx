export default function ExportPanel() {
  return (
    <section className="panel" id="panel-export">
      <div className="grid2">
        <div className="card">
          <div className="section-title">
            <div>
              <h2>Tiếp tục / Con trỏ chuỗi</h2>
              <p>Xuất trạng thái để tiếp tục ở phiên khác.</p>
            </div>
          </div>
          <div className="actionrow">
            <button className="btn primary" id="downloadCursor" type="button">
              Tải AUTO_CHAIN_CURSOR
            </button>
            <button className="btn" id="downloadChainRecords2" type="button">
              Tải Chain Records
            </button>
            <button className="btn danger" id="resetChain" type="button">
              Đặt lại chuỗi hiện tại
            </button>
          </div>
          <pre className="authority" id="cursorPreview">
            Chưa có gói.
          </pre>
        </div>
        <div className="card">
          <div className="section-title">
            <div>
              <h2>Ghi chú gói / bộ điều khiển</h2>
              <p>
                Thẩm quyền trạng thái đã chấp nhận vẫn là SEGMENT_STATE_COMMIT
                của Giai đoạn 7.
              </p>
            </div>
          </div>
          <div id="exportNotes" className="notice">
            Nạp gói để xem hướng dẫn.
          </div>
        </div>
      </div>
    </section>
  );
}
