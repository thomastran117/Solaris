import { useState } from "react";
import { useSelector } from "react-redux";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { Plus, Send, Trash2, Megaphone, X } from "lucide-react";
import { announcementsApi, type CreateAnnouncementPayload } from "../../api/follow";
import type { AnnouncementType, AnnouncementStatus } from "../../types/company";
import type { RootState } from "../../stores";
import { useAnims } from "../../hooks/useAnims";


const STATUS_COLORS: Record<AnnouncementStatus, string> = {
  DRAFT: "bg-white/10 text-white/60 border-white/15",
  PUBLISHED: "bg-green-500/15 text-green-300 border-green-400/30",
};

const TYPE_OPTIONS: { value: AnnouncementType; label: string }[] = [
  { value: "GENERAL", label: "General" },
  { value: "NEW_PRODUCT", label: "New Product" },
  { value: "SALE", label: "Sale" },
  { value: "NEW_COLLECTION", label: "New Collection" },
];

const inputCls =
  "w-full rounded-xl border border-white/10 bg-white/[0.06] backdrop-blur px-3 py-2 text-sm text-white placeholder:text-white/45 focus:outline-none focus:border-white/25";

export default function AdminAnnouncementsPage() {
  const companyId = useSelector((s: RootState) => s.auth.companyId);
  const queryClient = useQueryClient();
  const { fadeInUp, stagger } = useAnims();

  const [page, setPage] = useState(0);
  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState<CreateAnnouncementPayload>({
    title: "",
    body: "",
    type: "GENERAL",
  });
  const [formError, setFormError] = useState<string | null>(null);

  const listQuery = useQuery({
    queryKey: ["admin", "announcements", companyId, page],
    queryFn: () => announcementsApi.listAll(companyId!, page).then((r) => r.data),
    enabled: !!companyId,
  });

  const createMutation = useMutation({
    mutationFn: (payload: CreateAnnouncementPayload) =>
      announcementsApi.create(companyId!, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "announcements", companyId] });
      setShowCreate(false);
      setForm({ title: "", body: "", type: "GENERAL" });
      setFormError(null);
    },
    onError: () => setFormError("Failed to create announcement. Please try again."),
  });

  const publishMutation = useMutation({
    mutationFn: (annId: string) =>
      announcementsApi.update(companyId!, annId, { publish: true }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["admin", "announcements", companyId] }),
  });

  const deleteMutation = useMutation({
    mutationFn: (annId: string) => announcementsApi.delete(companyId!, annId),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["admin", "announcements", companyId] }),
  });

  function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    if (!form.title.trim()) { setFormError("Title is required."); return; }
    if (!form.body.trim()) { setFormError("Body is required."); return; }
    createMutation.mutate(form);
  }

  const announcements = listQuery.data?.items ?? [];
  const totalPages = listQuery.data?.totalPages ?? 0;

  return (
    <div className="min-h-screen bg-slate-950 relative">
      <div aria-hidden className="pointer-events-none fixed inset-0" style={{ zIndex: 0 }}>
        <div className="absolute top-1/4 left-1/4 w-[500px] h-[500px] rounded-full bg-blue-600/10 blur-[120px]" />
      </div>

      <div className="relative z-10 max-w-4xl mx-auto px-4 sm:px-6 py-8">
        {/* Header */}
        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-blue-500/15 border border-white/10 flex items-center justify-center">
              <Megaphone className="w-4 h-4 text-sky-200" />
            </div>
            <div>
              <h1 className="text-xl font-extrabold text-white">Announcements</h1>
              <p className="text-xs text-white/50 mt-0.5">
                Publish updates to your followers
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={() => { setShowCreate((v) => !v); setFormError(null); }}
            className="inline-flex items-center gap-2 text-sm bg-blue-600 hover:bg-blue-500 rounded-full px-4 py-2 text-white font-medium transition-colors"
          >
            {showCreate ? <X className="w-4 h-4" /> : <Plus className="w-4 h-4" />}
            {showCreate ? "Cancel" : "New Announcement"}
          </button>
        </div>

        {/* Create form */}
        {showCreate && (
          <motion.div
            initial={{ opacity: 0, y: -8 }}
            animate={{ opacity: 1, y: 0 }}
            className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-6 mb-6"
          >
            <h2 className="text-base font-semibold text-white mb-4">New Announcement</h2>
            <form onSubmit={handleCreate} className="flex flex-col gap-4">
              <div>
                <label className="text-xs text-white/60 mb-1.5 block">Title</label>
                <input
                  className={inputCls}
                  placeholder="e.g. Summer sale starts Friday!"
                  maxLength={200}
                  value={form.title}
                  onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
                />
              </div>
              <div>
                <label className="text-xs text-white/60 mb-1.5 block">Body</label>
                <textarea
                  className={`${inputCls} resize-none`}
                  rows={4}
                  placeholder="Share details about your announcement…"
                  value={form.body}
                  onChange={(e) => setForm((f) => ({ ...f, body: e.target.value }))}
                />
              </div>
              <div>
                <label className="text-xs text-white/60 mb-1.5 block">Type</label>
                <select
                  className={`${inputCls} appearance-none cursor-pointer`}
                  value={form.type}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, type: e.target.value as AnnouncementType }))
                  }
                >
                  {TYPE_OPTIONS.map((o) => (
                    <option key={o.value} value={o.value} className="bg-slate-900 text-white">
                      {o.label}
                    </option>
                  ))}
                </select>
              </div>
              {formError && (
                <p className="text-xs text-red-400">{formError}</p>
              )}
              <div className="flex gap-3 justify-end">
                <button
                  type="submit"
                  disabled={createMutation.isPending}
                  className="inline-flex items-center gap-2 text-sm bg-blue-600 hover:bg-blue-500 rounded-full px-5 py-2 text-white font-medium transition-colors disabled:opacity-50"
                >
                  Save as Draft
                </button>
              </div>
            </form>
          </motion.div>
        )}

        {/* Table */}
        {listQuery.isLoading && (
          <div className="space-y-3">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="rounded-2xl border border-white/10 bg-white/[0.04] h-20 animate-pulse" />
            ))}
          </div>
        )}

        {!listQuery.isLoading && announcements.length === 0 && (
          <div className="flex flex-col items-center justify-center py-24 text-center">
            <Megaphone className="w-12 h-12 text-white/20 mb-4" />
            <h3 className="text-lg font-semibold text-white mb-2">No announcements yet</h3>
            <p className="text-sm text-white/50">
              Create your first announcement to notify your followers.
            </p>
          </div>
        )}

        {!listQuery.isLoading && announcements.length > 0 && (
          <motion.div variants={stagger} initial="hidden" animate="visible" className="space-y-3">
            {announcements.map((ann) => (
              <motion.div
                key={ann.id}
                variants={fadeInUp}
                className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur px-5 py-4 flex items-start gap-4"
              >
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-1.5 flex-wrap">
                    <span
                      className={`text-xs font-semibold px-2.5 py-0.5 rounded-full border ${STATUS_COLORS[ann.status as AnnouncementStatus]}`}
                    >
                      {ann.status.charAt(0) + ann.status.slice(1).toLowerCase()}
                    </span>
                    <span className="text-xs text-white/40">
                      {TYPE_OPTIONS.find((o) => o.value === ann.type)?.label ?? ann.type}
                    </span>
                    {ann.publishedAt && (
                      <span className="text-xs text-white/35">
                        {new Date(ann.publishedAt).toLocaleDateString(undefined, {
                          month: "short", day: "numeric", year: "numeric",
                        })}
                      </span>
                    )}
                  </div>
                  <p className="text-sm font-semibold text-white leading-snug mb-1">{ann.title}</p>
                  <p className="text-xs text-white/55 line-clamp-2">{ann.body}</p>
                </div>
                <div className="flex items-center gap-2 flex-shrink-0">
                  {ann.status === "DRAFT" && (
                    <button
                      type="button"
                      title="Publish to followers"
                      onClick={() => publishMutation.mutate(ann.id)}
                      disabled={publishMutation.isPending}
                      className="p-2 rounded-xl border border-blue-500/30 bg-blue-600/10 text-sky-300 hover:bg-blue-600/20 transition-colors disabled:opacity-50"
                    >
                      <Send className="w-3.5 h-3.5" />
                    </button>
                  )}
                  {ann.status === "DRAFT" && (
                    <button
                      type="button"
                      title="Delete draft"
                      onClick={() => deleteMutation.mutate(ann.id)}
                      disabled={deleteMutation.isPending}
                      className="p-2 rounded-xl border border-white/10 text-white/40 hover:text-red-400 hover:border-red-400/30 transition-colors disabled:opacity-50"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>
              </motion.div>
            ))}
          </motion.div>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex items-center justify-center gap-2 mt-8">
            <button
              type="button"
              disabled={page === 0}
              onClick={() => setPage((p) => p - 1)}
              className="text-sm border border-white/10 rounded-full px-4 py-1.5 text-white/60 hover:bg-white/[0.06] disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
            >
              Previous
            </button>
            <span className="text-sm text-white/50">{page + 1} / {totalPages}</span>
            <button
              type="button"
              disabled={!listQuery.data?.hasNext}
              onClick={() => setPage((p) => p + 1)}
              className="text-sm border border-white/10 rounded-full px-4 py-1.5 text-white/60 hover:bg-white/[0.06] disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
            >
              Next
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
