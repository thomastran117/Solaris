"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { motion } from "framer-motion";
import { Bookmark, ShoppingBag, Check } from "lucide-react";
import { usePublicSavedList } from "../hooks/useSavedLists";
import {
  LIST_TYPE_META,
  showsProgress,
} from "../components/savedlist/listTypeMeta";
import { useAnims } from "../hooks/useAnims";
import { Progress } from "../components/ui/Progress";

export default function PublicSavedListPage() {
  const { slug } = useParams<{ slug: string }>();
  const { fadeInUp, stagger } = useAnims();
  const list = usePublicSavedList(slug);

  if (list.isLoading) {
    return (
      <div className="bg-background px-4 py-10">
        <div className="max-w-4xl mx-auto space-y-4">
          <div className="h-8 w-1/3 rounded-lg bg-surface animate-pulse" />
          <div className="h-24 rounded-xl border border-border bg-surface animate-pulse" />
        </div>
      </div>
    );
  }

  if (list.isError || !list.data) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center">
        <div className="text-center">
          <Bookmark className="w-10 h-10 text-muted-foreground mx-auto mb-3" />
          <p className="text-muted-foreground mb-2">
            This list isn't available.
          </p>
          <p className="text-sm text-muted-foreground">
            It may have been removed or made private.
          </p>
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

  return (
    <div className="bg-background relative overflow-hidden">
      <div className="relative z-10 max-w-4xl mx-auto px-4 sm:px-6 py-10">
        <motion.div
          variants={fadeInUp}
          initial="hidden"
          animate="visible"
          className="rounded-xl border border-border bg-surface p-6 mb-6"
        >
          <div className="flex items-start gap-3 mb-3">
            <div className="bg-primary/10 border border-border rounded-xl p-2.5">
              <Icon className="w-5 h-5 text-link" />
            </div>
            <div className="flex-1">
              <p className="text-xs font-semibold text-link">
                {meta.kicker} · Shared by {data.ownerDisplayName}
              </p>
              <h1 className="text-2xl md:text-3xl font-semibold text-foreground mt-1 leading-tight">
                {data.name}
              </h1>
            </div>
          </div>

          {data.description && (
            <p className="text-sm text-muted-foreground leading-relaxed">
              {data.description}
            </p>
          )}

          {showProgress && (
            <div className="mt-5">
              <div className="flex items-center justify-between text-xs text-muted-foreground mb-1.5">
                <span>Progress</span>
                <span className="text-link font-medium">
                  {purchased} / {total} done
                </span>
              </div>
              <Progress
                value={(purchased / total) * 100}
                label="Items purchased"
                valueText={`${purchased} of ${total} purchased`}
                className="h-1.5"
              />
            </div>
          )}
        </motion.div>

        {data.items.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-center rounded-xl border border-dashed border-border bg-surface">
            <Bookmark className="w-10 h-10 text-muted-foreground mb-3" />
            <p className="text-muted-foreground text-sm">
              This list has no items yet.
            </p>
          </div>
        ) : (
          <motion.div
            variants={stagger}
            initial="hidden"
            animate="visible"
            className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4"
          >
            {data.items.map((item) => (
              <motion.div key={item.id} variants={fadeInUp}>
                <Link
                  href={`/products/${item.productId}`}
                  className={`group block rounded-xl border border-border bg-surface shadow-sm hover:shadow-md transition-shadow overflow-hidden ${
                    item.purchased ? "opacity-60" : ""
                  }`}
                >
                  <div className="aspect-square bg-surface flex items-center justify-center overflow-hidden relative">
                    {item.productThumbnailUrl ? (
                      <img
                        src={item.productThumbnailUrl}
                        alt={item.productName}
                        className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
                      />
                    ) : (
                      <ShoppingBag className="w-10 h-10 text-muted-foreground" />
                    )}
                    {item.purchased && (
                      <span className="absolute top-2 left-2 inline-flex items-center gap-1 text-[10px] text-success bg-success-subtle border border-success/40 rounded-full px-2 py-0.5">
                        <Check className="w-3 h-3" />
                        Done
                      </span>
                    )}
                  </div>
                  <div className="p-4">
                    <h3
                      className={`text-sm font-semibold text-foreground leading-snug line-clamp-2 mb-1 ${
                        item.purchased ? "line-through" : ""
                      }`}
                    >
                      {item.productName}
                    </h3>
                    <div className="flex items-center justify-between text-xs text-muted-foreground">
                      <span>Qty {item.quantity}</span>
                      {item.note && (
                        <span className="truncate ml-2 italic">
                          "{item.note}"
                        </span>
                      )}
                    </div>
                  </div>
                </Link>
              </motion.div>
            ))}
          </motion.div>
        )}
      </div>
    </div>
  );
}
