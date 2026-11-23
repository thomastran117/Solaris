import type { Metadata } from "next";
import OrderDetailPage from "@views/OrderDetailPage";

export const metadata: Metadata = { title: "Order" };

export default function Page() {
  return <OrderDetailPage />;
}
