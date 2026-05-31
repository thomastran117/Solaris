import { useState, useEffect } from "react";
import axios from "axios";
import { useDispatch } from "react-redux";
import { setCredentials, clearCredentials } from "../../stores/authSlice";
import type { AppDispatch } from "../../stores";
import Environment from "../../configuration/Environment";

function unwrapEnvelope(data: unknown): Record<string, unknown> {
  if (
    data &&
    typeof data === "object" &&
    "success" in data &&
    "data" in data
  ) {
    return (data as { data: Record<string, unknown> }).data ?? {};
  }
  return (data as Record<string, unknown>) ?? {};
}

export default function AuthInitializer({ children }: { children: React.ReactNode }) {
  const dispatch = useDispatch<AppDispatch>();
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    let cancelled = false;

    axios
      .post(`${Environment.backend_url}/auth/refresh`, {}, { withCredentials: true })
      .then(async (resp) => {
        if (cancelled) return;
        const payload = unwrapEnvelope(resp.data);
        const accessToken = payload.accessToken as string | undefined;
        dispatch(
          setCredentials({
            accessToken: accessToken ?? null,
            email: (payload.email as string) ?? null,
            role: (payload.role as string) ?? null,
            tier: (payload.tier as string) ?? null,
          })
        );
        if (accessToken) {
          try {
            const companyResp = await axios.get(
              `${Environment.backend_url}/companies/mine`,
              {
                headers: { Authorization: `Bearer ${accessToken}` },
                withCredentials: true,
              }
            );
            if (!cancelled) {
              const company = unwrapEnvelope(companyResp.data);
              dispatch(setCredentials({ companyId: (company.id as string) ?? null }));
            }
          } catch {
            // No company associated with this account — companyId stays null.
          }
        }
      })
      .catch(() => {
        if (!cancelled) dispatch(clearCredentials());
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
