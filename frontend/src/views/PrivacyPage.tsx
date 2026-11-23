"use client";

import React, { useEffect, useMemo, useRef, useState } from "react";
import { motion } from "framer-motion";
import {
  ShieldCheck,
  Lock,
  Eye,
  Database,
  Globe,
  UserCheck,
  FileCheck,
  KeyRound,
  AlertTriangle,
  Mail,
  ExternalLink,
  ChevronRight,
  ChevronUp,
  Info,
  Cookie,
} from "lucide-react";

/**
 * Privacy Policy Page (Dark theme with blue accents)
 * - React + TypeScript + Tailwind CSS
 * - Matches the look/feel of the Terms & Conditions page you have
 * - Color-coded sections via <Card variant> instead of ALL CAPS
 */

export type PrivacyPolicyProps = {
  companyName?: string;
  lastUpdated?: string; // e.g., "August 22, 2025"
  contactEmail?: string; // e.g., "privacy@yourcompany.com"
  dpoEmail?: string; // optional: dedicated DPO/Privacy contact
};

const sections = [
  { id: "overview", title: "Overview", icon: Info },
  { id: "scope", title: "Scope & Definitions", icon: FileCheck },
  { id: "collection", title: "Data We Collect", icon: Database },
  { id: "use", title: "How We Use Data", icon: KeyRound },
  { id: "cookies", title: "Cookies & Tracking", icon: Cookie },
  { id: "sharing", title: "Sharing & Transfers", icon: Globe },
  { id: "retention", title: "Data Retention", icon: Eye },
  { id: "rights", title: "Your Rights", icon: UserCheck },
  { id: "security", title: "Security", icon: Lock },
  { id: "children", title: "Children’s Privacy", icon: AlertTriangle },
  { id: "international", title: "International Transfers", icon: Globe },
  { id: "changes", title: "Changes to this Policy", icon: Info },
  { id: "contact", title: "Contact Us", icon: Mail },
] as const;

const containerVariants = {
  hidden: { opacity: 0 },
  show: { opacity: 1, transition: { staggerChildren: 0.06 } },
};

const itemVariants = {
  hidden: { opacity: 0, y: 12 },
  show: {
    opacity: 1,
    y: 0,
    transition: { duration: 0.35, ease: "easeOut" as const },
  },
};

export default function PrivacyPolicy({
  companyName = "Your Company",
  lastUpdated = "August 22, 2025",
  contactEmail = "privacy@example.com",
  dpoEmail,
}: PrivacyPolicyProps) {
  const [activeId, setActiveId] = useState<string>(sections[0].id);
  const [showTop, setShowTop] = useState(false);
  const observer = useRef<IntersectionObserver | null>(null);

  useEffect(() => {
    const opts: IntersectionObserverInit = {
      rootMargin: "-56% 0px -40% 0px",
      threshold: [0, 1],
    };
    observer.current = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) setActiveId(entry.target.id);
      });
    }, opts);

    sections.forEach((s) => {
      const el = document.getElementById(s.id);
      if (el) observer.current?.observe(el);
    });

    const onScroll = () => setShowTop(window.scrollY > 320);
    window.addEventListener("scroll", onScroll);
    onScroll();

    return () => {
      window.removeEventListener("scroll", onScroll);
      observer.current?.disconnect();
    };
  }, []);

  const year = useMemo(() => new Date().getFullYear(), []);

  const scrollTo = (id: string) => {
    const el = document.getElementById(id);
    if (el) el.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  return (
    <div className="bg-background text-foreground antialiased">
      {/* Header */}
      <header className="border-b border-border bg-surface">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
          <div className="flex items-center gap-3">
            <div className="grid h-9 w-9 place-items-center rounded-xl ring-1 ring-inset ring-primary/40">
              <ShieldCheck className="h-5 w-5 text-link" />
            </div>
            <div>
              <p className="text-xs text-muted-foreground">Privacy</p>
              <h1 className="text-lg font-semibold text-foreground">
                Privacy Policy
              </h1>
            </div>
          </div>

          <div className="hidden items-center gap-2 md:flex">
            <button
              onClick={() => window.print()}
              className="group inline-flex items-center gap-2 rounded-xl border border-border bg-surface px-3 py-2 text-sm text-foreground shadow-sm ring-1 ring-inset ring-border transition hover:border-primary/40 hover:ring-primary/40"
            >
              <ExternalLink className="h-4 w-4 transition group-hover:rotate-6" />
              <span>Print / Save as PDF</span>
            </button>
          </div>
        </div>
      </header>

      {/* Layout */}
      <div className="mx-auto grid max-w-6xl grid-cols-1 gap-8 px-4 py-10 lg:grid-cols-[18rem,1fr]">
        {/* TOC */}
        <aside className="hidden lg:block">
          <nav className="sticky top-24 space-y-3">
            <div className="rounded-xl border border-border bg-surface p-4 shadow-sm ring-1 ring-inset ring-border">
              <p className="mb-2 text-xs font-semibold text-muted-foreground">
                On this page
              </p>
              <ul className="space-y-1.5">
                {sections.map(({ id, title, icon: Icon }) => (
                  <li key={id}>
                    <button
                      onClick={() => scrollTo(id)}
                      className={[
                        "group flex w-full items-center gap-2 rounded-xl px-2 py-1.5 text-left text-sm transition",
                        activeId === id
                          ? "bg-primary/10 text-link ring-1 ring-inset ring-primary/40"
                          : "text-foreground hover:bg-muted hover:text-foreground",
                      ].join(" ")}
                    >
                      <Icon className="h-4 w-4 shrink-0 opacity-80" />
                      <span className="flex-1">{title}</span>
                      <ChevronRight className="h-3.5 w-3.5 opacity-60 group-hover:translate-x-0.5 transition" />
                    </button>
                  </li>
                ))}
              </ul>
            </div>

            <div className="rounded-xl border border-border p-4 ring-1 ring-inset ring-border">
              <p className="text-xs text-muted-foreground">Last updated</p>
              <p className="text-sm font-medium text-foreground">
                {lastUpdated}
              </p>
            </div>
          </nav>
        </aside>

        {/* Content */}
        <section>
          <motion.div variants={containerVariants} className="space-y-8">
            {/* Intro Banner */}
            <motion.div
              variants={itemVariants}
              className="rounded-xl border border-border bg-surface p-6 shadow-xl ring-1 ring-inset ring-border"
            >
              <div className="flex flex-wrap items-center justify-between gap-4">
                <div>
                  <h2 className="text-2xl font-semibold leading-tight text-foreground">
                    Privacy Policy for{" "}
                    <span className="text-link">{companyName}</span>
                  </h2>
                  <p className="mt-1 text-sm text-muted-foreground">
                    How we collect, use, and protect your information.
                  </p>
                </div>
                <div className="rounded-xl border border-primary/40 bg-primary/10 px-3 py-2 text-sm text-link ring-1 ring-inset ring-border">
                  <span className="font-medium">Effective:</span> {lastUpdated}
                </div>
              </div>
            </motion.div>

            {/* Overview */}
            <Card id="overview" title="Overview" variant="info">
              <p className="text-foreground">
                This Privacy Policy explains what personal data we collect, how
                we use it, and your choices. By using our websites, apps, or
                related services (the{" "}
                <span className="text-link font-medium">Services</span>), you
                agree to the practices described here.
              </p>
            </Card>

            {/* Scope & Definitions */}
            <Card id="scope" title="Scope & Definitions">
              <p className="text-foreground">
                This Policy applies to the Services offered by{" "}
                <span className="text-link font-medium">{companyName}</span>.
                "Personal Data" means any information related to an identified
                or identifiable natural person.
              </p>
            </Card>

            {/* Data We Collect */}
            <Card id="collection" title="Data We Collect">
              <ul className="list-outside list-disc space-y-2 pl-5 text-foreground">
                <li>
                  <span className="font-medium text-link">
                    Account & Contact
                  </span>
                  : name, email, phone, and authentication identifiers.
                </li>
                <li>
                  <span className="font-medium text-link">Usage</span>: pages
                  viewed, actions taken, device information, and approximate
                  location.
                </li>
                <li>
                  <span className="font-medium text-link">Payments</span>:
                  billing details processed by our payment provider (we do not
                  store full card numbers).
                </li>
                <li>
                  <span className="font-medium text-link">Support</span>:
                  messages, attachments, and feedback you submit.
                </li>
              </ul>
            </Card>

            {/* How We Use Data */}
            <Card id="use" title="How We Use Data">
              <ul className="list-outside list-disc space-y-2 pl-5 text-foreground">
                <li>
                  Provide and maintain the Services, including authentication
                  and troubleshooting.
                </li>
                <li>
                  Improve features and performance through analytics and
                  research.
                </li>
                <li>
                  Communicate with you about updates, security, and support.
                </li>
                <li>Comply with legal obligations and enforce our terms.</li>
              </ul>
              <p className="mt-2 text-foreground">
                Where applicable (e.g., in the EEA/UK), our{" "}
                <span className="text-link font-medium">lawful bases</span>{" "}
                include performance of a contract, legitimate interests,
                consent, and legal obligations.
              </p>
            </Card>

            {/* Cookies */}
            <Card id="cookies" title="Cookies & Tracking" variant="info">
              <p className="text-foreground">
                We use cookies and similar technologies to operate and secure
                the Services, remember preferences, and measure performance. You
                can control non-essential cookies in your browser or any consent
                tools we provide.
              </p>
              <ul className="mt-2 list-outside list-disc space-y-2 pl-5 text-foreground">
                <li>
                  <span className="font-medium">Essential</span> — required for
                  core features like login.
                </li>
                <li>
                  <span className="font-medium">Analytics</span> — help us
                  understand usage and improve.
                </li>
                <li>
                  <span className="font-medium">Preferences</span> — remember
                  settings like theme or language.
                </li>
              </ul>
            </Card>

            {/* Sharing & Transfers */}
            <Card id="sharing" title="Sharing & Transfers" variant="warning">
              <p className="text-foreground">
                We may share data with service providers (e.g., hosting,
                analytics, payment processing) under contracts that protect your
                information. We may disclose information to comply with law or
                to protect rights, safety, and property. If data is transferred
                across borders, we use appropriate safeguards.
              </p>
            </Card>

            {/* Retention */}
            <Card id="retention" title="Data Retention">
              <p className="text-foreground">
                We retain personal data only as long as necessary for the
                purposes described in this Policy, to comply with legal
                requirements, and to resolve disputes. Retention periods vary
                based on data type and context.
              </p>
            </Card>

            {/* Your Rights */}
            <Card id="rights" title="Your Rights" variant="info">
              <ul className="list-outside list-disc space-y-2 pl-5 text-foreground">
                <li>Access, update, or delete certain personal data.</li>
                <li>
                  Object to or restrict processing in certain circumstances.
                </li>
                <li>Data portability where technically feasible.</li>
                <li>Withdraw consent where processing relies on consent.</li>
              </ul>
              <p className="mt-2 text-foreground">
                To exercise rights, contact us at{" "}
                <a
                  href={`mailto:${dpoEmail || contactEmail}`}
                  className="font-medium text-link underline decoration-current underline-offset-4 hover:text-link"
                >
                  {dpoEmail || contactEmail}
                </a>
                . If you are in California or the EEA/UK, you may have
                additional rights under local law.
              </p>
            </Card>

            {/* Security */}
            <Card id="security" title="Security" variant="critical">
              <p className="text-foreground">
                We implement technical and organizational measures designed to
                protect your data (e.g., encryption in transit, access controls,
                and regular monitoring). No method of transmission or storage is
                completely secure.
              </p>
            </Card>

            {/* Children's Privacy */}
            <Card id="children" title="Children’s Privacy" variant="warning">
              <p className="text-foreground">
                Our Services are not directed to children under the age required
                by applicable law. We do not knowingly collect personal data
                from children. If you believe a child has provided us personal
                data, please contact us so we can take appropriate action.
              </p>
            </Card>

            {/* International Transfers */}
            <Card id="international" title="International Transfers">
              <p className="text-foreground">
                If we transfer personal data internationally, we use appropriate
                safeguards such as standard contractual clauses, and we assess
                local laws that may impact your rights.
              </p>
            </Card>

            {/* Changes */}
            <Card id="changes" title="Changes to this Policy">
              <p className="text-foreground">
                We may update this Policy from time to time. Material changes
                will be communicated through the Services or by other reasonable
                means. Your continued use of the Services after changes become
                effective indicates acceptance.
              </p>
            </Card>

            {/* Contact */}
            <Card id="contact" title="Contact Us">
              <p className="text-foreground">
                Questions about this Policy? Contact us at{" "}
                <a
                  href={`mailto:${contactEmail}`}
                  className="font-medium text-link underline decoration-current underline-offset-4 hover:text-link"
                >
                  {contactEmail}
                </a>
                {dpoEmail ? (
                  <>
                    {" "}
                    or our Data Protection Officer at{" "}
                    <a
                      href={`mailto:${dpoEmail}`}
                      className="font-medium text-link underline decoration-current underline-offset-4 hover:text-link"
                    >
                      {dpoEmail}
                    </a>
                    .
                  </>
                ) : (
                  "."
                )}
              </p>
            </Card>

            {/* Footer Note */}
            <footer className="mt-10 flex flex-col items-center justify-between gap-4 rounded-xl border border-border bg-surface px-6 py-6 text-sm text-muted-foreground ring-1 ring-inset ring-border md:flex-row">
              <p>
                © {year} {companyName}. All rights reserved.
              </p>
            </footer>
          </motion.div>
        </section>
      </div>

      {/* Back to top */}
      <button
        onClick={() => window.scrollTo({ top: 0, behavior: "smooth" })}
        className={[
          "fixed bottom-6 right-6 z-40 inline-flex items-center gap-2 rounded-xl border px-3 py-2 text-sm shadow-lg transition",
          "border-border bg-surface text-foreground ring-1 ring-inset ring-border",
          showTop ? "opacity-100" : "opacity-0 pointer-events-none",
        ].join(" ")}
        aria-label="Back to top"
      >
        <ChevronUp className="h-4 w-4" />
        <span>Top</span>
      </button>
    </div>
  );
}

function Card({
  id,
  title,
  variant = "default",
  children,
}: React.PropsWithChildren<{
  id: string;
  title: string;
  variant?: "default" | "info" | "warning" | "critical";
}>) {
  const scheme = {
    default: {
      border: "border-border",
      bg: "bg-surface",
      ring: "ring-border",
      title: "text-foreground",
      accent: "bg-primary/10",
    },
    info: {
      border: "border-primary/40",
      bg: "bg-primary/10",
      ring: "ring-primary/40",
      title: "text-link",
      accent: "bg-primary/10",
    },
    warning: {
      border: "border-warning/40",
      bg: "bg-warning-subtle",
      ring: "ring-warning/40",
      title: "text-warning",
      accent: "bg-warning-subtle",
    },
    critical: {
      border: "border-danger/40",
      bg: "bg-danger-subtle",
      ring: "ring-danger/40",
      title: "text-danger",
      accent: "bg-danger-subtle",
    },
  }[variant];

  return (
    <motion.article
      id={id}
      variants={itemVariants}
      className={`scroll-mt-28 rounded-xl border ${scheme.border} ${scheme.bg} p-6 shadow-xl ring-1 ring-inset ${scheme.ring}`}
    >
      <div className="mb-1 flex items-center gap-2">
        <span className={`h-2 w-2 rounded-full ${scheme.accent}`} />
        <h3 className={`text-xl font-semibold ${scheme.title}`}>{title}</h3>
      </div>
      <div className="mt-3 space-y-4 leading-7 text-foreground">{children}</div>
    </motion.article>
  );
}
