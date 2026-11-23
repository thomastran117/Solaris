import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

export default defineConfig([
  ...nextVitals,
  ...nextTs,
  globalIgnores([
    ".next/**",
    "out/**",
    "dist/**",
    "coverage/**",
    "next-env.d.ts",
  ]),
  {
    rules: {
      // CLAUDE.md: avoid `any`. Existing occurrences are reported, not blocking.
      "@typescript-eslint/no-explicit-any": "warn",
      "@typescript-eslint/no-unused-vars": "off",
      "no-useless-escape": "off",
      // Introduced by eslint-config-next 16 (React Compiler-oriented hook rules and
      // next/image guidance). They flag pre-existing patterns across the codebase;
      // start as warnings and tighten in follow-up work.
      "@next/next/no-img-element": "warn",
      "react-hooks/set-state-in-effect": "warn",
      "react-hooks/refs": "warn",
      "react-hooks/purity": "warn",
      "react-hooks/immutability": "warn",
      "react-hooks/static-components": "warn",
      "react-hooks/preserve-manual-memoization": "warn",
      "react-hooks/incompatible-library": "warn",
      "react-hooks/use-memo": "warn",
      "react/no-unescaped-entities": "warn",
    },
  },
]);
