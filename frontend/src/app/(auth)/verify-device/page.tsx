import type { Metadata } from "next";
import VerifyTokenPage from "@views/VerifyTokenPage";

// Target of the new-device approval email (EmailSender: /verify-device?token=).
export const metadata: Metadata = {
  title: "Approve this device",
  robots: { index: false },
};

export default function Page() {
  return <VerifyTokenPage kind="device" />;
}
