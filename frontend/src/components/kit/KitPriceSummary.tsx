import type { KitSlot, SlotSelectionState } from "../../types/kit";

interface Props {
  slots: KitSlot[];
  selections: Record<string, SlotSelectionState>;
  currency: string;
  compareAtPrice: number | null;
  allRequiredFilled: boolean;
  purchasable: boolean;
  onAddToCart: () => void;
  addingToCart?: boolean;
}

function formatPrice(amount: number, currency: string) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency }).format(amount);
}

export default function KitPriceSummary({
  slots,
  selections,
  currency,
  compareAtPrice,
  allRequiredFilled,
  purchasable,
  onAddToCart,
  addingToCart,
}: Props) {
  const selectedLines: { slotName: string; choiceName: string; qty: number; price: number }[] = [];
  let total = 0;

  for (const slot of slots) {
    const sel = selections[slot.id];
    if (sel?.status !== "selected") continue;
    const choice = slot.choices.find(c => c.id === sel.choiceId);
    if (!choice) continue;
    const lineTotal = choice.effectivePrice * sel.quantity;
    total += lineTotal;
    selectedLines.push({
      slotName: slot.name,
      choiceName: choice.productName + (choice.variantTitle ? ` — ${choice.variantTitle}` : ""),
      qty: sel.quantity,
      price: lineTotal,
    });
  }

  const canAdd = allRequiredFilled && purchasable;

  return (
    <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-5 flex flex-col gap-4 sticky top-24">
      <p className="text-xs uppercase tracking-[0.2em] font-semibold text-sky-200/90">Your configuration</p>

      {selectedLines.length === 0 ? (
        <p className="text-xs text-white/45 italic">No components selected yet.</p>
      ) : (
        <div className="flex flex-col gap-2">
          {selectedLines.map((line, i) => (
            <div key={i} className="flex items-start justify-between gap-3 text-xs">
              <div className="min-w-0">
                <p className="text-white/55">{line.slotName}</p>
                <p className="text-white/80 truncate">{line.choiceName}</p>
                {line.qty > 1 && <p className="text-white/45">× {line.qty}</p>}
              </div>
              <p className="text-white font-semibold shrink-0">{formatPrice(line.price, currency)}</p>
            </div>
          ))}
        </div>
      )}

      <div className="border-t border-white/10 pt-3 flex items-end justify-between gap-2">
        <div>
          <p className="text-xs text-white/55">Total</p>
          <p className="text-2xl font-extrabold text-white">{formatPrice(total, currency)}</p>
          {compareAtPrice !== null && compareAtPrice > total && (
            <p className="text-xs text-white/40 line-through">{formatPrice(compareAtPrice, currency)}</p>
          )}
        </div>
      </div>

      <button
        onClick={onAddToCart}
        disabled={!canAdd || addingToCart}
        className={`w-full rounded-full py-3 text-sm font-semibold transition-all ${
          canAdd
            ? "bg-blue-600 hover:bg-blue-500 text-white"
            : "bg-white/[0.06] text-white/30 cursor-not-allowed border border-white/10"
        }`}
      >
        {addingToCart
          ? "Adding…"
          : !purchasable
          ? "Not available"
          : !allRequiredFilled
          ? "Select required components"
          : "Add to Cart"}
      </button>
    </div>
  );
}
