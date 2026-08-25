import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { readFile } from "node:fs/promises";
import { createServer } from "node:net";
import { fileURLToPath } from "node:url";
import { after, before, test } from "node:test";

const projectRoot = fileURLToPath(new URL("..", import.meta.url));
let productionServer;
let productionOrigin;

async function availablePort() {
  const probe = createServer();
  probe.listen(0, "127.0.0.1");
  await once(probe, "listening");
  const address = probe.address();
  assert.notEqual(address, null);
  assert.equal(typeof address, "object");
  const port = address.port;
  probe.close();
  await once(probe, "close");
  return port;
}

before(async () => {
  const port = await availablePort();
  productionOrigin = `http://127.0.0.1:${port}`;
  productionServer = spawn(process.execPath, ["dist/standalone/server.js"], {
    cwd: projectRoot,
    env: {
      ...process.env,
      HOST: "127.0.0.1",
      PORT: String(port),
    },
    stdio: ["ignore", "pipe", "pipe"],
  });

  let startupOutput = "";
  productionServer.stdout.on("data", (chunk) => {
    startupOutput += chunk;
  });
  productionServer.stderr.on("data", (chunk) => {
    startupOutput += chunk;
  });

  for (let attempt = 0; attempt < 50; attempt += 1) {
    if (productionServer.exitCode !== null) {
      throw new Error(`Production server exited during startup:\n${startupOutput}`);
    }
    try {
      const response = await fetch(productionOrigin);
      if (response.ok) return;
    } catch {
      // The server has not bound its port yet.
    }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }

  throw new Error(`Production server did not become ready:\n${startupOutput}`);
}, { timeout: 10_000 });

after(async () => {
  if (productionServer?.exitCode === null) {
    productionServer.kill();
    await once(productionServer, "exit");
  }
});

async function render(pathname = "/") {
  return fetch(`${productionOrigin}${pathname}`, {
    headers: { accept: "text/html" },
  });
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
  assert.match(html, /href="\/login"/);
  assert.match(html, /href="\/signup"/);
  assert.doesNotMatch(html, /react-loading-skeleton/i);
});

test("server-renders a room-specific loading state", async () => {
  const response = await render("/room/4821k");
  assert.equal(response.status, 200);

  const html = await response.text();
  assert.match(html, /Opening ShareRoom\s*(?:<!-- -->)?\s*4821K/);
  assert.match(html, /role="status"/);
});

test("server-renders authentication, dashboard, and account settings routes", async () => {
  const [loginResponse, signupResponse, dashboardResponse, settingsResponse] = await Promise.all([
    render("/login"),
    render("/signup"),
    render("/dashboard"),
    render("/settings"),
  ]);
  assert.equal(loginResponse.status, 200);
  assert.equal(signupResponse.status, 200);
  assert.equal(dashboardResponse.status, 200);
  assert.equal(settingsResponse.status, 200);

  const [login, signup, dashboard, settings] = await Promise.all([
    loginResponse.text(),
    signupResponse.text(),
    dashboardResponse.text(),
    settingsResponse.text(),
  ]);

  assert.match(login, /Come back to your rooms/);
  assert.match(login, /Log in/);
  assert.match(login, /Create an account/);
  assert.match(signup, /Keep the rooms you create/);
  assert.match(signup, /Create your account/);
  assert.match(signup, /Send verification code/);
  assert.match(dashboard, /Opening your dashboard/);
  assert.match(settings, /Opening account settings/);
  assert.doesNotMatch(settings, /Choose a strong password/);
});

test("keeps Premium in maintenance behind authenticated settings", async () => {
  const settingsSource = await readFile(
    new URL("../app/ui/SettingsExperience.tsx", import.meta.url),
    "utf8",
  );

  assert.match(settingsSource, /Premium is being updated/);
  assert.match(settingsSource, /if \(!account\)/);
  assert.doesNotMatch(settingsSource, /api<Room|\/api\/rooms/);
});

test("opens the dashboard after authentication and exposes account navigation", async () => {
  const [authSource, headerSource, dashboardSource] = await Promise.all([
    readFile(new URL("../app/ui/AuthExperience.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/ui/SiteHeader.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/ui/DashboardExperience.tsx", import.meta.url), "utf8"),
  ]);

  assert.equal(
    authSource.match(/window\.location\.replace\("\/dashboard"\)/g)?.length,
    2,
  );
  assert.doesNotMatch(authSource, /router\.refresh\(\)/);
  assert.match(headerSource, /Dashboard/);
  assert.match(headerSource, /Settings/);
  assert.match(headerSource, /Log out/);
  assert.match(dashboardSource, /Create a ShareRoom/);
  assert.match(dashboardSource, /Join a ShareRoom/);
});

test("keeps API contracts and metadata product-specific", async () => {
  const [layout, api, packageJson, viteConfig, dockerfile, railwayConfig] = await Promise.all([
    readFile(new URL("../app/layout.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/lib/api.ts", import.meta.url), "utf8"),
    readFile(new URL("../package.json", import.meta.url), "utf8"),
    readFile(new URL("../vite.config.ts", import.meta.url), "utf8"),
    readFile(new URL("../Dockerfile", import.meta.url), "utf8"),
    readFile(new URL("../railway.toml", import.meta.url), "utf8"),
  ]);

  assert.match(layout, /MySend: Send what you need\./);
  assert.match(layout, /summary_large_image/);
  assert.match(api, /NEXT_PUBLIC_API_BASE_URL/);
  assert.match(api, /credentials:\s*"include"/);
  assert.match(api, /AbortSignal\.timeout\(REQUEST_TIMEOUT_MS\)/);
  assert.match(api, /The request timed out\. Please try again\./);
  assert.match(packageJson, /node dist\/standalone\/server\.js/);
  assert.doesNotMatch(packageJson, /react-loading-skeleton/);
  assert.doesNotMatch(packageJson, /wrangler|cloudflare/i);
  assert.doesNotMatch(viteConfig, /cloudflare|worker/i);
  assert.match(dockerfile, /COPY --from=build \/app\/dist\/standalone/);
  assert.match(railwayConfig, /healthcheckPath = "\/"/);
});
