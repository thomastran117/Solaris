import { X } from "lucide-react";
import { motion } from "framer-motion";
import RatingStars from "../reviews/RatingStars";
import { useAnims } from "../../hooks/useAnims";
import type {
  ProductComparisonResponse,
  CompareBundle,
} from "../../types/comparison";
import { formatMoney } from "../../lib/format";

// ---- Best badge ----
function BestBadge() {
  return (
    <span className="ml-1.5 inline-block rounded-full border border-success/40 bg-success-subtle px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-success">
      Best
    </span>
  );
}

// ---- Shared cell styles ----
const cellBase =
  "px-4 py-3.5 border-b border-border text-sm text-foreground min-w-[160px]";
const labelCell =
  "px-4 py-3.5 border-b border-border text-sm font-medium text-muted-foreground whitespace-nowrap sticky left-0 bg-background z-10 min-w-[130px]";
const headerCell = "px-4 py-4 border-b border-border text-center min-w-[160px]";

/** Index of the best numeric value, or -1 when none are comparable. */
function findBestIndex(
  values: (number | null)[],
  lowerIsBetter = false
): number {
  let bestIdx = -1;
  let best = lowerIsBetter ? Infinity : -Infinity;
  values.forEach((v, i) => {
    if (v == null) return;
    if (lowerIsBetter ? v < best : v > best) {
      best = v;
      bestIdx = i;
    }
  });
  return bestIdx;
}

const STOCK_LABELS: Record<string, { text: string; className: string }> = {
  IN_STOCK: { text: "In stock", className: "text-success" },
  LOW_STOCK: { text: "Low stock", className: "text-warning" },
  OUT_OF_STOCK: { text: "Out of stock", className: "text-danger" },
};

// ======================================================================
// Products comparison table — consumes the server-built matrix
// ======================================================================

interface ProductTableProps {
  matrix: ProductComparisonResponse;
  onRemove: (id: string) => void;
}

export function ProductComparisonTable({
  matrix,
  onRemove,
}: ProductTableProps) {
  const { fadeInUp, stagger } = useAnims();
  const { products, attributes } = matrix;

  const priceIdx = findBestIndex(
    products.map((p) => p.price),
    true
  );
  const ratingIdx = findBestIndex(products.map((p) => p.rating));

  return (
    <motion.div
      initial="hidden"
      whileInView="visible"
      viewport={{ once: true, amount: 0.1 }}
      variants={stagger}
      className="overflow-x-auto rounded-xl border border-border bg-surface shadow-sm"
    >
      <table
        className="w-full border-collapse"
        style={{ minWidth: `${130 + products.length * 160}px` }}
      >
        <thead>
          <tr>
            <th
              className={`${labelCell} text-xs font-semibold text-muted-foreground`}
            >
              Compare
            </th>
            {products.map((p) => (
              <th key={p.productId} className={headerCell}>
                <motion.div
                  variants={fadeInUp}
                  className="flex flex-col items-center gap-2"
                >
                  <div className="relative">
                    {p.imageUrl ? (
                      <img
                        src={p.imageUrl}
                        alt={p.name}
                        className="h-16 w-16 rounded-xl border border-border object-cover"
                      />
                    ) : (
                      <div className="flex h-16 w-16 items-center justify-center rounded-xl border border-border bg-surface text-xs text-muted-foreground">
                        No img
                      </div>
                    )}
                    <button
                      onClick={() => onRemove(p.productId)}
                      className="absolute -right-2 -top-2 flex h-5 w-5 items-center justify-center rounded-full border border-input bg-muted transition-colors hover:border-danger/40 hover:bg-danger-subtle"
                      title="Remove from comparison"
                      aria-label={`Remove ${p.name} from comparison`}
                    >
                      <X size={10} className="text-muted-foreground" />
                    </button>
                  </div>
                  <span className="line-clamp-2 max-w-[130px] text-center text-xs font-semibold leading-tight text-foreground">
                    {p.name}
                  </span>
                </motion.div>
              </th>
            ))}
          </tr>
        </thead>

        <tbody>
          {/* Price */}
          <tr className="odd:bg-surface">
            <td className={labelCell}>Price</td>
            {products.map((p, i) => (
              <td key={p.productId} className={`${cellBase} text-center`}>
                <motion.div variants={fadeInUp}>
                  <span
                    className={`font-semibold ${i === priceIdx ? "text-success" : "text-foreground"}`}
                  >
                    {formatMoney(p.price, p.currency)}
                  </span>
                  {i === priceIdx && <BestBadge />}
                </motion.div>
              </td>
            ))}
          </tr>

          {/* Rating */}
          <tr className="odd:bg-surface">
            <td className={labelCell}>Rating</td>
            {products.map((p, i) => (
              <td key={p.productId} className={`${cellBase} text-center`}>
                <motion.div
                  variants={fadeInUp}
                  className="flex flex-col items-center gap-1"
                >
                  {p.rating != null ? (
                    <>
                      <div className="flex items-center gap-1">
                        <RatingStars rating={p.rating} size="sm" />
                        {i === ratingIdx && <BestBadge />}
                      </div>
                      <span className="text-xs text-muted-foreground">
                        {p.rating.toFixed(1)} ({p.reviewCount} review
                        {p.reviewCount !== 1 ? "s" : ""})
                      </span>
                    </>
                  ) : (
                    <span className="text-xs text-muted-foreground">
                      No reviews
                    </span>
                  )}
                </motion.div>
              </td>
            ))}
          </tr>

          {/* Stock */}
          <tr className="odd:bg-surface">
            <td className={labelCell}>Availability</td>
            {products.map((p) => {
              const stock = STOCK_LABELS[p.stockStatus] ?? {
                text: p.stockStatus,
                className: "text-muted-foreground",
              };
              return (
                <td key={p.productId} className={`${cellBase} text-center`}>
                  <motion.div variants={fadeInUp}>
                    <span
                      className={`text-sm font-semibold ${stock.className}`}
                    >
                      {stock.text}
                    </span>
                  </motion.div>
                </td>
              );
            })}
          </tr>

          {/* Dynamic attribute rows from the server matrix */}
          {attributes.map((row) => (
            <tr key={row.attributeName} className="odd:bg-surface">
              <td className={labelCell}>{row.attributeName}</td>
              {products.map((p) => {
                const value = row.valuesByProductId[p.productId];
                return (
                  <td key={p.productId} className={`${cellBase} text-center`}>
                    <motion.div variants={fadeInUp}>
                      {value != null ? (
                        <span>{value}</span>
                      ) : (
                        <span className="text-muted-foreground">—</span>
                      )}
                    </motion.div>
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </motion.div>
  );
}

// ======================================================================
// Bundles comparison table — full objects, matrix built client-side
// ======================================================================

interface BundleTableProps {
  bundles: CompareBundle[];
  onRemove: (id: string) => void;
}

export function BundleComparisonTable({ bundles, onRemove }: BundleTableProps) {
  const { fadeInUp, stagger } = useAnims();

  const priceIdx = findBestIndex(
    bundles.map((b) => b.price),
    true
  );

  return (
    <motion.div
      initial="hidden"
      whileInView="visible"
      viewport={{ once: true, amount: 0.1 }}
      variants={stagger}
      className="overflow-x-auto rounded-xl border border-border bg-surface shadow-sm"
    >
      <table
        className="w-full border-collapse"
        style={{ minWidth: `${130 + bundles.length * 160}px` }}
      >
        <thead>
          <tr>
            <th
              className={`${labelCell} text-xs font-semibold text-muted-foreground`}
            >
              Compare
            </th>
            {bundles.map((b) => (
              <th key={b.id} className={headerCell}>
                <motion.div
                  variants={fadeInUp}
                  className="flex flex-col items-center gap-2"
                >
                  <div className="relative">
                    {b.thumbnailUrl ? (
                      <img
                        src={b.thumbnailUrl}
                        alt={b.name}
                        className="h-16 w-16 rounded-xl border border-border object-cover"
                      />
                    ) : (
                      <div className="flex h-16 w-16 items-center justify-center rounded-xl border border-border bg-surface text-xs text-muted-foreground">
                        Bundle
                      </div>
                    )}
                    <button
                      onClick={() => onRemove(b.id)}
                      className="absolute -right-2 -top-2 flex h-5 w-5 items-center justify-center rounded-full border border-input bg-muted transition-colors hover:border-danger/40 hover:bg-danger-subtle"
                      title="Remove from comparison"
                      aria-label={`Remove ${b.name} from comparison`}
                    >
                      <X size={10} className="text-muted-foreground" />
                    </button>
                  </div>
                  <span className="line-clamp-2 max-w-[130px] text-center text-xs font-semibold leading-tight text-foreground">
                    {b.name}
                  </span>
                </motion.div>
              </th>
            ))}
          </tr>
        </thead>

        <tbody>
          {/* Price */}
          <tr className="odd:bg-surface">
            <td className={labelCell}>Bundle Price</td>
            {bundles.map((b, i) => (
              <td key={b.id} className={`${cellBase} text-center`}>
                <motion.div variants={fadeInUp}>
                  <span
                    className={`font-semibold ${i === priceIdx ? "text-success" : "text-foreground"}`}
                  >
                    {formatMoney(b.price, b.currency)}
                  </span>
                  {i === priceIdx && <BestBadge />}
                  {b.compareAtPrice != null && (
                    <div className="mt-0.5 text-xs text-muted-foreground line-through">
                      {formatMoney(b.compareAtPrice, b.currency)}
                    </div>
                  )}
                </motion.div>
              </td>
            ))}
          </tr>

          {/* Item count */}
          <tr className="odd:bg-surface">
            <td className={labelCell}>Items</td>
            {bundles.map((b) => (
              <td key={b.id} className={`${cellBase} text-center`}>
                <motion.div variants={fadeInUp}>
                  <span className="font-semibold text-foreground">
                    {b.items.length}
                  </span>
                  <span className="text-xs text-muted-foreground">
                    {" "}
                    product{b.items.length !== 1 ? "s" : ""}
                  </span>
                </motion.div>
              </td>
            ))}
          </tr>

          {/* Items list */}
          <tr className="odd:bg-surface">
            <td className={`${labelCell} pt-4 align-top`}>Includes</td>
            {bundles.map((b) => (
              <td key={b.id} className={`${cellBase} align-top`}>
                <motion.div variants={fadeInUp} className="flex flex-col gap-1">
                  {[...b.items]
                    .sort((a, c) => a.displayOrder - c.displayOrder)
                    .map((item) => (
                      <div
                        key={item.id}
                        className="flex items-center gap-1.5 rounded-lg border border-border bg-surface px-2 py-1 text-xs text-muted-foreground"
                      >
                        <span className="shrink-0 font-mono text-[10px] text-muted-foreground">
                          ×{item.quantity}
                        </span>
                        <span className="truncate">{item.productName}</span>
                        {item.variantTitle && (
                          <span className="shrink-0 text-muted-foreground">
                            · {item.variantTitle}
                          </span>
                        )}
                      </div>
                    ))}
                </motion.div>
              </td>
            ))}
          </tr>
        </tbody>
      </table>
    </motion.div>
  );
}
