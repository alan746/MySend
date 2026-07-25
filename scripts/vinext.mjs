import { spawnSync } from "node:child_process";

const action = process.argv[2];
if (!["dev", "build", "start"].includes(action)) {
  throw new Error("Expected one of: dev, build, start");
}

const executable = process.platform === "win32" ? "vinext.cmd" : "vinext";
const result = spawnSync(executable, [action], {
  env: {
    ...process.env,
    WRANGLER_LOG_PATH:
      process.env.WRANGLER_LOG_PATH ?? ".wrangler/wrangler.log",
  },
  shell: process.platform === "win32",
  stdio: "inherit",
});

process.exitCode = result.status ?? 1;
