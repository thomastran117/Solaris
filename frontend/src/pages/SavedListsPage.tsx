import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useSelector } from "react-redux";
import { motion } from "framer-motion";
import { Plus, Bookmark } from "lucide-react";
import type { RootState } from "../stores";
import { useSavedLists } from "../hooks/useSavedLists";
import SavedListCard from "../components/savedlist/SavedListCard";
import CreateListModal from "../components/savedlist/CreateListModal";
import { ALL_TYPES, LIST_TYPE_META } from "../components/savedlist/listTypeMeta";
import type { SavedListType } from "../types/savedList";
import { useAnims } from "../hooks/useAnims";


export default function SavedListsPage() {
  const navigate = useNavigate();
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
      <div className="min-h-screen bg-slate-950 flex items-center justify-center">
        <div className="text-center">
          <p className="text-white/70 mb-4">Sign in to manage your saved lists.</p>
          <button
            type="button"
            onClick={() => navigate("/login")}
            className="rounded-full bg-blue-600 hover:bg-blue-500 px-5 py-2 text-sm font-semibold text-white transition-colors"
          >
            Sign in
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-950 relative overflow-hidden">
      <div aria-hidden className="pointer-events-none fixed inset-0" style={{ zIndex: 0 }}>
        <div className="absolute top-1/4 left-1/4 w-[600px] h-[600px] rounded-full bg-blue-600/10 blur-[120px]" />
        <div className="absolute bottom-1/3 right-1/4 w-[400px] h-[400px] rounded-full bg-sky-400/8 blur-[100px]" />
      </div>

      <div className="relative z-10 max-w-7xl mx-auto px-4 sm:px-6 py-10">
        {/* Header */}
        <motion.div variants={fadeInUp} initial="hidden" animate="visible" className="mb-8">
          <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90 mb-2">
            Your collections
          </p>
          <div className="flex flex-wrap items-end justify-between gap-4">
            <h1 className="text-3xl md:text-4xl font-extrabold text-white">
              Saved Lists
            </h1>
            <button
              type="button"
              onClick={() => setCreateOpen(true)}
              className="inline-flex items-center gap-1.5 rounded-full bg-blue-600 hover:bg-blue-500 px-5 py-2 text-sm font-semibold text-white transition-colors"
            >
              <Plus className="w-4 h-4" />
              New list
            </button>
          </div>
          <p className="text-sm text-white/60 mt-2 max-w-xl">
            Wishlists, gift lists, shopping plans, and project builds — all in one place.
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
                className="h-44 rounded-2xl border border-white/10 bg-white/[0.04] animate-pulse"
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
            <Bookmark className="w-12 h-12 text-white/20 mb-4" />
            <h3 className="text-lg font-semibold text-white mb-2">
              {filter ? `No ${LIST_TYPE_META[filter].label.toLowerCase()}s yet` : "No lists yet"}
            </h3>
            <p className="text-sm text-white/50 max-w-xs mb-5">
              Create your first list to start saving products you love.
            </p>
            <button
              type="button"
              onClick={() => setCreateOpen(true)}
              className="inline-flex items-center gap-1.5 rounded-full bg-blue-600 hover:bg-blue-500 px-5 py-2 text-sm font-semibold text-white transition-colors"
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
          ? "border-blue-400/60 bg-blue-500/15 text-white"
          : "border-white/10 bg-white/[0.04] text-white/70 hover:bg-white/[0.06]"
      }`}
    >
      {Icon && <Icon className="w-3.5 h-3.5 text-sky-200" />}
      {label}
      <span className={`text-xs ${active ? "text-white/80" : "text-white/45"}`}>
        ({count})
      </span>
    </button>
  );
}
