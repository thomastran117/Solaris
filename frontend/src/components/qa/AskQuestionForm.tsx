import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { qaApi } from "../../api/qa";

const schema = z.object({
  questionText: z
    .string()
    .min(10, "Question must be at least 10 characters")
    .max(500, "Question must not exceed 500 characters"),
});

type FormValues = z.infer<typeof schema>;

interface Props {
  productId: string;
}

export default function AskQuestionForm({ productId }: Props) {
  const queryClient = useQueryClient();
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const mutation = useMutation({
    mutationFn: ({ questionText }: FormValues) => qaApi.askQuestion(productId, questionText),
    onSuccess: () => {
      reset();
      queryClient.invalidateQueries({ queryKey: ["qa", "questions", productId] });
    },
  });

  return (
    <form onSubmit={handleSubmit((data) => mutation.mutate(data))} className="space-y-3">
      <textarea
        {...register("questionText")}
        placeholder="Ask a question about this product…"
        rows={3}
        className="w-full rounded-xl border border-white/10 bg-white/[0.06] backdrop-blur px-4 py-3 text-sm text-white placeholder:text-white/45 focus:outline-none focus:border-sky-400/50 focus:ring-1 focus:ring-sky-400/30 resize-none transition-colors"
      />
      {errors.questionText && (
        <p className="text-xs text-red-400">{errors.questionText.message}</p>
      )}
      {mutation.isError && (
        <p className="text-xs text-red-400">Failed to submit. Please try again.</p>
      )}
      <button
        type="submit"
        disabled={mutation.isPending}
        className="px-5 py-2 rounded-full bg-blue-600 hover:bg-blue-500 text-white text-sm font-semibold transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
      >
        {mutation.isPending ? "Submitting…" : "Submit Question"}
      </button>
    </form>
  );
}
