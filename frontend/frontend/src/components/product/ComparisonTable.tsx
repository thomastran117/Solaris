import { X, Star } from "lucide-react";
import { motion, useReducedMotion } from "framer-motion";
import type { Variants } from "framer-motion";
import type { CompareProduct, CompareBundle } from "../../types/comparison";

// ---- Animation helpers ----
const useAnims = () => {
  const prefersReducedMotion = useReducedMotion();

  const fadeInUp: Variants = prefersReducedMotion
    ? { hidden: { opacity: 0 }, visible: { opacity: 1, transition: { duration: 0.35 } } }
    : { hidden: { opacity: 0, y: 18 }, visible: { opacity: 1, y: 0, transition: { duration: 0.55 } } };

  const stagger: Variants = prefersReducedMotion
    ? { hidden: {}, visible: { transition: { staggerChildren: 0.05 } } }
    : { hidden: {}, visible: { transition: { staggerChildren: 0.1 } } };

  return { fadeInUp, stagger };
};

// ---- RatingStars ----
function RatingStars({ rating }: { rating: number }) {
  return (
    <span className="flex items-center gap-0.5">
      {Array.from({ length: 5 }, (_, i) => (
        <Star
          key={i}
          size={13}
          className={
            i < Math.round(rating)
              ? "text-blue-400 fill-blue-400"
              : "text-white/25"
          }
        />
      ))}
    </span>
  );
}

// ---- Best badge ----
function BestBadge() {
  return (
    <span className="ml-1.5 inline-block text-[10px] font-semibold uppercase tracking-wide bg-green-500/20 text-green-400 border border-green-500/30 rounded-full px-1.5 py-0.5">
      Best
    </span>
  );
}

// ---- Shared cell style ----
const cellBase =
  "px-4 py-3.5 border-b border-white/[0.06] text-sm text-white/85 min-w-[160px]";
const labelCell =
  "px-4 py-3.5 border-b border-white/[0.06] text-sm font-medium text-white/55 whitespace-nowrap sticky left-0 bg-slate-950/80 backdrop-blur z-10 min-w-[130px]";
const headerCell =
  "px-4 py-4 border-b border-white/10 text-center min-w-[160px]";

// ======================================================================
// Products comparison table
// ======================================================================

function findBestIndex(items: (number | null)[], lowerIsBetter = false): number {
  const valid = items.filter((v) => v != null) as number[];
  if (valid.length === 0) return -1;
  const best = lowerIsBetter ? Math.min(...valid) : Math.max(...valid);
  return items.indexOf(best);
}

interface ProductTableProps {
  products: CompareProduct[];
  onRemove: (id: number) => void;
}

export function ProductComparisonTable({ products, onRemove }: ProductTableProps) {
  const { fadeInUp, stagger } = useAnims();

  // Collect unique attribute names (sorted by frequency desc, then alpha)
  const attrFreq = new Map<string, number>();
  for (const p of products) {
    for (const a of p.attributes) {
      attrFreq.set(a.name, (attrFreq.get(a.name) ?? 0) + 1);
    }
  }
  const attrKeys = [...attrFreq.keys()].sort((a, b) => {
    const diff = (attrFreq.get(b) ?? 0) - (attrFreq.get(a) ?? 0);
    return diff !== 0 ? diff : a.localeCompare(b);
  });

  const priceIdx = findBestIndex(products.map((p) => p.price), true);
  const ratingIdx = findBestIndex(products.map((p) => p.avgRating));
  const stockIdx = findBestIndex(products.map((p) => p.stock));

  const colCount = products.length + 1; // +1 for label column

  return (
    <motion.div
      initial="hidden"
      whileInView="visible"
      viewport={{ once: true, amount: 0.1 }}
      variants={stagger}
      className="overflow-x-auto rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm"
    >
      <table className="w-full border-collapse" style={{ minWidth: `${130 + products.length * 160}px` }}>
        {/* Header row */}
        <thead>
          <tr>
            <th className={`${labelCell} font-semibold text-white/40 text-xs uppercase tracking-widest`}>
              Compare
            </th>
            {products.map((p) => (
              <th key={p.id} className={headerCell}>
                <motion.div variants={fadeInUp} className="flex flex-col items-center gap-2">
                  <div className="relative">
                    {p.thumbnailUrl ? (
                      <img
                        src={p.thumbnailUrl}
                        alt={p.name}
                        className="w-16 h-16 rounded-xl object-cover border border-white/10"
                      />
                    ) : (
                      <div className="w-16 h-16 rounded-xl bg-white/[0.06] border border-white/10 flex items-center justify-center text-white/25 text-xs">
                        No img
                      </div>
                    )}
                    <button
                      onClick={() => onRemove(p.id)}
                      className="absolute -top-2 -right-2 w-5 h-5 rounded-full bg-white/10 border border-white/20 flex items-center justify-center hover:bg-red-500/30 hover:border-red-400/40 transition-colors"
                      title="Remove from comparison"
                    >
                      <X size={10} className="text-white/70" />
                    </button>
                  </div>
                  <span className="text-xs font-semibold text-white text-center leading-tight max-w-[130px] line-clamp-2">
                    {p.name}
                  </span>
                </motion.div>
              </th>
            ))}
          </tr>
        </thead>

        {/* Body rows */}
        <tbody>
          {/* Price */}
          <tr className="odd:bg-white/[0.02]">
            <td className={labelCell}>Price</td>
            {products.map((p, i) => (
              <td key={p.id} className={`${cellBase} text-center`}>
                <motion.div variants={fadeInUp}>
                  <span className={`font-extrabold ${i === priceIdx ? "text-green-400" : "text-white"}`}>
                    {p.currency} {p.price.toFixed(2)}
                  </span>
                  {i === priceIdx && <BestBadge />}
                  {p.compareAtPrice != null && (
                    <div className="text-xs text-white/40 line-through mt-0.5">
                      {p.currency} {p.compareAtPrice.toFixed(2)}
                    </div>
                  )}
                </motion.div>
              </td>
            ))}
          </tr>

          {/* Rating */}
          <tr className="odd:bg-white/[0.02]">
            <td className={labelCell}>Rating</td>
            {products.map((p, i) => (
              <td key={p.id} className={`${cellBase} text-center`}>
                <motion.div variants={fadeInUp} className="flex flex-col items-center gap-1">
                  {p.avgRating != null ? (
                    <>
                      <div className="flex items-center gap-1">
                        <RatingStars rating={p.avgRating} />
                        {i === ratingIdx && <BestBadge />}
                      </div>
                      <span className="text-xs text-white/50">
                        {p.avgRating.toFixed(1)} ({p.reviewCount} review{p.reviewCount !== 1 ? "s" : ""})
                      </span>
                    </>
                  ) : (
                    <span className="text-white/30 text-xs">No reviews</span>
                  )}
                </motion.div>
              </td>
            ))}
          </tr>

          {/* Stock */}
          <tr className="odd:bg-white/[0.02]">
            <td className={labelCell}>Stock</td>
            {products.map((p, i) => (
              <td key={p.id} className={`${cellBase} text-center`}>
                <motion.div variants={fadeInUp}>
                  {p.stock != null ? (
                    <>
                      <span className={`font-semibold ${i === stockIdx ? "text-green-400" : "text-white"}`}>
                        {p.stock} units
                      </span>
                      {i === stockIdx && <BestBadge />}
                      {p.lowStockThreshold != null && p.stock <= p.lowStockThreshold && (
                        <div className="text-xs text-yellow-400 mt-0.5">Low stock</div>
                      )}
                    </>
                  ) : p.purchasable ? (
                    <span className="text-white/50 text-xs">Available</span>
                  ) : (
                    <span className="text-red-400 text-xs">Unavailable</span>
                  )}
                </motion.div>
              </td>
            ))}
          </tr>

          {/* Category */}
          <tr className="odd:bg-white/[0.02]">
            <td className={labelCell}>Category</td>
            {products.map((p) => (
              <td key={p.id} className={`${cellBase} text-center`}>
                <motion.div variants={fadeInUp}>
                  {p.category ?? <span className="text-white/30">—</span>}
                </motion.div>
              </td>
            ))}
          </tr>

          {/* Brand */}
          <tr className="odd:bg-white/[0.02]">
            <td className={labelCell}>Brand</td>
            {products.map((p) => (
              <td key={p.id} className={`${cellBase} text-center`}>
                <motion.div variants={fadeInUp}>
                  {p.brand ?? <span className="text-white/30">—</span>}
                </motion.div>
              </td>
            ))}
          </tr>

          {/* SKU */}
          <tr className="odd:bg-white/[0.02]">
            <td className={labelCell}>SKU</td>
            {products.map((p) => (
              <td key={p.id} className={`${cellBase} text-center`}>
                <motion.div variants={fadeInUp}>
                  {p.sku ? (
                    <span className="font-mono text-xs text-white/70">{p.sku}</span>
                  ) : (
                    <span className="text-white/30">—</span>
                  )}
                </motion.div>
              </td>
            ))}
          </tr>

          {/* Weight */}
          <tr className="odd:bg-white/[0.02]">
            <td className={labelCell}>Weight</td>
            {products.map((p) => (
              <td key={p.id} className={`${cellBase} text-center`}>
                <motion.div variants={fadeInUp}>
                  {p.weight != null ? (
                    <span>{p.weight} {p.weightUnit ?? ""}</span>
                  ) : (
                    <span className="text-white/30">—</span>
                  )}
                </motion.div>
              </td>
            ))}
          </tr>

          {/* Variants */}
          {products.some((p) => p.options.length > 0) && (
            <tr className="odd:bg-white/[0.02]">
              <td className={labelCell}>Variants</td>
              {products.map((p) => (
                <td key={p.id} className={`${cellBase} text-center`}>
                  <motion.div variants={fadeInUp}>
                    {p.options.length > 0 ? (
                      <div className="flex flex-wrap gap-1 justify-center">
                        {p.options.map((o) => (
                          <span
                            key={o.id}
                            className="text-xs px-2 py-0.5 rounded-full bg-white/[0.06] border border-white/10 text-white/70"
                          >
                            {o.name}
                          </span>
                        ))}
                      </div>
                    ) : (
                      <span className="text-white/30">—</span>
                    )}
                  </motion.div>
                </td>
              ))}
            </tr>
          )}

          {/* Dynamic attribute rows */}
          {attrKeys.map((attrName) => (
            <tr key={attrName} className="odd:bg-white/[0.02]">
              <td className={labelCell}>{attrName}</td>
              {products.map((p) => {
                const attr = p.attributes.find((a) => a.name === attrName);
                return (
                  <td key={p.id} className={`${cellBase} text-center`}>
                    <motion.div variants={fadeInUp}>
                      {attr ? (
                        <span>{attr.value}</span>
                      ) : (
                        <span className="text-white/30">—</span>
                      )}
                    </motion.div>
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>

      {/* phantom cells to prevent layout shift with fewer columns */}
      {products.length < 4 && (
        <div className="hidden" aria-hidden="true" style={{ "--col-count": colCount } as React.CSSProperties} />
      )}
    </motion.div>
  );
}

// ======================================================================
// Bundles comparison table
// ======================================================================

interface BundleTableProps {
  bundles: CompareBundle[];
  onRemove: (id: number) => void;
}

export function BundleComparisonTable({ bundles, onRemove }: BundleTableProps) {
  const { fadeInUp, stagger } = useAnims();

  const priceIdx = findBestIndex(bundles.map((b) => b.price), true);

  return (
    <motion.div
      initial="hidden"
      whileInView="visible"
      viewport={{ once: true, amount: 0.1 }}
      variants={stagger}
      className="overflow-x-auto rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm"
    >
      <table className="w-full border-collapse" style={{ minWidth: `${130 + bundles.length * 160}px` }}>
        <thead>
          <tr>
            <th className={`${labelCell} font-semibold text-white/40 text-xs uppercase tracking-widest`}>
              Compare
            </th>
            {bundles.map((b) => (
              <th key={b.id} className={headerCell}>
                <motion.div variants={fadeInUp} className="flex flex-col items-center gap-2">
                  <div className="relative">
                    {b.thumbnailUrl ? (
                      <img
                        src={b.thumbnailUrl}
                        alt={b.name}
                        className="w-16 h-16 rounded-xl object-cover border border-white/10"
                      />
                    ) : (
                      <div className="w-16 h-16 rounded-xl bg-white/[0.06] border border-white/10 flex items-center justify-center text-white/25 text-xs">
                        Bundle
                      </div>
                    )}
                    <button
                      onClick={() => onRemove(b.id)}
                      className="absolute -top-2 -right-2 w-5 h-5 rounded-full bg-white/10 border border-white/20 flex items-center justify-center hover:bg-red-500/30 hover:border-red-400/40 transition-colors"
                      title="Remove from comparison"
                    >
                      <X size={10} className="text-white/70" />
                    </button>
                  </div>
                  <span className="text-xs font-semibold text-white text-center leading-tight max-w-[130px] line-clamp-2">
                    {b.name}
                  </span>
                </motion.div>
              </th>
            ))}
          </tr>
        </thead>

        <tbody>
          {/* Price */}
          <tr className="odd:bg-white/[0.02]">
            <td className={labelCell}>Bundle Price</td>
            {bundles.map((b, i) => (
              <td key={b.id} className={`${cellBase} text-center`}>
                <motion.div variants={fadeInUp}>
                  <span className={`font-extrabold ${i === priceIdx ? "text-green-400" : "text-white"}`}>
                    {b.currency} {b.price.toFixed(2)}
                  </span>
                  {i === priceIdx && <BestBadge />}
                  {b.compareAtPrice != null && (
                    <div className="text-xs text-white/40 line-through mt-0.5">
                      {b.currency} {b.compareAtPrice.toFixed(2)}
                    </div>
                  )}
                </motion.div>
              </td>
            ))}
          </tr>

          {/* Item count */}
          <tr className="odd:bg-white/[0.02]">
            <td className={labelCell}>Items</td>
            {bundles.map((b) => (
              <td key={b.id} className={`${cellBase} text-center`}>
                <motion.div variants={fadeInUp}>
                  <span className="font-semibold text-white">{b.items.length}</span>
                  <span className="text-white/50 text-xs"> product{b.items.length !== 1 ? "s" : ""}</span>
                </motion.div>
              </td>
            ))}
          </tr>

          {/* Items list */}
          <tr className="odd:bg-white/[0.02]">
            <td className={`${labelCell} align-top pt-4`}>Includes</td>
            {bundles.map((b) => (
              <td key={b.id} className={`${cellBase} align-top`}>
                <motion.div variants={fadeInUp} className="flex flex-col gap-1">
                  {b.items
                    .slice()
                    .sort((a, c) => a.displayOrder - c.displayOrder)
                    .map((item) => (
                      <div
                        key={item.id}
                        className="text-xs px-2 py-1 rounded-lg bg-white/[0.04] border border-white/[0.06] text-white/75 flex items-center gap-1.5"
                      >
                        <span className="text-white/40 font-mono text-[10px] shrink-0">×{item.quantity}</span>
                        <span className="truncate">{item.productName}</span>
                        {item.variantTitle && (
                          <span className="text-white/40 shrink-0">· {item.variantTitle}</span>
                        )}
                      </div>
                    ))}
                </motion.div>
              </td>
            ))}
          </tr>

          {/* Currency */}
          <tr className="odd:bg-white/[0.02]">
            <td className={labelCell}>Currency</td>
            {bundles.map((b) => (
              <td key={b.id} className={`${cellBase} text-center`}>
                <motion.div variants={fadeInUp}>
                  <span className="text-xs font-mono text-white/70">{b.currency}</span>
                </motion.div>
              </td>
            ))}
          </tr>
        </tbody>
      </table>
    </motion.div>
  );
}
