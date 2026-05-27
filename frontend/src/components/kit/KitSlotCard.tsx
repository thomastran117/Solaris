import { motion, useReducedMotion, type Variants } from "framer-motion";
import { Check, Minus, Plus } from "lucide-react";
import type { KitSlot, KitSlotChoice, SlotSelectionState } from "../../types/kit";

interface Props {
  slot: KitSlot;
  selection: SlotSelectionState;
  onSelect: (choiceId: string, quantity: number) => void;
}

function formatPrice(amount: number, currency: string) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency }).format(amount);
}

export default function KitSlotCard({ slot, selection, onSelect }: Props) {
  const prefersReducedMotion = useReducedMotion();
  const fadeInUp: Variants = prefersReducedMotion
    ? { hidden: { opacity: 0 }, visible: { opacity: 1 } }
    : { hidden: { opacity: 0, y: 12 }, visible: { opacity: 1, y: 0, transition: { duration: 0.4 } } };

  const selectedChoiceId = selection.status === "selected" ? selection.choiceId : null;
  const selectedQty = selection.status === "selected" ? selection.quantity : slot.minQty;

  function handleChoiceClick(choice: KitSlotChoice) {
    if (!choice.inStock) return;
    if (selectedChoiceId === choice.id) {
      // Deselect optional slots
      if (!slot.required) onSelect("", 0);
    } else {
      onSelect(choice.id, slot.minQty);
    }
  }

  function handleQtyChange(delta: number) {
    if (selection.status !== "selected") return;
    const next = selectedQty + delta;
    if (next < slot.minQty || next > slot.maxQty) return;
    onSelect(selection.choiceId, next);
  }

  return (
    <motion.div
      variants={fadeInUp}
      className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-5 flex flex-col gap-4"
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-white">{slot.name}</p>
          {slot.description && (
            <p className="text-xs text-white/60 mt-0.5 leading-relaxed">{slot.description}</p>
          )}
        </div>
        <span className={`text-xs px-2 py-0.5 rounded-full border font-semibold shrink-0 ${
          slot.required
            ? "bg-blue-500/15 border-blue-400/30 text-sky-200"
            : "bg-white/[0.06] border-white/10 text-white/50"
        }`}>
          {slot.required ? "Required" : "Optional"}
        </span>
      </div>

      <div className="flex flex-col gap-2">
        {slot.choices.map(choice => {
          const isSelected = selectedChoiceId === choice.id;
          return (
            <button
              key={choice.id}
              onClick={() => handleChoiceClick(choice)}
              disabled={!choice.inStock}
              className={`flex items-center gap-3 rounded-xl border px-4 py-3 text-left transition-all ${
                isSelected
                  ? "border-blue-500/60 bg-blue-500/10"
                  : choice.inStock
                  ? "border-white/10 bg-white/[0.04] hover:border-white/20 hover:bg-white/[0.06]"
                  : "border-white/[0.06] bg-white/[0.02] opacity-50 cursor-not-allowed"
              }`}
            >
              <div className={`w-4 h-4 rounded-full border-2 flex items-center justify-center shrink-0 ${
                isSelected ? "border-blue-400 bg-blue-500" : "border-white/30"
              }`}>
                {isSelected && <Check className="w-2.5 h-2.5 text-white" strokeWidth={3} />}
              </div>

              {choice.productThumbnailUrl && (
                <img
                  src={choice.productThumbnailUrl}
                  alt={choice.productName}
                  className="w-8 h-8 rounded-lg object-cover border border-white/10 shrink-0"
                />
              )}

              <div className="flex-1 min-w-0">
                <p className="text-sm text-white truncate">{choice.productName}</p>
                {choice.variantTitle && (
                  <p className="text-xs text-white/55">{choice.variantTitle}</p>
                )}
                {!choice.inStock && (
                  <p className="text-xs text-red-400/80">Out of stock</p>
                )}
              </div>

              <div className="text-right shrink-0">
                <p className="text-sm font-semibold text-white">
                  {formatPrice(choice.effectivePrice, "USD")}
                </p>
                {choice.priceDelta !== null && choice.priceDelta !== 0 && (
                  <p className="text-xs text-white/50">
                    {choice.priceDelta > 0 ? "+" : ""}{formatPrice(choice.priceDelta, "USD")}
                  </p>
                )}
              </div>
            </button>
          );
        })}
      </div>

      {/* Quantity stepper — only shown when slot allows qty > 1 and something is selected */}
      {slot.maxQty > 1 && selectedChoiceId && (
        <div className="flex items-center gap-3 pt-1">
          <span className="text-xs text-white/60">Quantity</span>
          <div className="flex items-center gap-2">
            <button
              onClick={() => handleQtyChange(-1)}
              disabled={selectedQty <= slot.minQty}
              className="w-7 h-7 rounded-lg border border-white/10 bg-white/[0.06] flex items-center justify-center text-white/70 hover:bg-white/10 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
            >
              <Minus className="w-3 h-3" />
            </button>
            <span className="text-sm font-semibold text-white w-5 text-center">{selectedQty}</span>
            <button
              onClick={() => handleQtyChange(1)}
              disabled={selectedQty >= slot.maxQty}
              className="w-7 h-7 rounded-lg border border-white/10 bg-white/[0.06] flex items-center justify-center text-white/70 hover:bg-white/10 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
            >
              <Plus className="w-3 h-3" />
            </button>
          </div>
        </div>
      )}
    </motion.div>
  );
}
