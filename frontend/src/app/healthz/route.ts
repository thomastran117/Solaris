/**
 * Liveness check for the frontend container (Docker HEALTHCHECK, orchestrators).
 * It lives outside /api because every /api/* request is rewritten to the backend, and
 * it deliberately does not call the backend: an unavailable API degrades pages but
 * must not make the frontend restart.
 */
export const dynamic = "force-dynamic";

export function GET() {
  return Response.json(
    { status: "ok" },
    { headers: { "Cache-Control": "no-store" } }
  );
}
