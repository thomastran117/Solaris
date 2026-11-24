"use client";

import { useCallback, useEffect, useRef } from "react";
import Environment, { getFrontendUrl } from "../configuration/Environment";

/**
 * Opens Google's OAuth popup (implicit `id_token` flow). `/auth/google`
 * (AuthCallback) posts the token back to this window, and `onIdToken` receives
 * it. Opening a second popup tears down the first, so a double click can't
 * start two sign-ins.
 */
export function useGoogleSignIn(onIdToken: (idToken: string) => void): {
  start: () => void;
  available: boolean;
} {
  const popupRef = useRef<Window | null>(null);
  const listenerRef = useRef<((e: MessageEvent) => void) | null>(null);
  const callbackRef = useRef(onIdToken);
  useEffect(() => {
    callbackRef.current = onIdToken;
  }, [onIdToken]);

  const teardown = useCallback(() => {
    if (listenerRef.current) {
      window.removeEventListener("message", listenerRef.current);
      listenerRef.current = null;
    }
    popupRef.current?.close();
    popupRef.current = null;
  }, []);

  useEffect(() => teardown, [teardown]);

  const start = useCallback(() => {
    const clientId = Environment.google_client;
    if (!clientId) return;
    teardown();

    const params = new URLSearchParams({
      client_id: clientId,
      redirect_uri: `${getFrontendUrl()}/auth/google`,
      response_type: "id_token",
      scope: "openid email profile",
      nonce: crypto.randomUUID(),
      prompt: "select_account",
    });
    const width = 600;
    const height = 700;
    const left = window.screenX + (window.outerWidth - width) / 2;
    const top = window.screenY + (window.outerHeight - height) / 2;
    popupRef.current = window.open(
      `https://accounts.google.com/o/oauth2/v2/auth?${params}`,
      "GoogleLogin",
      `width=${width},height=${height},left=${left},top=${top},resizable,scrollbars`
    );

    const listener = (event: MessageEvent) => {
      if (event.origin !== window.location.origin) return;
      if (event.data?.type === "google_oauth_token") {
        teardown();
        callbackRef.current(event.data.token);
      }
    };
    listenerRef.current = listener;
    window.addEventListener("message", listener);
  }, [teardown]);

  return { start, available: !!Environment.google_client };
}
