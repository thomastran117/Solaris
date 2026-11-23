import { useRef, forwardRef, useImperativeHandle, useState } from "react";
import ReCAPTCHA from "react-google-recaptcha";
import environment from "@configuration/Environment";

const MAX_RETRIES = 3;
const EXECUTE_TIMEOUT_MS = 10_000;

export interface CaptchaRef {
  execute: () => Promise<string>;
}

interface GoogleRecaptchaProps {
  onError?: (error: Error) => void;
  onStatusChange?: (status: CaptchaStatus) => void;
}

export type CaptchaStatus =
  | { state: "idle" }
  | { state: "verifying" }
  | { state: "retrying"; attempt: number; maxAttempts: number }
  | { state: "success" }
  | { state: "error"; message: string };

const withTimeout = <T,>(promise: Promise<T>, ms: number): Promise<T> => {
  const timeout = new Promise<never>((_, reject) =>
    setTimeout(() => reject(new Error(`reCAPTCHA timed out after ${ms}ms`)), ms)
  );
  return Promise.race([promise, timeout]);
};

const GoogleRecaptcha = forwardRef<CaptchaRef, GoogleRecaptchaProps>(
  ({ onError, onStatusChange }, ref) => {
    const captchaRef = useRef<ReCAPTCHA | null>(null);
    const [status, setStatus] = useState<CaptchaStatus>({ state: "idle" });

    const updateStatus = (next: CaptchaStatus) => {
      setStatus(next);
      onStatusChange?.(next);
    };

    useImperativeHandle(ref, () => ({
      async execute() {
        if (!captchaRef.current) {
          const error = new Error("reCAPTCHA not initialized");
          updateStatus({
            state: "error",
            message: "Verification unavailable. Please refresh the page.",
          });
          onError?.(error);
          throw error;
        }

        updateStatus({ state: "verifying" });

        for (let attempt = 1; attempt <= MAX_RETRIES; attempt++) {
          try {
            const token = await withTimeout(
              captchaRef.current.executeAsync(),
              EXECUTE_TIMEOUT_MS
            );
            captchaRef.current.reset();

            if (!token) throw new Error("reCAPTCHA returned empty token");

            updateStatus({ state: "success" });
            return token;
          } catch (err) {
            captchaRef.current?.reset();

            const isLastAttempt = attempt === MAX_RETRIES;

            if (isLastAttempt) {
              const error =
                err instanceof Error
                  ? err
                  : new Error("reCAPTCHA verification failed");
              updateStatus({
                state: "error",
                message:
                  "Unable to verify you're human. Please try again later.",
              });
              onError?.(error);
              throw error;
            }

            updateStatus({
              state: "retrying",
              attempt,
              maxAttempts: MAX_RETRIES,
            });

            await new Promise((res) => setTimeout(res, 1_000 * attempt));
          }
        }

        throw new Error("reCAPTCHA verification failed after max retries");
      },
    }));

    return (
      <>
        <ReCAPTCHA
          sitekey={environment.RECAPTCHA_SITE_KEY}
          size="invisible"
          ref={captchaRef}
          onExpired={() => {
            captchaRef.current?.reset();
            updateStatus({ state: "idle" });
          }}
          onErrored={() => {
            const error = new Error("reCAPTCHA failed to load");
            updateStatus({
              state: "error",
              message:
                "Verification service unavailable. Please refresh the page.",
            });
            onError?.(error);
          }}
        />
        <CaptchaStatusMessage status={status} />
      </>
    );
  }
);

const CaptchaStatusMessage = ({ status }: { status: CaptchaStatus }) => {
  if (status.state === "idle" || status.state === "success") return null;

  const messages: Record<string, string> = {
    verifying: "Verifying...",
    retrying: `Verification failed, retrying... (${(status as Extract<CaptchaStatus, { state: "retrying" }>).attempt}/${(status as Extract<CaptchaStatus, { state: "retrying" }>).maxAttempts})`,
    error: (status as Extract<CaptchaStatus, { state: "error" }>).message,
  };

  const styles: Record<string, string> = {
    verifying: "text-muted-foreground",
    retrying: "text-warning",
    error: "text-danger",
  };

  return (
    <p
      role="status"
      aria-live="polite"
      className={`text-sm mt-1 ${styles[status.state]}`}
    >
      {messages[status.state]}
    </p>
  );
};

GoogleRecaptcha.displayName = "GoogleRecaptcha";

export default GoogleRecaptcha;
