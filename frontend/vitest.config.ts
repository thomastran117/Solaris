import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import path from "path";

// Vite is used here only as Vitest's transform pipeline; the app itself is built
// and served by Next.js.
export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: "./src/test/setup.ts",
    // userEvent-driven form tests can exceed the 5 s default when every worker
    // is busy (CI runners, full local runs); they pass well under it alone.
    testTimeout: 15_000,
    exclude: ["node_modules/**", ".next/**"],
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
      "@components": path.resolve(__dirname, "./src/components"),
      "@configuration": path.resolve(__dirname, "./src/configuration"),
      "@stores": path.resolve(__dirname, "./src/stores"),
      "@views": path.resolve(__dirname, "./src/views"),
      // `server-only` throws outside a React Server Components build.
      "server-only": path.resolve(__dirname, "./src/test/serverOnlyStub.ts"),
    },
  },
});
