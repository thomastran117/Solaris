import type { Metadata } from "next";
import { connection } from "next/server";
import OrderDetailPage from "@views/OrderDetailPage";
import { getStripePublishableKey } from "../../../../configuration/server";

export const metadata: Metadata = { title: "Order" };

export default async function Page() {
  // Unpaid orders can be paid here; the Stripe key is read per request.
  await connection();
  return <OrderDetailPage stripePublishableKey={getStripePublishableKey()} />;
}
