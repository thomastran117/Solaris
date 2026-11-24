"use client";

import {
  forwardRef,
  type ComponentPropsWithoutRef,
  type ReactNode,
} from "react";
import { Dialog as DialogPrimitive } from "radix-ui";
import { X } from "lucide-react";
import { cn } from "../../lib/cn";
import { DialogOverlay } from "./Dialog";

/** A Dialog that slides in from a screen edge: cart drawer, mobile nav, filters. */
export const Sheet = DialogPrimitive.Root;
export const SheetTrigger = DialogPrimitive.Trigger;
export const SheetClose = DialogPrimitive.Close;
export const SheetTitle = DialogPrimitive.Title;
export const SheetDescription = DialogPrimitive.Description;

const sideClasses = {
  right:
    "inset-y-0 right-0 border-l data-[state=open]:animate-slide-in-right data-[state=closed]:animate-slide-out-right",
  left: "inset-y-0 left-0 border-r data-[state=open]:animate-slide-in-left data-[state=closed]:animate-slide-out-left",
} as const;

interface SheetContentProps extends ComponentPropsWithoutRef<
  typeof DialogPrimitive.Content
> {
  side?: keyof typeof sideClasses;
  hideClose?: boolean;
}

export const SheetContent = forwardRef<HTMLDivElement, SheetContentProps>(
  ({ side = "right", className, children, hideClose, ...props }, ref) => (
    <DialogPrimitive.Portal>
      <DialogOverlay />
      <DialogPrimitive.Content
        ref={ref}
        className={cn(
          "fixed z-50 flex h-full w-full max-w-md flex-col bg-surface-raised text-foreground shadow-xl",
          "focus:outline-none motion-reduce:animate-none",
          sideClasses[side],
          className
        )}
        {...props}
      >
        {children}
        {!hideClose && (
          <DialogPrimitive.Close
            className={cn(
              "absolute right-4 top-4 rounded-md p-1 text-muted-foreground transition-colors",
              "hover:bg-muted hover:text-foreground",
              "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            )}
            aria-label="Close"
          >
            <X className="size-5" aria-hidden />
          </DialogPrimitive.Close>
        )}
      </DialogPrimitive.Content>
    </DialogPrimitive.Portal>
  )
);
SheetContent.displayName = "SheetContent";

export function SheetHeader({
  className,
  children,
}: {
  className?: string;
  children: ReactNode;
}) {
  return (
    <div
      className={cn("flex flex-col gap-1 border-b px-6 py-4 pr-12", className)}
    >
      {children}
    </div>
  );
}

export function SheetBody({
  className,
  children,
}: {
  className?: string;
  children: ReactNode;
}) {
  return (
    <div className={cn("flex-1 overflow-y-auto px-6 py-4", className)}>
      {children}
    </div>
  );
}

export function SheetFooter({
  className,
  children,
}: {
  className?: string;
  children: ReactNode;
}) {
  return <div className={cn("border-t px-6 py-4", className)}>{children}</div>;
}
