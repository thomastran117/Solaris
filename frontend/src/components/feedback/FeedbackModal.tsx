import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Star, CheckCircle } from "lucide-react";
import Modal from "../ui/Modal";
import { feedbackApi } from "../../api/feedback";
import {
  feedbackFormSchema,
  feedbackFormDefaults,
  FEEDBACK_CATEGORIES,
  type FeedbackFormValues,
} from "../../schemas/feedback";

interface FeedbackModalProps {
  open: boolean;
  onClose: () => void;
  pageContext?: string;
}

const inputCls =
  "w-full rounded-xl border border-white/10 bg-white/[0.04] px-3 py-2 text-sm text-white placeholder:text-white/45 focus:outline-none focus:border-white/20 transition-colors";
const labelCls = "block text-xs font-medium text-white/60 mb-1";

function extractMessage(err: unknown): string {
  if (typeof err === "object" && err !== null) {
    const maybe = err as { response?: { data?: { message?: string; detail?: string } } };
    return (
      maybe.response?.data?.detail ??
      maybe.response?.data?.message ??
      "Could not submit feedback."
    );
  }
  return "Could not submit feedback.";
}

export default function FeedbackModal({ open, onClose, pageContext }: FeedbackModalProps) {
  const queryClient = useQueryClient();
  const [success, setSuccess] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<FeedbackFormValues>({
    resolver: zodResolver(feedbackFormSchema),
    defaultValues: feedbackFormDefaults,
  });

  const rating = watch("rating");
  const message = watch("message") ?? "";

  useEffect(() => {
    if (open) {
      setValue("pageContext", pageContext ?? window.location.pathname);
      setSuccess(false);
      setErrorMsg(null);
    }
  }, [open, pageContext, setValue]);

  const mutation = useMutation({
    mutationFn: (values: FeedbackFormValues) =>
      feedbackApi.submit({
        category: values.category,
        message: values.message,
        rating: values.rating ?? undefined,
        pageContext: values.pageContext,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["feedback", "mine"] });
      setSuccess(true);
      setTimeout(() => {
        onClose();
        reset(feedbackFormDefaults);
        setSuccess(false);
      }, 1500);
    },
    onError: (err: unknown) => setErrorMsg(extractMessage(err)),
  });

  const onSubmit = (values: FeedbackFormValues) => {
    setErrorMsg(null);
    mutation.mutate(values);
  };

  return (
    <Modal open={open} onClose={onClose} title="Share Feedback" maxWidth="max-w-md">
      {success ? (
        <div className="flex flex-col items-center gap-3 py-6 text-center">
          <CheckCircle className="h-10 w-10 text-green-400" />
          <p className="text-sm font-semibold text-white">Thanks for your feedback!</p>
          <p className="text-xs text-white/60">We appreciate you helping us improve ShopWave.</p>
        </div>
      ) : (
        <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-4">
          {/* Category */}
          <div>
            <label className={labelCls}>Category</label>
            <select {...register("category")} className={inputCls}>
              {FEEDBACK_CATEGORIES.map((c) => (
                <option key={c.value} value={c.value}>
                  {c.label}
                </option>
              ))}
            </select>
            {errors.category && (
              <p className="mt-1 text-xs text-red-400">{errors.category.message}</p>
            )}
          </div>

          {/* Rating */}
          <div>
            <label className={labelCls}>Rating (optional)</label>
            <div className="flex gap-1">
              {[1, 2, 3, 4, 5].map((n) => (
                <button
                  key={n}
                  type="button"
                  onClick={() => setValue("rating", rating === n ? null : n)}
                  className="p-0.5 transition-colors"
                  aria-label={`${n} star${n !== 1 ? "s" : ""}`}
                >
                  <Star
                    className={`h-5 w-5 ${
                      rating != null && n <= rating
                        ? "text-blue-400 fill-blue-400"
                        : "text-white/25"
                    }`}
                  />
                </button>
              ))}
            </div>
          </div>

          {/* Message */}
          <div>
            <label className={labelCls}>Message</label>
            <textarea
              {...register("message")}
              rows={5}
              placeholder="Tell us what you think…"
              className={`${inputCls} resize-none`}
            />
            <div className="flex justify-between mt-1">
              {errors.message ? (
                <p className="text-xs text-red-400">{errors.message.message}</p>
              ) : (
                <span />
              )}
              <span className="text-xs text-white/40">{message.length}/5000</span>
            </div>
          </div>

          {errorMsg && <p className="text-xs text-red-400">{errorMsg}</p>}

          <button
            type="submit"
            disabled={isSubmitting || mutation.isPending}
            className="w-full rounded-xl bg-blue-600 hover:bg-blue-500 px-4 py-2 text-sm font-semibold text-white transition-colors disabled:opacity-50"
          >
            {mutation.isPending ? "Submitting…" : "Submit Feedback"}
          </button>
        </form>
      )}
    </Modal>
  );
}
