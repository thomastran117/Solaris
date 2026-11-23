import type { Metadata } from "next";
import Link from "next/link";
import { StatusPage } from "../../components/layout/StatusPage";
import { Button } from "../../components/ui/Button";

export const metadata: Metadata = {
  title: "Not available",
  robots: { index: false },
};

/** Shown with HTTP 404 when a product, store, collection, kit, or shared list is missing. */
export default function StorefrontNotFound() {
  return (
    <StatusPage
      code="404"
      title="This item isn't available"
      description="It may have been removed, unpublished, or the link may be mistyped."
      actions={
        <>
          <Button asChild>
            <Link href="/browse">Browse products</Link>
          </Button>
          <Button asChild variant="outline">
            <Link href="/">Go home</Link>
          </Button>
        </>
      }
    />
  );
}
