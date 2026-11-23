import type { Metadata } from "next";
import { Suspense } from "react";
import { connection } from "next/server";
import CheckoutPage from "@views/CheckoutPage";
import { getStripePublishableKey } from "../../../configuration/server";

export const metadata: Metadata = { title: "Checkout" };

export default async function Page() {
  // Read the Stripe key per request so one build works in every environment.
  await connection();
  return (
    // The view reads useSearchParams() (?currency, ?order, ?stepUpToken).
    <Suspense>
      <CheckoutPage stripePublishableKey={getStripePublishableKey()} />
    </Suspense>
  );
}
