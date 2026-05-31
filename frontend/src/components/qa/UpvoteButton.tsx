import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useSelector } from "react-redux";
import { ChevronUp } from "lucide-react";
import { qaApi } from "../../api/qa";
import type { RootState } from "../../stores";

interface Props {
  questionId: string;
  answerId: string;
  upvoteCount: number;
  productId: string;
}

export default function UpvoteButton({ questionId, answerId, upvoteCount, productId }: Props) {
  const queryClient = useQueryClient();
  const accessToken = useSelector((s: RootState) => s.auth.accessToken);
  const authenticated = !!accessToken;

  const mutation = useMutation({
    mutationFn: () => qaApi.upvoteAnswer(questionId, answerId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["qa", "questions", productId] });
    },
  });

  return (
    <button
      disabled={!authenticated || mutation.isPending}
      onClick={() => mutation.mutate()}
      title={authenticated ? "Upvote this answer" : "Sign in to upvote"}
      className="flex items-center gap-1 px-2 py-1 rounded-lg text-xs font-medium border border-white/10 bg-white/[0.04] text-white/60 hover:text-sky-200 hover:border-sky-400/30 hover:bg-sky-400/10 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
    >
      <ChevronUp className="w-3.5 h-3.5" />
      {upvoteCount}
    </button>
  );
}
