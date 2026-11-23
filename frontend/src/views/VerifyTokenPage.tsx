"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { CheckCircle2, XCircle } from "lucide-react";
import { authApi, needsDeviceVerification } from "../api/auth";
import { AuthCard } from "../components/auth/AuthShell";
import { Button } from "../components/ui/Button";
import { Spinner } from "../components/ui/Spinner";
import { useCompleteSignIn } from "../hooks/useCompleteSignIn";
import { apiErrorMessage } from "../lib/apiError";

type Kind = "email" | "device";
type State =
  | { status: "verifying" }
  | { status: "verified" }
  | { status: "failed"; message: string };

const COPY = {
  email: {
    verifying: "Verifying your email…",
    verified: "Email verified",
    verifiedBody: "Your account is active. Sign in to start shopping.",
    failed: "We couldn't verify your email",
  },
  device: {
    verifying: "Approving this device…",
    verified: "Device approved",
    verifiedBody: "Signing you in…",
    failed: "We couldn't approve this device",
  },
} as const;

/**
 * Landing page for emailed `/verify-email?token=` and `/verify-device?token=`
 * links. Tokens are single-use, so the request runs exactly once even though
 * React StrictMode runs effects twice in development.
 */
export default function VerifyTokenPage({ kind }: { kind: Kind }) {
  const completeSignIn = useCompleteSignIn();
  const [state, setState] = useState<State>({ status: "verifying" });
  const started = useRef(false);
  const copy = COPY[kind];

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    const token = new URLSearchParams(window.location.search).get("token");

    const run = async () => {
      if (!token) {
        setState({
          status: "failed",
          message:
            "This link is missing its verification code. Open the link from your email again.",
        });
        return;
      }
      try {
        if (kind === "email") {
          await authApi.verifyEmail(token);
          setState({ status: "verified" });
          return;
        }
        const res = await authApi.verifyDevice(token);
        if (needsDeviceVerification(res.data)) {
          throw new Error("Device verification is still required.");
        }
        setState({ status: "verified" });
        await completeSignIn(res.data);
      } catch (error) {
        setState({
          status: "failed",
          message: apiErrorMessage(
            error,
            "The link may have expired or already been used."
          ),
        });
      }
    };
    void run();
  }, [kind, completeSignIn]);

  if (state.status === "verifying") {
    return (
      <AuthCard title={copy.verifying}>
        <div
          className="flex justify-center py-6"
          role="status"
          aria-label={copy.verifying}
        >
          <Spinner className="size-6 text-muted-foreground" />
        </div>
      </AuthCard>
    );
  }

  if (state.status === "verified") {
    return (
      <AuthCard title={copy.verified} description={copy.verifiedBody}>
        <div className="flex flex-col items-center gap-6 py-2">
          <CheckCircle2 className="size-10 text-success" aria-hidden />
          {kind === "email" && (
            <Button asChild className="w-full">
              <Link href="/login">Sign in</Link>
            </Button>
          )}
        </div>
      </AuthCard>
    );
  }

  return (
    <AuthCard title={copy.failed} description={state.message}>
      <div className="flex flex-col items-center gap-6 py-2">
        <XCircle className="size-10 text-danger" aria-hidden />
        <div className="flex w-full flex-col gap-2">
          <Button asChild className="w-full">
            <Link href="/login">Back to sign in</Link>
          </Button>
          {kind === "email" && (
            <Button asChild variant="outline" className="w-full">
              <Link href="/signup">Create an account</Link>
            </Button>
          )}
        </div>
      </div>
    </AuthCard>
  );
}
