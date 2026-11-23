import { forwardRef, type LabelHTMLAttributes } from "react";
import { cn } from "../../lib/cn";

interface LabelProps extends LabelHTMLAttributes<HTMLLabelElement> {
  /** Appends a muted "(optional)" marker. */
  optional?: boolean;
}

export const Label = forwardRef<HTMLLabelElement, LabelProps>(
  ({ className, optional, children, ...props }, ref) => (
    <label
      ref={ref}
      className={cn(
        "text-sm font-medium leading-none text-foreground",
        className
      )}
      {...props}
    >
      {children}
      {optional && (
        <span className="ml-1 font-normal text-muted-foreground">
          (optional)
        </span>
      )}
    </label>
  )
);
Label.displayName = "Label";
