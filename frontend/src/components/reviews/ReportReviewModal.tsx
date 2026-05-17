import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { X } from "lucide-react";
import { reviewsApi } from "../../api/reviews";
import type { ReportReason } from "../../types/review";

const REASONS: { value: ReportReason; label: string; hint: string }[] = [
  { value: "SPAM", label: "Spam or advertising", hint: "Promotional content or repetitive posts" },
  { value: "OFFENSIVE", label: "Offensive or harmful", hint: "Hate, harassment, or graphic content" },
  { value: "OFF_TOPIC", label: "Off topic", hint: "Doesn't actually review this product" },
  { value: "FAKE", label: "Fake or fraudulent", hint: "Suspect this review is not genuine" },
  { value: "OTHER", label: "Other", hint: "Something else worth flagging" },
];

interface Props {
  open: boolean;
  companyId: number;
  productId: number;
  reviewId: number;
  onClose: () => void;
  onSubmitted?: () => void;
}

export default function ReportReviewModal({
  open,
  companyId,
  productId,
  reviewId,
  onClose,
  onSubmitted,
}: Props) {
  const [reason, setReason] = useState<ReportReason>("SPAM");
  const [detail, setDetail] = useState("");
  const [submitted, setSubmitted] = useState(false);

  const mutation = useMutation({
    mutationFn: () =>
      reviewsApi.reportReview(companyId, productId, reviewId, {
        reason,
        detail: detail.trim() || undefined,
      }),
    onSuccess: () => {
      setSubmitted(true);
      onSubmitted?.();
    },
  });

  if (!open) return null;

  const handleClose = () => {
    setReason("SPAM");
    setDetail("");
    setSubmitted(false);
    mutation.reset();
    onClose();
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 backdrop-blur p-4"
      role="dialog"
      aria-modal="true"
      onClick={handleClose}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-md rounded-2xl border border-white/10 bg-slate-950/90 p-6 shadow-xl backdrop-blur"
      >
        <div className="mb-4 flex items-start justify-between">
          <div>
            <h3 className="text-lg font-semibold text-white">Report this review</h3>
            <p className="mt-1 text-xs text-white/60">
              Moderators will look at flagged reviews. Repeated false reports may limit your account.
            </p>
          </div>
          <button
            type="button"
            onClick={handleClose}
            aria-label="Close"
            className="rounded-full border border-white/10 bg-white/[0.04] p-1.5 text-white/70 hover:text-white"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {submitted ? (
          <div className="rounded-xl border border-green-400/30 bg-green-500/10 p-4 text-sm text-green-300">
            Thanks — the review has been flagged for moderator review.
          </div>
        ) : (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              mutation.mutate();
            }}
            className="space-y-4"
          >
            <fieldset className="space-y-2">
              <legend className="text-xs font-semibold uppercase tracking-[0.25em] text-sky-200/90">
                Reason
              </legend>
              {REASONS.map((r) => (
                <label
                  key={r.value}
                  className={`flex cursor-pointer items-start gap-3 rounded-xl border p-3 transition ${
                    reason === r.value
                      ? "border-blue-400/40 bg-blue-500/10"
                      : "border-white/10 bg-white/[0.04] hover:border-white/20"
                  }`}
                >
                  <input
                    type="radio"
                    name="report-reason"
                    value={r.value}
                    checked={reason === r.value}
                    onChange={() => setReason(r.value)}
                    className="mt-1 accent-blue-500"
                  />
                  <div>
                    <div className="text-sm font-medium text-white">{r.label}</div>
                    <div className="text-xs text-white/55">{r.hint}</div>
                  </div>
                </label>
              ))}
            </fieldset>

            <label className="block">
              <span className="text-xs font-semibold uppercase tracking-[0.25em] text-sky-200/90">
                Details (optional)
              </span>
              <textarea
                value={detail}
                onChange={(e) => setDetail(e.target.value)}
                rows={3}
                maxLength={500}
                placeholder="Anything moderators should know"
                className="mt-2 w-full resize-none rounded-xl border border-white/10 bg-white/[0.04] p-3 text-sm text-white placeholder:text-white/45 focus:border-blue-400/40 focus:outline-none"
              />
              <span className="mt-1 block text-right text-[10px] text-white/45">
                {detail.length}/500
              </span>
            </label>

            {mutation.isError && (
              <p className="text-xs text-red-400">
                Could not submit report. Please try again later.
              </p>
            )}

            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={handleClose}
                className="rounded-full border border-white/20 px-4 py-2 text-sm font-semibold text-white/80 hover:bg-white/10 hover:text-white"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={mutation.isPending}
                className="rounded-full bg-blue-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-500 disabled:opacity-60"
              >
                {mutation.isPending ? "Submitting..." : "Submit report"}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
