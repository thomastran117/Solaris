import { forwardRef, type TextareaHTMLAttributes } from "react";
import { cn } from "../../lib/cn";
import { controlClasses } from "./Input";

export const Textarea = forwardRef<
  HTMLTextAreaElement,
  TextareaHTMLAttributes<HTMLTextAreaElement>
>(({ className, rows = 4, ...props }, ref) => (
  <textarea
    ref={ref}
    rows={rows}
    className={cn(controlClasses, "min-h-20 py-2 leading-relaxed", className)}
    {...props}
  />
));
Textarea.displayName = "Textarea";
