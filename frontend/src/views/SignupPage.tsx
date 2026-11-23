"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Check, MailCheck, X } from "lucide-react";
import { authApi, needsDeviceVerification } from "../api/auth";
import {
  AuthCard,
  AuthDivider,
  FormAlert,
  GoogleIcon,
} from "../components/auth/AuthShell";
import { Button } from "../components/ui/Button";
import { Checkbox } from "../components/ui/Checkbox";
import { Field } from "../components/ui/Field";
import { Input } from "../components/ui/Input";
import { PasswordInput } from "../components/ui/PasswordInput";
import { useCaptcha } from "../hooks/useCaptcha";
import { useCompleteSignIn, withNext } from "../hooks/useCompleteSignIn";
import { useGoogleSignIn } from "../hooks/useGoogleSignIn";
import { apiErrorMessage } from "../lib/apiError";
import { cn } from "../lib/cn";
import {
  PASSWORD_RULES,
  signupSchema,
  type SignupValues,
} from "../schemas/auth";

function PasswordChecklist({ password }: { password: string }) {
  return (
    <ul id="password-rules" className="grid gap-1 text-xs sm:grid-cols-2">
      {PASSWORD_RULES.map((rule) => {
        const met = rule.test(password);
        return (
          <li
            key={rule.id}
            className={cn(
              "flex items-center gap-1.5",
              met ? "text-success" : "text-muted-foreground"
            )}
          >
            {met ? (
              <Check className="size-3.5" aria-hidden />
            ) : (
              <X className="size-3.5" aria-hidden />
            )}
            {rule.label}
            <span className="sr-only">{met ? "(met)" : "(not met)"}</span>
          </li>
        );
      })}
    </ul>
  );
}

export default function SignupPage() {
  const router = useRouter();
  const completeSignIn = useCompleteSignIn();
  const captcha = useCaptcha();
  const [formError, setFormError] = useState<string | null>(null);
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [googlePending, setGooglePending] = useState(false);
  const [deviceCheck, setDeviceCheck] = useState(false);

  const {
    register,
    handleSubmit,
    control,
    formState: { errors, isSubmitting },
  } = useForm<SignupValues>({
    resolver: zodResolver(signupSchema),
    defaultValues: {
      email: "",
      password: "",
      confirmPassword: "",
      acceptTerms: false as unknown as true,
    },
  });
  const password = useWatch({ control, name: "password" }) ?? "";

  const onSubmit = handleSubmit(async ({ email, password }) => {
    setFormError(null);
    try {
      const token = await captcha.getToken();
      await authApi.signup({ email, password, captcha: token });
      setSentTo(email);
    } catch (error) {
      setFormError(
        apiErrorMessage(
          error,
          "We couldn't create your account. Please try again."
        )
      );
    }
  });

  const google = useGoogleSignIn(async (idToken) => {
    setFormError(null);
    setGooglePending(true);
    try {
      const res = await authApi.google(idToken);
      if (needsDeviceVerification(res.data)) {
        setDeviceCheck(true);
        return;
      }
      await completeSignIn(res.data);
    } catch (error) {
      setFormError(
        apiErrorMessage(error, "Google sign-in failed. Please try again.")
      );
    } finally {
      setGooglePending(false);
    }
  });

  const signInLink = (
    <Link
      href="/login"
      onClick={(e) => {
        e.preventDefault();
        router.push(withNext("/login"));
      }}
      className="font-medium text-link underline-offset-4 hover:underline"
    >
      Sign in
    </Link>
  );

  if (sentTo) {
    return (
      <AuthCard
        title="Check your inbox"
        description={
          <>
            We sent a verification link to{" "}
            <span className="font-medium text-foreground">{sentTo}</span>. Open
            it to activate your account, then sign in.
          </>
        }
        footer={<>Already verified? {signInLink}</>}
      >
        <div className="flex flex-col items-center gap-4 py-2 text-center">
          <span className="flex size-12 items-center justify-center rounded-full bg-success-subtle text-success">
            <MailCheck className="size-6" aria-hidden />
          </span>
          <p className="text-sm text-muted-foreground">
            Can&apos;t find it? Check your spam folder. The link expires, so use
            it soon.
          </p>
        </div>
      </AuthCard>
    );
  }

  return (
    <AuthCard
      title="Create your account"
      description="Save favourites, track orders and earn rewards across ShopWave."
      footer={<>Already have an account? {signInLink}</>}
    >
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {formError && <FormAlert>{formError}</FormAlert>}
        {deviceCheck && (
          <FormAlert variant="info">
            We&apos;ve emailed you a link to approve this device and finish
            signing in.
          </FormAlert>
        )}

        <Field label="Email" error={errors.email}>
          <Input
            type="email"
            autoComplete="email"
            inputMode="email"
            {...register("email")}
          />
        </Field>
        <Field label="Password" error={errors.password}>
          <PasswordInput
            autoComplete="new-password"
            aria-describedby="password-rules"
            {...register("password")}
          />
        </Field>
        <PasswordChecklist password={password} />
        <Field label="Confirm password" error={errors.confirmPassword}>
          <PasswordInput
            autoComplete="new-password"
            {...register("confirmPassword")}
          />
        </Field>

        <div className="space-y-1.5">
          <Checkbox
            id="accept-terms"
            label={
              <>
                I agree to the{" "}
                <Link
                  href="/terms-and-conditions"
                  target="_blank"
                  className="text-link underline-offset-4 hover:underline"
                >
                  terms
                </Link>{" "}
                and{" "}
                <Link
                  href="/privacy"
                  target="_blank"
                  className="text-link underline-offset-4 hover:underline"
                >
                  privacy policy
                </Link>
              </>
            }
            aria-invalid={errors.acceptTerms ? true : undefined}
            aria-describedby={
              errors.acceptTerms ? "accept-terms-error" : undefined
            }
            {...register("acceptTerms")}
          />
          {errors.acceptTerms && (
            <p
              id="accept-terms-error"
              role="alert"
              className="text-xs font-medium text-danger"
            >
              {errors.acceptTerms.message}
            </p>
          )}
        </div>

        {captcha.widget}

        <Button
          type="submit"
          className="w-full"
          loading={isSubmitting}
          disabled={isSubmitting || googlePending}
        >
          Create account
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
            disabled={isSubmitting || googlePending}
          >
            {!googlePending && <GoogleIcon />}
            Sign up with Google
          </Button>
        </>
      )}
    </AuthCard>
  );
}
