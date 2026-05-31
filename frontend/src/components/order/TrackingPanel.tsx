import { useState } from "react";
import { Copy, Check, ExternalLink } from "lucide-react";

interface Props {
  trackingNumber: string;
  carrier: string | null;
}

const CARRIER_LINKS: Record<string, string> = {
  UPS: "https://www.ups.com/track?tracknum=",
  FEDEX: "https://www.fedex.com/apps/fedextrack/?tracknumbers=",
  USPS: "https://tools.usps.com/go/TrackConfirmAction?tLabels=",
  DHL: "https://www.dhl.com/en/express/tracking.html?AWB=",
};

function carrierTrackingUrl(trackingNumber: string, carrier: string | null): string | null {
  if (!carrier) return null;
  const key = carrier.toUpperCase();
  const base = CARRIER_LINKS[key];
  return base ? `${base}${encodeURIComponent(trackingNumber)}` : null;
}

export default function TrackingPanel({ trackingNumber, carrier }: Props) {
  const [copied, setCopied] = useState(false);
  const url = carrierTrackingUrl(trackingNumber, carrier);

  const handleCopy = () => {
    navigator.clipboard.writeText(trackingNumber).catch(() => {});
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-5 space-y-3">
      <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90">Tracking</p>
      {carrier && (
        <p className="text-sm text-white/70">Carrier: <span className="text-white font-medium">{carrier}</span></p>
      )}
      <div className="flex items-center gap-2 bg-white/[0.04] border border-white/10 rounded-xl px-3 py-2">
        <span className="flex-1 text-sm font-mono text-white select-all">{trackingNumber}</span>
        <button onClick={handleCopy} className="text-white/50 hover:text-white transition">
          {copied ? <Check className="h-4 w-4 text-green-400" /> : <Copy className="h-4 w-4" />}
        </button>
      </div>
      {url ? (
        <a
          href={url}
          target="_blank"
          rel="noopener noreferrer"
          className="flex items-center gap-1.5 text-sm text-sky-300 hover:text-sky-200 transition"
        >
          Track on {carrier} <ExternalLink className="h-3.5 w-3.5" />
        </a>
      ) : (
        <p className="text-xs text-white/40">No direct tracking link available for this carrier.</p>
      )}
    </div>
  );
}
