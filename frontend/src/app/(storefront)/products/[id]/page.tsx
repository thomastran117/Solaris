import type { Metadata } from "next";
import ProductPage from "@views/ProductPage";

export const metadata: Metadata = { title: "Product" };

export default function Page() {
  return <ProductPage />;
}
