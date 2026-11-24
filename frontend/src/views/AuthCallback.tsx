"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { XCircle } from "lucide-react";
import { AuthCard } from "../components/auth/AuthShell";
import { Button } from "../components/ui/Button";
import { Spinner } from "../components/ui/Spinner";

/**
 * Google OAuth popup redirect target. Reads `id_token` from the URL fragment
 * (which never reaches the server), posts it to the opener window, and closes.
 */
export default function AuthCallback() {
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    try {
      const hash = new URLSearchParams(window.location.hash.substring(1));
      const idToken = hash.get("id_token");
      if (idToken && window.opener) {
        window.opener.postMessage(
          { type: "google_oauth_token", token: idToken },
          window.location.origin
        );
        window.close();
      } else {
        setError(
          "We didn't receive a sign-in token from Google. Please try again."
        );
      }
    } catch {
      setError("Something went wrong. Please try again.");
    }
  }, []);

  if (error) {
    return (
      <AuthCard title="Google sign-in failed" description={error}>
        <div className="flex flex-col items-center gap-6 py-2">
          <XCircle className="size-10 text-danger" aria-hidden />
          <Button asChild className="w-full">
            <Link href="/login">Back to sign in</Link>
          </Button>
        </div>
      </AuthCard>
    );
  }

  return (
    <AuthCard
      title="Finishing sign-in…"
      description="Completing your Google sign-in. This window closes on its own."
    >
      <div
        className="flex justify-center py-6"
        role="status"
        aria-label="Signing in"
      >
        <Spinner className="size-6 text-muted-foreground" />
      </div>
    </AuthCard>
  );
}
