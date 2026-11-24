import { forwardRef, type ButtonHTMLAttributes, type MouseEvent } from "react";
import { Slot } from "radix-ui";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "../../lib/cn";
import { Spinner } from "./Spinner";

export const buttonVariants = cva(
  [
    "inline-flex shrink-0 items-center justify-center gap-2 whitespace-nowrap rounded-lg font-medium",
    "transition-colors duration-150 select-none",
    "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background",
    "disabled:pointer-events-none disabled:opacity-50 aria-disabled:pointer-events-none aria-disabled:opacity-50",
    "[&_svg]:pointer-events-none [&_svg]:shrink-0",
  ],
  {
    variants: {
      variant: {
        primary:
          "bg-primary text-primary-foreground shadow-sm hover:bg-primary-hover",
        secondary: "bg-muted text-foreground hover:bg-muted/70",
        outline:
          "border border-input bg-surface text-foreground hover:bg-muted",
        ghost: "text-foreground hover:bg-muted",
        destructive:
          "bg-danger text-danger-foreground shadow-sm hover:bg-danger-hover",
        link: "text-link underline-offset-4 hover:underline",
      },
      size: {
        sm: "h-8 px-3 text-sm [&_svg]:size-4",
        md: "h-10 px-4 text-sm [&_svg]:size-4",
        lg: "h-11 px-6 text-base [&_svg]:size-5",
        icon: "size-10 [&_svg]:size-5",
        "icon-sm": "size-8 [&_svg]:size-4",
      },
    },
    compoundVariants: [{ variant: "link", className: "h-auto px-0" }],
    defaultVariants: { variant: "primary", size: "md" },
  }
);

function swallowActivation(event: MouseEvent<HTMLElement>) {
  event.preventDefault();
  event.stopPropagation();
}

export interface ButtonProps
  extends
    ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  /** Render the child element (e.g. a Next `<Link>`) with button styling. */
  asChild?: boolean;
  /** Show a spinner, mark the button busy and disable it. */
  loading?: boolean;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      className,
      variant,
      size,
      asChild = false,
      loading = false,
      disabled,
      children,
      type,
      ...props
    },
    ref
  ) => {
    const classes = cn(buttonVariants({ variant, size }), className);
    if (asChild) {
      // The slotted child may be a link, which has no `disabled` attribute, so
      // disabled/loading are expressed with ARIA, removed from the tab order
      // and enforced by swallowing activation. A handler is only attached when
      // needed: server components render `<Button asChild><Link/></Button>`,
      // and a function prop can't cross into the client `Link`.
      const inert = disabled || loading;
      const { onClick, ...rest } = props;
      return (
        <Slot.Root
          ref={ref}
          className={classes}
          aria-disabled={inert || undefined}
          aria-busy={loading || undefined}
          tabIndex={inert ? -1 : undefined}
          onClick={inert ? swallowActivation : onClick}
          {...rest}
        >
          {loading && <Spinner />}
          <Slot.Slottable>{children}</Slot.Slottable>
        </Slot.Root>
      );
    }
    return (
      <button
        ref={ref}
        type={type ?? "button"}
        className={classes}
        disabled={disabled || loading}
        aria-busy={loading || undefined}
        {...props}
      >
        {loading && <Spinner />}
        {children}
      </button>
    );
  }
);
Button.displayName = "Button";
