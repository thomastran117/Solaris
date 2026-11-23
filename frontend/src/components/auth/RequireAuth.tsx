"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAppSelector } from "../../stores/hooks";

function SessionSpinner() {
  return (
    <div
      className="min-h-screen bg-slate-950 flex items-center justify-center"
      role="status"
      aria-label="Loading your session"
    >
      <div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
    </div>
  );
}

/**
 * Client-side guard for account and merchant routes. It renders a spinner until the
 * page-load session restore has settled (always the case in server HTML, so protected
 * shells never contain private data), then redirects visitors without an access token
 * to /login. The backend still authorizes every API call.
 */
export default function RequireAuth({
  children,
}: {
  children: React.ReactNode;
}) {
  const accessToken = useAppSelector((s) => s.auth.accessToken);
  const sessionRestored = useAppSelector((s) => s.auth.sessionRestored);
  const router = useRouter();

  useEffect(() => {
    if (sessionRestored && !accessToken) router.replace("/login");
  }, [sessionRestored, accessToken, router]);

  // Wait for the full restore, not just the token: merchant pages also need the
  // company membership that the restore loads right after refreshing.
  if (!sessionRestored || !accessToken) return <SessionSpinner />;
  return <>{children}</>;
}
