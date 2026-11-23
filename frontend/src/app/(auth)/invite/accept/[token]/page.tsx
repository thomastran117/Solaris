import type { Metadata } from "next";
import InviteAcceptPage from "@views/InviteAcceptPage";

export const metadata: Metadata = {
  title: "Accept invitation",
  robots: { index: false },
};

export default function Page() {
  return <InviteAcceptPage />;
}
