import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

async function render(pathname = "/") {
  const workerUrl = new URL("../dist/server/index.js", import.meta.url);
  workerUrl.searchParams.set("test", `${process.pid}-${Date.now()}`);
  const { default: worker } = await import(workerUrl.href);

  return worker.fetch(
    new Request(`http://localhost${pathname}`, {
      headers: { accept: "text/html" },
    }),
    {
      ASSETS: {
        fetch: async () => new Response("Not found", { status: 404 }),
      },
    },
    {
      waitUntil() {},
      passThroughOnException() {},
    },
  );
}

test("server-renders the MySend create and join experience", async () => {
  const response = await render();
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type") ?? "", /^text\/html\b/i);

  const html = await response.text();
  assert.match(html, /<title>Send what you need · MySend<\/title>/i);
  assert.match(html, /Create a room/);
  assert.match(html, /Join with code/);
  assert.match(html, /MySend/);
  assert.match(html, /Premium/);
  assert.doesNotMatch(html, /react-loading-skeleton/i);
});

test("server-renders a room-specific loading state", async () => {
  const response = await render("/room/4821k");
  assert.equal(response.status, 200);

  const html = await response.text();
  assert.match(html, /Opening ShareRoom\s*(?:<!-- -->)?\s*4821K/);
  assert.match(html, /role="status"/);
});

test("shows Premium as updating without an active checkout action", async () => {
  const response = await render("/settings");
  assert.equal(response.status, 200);

  const html = await response.text();
  assert.match(html, /Premium is being updated/);
  assert.doesNotMatch(html, /Upgrade to Premium/);
  assert.doesNotMatch(html, /Manage billing/);
});

test("keeps API contracts and metadata product-specific", async () => {
  const [layout, api, packageJson] = await Promise.all([
    readFile(new URL("../app/layout.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/lib/api.ts", import.meta.url), "utf8"),
    readFile(new URL("../package.json", import.meta.url), "utf8"),
  ]);

  assert.match(layout, /MySend — Send what you need\./);
  assert.match(layout, /summary_large_image/);
  assert.match(api, /NEXT_PUBLIC_API_BASE_URL/);
  assert.match(api, /credentials:\s*"include"/);
  assert.doesNotMatch(packageJson, /react-loading-skeleton/);
});
