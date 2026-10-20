export default function ShotsPanel() {
  return (
    <section className="panel" id="panel-shots">
      <div className="card panel-card">
        <div className="section-title">
          <div>
            <h2>Quy trình cảnh quay · Source Image → Veo 3.1</h2>
            <p>
              Tạo Source Image đúng trạng thái đầu, gắn ảnh vào đúng shot_id rồi
              tạo video bằng nhắc lệnh motion-only.
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
        <div className="veo-config">
          <label>
            Model Veo
            <select id="veoModel" className="select" defaultValue="veo-3.1-generate-001">
              <option value="veo-3.1-generate-001">Veo 3.1</option>
              <option value="veo-3.1-fast-generate-001">Veo 3.1 Fast</option>
            </select>
          </label>
          <label>
            Tỷ lệ ảnh và video
            <select id="veoAspectRatio" className="select" defaultValue="16:9">
              <option value="16:9">16:9 · ngang</option>
              <option value="9:16">9:16 · dọc</option>
            </select>
          </label>
          <label>
            Độ phân giải video
            <select id="veoResolution" className="select" defaultValue="720p">
              <option value="720p">720p · 4/6/8 giây</option>
              <option value="1080p">1080p · yêu cầu clip 8 giây</option>
            </select>
          </label>
          <label>
            Âm thanh
            <select id="veoAudioMode" className="select" defaultValue="NATIVE_AUDIO">
              <option value="NATIVE_AUDIO">Veo tạo audio native</option>
              <option value="EXTERNAL_AUDIO">Tạo hình video, ghép audio ngoài</option>
            </select>
          </label>
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
