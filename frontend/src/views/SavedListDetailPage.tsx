"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter, useParams } from "next/navigation";
import { motion } from "framer-motion";
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
import {
  LIST_TYPE_META,
  showsProgress,
} from "../components/savedlist/listTypeMeta";
import { useAnims } from "../hooks/useAnims";

export default function SavedListDetailPage() {
  const router = useRouter();
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
    if (!window.confirm(`Delete "${list.data.name}"? This cannot be undone.`))
      return;
    try {
      await remove.mutateAsync(list.data.id);
      router.push("/lists");
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
      <div className="px-4 py-10">
        <div className="max-w-4xl mx-auto space-y-4">
          <div className="h-8 w-1/3 rounded-lg bg-surface animate-pulse" />
          <div className="h-24 rounded-xl border border-border bg-surface animate-pulse" />
          <div className="h-24 rounded-xl border border-border bg-surface animate-pulse" />
        </div>
      </div>
    );
  }

  if (list.isError || !list.data) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center">
        <div className="text-center">
          <p className="text-muted-foreground mb-4">List not found.</p>
          <Link
            href="/lists"
            className="text-link hover:text-link transition-colors text-sm"
          >
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
    <div>
      <div className="max-w-4xl">
        <Link
          href="/lists"
          className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors mb-5"
        >
          <ChevronLeft className="w-4 h-4" />
          All lists
        </Link>

        {/* Header */}
        <motion.div
          variants={fadeInUp}
          initial="hidden"
          animate="visible"
          className="rounded-xl border border-border bg-surface p-6 mb-6"
        >
          <div className="flex items-start justify-between gap-4 flex-wrap">
            <div className="flex items-center gap-3">
              <div className="bg-primary/10 border border-border rounded-xl p-2.5">
                <Icon className="w-5 h-5 text-link" />
              </div>
              <div>
                <p className="text-xs font-semibold text-link">{meta.kicker}</p>
                {!editing && (
                  <h1 className="mt-1 text-2xl font-semibold tracking-tight text-foreground">
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
                className="inline-flex items-center gap-1.5 rounded-full border border-border bg-surface px-3 py-1.5 text-xs text-foreground hover:bg-muted transition-colors disabled:opacity-50"
              >
                {data.isPublic ? (
                  <Globe className="w-3.5 h-3.5" />
                ) : (
                  <Lock className="w-3.5 h-3.5" />
                )}
                {data.isPublic ? "Public" : "Private"}
              </button>

              {data.isPublic && data.shareSlug && (
                <button
                  type="button"
                  onClick={copyShareLink}
                  className="inline-flex items-center gap-1.5 rounded-full border border-border bg-surface px-3 py-1.5 text-xs text-foreground hover:bg-muted transition-colors"
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
                  className="inline-flex items-center justify-center w-8 h-8 rounded-full border border-border bg-surface text-muted-foreground hover:text-foreground hover:bg-muted transition-colors"
                >
                  <Pencil className="w-3.5 h-3.5" />
                </button>
              )}

              <button
                type="button"
                onClick={handleDelete}
                disabled={remove.isPending}
                aria-label="Delete list"
                className="inline-flex items-center justify-center w-8 h-8 rounded-full border border-border bg-surface text-muted-foreground hover:text-danger hover:border-danger/40 transition-colors disabled:opacity-50"
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
                className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-foreground focus:outline-none focus:border-ring"
              />
              <textarea
                value={editDescription}
                onChange={(e) => setEditDescription(e.target.value)}
                maxLength={500}
                rows={2}
                placeholder="Description"
                className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-ring resize-none"
              />
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={saveEdit}
                  disabled={update.isPending}
                  className="inline-flex items-center gap-1.5 rounded-full bg-primary hover:bg-primary-hover px-4 py-1.5 text-sm font-semibold text-primary-foreground transition-colors disabled:opacity-50"
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
                  className="rounded-full border border-border px-4 py-1.5 text-sm text-foreground hover:bg-muted transition-colors disabled:opacity-50"
                >
                  Cancel
                </button>
              </div>
            </div>
          ) : (
            data.description && (
              <p className="text-sm text-muted-foreground leading-relaxed mt-4">
                {data.description}
              </p>
            )
          )}

          {/* Progress strip */}
          {showProgress && (
            <div className="mt-5">
              <div className="flex items-center justify-between text-xs text-muted-foreground mb-1.5">
                <span>Progress</span>
                <span className="text-link font-medium">
                  {purchased} / {total} done
                </span>
              </div>
              <div className="h-1.5 rounded-full bg-surface overflow-hidden">
                <div
                  className="h-full transition-all"
                  style={{ width: `${(purchased / total) * 100}%` }}
                />
              </div>
            </div>
          )}
        </motion.div>

        {error && <p className="text-sm text-danger mb-3">{error}</p>}

        {/* Items */}
        {sortedItems.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-center rounded-xl border border-dashed border-border bg-surface">
            <Bookmark className="w-10 h-10 text-muted-foreground mb-3" />
            <p className="text-muted-foreground text-sm mb-1">
              This list is empty.
            </p>
            <p className="text-xs text-muted-foreground">
              Browse products and tap the bookmark icon to add them here.
            </p>
            <Link
              href="/browse"
              className="mt-4 rounded-full bg-primary hover:bg-primary-hover px-5 py-2 text-sm font-semibold text-primary-foreground transition-colors"
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
                        setError(
                          extractErrorMessage(err) ?? "Could not update item."
                        ),
                    }
                  )
                }
                onRemove={() =>
                  removeItem.mutate(item.id, {
                    onError: (err) =>
                      setError(
                        extractErrorMessage(err) ?? "Could not remove item."
                      ),
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
    const maybe = err as {
      response?: { data?: { detail?: string; message?: string } };
    };
    return (
      maybe.response?.data?.detail ?? maybe.response?.data?.message ?? null
    );
  }
  return null;
}
