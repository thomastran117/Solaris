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
      "src/components/layout/**/*.{ts,tsx}",
      "src/components/admin/**/*.{ts,tsx}",
      "src/app/layout.tsx",
      "src/app/not-found.tsx",
      "src/app/*/layout.tsx",
      "src/app/(storefront)/not-found.tsx",
      "src/app/(storefront)/error.tsx",
      "src/views/admin/AdminOverviewPage.tsx",
      // Storefront and auth (redesign PR 3)
      "src/views/{HomePage,BrowsePage,ProductPage,CompanyPage,ComparisonPage,CollectionPage,KitPage,PublicSavedListPage,GiftCardsPage,PrivacyPage,TermsAndConditionPage,HelloTestPage}.tsx",
      "src/views/{LoginPage,SignupPage,VerifyTokenPage,AuthCallback,InviteAcceptPage}.tsx",
      "src/app/(storefront)/**/*.{ts,tsx}",
      "src/app/(auth)/**/*.{ts,tsx}",
      "src/components/{auth,collection,company,kit,product,qa,report,reviews,savedlist,search,feedback}/**/*.{ts,tsx}",
      "src/hooks/{useCaptcha,useCompleteSignIn,useGoogleSignIn}.{ts,tsx}",
      "src/api/auth.ts",
      "src/schemas/auth.ts",
      // Cart and checkout (redesign PR 4)
      "src/views/{CartPage,CheckoutPage}.tsx",
      "src/app/(checkout)/**/*.{ts,tsx}",
      "src/components/{cart,checkout}/**/*.{ts,tsx}",
      "src/components/order/ShippingRatePanel.tsx",
      "src/hooks/useCheckoutSubmit.ts",
      "src/stores/{cartSlice,cartPersistence}.ts",
      "src/api/{addresses,pricing}.ts",
      "src/schemas/checkout.ts",
      "src/hooks/useLogout.ts",
      "src/lib/**/*.{ts,tsx}",
      "src/test/**/*.{ts,tsx}",
    ],
    ignores: [
      // Legacy, deleted once the last view migrates.
      "src/components/layout/NavyGridGlowBackground.tsx",
      // Only used by account/admin views; migrate with them.
      "src/components/product/ProductHistoryTimeline.tsx",
      "src/components/savedlist/SavedListCard.tsx",
      "src/components/savedlist/SavedListItemRow.tsx",
    ],
    rules: {
      "shopwave/no-raw-palette": "error",
      "no-restricted-imports": ["error", legacyImports],
    },
  },
  {
    // The sticky site header is the one surface allowed a backdrop blur.
    files: ["src/components/layout/SiteHeader.tsx"],
    rules: { "shopwave/no-raw-palette": ["error", { allowBlur: true }] },
  },
]);
