import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

/*
 * Guards WCAG 2.1 AA contrast for the semantic token pairs in globals.css, in
 * both themes. If you change a token value and this fails, pick a value that
 * passes rather than relaxing the threshold (see frontend/DESIGN.md).
 */

const css = readFileSync(
  path.resolve(process.cwd(), "src/app/globals.css"),
  "utf8"
);

function block(selector: string): Record<string, string> {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const match = css.match(new RegExp(`(?:^|\\n)${escaped}\\s*\\{([^}]*)\\}`));
  if (!match) throw new Error(`No ${selector} block in globals.css`);
  const vars: Record<string, string> = {};
  for (const [, name, value] of match[1].matchAll(/--([\w-]+):\s*([^;]+);/g)) {
    vars[name] = value.trim();
  }
  return vars;
}

function luminance(hex: string): number {
  const value = hex.replace("#", "");
  const full =
    value.length === 3
      ? value
          .split("")
          .map((c) => c + c)
          .join("")
      : value;
  const channels = [0, 2, 4].map(
    (i) => parseInt(full.slice(i, i + 2), 16) / 255
  );
  const [r, g, b] = channels.map((c) =>
    c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
  );
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

/** [foreground token, background token, minimum ratio] */
const TEXT = 4.5;
const UI = 3;
const PAIRS: Array<[string, string, number]> = [
  ["foreground", "background", TEXT],
  ["foreground", "surface", TEXT],
  ["foreground", "muted", TEXT],
  ["muted-foreground", "background", TEXT],
  ["muted-foreground", "surface", TEXT],
  ["muted-foreground", "muted", TEXT],
  ["primary-foreground", "primary", TEXT],
  ["primary-foreground", "primary-hover", TEXT],
  ["danger-foreground", "danger", TEXT],
  ["danger-foreground", "danger-hover", TEXT],
  ["foreground", "surface-raised", TEXT],
  ["muted-foreground", "surface-raised", TEXT],
  ["link", "background", TEXT],
  ["link", "surface", TEXT],
  ["success", "success-subtle", TEXT],
  ["warning", "warning-subtle", TEXT],
  ["danger", "danger-subtle", TEXT],
  ["info", "info-subtle", TEXT],
  ["danger", "surface", TEXT],
  ["success", "surface", TEXT],
  ["ring", "background", UI],
  ["ring", "surface", UI],
  ["primary", "background", UI],
];

const themes = { light: block(":root"), dark: block(".dark") };

describe.each(Object.entries(themes))("%s theme tokens", (_name, vars) => {
  it.each(PAIRS)("%s on %s meets %s:1", (fg, bg, min) => {
    expect(vars[fg], `--${fg} missing`).toMatch(/^#[0-9a-f]{3,6}$/i);
    expect(vars[bg], `--${bg} missing`).toMatch(/^#[0-9a-f]{3,6}$/i);
    expect(contrast(vars[fg], vars[bg])).toBeGreaterThanOrEqual(min);
  });

  it("defines the same tokens as the other theme", () => {
    expect(Object.keys(vars).sort()).toEqual(Object.keys(themes.light).sort());
  });
});
