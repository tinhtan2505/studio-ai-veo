const tabs = [
  ["dashboard", "Tổng quan"],
  ["segments", "Kiểm định / Phê duyệt"],
  ["shots", "Nhắc lệnh cảnh quay"],
  ["phases", "7 giai đoạn"],
  ["reference", "Tài liệu / Kiểm thử"],
  ["export", "Chuỗi / Xuất"],
];

export default function StudioNavigation() {
  return (
    <nav className="nav" id="nav" aria-label="Điều hướng Studio">
      {tabs.map(([id, label]) => (
        <button
          key={id}
          type="button"
          data-tab={id}
          className={id === "phases" ? "active" : undefined}
        >
          {label}
        </button>
      ))}
    </nav>
  );
}
