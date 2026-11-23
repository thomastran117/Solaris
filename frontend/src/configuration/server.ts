import "server-only";

let warned = false;

/**
 * Storefront marketplace for catalog pages. URLs carry no marketplace, so the
 * deployment configures one. Read on the server and passed to the client store by
 * the root layout, which keeps server-rendered and client query keys identical.
 */
export function getDefaultMarketplaceId(): string | null {
  const value = (
    process.env.DEFAULT_MARKETPLACE_ID ||
    process.env.NEXT_PUBLIC_DEFAULT_MARKETPLACE_ID ||
    ""
  ).trim();
  if (!value && !warned) {
    warned = true;
    console.warn(
      "DEFAULT_MARKETPLACE_ID is not set; catalog pages will show a not-configured state."
    );
  }
  return value || null;
}
