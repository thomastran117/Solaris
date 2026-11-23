import type { Metadata } from "next";
import KitPage from "@views/KitPage";

export const metadata: Metadata = { title: "Kit" };

export default function Page() {
  return <KitPage />;
}
