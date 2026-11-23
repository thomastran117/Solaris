import type { Metadata } from "next";
import OrdersPage from "@views/OrdersPage";

export const metadata: Metadata = { title: "Orders" };

export default function Page() {
  return <OrdersPage />;
}
