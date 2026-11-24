import Link from "next/link";
import { motion, type Variants } from "framer-motion";
import { Layers, Sparkles } from "lucide-react";
import type { Collection } from "../../types/collection";
import { useAnims } from "../../hooks/useAnims";
import { Badge } from "../ui/Badge";

interface Props {
  collection: Collection;
  variants?: Variants;
}

/**
 * Storefront tile for a featured collection. Shares ProductCard's surface,
 * media slot and hover so the two sit together in one grid.
 */
export default function CollectionCard({ collection, variants }: Props) {
  const { hoverLift } = useAnims();
  return (
    <motion.div variants={variants} whileHover={hoverLift} className="h-full">
      <Link
        href={`/collections/${collection.slug}`}
        className="group flex h-full flex-col overflow-hidden rounded-xl border bg-surface shadow-xs transition-shadow hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background"
      >
        <div className="relative aspect-square overflow-hidden bg-muted">
          {collection.imageUrl ? (
            <img
              src={collection.imageUrl}
              alt={collection.name}
              loading="lazy"
              className="size-full object-cover transition-transform duration-500 group-hover:scale-105 motion-reduce:transition-none motion-reduce:group-hover:scale-100"
            />
          ) : (
            <div className="flex size-full items-center justify-center">
              <Layers className="size-10 text-muted-foreground" aria-hidden />
            </div>
          )}
          {collection.type === "DYNAMIC" && (
            <Badge variant="info" className="absolute left-2 top-2">
              <Sparkles aria-hidden />
              Curated
            </Badge>
          )}
        </div>
        <div className="flex flex-1 flex-col gap-1 p-4">
          <h3 className="line-clamp-2 text-base font-semibold leading-snug text-foreground">
            {collection.name}
          </h3>
          {collection.description && (
            <p className="line-clamp-2 text-sm text-muted-foreground">
              {collection.description}
            </p>
          )}
          <p className="mt-auto pt-2 text-xs font-medium text-link">
            {collection.productCount}{" "}
            {collection.productCount === 1 ? "product" : "products"}
          </p>
        </div>
      </Link>
    </motion.div>
  );
}
