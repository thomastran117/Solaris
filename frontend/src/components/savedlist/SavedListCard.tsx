import Link from "next/link";
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
    <Link href={`/lists/${list.id}`} className="group block">
      <motion.div
        variants={variants}
        whileHover={{ y: -4 }}
        className="h-full rounded-xl border border-border bg-surface shadow-sm hover:shadow-md transition-shadow p-5 flex flex-col"
      >
        <div className="flex items-start justify-between gap-3 mb-3">
          <div className="flex items-center gap-2.5">
            <div className="bg-primary/10 border border-border rounded-xl p-2">
              <Icon className="w-4 h-4 text-link" />
            </div>
            <p className="text-xs font-semibold text-link">{meta.kicker}</p>
          </div>
          {list.isPublic ? (
            <span className="inline-flex items-center gap-1 text-[10px] text-success border border-success/40 bg-success-subtle rounded-full px-2 py-0.5">
              <Globe className="w-3 h-3" /> Public
            </span>
          ) : (
            <span className="inline-flex items-center gap-1 text-[10px] text-muted-foreground border border-border bg-surface rounded-full px-2 py-0.5">
              <Lock className="w-3 h-3" /> Private
            </span>
          )}
        </div>

        <h3 className="text-lg font-semibold text-foreground leading-tight mb-1 line-clamp-2">
          {list.name}
        </h3>
        {list.description && (
          <p className="text-sm text-muted-foreground line-clamp-2 mb-4">
            {list.description}
          </p>
        )}

        <div className="mt-auto pt-3 border-t border-border space-y-2">
          <div className="flex items-center justify-between text-xs text-muted-foreground">
            <span>
              {list.itemCount} item{list.itemCount === 1 ? "" : "s"}
            </span>
            {showProgress && (
              <span className="text-link font-medium">
                {list.purchasedCount} / {list.itemCount} done
              </span>
            )}
          </div>
          {showProgress && (
            <div className="h-1 rounded-full bg-surface overflow-hidden">
              <div
                className="h-full transition-all"
                style={{ width: `${progressPct}%` }}
              />
            </div>
          )}
        </div>
      </motion.div>
    </Link>
  );
}
