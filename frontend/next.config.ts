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
  // Self-contained server (`.next/standalone/server.js`) for the production image.
  output: "standalone",
  // Next.js gzips proxied responses too, which buffers Server-Sent Events: the order
  // tracking stream (/api/orders/{id}/stream) was delivered only when it closed.
  // Compression belongs at the edge proxy/CDN, which can exempt text/event-stream.
  compress: false,
  experimental: {
    // Idle timeout for requests proxied by the /api rewrite (default 30 s). Matches the
    // 60 s proxy_read_timeout the previous Nginx container used. SSE heartbeats every
    // 25 s keep order streams alive; upgraded WebSocket connections are not affected.
    proxyTimeout: 60_000,
  },
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
