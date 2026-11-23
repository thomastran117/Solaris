import type { Metadata } from "next";
import CartPage from "@views/CartPage";

// The cart lives in the browser (localStorage), so this is a client shell.
export const metadata: Metadata = {
  title: "Your cart",
  robots: { index: false },
};

export default function Page() {
  return <CartPage />;
}
