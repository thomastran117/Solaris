import { useEffect, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Pencil, Trash2 } from "lucide-react";
import { reviewsApi } from "../../api/reviews";
import type { Review, ReviewMedia } from "../../types/review";
import RatingStars from "./RatingStars";
import ReviewPhotoUploader from "./ReviewPhotoUploader";

interface Props {
  companyId: number;
  productId: number;
  existing: Review | null;
  authenticated: boolean;
  onSaved?: (review: Review) => void;
  onDeleted?: () => void;
}

const TITLE_MAX = 255;
const BODY_MAX = 5000;

export default function ReviewForm({
  companyId,
  productId,
  existing,
  authenticated,
  onSaved,
  onDeleted,
}: Props) {
  const queryClient = useQueryClient();
  const [rating, setRating] = useState(existing?.rating ?? 0);
  const [title, setTitle] = useState(existing?.title ?? "");
  const [body, setBody] = useState(existing?.body ?? "");
  const [media, setMedia] = useState<ReviewMedia[]>(existing?.media ?? []);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    setRating(existing?.rating ?? 0);
    setTitle(existing?.title ?? "");
    setBody(existing?.body ?? "");
    setMedia(existing?.media ?? []);
  }, [existing]);

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["reviews", "list", productId] });
    queryClient.invalidateQueries({ queryKey: ["reviews", "summary", productId] });
    queryClient.invalidateQueries({ queryKey: ["reviews", "me", productId] });
  };

  const createMutation = useMutation({
    mutationFn: () =>
      reviewsApi
        .create(companyId, productId, {
          rating,
          title: title.trim() || undefined,
          body: body.trim() || undefined,
        })
        .then((r) => r.data),
    onSuccess: (saved) => {
      setMedia(saved.media);
      invalidate();
      onSaved?.(saved);
      setErrorMessage(null);
    },
    onError: (err: unknown) => {
      setErrorMessage(extractMessage(err));
    },
  });

  const updateMutation = useMutation({
    mutationFn: () =>
      reviewsApi
        .update(companyId, productId, {
          rating,
          title: title.trim(),
          body: body.trim(),
        })
        .then((r) => r.data),
    onSuccess: (saved) => {
      setMedia(saved.media);
      invalidate();
      onSaved?.(saved);
      setErrorMessage(null);
    },
    onError: (err: unknown) => setErrorMessage(extractMessage(err)),
  });

  const deleteMutation = useMutation({
    mutationFn: () => reviewsApi.remove(companyId, productId),
    onSuccess: () => {
      invalidate();
      onDeleted?.();
      setRating(0);
      setTitle("");
      setBody("");
      setMedia([]);
      setErrorMessage(null);
    },
    onError: (err: unknown) => setErrorMessage(extractMessage(err)),
  });

  const submitting = createMutation.isPending || updateMutation.isPending;

  if (!authenticated) {
    return (
      <div className="rounded-2xl border border-white/10 bg-white/[0.06] p-6 text-sm text-white/65 backdrop-blur">
        <p>
          <a href="/login" className="font-semibold text-sky-200 underline-offset-2 hover:underline">
            Sign in
          </a>{" "}
          to leave a review. You'll need a delivered or shipped order for this product.
        </p>
      </div>
    );
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);
    if (rating < 1 || rating > 5) {
      setErrorMessage("Please pick a star rating.");
      return;
    }
    if (existing) {
      updateMutation.mutate();
    } else {
      createMutation.mutate();
    }
  };

  return (
    <form
      onSubmit={handleSubmit}
      className="space-y-5 rounded-2xl border border-white/10 bg-white/[0.06] p-6 backdrop-blur shadow-sm"
    >
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.25em] text-sky-200/90">
            {existing ? "Your review" : "Share your experience"}
          </p>
          <h3 className="mt-1 text-lg font-semibold text-white">
            {existing ? "Edit review" : "Write a review"}
          </h3>
        </div>
        {existing && (
          <button
            type="button"
            onClick={() => {
              if (window.confirm("Delete your review?")) deleteMutation.mutate();
            }}
            disabled={deleteMutation.isPending}
            className="inline-flex items-center gap-1.5 rounded-full border border-white/15 px-3 py-1.5 text-xs font-semibold text-white/65 transition hover:border-red-400/40 hover:text-red-300 disabled:opacity-50"
          >
            <Trash2 className="h-3.5 w-3.5" />
            Delete
          </button>
        )}
      </div>

      <div className="space-y-1.5">
        <span className="text-xs font-semibold uppercase tracking-[0.25em] text-sky-200/90">
          Rating
        </span>
        <RatingStars rating={rating} interactive size="lg" onChange={setRating} />
      </div>

      <label className="block">
        <span className="text-xs font-semibold uppercase tracking-[0.25em] text-sky-200/90">
          Title (optional)
        </span>
        <input
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          maxLength={TITLE_MAX}
          placeholder="Summarise your experience"
          className="mt-2 w-full rounded-xl border border-white/10 bg-white/[0.04] p-3 text-sm text-white placeholder:text-white/45 focus:border-blue-400/40 focus:outline-none"
        />
      </label>

      <label className="block">
        <span className="text-xs font-semibold uppercase tracking-[0.25em] text-sky-200/90">
          Details (optional)
        </span>
        <textarea
          value={body}
          onChange={(e) => setBody(e.target.value)}
          maxLength={BODY_MAX}
          rows={4}
          placeholder="Tell other shoppers what you liked or didn't"
          className="mt-2 w-full resize-none rounded-xl border border-white/10 bg-white/[0.04] p-3 text-sm text-white placeholder:text-white/45 focus:border-blue-400/40 focus:outline-none"
        />
        <span className="mt-1 block text-right text-[10px] text-white/45">
          {body.length}/{BODY_MAX}
        </span>
      </label>

      {existing && (
        <div className="space-y-2">
          <span className="text-xs font-semibold uppercase tracking-[0.25em] text-sky-200/90">
            Photos
          </span>
          <ReviewPhotoUploader
            companyId={companyId}
            productId={productId}
            reviewId={existing.id}
            media={media}
            onChange={(next) => {
              setMedia(next);
              invalidate();
            }}
          />
        </div>
      )}

      {!existing && (
        <p className="text-xs text-white/55">
          You can attach photos after saving your review.
        </p>
      )}

      {errorMessage && <p className="text-xs text-red-400">{errorMessage}</p>}

      <div className="flex justify-end">
        <button
          type="submit"
          disabled={submitting || rating === 0}
          className="inline-flex items-center gap-2 rounded-full bg-blue-600 px-5 py-2 text-sm font-semibold text-white transition hover:bg-blue-500 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {existing ? <Pencil className="h-3.5 w-3.5" /> : null}
          {submitting ? "Saving..." : existing ? "Save changes" : "Submit review"}
        </button>
      </div>
    </form>
  );
}

function extractMessage(err: unknown): string {
  if (typeof err === "object" && err !== null) {
    const maybe = err as { response?: { data?: { message?: string; detail?: string } } };
    return (
      maybe.response?.data?.detail ??
      maybe.response?.data?.message ??
      "Could not save review."
    );
  }
  return "Could not save review.";
}
