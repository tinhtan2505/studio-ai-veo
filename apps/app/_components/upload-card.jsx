export default function UploadCard() {
  return (
    <>
      <div className="notice sync-notice">
        <strong>Quy trình Studio AI chạy trực tiếp bằng Next.js</strong>
        <br />
        Đủ 7 prompt nguyên văn, parser gói Phase 5 và luồng Source Image →
        Gemini Omni. Schema: <code>STUDIO_AI_PROMPT_SYSTEM_DIRECT_SCREENPLAY</code>.
      </div>

      <section className="card upload">
        <label className="dropzone" id="dropzone" htmlFor="fileInput">
          <input
            id="fileInput"
            type="file"
            accept=".txt,.yaml,.yml,.md,text/plain"
          />
          <div>
            <div className="drop-title">
              📦 Nạp tệp gói một tệp của Giai đoạn 5
            </div>
            <div className="drop-help">
              Nhận YAML và định dạng thẻ <code>[SHOT_PACKAGE]</code>,{" "}
              <code>[SHOT_IDENTITY]</code>,{" "}
              <code>[EMBEDDED_SEGMENT_MANIFEST]</code> hoặc{" "}
              <code>[SEGMENT_BLOCK]</code>.
            </div>
            <div className="filename" id="filename" />
          </div>
        </label>
        <div className="errorbox" id="errorBox" />
      </section>
    </>
  );
}
