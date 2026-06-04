import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useSelector } from "react-redux";
import { motion } from "framer-motion";
import { ChevronLeft, ChevronRight, MessageSquareText } from "lucide-react";
import axios, { AxiosError } from "axios";
import { reviewsApi, type ReviewListFilters } from "../../api/reviews";
import type { Review, ReviewSearchHit } from "../../types/review";
import type { RootState } from "../../stores";
import ReviewSummary from "./ReviewSummary";
import ReviewSearchBar from "./ReviewSearchBar";
import ReviewFilters from "./ReviewFilters";
import ReviewCard from "./ReviewCard";
import ReviewForm from "./ReviewForm";
import { useAnims } from "../../hooks/useAnims";

interface Props {
  companyId: string;
  productId: string;
}


const DEFAULT_FILTERS: ReviewListFilters = {
  page: 0,
  size: 10,
  sort: "createdAt",
  direction: "desc",
};

function isNotFoundError(err: unknown): boolean {
  return (
    axios.isAxiosError(err) &&
    (err as AxiosError).response?.status === 404
  );
}

/**
 * Search hits omit some Review fields; this shim adapts them into the ReviewCard contract
 * so users can browse keyword matches with the same UI affordances.
 */
function hitToReview(hit: ReviewSearchHit): Review {
  return {
    id: hit.id,
    productId: hit.productId,
    reviewerId: hit.reviewerId ?? "",
    reviewerFirstName: hit.reviewerName.split(" ")[0] ?? "",
    reviewerLastName: hit.reviewerName.split(" ").slice(1).join(" ") ?? "",
    rating: hit.rating,
    title: hit.title,
    body: hit.body,
    status: hit.status,
    verifiedPurchase: hit.verifiedPurchase,
    helpfulCount: hit.helpfulCount,
    media: [],
    createdAt: hit.createdAt,
    updatedAt: hit.updatedAt,
  };
}

export default function ReviewsSection({ companyId, productId }: Props) {
  const { fadeInUp, stagger } = useAnims();
  const accessToken = useSelector((s: RootState) => s.auth.accessToken);
  const authenticated = !!accessToken;

  const [filters, setFilters] = useState<ReviewListFilters>(DEFAULT_FILTERS);
  const [searchResults, setSearchResults] = useState<ReviewSearchHit[] | null>(null);

  const summaryQuery = useQuery({
    queryKey: ["reviews", "summary", productId],
    queryFn: () => reviewsApi.summary(companyId, productId).then((r) => r.data),
    enabled: !!productId,
  });

  const listQuery = useQuery({
    queryKey: ["reviews", "list", productId, filters],
    queryFn: () => reviewsApi.list(companyId, productId, filters).then((r) => r.data),
    enabled: !!productId && searchResults === null,
    placeholderData: (prev) => prev,
  });

  const myReviewQuery = useQuery({
    queryKey: ["reviews", "me", productId],
    queryFn: () => reviewsApi.getMine(companyId, productId).then((r) => r.data),
    enabled: authenticated && !!productId,
    retry: (failureCount, error) => !isNotFoundError(error) && failureCount < 2,
  });

  const myReview: Review | null = myReviewQuery.data ?? null;
  const myReviewId = myReview?.id;

  const reviewsToShow: Review[] = useMemo(() => {
    if (searchResults !== null) return searchResults.map(hitToReview);
    return listQuery.data?.content ?? [];
  }, [searchResults, listQuery.data]);

  const totalPages = listQuery.data?.totalPages ?? 0;

  const goToPage = (next: number) => {
    setFilters((f) => ({ ...f, page: Math.max(0, Math.min(totalPages - 1, next)) }));
  };

  return (
    <section className="relative px-6 py-16 md:px-10">
      <div className="mx-auto max-w-6xl">
        <motion.div
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true, amount: 0.15 }}
          variants={stagger}
          className="space-y-8"
        >
          <motion.div variants={fadeInUp} className="flex items-center gap-3">
            <span className="rounded-xl border border-white/10 bg-blue-500/15 p-2 text-sky-200">
              <MessageSquareText className="h-5 w-5" />
            </span>
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.25em] text-sky-200/90">
                Customer reviews
              </p>
              <h2 className="text-3xl font-extrabold tracking-tight text-white md:text-4xl">
                What shoppers are saying
              </h2>
            </div>
          </motion.div>

          <motion.div variants={fadeInUp}>
            <ReviewSummary summary={summaryQuery.data} isLoading={summaryQuery.isLoading} />
          </motion.div>

          <motion.div variants={fadeInUp}>
            <ReviewForm
              companyId={companyId}
              productId={productId}
              existing={myReview ?? null}
              authenticated={authenticated}
            />
          </motion.div>

          <motion.div variants={fadeInUp} className="space-y-4">
            <ReviewSearchBar
              companyId={companyId}
              productId={productId}
              onResults={(hits) => setSearchResults(hits)}
            />
            {searchResults === null && (
              <ReviewFilters filters={filters} onChange={setFilters} />
            )}
          </motion.div>

          <motion.div variants={stagger} className="space-y-4">
            {listQuery.isLoading && searchResults === null ? (
              <p className="text-sm text-white/60">Loading reviews...</p>
            ) : reviewsToShow.length === 0 ? (
              <motion.div
                variants={fadeInUp}
                className="rounded-2xl border border-white/10 bg-white/[0.06] p-8 text-center text-sm text-white/65 backdrop-blur"
              >
                {searchResults !== null
                  ? "No reviews matched your search."
                  : "No reviews yet — be the first to share your experience."}
              </motion.div>
            ) : (
              reviewsToShow.map((review) => (
                <motion.div key={review.id} variants={fadeInUp}>
                  <ReviewCard
                    review={review}
                    companyId={companyId}
                    productId={productId}
                    isOwn={review.id === myReviewId}
                  />
                </motion.div>
              ))
            )}
          </motion.div>

          {searchResults === null && totalPages > 1 && (
            <motion.div variants={fadeInUp} className="flex items-center justify-center gap-2">
              <button
                type="button"
                onClick={() => goToPage((filters.page ?? 0) - 1)}
                disabled={(filters.page ?? 0) === 0}
                className="rounded-full border border-white/15 bg-white/[0.04] p-2 text-white/70 hover:text-white disabled:opacity-40"
              >
                <ChevronLeft className="h-4 w-4" />
              </button>
              <span className="text-xs text-white/65">
                Page {(filters.page ?? 0) + 1} of {totalPages}
              </span>
              <button
                type="button"
                onClick={() => goToPage((filters.page ?? 0) + 1)}
                disabled={(filters.page ?? 0) >= totalPages - 1}
                className="rounded-full border border-white/15 bg-white/[0.04] p-2 text-white/70 hover:text-white disabled:opacity-40"
              >
                <ChevronRight className="h-4 w-4" />
              </button>
            </motion.div>
          )}
        </motion.div>
      </div>
    </section>
  );
}
