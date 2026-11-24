"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { MailCheck } from "lucide-react";
import { authApi, needsDeviceVerification } from "../api/auth";
import {
  AuthCard,
  AuthDivider,
  FormAlert,
  GoogleIcon,
} from "../components/auth/AuthShell";
import { Button } from "../components/ui/Button";
import { Field } from "../components/ui/Field";
import { Input } from "../components/ui/Input";
import { PasswordInput } from "../components/ui/PasswordInput";
import { useCaptcha } from "../hooks/useCaptcha";
import {
  postLoginDestination,
  useCompleteSignIn,
  withNext,
} from "../hooks/useCompleteSignIn";
import { useGoogleSignIn } from "../hooks/useGoogleSignIn";
import { apiErrorMessage, apiErrorStatus } from "../lib/apiError";
import { loginSchema, type LoginValues } from "../schemas/auth";
import { useAppSelector } from "../stores/hooks";

function loginErrorMessage(error: unknown): string {
  const status = apiErrorStatus(error);
  // Same wording for both so the form never reveals whether an account exists.
  if (status === 401 || status === 403) return "Invalid email or password.";
  return apiErrorMessage(error, "Something went wrong. Please try again.");
}

export default function LoginPage() {
  const router = useRouter();
  const auth = useAppSelector((state) => state.auth);
  const completeSignIn = useCompleteSignIn();
  const captcha = useCaptcha();
  const [formError, setFormError] = useState<string | null>(null);
  const [deviceCheck, setDeviceCheck] = useState(false);
  const [googlePending, setGooglePending] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  });

  const busy = isSubmitting || googlePending;

  // Already signed in (e.g. the session was restored from the refresh cookie
  // after a cross-site return): go straight to the destination. Waits while a
  // sign-in is in flight; that handler navigates once the company is loaded.
  useEffect(() => {
    if (auth.sessionRestored && auth.accessToken && !busy) {
      router.replace(postLoginDestination());
    }
  }, [auth.sessionRestored, auth.accessToken, busy, router]);

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    setDeviceCheck(false);
    try {
      const token = await captcha.getToken();
      const res = await authApi.login({ ...values, captcha: token });
      if (needsDeviceVerification(res.data)) {
        setDeviceCheck(true);
        return;
      }
      await completeSignIn(res.data);
    } catch (error) {
      setFormError(loginErrorMessage(error));
    }
  });

  const google = useGoogleSignIn(async (idToken) => {
    setFormError(null);
    setDeviceCheck(false);
    setGooglePending(true);
    try {
      const res = await authApi.google(idToken);
      if (needsDeviceVerification(res.data)) {
        setDeviceCheck(true);
        return;
      }
      await completeSignIn(res.data);
    } catch (error) {
      setFormError(loginErrorMessage(error));
    } finally {
      setGooglePending(false);
    }
  });

  return (
    <AuthCard
      title="Sign in"
      description="Welcome back. Sign in to your ShopWave account."
      footer={
        <>
          New to ShopWave?{" "}
          <Link
            href="/signup"
            // Carry ?next= over; read at click time so the page stays prerenderable.
            onClick={(e) => {
              e.preventDefault();
              router.push(withNext("/signup"));
            }}
            className="font-medium text-link underline-offset-4 hover:underline"
          >
            Create an account
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {formError && <FormAlert>{formError}</FormAlert>}
        {deviceCheck && (
          <FormAlert variant="info">
            <span className="flex gap-2">
              <MailCheck className="mt-0.5 size-4 shrink-0" aria-hidden />
              <span>
                We don&apos;t recognise this device. We&apos;ve emailed you a
                link to approve it and finish signing in.
              </span>
            </span>
          </FormAlert>
        )}

        <Field label="Email" error={errors.email}>
          <Input
            type="email"
            autoComplete="email username"
            inputMode="email"
            {...register("email")}
          />
        </Field>
        <Field label="Password" error={errors.password}>
          <PasswordInput
            autoComplete="current-password"
            {...register("password")}
          />
        </Field>

        {captcha.widget}

        <Button
          type="submit"
          className="w-full"
          loading={isSubmitting}
          disabled={busy}
        >
          Sign in
        </Button>
      </form>

      {google.available && (
        <>
          <AuthDivider />
          <Button
            variant="outline"
            className="w-full"
            onClick={google.start}
            loading={googlePending}
            disabled={busy}
          >
            {!googlePending && <GoogleIcon />}
            Continue with Google
          </Button>
        </>
      )}
    </AuthCard>
  );
}
