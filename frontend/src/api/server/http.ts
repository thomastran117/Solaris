import "server-only";
import { envelopeData, isEnvelope } from "../envelope";

/**
 * Server-side reads of public backend data for server-rendered pages.
 *
 * Every request is anonymous: this module never reads the incoming request's cookies
 * or headers and never sends `Authorization` or `Cookie`. That is what guarantees
 * that server HTML and the Next.js data cache can only ever hold public data, so do
 * not add an auth option here. Per-user data is fetched in the browser instead.
 */

export class ApiHttpError extends Error {
  constructor(
    readonly status: number,
    readonly code: string | null,
    message: string
  ) {
    super(message);
    this.name = "ApiHttpError";
  }
}

type QueryValue = string | number | boolean | null | undefined;

export interface ServerGetOptions {
  searchParams?: Record<string, QueryValue>;
  /** Seconds the response may be served from the Next.js data cache. */
  revalidate?: number;
  tags?: string[];
  /** Abort after this many milliseconds (default 5000). */
  timeoutMs?: number;
}

/** Backend `/api` base URL as seen from the Next.js server, read at request time. */
export function apiBaseUrl(): string {
  const origin = (
    process.env.API_INTERNAL_URL || "http://localhost:8090"
  ).replace(/\/+$/, "");
  return `${origin}/api`;
}

export async function serverGet<T>(
  path: string,
  {
    searchParams,
    revalidate = 60,
    tags,
    timeoutMs = 5000,
  }: ServerGetOptions = {}
): Promise<T> {
  const url = new URL(`${apiBaseUrl()}${path}`);
  for (const [key, value] of Object.entries(searchParams ?? {})) {
    if (value !== undefined && value !== null && value !== "") {
      url.searchParams.set(key, String(value));
    }
  }

  const res = await fetch(url, {
    headers: { Accept: "application/json" },
    next: { revalidate, tags },
    signal: AbortSignal.timeout(timeoutMs),
  });

  const body: unknown = await res.json().catch(() => null);
  if (!res.ok) {
    const error = isEnvelope(body) ? body.error : null;
    throw new ApiHttpError(
      res.status,
      error?.code ?? null,
      `GET ${url.pathname} failed with ${res.status}`
    );
  }
  return envelopeData<T>(body);
}
