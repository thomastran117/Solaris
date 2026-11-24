import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useSelector } from "react-redux";
import { ThumbsUp } from "lucide-react";
import { reviewsApi } from "../../api/reviews";
import type { RootState } from "../../stores";
import type { HelpfulVote } from "../../types/review";

interface Props {
  companyId: string;
  productId: string;
  reviewId: string;
  helpfulCount: number;
  userHasVoted: boolean;
  onChange?: (vote: HelpfulVote) => void;
}

export default function HelpfulButton({
  companyId,
  productId,
  reviewId,
  helpfulCount,
  userHasVoted,
  onChange,
}: Props) {
  const accessToken = useSelector((s: RootState) => s.auth.accessToken);
  const queryClient = useQueryClient();
  const [optimistic, setOptimistic] = useState<HelpfulVote>({
    reviewId,
    helpfulCount,
    userHasVoted,
  });

  const mutation = useMutation({
    mutationFn: async (next: boolean) => {
      const res = next
        ? await reviewsApi.voteHelpful(companyId, productId, reviewId)
        : await reviewsApi.removeHelpful(companyId, productId, reviewId);
      return res.data;
    },
    onMutate: (next) => {
      const previous = optimistic;
      const delta = next === previous.userHasVoted ? 0 : next ? 1 : -1;
      const projected: HelpfulVote = {
        reviewId,
        helpfulCount: Math.max(0, previous.helpfulCount + delta),
        userHasVoted: next,
      };
      setOptimistic(projected);
      return { previous };
    },
    onError: (_err, _next, ctx) => {
      if (ctx?.previous) setOptimistic(ctx.previous);
    },
    onSuccess: (vote) => {
      setOptimistic(vote);
      onChange?.(vote);
      queryClient.invalidateQueries({
        queryKey: ["reviews", "list", productId],
      });
    },
  });

  const disabled = !accessToken || mutation.isPending;

  const handleClick = () => {
    if (disabled) return;
    mutation.mutate(!optimistic.userHasVoted);
  };

  const baseClasses =
    "inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-semibold transition";
  const activeClasses = optimistic.userHasVoted
    ? "border-primary/40 bg-primary/10 text-link"
    : "border-border bg-surface text-muted-foreground hover:border-input hover:text-foreground";
  const disabledClasses = disabled ? "opacity-60 cursor-not-allowed" : "";

  return (
    <button
      type="button"
      onClick={handleClick}
      disabled={disabled}
      aria-pressed={optimistic.userHasVoted}
      title={!accessToken ? "Sign in to vote" : undefined}
      className={`${baseClasses} ${activeClasses} ${disabledClasses}`}
    >
      <ThumbsUp
        className={`h-3.5 w-3.5 ${optimistic.userHasVoted ? "fill-link" : ""}`}
      />
      <span>Helpful</span>
      <span className="text-muted-foreground">·</span>
      <span>{optimistic.helpfulCount}</span>
    </button>
  );
}
