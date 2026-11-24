import type { HTMLAttributes } from "react";
import { cn } from "../../lib/cn";

interface ContainerProps extends HTMLAttributes<HTMLDivElement> {
  /** `default` = storefront width, `narrow` = forms/reading, `wide` = dense grids. */
  size?: "narrow" | "default" | "wide";
}

const widths = {
  narrow: "max-w-3xl",
  default: "max-w-7xl",
  wide: "max-w-[90rem]",
} as const;

export function Container({
  size = "default",
  className,
  ...props
}: ContainerProps) {
  return (
    <div
      className={cn(
        "mx-auto w-full px-4 sm:px-6 lg:px-8",
        widths[size],
        className
      )}
      {...props}
    />
  );
}
