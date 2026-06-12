import { useState } from "react";
import { useSelector } from "react-redux";
import { Link } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { Star, CheckCircle, ChevronLeft, ChevronRight, MessageSquare } from "lucide-react";
import type { RootState } from "../stores";
import NavyGridGlowBackground from "../components/layout/NavyGridGlowBackground";
import SectionGlow from "../components/section/SectionGlow";
import SectionFade from "../components/section/SectionFade";
import SectionTitle from "../components/section/SectionTitle";
import { feedbackApi } from "../api/feedback";
import type { FeedbackStatus } from "../types/feedback";
import { useAnims } from "../hooks/useAnims";
import {
  feedbackFormSchema,
  feedbackFormDefaults,
  FEEDBACK_CATEGORIES,
  type FeedbackFormValues,
} from "../schemas/feedback";


const inputCls =
  "w-full rounded-xl border border-white/10 bg-white/[0.04] px-3 py-2 text-sm text-white placeholder:text-white/45 focus:outline-none focus:border-white/20 transition-colors";
const labelCls = "block text-xs font-medium text-white/60 mb-1";

const STATUS_COLORS: Record<FeedbackStatus, string> = {
  OPEN:         "border-blue-400/40 bg-blue-500/10 text-blue-300",
  UNDER_REVIEW: "border-yellow-400/40 bg-yellow-500/10 text-yellow-300",
  RESOLVED:     "border-green-400/40 bg-green-500/10 text-green-300",
  CLOSED:       "border-white/15 bg-white/[0.04] text-white/60",
};

const STATUS_LABELS: Record<FeedbackStatus, string> = {
  OPEN:         "Open",
  UNDER_REVIEW: "Under Review",
  RESOLVED:     "Resolved",
  CLOSED:       "Closed",
};

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

export default function FeedbackPage() {
  const { fadeInUp, stagger } = useAnims();
  const accessToken = useSelector((state: RootState) => state.auth.accessToken);
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [submitSuccess, setSubmitSuccess] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const size = 10;

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<FeedbackFormValues>({
    resolver: zodResolver(feedbackFormSchema),
    defaultValues: {
      ...feedbackFormDefaults,
      pageContext: window.location.pathname,
    },
  });

  const rating = watch("rating");
  const message = watch("message") ?? "";

  const historyQuery = useQuery({
    queryKey: ["feedback", "mine", page, size],
    queryFn: () => feedbackApi.getMine(page, size).then((r) => r.data),
    enabled: !!accessToken,
  });

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
      setSubmitSuccess(true);
      setTimeout(() => {
        reset(feedbackFormDefaults);
        setSubmitSuccess(false);
      }, 2000);
    },
    onError: (err: unknown) => setErrorMsg(extractMessage(err)),
  });

  const onSubmit = (values: FeedbackFormValues) => {
    setErrorMsg(null);
    mutation.mutate(values);
  };

  if (!accessToken) {
    return (
      <div className="relative min-h-screen bg-slate-950 text-white flex items-center justify-center">
        <NavyGridGlowBackground />
        <div className="relative rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-8 text-center max-w-sm mx-4">
          <MessageSquare className="h-10 w-10 text-sky-200 mx-auto mb-4" />
          <h2 className="text-lg font-semibold text-white mb-2">Sign in to submit feedback</h2>
          <p className="text-sm text-white/60 mb-5">
            We'd love to hear what you think. Please sign in to share your feedback.
          </p>
          <Link
            to="/login"
            className="inline-block rounded-full bg-blue-600 hover:bg-blue-500 px-5 py-2 text-sm font-semibold text-white transition-colors"
          >
            Sign In
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="relative min-h-screen bg-slate-950 text-white">
      <NavyGridGlowBackground />

      {/* Header */}
      <section className="relative px-6 pt-16 pb-8 md:px-10">
        <SectionGlow variant="a" />
        <SectionFade top />
        <SectionTitle
          kicker="Platform"
          title="Share Your Feedback"
          subtitle="Help us make ShopWave better. Let us know what's working and what could be improved."
          align="center"
          theme="dark"
        />
      </section>

      {/* Submit form */}
      <section className="relative px-6 pb-12 md:px-10">
        <div className="mx-auto max-w-xl">
          {submitSuccess ? (
            <motion.div
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              className="flex flex-col items-center gap-3 py-10 text-center rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur"
            >
              <CheckCircle className="h-12 w-12 text-green-400" />
              <p className="text-lg font-semibold text-white">Thanks for your feedback!</p>
              <p className="text-sm text-white/60">We appreciate you helping us improve ShopWave.</p>
            </motion.div>
          ) : (
            <motion.div
              variants={fadeInUp}
              initial="hidden"
              whileInView="visible"
              viewport={{ once: true, amount: 0.25 }}
              className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-6 shadow-sm"
            >
              <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-5">
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
                  <div className="flex gap-1.5">
                    {[1, 2, 3, 4, 5].map((n) => (
                      <button
                        key={n}
                        type="button"
                        onClick={() => setValue("rating", rating === n ? null : n)}
                        className="p-0.5 transition-colors"
                        aria-label={`${n} star${n !== 1 ? "s" : ""}`}
                      >
                        <Star
                          className={`h-6 w-6 ${
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
                    rows={6}
                    placeholder="Tell us what you think about ShopWave…"
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
                  className="w-full rounded-xl bg-blue-600 hover:bg-blue-500 px-4 py-2.5 text-sm font-semibold text-white transition-colors disabled:opacity-50"
                >
                  {mutation.isPending ? "Submitting…" : "Submit Feedback"}
                </button>
              </form>
            </motion.div>
          )}
        </div>
      </section>

      {/* Past feedback */}
      <section className="relative bg-white/[0.04] backdrop-blur border-y border-white/10 px-6 py-12 md:px-10">
        <SectionGlow variant="b" />
        <SectionFade top />
        <SectionFade bottom />
        <div className="mx-auto max-w-xl">
          <h2 className="text-xl font-extrabold text-white mb-6">Your Past Feedback</h2>

          {historyQuery.isLoading && (
            <p className="text-sm text-white/50">Loading…</p>
          )}

          {historyQuery.data && historyQuery.data.items.length === 0 && (
            <p className="text-sm text-white/50">You haven't submitted any feedback yet.</p>
          )}

          {historyQuery.data && historyQuery.data.items.length > 0 && (
            <>
              <motion.ul
                variants={stagger}
                initial="hidden"
                whileInView="visible"
                viewport={{ once: true, amount: 0.1 }}
                className="flex flex-col gap-3"
              >
                {historyQuery.data.items.map((f) => (
                  <motion.li
                    key={f.id}
                    variants={fadeInUp}
                    className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-5 shadow-sm"
                  >
                    <div className="flex flex-wrap items-center gap-2 mb-2">
                      <span className="text-xs font-semibold uppercase tracking-wide text-sky-200/90">
                        {f.category.replace("_", " ")}
                      </span>
                      <span
                        className={`rounded-full border px-2.5 py-0.5 text-xs font-semibold ${STATUS_COLORS[f.status]}`}
                      >
                        {STATUS_LABELS[f.status]}
                      </span>
                      {f.rating != null && (
                        <span className="flex items-center gap-0.5">
                          {Array.from({ length: 5 }, (_, i) => (
                            <Star
                              key={i}
                              className={`h-3.5 w-3.5 ${
                                i < f.rating! ? "text-blue-400 fill-blue-400" : "text-white/20"
                              }`}
                            />
                          ))}
                        </span>
                      )}
                      <span className="ml-auto text-xs text-white/40">
                        {new Date(f.createdAt).toLocaleDateString()}
                      </span>
                    </div>
                    <p className="text-sm text-white/80 leading-relaxed line-clamp-3">
                      {f.message}
                    </p>
                  </motion.li>
                ))}
              </motion.ul>

              {historyQuery.data.totalPages > 1 && (
                <div className="flex items-center justify-center gap-3 mt-6">
                  <button
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                    disabled={!historyQuery.data.hasPrevious}
                    className="rounded-full border border-white/15 p-2 text-white/60 hover:text-white hover:border-white/30 disabled:opacity-30 transition-colors"
                  >
                    <ChevronLeft className="h-4 w-4" />
                  </button>
                  <span className="text-xs text-white/50">
                    {page + 1} / {historyQuery.data.totalPages}
                  </span>
                  <button
                    onClick={() => setPage((p) => p + 1)}
                    disabled={!historyQuery.data.hasNext}
                    className="rounded-full border border-white/15 p-2 text-white/60 hover:text-white hover:border-white/30 disabled:opacity-30 transition-colors"
                  >
                    <ChevronRight className="h-4 w-4" />
                  </button>
                </div>
              )}
            </>
          )}
        </div>
      </section>
    </div>
  );
}
