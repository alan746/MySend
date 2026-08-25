import vinext from "vinext";
import { defineConfig } from "vite";

const usePolling = process.env.VITE_USE_POLLING === "true";
export default defineConfig({
  server: usePolling
    ? { watch: { useFsEvents: false, usePolling: true } }
    : undefined,
  plugins: [vinext()],
});
