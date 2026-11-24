"use client";

import { useState, type FormEvent } from "react";
import { MailCheck } from "lucide-react";
import { extractStepUpToken } from "../../lib/checkoutDraft";
import { Button } from "../ui/Button";
import { Field } from "../ui/Field";
import { Input } from "../ui/Input";

/**
 * Shown when the order needs email confirmation (HTTP 428). The email links
 * back to this checkout; on a different device the buyer can paste the link.
 */
export function StepUpPanel({
  onToken,
  busy,
}: {
  onToken: (token: string) => void;
  busy?: boolean;
}) {
  const [value, setValue] = useState("");
  const [error, setError] = useState<string | null>(null);

  function submit(event: FormEvent) {
    event.preventDefault();
    const token = extractStepUpToken(value);
    if (!token) {
      setError("Paste the full link from the email.");
      return;
    }
    setError(null);
    onToken(token);
  }

  return (
    <div
      role="status"
      className="space-y-4 rounded-xl border border-info/30 bg-info-subtle p-5"
    >
      <div className="flex gap-3">
        <MailCheck className="mt-0.5 size-5 shrink-0 text-info" aria-hidden />
        <div className="space-y-1">
          <p className="font-medium text-foreground">
            Confirm your order by email
          </p>
          <p className="text-sm text-muted-foreground">
            For your security we sent a confirmation link to your email. Open it
            on this device and we&apos;ll place your order with the details you
            entered.
          </p>
        </div>
      </div>
      <form
        onSubmit={submit}
        className="flex flex-col gap-2 sm:flex-row sm:items-end"
      >
        <Field label="Or paste the link here" error={error} className="flex-1">
          <Input
            value={value}
            onChange={(e) => setValue(e.target.value)}
            placeholder="https://…/checkout?stepUpToken=…"
            autoComplete="off"
          />
        </Field>
        <Button type="submit" variant="outline" loading={busy}>
          Confirm order
        </Button>
      </form>
    </div>
  );
}
