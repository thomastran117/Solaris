"use client";

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useSelector, useDispatch } from "react-redux";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { motion } from "framer-motion";
import {
  Bell,
  BellOff,
  ShoppingCart,
  CheckCircle,
  ChevronLeft,
  Package,
  Bookmark,
  Star,
  Flag,
  TrendingDown,
} from "lucide-react";
import { catalogApi } from "../api/catalog";
import { notificationsApi } from "../api/notifications";
import { priceWatchersApi } from "../api/priceWatchers";
import { loyaltyApi } from "../api/loyalty";
import AvailabilityPanel from "../components/product/AvailabilityPanel";
import SaveToListModal from "../components/savedlist/SaveToListModal";
import ReviewsSection from "../components/reviews/ReviewsSection";
import QASection from "../components/qa/QASection";
import ReportModal from "../components/report/ReportModal";
import type { RootState, AppDispatch } from "../stores";
import { selectActiveMarketplaceId } from "../stores/marketplaceSlice";
import type { StockNotification } from "../types/notifications";
import type { PriceWatcher } from "../types/priceWatchers";
import { addLine } from "../stores/cartSlice";
import { useCartDrawer } from "../components/cart/CartDrawer";
import { formatCalendarDate } from "../lib/format";
import { productKeys } from "../api/keys";
import { loginUrlFor } from "../lib/routes";
import { useIsClient } from "../hooks/useIsClient";
import { useAnims } from "../hooks/useAnims";
import { formatMoney } from "../lib/format";

function buildVariantLabel(v: {
  option1: string | null;
  option2: string | null;
  option3: string | null;
}) {
  return [v.option1, v.option2, v.option3].filter(Boolean).join(" / ");
}

export default function ProductPage() {
  const { id } = useParams<{ id: string }>();
  const productId = id;
  // Time-zone and clock dependent labels render only after hydration.
  const isClient = useIsClient();
  const { fadeInUp, fadeIn } = useAnims();
  const queryClient = useQueryClient();

  const marketplaceId = useSelector(selectActiveMarketplaceId);
  const accessToken = useSelector((s: RootState) => s.auth.accessToken);
  const dispatch = useDispatch<AppDispatch>();

  const [selectedVariantId, setSelectedVariantId] = useState<string | null>(
    null
  );
  const { openCart } = useCartDrawer();
  const [saveOpen, setSaveOpen] = useState(false);
  const [reportOpen, setReportOpen] = useState(false);
  const router = useRouter();

  const {
    data: product,
    isLoading,
    isError,
  } = useQuery({
    queryKey: productKeys.detail(marketplaceId, productId),
    queryFn: () =>
      catalogApi.getProduct(marketplaceId!, productId).then((r) => r.data),
    enabled: !!marketplaceId && !!productId,
  });

  const { data: myNotifications } = useQuery({
    queryKey: ["stock-notifications"],
    queryFn: () => notificationsApi.listBackInStock().then((r) => r.data),
    enabled: !!accessToken,
  });

  const subscribeMutation = useMutation({
    mutationFn: (variantId?: string) =>
      notificationsApi
        .subscribeBackInStock(productId, variantId)
        .then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["stock-notifications"] });
    },
  });

  const cancelMutation = useMutation({
    mutationFn: (notificationId: string) =>
      notificationsApi.cancelBackInStock(notificationId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["stock-notifications"] });
    },
  });

  const { data: priceWatches } = useQuery({
    queryKey: ["price-watches"],
    queryFn: () => priceWatchersApi.list().then((r) => r.data),
    enabled: !!accessToken,
  });

  const activeWatch: PriceWatcher | undefined = priceWatches?.content.find(
    (w) => w.productId === productId
  );
  const isWatching = !!activeWatch;

  const watchMutation = useMutation({
    mutationFn: () => priceWatchersApi.watch(productId!).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["price-watches"] });
    },
  });

  const unwatchMutation = useMutation({
    mutationFn: () => priceWatchersApi.unwatch(productId!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["price-watches"] });
    },
  });

  function handleWatchToggle() {
    if (!accessToken) return;
    if (isWatching) {
      unwatchMutation.mutate();
    } else {
      watchMutation.mutate();
    }
  }

  const { data: loyaltyPolicy } = useQuery({
    queryKey: ["loyalty", "policy", product?.companyId],
    queryFn: () => loyaltyApi.getPolicy(product!.companyId).then((r) => r.data),
    enabled: !!accessToken && !!product?.companyId,
    retry: false,
  });

  const { data: loyaltyAccount } = useQuery({
    queryKey: ["loyalty", "account", product?.companyId],
    queryFn: () =>
      loyaltyApi.getAccount(product!.companyId).then((r) => r.data),
    enabled: !!accessToken && !!product?.companyId,
    retry: false,
  });

  const { data: loyaltyTiers } = useQuery({
    queryKey: ["loyalty", "tiers", product?.companyId],
    queryFn: () => loyaltyApi.listTiers(product!.companyId).then((r) => r.data),
    enabled: !!product?.companyId,
    retry: false,
  });

  const selectedVariant =
    product?.variants.find((v) => v.id === selectedVariantId) ?? null;
  // When a variant is selected, use its stock directly (null = unlimited, 0 = depleted).
  // Only fall back to product-level stock when no variant is selected, to avoid a
  // null-stock variant (unlimited) incorrectly inheriting a depleted product stock of 0.
  const effectiveStock = selectedVariant
    ? selectedVariant.stock
    : (product?.stock ?? null);
  const isOutOfStock = effectiveStock === 0;
  const isPurchasable =
    selectedVariant?.purchasable ?? product?.purchasable ?? true;
  const unavailable = isOutOfStock || !isPurchasable;
  const effectivePreorderEnabled =
    selectedVariant?.preorderEnabled ?? product?.preorderEnabled ?? false;
  const isPreorderEligible = isOutOfStock && effectivePreorderEnabled;
  const preorderExpectedDate =
    selectedVariant?.preorderExpectedDate ??
    product?.preorderExpectedDate ??
    null;

  const activeNotification: StockNotification | undefined =
    myNotifications?.find((n) => {
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

  function handleAddToCart() {
    if (!product || !marketplaceId) return;
    const selectedVariant =
      product.variants.find((v) => v.id === selectedVariantId) ?? null;
    const variantLabel = selectedVariant
      ? [
          selectedVariant.option1,
          selectedVariant.option2,
          selectedVariant.option3,
        ]
          .filter(Boolean)
          .join(" / ") || null
      : null;
    dispatch(
      addLine({
        kind: "product",
        productId: product.id,
        variantId: selectedVariantId,
        name: product.name,
        variantLabel,
        price: selectedVariant?.price ?? product.price,
        currency: product.currency?.toUpperCase() ?? "USD",
        quantity: 1,
        thumbnailUrl:
          product.thumbnailUrl ?? product.images?.[0]?.imageUrl ?? null,
        marketplaceId,
        companyId: product.companyId,
      })
    );
    openCart();
  }

  if (!marketplaceId) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center">
        <p className="text-muted-foreground text-sm">
          No marketplace selected.
        </p>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center">
        <div className="w-8 h-8 rounded-full border-2 border-primary/40 border-t-primary animate-spin" />
      </div>
    );
  }

  if (isError || !product) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center">
        <p className="text-danger text-sm">Product not found.</p>
      </div>
    );
  }

  const thumbnailUrl = product.thumbnailUrl ?? product.images?.[0]?.imageUrl;
  const hasVariants = product.variants.length > 0;
  const price = selectedVariant?.price ?? product.price;
  const currency = product.currency?.toUpperCase() ?? "USD";
  const promo = product.activePromotion;
  const showStrikethrough =
    !!promo ||
    (product.compareAtPrice != null && product.compareAtPrice > product.price);
  const saleLabel =
    promo?.percentOff != null
      ? `-${Math.round(promo.percentOff)}%`
      : promo?.amountOff != null
        ? `-${formatMoney(promo.amountOff, currency)}`
        : promo
          ? "Sale"
          : null;
  // Show "Sale ends …" only when the window closes within the next 14 days — beyond that
  // the deadline is too far off to feel urgent and the line just adds noise.
  const saleEndsLabel = (() => {
    if (!isClient || !promo?.endDate) return null;
    const end = new Date(promo.endDate);
    const ms = end.getTime() - Date.now();
    const fourteenDaysMs = 14 * 24 * 60 * 60 * 1000;
    if (ms <= 0 || ms > fourteenDaysMs) return null;
    return end.toLocaleString(undefined, {
      dateStyle: "medium",
      timeStyle: "short",
    });
  })();

  // Plain computation: this runs after the loading/error early returns above, so it
  // must not be a hook (a useMemo here broke the rules of hooks once data loaded).
  const earnablePts = (() => {
    if (!loyaltyPolicy || loyaltyPolicy.earnMode === "CASHBACK") return null;
    if (typeof price !== "number") return null;
    const currentTier = loyaltyTiers?.find(
      (t) => t.id === loyaltyAccount?.currentTierId
    );
    const multiplier = currentTier?.earnMultiplier ?? 1;
    return Math.floor(price * loyaltyPolicy.earnRatePerDollar * multiplier);
  })();

  return (
    <div className="bg-background relative overflow-hidden">
      <div className="relative z-10 max-w-5xl mx-auto px-4 sm:px-6 py-10">
        {/* Back link */}
        <motion.a
          href="/"
          variants={fadeIn}
          initial="hidden"
          animate="visible"
          className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors mb-8 group"
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
          <div className="rounded-xl border border-border bg-surface overflow-hidden aspect-square flex items-center justify-center">
            {thumbnailUrl ? (
              <img
                src={thumbnailUrl}
                alt={product.name}
                className="w-full h-full object-cover"
              />
            ) : (
              <div className="flex flex-col items-center gap-3 text-muted-foreground">
                <Package className="w-16 h-16" />
                <span className="text-xs">No image</span>
              </div>
            )}
          </div>

          {/* Product details */}
          <div className="flex flex-col gap-5">
            {/* Kicker */}
            {product.category && (
              <span className="text-xs font-semibold text-link">
                {product.category}
              </span>
            )}

            {/* Name */}
            <h1 className="text-3xl md:text-4xl font-semibold tracking-tight text-foreground leading-tight">
              {product.name}
            </h1>

            {/* Brand / vendor */}
            {(product.brand || product.vendorName) && (
              <p className="text-sm text-muted-foreground">
                {product.brand ?? product.vendorName}
              </p>
            )}

            {/* Price */}
            <div className="flex flex-col gap-1">
              <div className="flex items-baseline gap-3 flex-wrap">
                <p className="text-2xl font-semibold tabular-nums text-foreground">
                  {typeof price === "number"
                    ? formatMoney(price, currency)
                    : "—"}
                </p>
                {showStrikethrough && product.compareAtPrice != null && (
                  <p className="text-sm font-medium text-muted-foreground line-through">
                    {formatMoney(product.compareAtPrice, currency)}
                  </p>
                )}
                {saleLabel && (
                  <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-bold tracking-wide bg-primary/10 border border-border text-link">
                    {saleLabel}
                  </span>
                )}
              </div>
              {saleEndsLabel && (
                <p className="text-xs text-muted-foreground">
                  Sale ends {saleEndsLabel}
                </p>
              )}
            </div>

            {/* Points-earning indicator */}
            {earnablePts !== null && earnablePts > 0 && (
              <div className="flex items-center gap-1.5 text-link text-xs font-medium">
                <Star className="h-3.5 w-3.5 fill-link" />
                Earn ~{earnablePts.toLocaleString()} pts on this purchase
              </div>
            )}

            {/* Variant selector */}
            {hasVariants && (
              <div className="flex flex-col gap-2">
                <p className="text-xs font-semibold text-muted-foreground">
                  Variant
                </p>
                <div className="flex flex-wrap gap-2">
                  {product.variants.map((v) => {
                    const label = buildVariantLabel(v);
                    const outOfVariant = v.stock === 0;
                    const isSelected = selectedVariantId === v.id;
                    return (
                      <button
                        key={v.id}
                        onClick={() =>
                          setSelectedVariantId(isSelected ? null : v.id)
                        }
                        className={[
                          "px-3 py-1.5 rounded-xl text-sm font-medium border transition-all",
                          isSelected
                            ? "border-primary/40 bg-primary/10 text-link"
                            : outOfVariant
                              ? "border-border bg-surface text-muted-foreground cursor-pointer"
                              : "border-input bg-surface text-foreground hover:border-input hover:bg-muted",
                        ].join(" ")}
                      >
                        {label || `Variant ${v.id}`}
                        {outOfVariant && (
                          <span className="ml-1.5 text-muted-foreground text-xs">
                            (OOS)
                          </span>
                        )}
                      </button>
                    );
                  })}
                </div>
              </div>
            )}

            {/* Stock badge */}
            <div className="flex flex-wrap items-center gap-2">
              {unavailable ? (
                isPreorderEligible ? (
                  <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-primary/10 border border-primary/40 text-link">
                    Preorder
                  </span>
                ) : (
                  <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-danger-subtle border border-danger/40 text-danger">
                    Out of stock
                  </span>
                )
              ) : (
                <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-success-subtle border border-success/40 text-success">
                  <CheckCircle className="w-3.5 h-3.5" />
                  In stock
                  {effectiveStock !== null &&
                    effectiveStock <= 5 &&
                    effectiveStock > 0 && (
                      <span className="text-warning ml-0.5">
                        — only {effectiveStock} left
                      </span>
                    )}
                </span>
              )}
              {isPreorderEligible && preorderExpectedDate && (
                <span className="text-xs text-muted-foreground">
                  Expected {formatCalendarDate(preorderExpectedDate)}
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
              <p className="text-sm text-muted-foreground leading-relaxed line-clamp-4">
                {product.description}
              </p>
            )}

            {/* CTA area */}
            <div className="flex flex-col gap-3 mt-auto pt-2">
              {!unavailable ? (
                /* In stock → Add to Cart + Save to list */
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={handleAddToCart}
                    className="flex-1 flex items-center justify-center gap-2 py-3 rounded-full bg-primary hover:bg-primary-hover text-primary-foreground font-semibold text-sm transition-colors"
                  >
                    <ShoppingCart className="w-4 h-4" />
                    {"Add to cart"}
                  </button>
                  <button
                    type="button"
                    onClick={() =>
                      accessToken
                        ? setSaveOpen(true)
                        : router.push(loginUrlFor(window.location.pathname))
                    }
                    aria-label="Save to list"
                    className="flex items-center justify-center gap-1.5 px-4 py-3 rounded-full border border-input bg-surface text-foreground hover:bg-muted hover:text-link transition-colors text-sm font-semibold"
                  >
                    <Bookmark className="w-4 h-4" />
                    Save
                  </button>
                </div>
              ) : isPreorderEligible ? (
                /* Preorder-eligible → Preorder button + Save */
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={handleAddToCart}
                    className="flex-1 flex items-center justify-center gap-2 py-3 rounded-full border border-primary/40 bg-primary/10 text-link font-semibold text-sm transition-colors hover:bg-primary/20"
                  >
                    <Package className="w-4 h-4" />
                    {"Preorder"}
                  </button>
                  <button
                    type="button"
                    onClick={() =>
                      accessToken
                        ? setSaveOpen(true)
                        : router.push(loginUrlFor(window.location.pathname))
                    }
                    aria-label="Save to list"
                    className="flex items-center justify-center gap-1.5 px-4 py-3 rounded-full border border-input bg-surface text-foreground hover:bg-muted hover:text-link transition-colors text-sm font-semibold"
                  >
                    <Bookmark className="w-4 h-4" />
                    Save
                  </button>
                </div>
              ) : /* Out of stock → Notify Me */
              accessToken ? (
                <NotifyButton
                  hasNotification={hasNotification}
                  isLoading={
                    subscribeMutation.isPending || cancelMutation.isPending
                  }
                  onToggle={handleNotify}
                />
              ) : (
                <div className="rounded-xl border border-border bg-surface p-4 text-center">
                  <p className="text-muted-foreground text-sm mb-2">
                    <a
                      href="/login"
                      className="text-link hover:text-link underline underline-offset-2"
                    >
                      Sign in
                    </a>{" "}
                    to be notified when this is back in stock.
                  </p>
                </div>
              )}
            </div>

            {/* Watch price */}
            {accessToken && (
              <WatchPriceButton
                isWatching={isWatching}
                isLoading={watchMutation.isPending || unwatchMutation.isPending}
                onToggle={handleWatchToggle}
              />
            )}

            {/* Report link */}
            {accessToken && (
              <button
                type="button"
                onClick={() => setReportOpen(true)}
                className="mt-1 flex items-center gap-1.5 text-xs text-muted-foreground hover:text-danger transition-colors"
              >
                <Flag className="h-3 w-3" />
                Report this product
              </button>
            )}
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
            {product.tags
              .split(",")
              .map((tag) => tag.trim())
              .filter(Boolean)
              .map((tag) => (
                <span
                  key={tag}
                  className="px-3 py-1 rounded-full text-xs font-medium border border-border bg-surface text-muted-foreground"
                >
                  {tag}
                </span>
              ))}
          </motion.div>
        )}
      </div>

      <div className="relative z-10 mx-auto max-w-6xl">
        <ReviewsSection companyId={product.companyId} productId={product.id} />
      </div>

      <QASection productId={product.id} />

      <SaveToListModal
        open={saveOpen}
        onClose={() => setSaveOpen(false)}
        productId={product.id}
        productName={product.name}
      />
      <ReportModal
        open={reportOpen}
        onClose={() => setReportOpen(false)}
        targetType="PRODUCT"
        targetId={product.id}
        targetName={product.name}
      />
    </div>
  );
}

interface WatchPriceButtonProps {
  isWatching: boolean;
  isLoading: boolean;
  onToggle: () => void;
}

function WatchPriceButton({
  isWatching,
  isLoading,
  onToggle,
}: WatchPriceButtonProps) {
  if (isWatching) {
    return (
      <div className="flex items-center justify-between gap-2 px-4 py-2.5 rounded-xl border border-primary/40 bg-primary/10">
        <div className="flex items-center gap-2 text-link text-sm">
          <TrendingDown className="w-4 h-4" />
          Watching for price drops
        </div>
        <button
          onClick={onToggle}
          disabled={isLoading}
          className="text-xs text-muted-foreground hover:text-muted-foreground transition-colors disabled:opacity-40"
        >
          Unwatch
        </button>
      </div>
    );
  }

  return (
    <button
      onClick={onToggle}
      disabled={isLoading}
      className="flex items-center justify-center gap-2 w-full py-2.5 rounded-full border border-border bg-transparent hover:bg-muted text-muted-foreground hover:text-foreground text-sm transition-all disabled:opacity-40"
    >
      {isLoading ? (
        <div className="w-3.5 h-3.5 rounded-full border-2 border-input border-t-input animate-spin" />
      ) : (
        <TrendingDown className="w-3.5 h-3.5" />
      )}
      Watch for price drops
    </button>
  );
}

interface NotifyButtonProps {
  hasNotification: boolean;
  isLoading: boolean;
  onToggle: () => void;
}

function NotifyButton({
  hasNotification,
  isLoading,
  onToggle,
}: NotifyButtonProps) {
  if (hasNotification) {
    return (
      <div className="flex flex-col gap-2">
        <div className="flex items-center gap-2 px-4 py-3 rounded-xl border border-primary/40 bg-primary/10 text-link text-sm font-medium">
          <Bell className="w-4 h-4 fill-link" />
          You'll be notified when it's back
        </div>
        <button
          onClick={onToggle}
          disabled={isLoading}
          className="flex items-center justify-center gap-1.5 text-xs text-muted-foreground hover:text-muted-foreground transition-colors disabled:opacity-40"
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
      className="flex items-center justify-center gap-2 w-full py-3 rounded-full border border-input bg-transparent hover:bg-muted text-foreground font-semibold text-sm transition-all disabled:opacity-40"
    >
      {isLoading ? (
        <div className="w-4 h-4 rounded-full border-2 border-input border-t-input animate-spin" />
      ) : (
        <Bell className="w-4 h-4" />
      )}
      Notify me when available
    </button>
  );
}
