import { forwardRef, type InputHTMLAttributes, type ReactNode } from "react";
import { cn } from "../../lib/cn";

interface CheckboxProps extends Omit<
  InputHTMLAttributes<HTMLInputElement>,
  "type"
> {
  label?: ReactNode;
  description?: ReactNode;
}

/** Native checkbox. With `label`, renders a clickable label row. */
export const Checkbox = forwardRef<HTMLInputElement, CheckboxProps>(
  ({ className, label, description, ...props }, ref) => {
    const input = (
      <input
        ref={ref}
        type="checkbox"
        className={cn(
          "size-4 shrink-0 rounded border-input accent-primary",
          "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background",
          "disabled:cursor-not-allowed disabled:opacity-50",
          label ? "mt-0.5" : className
        )}
        {...props}
      />
    );
    if (!label) return input;
    return (
      <label
        className={cn(
          "flex cursor-pointer items-start gap-3 text-sm",
          className
        )}
      >
        {input}
        <span className="grid gap-0.5">
          <span className="font-medium text-foreground">{label}</span>
          {description && (
            <span className="text-muted-foreground">{description}</span>
          )}
        </span>
      </label>
    );
  }
);
Checkbox.displayName = "Checkbox";
