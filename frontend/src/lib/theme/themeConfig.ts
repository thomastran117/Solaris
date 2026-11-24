export type ThemePreference = "light" | "dark" | "system";
export type ResolvedTheme = "light" | "dark";

export const THEME_STORAGE_KEY = "shopwave-theme";

/**
 * Migration valve for the redesign (see frontend/DESIGN.md, "Migration status").
 *
 * Views that still hard-code the legacy dark palette are unreadable in light
 * mode, so routes under these prefixes are forced to dark (and the theme toggle
 * is hidden there) until they move to semantic tokens. Exceptions are migrated
 * routes that share a forced prefix. All of this is deleted once the last view
 * migrates.
 */
export const FORCE_ALL_DARK = false;

export const LEGACY_DARK_PREFIXES: readonly string[] = [
  // Seller workspace
  "/admin",
  "/dashboard",
];

export const LEGACY_DARK_EXCEPTIONS: readonly string[] = [
  // Seller catalog (redesign PR 6a)
  "/admin/products",
  "/admin/bundles",
  "/admin/kits",
  "/admin/collections",
  "/admin/bulk-import",
];

function underPrefix(pathname: string, prefix: string): boolean {
  return pathname === prefix || pathname.startsWith(`${prefix}/`);
}

export function isForcedDarkPath(pathname: string): boolean {
  if (FORCE_ALL_DARK) return true;
  if (LEGACY_DARK_EXCEPTIONS.some((p) => underPrefix(pathname, p)))
    return false;
  return LEGACY_DARK_PREFIXES.some((p) => underPrefix(pathname, p));
}

export function isThemePreference(value: unknown): value is ThemePreference {
  return value === "light" || value === "dark" || value === "system";
}
