"use client";

import { useRef, type ReactNode } from "react";
import { cn } from "../../lib/cn";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "./Dialog";

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title?: string;
  /** Optional supporting text under the title. */
  description?: string;
  children: ReactNode;
  /** Tailwind max-width class, e.g. "max-w-md", "max-w-lg". Default: max-w-lg. */
  maxWidth?: string;
  /** Hide the default close button. */
  hideClose?: boolean;
}

/**
 * Convenience wrapper over Dialog for the common "controlled modal with a
 * title" case. Prefer composing Dialog parts directly for anything custom.
 */
export default function Modal({
  open,
  onClose,
  title,
  description,
  children,
  maxWidth = "max-w-lg",
  hideClose = false,
}: ModalProps) {
  // Radix only returns focus to a <DialogTrigger>. Modal is opened by the
  // caller's own controls, so remember what had focus and restore it.
  const returnFocusRef = useRef<HTMLElement | null>(null);
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) onClose();
      }}
    >
      <DialogContent
        hideClose={hideClose}
        className={cn("gap-0 p-0", maxWidth)}
        onOpenAutoFocus={() => {
          returnFocusRef.current =
            document.activeElement instanceof HTMLElement
              ? document.activeElement
              : null;
        }}
        onCloseAutoFocus={(event) => {
          const target = returnFocusRef.current;
          if (target?.isConnected) {
            event.preventDefault();
            target.focus();
          }
          returnFocusRef.current = null;
        }}
        // Without a description Radix warns unless this is explicitly unset.
        {...(description ? {} : { "aria-describedby": undefined })}
      >
        {title ? (
          <div className="border-b px-5 pb-3 pt-4 pr-12">
            <DialogTitle className="text-lg">{title}</DialogTitle>
            {description && (
              <DialogDescription className="mt-1">
                {description}
              </DialogDescription>
            )}
          </div>
        ) : (
          // Radix requires a title for screen readers.
          <DialogTitle className="sr-only">Dialog</DialogTitle>
        )}
        <div className="max-h-[80vh] overflow-y-auto px-5 py-4">{children}</div>
      </DialogContent>
    </Dialog>
  );
}
