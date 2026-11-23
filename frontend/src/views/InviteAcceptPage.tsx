"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter, usePathname } from "next/navigation";
import { useSelector } from "react-redux";
import { useQuery, useMutation } from "@tanstack/react-query";
import { CheckCircle, AlertCircle } from "lucide-react";
import { teamApi, type TeamInvitePreview } from "../api/team";
import type { RootState } from "../stores";
import { loginUrlFor } from "../lib/routes";
import { Spinner } from "../components/ui/Spinner";

export default function InviteAcceptPage() {
  const { token } = useParams<{ token: string }>();
  const router = useRouter();
  const pathname = usePathname();
  const accessToken = useSelector((s: RootState) => s.auth.accessToken);
  const sessionRestored = useSelector((s: RootState) => s.auth.sessionRestored);
  const [accepted, setAccepted] = useState(false);

  const previewQuery = useQuery<TeamInvitePreview>({
    queryKey: ["invite", token],
    queryFn: () => teamApi.previewInvite(token!).then((r) => r.data),
    enabled: !!token,
    retry: false,
  });

  const acceptMutation = useMutation({
    mutationFn: () => teamApi.acceptInvite(token!).then((r) => r.data),
    onSuccess: () => setAccepted(true),
  });

  useEffect(() => {
    if (sessionRestored && !accessToken && token) {
      router.replace(loginUrlFor(pathname));
    }
  }, [sessionRestored, accessToken, token, router, pathname]);

  if (!token) {
    return (
      <div className="flex justify-center">
        <p className="text-muted-foreground">Invalid invite link.</p>
      </div>
    );
  }

  if (previewQuery.isLoading) {
    return (
      <div className="flex justify-center">
        <Spinner
          className="size-6 text-muted-foreground"
          label="Loading invite"
        />
      </div>
    );
  }

  if (previewQuery.error || !previewQuery.data) {
    return (
      <div className="flex justify-center">
        <div className="w-full rounded-xl border bg-surface p-8 text-center shadow-xs">
          <AlertCircle className="w-8 h-8 text-danger mx-auto mb-3" />
          <h1 className="text-lg font-semibold text-foreground">
            Invite invalid or expired
          </h1>
          <p className="text-sm text-muted-foreground mt-2">
            This invite link is no longer valid. Ask the company owner to send a
            new one.
          </p>
        </div>
      </div>
    );
  }

  const preview = previewQuery.data;

  if (accepted) {
    return (
      <div className="flex justify-center">
        <div className="w-full rounded-xl border bg-surface p-8 text-center shadow-xs">
          <CheckCircle className="w-8 h-8 text-success mx-auto mb-3" />
          <h1 className="text-lg font-semibold text-foreground">You're in!</h1>
          <p className="text-sm text-muted-foreground mt-2">
            You have joined <strong>{preview.companyName}</strong> as a{" "}
            {preview.role.toLowerCase()}.
          </p>
          <button
            onClick={() => router.push("/dashboard")}
            className="mt-5 inline-flex items-center gap-2 px-5 py-2 rounded-full bg-primary hover:bg-primary-hover text-primary-foreground font-semibold transition-colors"
          >
            Go to dashboard
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex justify-center">
      <div className="w-full rounded-xl border bg-surface p-8 text-center shadow-xs">
        <p className="text-xs font-semibold text-link mb-2">Invitation</p>
        <h1 className="text-2xl font-semibold text-foreground">
          {preview.companyName}
        </h1>
        <p className="text-sm text-muted-foreground mt-3 leading-relaxed">
          {preview.inviterDisplayName ? (
            <strong>{preview.inviterDisplayName}</strong>
          ) : (
            "Someone"
          )}{" "}
          invited you to join as a <strong>{preview.role.toLowerCase()}</strong>
          .
        </p>
        <p className="text-xs text-muted-foreground mt-2">
          Invite sent to <strong>{preview.invitedEmail}</strong>
        </p>
        {acceptMutation.error && (
          <p className="text-sm text-danger mt-4">
            {(acceptMutation.error as any)?.response?.data?.message ??
              "Failed to accept invite"}
          </p>
        )}
        <button
          onClick={() => acceptMutation.mutate()}
          disabled={acceptMutation.isPending}
          className="mt-5 inline-flex items-center gap-2 px-5 py-2 rounded-full bg-primary hover:bg-primary-hover text-primary-foreground font-semibold transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
        >
          Accept invitation
        </button>
      </div>
    </div>
  );
}
