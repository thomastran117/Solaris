import type { Metadata } from "next";
import AuthCallback from "@views/AuthCallback";

export const metadata: Metadata = {
  title: "Signing in",
  robots: { index: false },
};

export default function Page() {
  return <AuthCallback />;
}
