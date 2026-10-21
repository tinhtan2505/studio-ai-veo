(function attachOmniCore(root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  if (root) root.StudioOmniCore = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function createOmniCore() {
  "use strict";

  const MODES = Object.freeze(["GENERATE", "EDIT", "EXTEND"]);
  const ROLES = Object.freeze([
    "FIRST_FRAME",
    "LAST_FRAME",
    "IMAGE_REFERENCE",
    "SOURCE_VIDEO",
    "VIDEO_REFERENCE",
  ]);
  const TASKS = Object.freeze([
    "auto",
    "text_to_video",
    "image_to_video",
    "reference_to_video",
    "edit",
    "extend",
  ]);
  const RESOLUTIONS = Object.freeze(["360p", "720p", "1080p", "4k"]);
  const ASPECT_RATIOS = Object.freeze(["16:9", "9:16"]);
  const DONE_STATUSES = new Set(["SUCCEEDED", "FAILED"]);

  function roleTag(role, indexByRole) {
    if (role === "FIRST_FRAME") return "<FIRST_FRAME>";
    if (role === "LAST_FRAME") return "<LAST_FRAME>";
    if (role === "SOURCE_VIDEO") return "<VIDEO_0>";
    if (role === "IMAGE_REFERENCE") {
      const index = indexByRole.image;
      indexByRole.image += 1;
      return `<IMAGE_REF_${index}>`;
    }
    if (role === "VIDEO_REFERENCE") {
      const index = indexByRole.video;
      indexByRole.video += 1;
      return `<VIDEO_REF_${index}>`;
    }
    return "<MEDIA>";
  }

  function compilePrompt(content, media, mode) {
    const ordered = [...(media || [])].sort(
      (a, b) => Number(a.order ?? 0) - Number(b.order ?? 0),
    );
    const indexes = { image: 0, video: 0 };
    const declarations = ordered.map((item) => {
      const tag = roleTag(item.role, indexes);
      const name = item.displayName || item.name || item.fileId || "media";
      const instruction = {
        FIRST_FRAME: "khung mở đầu bắt buộc",
        LAST_FRAME: "khung kết thúc bắt buộc; nội suy liên tục từ FIRST_FRAME",
        IMAGE_REFERENCE: "tham chiếu hình ảnh; chỉ dùng đặc trưng được prompt yêu cầu",
        SOURCE_VIDEO: "video nguồn chính duy nhất để chỉnh sửa hoặc mở rộng",
        VIDEO_REFERENCE: "tham chiếu hình/chuyển động; bỏ qua âm thanh trong tệp",
      }[item.role] || "media tham chiếu";
      return `${tag} = ${name} — ${instruction}.`;
    });
    const modeInstruction = {
      GENERATE: "Tạo một video mới theo mô tả và các media đã khai báo.",
      EDIT: "Chỉnh sửa video nguồn theo yêu cầu. Keep everything else the same.",
      EXTEND: "Tiếp tục tự nhiên từ frame cuối của video nguồn; không lặp hoặc reset hành động.",
    }[mode] || "";
    return [
      declarations.length ? "MEDIA DECLARATIONS:\n" + declarations.join("\n") : "",
      modeInstruction,
      String(content || "").trim(),
    ]
      .filter(Boolean)
      .join("\n\n")
      .trim();
  }

  function validateDraft(draft) {
    const errors = [];
    const mode = draft?.mode || "GENERATE";
    const media = draft?.media || [];
    const count = (role) => media.filter((item) => item.role === role).length;
    const previous = String(draft?.previousInteractionId || "").trim();
    const sourceCount = count("SOURCE_VIDEO");
    if (!MODES.includes(mode)) errors.push("Chế độ không hợp lệ.");
    if (!String(draft?.prompt || "").trim()) errors.push("Prompt không được để trống.");
    if (count("FIRST_FRAME") > 1) errors.push("Chỉ được có một FIRST_FRAME.");
    if (count("LAST_FRAME") > 1) errors.push("Chỉ được có một LAST_FRAME.");
    if (count("LAST_FRAME") && !count("FIRST_FRAME")) {
      errors.push("LAST_FRAME bắt buộc đi cùng FIRST_FRAME.");
    }
    if (count("VIDEO_REFERENCE") > 3) errors.push("Tối đa ba VIDEO_REFERENCE.");
    if (sourceCount > 1) errors.push("Chỉ được có một SOURCE_VIDEO.");
    if (mode === "GENERATE" && (previous || sourceCount)) {
      errors.push("Chế độ Tạo không dùng interaction trước hoặc SOURCE_VIDEO.");
    }
    if (mode !== "GENERATE" && Boolean(previous) === Boolean(sourceCount)) {
      errors.push("Chỉnh sửa/Mở rộng phải chọn đúng một nguồn: turn trước hoặc SOURCE_VIDEO.");
    }
    for (const item of media) {
      if (!ROLES.includes(item.role)) errors.push(`Vai trò media không hợp lệ: ${item.role}.`);
      if (!item.fileId || item.state !== "ACTIVE") errors.push(`${item.displayName || "Media"} chưa sẵn sàng.`);
      if (item.expirationTime && Date.parse(item.expirationTime) <= Date.now()) {
        errors.push(`${item.displayName || "Media"} đã hết hạn và cần tải lại.`);
      }
      if (item.role === "SOURCE_VIDEO" && Number(item.durationSeconds) > 10) {
        errors.push("SOURCE_VIDEO không được dài quá 10 giây.");
      }
      if (item.role === "VIDEO_REFERENCE" && Number(item.durationSeconds) > 3) {
        errors.push("VIDEO_REFERENCE không được dài quá 3 giây.");
      }
    }
    const task = draft?.task || "auto";
    if (!TASKS.includes(task)) errors.push("Task không hợp lệ.");
    if (task !== "auto") {
      const expected = mode === "EDIT" ? "edit" : mode === "EXTEND" ? "extend" : null;
      if (expected && task !== expected) errors.push(`Mode ${mode} chỉ tương thích task ${expected}.`);
      if (!expected && ["edit", "extend"].includes(task)) errors.push("Task edit/extend không dùng ở tab Tạo.");
    }
    if (!ASPECT_RATIOS.includes(draft?.aspectRatio)) errors.push("Tỷ lệ không hợp lệ.");
    if (!RESOLUTIONS.includes(draft?.resolution)) errors.push("Độ phân giải không hợp lệ.");
    return errors;
  }

  function toJobRequest(shotId, draft) {
    const prompt = compilePrompt(draft.prompt, draft.media, draft.mode);
    const normalized = { ...draft, prompt };
    const errors = validateDraft(normalized);
    if (errors.length) throw new Error(errors.join(" "));
    const request = {
      shotId,
      mode: draft.mode,
      prompt,
      media: [...draft.media]
        .sort((a, b) => Number(a.order ?? 0) - Number(b.order ?? 0))
        .map((item, index) => ({ fileId: item.fileId, role: item.role, order: index })),
      aspectRatio: draft.aspectRatio,
      resolution: draft.resolution,
      store: Boolean(draft.store),
    };
    if (draft.task && draft.task !== "auto") request.task = draft.task;
    if (draft.previousInteractionId) request.previousInteractionId = draft.previousInteractionId;
    return request;
  }

  function createState(overrides) {
    return {
      mode: "GENERATE",
      prompt: "",
      media: [],
      task: "auto",
      aspectRatio: "16:9",
      resolution: "720p",
      store: true,
      previousInteractionId: "",
      selectedTurnId: "",
      job: null,
      history: [],
      ...overrides,
    };
  }

  function reduceState(state, action) {
    switch (action.type) {
      case "PATCH":
        return { ...state, ...action.patch };
      case "ADD_MEDIA":
        return {
          ...state,
          media: [...state.media, { ...action.media, order: state.media.length }],
        };
      case "UPDATE_MEDIA":
        return {
          ...state,
          media: state.media.map((item) =>
            item.localId === action.localId ? { ...item, ...action.patch } : item,
          ),
        };
      case "REMOVE_MEDIA":
        return {
          ...state,
          media: state.media
            .filter((item) => item.localId !== action.localId)
            .map((item, order) => ({ ...item, order })),
        };
      case "MOVE_MEDIA": {
        const items = [...state.media];
        const from = items.findIndex((item) => item.localId === action.localId);
        const to = Math.max(0, Math.min(items.length - 1, from + action.delta));
        if (from < 0 || from === to) return state;
        const [item] = items.splice(from, 1);
        items.splice(to, 0, item);
        return { ...state, media: items.map((entry, order) => ({ ...entry, order })) };
      }
      case "JOB_UPDATE":
        return { ...state, job: action.job };
      case "JOB_SUCCEEDED": {
        const parentInteractionId = state.previousInteractionId || null;
        const turn = {
          interactionId: action.job.interactionId,
          parentInteractionId,
          outputUrl: action.job.videoUrl || action.job.outputUrl,
          durationSeconds: action.job.durationSeconds ?? null,
          store: action.job.stored ?? action.job.store ?? false,
          mode: action.job.mode,
          createdAt: action.job.updatedAt || new Date().toISOString(),
        };
        return { ...state, job: action.job, history: [...state.history, turn], selectedTurnId: turn.interactionId };
      }
      default:
        return state;
    }
  }

  function historyKey(projectFingerprint, shotId) {
    return `studioOmniHistory:${projectFingerprint || "session"}:${shotId}`;
  }

  function serializableState(state) {
    return {
      mode: state.mode,
      prompt: state.prompt,
      task: state.task,
      aspectRatio: state.aspectRatio,
      resolution: state.resolution,
      store: state.store,
      selectedTurnId: state.selectedTurnId,
      history: state.history,
    };
  }

  function pollDelay(attempt) {
    return Math.min(10000, 1500 * 2 ** Math.min(Math.max(0, attempt), 3));
  }

  return Object.freeze({
    MODES,
    ROLES,
    TASKS,
    RESOLUTIONS,
    ASPECT_RATIOS,
    DONE_STATUSES,
    compilePrompt,
    validateDraft,
    toJobRequest,
    createState,
    reduceState,
    historyKey,
    serializableState,
    pollDelay,
  });
});
