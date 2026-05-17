import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { motion, useReducedMotion, type Variants } from "framer-motion";
import { ShieldAlert, Eye, EyeOff, Trash2, ChevronLeft, ChevronRight } from "lucide-react";
import { reviewAdminApi } from "../../api/reviews";
import type { ModerationAction, ReportStatus, ReviewReport } from "../../types/review";

const useAnims = () => {
  const reduced = useReducedMotion();
  const fadeInUp: Variants = reduced
    ? { hidden: { opacity: 0 }, visible: { opacity: 1, transition: { duration: 0.3 } } }
    : { hidden: { opacity: 0, y: 12 }, visible: { opacity: 1, y: 0, transition: { duration: 0.45 } } };
  const stagger: Variants = { hidden: {}, visible: { transition: { staggerChildren: 0.05 } } };
  return { fadeInUp, stagger };
};

const STATUS_FILTERS: { value: ReportStatus; label: string }[] = [
  { value: "OPEN", label: "Open" },
  { value: "ACTIONED", label: "Actioned" },
  { value: "DISMISSED", label: "Dismissed" },
];

const REASON_LABEL: Record<string, string> = {
  SPAM: "Spam",
  OFFENSIVE: "Offensive",
  OFF_TOPIC: "Off topic",
  FAKE: "Fake",
  OTHER: "Other",
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
  tone: "ok" | "warn" | "danger";
  disabled?: boolean;
  onClick: () => void;
}) {
  const toneClass =
    tone === "ok"
      ? "border-green-400/40 text-green-300 hover:bg-green-500/10"
      : tone === "warn"
      ? "border-yellow-400/40 text-yellow-300 hover:bg-yellow-500/10"
      : "border-red-400/40 text-red-300 hover:bg-red-500/10";
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

export default function ReviewModerationPage() {
  const { fadeInUp, stagger } = useAnims();
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<ReportStatus>("OPEN");
  const [page, setPage] = useState(0);
  const size = 20;

  const reportsQuery = useQuery({
    queryKey: ["admin", "review-reports", status, page, size],
    queryFn: () => reviewAdminApi.listReports({ status, page, size }).then((r) => r.data),
  });

  const moderate = useMutation({
    mutationFn: ({ reviewId, action }: { reviewId: number; action: ModerationAction }) =>
      reviewAdminApi.moderate(reviewId, action),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "review-reports"] });
    },
  });

  const totalPages = reportsQuery.data?.totalPages ?? 0;
  const reports: ReviewReport[] = reportsQuery.data?.content ?? [];

  return (
    <main className="relative min-h-screen px-6 py-12 md:px-10">
      <motion.div
        initial="hidden"
        animate="visible"
        variants={stagger}
        className="mx-auto max-w-5xl space-y-8"
      >
        <motion.div variants={fadeInUp} className="flex items-center gap-3">
          <span className="rounded-xl border border-white/10 bg-blue-500/15 p-2 text-sky-200">
            <ShieldAlert className="h-5 w-5" />
          </span>
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.25em] text-sky-200/90">
              Moderation
            </p>
            <h1 className="text-2xl font-extrabold text-white">Review reports</h1>
          </div>
        </motion.div>

        <motion.div variants={fadeInUp} className="flex flex-wrap gap-2">
          {STATUS_FILTERS.map((f) => (
            <button
              key={f.value}
              type="button"
              onClick={() => {
                setStatus(f.value);
                setPage(0);
              }}
              className={`rounded-full border px-3 py-1.5 text-xs font-semibold transition ${
                status === f.value
                  ? "border-blue-400/40 bg-blue-500/15 text-blue-300"
                  : "border-white/15 bg-white/[0.04] text-white/70 hover:border-white/30 hover:text-white"
              }`}
            >
              {f.label}
            </button>
          ))}
        </motion.div>

        {reportsQuery.isLoading ? (
          <p className="text-sm text-white/60">Loading...</p>
        ) : reports.length === 0 ? (
          <motion.div
            variants={fadeInUp}
            className="rounded-2xl border border-white/10 bg-white/[0.06] p-8 text-center text-sm text-white/60 backdrop-blur"
          >
            No {status.toLowerCase()} reports right now.
          </motion.div>
        ) : (
          <motion.ul variants={stagger} className="space-y-4">
            {reports.map((r) => (
              <motion.li
                key={r.id}
                variants={fadeInUp}
                className="rounded-2xl border border-white/10 bg-white/[0.06] p-5 backdrop-blur shadow-sm"
              >
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="space-y-1">
                    <div className="flex items-center gap-2 text-xs">
                      <span className="rounded-full border border-red-400/30 bg-red-500/10 px-2 py-0.5 font-semibold text-red-300">
                        {REASON_LABEL[r.reason] ?? r.reason}
                      </span>
                      <span className="text-white/55">·</span>
                      <span className="text-white/65">
                        {r.reportCount} report{r.reportCount === 1 ? "" : "s"} total
                      </span>
                      <span className="text-white/55">·</span>
                      <span className="text-white/55">
                        {new Date(r.createdAt).toLocaleString()}
                      </span>
                    </div>
                    <p className="text-sm font-semibold text-white">
                      {r.reviewTitle ?? "(no title)"}
                    </p>
                    <p className="text-xs text-white/55">
                      by {r.reviewerName || "Unknown"} · rating {r.rating}/5 ·{" "}
                      status: {r.reviewStatus ?? "—"}
                    </p>
                  </div>
                  {status === "OPEN" && (
                    <div className="flex flex-wrap gap-2">
                      <ActionButton
                        label="Publish"
                        icon={Eye}
                        tone="ok"
                        disabled={moderate.isPending}
                        onClick={() =>
                          moderate.mutate({ reviewId: r.reviewId, action: "PUBLISH" })
                        }
                      />
                      <ActionButton
                        label="Hide"
                        icon={EyeOff}
                        tone="warn"
                        disabled={moderate.isPending}
                        onClick={() =>
                          moderate.mutate({ reviewId: r.reviewId, action: "HIDE" })
                        }
                      />
                      <ActionButton
                        label="Remove"
                        icon={Trash2}
                        tone="danger"
                        disabled={moderate.isPending}
                        onClick={() =>
                          moderate.mutate({ reviewId: r.reviewId, action: "REMOVE" })
                        }
                      />
                    </div>
                  )}
                </div>

                {r.reviewBody && (
                  <p className="mt-3 whitespace-pre-line text-sm text-white/80">
                    {r.reviewBody}
                  </p>
                )}

                {r.detail && (
                  <p className="mt-3 rounded-xl border border-white/10 bg-white/[0.04] p-3 text-xs text-white/65">
                    <span className="font-semibold text-white/80">Reporter note:</span>{" "}
                    {r.detail}
                  </p>
                )}

                {r.media.length > 0 && (
                  <div className="mt-3 flex flex-wrap gap-2">
                    {r.media.map((m) => (
                      <a
                        key={m.id}
                        href={m.url}
                        target="_blank"
                        rel="noreferrer noopener"
                        className="h-14 w-14 overflow-hidden rounded-lg border border-white/10 bg-white/[0.04]"
                      >
                        <img src={m.url} alt="" className="h-full w-full object-cover" />
                      </a>
                    ))}
                  </div>
                )}
              </motion.li>
            ))}
          </motion.ul>
        )}

        {totalPages > 1 && (
          <motion.div variants={fadeInUp} className="flex items-center justify-center gap-2">
            <button
              type="button"
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="rounded-full border border-white/15 bg-white/[0.04] p-2 text-white/70 disabled:opacity-40 hover:text-white"
            >
              <ChevronLeft className="h-4 w-4" />
            </button>
            <span className="text-xs text-white/65">
              Page {page + 1} of {totalPages}
            </span>
            <button
              type="button"
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1}
              className="rounded-full border border-white/15 bg-white/[0.04] p-2 text-white/70 disabled:opacity-40 hover:text-white"
            >
              <ChevronRight className="h-4 w-4" />
            </button>
          </motion.div>
        )}
      </motion.div>
    </main>
  );
}
