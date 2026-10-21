const { test, expect } = require("@playwright/test");

function image(name) {
  return { name, mimeType: "image/png", buffer: Buffer.from("mock-image") };
}

function video(name) {
  return { name, mimeType: "video/mp4", buffer: Buffer.from("mock-video") };
}

async function installOmniMock(page) {
  let mediaSequence = 0;
  let jobSequence = 0;
  const jobs = new Map();
  const requests = [];
  const deleted = [];

  // Context-level routing also intercepts browser-managed download requests.
  await page.context().route("**/api/omni/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();
    if (path === "/api/omni/config" && method === "GET") {
      return route.fulfill({
        json: { configured: true, model: "gemini-omni-1.1-flash", maxConcurrentJobs: 2 },
      });
    }
    if (path === "/api/omni/media" && method === "POST") {
      mediaSequence += 1;
      const body = request.postDataBuffer()?.toString("latin1") || "";
      const isVideo = /\.mp4/i.test(body);
      return route.fulfill({
        status: 201,
        json: {
          fileId: `media-${mediaSequence}`,
          name: `files/media-${mediaSequence}`,
          displayName: isVideo ? `video-${mediaSequence}.mp4` : `image-${mediaSequence}.png`,
          uri: `https://mock/files/media-${mediaSequence}`,
          mimeType: isVideo ? "video/mp4" : "image/png",
          sizeBytes: 10,
          state: "ACTIVE",
          durationSeconds: isVideo ? 3 : null,
          expirationTime: new Date(Date.now() + 86_400_000).toISOString(),
          error: "",
        },
      });
    }
    if (/^\/api\/omni\/media\//.test(path) && method === "DELETE") {
      return route.fulfill({ status: 204, body: "" });
    }
    if (path === "/api/omni/jobs" && method === "POST") {
      jobSequence += 1;
      const body = request.postDataJSON();
      requests.push(body);
      const id = `job-${jobSequence}`;
      jobs.set(id, body);
      return route.fulfill({
        status: 202,
        json: {
          id,
          shotId: body.shotId,
          mode: body.mode,
          status: "QUEUED",
          done: false,
          stored: body.store,
          statusUrl: `/api/omni/jobs/${id}`,
        },
      });
    }
    const jobMatch = path.match(/^\/api\/omni\/jobs\/(job-\d+)$/);
    if (jobMatch && method === "GET") {
      const body = jobs.get(jobMatch[1]);
      const interactionId = `interaction-${jobMatch[1].slice(4)}`;
      return route.fulfill({
        json: {
          id: jobMatch[1],
          shotId: body.shotId,
          mode: body.mode,
          status: "SUCCEEDED",
          done: true,
          interactionId,
          previousInteractionId: body.previousInteractionId || null,
          stored: body.store,
          videoUrl: `/api/omni/outputs/${interactionId}`,
          durationSeconds: 8,
          retryable: false,
          updatedAt: new Date().toISOString(),
          statusUrl: `/api/omni/jobs/${jobMatch[1]}`,
        },
      });
    }
    if (/^\/api\/omni\/outputs\//.test(path) && method === "GET") {
      return route.fulfill({ status: 200, contentType: "video/mp4", body: Buffer.from("mock-mp4") });
    }
    if (/^\/api\/omni\/(outputs|interactions)\//.test(path) && method === "DELETE") {
      deleted.push(path);
      return route.fulfill({ status: 204, body: "" });
    }
    return route.fulfill({ status: 404, json: { code: "MOCK_NOT_FOUND", message: path } });
  });
  return { requests, deleted };
}

async function openShots(page) {
  await page.goto("/");
  await expect(page.getByText("Studio sẵn sàng")).toBeVisible();
  await page.getByRole("button", { name: "Nạp gói mẫu" }).click();
  await expect(page.locator("#filename")).toContainText("BAY_MANH_NHAN_TAM");
  await page.locator('#nav button[data-tab="shots"]').click();
  await expect(page.locator("article.shot").first()).toBeVisible();
}

test("text, interpolation, references, multi-turn edit/extend, preview and delete", async ({ page }) => {
  const mock = await installOmniMock(page);
  await openShots(page);
  const cards = page.locator("article.shot");

  const textCard = cards.nth(0);
  await expect(textCard.locator(".omni-tabs button")).toHaveCount(3);
  await textCard.locator("[data-omni-generate]").click();
  await expect(textCard.locator(".omni-turn")).toHaveCount(1, { timeout: 10_000 });
  await expect(textCard.locator(".omni-turn video")).toHaveCount(1);
  await expect(textCard.locator(".omni-turn a[download]")).toHaveCount(1);
  const downloadEvent = page.waitForEvent("download");
  await textCard.locator(".omni-turn a[download]").click();
  await downloadEvent;

  await textCard.locator('[data-omni-mode="EDIT"]').click();
  await textCard.locator("[data-omni-parent]").selectOption("interaction-1");
  await textCard.locator("[data-omni-generate]").click();
  await expect(textCard.locator(".omni-turn")).toHaveCount(2, { timeout: 10_000 });
  expect(mock.requests.at(-1).previousInteractionId).toBe("interaction-1");

  await textCard.locator('[data-omni-mode="EXTEND"]').click();
  await textCard.locator("[data-omni-parent]").selectOption("interaction-2");
  await textCard.locator("[data-omni-upload]").setInputFiles(image("character.png"));
  await expect(textCard.locator("[data-omni-role]")).toHaveValue("IMAGE_REFERENCE");
  await textCard.locator("[data-omni-generate]").click();
  await expect(textCard.locator(".omni-turn")).toHaveCount(3, { timeout: 10_000 });
  expect(mock.requests.at(-1).mode).toBe("EXTEND");
  expect(mock.requests.at(-1).media[0].role).toBe("IMAGE_REFERENCE");

  const referenceCard = cards.nth(1);
  await referenceCard.locator("[data-omni-upload]").setInputFiles([
    image("first.png"), image("last.png"), image("style.png"),
    video("person.mp4"), video("object.mp4"),
  ]);
  await expect(referenceCard.locator("[data-omni-role]")).toHaveCount(5);
  await referenceCard.locator("[data-omni-role]").nth(1).selectOption("LAST_FRAME");
  await expect(referenceCard.locator(".omni-compiled pre")).toContainText("<FIRST_FRAME>");
  await expect(referenceCard.locator(".omni-compiled pre")).toContainText("<LAST_FRAME>");
  await expect(referenceCard.locator(".omni-compiled pre")).toContainText("<IMAGE_REF_0>");
  await expect(referenceCard.locator(".omni-compiled pre")).toContainText("<VIDEO_REF_0>");

  const uploadEditCard = cards.nth(2);
  await uploadEditCard.locator('[data-omni-mode="EDIT"]').click();
  await uploadEditCard.locator("[data-omni-upload]").setInputFiles(video("source.mp4"));
  await expect(uploadEditCard.locator("[data-omni-role]")).toHaveValue("SOURCE_VIDEO");
  await uploadEditCard.locator("[data-omni-generate]").click();
  await expect(uploadEditCard.locator(".omni-turn")).toHaveCount(1, { timeout: 10_000 });

  const uploadExtendCard = cards.nth(3);
  await uploadExtendCard.locator('[data-omni-mode="EXTEND"]').click();
  await uploadExtendCard.locator("[data-omni-upload]").setInputFiles([
    video("source-extend.mp4"), image("reference.png"),
  ]);
  await expect(uploadExtendCard.locator("[data-omni-role]").nth(0)).toHaveValue("SOURCE_VIDEO");
  await expect(uploadExtendCard.locator("[data-omni-role]").nth(1)).toHaveValue("IMAGE_REFERENCE");
  await uploadExtendCard.locator("[data-omni-generate]").click();
  await expect(uploadExtendCard.locator(".omni-turn")).toHaveCount(1, { timeout: 10_000 });

  await textCard.locator("[data-omni-delete-turn]").first().click();
  await expect(textCard.locator(".omni-turn")).toHaveCount(2);
  expect(mock.deleted.some((path) => path.includes("/interactions/"))).toBeTruthy();
});
