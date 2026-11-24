"use client";

import React, { useEffect, useMemo, useRef, useState } from "react";
import { motion } from "framer-motion";
import {
  ShieldCheck,
  Scale,
  Mail,
  ScrollText,
  ExternalLink,
  ChevronRight,
  ChevronUp,
  Info,
} from "lucide-react";

export type TermsAndConditionsProps = {
  companyName?: string;
  lastUpdated?: string;
  contactEmail?: string;
};

const sections = [
  { id: "introduction", title: "Introduction", icon: Info },
  { id: "acceptance", title: "Acceptance of Terms", icon: ShieldCheck },
  { id: "changes", title: "Changes to These Terms", icon: ScrollText },
  { id: "accounts", title: "Accounts & Security", icon: ShieldCheck },
  { id: "conduct", title: "Acceptable Use & Conduct", icon: ShieldCheck },
  { id: "ip", title: "Intellectual Property", icon: Scale },
  { id: "billing", title: "Payment & Billing", icon: Scale },
  { id: "termination", title: "Termination", icon: ShieldCheck },
  { id: "disclaimers", title: "Disclaimers", icon: Info },
  { id: "liability", title: "Limitation of Liability", icon: Scale },
  { id: "law", title: "Governing Law", icon: Scale },
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

export default function TermsAndConditions({
  companyName = "Your Company",
  lastUpdated = "August 22, 2025",
  contactEmail = "support@example.com",
}: TermsAndConditionsProps) {
  const [activeId, setActiveId] = useState<string>(sections[0].id);
  const [showTop, setShowTop] = useState(false);
  const observer = useRef<IntersectionObserver | null>(null);

  // Set up intersection observer for scrollspy
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

      {/* Page wrapper */}
      <header className="sticky top-0 z-40 border-b border-slate-800/70 backdrop-blur supports-[backdrop-filter]:bg-slate-950/50">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
          <div className="flex items-center gap-3">
            <div className="grid h-9 w-9 place-items-center rounded-xl bg-gradient-to-br from-sky-400/20 to-blue-500/20 ring-1 ring-inset ring-sky-500/30">
              <ShieldCheck className="h-5 w-5 text-sky-300" />
            </div>
            <div>
              <p className="text-xs uppercase tracking-widest text-slate-400">
                Legal
              </p>
              <h1 className="text-lg font-semibold text-slate-100">
                Terms & Conditions
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

      <div className="mx-auto grid max-w-6xl grid-cols-1 gap-8 px-4 py-10 lg:grid-cols-[18rem,1fr]">
        {/* TOC sidebar */}
        <aside className="hidden lg:block">
          <nav className="sticky top-24 space-y-3">
            <div className="rounded-2xl border border-slate-800/70 bg-slate-900/50 p-4 shadow-sm ring-1 ring-inset ring-white/5">
              <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-slate-400">
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
            <motion.div
              variants={itemVariants}
              className="rounded-3xl border border-slate-800/70 bg-slate-900/60 p-6 shadow-xl ring-1 ring-inset ring-white/5"
            >
              <div className="flex flex-wrap items-center justify-between gap-4">
                <div>
                  <h2 className="text-2xl font-semibold leading-tight text-slate-100">
                    Terms & Conditions for {companyName}
                  </h2>
                  <p className="mt-1 text-sm text-slate-400">
                    Please read these terms carefully before using our services.
                  </p>
                </div>
                <div className="rounded-xl border border-sky-500/30 bg-sky-500/10 px-3 py-2 text-sm text-sky-200 ring-1 ring-inset ring-white/5">
                  <span className="font-medium">Effective:</span> {lastUpdated}
                </div>
              </div>
            </motion.div>

            {/* Intro */}
            <motion.article
              id="introduction"
              variants={itemVariants}
              className="scroll-mt-28 rounded-3xl border border-slate-800/70 bg-gradient-to-b from-slate-900/70 to-slate-950/60 p-6 ring-1 ring-inset ring-white/5"
            >
              <SectionHeading
                title="Introduction"
                subtitle="Who we are and what this page covers"
              />
              <p className="mt-3 leading-7 text-slate-300">
                These Terms & Conditions (the "Terms") govern your access to and
                use of the websites, applications, APIs, and services provided
                by {companyName}
                (collectively, the "Services"). By accessing or using the
                Services, you agree to be bound by these Terms.
              </p>
            </motion.article>

            {/* Acceptance */}
            <Card id="acceptance" title="Acceptance of Terms" variant="warning">
              <ul className="list-outside list-disc space-y-2 pl-5 text-slate-300">
                <li>
                  You must be able to form a binding contract to use the
                  Services.
                </li>
                <li>
                  If you use the Services on behalf of an entity, you represent
                  that you have authority to bind that entity, and "you" refers
                  to the entity.
                </li>
                <li>
                  If you do not agree to these Terms, do not access or use the
                  Services.
                </li>
              </ul>
            </Card>

            {/* Changes */}
            <Card id="changes" title="Changes to These Terms" variant="info">
              <p className="text-slate-300">
                We may update these Terms from time to time. Material changes
                will be notified through the Services or by other reasonable
                means. Changes become effective upon posting unless stated
                otherwise. Your continued use of the Services constitutes
                acceptance of the updated Terms.
              </p>
            </Card>

            {/* Accounts & Security */}
            <Card id="accounts" title="Accounts & Security" variant="critical">
              <ul className="list-outside list-disc space-y-2 pl-5 text-slate-300">
                <li>Provide accurate and complete registration information.</li>
                <li>
                  Maintain the security of your account credentials; you are
                  responsible for all activities under your account.
                </li>
                <li>
                  Notify us immediately of any unauthorized use or security
                  breach.
                </li>
              </ul>
            </Card>

            {/* Acceptable Use */}
            <Card
              id="conduct"
              title="Acceptable Use & Conduct"
              variant="warning"
            >
              <p className="text-slate-300">
                You agree not to misuse the Services. Prohibited activities
                include, but are not limited to:
              </p>
              <ul className="mt-2 list-outside list-disc space-y-2 pl-5 text-slate-300">
                <li>Violating any applicable law or regulation.</li>
                <li>
                  Infringing or misappropriating intellectual property or
                  privacy rights.
                </li>
                <li>
                  Attempting to access or probe non-public areas of the
                  Services.
                </li>
                <li>
                  Interfering with or disrupting the integrity or performance of
                  the Services.
                </li>
                <li>Uploading malicious code or content.</li>
              </ul>
            </Card>

            {/* Intellectual Property */}
            <Card id="ip" title="Intellectual Property" variant="info">
              <p className="text-slate-300">
                The Services, including all content, features, and
                functionality, are owned by {companyName} or its licensors and
                are protected by intellectual property laws. Except as expressly
                permitted, you may not copy, modify, distribute, sell, or lease
                any part of the Services.
              </p>
            </Card>

            {/* Billing */}
            <Card id="billing" title="Payment & Billing" variant="warning">
              <ul className="list-outside list-disc space-y-2 pl-5 text-slate-300">
                <li>
                  Fees are due as stated at purchase and are generally
                  non-refundable unless required by law.
                </li>
                <li>
                  We may change prices with reasonable prior notice where
                  required.
                </li>
                <li>
                  Taxes may apply based on your billing address and local
                  regulations.
                </li>
              </ul>
            </Card>

            {/* Cookies */}
            <Card id="cookies" title="Cookies & Tracking" variant="info">
              <p className="text-slate-300">
                We use cookies and similar technologies (such as local storage,
                pixels, and SDKs) to operate and personalize the Services,
                analyze traffic, and, where applicable, support marketing.
              </p>
              <ul className="mt-2 list-outside list-disc space-y-2 pl-5 text-slate-300">
                <li>
                  <span className="font-medium">Essential cookies</span> are
                  required for core functionality such as authentication and
                  security and cannot be disabled.
                </li>
                <li>
                  <span className="font-medium">Analytics cookies</span> help us
                  understand how the Services are used so we can improve
                  performance and features.
                </li>
                <li>
                  <span className="font-medium">Preference cookies</span>{" "}
                  remember your settings (e.g., language or theme).
                </li>
              </ul>
              <p className="mt-2 text-slate-300">
                You may control non-essential cookies through your browser
                settings or any consent tools we provide. For more details,
                contact us at {contactEmail}.
              </p>
            </Card>

            {/* Termination */}
            <Card id="termination" title="Termination" variant="critical">
              <p className="text-slate-300">
                We may suspend or terminate your access to the Services if you
                violate these Terms or if we are investigating suspected
                misconduct. Upon termination, your right to use the Services
                will cease immediately.
              </p>
            </Card>

            {/* Disclaimers */}
            <Card id="disclaimers" title="Disclaimers" variant="warning">
              <p className="text-slate-300">
                THE SERVICES ARE PROVIDED ON AN "AS IS" AND "AS AVAILABLE" BASIS
                WITHOUT WARRANTIES OF ANY KIND, WHETHER EXPRESS, IMPLIED, OR
                STATUTORY, INCLUDING WARRANTIES OF MERCHANTABILITY, FITNESS FOR
                A PARTICULAR PURPOSE, AND NON-INFRINGEMENT.
              </p>
            </Card>

            {/* Limitation of Liability */}
            <Card
              id="liability"
              title="Limitation of Liability"
              variant="critical"
            >
              <p className="text-slate-300">
                TO THE MAXIMUM EXTENT PERMITTED BY LAW, {companyName} AND ITS
                AFFILIATES SHALL NOT BE LIABLE FOR ANY INDIRECT, INCIDENTAL,
                SPECIAL, CONSEQUENTIAL, OR PUNITIVE DAMAGES, OR ANY LOSS OF
                PROFITS OR REVENUES, WHETHER INCURRED DIRECTLY OR INDIRECTLY, OR
                ANY LOSS OF DATA, USE, GOODWILL, OR OTHER INTANGIBLE LOSSES.
              </p>
            </Card>

            {/* Governing Law */}
            <Card id="law" title="Governing Law">
              <p className="text-slate-300">
                These Terms shall be governed by and construed in accordance
                with the laws applicable in your place of residence or, if
                specified elsewhere in your customer agreement, the laws of that
                jurisdiction, without regard to its conflict of law principles.
              </p>
            </Card>

            {/* Contact */}
            <Card id="contact" title="Contact Us">
              <p className="text-slate-300">
                Questions about these Terms? Reach out to us at{" "}
                <a
                  href={`mailto:${contactEmail}`}
                  className="font-medium text-sky-300 underline decoration-sky-500/40 underline-offset-4 hover:text-sky-200"
                >
                  {contactEmail}
                </a>
                .
              </p>
            </Card>

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

function SectionHeading({
  title,
  subtitle,
}: {
  title: string;
  subtitle?: string;
}) {
  return (
    <div className="mb-1">
      <h3 className="text-xl font-semibold text-slate-100">{title}</h3>
      {subtitle ? <p className="text-sm text-slate-400">{subtitle}</p> : null}
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
