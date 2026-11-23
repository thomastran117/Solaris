"use client";

import { useEffect } from "react";
import { RotateCw } from "lucide-react";
import { StatusPage } from "../../components/layout/StatusPage";
import { Button } from "../../components/ui/Button";

/** Unexpected rendering failure on a storefront page (backend errors degrade without this). */
export default function StorefrontError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <StatusPage
      alert
      title="Something went wrong"
      description="We couldn't load this page. Please try again in a moment."
      actions={
        <Button onClick={reset}>
          <RotateCw aria-hidden />
          Try again
        </Button>
      }
    />
  );
}
