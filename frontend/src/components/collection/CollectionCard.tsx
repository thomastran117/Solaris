import { Link } from "react-router-dom";
import { motion, type Variants } from "framer-motion";
import { Layers, Sparkles } from "lucide-react";
import type { Collection } from "../../types/collection";

interface Props {
  collection: Collection;
  variants?: Variants;
}

/**
 * Storefront tile for a featured collection. Mirrors the visual rhythm of
 * {@code ProductCard}: same border, surface, hover lift, and aspect-square media slot.
 */
export default function CollectionCard({ collection, variants }: Props) {
  return (
    <Link to={`/collections/${collection.slug}`} className="group block">
      <motion.div
        variants={variants}
        whileHover={{ y: -4 }}
        className="relative rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm hover:shadow-md transition-shadow overflow-hidden"
      >
        {collection.type === "DYNAMIC" && (
          <span className="absolute top-2 left-2 z-10 inline-flex items-center gap-1 px-2 py-1 rounded-full text-[10px] font-bold tracking-wide bg-blue-500/15 border border-white/10 text-sky-200 backdrop-blur">
            <Sparkles className="w-3 h-3" />
            Curated
          </span>
        )}
        <div className="aspect-square bg-white/[0.04] flex items-center justify-center overflow-hidden">
          {collection.imageUrl ? (
            <img
              src={collection.imageUrl}
              alt={collection.name}
              className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
            />
          ) : (
            <Layers className="w-10 h-10 text-white/20" />
          )}
        </div>
        <div className="p-4">
          <h3 className="text-base font-semibold text-white leading-snug line-clamp-2 mb-1">
            {collection.name}
          </h3>
          {collection.description && (
            <p className="text-xs text-white/55 line-clamp-2 mb-2">{collection.description}</p>
          )}
          <p className="text-xs text-sky-200/80 font-semibold">
            {collection.productCount}{" "}
            {collection.productCount === 1 ? "product" : "products"}
          </p>
        </div>
      </motion.div>
    </Link>
  );
}
