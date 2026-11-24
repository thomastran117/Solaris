export type ThemePreference = "light" | "dark" | "system";
export type ResolvedTheme = "light" | "dark";

export const THEME_STORAGE_KEY = "shopwave-theme";

/**
 * Migration valve for the redesign (see frontend/DESIGN.md, "Migration status").
 *
 * Views that still hard-code the legacy dark palette are unreadable in light
 * mode, so routes under these prefixes are forced to dark until they move to
 * semantic tokens. `FORCE_ALL_DARK` covers every route until the shared shell
 * and storefront have migrated. Both are deleted once the last view migrates.
 */
export const FORCE_ALL_DARK = true;

export const LEGACY_DARK_PREFIXES: readonly string[] = [];

export function isForcedDarkPath(pathname: string): boolean {
  if (FORCE_ALL_DARK) return true;
  return LEGACY_DARK_PREFIXES.some(
    (prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`)
  );
}

export function isThemePreference(value: unknown): value is ThemePreference {
  return value === "light" || value === "dark" || value === "system";
}
