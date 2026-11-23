import type { NextConfig } from "next";

// Where the Spring Boot API listens, as seen from the Next.js server. The browser
// always calls same-origin `/api/*`; this rewrite forwards it so the host-only
// refresh cookie keeps working and no CORS is needed. Rewrites are resolved when
// `next build` runs, so changing this value requires a rebuild.
const apiInternalUrl = (
  process.env.API_INTERNAL_URL || "http://localhost:8090"
).replace(/\/+$/, "");

const nextConfig: NextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${apiInternalUrl}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
