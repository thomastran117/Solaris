import { loadStripe, type Appearance, type Stripe } from "@stripe/stripe-js";
import type { ResolvedTheme } from "./theme/themeConfig";

const instances = new Map<string, Promise<Stripe | null>>();

/** One Stripe.js instance per publishable key (loadStripe must not run per render). */
export function getStripe(publishableKey: string): Promise<Stripe | null> {
  let instance = instances.get(publishableKey);
  if (!instance) {
    instance = loadStripe(publishableKey);
    instances.set(publishableKey, instance);
  }
  return instance;
}

function token(name: string): string {
  return getComputedStyle(document.documentElement)
    .getPropertyValue(`--${name}`)
    .trim();
}

/**
 * Payment Element styling from the live design tokens. The Element renders in
 * Stripe's iframe, which can't read our CSS variables, so the resolved values
 * are passed in. Browser only (reads computed styles).
 */
export function stripeAppearance(theme: ResolvedTheme): Appearance {
  return {
    theme: theme === "dark" ? "night" : "stripe",
    variables: {
      colorPrimary: token("primary"),
      colorBackground: token("surface"),
      colorText: token("foreground"),
      colorTextSecondary: token("muted-foreground"),
      colorDanger: token("danger"),
      borderRadius: "8px",
      fontFamily:
        getComputedStyle(document.body).fontFamily ||
        "ui-sans-serif, system-ui, sans-serif",
      spacingUnit: "4px",
    },
    rules: {
      ".Input": {
        borderColor: token("input"),
        boxShadow: "none",
      },
      ".Input:focus": {
        borderColor: token("ring"),
      },
    },
  };
}
