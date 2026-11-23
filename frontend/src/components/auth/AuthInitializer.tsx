"use client";

import { useEffect, useState } from "react";
import axios from "axios";
import Environment from "../../configuration/Environment";
import { envelopeData } from "../../api/envelope";
import { refreshSession } from "../../api/session";
import { setCredentials } from "../../stores/authSlice";
import { useAppDispatch } from "../../stores/hooks";

/**
 * Restores the session from the `refreshToken` cookie once per page load, then loads
 * the user's company membership. Children render after the attempt settles.
 *
 * The refresh runs only in the browser and goes through the shared `refreshSession()`
 * so that StrictMode's double effect and the 401 interceptor cannot burn the
 * single-use refresh token twice.
 */
export default function AuthInitializer({
  children,
}: {
  children: React.ReactNode;
}) {
  const dispatch = useAppDispatch();
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    let cancelled = false;

    refreshSession()
      .then(async (accessToken) => {
        try {
          // Raw axios: a 401/403 here means "no company", not an expired token, so it
          // must not trigger the shared client's refresh-and-retry interceptor.
          const companyResp = await axios.get(
            `${Environment.backend_url}/companies/mine`,
            {
              headers: { Authorization: `Bearer ${accessToken}` },
              withCredentials: true,
            }
          );
          if (!cancelled) {
            const company = envelopeData<{ id?: string } | null>(
              companyResp.data
            );
            dispatch(setCredentials({ companyId: company?.id ?? null }));
          }
        } catch {
          // No company associated with this account; companyId stays null.
        }
      })
      .catch(() => {
        // Anonymous visitor or expired session; refreshSession cleared credentials.
      })
      .finally(() => {
        if (!cancelled) setHydrated(true);
      });

    return () => {
      cancelled = true;
    };
  }, [dispatch]);

  if (!hydrated) {
    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center">
        <div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  return <>{children}</>;
}
