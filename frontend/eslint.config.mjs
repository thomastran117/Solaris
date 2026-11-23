import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";
import noRawPalette from "./eslint-rules/no-raw-palette.mjs";

const shopwave = { rules: { "no-raw-palette": noRawPalette } };

// Legacy modules being retired by the redesign (see frontend/DESIGN.md).
const legacyImports = {
  paths: [
    {
      name: "react-icons",
      message: "Use lucide-react; it is the only icon set.",
    },
  ],
  patterns: [
    {
      group: ["react-icons/*"],
      message: "Use lucide-react; it is the only icon set.",
    },
    {
      group: [
        "**/layout/NavyGridGlowBackground",
        "**/section/SectionTitle",
        "**/section/SectionGlow",
        "**/section/SectionFade",
      ],
      message:
        "Legacy navy-glass component. Use SectionHeader / PageHeader from components/ui and plain token backgrounds.",
    },
  ],
};

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
  {
    // Design-system guard. Warns everywhere while views migrate; each migration
    // PR adds its folders to the "error" block below.
    files: ["src/**/*.{ts,tsx}"],
    plugins: { shopwave },
    rules: {
      "shopwave/no-raw-palette": "warn",
      "no-restricted-imports": ["warn", legacyImports],
    },
  },
  {
    // Migrated to semantic tokens: regressions fail lint.
    files: [
      "src/components/ui/**/*.{ts,tsx}",
      "src/components/theme/**/*.{ts,tsx}",
      "src/lib/**/*.{ts,tsx}",
    ],
    rules: {
      "shopwave/no-raw-palette": "error",
      "no-restricted-imports": ["error", legacyImports],
    },
  },
]);
