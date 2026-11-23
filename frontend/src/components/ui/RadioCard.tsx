import { forwardRef, type InputHTMLAttributes, type ReactNode } from "react";
import { cn } from "../../lib/cn";

interface RadioCardProps extends Omit<
  InputHTMLAttributes<HTMLInputElement>,
  "type" | "title"
> {
  title: ReactNode;
  description?: ReactNode;
  /** Right-aligned detail, e.g. a price. */
  aside?: ReactNode;
}

/**
 * A native radio styled as a selectable card, for choices that need detail:
 * addresses, shipping rates, fulfilment methods. Group several with the same
 * `name` inside a `<fieldset>` with a `<legend>`.
 */
export const RadioCard = forwardRef<HTMLInputElement, RadioCardProps>(
  ({ className, title, description, aside, disabled, ...props }, ref) => (
    <label
      className={cn(
        "flex cursor-pointer items-start gap-3 rounded-xl border bg-surface p-4 transition-colors",
        "hover:bg-muted/60 has-[:checked]:border-primary has-[:checked]:ring-1 has-[:checked]:ring-primary",
        "has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-ring",
        disabled && "cursor-not-allowed opacity-50",
        className
      )}
    >
      <input
        ref={ref}
        type="radio"
        disabled={disabled}
        className="mt-0.5 size-4 shrink-0 accent-primary focus-visible:outline-none"
        {...props}
      />
      <span className="grid flex-1 gap-0.5 text-sm">
        <span className="font-medium text-foreground">{title}</span>
        {description && (
          <span className="text-muted-foreground">{description}</span>
        )}
      </span>
      {aside && (
        <span className="text-sm font-medium text-foreground">{aside}</span>
      )}
    </label>
  )
);
RadioCard.displayName = "RadioCard";
