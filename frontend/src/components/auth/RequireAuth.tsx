"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAppSelector } from "../../stores/hooks";

/**
 * Client-side guard for account and merchant routes. `AuthInitializer` has already
 * attempted session restore before this renders, so a missing access token means the
 * visitor is anonymous. The backend still authorizes every API call.
 */
export default function RequireAuth({
  children,
}: {
  children: React.ReactNode;
}) {
  const accessToken = useAppSelector((s) => s.auth.accessToken);
  const router = useRouter();

  useEffect(() => {
    if (!accessToken) router.replace("/login");
  }, [accessToken, router]);

  if (!accessToken) return null;
  return <>{children}</>;
}
