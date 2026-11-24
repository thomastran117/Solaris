import { forwardRef } from "react";
import { Button, type ButtonProps } from "./Button";

export interface IconButtonProps extends Omit<ButtonProps, "size"> {
  /** Required: icon-only controls have no visible text. */
  "aria-label": string;
  size?: "icon" | "icon-sm";
}

export const IconButton = forwardRef<HTMLButtonElement, IconButtonProps>(
  ({ variant = "ghost", size = "icon", ...props }, ref) => (
    <Button ref={ref} variant={variant} size={size} {...props} />
  )
);
IconButton.displayName = "IconButton";
