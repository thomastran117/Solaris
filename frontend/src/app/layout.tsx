import type { Metadata, Viewport } from "next";
import { GeistSans } from "geist/font/sans";
import { GeistMono } from "geist/font/mono";
import { getDefaultMarketplaceId } from "../configuration/server";
import Providers from "./providers";
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
    // eslint-disable-next-line shopwave/no-raw-palette -- browser meta tags can't read CSS variables
    { media: "(prefers-color-scheme: light)", color: "#fafafa" },
    // eslint-disable-next-line shopwave/no-raw-palette -- browser meta tags can't read CSS variables
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
      className={cn(GeistSans.variable, GeistMono.variable)}
    >
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeScript }} />
      </head>
      <body>
        <a
          href="#main"
          className="sr-only rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-[60]"
        >
          Skip to content
        </a>
        {/* Route-group layouts add the chrome: SiteShell for storefront, auth
            and account pages, AdminShell for the seller workspace. */}
        <Providers defaultMarketplaceId={getDefaultMarketplaceId()}>
          {children}
        </Providers>
      </body>
    </html>
  );
}
