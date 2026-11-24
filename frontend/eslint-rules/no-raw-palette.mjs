/**
 * shopwave/no-raw-palette
 *
 * Keeps colour decisions in the semantic tokens (src/app/globals.css) so every
 * view works in light and dark mode. Flags, inside string and template
 * literals:
 *   - Tailwind palette colour utilities: `text-white`, `bg-slate-950`,
 *     `border-sky-400/30`, `hover:bg-blue-500`, `from-indigo-500`, …
 *   - arbitrary hex colours: `bg-[#0b1221]`, `text-[#fff]`
 *   - `backdrop-blur*` (reserved for the site header; pass `allowBlur`)
 *   - bare hex colour strings such as "#38bdf8" (chart and inline styles
 *     should use `var(--chart-1)` etc.)
 *
 * See frontend/DESIGN.md → "Guardrails" for the token to use instead.
 */

const UTILITY =
  "(?:text|bg|border(?:-[trblxy])?|ring|ring-offset|outline|divide|from|via|to|fill|stroke|placeholder|caret|accent|decoration|shadow)";
const PALETTE =
  "(?:white|black|slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose)";

// A class token: optional variant prefixes (hover:, dark:, md:, group-hover:),
// an optional leading "!" and "-", then a colour utility with an optional
// opacity modifier and trailing "!".
const CLASS_RE = new RegExp(
  String.raw`(?:^|[\s"'\x60{(])(?:[\w-]+(?:\[[^\]]*\])?:)*!?-?(${UTILITY}-(?:${PALETTE}(?:-\d{2,3})?|\[#[0-9a-fA-F]{3,8}\]))(?:\/(?:\d{1,3}|\[[^\]]+\]))?!?(?=$|[\s"'\x60)}])`,
  "g"
);
const BLUR_RE =
  /(?:^|[\s"'`])(?:[\w-]+:)*(backdrop-blur(?:-[\w[\]]+)?)(?=$|[\s"'`])/g;
const HEX_RE = /^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$/;

/** @type {import("eslint").Rule.RuleModule} */
const rule = {
  meta: {
    type: "suggestion",
    docs: {
      description:
        "Disallow raw Tailwind palette colours, hex colours and backdrop blur; use semantic tokens.",
    },
    schema: [
      {
        type: "object",
        properties: { allowBlur: { type: "boolean" } },
        additionalProperties: false,
      },
    ],
    messages: {
      palette:
        "'{{value}}' hard-codes a colour. Use a semantic token (text-foreground, text-muted-foreground, bg-surface, border-border, bg-primary, text-danger, …) — see frontend/DESIGN.md.",
      blur: "'{{value}}' is reserved for the site header. Use an opaque surface (bg-surface / bg-surface-raised).",
      hex: "Hex colour '{{value}}' bypasses the theme. Use a CSS variable such as var(--chart-1) or var(--foreground).",
    },
  },
  create(context) {
    const allowBlur = Boolean(context.options[0]?.allowBlur);

    function check(node, text) {
      if (!text) return;
      if (HEX_RE.test(text.trim())) {
        context.report({
          node,
          messageId: "hex",
          data: { value: text.trim() },
        });
        return;
      }
      for (const match of text.matchAll(CLASS_RE)) {
        context.report({
          node,
          messageId: "palette",
          data: { value: match[1] },
        });
      }
      if (!allowBlur) {
        for (const match of text.matchAll(BLUR_RE)) {
          context.report({
            node,
            messageId: "blur",
            data: { value: match[1] },
          });
        }
      }
    }

    return {
      Literal(node) {
        if (typeof node.value === "string") check(node, node.value);
      },
      TemplateElement(node) {
        check(node, node.value.raw);
      },
    };
  },
};

export default rule;
