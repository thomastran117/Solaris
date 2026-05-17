import { useState } from "react";
import { CheckCircle, Flag } from "lucide-react";
import RatingStars from "./RatingStars";
import HelpfulButton from "./HelpfulButton";
import ReviewPhotoGallery from "./ReviewPhotoGallery";
import ReportReviewModal from "./ReportReviewModal";
import type { Review } from "../../types/review";

interface Props {
  review: Review;
  companyId: number;
  productId: number;
  /** When provided, the helpful button uses this initial state to show whether the current user voted */
  userHasVoted?: boolean;
  /** True when the current viewer authored the review. Hides the report button. */
  isOwn?: boolean;
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

export default function ReviewCard({
  review,
  companyId,
  productId,
  userHasVoted = false,
  isOwn = false,
}: Props) {
  const [reportOpen, setReportOpen] = useState(false);
  const reviewerName = [review.reviewerFirstName, review.reviewerLastName]
    .filter(Boolean)
    .join(" ") || "Reviewer";

  return (
    <article className="rounded-2xl border border-white/10 bg-white/[0.06] p-5 backdrop-blur shadow-sm hover:shadow-md transition-shadow">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="flex flex-wrap items-center gap-2 text-xs">
            <span className="text-sm font-semibold text-white">{reviewerName}</span>
            {review.verifiedPurchase && (
              <span className="inline-flex items-center gap-1 rounded-full border border-green-500/20 bg-green-500/15 px-2 py-0.5 text-[10px] font-semibold text-green-400">
                <CheckCircle className="h-3 w-3" />
                Verified purchase
              </span>
            )}
            <span className="text-white/55">·</span>
            <span className="text-white/55">{formatDate(review.createdAt)}</span>
          </div>
          <div className="mt-1">
            <RatingStars rating={review.rating} size="sm" />
          </div>
        </div>
      </header>

      {review.title && (
        <h4 className="mt-3 text-base font-semibold text-white">{review.title}</h4>
      )}
      {review.body && (
        <p className="mt-1 whitespace-pre-line text-sm leading-relaxed text-white/80">
          {review.body}
        </p>
      )}

      {review.media.length > 0 && (
        <div className="mt-4">
          <ReviewPhotoGallery media={review.media} />
        </div>
      )}

      <footer className="mt-4 flex flex-wrap items-center gap-2">
        <HelpfulButton
          companyId={companyId}
          productId={productId}
          reviewId={review.id}
          helpfulCount={review.helpfulCount}
          userHasVoted={userHasVoted}
        />
        {!isOwn && (
          <button
            type="button"
            onClick={() => setReportOpen(true)}
            className="inline-flex items-center gap-1.5 rounded-full border border-white/15 bg-white/[0.04] px-3 py-1.5 text-xs font-semibold text-white/65 transition hover:border-red-400/40 hover:text-red-300"
          >
            <Flag className="h-3.5 w-3.5" />
            Report
          </button>
        )}
      </footer>

      <ReportReviewModal
        open={reportOpen}
        companyId={companyId}
        productId={productId}
        reviewId={review.id}
        onClose={() => setReportOpen(false)}
      />
    </article>
  );
}
