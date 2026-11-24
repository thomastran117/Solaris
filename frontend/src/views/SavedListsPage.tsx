"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useSelector } from "react-redux";
import { motion } from "framer-motion";
import { Plus, Bookmark } from "lucide-react";
import type { RootState } from "../stores";
import { useSavedLists } from "../hooks/useSavedLists";
import SavedListCard from "../components/savedlist/SavedListCard";
import CreateListModal from "../components/savedlist/CreateListModal";
import {
  ALL_TYPES,
  LIST_TYPE_META,
} from "../components/savedlist/listTypeMeta";
import type { SavedListType } from "../types/savedList";
import { useAnims } from "../hooks/useAnims";

export default function SavedListsPage() {
  const router = useRouter();
  const accessToken = useSelector((s: RootState) => s.auth.accessToken);
  const { fadeInUp, stagger } = useAnims();

  const [filter, setFilter] = useState<SavedListType | null>(null);
  const [createOpen, setCreateOpen] = useState(false);

  // Always fetch all so the type-filter pills can show counts; client-side filter for instant UX.
  const lists = useSavedLists(undefined, !!accessToken);

  const filtered = useMemo(() => {
    if (!lists.data) return [];
    if (filter == null) return lists.data;
    return lists.data.filter((l) => l.type === filter);
  }, [lists.data, filter]);

  const counts = useMemo(() => {
    const map: Record<SavedListType, number> = {
      WISHLIST: 0,
      GIFT: 0,
      SHOPPING: 0,
      PROJECT: 0,
    };
    (lists.data ?? []).forEach((l) => (map[l.type] += 1));
    return map;
  }, [lists.data]);

  if (!accessToken) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center">
        <div className="text-center">
          <p className="text-muted-foreground mb-4">
            Sign in to manage your saved lists.
          </p>
          <button
            type="button"
            onClick={() => router.push("/login")}
            className="rounded-full bg-primary hover:bg-primary-hover px-5 py-2 text-sm font-semibold text-primary-foreground transition-colors"
          >
            Sign in
          </button>
        </div>
      </div>
    );
  }

  return (
    <div>
      <div>
        {/* Header */}
        <motion.div
          variants={fadeInUp}
          initial="hidden"
          animate="visible"
          className="mb-8"
        >
          <p className="text-xs font-semibold text-link mb-2">
            Your collections
          </p>
          <div className="flex flex-wrap items-end justify-between gap-4">
            <h1 className="text-2xl font-semibold tracking-tight text-foreground">
              Saved Lists
            </h1>
            <button
              type="button"
              onClick={() => setCreateOpen(true)}
              className="inline-flex items-center gap-1.5 rounded-full bg-primary hover:bg-primary-hover px-5 py-2 text-sm font-semibold text-primary-foreground transition-colors"
            >
              <Plus className="w-4 h-4" />
              New list
            </button>
          </div>
          <p className="text-sm text-muted-foreground mt-2 max-w-xl">
            Wishlists, gift lists, shopping plans, and project builds — all in
            one place.
          </p>
        </motion.div>

        {/* Type filter pills */}
        <div className="flex flex-wrap gap-2 mb-6">
          <FilterPill
            active={filter === null}
            label="All"
            count={lists.data?.length ?? 0}
            onClick={() => setFilter(null)}
          />
          {ALL_TYPES.map((t) => {
            const meta = LIST_TYPE_META[t];
            const Icon = meta.Icon;
            return (
              <FilterPill
                key={t}
                active={filter === t}
                label={meta.label}
                count={counts[t]}
                onClick={() => setFilter(t)}
                Icon={Icon}
              />
            );
          })}
        </div>

        {/* Loading skeleton */}
        {lists.isLoading && (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {Array.from({ length: 6 }).map((_, i) => (
              <div
                key={i}
                className="h-44 rounded-xl border border-border bg-surface animate-pulse"
              />
            ))}
          </div>
        )}

        {/* Empty state */}
        {!lists.isLoading && filtered.length === 0 && (
          <motion.div
            variants={fadeInUp}
            initial="hidden"
            animate="visible"
            className="flex flex-col items-center justify-center py-20 text-center"
          >
            <Bookmark className="w-12 h-12 text-muted-foreground mb-4" />
            <h3 className="text-lg font-semibold text-foreground mb-2">
              {filter
                ? `No ${LIST_TYPE_META[filter].label.toLowerCase()}s yet`
                : "No lists yet"}
            </h3>
            <p className="text-sm text-muted-foreground max-w-xs mb-5">
              Create your first list to start saving products you love.
            </p>
            <button
              type="button"
              onClick={() => setCreateOpen(true)}
              className="inline-flex items-center gap-1.5 rounded-full bg-primary hover:bg-primary-hover px-5 py-2 text-sm font-semibold text-primary-foreground transition-colors"
            >
              <Plus className="w-4 h-4" />
              Create a list
            </button>
          </motion.div>
        )}

        {/* Results grid */}
        {!lists.isLoading && filtered.length > 0 && (
          <motion.div
            variants={stagger}
            initial="hidden"
            animate="visible"
            className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4"
          >
            {filtered.map((l) => (
              <SavedListCard key={l.id} list={l} variants={fadeInUp} />
            ))}
          </motion.div>
        )}
      </div>

      <CreateListModal open={createOpen} onClose={() => setCreateOpen(false)} />
    </div>
  );
}

function FilterPill({
  active,
  label,
  count,
  onClick,
  Icon,
}: {
  active: boolean;
  label: string;
  count: number;
  onClick: () => void;
  Icon?: React.ComponentType<{ className?: string }>;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`inline-flex items-center gap-1.5 rounded-full border px-3.5 py-1.5 text-sm transition-colors ${
        active
          ? "border-primary/40 bg-primary/10 text-foreground"
          : "border-border bg-surface text-muted-foreground hover:bg-muted"
      }`}
    >
      {Icon && <Icon className="w-3.5 h-3.5 text-link" />}
      {label}
      <span
        className={`text-xs ${active ? "text-foreground" : "text-muted-foreground"}`}
      >
        ({count})
      </span>
    </button>
  );
}
