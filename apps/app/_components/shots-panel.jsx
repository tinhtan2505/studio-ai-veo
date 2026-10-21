export default function ShotsPanel() {
  return (
    <section className="panel" id="panel-shots">
      <div className="card panel-card">
        <div className="section-title">
          <div>
            <h2>Quy trình cảnh quay · Gemini Omni 1.1 Flash</h2>
            <p>
              Tạo mới, nội suy, dùng media tham chiếu, chỉnh sửa nhiều lượt và
              mở rộng video ngay trong từng shot.
            </p>
          </div>
          <button className="btn" id="resetTasks" type="button">
            Khôi phục nhiệm vụ
          </button>
        </div>
        <div className="grid2">
          <div>
            <div className="small">Nhiệm vụ khung nguồn</div>
            <textarea id="frameTask" className="textarea mono" />
          </div>
          <div>
            <div className="small">Nhiệm vụ video</div>
            <textarea id="videoTask" className="textarea mono" />
          </div>
        </div>
        <div className="omni-config">
          <label>
            Model cố định
            <input className="input" value="gemini-omni-1.1-flash" readOnly />
          </label>
          <label>
            Tỷ lệ ảnh và video
            <select id="omniAspectRatio" className="select" defaultValue="16:9">
              <option value="16:9">16:9 · ngang</option>
              <option value="9:16">9:16 · dọc</option>
            </select>
          </label>
          <label>
            Độ phân giải video
            <select id="omniResolution" className="select" defaultValue="720p">
              <option value="360p">360p</option>
              <option value="720p">720p</option>
              <option value="1080p">1080p</option>
              <option value="4k">4K</option>
            </select>
          </label>
          <label>
            Task
            <select id="omniTask" className="select" defaultValue="auto">
              <option value="auto">Auto</option>
              <option value="text_to_video">text_to_video</option>
              <option value="image_to_video">image_to_video</option>
              <option value="reference_to_video">reference_to_video</option>
              <option value="edit">edit</option>
              <option value="extend">extend</option>
            </select>
          </label>
          <label className="omni-store-option">
            <input id="omniStore" type="checkbox" defaultChecked />
            Cho phép chỉnh sửa tiếp (lưu interaction)
          </label>
        </div>
        <div className="notice omni-backend-status" id="omniBackendStatus" role="status">
          Đang kiểm tra backend Gemini Omni…
        </div>
      </div>

      <div className="shot-toolbar">
        <input
          id="shotSearch"
          className="input"
          placeholder="Tìm shot / scene / beat / story / hook / voice..."
        />
        <div className="seg-nav-controls">
          <button className="btn" id="shotSegBack" type="button" disabled>
            ◀ Phân đoạn trước
          </button>
          <select id="shotSegmentFilter" className="select" defaultValue="">
            <option value="">Tất cả phân đoạn</option>
          </select>
          <button className="btn" id="shotSegNext" type="button" disabled>
            Phân đoạn sau ▶
          </button>
        </div>
        <div className="shot-batch-controls">
          <label className="shot-select shot-select-wide">
            <input type="checkbox" id="shotTextVoiceOnly" /> Chỉ hiển thị shot có
            Text Voice
          </label>
          <button className="btn" id="selectSegmentFrames" type="button" disabled>
            ☑ Chọn cả phân đoạn
          </button>
          <button className="btn" id="clearFrameSelection" type="button" disabled>
            ☐ Bỏ chọn
          </button>
          <button className="btn secondary" id="copySelectedFrames" type="button" disabled>
            📋 Sao chép Source Frame đã chọn (0)
          </button>
          <button className="btn warn" id="copyFirstHalfFrames" type="button" disabled>
            📋 Copy 50% Source Frame đầu (0)
          </button>
          <button className="btn warn" id="copySecondHalfFrames" type="button" disabled>
            📋 Copy 50% Source Frame sau (0)
          </button>
          <button className="btn primary" id="copySegmentFrames" type="button" disabled>
            📋 Sao chép toàn bộ khung nguồn
          </button>
          <button className="btn" id="downloadSegmentPrompts" type="button" disabled>
            Tải gói nhắc lệnh
          </button>
        </div>
      </div>
      <div className="copy-feedback" id="copyFeedback" role="status" aria-live="polite">
        Chọn một SEG và các shot cần dùng để sao chép Source Frame.
      </div>
      <div className="small shot-stats" id="shotStats">
        0 cảnh quay
      </div>
      <section className="shots" id="shots" />
      <div className="actionrow">
        <button className="btn initially-hidden" id="showMoreShots" type="button">
          Hiện thêm cảnh quay
        </button>
      </div>
    </section>
  );
}
