"use client";

import { useMemo, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import {
  Elements,
  PaymentElement,
  useElements,
  useStripe,
} from "@stripe/react-stripe-js";
import { CreditCard, Lock } from "lucide-react";
import type { Order } from "../../types/order";
import { getStripe, stripeAppearance } from "../../lib/stripe";
import { formatMoney } from "../../lib/format";
import { useTheme } from "../theme/ThemeProvider";
import { Button } from "../ui/Button";
import { Card, CardContent, CardHeader, CardTitle } from "../ui/Card";
import { EmptyState } from "../ui/EmptyState";

interface PaymentPanelProps {
  order: Order;
  /** From the server (STRIPE_PUBLISHABLE_KEY); null when payments aren't set up. */
  publishableKey: string | null;
  title?: string;
}

/**
 * Collects payment for a RESERVED order with Stripe Payment Element.
 *
 * The client never decides that payment succeeded: after Stripe confirms, the
 * buyer lands on the order page, which shows PAID once the backend has
 * processed Stripe's webhook (live updates over SSE).
 */
export function PaymentPanel({
  order,
  publishableKey,
  title = "Payment",
}: PaymentPanelProps) {
  const { resolved } = useTheme();
  const clientSecret = order.paymentClientSecret ?? null;
  const stripe = useMemo(
    () => (publishableKey ? getStripe(publishableKey) : null),
    [publishableKey]
  );

  if (!publishableKey || !stripe) {
    return (
      <EmptyState
        icon={CreditCard}
        title="Payments aren't configured"
        description="This store can't take card payments right now. Your order is reserved; you can complete payment from your orders page later."
      />
    );
  }

  if (!clientSecret) {
    return (
      <EmptyState
        icon={CreditCard}
        title="Payment isn't available for this order"
        description="There's nothing to pay here. Check the order page for its current status."
      />
    );
  }

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between gap-3">
        <CardTitle>{title}</CardTitle>
        <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
          <Lock className="size-3.5" aria-hidden />
          Secured by Stripe
        </span>
      </CardHeader>
      <CardContent>
        {/* Re-mount on theme change so the iframe picks up the new appearance. */}
        <Elements
          key={`${clientSecret}:${resolved}`}
          stripe={stripe}
          options={{ clientSecret, appearance: stripeAppearance(resolved) }}
        >
          <PaymentForm order={order} />
        </Elements>
      </CardContent>
    </Card>
  );
}

function PaymentForm({ order }: { order: Order }) {
  const stripe = useStripe();
  const elements = useElements();
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!stripe || !elements || submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      const submitted = await elements.submit();
      if (submitted.error) {
        setError(submitted.error.message ?? "Check your payment details.");
        return;
      }
      // Shipping confirmation changes the PaymentIntent amount server-side.
      await elements.fetchUpdates();
      const result = await stripe.confirmPayment({
        elements,
        redirect: "if_required",
        confirmParams: {
          return_url: `${window.location.origin}/orders/${order.id}?payment=return`,
        },
      });
      if (result.error) {
        setError(
          result.error.message ??
            "Your payment couldn't be completed. Try another payment method."
        );
        return;
      }
      router.replace(`/orders/${order.id}?placed=1`);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4">
      <PaymentElement options={{ layout: "tabs" }} />
      {error && (
        <p role="alert" className="text-sm font-medium text-danger">
          {error}
        </p>
      )}
      <Button
        type="submit"
        size="lg"
        className="w-full"
        loading={submitting}
        disabled={!stripe || !elements}
      >
        Pay {formatMoney(order.totalAmount, order.currency)}
      </Button>
      <p className="text-center text-xs text-muted-foreground">
        Your order is placed when payment is confirmed.
      </p>
    </form>
  );
}
