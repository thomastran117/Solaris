"use client";

import { useCallback } from "react";
import { useRouter } from "next/navigation";
import api from "../api";
import type { AuthSession } from "../api/auth";
import { useAppDispatch } from "../stores/hooks";
import { setCredentials } from "../stores/authSlice";
import { DEFAULT_AFTER_LOGIN } from "../lib/routes";
import { safeNextPath } from "../lib/safeRedirect";

/**
 * Where to go after signing in: a safe same-origin `?next=` path (open-redirect
 * protection), otherwise the default. Read from `window.location` at navigation
 * time so auth pages stay statically prerendered (no useSearchParams()).
 */
export function postLoginDestination(): string {
  const next = new URLSearchParams(window.location.search).get("next");
  return safeNextPath(next) ?? DEFAULT_AFTER_LOGIN;
}

/** `path` with the current `?next=` carried over (sign-in ↔ sign-up links). */
export function withNext(path: string): string {
  const next = new URLSearchParams(window.location.search).get("next");
  return next ? `${path}?next=${encodeURIComponent(next)}` : path;
}

/**
 * Stores a new session, loads the user's company membership (merchant pages
 * need it), then navigates to the post-login destination.
 */
export function useCompleteSignIn(): (session: AuthSession) => Promise<void> {
  const router = useRouter();
  const dispatch = useAppDispatch();

  return useCallback(
    async (session: AuthSession) => {
      dispatch(
        setCredentials({
          accessToken: session.token,
          email: session.email,
          role: session.usertype,
          tier: session.tier,
        })
      );
      try {
        const res = await api.get<{ id: string }>("/companies/mine");
        dispatch(setCredentials({ companyId: res.data.id }));
      } catch {
        // Not a company member; companyId stays null.
      }
      router.replace(postLoginDestination());
    },
    [dispatch, router]
  );
}
