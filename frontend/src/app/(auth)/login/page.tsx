import type { Metadata } from "next";
import LoginPage from "@views/LoginPage";

export const metadata: Metadata = { title: "Sign in" };

export default function Page() {
  return <LoginPage />;
}
