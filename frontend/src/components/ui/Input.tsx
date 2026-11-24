import { forwardRef, type InputHTMLAttributes } from "react";
import { cn } from "../../lib/cn";

/** Shared by Input, Textarea and Select so every text control looks the same. */
export const controlClasses = cn(
  "w-full rounded-lg border border-input bg-surface px-3 text-sm text-foreground shadow-xs",
  "placeholder:text-muted-foreground",
  "transition-colors focus-visible:outline-none focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/30",
  "disabled:cursor-not-allowed disabled:opacity-50",
  "aria-[invalid=true]:border-danger aria-[invalid=true]:focus-visible:ring-danger/30"
);

export const Input = forwardRef<
  HTMLInputElement,
  InputHTMLAttributes<HTMLInputElement>
>(({ className, type = "text", ...props }, ref) => (
  <input
    ref={ref}
    type={type}
    className={cn(
      controlClasses,
      "h-10 file:mr-3 file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-foreground",
      className
    )}
    {...props}
  />
));
Input.displayName = "Input";
