import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { MessageSquare, Star, ChevronLeft, ChevronRight, Eye, CheckCircle } from "lucide-react";
import NavyGridGlowBackground from "../../components/layout/NavyGridGlowBackground";
import { feedbackAdminApi } from "../../api/feedback";
import type { Feedback, FeedbackCategory, FeedbackStatus } from "../../types/feedback";
import { FEEDBACK_CATEGORIES } from "../../schemas/feedback";
import { useAnims } from "../../hooks/useAnims";


const STATUS_FILTERS: { value: FeedbackStatus; label: string }[] = [
  { value: "OPEN",         label: "Open" },
  { value: "UNDER_REVIEW", label: "Under Review" },
  { value: "RESOLVED",     label: "Resolved" },
  { value: "CLOSED",       label: "Closed" },
];

const STATUS_COLORS: Record<FeedbackStatus, string> = {
  OPEN:         "border-blue-400/40 bg-blue-500/10 text-blue-300",
  UNDER_REVIEW: "border-yellow-400/40 bg-yellow-500/10 text-yellow-300",
  RESOLVED:     "border-green-400/40 bg-green-500/10 text-green-300",
  CLOSED:       "border-white/15 bg-white/[0.04] text-white/60",
};

function ActionButton({
  label,
  icon: Icon,
  tone,
  disabled,
  onClick,
}: {
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  tone: "ok" | "warn";
  disabled?: boolean;
  onClick: () => void;
}) {
  const toneClass =
    tone === "ok"
      ? "border-green-400/40 text-green-300 hover:bg-green-500/10"
      : "border-yellow-400/40 text-yellow-300 hover:bg-yellow-500/10";
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={`inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-semibold transition disabled:opacity-50 ${toneClass}`}
    >
      <Icon className="h-3.5 w-3.5" />
      {label}
    </button>
  );
}

function FeedbackCard({
  feedback,
  onUpdateStatus,
  isPending,
}: {
  feedback: Feedback;
  onUpdateStatus: (id: string, status: FeedbackStatus) => void;
  isPending: boolean;
}) {
  return (
    <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-5 shadow-sm">
      <div className="flex flex-wrap items-center gap-2 mb-3">
        <span className="text-xs font-semibold uppercase tracking-wide text-sky-200/90">
          {feedback.category.replace(/_/g, " ")}
        </span>
        <span
          className={`rounded-full border px-2.5 py-0.5 text-xs font-semibold ${STATUS_COLORS[feedback.status]}`}
        >
          {feedback.status.replace("_", " ")}
        </span>
        {feedback.pageContext && (
          <span className="rounded-full border border-white/10 bg-white/[0.04] px-2.5 py-0.5 text-xs text-white/50">
            {feedback.pageContext}
          </span>
        )}
        {feedback.rating != null && (
          <span className="flex items-center gap-0.5">
            {Array.from({ length: 5 }, (_, i) => (
              <Star
                key={i}
                className={`h-3.5 w-3.5 ${
                  i < feedback.rating! ? "text-blue-400 fill-blue-400" : "text-white/20"
                }`}
              />
            ))}
          </span>
        )}
        <span className="ml-auto text-xs text-white/40">
          {new Date(feedback.createdAt).toLocaleDateString()}
        </span>
      </div>

      <p className="text-xs text-white/55 mb-2">
        {feedback.submitterName ?? feedback.submitterEmail}
      </p>

      <p className="text-sm text-white/80 leading-relaxed whitespace-pre-line mb-4">
        {feedback.message}
      </p>

      <div className="flex flex-wrap gap-2">
        {feedback.status === "OPEN" && (
          <ActionButton
            label="Mark Under Review"
            icon={Eye}
            tone="warn"
            disabled={isPending}
            onClick={() => onUpdateStatus(feedback.id, "UNDER_REVIEW")}
          />
        )}
        {(feedback.status === "OPEN" || feedback.status === "UNDER_REVIEW") && (
          <ActionButton
            label="Mark Resolved"
            icon={CheckCircle}
            tone="ok"
            disabled={isPending}
            onClick={() => onUpdateStatus(feedback.id, "RESOLVED")}
          />
        )}
      </div>
    </div>
  );
}

export default function AdminFeedbackPage() {
  const { fadeInUp, stagger } = useAnims();
  const queryClient = useQueryClient();

  const [status, setStatus] = useState<FeedbackStatus | undefined>(undefined);
  const [category, setCategory] = useState<FeedbackCategory | undefined>(undefined);
  const [page, setPage] = useState(0);
  const size = 20;

  const feedbackQuery = useQuery({
    queryKey: ["admin", "feedback", status, category, page, size],
    queryFn: () =>
      feedbackAdminApi.list({ status, category, page, size }).then((r) => r.data),
  });

  const updateStatus = useMutation({
    mutationFn: ({ id, newStatus }: { id: string; newStatus: FeedbackStatus }) =>
      feedbackAdminApi.updateStatus(id, newStatus),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "feedback"] });
    },
  });

  const handleUpdateStatus = (id: string, newStatus: FeedbackStatus) => {
    updateStatus.mutate({ id, newStatus });
  };

  const filterPill = (active: boolean) =>
    `rounded-full border px-3 py-1 text-xs font-semibold transition ${
      active
        ? "border-blue-400/40 bg-blue-500/15 text-blue-300"
        : "border-white/15 bg-white/[0.04] text-white/70 hover:text-white hover:border-white/30"
    }`;

  return (
    <div className="relative min-h-screen bg-slate-950 text-white">
      <NavyGridGlowBackground />

      <div className="relative mx-auto max-w-4xl px-6 py-12 md:px-10">
        {/* Header */}
        <motion.div
          variants={fadeInUp}
          initial="hidden"
          animate="visible"
          className="mb-8"
        >
          <div className="flex items-center gap-3 mb-1">
            <div className="rounded-xl bg-blue-500/15 border border-white/10 p-2">
              <MessageSquare className="h-5 w-5 text-sky-200" />
            </div>
            <span className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90">
              Admin
            </span>
          </div>
          <h1 className="text-3xl font-extrabold text-white">Platform Feedback</h1>
          <p className="mt-1 text-sm text-white/60">
            Review and manage feedback submitted by users.
          </p>
        </motion.div>

        {/* Status filters */}
        <div className="flex flex-wrap gap-2 mb-3">
          <button
            className={filterPill(status === undefined)}
            onClick={() => { setStatus(undefined); setPage(0); }}
          >
            All
          </button>
          {STATUS_FILTERS.map((f) => (
            <button
              key={f.value}
              className={filterPill(status === f.value)}
              onClick={() => { setStatus(f.value); setPage(0); }}
            >
              {f.label}
            </button>
          ))}
        </div>

        {/* Category filters */}
        <div className="flex flex-wrap gap-2 mb-8">
          <button
            className={filterPill(category === undefined)}
            onClick={() => { setCategory(undefined); setPage(0); }}
          >
            All Categories
          </button>
          {FEEDBACK_CATEGORIES.map((c) => (
            <button
              key={c.value}
              className={filterPill(category === c.value)}
              onClick={() => { setCategory(c.value as FeedbackCategory); setPage(0); }}
            >
              {c.label}
            </button>
          ))}
        </div>

        {/* List */}
        {feedbackQuery.isLoading && (
          <p className="text-sm text-white/50">Loading…</p>
        )}

        {feedbackQuery.data && feedbackQuery.data.items.length === 0 && (
          <p className="text-sm text-white/50">No feedback found for the selected filters.</p>
        )}

        {feedbackQuery.data && feedbackQuery.data.items.length > 0 && (
          <>
            <motion.ul
              variants={stagger}
              initial="hidden"
              animate="visible"
              className="flex flex-col gap-4"
            >
              {feedbackQuery.data.items.map((f) => (
                <motion.li key={f.id} variants={fadeInUp}>
                  <FeedbackCard
                    feedback={f}
                    onUpdateStatus={handleUpdateStatus}
                    isPending={updateStatus.isPending}
                  />
                </motion.li>
              ))}
            </motion.ul>

            {feedbackQuery.data.totalPages > 1 && (
              <div className="flex items-center justify-center gap-3 mt-8">
                <button
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={!feedbackQuery.data.hasPrevious}
                  className="rounded-full border border-white/15 p-2 text-white/60 hover:text-white hover:border-white/30 disabled:opacity-30 transition-colors"
                >
                  <ChevronLeft className="h-4 w-4" />
                </button>
                <span className="text-xs text-white/50">
                  {page + 1} / {feedbackQuery.data.totalPages}
                </span>
                <button
                  onClick={() => setPage((p) => p + 1)}
                  disabled={!feedbackQuery.data.hasNext}
                  className="rounded-full border border-white/15 p-2 text-white/60 hover:text-white hover:border-white/30 disabled:opacity-30 transition-colors"
                >
                  <ChevronRight className="h-4 w-4" />
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
