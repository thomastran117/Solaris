import type { Metadata } from "next";
import VerifyTokenPage from "@views/VerifyTokenPage";

// Target of the account verification email (EmailSender: /verify-email?token=).
export const metadata: Metadata = {
  title: "Verify your email",
  robots: { index: false },
};

export default function Page() {
  return <VerifyTokenPage kind="email" />;
}
