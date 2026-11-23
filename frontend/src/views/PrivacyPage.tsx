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
  show: { opacity: 1, y: 0, transition: { duration: 0.35, ease: "easeOut" as const } },
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
    <div className="min-h-screen bg-[#0B1220] text-slate-200 antialiased">
      {/* Decorative background */}
      <div
        aria-hidden
        className="pointer-events-none fixed inset-0 -z-10 overflow-hidden"
      >
        <div className="absolute -top-24 -left-24 h-96 w-96 rounded-full bg-blue-500/10 blur-3xl" />
        <div className="absolute bottom-0 right-0 h-[28rem] w-[28rem] rounded-full bg-sky-400/10 blur-3xl" />
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,rgba(14,165,233,0.12),transparent_60%)]" />
        <div className="absolute inset-0 bg-[linear-gradient(transparent,rgba(2,6,23,0.6))]" />
      </div>

      {/* Header */}
      <header className="sticky top-0 z-40 border-b border-slate-800/70 backdrop-blur supports-[backdrop-filter]:bg-slate-950/50">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
          <div className="flex items-center gap-3">
            <div className="grid h-9 w-9 place-items-center rounded-xl bg-gradient-to-br from-sky-400/20 to-blue-500/20 ring-1 ring-inset ring-sky-500/30">
              <ShieldCheck className="h-5 w-5 text-sky-300" />
            </div>
            <div>
              <p className="text-xs tracking-widest text-slate-400">Privacy</p>
              <h1 className="text-lg font-semibold text-slate-100">
                Privacy Policy
              </h1>
            </div>
          </div>

          <div className="hidden items-center gap-2 md:flex">
            <button
              onClick={() => window.print()}
              className="group inline-flex items-center gap-2 rounded-xl border border-slate-800 bg-slate-900/60 px-3 py-2 text-sm text-slate-200 shadow-sm ring-1 ring-inset ring-white/5 transition hover:border-sky-600/50 hover:ring-sky-500/30"
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
            <div className="rounded-2xl border border-slate-800/70 bg-slate-900/50 p-4 shadow-sm ring-1 ring-inset ring-white/5">
              <p className="mb-2 text-xs font-semibold tracking-wider text-slate-400">
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
                          ? "bg-sky-500/15 text-sky-200 ring-1 ring-inset ring-sky-500/30"
                          : "text-slate-300 hover:bg-slate-800/60 hover:text-slate-100",
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

            <div className="rounded-2xl border border-slate-800/70 bg-gradient-to-b from-slate-900/60 to-slate-950/60 p-4 ring-1 ring-inset ring-white/5">
              <p className="text-xs text-slate-400">Last updated</p>
              <p className="text-sm font-medium text-slate-100">
                {lastUpdated}
              </p>
            </div>
          </nav>
        </aside>

        {/* Content */}
        <section>
          <motion.div
            variants={containerVariants}
            initial="hidden"
            animate="show"
            className="space-y-8"
          >
            {/* Intro Banner */}
            <motion.div
              variants={itemVariants}
              className="rounded-3xl border border-slate-800/70 bg-slate-900/60 p-6 shadow-xl ring-1 ring-inset ring-white/5"
            >
              <div className="flex flex-wrap items-center justify-between gap-4">
                <div>
                  <h2 className="text-2xl font-semibold leading-tight text-slate-100">
                    Privacy Policy for{" "}
                    <span className="text-sky-300">{companyName}</span>
                  </h2>
                  <p className="mt-1 text-sm text-slate-400">
                    How we collect, use, and protect your information.
                  </p>
                </div>
                <div className="rounded-xl border border-sky-500/30 bg-sky-500/10 px-3 py-2 text-sm text-sky-200 ring-1 ring-inset ring-white/5">
                  <span className="font-medium">Effective:</span> {lastUpdated}
                </div>
              </div>
            </motion.div>

            {/* Overview */}
            <Card id="overview" title="Overview" variant="info">
              <p className="text-slate-300">
                This Privacy Policy explains what personal data we collect, how
                we use it, and your choices. By using our websites, apps, or
                related services (the{" "}
                <span className="text-sky-300 font-medium">Services</span>), you
                agree to the practices described here.
              </p>
            </Card>

            {/* Scope & Definitions */}
            <Card id="scope" title="Scope & Definitions">
              <p className="text-slate-300">
                This Policy applies to the Services offered by{" "}
                <span className="text-sky-300 font-medium">{companyName}</span>.
                "Personal Data" means any information related to an identified
                or identifiable natural person.
              </p>
            </Card>

            {/* Data We Collect */}
            <Card id="collection" title="Data We Collect">
              <ul className="list-outside list-disc space-y-2 pl-5 text-slate-300">
                <li>
                  <span className="font-medium text-sky-300">
                    Account & Contact
                  </span>
                  : name, email, phone, and authentication identifiers.
                </li>
                <li>
                  <span className="font-medium text-sky-300">Usage</span>: pages
                  viewed, actions taken, device information, and approximate
                  location.
                </li>
                <li>
                  <span className="font-medium text-sky-300">Payments</span>:
                  billing details processed by our payment provider (we do not
                  store full card numbers).
                </li>
                <li>
                  <span className="font-medium text-sky-300">Support</span>:
                  messages, attachments, and feedback you submit.
                </li>
              </ul>
            </Card>

            {/* How We Use Data */}
            <Card id="use" title="How We Use Data">
              <ul className="list-outside list-disc space-y-2 pl-5 text-slate-300">
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
              <p className="mt-2 text-slate-300">
                Where applicable (e.g., in the EEA/UK), our{" "}
                <span className="text-sky-300 font-medium">lawful bases</span>{" "}
                include performance of a contract, legitimate interests,
                consent, and legal obligations.
              </p>
            </Card>

            {/* Cookies */}
            <Card id="cookies" title="Cookies & Tracking" variant="info">
              <p className="text-slate-300">
                We use cookies and similar technologies to operate and secure
                the Services, remember preferences, and measure performance. You
                can control non-essential cookies in your browser or any consent
                tools we provide.
              </p>
              <ul className="mt-2 list-outside list-disc space-y-2 pl-5 text-slate-300">
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
              <p className="text-slate-300">
                We may share data with service providers (e.g., hosting,
                analytics, payment processing) under contracts that protect your
                information. We may disclose information to comply with law or
                to protect rights, safety, and property. If data is transferred
                across borders, we use appropriate safeguards.
              </p>
            </Card>

            {/* Retention */}
            <Card id="retention" title="Data Retention">
              <p className="text-slate-300">
                We retain personal data only as long as necessary for the
                purposes described in this Policy, to comply with legal
                requirements, and to resolve disputes. Retention periods vary
                based on data type and context.
              </p>
            </Card>

            {/* Your Rights */}
            <Card id="rights" title="Your Rights" variant="info">
              <ul className="list-outside list-disc space-y-2 pl-5 text-slate-300">
                <li>Access, update, or delete certain personal data.</li>
                <li>
                  Object to or restrict processing in certain circumstances.
                </li>
                <li>Data portability where technically feasible.</li>
                <li>Withdraw consent where processing relies on consent.</li>
              </ul>
              <p className="mt-2 text-slate-300">
                To exercise rights, contact us at{" "}
                <a
                  href={`mailto:${dpoEmail || contactEmail}`}
                  className="font-medium text-sky-300 underline decoration-sky-500/40 underline-offset-4 hover:text-sky-200"
                >
                  {dpoEmail || contactEmail}
                </a>
                . If you are in California or the EEA/UK, you may have
                additional rights under local law.
              </p>
            </Card>

            {/* Security */}
            <Card id="security" title="Security" variant="critical">
              <p className="text-slate-300">
                We implement technical and organizational measures designed to
                protect your data (e.g., encryption in transit, access controls,
                and regular monitoring). No method of transmission or storage is
                completely secure.
              </p>
            </Card>

            {/* Children's Privacy */}
            <Card id="children" title="Children’s Privacy" variant="warning">
              <p className="text-slate-300">
                Our Services are not directed to children under the age required
                by applicable law. We do not knowingly collect personal data
                from children. If you believe a child has provided us personal
                data, please contact us so we can take appropriate action.
              </p>
            </Card>

            {/* International Transfers */}
            <Card id="international" title="International Transfers">
              <p className="text-slate-300">
                If we transfer personal data internationally, we use appropriate
                safeguards such as standard contractual clauses, and we assess
                local laws that may impact your rights.
              </p>
            </Card>

            {/* Changes */}
            <Card id="changes" title="Changes to this Policy">
              <p className="text-slate-300">
                We may update this Policy from time to time. Material changes
                will be communicated through the Services or by other reasonable
                means. Your continued use of the Services after changes become
                effective indicates acceptance.
              </p>
            </Card>

            {/* Contact */}
            <Card id="contact" title="Contact Us">
              <p className="text-slate-300">
                Questions about this Policy? Contact us at{" "}
                <a
                  href={`mailto:${contactEmail}`}
                  className="font-medium text-sky-300 underline decoration-sky-500/40 underline-offset-4 hover:text-sky-200"
                >
                  {contactEmail}
                </a>
                {dpoEmail ? (
                  <>
                    {" "}
                    or our Data Protection Officer at{" "}
                    <a
                      href={`mailto:${dpoEmail}`}
                      className="font-medium text-sky-300 underline decoration-sky-500/40 underline-offset-4 hover:text-sky-200"
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
            <footer className="mt-10 flex flex-col items-center justify-between gap-4 rounded-3xl border border-slate-800/70 bg-slate-900/50 px-6 py-6 text-sm text-slate-400 ring-1 ring-inset ring-white/5 md:flex-row">
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
          "fixed bottom-6 right-6 z-40 inline-flex items-center gap-2 rounded-2xl border px-3 py-2 text-sm shadow-lg transition",
          "border-slate-800 bg-slate-900/70 text-slate-200 ring-1 ring-inset ring-white/5",
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
      border: "border-slate-800/70",
      bg: "bg-slate-900/60",
      ring: "ring-white/5",
      title: "text-slate-100",
      accent: "bg-sky-500/25",
    },
    info: {
      border: "border-sky-500/30",
      bg: "bg-sky-500/10",
      ring: "ring-sky-500/20",
      title: "text-sky-100",
      accent: "bg-sky-500/40",
    },
    warning: {
      border: "border-amber-500/30",
      bg: "bg-amber-500/10",
      ring: "ring-amber-500/20",
      title: "text-amber-100",
      accent: "bg-amber-500/40",
    },
    critical: {
      border: "border-rose-500/30",
      bg: "bg-rose-500/10",
      ring: "ring-rose-500/20",
      title: "text-rose-100",
      accent: "bg-rose-500/40",
    },
  }[variant];

  return (
    <motion.article
      id={id}
      variants={itemVariants}
      className={`scroll-mt-28 rounded-3xl border ${scheme.border} ${scheme.bg} p-6 shadow-xl ring-1 ring-inset ${scheme.ring}`}
    >
      <div className="mb-1 flex items-center gap-2">
        <span className={`h-2 w-2 rounded-full ${scheme.accent}`} />
        <h3 className={`text-xl font-semibold ${scheme.title}`}>{title}</h3>
      </div>
      <div className="mt-3 space-y-4 leading-7 text-slate-200">{children}</div>
    </motion.article>
  );
}
