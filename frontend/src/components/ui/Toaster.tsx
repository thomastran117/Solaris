"use client";

import { Toaster as Sonner } from "sonner";
import { useTheme } from "../theme/ThemeProvider";

/**
 * Mounted once in the root providers. Raise toasts with `toast` from
 * `components/ui/toast`. Use toasts for confirmations of completed actions;
 * keep validation and load errors inline next to what failed.
 */
export function Toaster() {
  const { resolved } = useTheme();
  return (
    <Sonner
      theme={resolved}
      position="bottom-right"
      closeButton
      toastOptions={{
        classNames: {
          toast:
            "rounded-xl! border! border-border! bg-surface-raised! text-foreground! shadow-lg!",
          description: "text-muted-foreground!",
          actionButton: "bg-primary! text-primary-foreground!",
          cancelButton: "bg-muted! text-foreground!",
        },
      }}
    />
  );
}
