import type { Metadata, Viewport } from "next";
import Navbar from "../components/Navbar";
import Footer from "../components/Footer";
import FeedbackButton from "../components/feedback/FeedbackButton";
import { getDefaultMarketplaceId } from "../configuration/server";
import Providers from "./providers";
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
  themeColor: "#020617",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
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
