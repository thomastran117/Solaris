import {
  cloneElement,
  isValidElement,
  useId,
  type ReactElement,
  type ReactNode,
} from "react";
import { cn } from "../../lib/cn";
import { Label } from "./Label";

/** Accepts a plain message or a React Hook Form `FieldError`. */
export type FieldErrorLike = string | { message?: string } | null | undefined;

function errorMessage(error: FieldErrorLike): string | undefined {
  if (!error) return undefined;
  return typeof error === "string" ? error : error.message;
}

interface ControlProps {
  id?: string;
  "aria-invalid"?: boolean;
  "aria-describedby"?: string;
  "aria-required"?: boolean;
}

interface FieldProps {
  label: ReactNode;
  /** Exactly one form control (Input, Select, Textarea, …). */
  children: ReactElement<ControlProps>;
  description?: ReactNode;
  error?: FieldErrorLike;
  required?: boolean;
  optional?: boolean;
  /** Override the generated control id. */
  id?: string;
  className?: string;
}

/**
 * Label + control + description + error, with the ids and ARIA attributes wired
 * up. Pass a single control as the child; Field gives it `id`,
 * `aria-invalid` and `aria-describedby`.
 */
export function Field({
  label,
  children,
  description,
  error,
  required,
  optional,
  id,
  className,
}: FieldProps) {
  const generatedId = useId();
  const controlId = id ?? children.props.id ?? generatedId;
  const descriptionId = description ? `${controlId}-description` : undefined;
  const message = errorMessage(error);
  const errorId = message ? `${controlId}-error` : undefined;
  const describedBy =
    [children.props["aria-describedby"], descriptionId, errorId]
      .filter(Boolean)
      .join(" ") || undefined;

  const control = isValidElement(children)
    ? cloneElement(children, {
        id: controlId,
        "aria-invalid": message ? true : children.props["aria-invalid"],
        "aria-describedby": describedBy,
        "aria-required": required || undefined,
      })
    : children;

  return (
    <div className={cn("grid gap-1.5", className)}>
      <Label htmlFor={controlId} optional={optional}>
        {label}
        {required && (
          <span aria-hidden className="ml-0.5 text-danger">
            *
          </span>
        )}
      </Label>
      {control}
      {description && (
        <p id={descriptionId} className="text-xs text-muted-foreground">
          {description}
        </p>
      )}
      {message && (
        <p
          id={errorId}
          role="alert"
          className="text-xs font-medium text-danger"
        >
          {message}
        </p>
      )}
    </div>
  );
}
