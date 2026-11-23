import type { Metadata } from "next";
import HelloTestPage from "@views/HelloTestPage";

export const metadata: Metadata = { title: "Hello", robots: { index: false } };

export default function Page() {
  return <HelloTestPage />;
}
