import type { Metadata } from "next";
import CollectionPage from "@views/CollectionPage";

export const metadata: Metadata = { title: "Collection" };

export default function Page() {
  return <CollectionPage />;
}
