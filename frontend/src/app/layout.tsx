import type { Metadata, Viewport } from "next";
import { GeistSans } from "geist/font/sans";
import { GeistMono } from "geist/font/mono";
import Navbar from "../components/Navbar";
import Footer from "../components/Footer";
import FeedbackButton from "../components/feedback/FeedbackButton";
import { getDefaultMarketplaceId } from "../configuration/server";
import Providers from "./providers";
import { FORCE_ALL_DARK } from "../lib/theme/themeConfig";
import { themeScript } from "../lib/theme/themeScript";
import { cn } from "../lib/cn";
import "./globals.css";

const siteUrl = process.env.NEXT_PUBLIC_FRONTEND_URL?.trim();

export const metadata: Metadata = {
  ...(siteUrl ? { metadataBase: new URL(siteUrl) } : {}),
  title: { default: "ShopWave", template: "%s | ShopWave" },
  description:
    "Discover products, merchants, and curated collections on ShopWave.",
  applicationName: "ShopWave",
};

export const viewport: Viewport = {
  // Keep in step with --background in globals.css.
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#fafafa" },
    { media: "(prefers-color-scheme: dark)", color: "#09090b" },
  ],
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    // The inline theme script sets `class` and `style` on <html> before React
    // hydrates, so those two attributes are expected to differ from the server.
    <html
      lang="en"
      suppressHydrationWarning
      className={cn(
        GeistSans.variable,
        GeistMono.variable,
        // While every route is forced dark, render it on the server too so
        // pages look right before (or without) JavaScript.
        FORCE_ALL_DARK && "dark"
      )}
    >
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeScript }} />
      </head>
      {/* bg-slate-950 is the legacy page colour; the app shell PR removes it. */}
      <body className="bg-slate-950 antialiased">
        <Providers defaultMarketplaceId={getDefaultMarketplaceId()}>
          <Navbar />
          <div className="min-h-screen overflow-y-auto overflow-x-hidden">
            {children}
          </div>
          <FeedbackButton />
          <Footer />
        </Providers>
      </body>
    </html>
  );
}
