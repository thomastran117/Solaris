import type { MetadataRoute } from "next";

/**
 * Account and merchant screens are private shells (they also send `noindex`).
 * `/lists/` covers signed-in list management; shared lists under `/lists/public/`
 * stay reachable by link but send their own `noindex`.
 */
const PRIVATE_PREFIXES = [
  "/admin/",
  "/account",
  "/dashboard",
  "/feed",
  "/following",
  "/lists/",
  "/loyalty",
  "/price-watches",
  "/feedback",
  "/orders",
  "/b2b/",
  "/login",
  "/auth/",
  "/invite/",
  "/api/",
];

export default function robots(): MetadataRoute.Robots {
  return {
    rules: [{ userAgent: "*", allow: "/", disallow: PRIVATE_PREFIXES }],
  };
}
