import { z } from "zod";

/** Mirrors backend `StrongPasswordValidator` (`@StrongPassword(minLength = 8)`). */
export const PASSWORD_RULES = [
  {
    id: "length",
    label: "At least 8 characters",
    test: (v: string) => v.length >= 8,
  },
  {
    id: "upper",
    label: "An uppercase letter",
    test: (v: string) => /[A-Z]/.test(v),
  },
  {
    id: "lower",
    label: "A lowercase letter",
    test: (v: string) => /[a-z]/.test(v),
  },
  { id: "digit", label: "A number", test: (v: string) => /\d/.test(v) },
  {
    id: "symbol",
    label: "A symbol (@ $ ! % * ? & . _ -)",
    test: (v: string) => /[@$!%*?&._-]/.test(v),
  },
] as const;

const email = z
  .string()
  .trim()
  .min(1, "Enter your email address")
  .email("Enter a valid email address");

export const loginSchema = z.object({
  email,
  // LoginRequest requires at least 8 characters.
  password: z
    .string()
    .min(1, "Enter your password")
    .min(8, "Passwords are at least 8 characters"),
});
export type LoginValues = z.infer<typeof loginSchema>;

export const signupSchema = z
  .object({
    email,
    password: z
      .string()
      .min(1, "Choose a password")
      .refine(
        (v) => PASSWORD_RULES.every((rule) => rule.test(v)),
        "Your password doesn't meet all the requirements"
      ),
    confirmPassword: z.string().min(1, "Confirm your password"),
    acceptTerms: z.literal(true, {
      error: "Accept the terms and privacy policy to continue",
    }),
  })
  .refine((v) => v.password === v.confirmPassword, {
    path: ["confirmPassword"],
    message: "Passwords don't match",
  });
export type SignupValues = z.infer<typeof signupSchema>;
