"use client";

import { useCallback, useRef, type ReactNode } from "react";
import Environment from "../configuration/Environment";
import GoogleRecaptcha, {
  type CaptchaRef,
} from "../components/auth/GoogleRecaptcha";

/**
 * Invisible reCAPTCHA for forms the backend protects (login, signup).
 *
 * Render `widget` inside the form and call `getToken()` on submit. Without a
 * site key (local development against the stub API) no widget renders and
 * `getToken()` resolves to an empty string; a real backend then rejects the
 * request, because captcha verification fails closed.
 */
export function useCaptcha(): {
  widget: ReactNode;
  getToken: () => Promise<string>;
  enabled: boolean;
} {
  const ref = useRef<CaptchaRef>(null);
  const enabled = !!Environment.RECAPTCHA_SITE_KEY;

  const getToken = useCallback(async () => {
    if (!enabled) return "";
    if (!ref.current) throw new Error("Verification is still loading.");
    return ref.current.execute();
  }, [enabled]);

  return {
    widget: enabled ? <GoogleRecaptcha ref={ref} /> : null,
    getToken,
    enabled,
  };
}
