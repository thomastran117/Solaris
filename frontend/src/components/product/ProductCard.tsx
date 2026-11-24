import { useState, type MouseEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useSelector } from "react-redux";
import { motion, type Variants } from "framer-motion";
import { Bookmark, ShoppingBag } from "lucide-react";
import type { CatalogProduct } from "../../api/catalog";
import type { RootState } from "../../stores";
import { loginUrlFor } from "../../lib/routes";
import { formatMoney } from "../../lib/format";
import { useAnims } from "../../hooks/useAnims";
import { Badge } from "../ui/Badge";
import SaveToListModal from "../savedlist/SaveToListModal";

interface Props {
  product: CatalogProduct;
  variants?: Variants;
}

export default function ProductCard({ product, variants }: Props) {
  const router = useRouter();
  const { hoverLift } = useAnims();
  const accessToken = useSelector((s: RootState) => s.auth.accessToken);
  const [saveOpen, setSaveOpen] = useState(false);

  const thumbnailUrl = product.thumbnailUrl ?? product.images?.[0]?.imageUrl;
  const price = product.price;
  const currency = product.currency?.toUpperCase() ?? "USD";
  const promo = product.activePromotion;
  const showCompareAt =
    product.compareAtPrice != null &&
    (!!promo || product.compareAtPrice > price);
  const saleLabel =
    promo?.percentOff != null
      ? `-${Math.round(promo.percentOff)}%`
      : promo?.amountOff != null
        ? `-${formatMoney(promo.amountOff, currency)}`
        : promo
          ? "Sale"
          : null;

  function handleBookmarkClick(e: MouseEvent) {
    e.preventDefault();
    e.stopPropagation();
    if (!accessToken) {
      router.push(
        loginUrlFor(window.location.pathname + window.location.search)
      );
      return;
    }
    setSaveOpen(true);
  }

  return (
    <>
      <motion.div
        variants={variants}
        whileHover={hoverLift}
        className="relative h-full"
      >
        <Link
          href={`/products/${product.id}`}
          className="group relative flex h-full flex-col overflow-hidden rounded-xl border bg-surface shadow-xs transition-shadow hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background"
        >
          <div className="relative aspect-square overflow-hidden bg-muted">
            {thumbnailUrl ? (
              <img
                src={thumbnailUrl}
                alt={product.name}
                loading="lazy"
                className="size-full object-cover transition-transform duration-500 group-hover:scale-105 motion-reduce:transition-none motion-reduce:group-hover:scale-100"
              />
            ) : (
              <div className="flex size-full items-center justify-center">
                <ShoppingBag
                  className="size-10 text-muted-foreground"
                  aria-hidden
                />
              </div>
            )}
            {saleLabel && (
              <Badge variant="danger" className="absolute left-2 top-2">
                {saleLabel}
              </Badge>
            )}
          </div>
          <div className="flex flex-1 flex-col gap-1 p-4">
            {product.category && (
              <p className="truncate text-xs text-muted-foreground">
                {product.category}
              </p>
            )}
            <h3 className="line-clamp-2 text-sm font-medium leading-snug text-foreground">
              {product.name}
            </h3>
            {product.brand && (
              <p className="text-xs text-muted-foreground">{product.brand}</p>
            )}
            <div className="mt-auto flex items-baseline gap-2 pt-2 tabular-nums">
              <p className="text-base font-semibold text-foreground">
                {formatMoney(price, currency)}
              </p>
              {showCompareAt && product.compareAtPrice != null && (
                <p className="text-xs text-muted-foreground line-through">
                  {formatMoney(product.compareAtPrice, currency)}
                </p>
              )}
            </div>
          </div>
        </Link>
        {/* Outside the link so it isn't a nested interactive element. */}
        <button
          type="button"
          onClick={handleBookmarkClick}
          aria-label={`Save ${product.name} to a list`}
          className="absolute right-2 top-2 z-10 flex size-8 items-center justify-center rounded-full border bg-surface-raised/90 text-muted-foreground shadow-sm transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <Bookmark className="size-4" aria-hidden />
        </button>
      </motion.div>

      <SaveToListModal
        open={saveOpen}
        onClose={() => setSaveOpen(false)}
        productId={product.id}
        productName={product.name}
      />
    </>
  );
}
