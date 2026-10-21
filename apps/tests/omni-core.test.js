const test = require("node:test");
const assert = require("node:assert/strict");
const Omni = require("../public/omni-core.js");

const active = (fileId, role, extra = {}) => ({
  localId: fileId,
  fileId,
  displayName: `${fileId}.mp4`,
  role,
  state: "ACTIVE",
  order: 0,
  ...extra,
});

test("compiler numbers references and keeps first/last order", () => {
  const prompt = Omni.compilePrompt(
    "A continuous camera move.",
    [
      active("first", "FIRST_FRAME", { displayName: "first.png", order: 0 }),
      active("image-a", "IMAGE_REFERENCE", { displayName: "a.png", order: 1 }),
      active("last", "LAST_FRAME", { displayName: "last.png", order: 3 }),
      active("image-b", "IMAGE_REFERENCE", { displayName: "b.png", order: 2 }),
    ],
    "GENERATE",
  );
  assert.match(prompt, /<FIRST_FRAME> = first\.png/);
  assert.match(prompt, /<IMAGE_REF_0> = a\.png/);
  assert.match(prompt, /<IMAGE_REF_1> = b\.png/);
  assert.ok(prompt.indexOf("<FIRST_FRAME>") < prompt.indexOf("<LAST_FRAME>"));
});

test("validation rejects last without first and more than three video refs", () => {
  const draft = Omni.createState({
    prompt: "test",
    media: [
      active("last", "LAST_FRAME"),
      ...[1, 2, 3, 4].map((value) =>
        active(`ref-${value}`, "VIDEO_REFERENCE", { durationSeconds: 3, order: value }),
      ),
    ],
  });
  const errors = Omni.validateDraft(draft).join(" ");
  assert.match(errors, /LAST_FRAME/);
  assert.match(errors, /Tối đa ba VIDEO_REFERENCE/);
});

test("edit requires exactly one previous interaction or source video", () => {
  const base = Omni.createState({ mode: "EDIT", prompt: "change the sky" });
  assert.match(Omni.validateDraft(base).join(" "), /đúng một nguồn/);
  assert.equal(
    Omni.validateDraft({ ...base, previousInteractionId: "interaction-1" }).length,
    0,
  );
  const source = active("source", "SOURCE_VIDEO", { durationSeconds: 8 });
  assert.equal(Omni.validateDraft({ ...base, media: [source] }).length, 0);
  assert.match(
    Omni.validateDraft({ ...base, media: [source], previousInteractionId: "interaction-1" }).join(" "),
    /đúng một nguồn/,
  );
});

test("request builder omits auto task and keeps ordered roles", () => {
  const media = [
    active("b", "IMAGE_REFERENCE", { order: 2 }),
    active("a", "FIRST_FRAME", { order: 0, displayName: "first.png" }),
  ];
  const request = Omni.toJobRequest("SHOT-1", Omni.createState({ prompt: "move", media }));
  assert.equal(request.task, undefined);
  assert.deepEqual(request.media.map((item) => item.fileId), ["a", "b"]);
  assert.equal(request.media[0].order, 0);
});

test("timeline reducer preserves parent when branching from an old turn", () => {
  const state = Omni.createState({
    mode: "EDIT",
    previousInteractionId: "root-turn",
    history: [{ interactionId: "root-turn", store: true }],
  });
  const next = Omni.reduceState(state, {
    type: "JOB_SUCCEEDED",
    job: {
      interactionId: "branch-turn",
      outputUrl: "/api/omni/outputs/branch-turn",
      store: true,
      mode: "EDIT",
    },
  });
  assert.equal(next.history.at(-1).parentInteractionId, "root-turn");
  assert.equal(next.selectedTurnId, "branch-turn");
});

test("serializable state excludes media, jobs, and base64 payloads", () => {
  const saved = Omni.serializableState(
    Omni.createState({ media: [active("image", "FIRST_FRAME")], job: { id: "job" } }),
  );
  assert.equal(saved.media, undefined);
  assert.equal(saved.job, undefined);
  assert.match(Omni.historyKey("project", "SHOT-1"), /^studioOmniHistory:/);
  assert.equal(Omni.pollDelay(20), 10000);
});
