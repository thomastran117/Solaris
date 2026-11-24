import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { Check, Minus, Plus, ShoppingBag, Trash2 } from "lucide-react";
import type { SavedListItem } from "../../types/savedList";

interface Props {
  item: SavedListItem;
  onUpdate: (changes: {
    quantity?: number;
    note?: string;
    purchased?: boolean;
  }) => void;
  onRemove: () => void;
  disabled?: boolean;
}

export default function SavedListItemRow({
  item,
  onUpdate,
  onRemove,
  disabled,
}: Props) {
  const [quantity, setQuantity] = useState(item.quantity);
  const [note, setNote] = useState(item.note ?? "");

  // Keep local state in sync if the server sends fresh data (e.g. after invalidation).
  useEffect(() => setQuantity(item.quantity), [item.quantity]);
  useEffect(() => setNote(item.note ?? ""), [item.note]);

  const noteTimer = useRef<number | null>(null);
  const qtyTimer = useRef<number | null>(null);

  function bumpQuantity(delta: number) {
    const next = Math.max(1, quantity + delta);
    if (next === quantity) return;
    setQuantity(next);
    if (qtyTimer.current) window.clearTimeout(qtyTimer.current);
    qtyTimer.current = window.setTimeout(
      () => onUpdate({ quantity: next }),
      350
    );
  }

  function handleNoteChange(e: React.ChangeEvent<HTMLInputElement>) {
    const v = e.target.value;
    setNote(v);
    if (noteTimer.current) window.clearTimeout(noteTimer.current);
    noteTimer.current = window.setTimeout(() => onUpdate({ note: v }), 600);
  }

  return (
    <div
      className={`rounded-xl border border-border bg-surface p-4 flex flex-col sm:flex-row gap-4 transition-opacity ${
        item.purchased ? "opacity-60" : "opacity-100"
      }`}
    >
      {/* Thumbnail */}
      <Link
        href={`/products/${item.productId}`}
        className="w-full sm:w-24 h-24 flex-shrink-0 rounded-xl overflow-hidden bg-surface flex items-center justify-center"
      >
        {item.productThumbnailUrl ? (
          <img
            src={item.productThumbnailUrl}
            alt={item.productName}
            className="w-full h-full object-cover"
          />
        ) : (
          <ShoppingBag className="w-8 h-8 text-muted-foreground" />
        )}
      </Link>

      {/* Body */}
      <div className="flex-1 min-w-0 flex flex-col gap-2">
        <div>
          <Link
            href={`/products/${item.productId}`}
            className={`block text-sm font-semibold text-foreground leading-snug hover:text-link transition-colors ${
              item.purchased ? "line-through" : ""
            }`}
          >
            {item.productName}
          </Link>
          {item.variantSku && (
            <p className="text-xs text-muted-foreground mt-0.5">
              SKU: {item.variantSku}
            </p>
          )}
        </div>

        <div className="flex flex-wrap items-center gap-3">
          {/* Quantity stepper */}
          <div className="inline-flex items-center rounded-full border border-border bg-surface">
            <button
              type="button"
              onClick={() => bumpQuantity(-1)}
              disabled={disabled || quantity <= 1}
              aria-label="Decrease quantity"
              className="p-1.5 text-muted-foreground hover:text-foreground disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
            >
              <Minus className="w-3.5 h-3.5" />
            </button>
            <span className="px-2 text-sm font-medium text-foreground tabular-nums w-7 text-center">
              {quantity}
            </span>
            <button
              type="button"
              onClick={() => bumpQuantity(1)}
              disabled={disabled}
              aria-label="Increase quantity"
              className="p-1.5 text-muted-foreground hover:text-foreground disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
            >
              <Plus className="w-3.5 h-3.5" />
            </button>
          </div>

          {/* Note input */}
          <input
            type="text"
            value={note}
            onChange={handleNoteChange}
            disabled={disabled}
            maxLength={500}
            placeholder="Add a note…"
            className="flex-1 min-w-[8rem] text-sm rounded-full border border-border bg-surface px-3 py-1.5 text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-ring"
          />
        </div>
      </div>

      {/* Actions */}
      <div className="flex sm:flex-col items-center sm:items-end gap-2">
        <button
          type="button"
          onClick={() => onUpdate({ purchased: !item.purchased })}
          disabled={disabled}
          aria-label={item.purchased ? "Mark as not done" : "Mark as done"}
          className={`flex items-center justify-center w-9 h-9 rounded-full border transition-all ${
            item.purchased
              ? "bg-success-subtle border-success/40 text-foreground"
              : "border-border bg-surface text-muted-foreground hover:text-foreground hover:border-input"
          }`}
        >
          <Check className="w-4 h-4" />
        </button>
        <button
          type="button"
          onClick={onRemove}
          disabled={disabled}
          aria-label="Remove item"
          className="flex items-center justify-center w-9 h-9 rounded-full border border-border bg-surface text-muted-foreground hover:text-danger hover:border-danger/40 transition-colors"
        >
          <Trash2 className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
}
