function Metric({ label, value, valueId, detail, detailId, progress }) {
  return (
    <div className="metric">
      <div className="k">{label}</div>
      <div className="v" id={valueId}>
        {value}
      </div>
      {progress ? (
        <div className="progress">
          <div id="greenBar" />
        </div>
      ) : (
        <div className="s" id={detailId}>
          {detail}
        </div>
      )}
    </div>
  );
}

function DiagnosticCard({ title, description, id, children }) {
  return (
    <div className="card">
      <div className="section-title">
        <div>
          <h2>{title}</h2>
          <p>{description}</p>
        </div>
      </div>
      {id ? (
        <div className="diag-list" id={id}>
          <div className="notice">{children}</div>
        </div>
      ) : (
        <div className="notice">{children}</div>
      )}
    </div>
  );
}

export default function DashboardPanel() {
  return (
    <section className="panel" id="panel-dashboard">
      <div className="metrics">
        <Metric
          label="Dự án"
          value="—"
          valueId="mProject"
          detail="Chưa nạp file"
          detailId="mVersion"
        />
        <Metric
          label="Cảnh quay"
          value="0"
          valueId="mShots"
          detail="đúng một lần / thứ tự dựng phim"
        />
        <Metric
          label="Phân đoạn"
          value="0"
          valueId="mSegments"
          detail="hàng đợi Giai đoạn 6 → 11"
        />
        <Metric
          label="Thời lượng"
          value="0s"
          valueId="mRuntime"
          detail="thời lượng đã biên dịch"
        />
        <Metric
          label="Giai đoạn 5"
          value="—"
          valueId="mStatus"
          detail="scores —"
          detailId="mScores"
        />
        <Metric label="Phê duyệt" value="0/0" valueId="mGreen" progress />
      </div>

      <div className="grid2 dashboard-grid">
        <DiagnosticCard
          title="Thiết kế shot và cặp nối"
          description="Nguồn thiết kế, vị trí, hướng nhìn, điểm cắt và thoại sau trim."
          id="shotDesignDiagnostics"
        >
          Nạp gói để kiểm cấu trúc dữ liệu thiết kế.
        </DiagnosticCard>
        <DiagnosticCard
          title="Thực thi câu chuyện"
          description="Hợp đồng trải nghiệm, chi tiết nguồn và bằng chứng nội dung trong phạm vi."
          id="storyExecutionDiagnostics"
        >
          Nạp gói để đối chiếu dữ liệu; kiểm cấu trúc không thay thế đánh giá
          nội dung.
        </DiagnosticCard>
        <DiagnosticCard
          title="Kiểm tra trước gói sản xuất"
          description="Kiểm tra cục bộ, gồm trần cứng tuyệt đối 8,0 giây cho mỗi cảnh quay."
          id="diagnostics"
        >
          Nạp tệp Giai đoạn 5 để chạy kiểm tra trước.
        </DiagnosticCard>
        <div className="card">
          <div className="section-title">
            <div>
              <h2>Tóm tắt thẩm quyền</h2>
              <p>Thông tin bộ điều khiển lấy trực tiếp từ gói.</p>
            </div>
          </div>
          <pre className="authority" id="authority">
            Chưa có gói.
          </pre>
        </div>
        <DiagnosticCard
          title="Phân cấp duy trì"
          description="Chuỗi duy trì → móc cuối cảnh quay → tái móc cuối cảnh → tiếp nối cuối tập."
          id="retentionDiagnostics"
        >
          Nạp gói để kiểm tra duy trì.
        </DiagnosticCard>
        <DiagnosticCard
          title="Cửa sổ thoại & phân bổ câu"
          description="Kiểm chuỗi thoại, chủ quản và bao phủ chuẩn chính xác qua các cảnh quay."
          id="voiceDiagnostics"
        >
          Nạp gói để kiểm tra thoại.
        </DiagnosticCard>
        <DiagnosticCard
          title="Độ rõ câu chuyện ưu tiên nhân vật"
          description="Kiểm lớp người xem nhìn thấy, tải nhận thức và rò rỉ lô-gic kỹ thuật."
          id="clarityDiagnostics"
        >
          Nạp gói để kiểm tra độ rõ câu chuyện.
        </DiagnosticCard>
        <DiagnosticCard
          title="Thẩm quyền / trung thành nguồn"
          description="Kiểm mức trung thành với nguồn tự sự và trạng thái biên dịch theo lược đồ."
        >
          Các lỗi độ rõ câu chuyện được định tuyến về đúng giai đoạn chủ quản.
        </DiagnosticCard>
        <DiagnosticCard
          title="Tính mới sáng tạo"
          description="Kiểm dấu vân tay sáng tạo, mô-típ, môi trường và cấu trúc quyết định."
          id="noveltyDiagnostics"
        >
          Nạp gói để kiểm tra tính mới sáng tạo và đa dạng.
        </DiagnosticCard>
        <DiagnosticCard
          title="Biên độ sáng tạo"
          description="Giữ khóa trực tiếp của người dùng và dùng dữ liệu bộ nhớ khi khả dụng."
        >
          Nếu bộ nhớ là UNAVAILABLE, dùng BOOTSTRAP_DIVERSITY_MODE và không
          tuyên bố khác lịch sử.
        </DiagnosticCard>
        <DiagnosticCard
          title="Tuổi, phe, trang phục và giáp"
          description="Kiểm tuổi chuẩn, AGE_STATE và kế thừa khóa hình ảnh."
          id="identityDiagnostics"
        >
          Nạp gói để kiểm tra nhận diện hình ảnh.
        </DiagnosticCard>
        <DiagnosticCard
          title="Kế thừa khóa hình ảnh"
          description="FACTION → ENSEMBLE_LOCK → ROLE_VARIANT → CURRENT_STATE → SHOT."
        >
          Cổng tuổi chính xác luôn là HARD cho nhân vật có tên hoặc lặp lại từ
          Giai đoạn 3.
        </DiagnosticCard>
        <DiagnosticCard
          title="Hiện diện hình ảnh – âm thanh"
          description="Kiểm chủ thể nhìn thấy, người nói ngoài khung và vai trò chỉ âm thanh."
          id="visualPresenceDiagnostics"
        >
          Nạp gói để kiểm tra thẩm quyền hiện diện.
        </DiagnosticCard>
        <DiagnosticCard
          title="Chênh lệch hình ảnh"
          description="Đối chiếu visual_delta_from_previous_shot giữa các cảnh quay liền kề."
        >
          Hệ thống báo VISUAL_DELTA_MISSING hoặc SOURCE_STATE_DUPLICATION khi
          phát hiện sai lệch.
        </DiagnosticCard>
      </div>
    </section>
  );
}
