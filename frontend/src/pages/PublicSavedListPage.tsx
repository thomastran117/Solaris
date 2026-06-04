import { Link, useParams } from "react-router-dom";
import { motion } from "framer-motion";
import { Bookmark, ShoppingBag, Check } from "lucide-react";
import { usePublicSavedList } from "../hooks/useSavedLists";
import { LIST_TYPE_META, showsProgress } from "../components/savedlist/listTypeMeta";
import { useAnims } from "../hooks/useAnims";


export default function PublicSavedListPage() {
  const { slug } = useParams<{ slug: string }>();
  const { fadeInUp, stagger } = useAnims();
  const list = usePublicSavedList(slug);

  if (list.isLoading) {
    return (
      <div className="min-h-screen bg-slate-950 px-4 py-10">
        <div className="max-w-4xl mx-auto space-y-4">
          <div className="h-8 w-1/3 rounded-lg bg-white/[0.06] animate-pulse" />
          <div className="h-24 rounded-2xl border border-white/10 bg-white/[0.04] animate-pulse" />
        </div>
      </div>
    );
  }

  if (list.isError || !list.data) {
    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center">
        <div className="text-center">
          <Bookmark className="w-10 h-10 text-white/20 mx-auto mb-3" />
          <p className="text-white/70 mb-2">This list isn't available.</p>
          <p className="text-sm text-white/40">
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
    <div className="min-h-screen bg-slate-950 relative overflow-hidden">
      <div aria-hidden className="pointer-events-none fixed inset-0" style={{ zIndex: 0 }}>
        <div className="absolute top-1/4 left-1/4 w-[600px] h-[600px] rounded-full bg-blue-600/10 blur-[120px]" />
      </div>

      <div className="relative z-10 max-w-4xl mx-auto px-4 sm:px-6 py-10">
        <motion.div
          variants={fadeInUp}
          initial="hidden"
          animate="visible"
          className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-6 mb-6"
        >
          <div className="flex items-start gap-3 mb-3">
            <div className="bg-blue-500/15 border border-white/10 rounded-xl p-2.5">
              <Icon className="w-5 h-5 text-sky-200" />
            </div>
            <div className="flex-1">
              <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90">
                {meta.kicker} · Shared by {data.ownerDisplayName}
              </p>
              <h1 className="text-2xl md:text-3xl font-extrabold text-white mt-1 leading-tight">
                {data.name}
              </h1>
            </div>
          </div>

          {data.description && (
            <p className="text-sm text-white/70 leading-relaxed">{data.description}</p>
          )}

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

        {data.items.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-center rounded-2xl border border-dashed border-white/10 bg-white/[0.02]">
            <Bookmark className="w-10 h-10 text-white/20 mb-3" />
            <p className="text-white/60 text-sm">This list has no items yet.</p>
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
                  to={`/products/${item.productId}`}
                  className={`group block rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm hover:shadow-md transition-shadow overflow-hidden ${
                    item.purchased ? "opacity-60" : ""
                  }`}
                >
                  <div className="aspect-square bg-white/[0.04] flex items-center justify-center overflow-hidden relative">
                    {item.productThumbnailUrl ? (
                      <img
                        src={item.productThumbnailUrl}
                        alt={item.productName}
                        className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
                      />
                    ) : (
                      <ShoppingBag className="w-10 h-10 text-white/20" />
                    )}
                    {item.purchased && (
                      <span className="absolute top-2 left-2 inline-flex items-center gap-1 text-[10px] uppercase tracking-wider text-green-200 bg-green-500/30 border border-green-400/40 rounded-full px-2 py-0.5 backdrop-blur">
                        <Check className="w-3 h-3" />
                        Done
                      </span>
                    )}
                  </div>
                  <div className="p-4">
                    <h3
                      className={`text-sm font-semibold text-white leading-snug line-clamp-2 mb-1 ${
                        item.purchased ? "line-through" : ""
                      }`}
                    >
                      {item.productName}
                    </h3>
                    <div className="flex items-center justify-between text-xs text-white/55">
                      <span>Qty {item.quantity}</span>
                      {item.note && <span className="truncate ml-2 italic">"{item.note}"</span>}
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
