"use client";

import Link from "next/link";
import { Minus, Package, Plus, Trash2 } from "lucide-react";
import { useAppDispatch } from "../../stores/hooks";
import {
  MAX_LINE_QUANTITY,
  lineTotal,
  removeLine,
  setQuantity,
  type CartLine,
} from "../../stores/cartSlice";
import { formatMoney } from "../../lib/format";
import { cn } from "../../lib/cn";
import { IconButton } from "../ui/IconButton";

function lineHref(line: CartLine): string {
  return line.kind === "product"
    ? `/products/${line.productId}`
    : `/kits/${line.companyId}/${line.kitId}`;
}

/** One cart line with quantity controls. Used by the drawer and the cart page. */
export function CartLineItem({
  line,
  compact = false,
  onNavigate,
}: {
  line: CartLine;
  /** Smaller thumbnail for the drawer. */
  compact?: boolean;
  /** Called when the line's link is followed (the drawer closes itself). */
  onNavigate?: () => void;
}) {
  const dispatch = useAppDispatch();
  const detail = line.kind === "product" ? line.variantLabel : line.summary;

  return (
    <li className="flex gap-4 py-4">
      <Link
        href={lineHref(line)}
        onClick={onNavigate}
        className={cn(
          "shrink-0 overflow-hidden rounded-lg border bg-muted",
          compact ? "size-16" : "size-20 sm:size-24"
        )}
        tabIndex={-1}
        aria-hidden
      >
        {line.thumbnailUrl ? (
          <img
            src={line.thumbnailUrl}
            alt=""
            className="size-full object-cover"
          />
        ) : (
          <span className="flex size-full items-center justify-center">
            <Package className="size-6 text-muted-foreground" />
          </span>
        )}
      </Link>

      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <Link
              href={lineHref(line)}
              onClick={onNavigate}
              className="line-clamp-2 text-sm font-medium text-foreground hover:underline"
            >
              {line.name}
            </Link>
            {detail && (
              <p className="mt-0.5 line-clamp-2 text-xs text-muted-foreground">
                {line.kind === "kit" && (
                  <span className="font-medium">Kit · </span>
                )}
                {detail}
              </p>
            )}
          </div>
          <p className="shrink-0 text-sm font-semibold tabular-nums text-foreground">
            {formatMoney(lineTotal(line), line.currency)}
          </p>
        </div>

        <div className="mt-auto flex items-center justify-between gap-3 pt-1">
          <div
            className="flex items-center rounded-lg border"
            role="group"
            aria-label={`Quantity of ${line.name}`}
          >
            <IconButton
              size="icon-sm"
              aria-label="Decrease quantity"
              disabled={line.quantity <= 1}
              onClick={() =>
                dispatch(
                  setQuantity({
                    lineId: line.lineId,
                    quantity: line.quantity - 1,
                  })
                )
              }
            >
              <Minus aria-hidden />
            </IconButton>
            <span
              className="w-8 text-center text-sm tabular-nums"
              aria-live="polite"
            >
              {line.quantity}
            </span>
            <IconButton
              size="icon-sm"
              aria-label="Increase quantity"
              disabled={line.quantity >= MAX_LINE_QUANTITY}
              onClick={() =>
                dispatch(
                  setQuantity({
                    lineId: line.lineId,
                    quantity: line.quantity + 1,
                  })
                )
              }
            >
              <Plus aria-hidden />
            </IconButton>
          </div>
          <IconButton
            size="icon-sm"
            aria-label={`Remove ${line.name}`}
            onClick={() => dispatch(removeLine(line.lineId))}
          >
            <Trash2 aria-hidden />
          </IconButton>
        </div>
      </div>
    </li>
  );
}
