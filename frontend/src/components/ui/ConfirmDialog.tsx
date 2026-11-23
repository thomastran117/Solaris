"use client";

import type { ReactNode } from "react";
import { AlertDialog } from "radix-ui";
import { cn } from "../../lib/cn";
import { buttonVariants } from "./Button";
import { overlayClasses } from "./Dialog";
import { Spinner } from "./Spinner";

interface ConfirmDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: ReactNode;
  description?: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  /** Style the confirm button as destructive (delete, cancel order, …). */
  destructive?: boolean;
  /** Keeps the dialog open with a busy confirm button while the action runs. */
  loading?: boolean;
  onConfirm: () => void;
}

/**
 * Confirmation for destructive or irreversible actions; replaces
 * `window.confirm`. Built on Radix AlertDialog, so focus starts on Cancel and
 * clicking outside does not dismiss it.
 */
export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel = "Confirm",
  cancelLabel = "Cancel",
  destructive = false,
  loading = false,
  onConfirm,
}: ConfirmDialogProps) {
  return (
    <AlertDialog.Root open={open} onOpenChange={onOpenChange}>
      <AlertDialog.Portal>
        <AlertDialog.Overlay className={overlayClasses} />
        <AlertDialog.Content
          className={cn(
            "fixed left-1/2 top-1/2 z-50 grid w-[calc(100%-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2 gap-4",
            "rounded-xl border bg-surface-raised p-6 text-foreground shadow-lg focus:outline-none",
            "data-[state=open]:animate-zoom-in data-[state=closed]:animate-zoom-out motion-reduce:animate-none"
          )}
        >
          <div className="space-y-1.5">
            <AlertDialog.Title className="text-lg font-semibold tracking-tight">
              {title}
            </AlertDialog.Title>
            {description && (
              <AlertDialog.Description className="text-sm text-muted-foreground">
                {description}
              </AlertDialog.Description>
            )}
          </div>
          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <AlertDialog.Cancel
              className={buttonVariants({ variant: "outline" })}
              disabled={loading}
            >
              {cancelLabel}
            </AlertDialog.Cancel>
            <AlertDialog.Action
              className={buttonVariants({
                variant: destructive ? "destructive" : "primary",
              })}
              disabled={loading}
              aria-busy={loading || undefined}
              onClick={(event) => {
                // Let the caller close the dialog when the action settles.
                event.preventDefault();
                onConfirm();
              }}
            >
              {loading && <Spinner />}
              {confirmLabel}
            </AlertDialog.Action>
          </div>
        </AlertDialog.Content>
      </AlertDialog.Portal>
    </AlertDialog.Root>
  );
}
