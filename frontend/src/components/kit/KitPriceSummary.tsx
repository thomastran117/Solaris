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
  return new Intl.NumberFormat("en-US", { style: "currency", currency }).format(
    amount
  );
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
  const selectedLines: {
    slotName: string;
    choiceName: string;
    qty: number;
    price: number;
  }[] = [];
  let total = 0;

  for (const slot of slots) {
    const sel = selections[slot.id];
    if (sel?.status !== "selected") continue;
    const choice = slot.choices.find((c) => c.id === sel.choiceId);
    if (!choice) continue;
    const lineTotal = choice.effectivePrice * sel.quantity;
    total += lineTotal;
    selectedLines.push({
      slotName: slot.name,
      choiceName:
        choice.productName +
        (choice.variantTitle ? ` — ${choice.variantTitle}` : ""),
      qty: sel.quantity,
      price: lineTotal,
    });
  }

  const canAdd = allRequiredFilled && purchasable;

  return (
    <div className="rounded-xl border border-border bg-surface p-5 flex flex-col gap-4 sticky top-24">
      <p className="text-xs font-semibold text-link">Your configuration</p>

      {selectedLines.length === 0 ? (
        <p className="text-xs text-muted-foreground italic">
          No components selected yet.
        </p>
      ) : (
        <div className="flex flex-col gap-2">
          {selectedLines.map((line, i) => (
            <div
              key={i}
              className="flex items-start justify-between gap-3 text-xs"
            >
              <div className="min-w-0">
                <p className="text-muted-foreground">{line.slotName}</p>
                <p className="text-foreground truncate">{line.choiceName}</p>
                {line.qty > 1 && (
                  <p className="text-muted-foreground">× {line.qty}</p>
                )}
              </div>
              <p className="text-foreground font-semibold shrink-0">
                {formatPrice(line.price, currency)}
              </p>
            </div>
          ))}
        </div>
      )}

      <div className="border-t border-border pt-3 flex items-end justify-between gap-2">
        <div>
          <p className="text-xs text-muted-foreground">Total</p>
          <p className="text-2xl font-semibold text-foreground">
            {formatPrice(total, currency)}
          </p>
          {compareAtPrice !== null && compareAtPrice > total && (
            <p className="text-xs text-muted-foreground line-through">
              {formatPrice(compareAtPrice, currency)}
            </p>
          )}
        </div>
      </div>

      <button
        onClick={onAddToCart}
        disabled={!canAdd || addingToCart}
        className={`w-full rounded-full py-3 text-sm font-semibold transition-all ${
          canAdd
            ? "bg-primary hover:bg-primary-hover text-primary-foreground"
            : "bg-surface text-muted-foreground cursor-not-allowed border border-border"
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
