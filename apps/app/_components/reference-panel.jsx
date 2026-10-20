import { REFERENCE_HTML } from "../_data/reference-content";

export default function ReferencePanel() {
  return (
    <section className="panel" id="panel-reference">
      <div className="section-title">
        <div>
          <h2>Tài liệu vận hành và kiểm thử</h2>
          <p>
            Hướng dẫn, hợp đồng đầu vào/đầu ra và các ca kiểm thử của quy trình 7
            giai đoạn.
          </p>
        </div>
        <button className="btn" id="downloadSourceText" type="button">
          Tải toàn bộ nội dung Word (.txt)
        </button>
      </div>
      <div dangerouslySetInnerHTML={{ __html: REFERENCE_HTML }} />
    </section>
  );
}
