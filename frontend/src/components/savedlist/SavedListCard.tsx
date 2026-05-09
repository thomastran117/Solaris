import { Link } from "react-router-dom";
import { motion, type Variants } from "framer-motion";
import { Globe, Lock } from "lucide-react";
import type { SavedListSummary } from "../../types/savedList";
import { LIST_TYPE_META, showsProgress } from "./listTypeMeta";

interface Props {
  list: SavedListSummary;
  variants?: Variants;
}

export default function SavedListCard({ list, variants }: Props) {
  const meta = LIST_TYPE_META[list.type];
  const Icon = meta.Icon;
  const showProgress = showsProgress(list.type) && list.itemCount > 0;
  const progressPct = showProgress
    ? Math.round((list.purchasedCount / list.itemCount) * 100)
    : 0;

  return (
    <Link to={`/lists/${list.id}`} className="group block">
      <motion.div
        variants={variants}
        whileHover={{ y: -4 }}
        className="h-full rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm hover:shadow-md transition-shadow p-5 flex flex-col"
      >
        <div className="flex items-start justify-between gap-3 mb-3">
          <div className="flex items-center gap-2.5">
            <div className="bg-blue-500/15 border border-white/10 rounded-xl p-2">
              <Icon className="w-4 h-4 text-sky-200" />
            </div>
            <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90">
              {meta.kicker}
            </p>
          </div>
          {list.isPublic ? (
            <span className="inline-flex items-center gap-1 text-[10px] uppercase tracking-wider text-green-300/90 border border-green-400/30 bg-green-500/10 rounded-full px-2 py-0.5">
              <Globe className="w-3 h-3" /> Public
            </span>
          ) : (
            <span className="inline-flex items-center gap-1 text-[10px] uppercase tracking-wider text-white/50 border border-white/15 bg-white/[0.04] rounded-full px-2 py-0.5">
              <Lock className="w-3 h-3" /> Private
            </span>
          )}
        </div>

        <h3 className="text-lg font-semibold text-white leading-tight mb-1 line-clamp-2">
          {list.name}
        </h3>
        {list.description && (
          <p className="text-sm text-white/60 line-clamp-2 mb-4">{list.description}</p>
        )}

        <div className="mt-auto pt-3 border-t border-white/5 space-y-2">
          <div className="flex items-center justify-between text-xs text-white/60">
            <span>
              {list.itemCount} item{list.itemCount === 1 ? "" : "s"}
            </span>
            {showProgress && (
              <span className="text-sky-200/90 font-medium">
                {list.purchasedCount} / {list.itemCount} done
              </span>
            )}
          </div>
          {showProgress && (
            <div className="h-1 rounded-full bg-white/[0.06] overflow-hidden">
              <div
                className="h-full bg-gradient-to-r from-sky-400 to-blue-500 transition-all"
                style={{ width: `${progressPct}%` }}
              />
            </div>
          )}
        </div>
      </motion.div>
    </Link>
  );
}
