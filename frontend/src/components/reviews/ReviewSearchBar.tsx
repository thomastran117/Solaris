import { useEffect, useMemo, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Search, X } from "lucide-react";
import { reviewsApi } from "../../api/reviews";
import type { ReviewSearchHit } from "../../types/review";

interface Props {
  companyId: number;
  productId: number;
  onResults?: (hits: ReviewSearchHit[] | null, query: string) => void;
}

const HIGHLIGHT_REGEX = /<mark>(.+?)<\/mark>/g;

function renderHighlighted(fragment: string) {
  const parts: Array<{ text: string; mark: boolean }> = [];
  let lastIndex = 0;
  let match: RegExpExecArray | null;
  const regex = new RegExp(HIGHLIGHT_REGEX);
  while ((match = regex.exec(fragment)) !== null) {
    if (match.index > lastIndex) {
      parts.push({ text: fragment.slice(lastIndex, match.index), mark: false });
    }
    parts.push({ text: match[1], mark: true });
    lastIndex = regex.lastIndex;
  }
  if (lastIndex < fragment.length) {
    parts.push({ text: fragment.slice(lastIndex), mark: false });
  }
  return parts;
}

export default function ReviewSearchBar({ companyId, productId, onResults }: Props) {
  const [input, setInput] = useState("");
  const [debouncedQ, setDebouncedQ] = useState("");
  const lastNotifiedRef = useRef<string | null>(null);

  useEffect(() => {
    const t = window.setTimeout(() => setDebouncedQ(input.trim()), 250);
    return () => window.clearTimeout(t);
  }, [input]);

  const enabled = debouncedQ.length >= 2;

  const searchQuery = useQuery({
    queryKey: ["reviews", "search", productId, debouncedQ],
    queryFn: () =>
      reviewsApi.search(companyId, productId, { q: debouncedQ, size: 10 }).then((r) => r.data),
    enabled,
    staleTime: 30_000,
  });

  const hits: ReviewSearchHit[] = useMemo(
    () => searchQuery.data?.content ?? [],
    [searchQuery.data],
  );

  useEffect(() => {
    if (!onResults) return;
    if (!enabled) {
      if (lastNotifiedRef.current !== null) {
        lastNotifiedRef.current = null;
        onResults(null, "");
      }
      return;
    }
    if (searchQuery.isSuccess) {
      lastNotifiedRef.current = debouncedQ;
      onResults(hits, debouncedQ);
    }
  }, [enabled, hits, debouncedQ, searchQuery.isSuccess, onResults]);

  return (
    <div className="space-y-3">
      <div className="relative">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-white/45" />
        <input
          type="search"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Search reviews..."
          className="w-full rounded-full border border-white/10 bg-white/[0.06] py-2.5 pl-10 pr-10 text-sm text-white placeholder:text-white/45 backdrop-blur focus:border-blue-400/40 focus:outline-none"
        />
        {input && (
          <button
            type="button"
            onClick={() => setInput("")}
            aria-label="Clear search"
            className="absolute right-2 top-1/2 -translate-y-1/2 rounded-full p-1 text-white/55 hover:text-white"
          >
            <X className="h-4 w-4" />
          </button>
        )}
      </div>

      {enabled && searchQuery.isLoading && (
        <p className="text-xs text-white/55">Searching...</p>
      )}

      {enabled && searchQuery.isSuccess && hits.length === 0 && (
        <p className="text-xs text-white/55">
          No reviews matched &ldquo;{debouncedQ}&rdquo;.
        </p>
      )}

      {enabled && hits.length > 0 && (
        <ul className="space-y-3">
          {hits.map((hit) => (
            <li
              key={hit.id}
              className="rounded-2xl border border-white/10 bg-white/[0.04] p-4 backdrop-blur"
            >
              <div className="flex flex-wrap items-center gap-2 text-xs text-white/60">
                <span className="font-semibold text-white">{hit.reviewerName || "Reviewer"}</span>
                <span>·</span>
                <span>{hit.rating}/5</span>
                {hit.verifiedPurchase && (
                  <>
                    <span>·</span>
                    <span className="rounded-full border border-green-400/30 bg-green-500/10 px-2 py-0.5 text-[10px] font-semibold text-green-300">
                      Verified
                    </span>
                  </>
                )}
                <span className="ml-auto text-white/45">
                  {new Date(hit.createdAt).toLocaleDateString()}
                </span>
              </div>
              {(hit.titleHighlights[0] || hit.title) && (
                <p className="mt-2 text-sm font-semibold text-white">
                  {renderHighlighted(hit.titleHighlights[0] ?? hit.title ?? "").map((p, i) =>
                    p.mark ? (
                      <mark key={i} className="bg-blue-500/30 text-white">
                        {p.text}
                      </mark>
                    ) : (
                      <span key={i}>{p.text}</span>
                    ),
                  )}
                </p>
              )}
              {(hit.bodyHighlights.length > 0 || hit.body) && (
                <p className="mt-1 text-sm text-white/75 line-clamp-3">
                  {renderHighlighted(hit.bodyHighlights[0] ?? hit.body ?? "").map((p, i) =>
                    p.mark ? (
                      <mark key={i} className="bg-blue-500/30 text-white">
                        {p.text}
                      </mark>
                    ) : (
                      <span key={i}>{p.text}</span>
                    ),
                  )}
                </p>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
