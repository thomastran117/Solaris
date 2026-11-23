import { useEffect, useMemo, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Search, X } from "lucide-react";
import { reviewsApi } from "../../api/reviews";
import type { ReviewSearchHit } from "../../types/review";

interface Props {
  companyId: string;
  productId: string;
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

export default function ReviewSearchBar({
  companyId,
  productId,
  onResults,
}: Props) {
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
      reviewsApi
        .search(companyId, productId, { q: debouncedQ, size: 10 })
        .then((r) => r.data),
    enabled,
    staleTime: 30_000,
  });

  const hits: ReviewSearchHit[] = useMemo(
    () => searchQuery.data?.content ?? [],
    [searchQuery.data]
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
        <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <input
          type="search"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Search reviews..."
          className="w-full rounded-full border border-border bg-surface py-2.5 pl-10 pr-10 text-sm text-foreground placeholder:text-muted-foreground focus:border-ring focus:outline-none"
        />
        {input && (
          <button
            type="button"
            onClick={() => setInput("")}
            aria-label="Clear search"
            className="absolute right-2 top-1/2 -translate-y-1/2 rounded-full p-1 text-muted-foreground hover:text-foreground"
          >
            <X className="h-4 w-4" />
          </button>
        )}
      </div>

      {enabled && searchQuery.isLoading && (
        <p className="text-xs text-muted-foreground">Searching...</p>
      )}

      {enabled && searchQuery.isSuccess && hits.length === 0 && (
        <p className="text-xs text-muted-foreground">
          No reviews matched &ldquo;{debouncedQ}&rdquo;.
        </p>
      )}

      {enabled && hits.length > 0 && (
        <ul className="space-y-3">
          {hits.map((hit) => (
            <li
              key={hit.id}
              className="rounded-xl border border-border bg-surface p-4"
            >
              <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                <span className="font-semibold text-foreground">
                  {hit.reviewerName || "Reviewer"}
                </span>
                <span>·</span>
                <span>{hit.rating}/5</span>
                {hit.verifiedPurchase && (
                  <>
                    <span>·</span>
                    <span className="rounded-full border border-success/40 bg-success-subtle px-2 py-0.5 text-[10px] font-semibold text-success">
                      Verified
                    </span>
                  </>
                )}
                <span className="ml-auto text-muted-foreground">
                  {new Date(hit.createdAt).toLocaleDateString()}
                </span>
              </div>
              {(hit.titleHighlights[0] || hit.title) && (
                <p className="mt-2 text-sm font-semibold text-foreground">
                  {renderHighlighted(
                    hit.titleHighlights[0] ?? hit.title ?? ""
                  ).map((p, i) =>
                    p.mark ? (
                      <mark key={i} className="bg-primary/10 text-foreground">
                        {p.text}
                      </mark>
                    ) : (
                      <span key={i}>{p.text}</span>
                    )
                  )}
                </p>
              )}
              {(hit.bodyHighlights.length > 0 || hit.body) && (
                <p className="mt-1 text-sm text-muted-foreground line-clamp-3">
                  {renderHighlighted(
                    hit.bodyHighlights[0] ?? hit.body ?? ""
                  ).map((p, i) =>
                    p.mark ? (
                      <mark key={i} className="bg-primary/10 text-foreground">
                        {p.text}
                      </mark>
                    ) : (
                      <span key={i}>{p.text}</span>
                    )
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
