import { SiteShell } from "../../components/layout/SiteShell";

// Sign-in screens keep the site chrome for now; the auth redesign replaces this
// with a focused layout.
export default function AuthLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <SiteShell>{children}</SiteShell>;
}
