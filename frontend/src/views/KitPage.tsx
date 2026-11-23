"use client";

import { useMemo, useState } from "react";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { kitsApi } from "../api/kits";
import KitSlotCard from "../components/kit/KitSlotCard";
import KitPriceSummary from "../components/kit/KitPriceSummary";
import type { SlotSelectionState } from "../types/kit";
import { useAppDispatch, useAppSelector } from "../stores/hooks";
import { addLine, type KitSelection } from "../stores/cartSlice";
import { selectActiveMarketplaceId } from "../stores/marketplaceSlice";
import { useCartDrawer } from "../components/cart/CartDrawer";
import { useAnims } from "../hooks/useAnims";
import { kitKeys } from "../api/keys";

export default function KitPage() {
  const { companyId, kitId } = useParams<{
    companyId: string;
    kitId: string;
  }>();
  const { fadeInUp, stagger } = useAnims();

  const {
    data: kit,
    isLoading,
    isError,
  } = useQuery({
    queryKey: kitKeys.detail(companyId, kitId),
    queryFn: () => kitsApi.get(companyId!, kitId!).then((r) => r.data),
    enabled: !!companyId && !!kitId,
  });

  const [selections, setSelections] = useState<
    Record<string, SlotSelectionState>
  >({});

  // Initialise default selections once the kit loads (runs once per kit load)
  const [initialised, setInitialised] = useState(false);
  if (kit && !initialised) {
    const defaults: Record<string, SlotSelectionState> = {};
    for (const slot of kit.slots) {
      const defaultChoice = slot.choices.find(
        (c) => c.defaultChoice && c.inStock
      );
      defaults[slot.id] = defaultChoice
        ? {
            status: "selected",
            choiceId: defaultChoice.id,
            quantity: slot.minQty,
          }
        : { status: "empty" };
    }
    setSelections(defaults);
    setInitialised(true);
  }

  const allRequiredFilled = useMemo(() => {
    if (!kit) return false;
    return kit.slots.every((slot) => {
      if (!slot.required) return true;
      const sel = selections[slot.id];
      return sel?.status === "selected";
    });
  }, [kit, selections]);

  const dispatch = useAppDispatch();
  const marketplaceId = useAppSelector(selectActiveMarketplaceId);
  const { openCart } = useCartDrawer();

  function addKitToCart() {
    if (!kit || !marketplaceId) return;
    const kitSelections: KitSelection[] = [];
    const summary: string[] = [];
    let unitPrice = 0;
    for (const slot of kit.slots) {
      const sel = selections[slot.id];
      if (sel?.status !== "selected") continue;
      const choice = slot.choices.find((c) => c.id === sel.choiceId);
      if (!choice) continue;
      kitSelections.push({
        slotId: slot.id,
        productId: choice.productId,
        variantId: choice.variantId,
        quantity: sel.quantity,
      });
      unitPrice += choice.effectivePrice * sel.quantity;
      summary.push(
        `${slot.name}: ${choice.productName}${choice.variantTitle ? ` (${choice.variantTitle})` : ""}${sel.quantity > 1 ? ` ×${sel.quantity}` : ""}`
      );
    }
    if (kitSelections.length === 0) return;
    dispatch(
      addLine({
        kind: "kit",
        kitId: kit.id,
        selections: kitSelections,
        summary: summary.join(" · "),
        name: kit.name,
        thumbnailUrl: kit.thumbnailUrl,
        // Estimate from the configured choices; the order is priced server-side.
        price: unitPrice,
        currency: kit.currency.toUpperCase(),
        quantity: 1,
        marketplaceId,
        companyId: kit.companyId,
      })
    );
    openCart();
  }

  function handleSelect(slotId: string, choiceId: string, quantity: number) {
    setSelections((prev) => ({
      ...prev,
      [slotId]: choiceId
        ? { status: "selected", choiceId, quantity }
        : { status: "empty" },
    }));
  }

  if (isLoading) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center">
        <div className="w-8 h-8 rounded-full border-2 border-primary/40 border-t-primary animate-spin" />
      </div>
    );
  }

  if (isError || !kit) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center">
        <p className="text-muted-foreground">Kit not found.</p>
      </div>
    );
  }

  return (
    <div className="bg-background pt-20 pb-32 px-4 md:px-6">
      <div className="max-w-6xl mx-auto">
        {/* Header */}
        <motion.div
          initial="hidden"
          animate="visible"
          variants={fadeInUp}
          className="mb-10"
        >
          {kit.thumbnailUrl && (
            <img
              src={kit.thumbnailUrl}
              alt={kit.name}
              className="w-20 h-20 rounded-xl object-cover border border-border mb-5"
            />
          )}
          <p className="text-xs font-semibold text-link mb-2">
            Configurable Kit
          </p>
          <h1 className="text-3xl md:text-5xl font-semibold tracking-tight text-foreground">
            {kit.name}
          </h1>
          {kit.description && (
            <p className="mt-3 text-base text-muted-foreground leading-relaxed max-w-2xl">
              {kit.description}
            </p>
          )}
          <p className="mt-3 text-sm text-muted-foreground">
            Configure starting from{" "}
            <span className="text-foreground font-semibold">
              {new Intl.NumberFormat("en-US", {
                style: "currency",
                currency: kit.currency,
              }).format(kit.computedMinPrice)}
            </span>
          </p>
        </motion.div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Slot cards */}
          <motion.div
            initial="hidden"
            whileInView="visible"
            viewport={{ once: true, amount: 0.1 }}
            variants={stagger}
            className="lg:col-span-2 flex flex-col gap-4"
          >
            {kit.slots.map((slot) => (
              <KitSlotCard
                key={slot.id}
                slot={slot}
                selection={selections[slot.id] ?? { status: "empty" }}
                onSelect={(choiceId, qty) =>
                  handleSelect(slot.id, choiceId, qty)
                }
              />
            ))}
          </motion.div>

          {/* Price summary (sticky) */}
          <div className="lg:col-span-1">
            <KitPriceSummary
              slots={kit.slots}
              selections={selections}
              currency={kit.currency}
              compareAtPrice={kit.compareAtPrice}
              allRequiredFilled={allRequiredFilled}
              purchasable={kit.purchasable}
              onAddToCart={addKitToCart}
            />
          </div>
        </div>
      </div>
    </div>
  );
}
