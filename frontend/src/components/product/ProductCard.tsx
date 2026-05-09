import { useState, type MouseEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useSelector } from "react-redux";
import { motion, type Variants } from "framer-motion";
import { Bookmark, ShoppingBag } from "lucide-react";
import type { CatalogProduct } from "../../api/catalog";
import type { RootState } from "../../stores";
import SaveToListModal from "../savedlist/SaveToListModal";

interface Props {
  product: CatalogProduct;
  variants?: Variants;
}

export default function ProductCard({ product, variants }: Props) {
  const navigate = useNavigate();
  const accessToken = useSelector((s: RootState) => s.auth.accessToken);
  const [saveOpen, setSaveOpen] = useState(false);

  const thumbnailUrl = product.thumbnailUrl ?? product.images?.[0]?.imageUrl;
  const effectivePrice = product.price;
  const currency = product.currency?.toUpperCase() ?? "USD";

  function handleBookmarkClick(e: MouseEvent) {
    e.preventDefault();
    e.stopPropagation();
    if (!accessToken) {
      navigate("/login");
      return;
    }
    setSaveOpen(true);
  }

  return (
    <>
      <Link to={`/products/${product.id}`} className="group block">
        <motion.div
          variants={variants}
          whileHover={{ y: -4 }}
          className="relative rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm hover:shadow-md transition-shadow overflow-hidden"
        >
          {/* Bookmark button — overlay top-right */}
          <button
            type="button"
            onClick={handleBookmarkClick}
            aria-label="Save to list"
            className="absolute top-2 right-2 z-10 flex items-center justify-center w-8 h-8 rounded-full border border-white/15 bg-slate-950/60 backdrop-blur text-white/70 hover:text-sky-200 hover:border-white/30 transition-colors"
          >
            <Bookmark className="w-4 h-4" />
          </button>

          <div className="aspect-square bg-white/[0.04] flex items-center justify-center overflow-hidden">
            {thumbnailUrl ? (
              <img
                src={thumbnailUrl}
                alt={product.name}
                className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
              />
            ) : (
              <ShoppingBag className="w-10 h-10 text-white/20" />
            )}
          </div>
          <div className="p-4">
            {product.category && (
              <p className="text-xs uppercase tracking-[0.15em] font-semibold text-sky-200/70 mb-1 truncate">
                {product.category}
              </p>
            )}
            <h3 className="text-sm font-semibold text-white leading-snug line-clamp-2 mb-1">
              {product.name}
            </h3>
            {product.brand && (
              <p className="text-xs text-white/50 mb-2">{product.brand}</p>
            )}
            <p className="text-base font-extrabold text-white">
              {currency} {effectivePrice.toFixed(2)}
            </p>
          </div>
        </motion.div>
      </Link>

      <SaveToListModal
        open={saveOpen}
        onClose={() => setSaveOpen(false)}
        productId={product.id}
        productName={product.name}
      />
    </>
  );
}
