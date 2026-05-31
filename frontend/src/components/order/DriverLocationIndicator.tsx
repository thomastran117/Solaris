import { MapPin } from "lucide-react";
import type { DriverLocation } from "../../types/order";

interface Props {
  location: DriverLocation | null;
}

function formatRelativeTime(timestamp: string): string {
  const diff = Math.floor((Date.now() - new Date(timestamp).getTime()) / 1000);
  if (diff < 60) return `Updated ${diff}s ago`;
  if (diff < 3600) return `Updated ${Math.floor(diff / 60)}m ago`;
  return `Updated ${Math.floor(diff / 3600)}h ago`;
}

export default function DriverLocationIndicator({ location }: Props) {
  if (!location) return null;

  const mapsUrl = `https://maps.google.com/?q=${location.lat},${location.lng}`;

  return (
    <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-5">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="bg-green-500/15 border border-white/10 rounded-xl p-2">
            <MapPin className="h-5 w-5 text-green-400" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="relative flex h-2 w-2">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75" />
                <span className="relative inline-flex rounded-full h-2 w-2 bg-green-500" />
              </span>
              <p className="text-sm font-semibold text-white">Driver en route</p>
            </div>
            <p className="text-xs text-white/50 mt-0.5">{formatRelativeTime(location.timestamp)}</p>
          </div>
        </div>
        <a
          href={mapsUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="text-xs text-sky-400 hover:text-sky-300 transition border border-white/10 rounded-lg px-3 py-1.5 hover:bg-white/5"
        >
          View map
        </a>
      </div>
    </div>
  );
}
