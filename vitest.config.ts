import { defineConfig } from "vitest/config";

export default defineConfig({
  esbuild: { jsx: "automatic" },
  test: {
    environment: "jsdom",
    include: ["tests/web/**/*.test.{ts,tsx}"],
    setupFiles: ["tests/web/setup.tsx"],
    restoreMocks: true,
    coverage: {
      provider: "v8",
      include: ["app/**/*.{ts,tsx}"],
      reporter: ["text", "html", "json-summary"],
      thresholds: { lines: 80, statements: 80, branches: 85, functions: 85 },
    },
  },
});
