import { useState } from "react";
import { useParams } from "react-router-dom";
import { useSelector } from "react-redux";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { motion, useReducedMotion, type Variants } from "framer-motion";
import { Bell, BellOff, ShoppingCart, CheckCircle, ChevronLeft, Package, Bookmark } from "lucide-react";
import { catalogApi } from "../api/catalog";
import { notificationsApi } from "../api/notifications";
import AvailabilityPanel from "../components/product/AvailabilityPanel";
import SaveToListModal from "../components/savedlist/SaveToListModal";
import type { RootState } from "../stores";
import type { StockNotification } from "../types/notifications";
import { useNavigate } from "react-router-dom";

const useAnims = () => {
  const prefersReducedMotion = useReducedMotion();
  const fadeInUp: Variants = prefersReducedMotion
    ? { hidden: { opacity: 0 }, visible: { opacity: 1, transition: { duration: 0.25 } } }
    : { hidden: { opacity: 0, y: 18 }, visible: { opacity: 1, y: 0, transition: { duration: 0.5 } } };
  const fadeIn: Variants = prefersReducedMotion
    ? { hidden: { opacity: 0 }, visible: { opacity: 1 } }
    : { hidden: { opacity: 0 }, visible: { opacity: 1, transition: { duration: 0.5 } } };
  return { fadeInUp, fadeIn };
};

function buildVariantLabel(
  v: { option1: string | null; option2: string | null; option3: string | null }
) {
  return [v.option1, v.option2, v.option3].filter(Boolean).join(" / ");
}

export default function ProductPage() {
  const { id } = useParams<{ id: string }>();
  const productId = Number(id);
  const { fadeInUp, fadeIn } = useAnims();
  const queryClient = useQueryClient();

  const marketplaceId = useSelector((s: RootState) => s.marketplace.currentMarketplace?.id);
  const accessToken = useSelector((s: RootState) => s.auth.accessToken);

  const [selectedVariantId, setSelectedVariantId] = useState<number | null>(null);
  const [saveOpen, setSaveOpen] = useState(false);
  const navigate = useNavigate();

  const { data: product, isLoading, isError } = useQuery({
    queryKey: ["product", marketplaceId, productId],
    queryFn: () => catalogApi.getProduct(marketplaceId!, productId).then(r => r.data),
    enabled: !!marketplaceId && !!productId,
  });

  const { data: myNotifications } = useQuery({
    queryKey: ["stock-notifications"],
    queryFn: () => notificationsApi.listBackInStock().then(r => r.data),
    enabled: !!accessToken,
  });

  const subscribeMutation = useMutation({
    mutationFn: (variantId?: number) =>
      notificationsApi.subscribeBackInStock(productId, variantId).then(r => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["stock-notifications"] });
    },
  });

  const cancelMutation = useMutation({
    mutationFn: (notificationId: number) =>
      notificationsApi.cancelBackInStock(notificationId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["stock-notifications"] });
    },
  });

  const selectedVariant = product?.variants.find(v => v.id === selectedVariantId) ?? null;
  const effectiveStock = selectedVariant?.stock ?? product?.stock ?? null;
  const isOutOfStock = effectiveStock === 0;
  const isPurchasable = selectedVariant?.purchasable ?? product?.purchasable ?? true;
  const unavailable = isOutOfStock || !isPurchasable;

  const activeNotification: StockNotification | undefined = myNotifications?.find(n => {
    if (n.productId !== productId) return false;
    if (selectedVariantId !== null) return n.variantId === selectedVariantId;
    return n.variantId === null;
  });

  const hasNotification = activeNotification?.status === "PENDING";

  function handleNotify() {
    if (!accessToken) return;
    if (hasNotification && activeNotification) {
      cancelMutation.mutate(activeNotification.id);
    } else {
      subscribeMutation.mutate(selectedVariantId ?? undefined);
    }
  }

  if (!marketplaceId) {
    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center">
        <p className="text-white/60 text-sm">No marketplace selected.</p>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center">
        <div className="w-8 h-8 rounded-full border-2 border-sky-400/40 border-t-sky-400 animate-spin" />
      </div>
    );
  }

  if (isError || !product) {
    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center">
        <p className="text-red-400 text-sm">Product not found.</p>
      </div>
    );
  }

  const thumbnailUrl = product.thumbnailUrl ?? product.images?.[0]?.imageUrl;
  const hasVariants = product.variants.length > 0;
  const price = selectedVariant?.price ?? product.price;
  const currency = product.currency?.toUpperCase() ?? "USD";

  return (
    <div className="min-h-screen bg-slate-950 relative overflow-hidden">
      {/* Ambient glow */}
      <div
        aria-hidden
        className="pointer-events-none fixed inset-0"
        style={{ zIndex: 0 }}
      >
        <div className="absolute top-1/4 left-1/4 w-[600px] h-[600px] rounded-full bg-blue-600/10 blur-[120px]" />
        <div className="absolute bottom-1/3 right-1/4 w-[400px] h-[400px] rounded-full bg-sky-400/8 blur-[100px]" />
      </div>

      <div className="relative z-10 max-w-5xl mx-auto px-4 sm:px-6 py-10">
        {/* Back link */}
        <motion.a
          href="/"
          variants={fadeIn}
          initial="hidden"
          animate="visible"
          className="inline-flex items-center gap-1.5 text-sm text-white/60 hover:text-white/90 transition-colors mb-8 group"
        >
          <ChevronLeft className="w-4 h-4 group-hover:-translate-x-0.5 transition-transform" />
          Back to catalogue
        </motion.a>

        <motion.div
          variants={fadeInUp}
          initial="hidden"
          animate="visible"
          className="grid grid-cols-1 md:grid-cols-2 gap-8 lg:gap-12"
        >
          {/* Product image */}
          <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur overflow-hidden aspect-square flex items-center justify-center">
            {thumbnailUrl ? (
              <img
                src={thumbnailUrl}
                alt={product.name}
                className="w-full h-full object-cover"
              />
            ) : (
              <div className="flex flex-col items-center gap-3 text-white/30">
                <Package className="w-16 h-16" />
                <span className="text-xs uppercase tracking-[0.2em]">No image</span>
              </div>
            )}
          </div>

          {/* Product details */}
          <div className="flex flex-col gap-5">
            {/* Kicker */}
            {product.category && (
              <span className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90">
                {product.category}
              </span>
            )}

            {/* Name */}
            <h1 className="text-3xl md:text-4xl font-extrabold tracking-tight text-white leading-tight">
              {product.name}
            </h1>

            {/* Brand / vendor */}
            {(product.brand || product.vendorName) && (
              <p className="text-sm text-white/60">
                {product.brand ?? product.vendorName}
              </p>
            )}

            {/* Price */}
            <p className="text-2xl font-extrabold text-white">
              {currency}{" "}
              {typeof price === "number" ? price.toFixed(2) : "—"}
            </p>

            {/* Variant selector */}
            {hasVariants && (
              <div className="flex flex-col gap-2">
                <p className="text-xs uppercase tracking-[0.2em] font-semibold text-white/60">
                  Variant
                </p>
                <div className="flex flex-wrap gap-2">
                  {product.variants.map(v => {
                    const label = buildVariantLabel(v);
                    const outOfVariant = v.stock === 0;
                    const isSelected = selectedVariantId === v.id;
                    return (
                      <button
                        key={v.id}
                        onClick={() => setSelectedVariantId(isSelected ? null : v.id)}
                        className={[
                          "px-3 py-1.5 rounded-xl text-sm font-medium border transition-all",
                          isSelected
                            ? "border-sky-400/70 bg-sky-400/15 text-sky-200"
                            : outOfVariant
                            ? "border-white/10 bg-white/[0.03] text-white/35 cursor-pointer"
                            : "border-white/20 bg-white/[0.06] text-white/80 hover:border-white/40 hover:bg-white/10",
                        ].join(" ")}
                      >
                        {label || `Variant ${v.id}`}
                        {outOfVariant && (
                          <span className="ml-1.5 text-white/35 text-xs">(OOS)</span>
                        )}
                      </button>
                    );
                  })}
                </div>
              </div>
            )}

            {/* Stock badge */}
            <div className="flex items-center gap-2">
              {unavailable ? (
                <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-red-500/15 border border-red-500/30 text-red-400">
                  Out of stock
                </span>
              ) : (
                <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-green-500/15 border border-green-500/30 text-green-400">
                  <CheckCircle className="w-3.5 h-3.5" />
                  In stock
                  {effectiveStock !== null && effectiveStock <= 5 && effectiveStock > 0 && (
                    <span className="text-yellow-400 ml-0.5">— only {effectiveStock} left</span>
                  )}
                </span>
              )}
            </div>

            {/* Availability & delivery */}
            <AvailabilityPanel
              marketplaceId={marketplaceId}
              productId={productId}
              variantId={selectedVariantId}
              unavailable={unavailable}
            />

            {/* Description */}
            {product.description && (
              <p className="text-sm text-white/70 leading-relaxed line-clamp-4">
                {product.description}
              </p>
            )}

            {/* CTA area */}
            <div className="flex flex-col gap-3 mt-auto pt-2">
              {!unavailable ? (
                /* In stock → Add to Cart + Save to list */
                <div className="flex gap-2">
                  <button
                    disabled
                    className="flex-1 flex items-center justify-center gap-2 py-3 rounded-full bg-blue-600 hover:bg-blue-500 text-white font-semibold text-sm transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    <ShoppingCart className="w-4 h-4" />
                    Add to Cart
                  </button>
                  <button
                    type="button"
                    onClick={() => (accessToken ? setSaveOpen(true) : navigate("/login"))}
                    aria-label="Save to list"
                    className="flex items-center justify-center gap-1.5 px-4 py-3 rounded-full border border-white/20 bg-white/[0.04] text-white/80 hover:bg-white/10 hover:text-sky-200 transition-colors text-sm font-semibold"
                  >
                    <Bookmark className="w-4 h-4" />
                    Save
                  </button>
                </div>
              ) : (
                /* Out of stock → Notify Me */
                accessToken ? (
                  <NotifyButton
                    hasNotification={hasNotification}
                    isLoading={subscribeMutation.isPending || cancelMutation.isPending}
                    onToggle={handleNotify}
                  />
                ) : (
                  <div className="rounded-2xl border border-white/10 bg-white/[0.06] p-4 text-center">
                    <p className="text-white/60 text-sm mb-2">
                      <a href="/login" className="text-sky-300 hover:text-sky-200 underline underline-offset-2">
                        Sign in
                      </a>{" "}
                      to be notified when this is back in stock.
                    </p>
                  </div>
                )
              )}
            </div>
          </div>
        </motion.div>

        {/* Tags */}
        {product.tags && (
          <motion.div
            variants={fadeIn}
            initial="hidden"
            animate="visible"
            className="mt-10 flex flex-wrap gap-2"
          >
            {product.tags.split(",").map(tag => tag.trim()).filter(Boolean).map(tag => (
              <span
                key={tag}
                className="px-3 py-1 rounded-full text-xs font-medium border border-white/10 bg-white/[0.05] text-white/60"
              >
                {tag}
              </span>
            ))}
          </motion.div>
        )}
      </div>

      <SaveToListModal
        open={saveOpen}
        onClose={() => setSaveOpen(false)}
        productId={product.id}
        productName={product.name}
      />
    </div>
  );
}

interface NotifyButtonProps {
  hasNotification: boolean;
  isLoading: boolean;
  onToggle: () => void;
}

function NotifyButton({ hasNotification, isLoading, onToggle }: NotifyButtonProps) {
  if (hasNotification) {
    return (
      <div className="flex flex-col gap-2">
        <div className="flex items-center gap-2 px-4 py-3 rounded-2xl border border-sky-400/30 bg-sky-400/10 text-sky-200 text-sm font-medium">
          <Bell className="w-4 h-4 fill-sky-200" />
          You'll be notified when it's back
        </div>
        <button
          onClick={onToggle}
          disabled={isLoading}
          className="flex items-center justify-center gap-1.5 text-xs text-white/45 hover:text-white/70 transition-colors disabled:opacity-40"
        >
          <BellOff className="w-3.5 h-3.5" />
          Cancel notification
        </button>
      </div>
    );
  }

  return (
    <button
      onClick={onToggle}
      disabled={isLoading}
      className="flex items-center justify-center gap-2 w-full py-3 rounded-full border border-white/20 bg-transparent hover:bg-white/10 text-white/90 font-semibold text-sm transition-all disabled:opacity-40"
    >
      {isLoading ? (
        <div className="w-4 h-4 rounded-full border-2 border-white/30 border-t-white/80 animate-spin" />
      ) : (
        <Bell className="w-4 h-4" />
      )}
      Notify me when available
    </button>
  );
}
