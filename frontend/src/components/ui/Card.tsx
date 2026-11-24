import { forwardRef, type HTMLAttributes } from "react";
import { cn } from "../../lib/cn";

type DivProps = HTMLAttributes<HTMLDivElement>;

export const Card = forwardRef<HTMLDivElement, DivProps>(
  ({ className, ...props }, ref) => (
    <div
      ref={ref}
      className={cn(
        "rounded-xl border bg-surface text-foreground shadow-xs",
        className
      )}
      {...props}
    />
  )
);
Card.displayName = "Card";

export const CardHeader = forwardRef<HTMLDivElement, DivProps>(
  ({ className, ...props }, ref) => (
    <div
      ref={ref}
      className={cn("flex flex-col gap-1 p-5 pb-0 sm:p-6 sm:pb-0", className)}
      {...props}
    />
  )
);
CardHeader.displayName = "CardHeader";

export const CardTitle = forwardRef<
  HTMLHeadingElement,
  HTMLAttributes<HTMLHeadingElement>
>(({ className, ...props }, ref) => (
  <h3
    ref={ref}
    className={cn(
      "text-base font-semibold leading-tight tracking-tight",
      className
    )}
    {...props}
  />
));
CardTitle.displayName = "CardTitle";

export const CardDescription = forwardRef<
  HTMLParagraphElement,
  HTMLAttributes<HTMLParagraphElement>
>(({ className, ...props }, ref) => (
  <p
    ref={ref}
    className={cn("text-sm text-muted-foreground", className)}
    {...props}
  />
));
CardDescription.displayName = "CardDescription";

export const CardContent = forwardRef<HTMLDivElement, DivProps>(
  ({ className, ...props }, ref) => (
    <div ref={ref} className={cn("p-5 sm:p-6", className)} {...props} />
  )
);
CardContent.displayName = "CardContent";

export const CardFooter = forwardRef<HTMLDivElement, DivProps>(
  ({ className, ...props }, ref) => (
    <div
      ref={ref}
      className={cn(
        "flex items-center gap-3 border-t px-5 py-4 sm:px-6",
        className
      )}
      {...props}
    />
  )
);
CardFooter.displayName = "CardFooter";
