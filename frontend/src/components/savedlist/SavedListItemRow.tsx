import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { Check, Minus, Plus, ShoppingBag, Trash2 } from "lucide-react";
import type { SavedListItem } from "../../types/savedList";

interface Props {
  item: SavedListItem;
  onUpdate: (changes: { quantity?: number; note?: string; purchased?: boolean }) => void;
  onRemove: () => void;
  disabled?: boolean;
}

export default function SavedListItemRow({ item, onUpdate, onRemove, disabled }: Props) {
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
    qtyTimer.current = window.setTimeout(() => onUpdate({ quantity: next }), 350);
  }

  function handleNoteChange(e: React.ChangeEvent<HTMLInputElement>) {
    const v = e.target.value;
    setNote(v);
    if (noteTimer.current) window.clearTimeout(noteTimer.current);
    noteTimer.current = window.setTimeout(() => onUpdate({ note: v }), 600);
  }

  return (
    <div
      className={`rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-4 flex flex-col sm:flex-row gap-4 transition-opacity ${
        item.purchased ? "opacity-60" : "opacity-100"
      }`}
    >
      {/* Thumbnail */}
      <Link
        to={`/products/${item.productId}`}
        className="w-full sm:w-24 h-24 flex-shrink-0 rounded-xl overflow-hidden bg-white/[0.04] flex items-center justify-center"
      >
        {item.productThumbnailUrl ? (
          <img
            src={item.productThumbnailUrl}
            alt={item.productName}
            className="w-full h-full object-cover"
          />
        ) : (
          <ShoppingBag className="w-8 h-8 text-white/20" />
        )}
      </Link>

      {/* Body */}
      <div className="flex-1 min-w-0 flex flex-col gap-2">
        <div>
          <Link
            to={`/products/${item.productId}`}
            className={`block text-sm font-semibold text-white leading-snug hover:text-sky-200 transition-colors ${
              item.purchased ? "line-through" : ""
            }`}
          >
            {item.productName}
          </Link>
          {item.variantSku && (
            <p className="text-xs text-white/50 mt-0.5">SKU: {item.variantSku}</p>
          )}
        </div>

        <div className="flex flex-wrap items-center gap-3">
          {/* Quantity stepper */}
          <div className="inline-flex items-center rounded-full border border-white/10 bg-white/[0.04]">
            <button
              type="button"
              onClick={() => bumpQuantity(-1)}
              disabled={disabled || quantity <= 1}
              aria-label="Decrease quantity"
              className="p-1.5 text-white/70 hover:text-white disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
            >
              <Minus className="w-3.5 h-3.5" />
            </button>
            <span className="px-2 text-sm font-medium text-white tabular-nums w-7 text-center">
              {quantity}
            </span>
            <button
              type="button"
              onClick={() => bumpQuantity(1)}
              disabled={disabled}
              aria-label="Increase quantity"
              className="p-1.5 text-white/70 hover:text-white disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
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
            className="flex-1 min-w-[8rem] text-sm rounded-full border border-white/10 bg-white/[0.04] px-3 py-1.5 text-white/85 placeholder:text-white/45 focus:outline-none focus:border-white/25"
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
              ? "bg-green-500 border-green-400 text-white"
              : "border-white/15 bg-white/[0.04] text-white/40 hover:text-white hover:border-white/30"
          }`}
        >
          <Check className="w-4 h-4" />
        </button>
        <button
          type="button"
          onClick={onRemove}
          disabled={disabled}
          aria-label="Remove item"
          className="flex items-center justify-center w-9 h-9 rounded-full border border-white/10 bg-white/[0.04] text-white/50 hover:text-red-300 hover:border-red-400/40 transition-colors"
        >
          <Trash2 className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
}
