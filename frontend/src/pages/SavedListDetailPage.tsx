import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { motion, useReducedMotion, type Variants } from "framer-motion";
import {
  ChevronLeft,
  Globe,
  Lock,
  Pencil,
  Save,
  Share2,
  Trash2,
  Bookmark,
} from "lucide-react";
import {
  useDeleteSavedList,
  useRemoveSavedListItem,
  useSavedList,
  useUpdateSavedList,
  useUpdateSavedListItem,
} from "../hooks/useSavedLists";
import SavedListItemRow from "../components/savedlist/SavedListItemRow";
import { LIST_TYPE_META, showsProgress } from "../components/savedlist/listTypeMeta";

const useAnims = () => {
  const prefersReducedMotion = useReducedMotion();
  const fadeInUp: Variants = prefersReducedMotion
    ? { hidden: { opacity: 0 }, visible: { opacity: 1, transition: { duration: 0.2 } } }
    : { hidden: { opacity: 0, y: 14 }, visible: { opacity: 1, y: 0, transition: { duration: 0.4 } } };
  return { fadeInUp };
};

export default function SavedListDetailPage() {
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const listId = id ?? null;
  const { fadeInUp } = useAnims();

  const list = useSavedList(listId);
  const update = useUpdateSavedList(listId ?? "");
  const remove = useDeleteSavedList();
  const updateItem = useUpdateSavedListItem(listId ?? "");
  const removeItem = useRemoveSavedListItem(listId ?? "");

  const [editing, setEditing] = useState(false);
  const [editName, setEditName] = useState("");
  const [editDescription, setEditDescription] = useState("");
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function startEdit() {
    if (!list.data) return;
    setEditName(list.data.name);
    setEditDescription(list.data.description ?? "");
    setEditing(true);
  }

  async function saveEdit() {
    setError(null);
    const trimmed = editName.trim();
    if (!trimmed) {
      setError("Name cannot be empty.");
      return;
    }
    try {
      await update.mutateAsync({
        name: trimmed,
        description: editDescription.trim() || undefined,
      });
      setEditing(false);
    } catch (err) {
      setError(extractErrorMessage(err) ?? "Could not save changes.");
    }
  }

  async function togglePublic() {
    if (!list.data) return;
    setError(null);
    try {
      await update.mutateAsync({ isPublic: !list.data.isPublic });
    } catch (err) {
      setError(extractErrorMessage(err) ?? "Could not update visibility.");
    }
  }

  async function handleDelete() {
    if (!list.data) return;
    if (!window.confirm(`Delete "${list.data.name}"? This cannot be undone.`)) return;
    try {
      await remove.mutateAsync(list.data.id);
      navigate("/lists");
    } catch (err) {
      setError(extractErrorMessage(err) ?? "Could not delete the list.");
    }
  }

  async function copyShareLink() {
    if (!list.data?.shareSlug) return;
    const url = `${window.location.origin}/lists/public/${list.data.shareSlug}`;
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      setError("Could not copy share link.");
    }
  }

  if (list.isLoading) {
    return (
      <div className="min-h-screen bg-slate-950 px-4 py-10">
        <div className="max-w-4xl mx-auto space-y-4">
          <div className="h-8 w-1/3 rounded-lg bg-white/[0.06] animate-pulse" />
          <div className="h-24 rounded-2xl border border-white/10 bg-white/[0.04] animate-pulse" />
          <div className="h-24 rounded-2xl border border-white/10 bg-white/[0.04] animate-pulse" />
        </div>
      </div>
    );
  }

  if (list.isError || !list.data) {
    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center">
        <div className="text-center">
          <p className="text-white/70 mb-4">List not found.</p>
          <Link to="/lists" className="text-sky-300 hover:text-sky-200 transition-colors text-sm">
            ← Back to your lists
          </Link>
        </div>
      </div>
    );
  }

  const data = list.data;
  const meta = LIST_TYPE_META[data.type];
  const Icon = meta.Icon;
  const total = data.items.length;
  const purchased = data.items.filter((i) => i.purchased).length;
  const showProgress = showsProgress(data.type) && total > 0;
  const sortedItems = [...data.items].sort((a, b) => {
    if (a.purchased !== b.purchased) return a.purchased ? 1 : -1;
    return a.id < b.id ? -1 : a.id > b.id ? 1 : 0;
  });

  return (
    <div className="min-h-screen bg-slate-950 relative overflow-hidden">
      <div aria-hidden className="pointer-events-none fixed inset-0" style={{ zIndex: 0 }}>
        <div className="absolute top-1/4 left-1/4 w-[600px] h-[600px] rounded-full bg-blue-600/10 blur-[120px]" />
      </div>

      <div className="relative z-10 max-w-4xl mx-auto px-4 sm:px-6 py-10">
        <Link
          to="/lists"
          className="inline-flex items-center gap-1.5 text-sm text-white/60 hover:text-white/90 transition-colors mb-5"
        >
          <ChevronLeft className="w-4 h-4" />
          All lists
        </Link>

        {/* Header */}
        <motion.div
          variants={fadeInUp}
          initial="hidden"
          animate="visible"
          className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-6 mb-6"
        >
          <div className="flex items-start justify-between gap-4 flex-wrap">
            <div className="flex items-center gap-3">
              <div className="bg-blue-500/15 border border-white/10 rounded-xl p-2.5">
                <Icon className="w-5 h-5 text-sky-200" />
              </div>
              <div>
                <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90">
                  {meta.kicker}
                </p>
                {!editing && (
                  <h1 className="text-2xl md:text-3xl font-extrabold text-white mt-1 leading-tight">
                    {data.name}
                  </h1>
                )}
              </div>
            </div>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={togglePublic}
                disabled={update.isPending}
                title={data.isPublic ? "Make private" : "Make public"}
                className="inline-flex items-center gap-1.5 rounded-full border border-white/15 bg-white/[0.04] px-3 py-1.5 text-xs text-white/80 hover:bg-white/10 transition-colors disabled:opacity-50"
              >
                {data.isPublic ? <Globe className="w-3.5 h-3.5" /> : <Lock className="w-3.5 h-3.5" />}
                {data.isPublic ? "Public" : "Private"}
              </button>

              {data.isPublic && data.shareSlug && (
                <button
                  type="button"
                  onClick={copyShareLink}
                  className="inline-flex items-center gap-1.5 rounded-full border border-white/15 bg-white/[0.04] px-3 py-1.5 text-xs text-white/80 hover:bg-white/10 transition-colors"
                >
                  <Share2 className="w-3.5 h-3.5" />
                  {copied ? "Copied!" : "Share"}
                </button>
              )}

              {!editing && (
                <button
                  type="button"
                  onClick={startEdit}
                  aria-label="Edit list"
                  className="inline-flex items-center justify-center w-8 h-8 rounded-full border border-white/15 bg-white/[0.04] text-white/70 hover:text-white hover:bg-white/10 transition-colors"
                >
                  <Pencil className="w-3.5 h-3.5" />
                </button>
              )}

              <button
                type="button"
                onClick={handleDelete}
                disabled={remove.isPending}
                aria-label="Delete list"
                className="inline-flex items-center justify-center w-8 h-8 rounded-full border border-white/15 bg-white/[0.04] text-white/60 hover:text-red-300 hover:border-red-400/40 transition-colors disabled:opacity-50"
              >
                <Trash2 className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>

          {/* Edit form or description */}
          {editing ? (
            <div className="mt-4 space-y-3">
              <input
                type="text"
                value={editName}
                onChange={(e) => setEditName(e.target.value)}
                maxLength={100}
                className="w-full rounded-xl border border-white/10 bg-white/[0.04] px-3 py-2 text-sm text-white focus:outline-none focus:border-white/25"
              />
              <textarea
                value={editDescription}
                onChange={(e) => setEditDescription(e.target.value)}
                maxLength={500}
                rows={2}
                placeholder="Description"
                className="w-full rounded-xl border border-white/10 bg-white/[0.04] px-3 py-2 text-sm text-white placeholder:text-white/45 focus:outline-none focus:border-white/25 resize-none"
              />
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={saveEdit}
                  disabled={update.isPending}
                  className="inline-flex items-center gap-1.5 rounded-full bg-blue-600 hover:bg-blue-500 px-4 py-1.5 text-sm font-semibold text-white transition-colors disabled:opacity-50"
                >
                  <Save className="w-3.5 h-3.5" />
                  {update.isPending ? "Saving…" : "Save"}
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setEditing(false);
                    setError(null);
                  }}
                  disabled={update.isPending}
                  className="rounded-full border border-white/15 px-4 py-1.5 text-sm text-white/80 hover:bg-white/10 transition-colors disabled:opacity-50"
                >
                  Cancel
                </button>
              </div>
            </div>
          ) : (
            data.description && (
              <p className="text-sm text-white/70 leading-relaxed mt-4">{data.description}</p>
            )
          )}

          {/* Progress strip */}
          {showProgress && (
            <div className="mt-5">
              <div className="flex items-center justify-between text-xs text-white/60 mb-1.5">
                <span>Progress</span>
                <span className="text-sky-200/90 font-medium">
                  {purchased} / {total} done
                </span>
              </div>
              <div className="h-1.5 rounded-full bg-white/[0.06] overflow-hidden">
                <div
                  className="h-full bg-gradient-to-r from-sky-400 to-blue-500 transition-all"
                  style={{ width: `${(purchased / total) * 100}%` }}
                />
              </div>
            </div>
          )}
        </motion.div>

        {error && (
          <p className="text-sm text-red-400 mb-3">{error}</p>
        )}

        {/* Items */}
        {sortedItems.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-center rounded-2xl border border-dashed border-white/10 bg-white/[0.02]">
            <Bookmark className="w-10 h-10 text-white/20 mb-3" />
            <p className="text-white/60 text-sm mb-1">This list is empty.</p>
            <p className="text-xs text-white/40">
              Browse products and tap the bookmark icon to add them here.
            </p>
            <Link
              to="/browse"
              className="mt-4 rounded-full bg-blue-600 hover:bg-blue-500 px-5 py-2 text-sm font-semibold text-white transition-colors"
            >
              Browse products
            </Link>
          </div>
        ) : (
          <div className="space-y-3">
            {sortedItems.map((item) => (
              <SavedListItemRow
                key={item.id}
                item={item}
                disabled={updateItem.isPending || removeItem.isPending}
                onUpdate={(changes) =>
                  updateItem.mutate(
                    { itemId: item.id, req: changes },
                    {
                      onError: (err) =>
                        setError(extractErrorMessage(err) ?? "Could not update item."),
                    }
                  )
                }
                onRemove={() =>
                  removeItem.mutate(item.id, {
                    onError: (err) =>
                      setError(extractErrorMessage(err) ?? "Could not remove item."),
                  })
                }
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function extractErrorMessage(err: unknown): string | null {
  if (typeof err === "object" && err !== null) {
    const maybe = err as { response?: { data?: { detail?: string; message?: string } } };
    return maybe.response?.data?.detail ?? maybe.response?.data?.message ?? null;
  }
  return null;
}
