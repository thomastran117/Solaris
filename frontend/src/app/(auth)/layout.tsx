import { AuthShell } from "../../components/auth/AuthShell";

// Sign-in, sign-up, verification and invite screens use a focused layout
// without the site navigation.
export default function AuthLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <AuthShell>{children}</AuthShell>;
}
