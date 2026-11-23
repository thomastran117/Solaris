/**
 * Validates a post-login `next` value so it can only point back into this site.
 *
 * Returns a same-origin relative URL (path + query + hash) or `null`. It rejects:
 * - absolute and scheme URLs (`https://evil.test`, `javascript:…`)
 * - protocol-relative and backslash tricks (`//evil.test`, `/\evil.test`, `\\evil`)
 * - control characters and whitespace that browsers strip or reinterpret
 * - anything that resolves to a different origin
 * - the login and OAuth callback routes themselves (redirect loops)
 */
const PLACEHOLDER_ORIGIN = "http://shopwave.invalid";
const LOOP_PREFIXES = ["/login/", "/auth/"];

export function safeNextPath(raw: string | null | undefined): string | null {
  if (!raw || raw.length > 2048) return null;
  // Control characters, whitespace and backslashes have no place in our paths and
  // are classic ways to smuggle an origin past naive checks.
  if (/[\u0000-\u001f\u007f\s\\]/.test(raw)) return null;
  if (!raw.startsWith("/") || raw.startsWith("//")) return null;

  let url: URL;
  try {
    url = new URL(raw, PLACEHOLDER_ORIGIN);
  } catch {
    return null;
  }
  if (url.origin !== PLACEHOLDER_ORIGIN) return null;

  const path = url.pathname;
  if (path === "/login" || LOOP_PREFIXES.some((p) => path.startsWith(p))) {
    return null;
  }
  return `${path}${url.search}${url.hash}`;
}
