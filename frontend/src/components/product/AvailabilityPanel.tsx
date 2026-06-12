import { useQuery } from "@tanstack/react-query";
import { motion, useReducedMotion, type Variants } from "framer-motion";
import { Clock, MapPin, Store, Truck } from "lucide-react";
import { catalogApi } from "../../api/catalog";
import { useUserLocation } from "../../hooks/useUserLocation";

interface Props {
  marketplaceId: string;
  productId: string;
  variantId: string | null;
  /** When the parent already knows the product is unavailable (purchasable=false), skip rendering. */
  unavailable: boolean;
}

const FADE_IN_UP: Variants = {
  hidden: { opacity: 0, y: 12 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.4 } },
};

const FADE_IN_NO_MOTION: Variants = {
  hidden: { opacity: 0 },
  visible: { opacity: 1, transition: { duration: 0.2 } },
};

const DATE_FORMAT: Intl.DateTimeFormatOptions = { weekday: "short", month: "short", day: "numeric" };

function toUtcDate(isoStr: string): Date {
  // Ensure the string is treated as UTC by appending Z if no timezone offset present.
  return new Date(/[Zz]|[+-]\d{2}:\d{2}$/.test(isoStr) ? isoStr : `${isoStr}Z`);
}

function formatEtaRange(min: string | null, max: string | null): string | null {
  if (!min || !max) return null;
  const minDate = toUtcDate(min);
  const maxDate = toUtcDate(max);
  if (Number.isNaN(minDate.getTime()) || Number.isNaN(maxDate.getTime())) return null;
  const fmt = new Intl.DateTimeFormat(undefined, DATE_FORMAT);
  if (min === max) return fmt.format(minDate);
  return `${fmt.format(minDate)} – ${fmt.format(maxDate)}`;
}

function formatDistance(km: number | null): string | null {
  if (km == null) return null;
  if (km < 1) return "less than 1 km away";
  if (km < 10) return `${km.toFixed(1)} km away`;
  return `${Math.round(km)} km away`;
}

export default function AvailabilityPanel({ marketplaceId, productId, variantId, unavailable }: Props) {
  const prefersReducedMotion = useReducedMotion();
  const variants = prefersReducedMotion ? FADE_IN_NO_MOTION : FADE_IN_UP;
  const { coords, permission, isRequesting, request } = useUserLocation();

  const roundedLat = coords ? Math.round(coords.lat * 100) / 100 : null;
  const roundedLng = coords ? Math.round(coords.lng * 100) / 100 : null;

  const { data, isLoading, isError } = useQuery({
    queryKey: [
      "availability",
      marketplaceId,
      productId,
      variantId ?? null,
      roundedLat,
      roundedLng,
    ],
    queryFn: () =>
      catalogApi
        .getAvailability(marketplaceId, productId, {
          variantId: variantId ?? undefined,
          lat: coords?.lat,
          lng: coords?.lng,
        })
        .then((r) => r.data),
    enabled: !unavailable,
    staleTime: 5 * 60 * 1000,
  });

  if (unavailable) return null;

  const wrapperClass =
    "rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm p-4";

  if (isLoading) {
    return (
      <motion.div variants={variants} initial="hidden" animate="visible" className={wrapperClass}>
        <div className="flex flex-col gap-3">
          <SkeletonRow />
          <SkeletonRow />
        </div>
      </motion.div>
    );
  }

  if (isError || !data) {
    return null;
  }

  if (!data.inStock) {
    return null;
  }

  const etaRange = formatEtaRange(data.etaDateMin, data.etaDateMax);
  const showLocationCta = permission === "prompt" || permission === "unknown";
  const distanceLabel = formatDistance(data.nearestSource?.distanceKm ?? null);

  return (
    <motion.div variants={variants} initial="hidden" animate="visible" className={wrapperClass}>
      <div className="flex flex-col gap-3">
        {/* ETA */}
        <Row icon={<Truck className="w-4 h-4" />}>
          {etaRange ? (
            <span className="text-white/80">
              Arrives <span className="font-semibold text-white">{etaRange}</span>
              {data.etaDaysMin != null && data.etaDaysMax != null && (
                <span className="text-white/55">
                  {" "}({data.etaDaysMin === data.etaDaysMax
                    ? `${data.etaDaysMin} day${data.etaDaysMin === 1 ? "" : "s"}`
                    : `${data.etaDaysMin}–${data.etaDaysMax} days`})
                </span>
              )}
            </span>
          ) : (
            <span className="text-white/65">Delivery estimate unavailable</span>
          )}
        </Row>

        {/* Nearest source */}
        {data.nearestSource && (
          <Row icon={<MapPin className="w-4 h-4" />}>
            <span className="text-white/80">
              Ships from{" "}
              <span className="font-semibold text-white">{data.nearestSource.name}</span>
              {data.nearestSource.city && (
                <span className="text-white/55">, {data.nearestSource.city}</span>
              )}
              {distanceLabel && (
                <span className="text-white/55"> · {distanceLabel}</span>
              )}
            </span>
          </Row>
        )}

        {/* Pickup */}
        {data.pickup && (
          <Row icon={<Store className="w-4 h-4" />}>
            <span className="text-white/80">
              Pick up at{" "}
              <span className="font-semibold text-white">{data.pickup.name}</span>
              {data.pickup.readyHours != null && (
                <span className="text-white/55">
                  {" "}— ready in {data.pickup.readyHours} hr{data.pickup.readyHours === 1 ? "" : "s"}
                </span>
              )}
            </span>
          </Row>
        )}

        {/* Location CTA / status */}
        {showLocationCta ? (
          <button
            type="button"
            onClick={request}
            disabled={isRequesting}
            className="self-start inline-flex items-center gap-1.5 text-xs font-semibold text-sky-300 hover:text-sky-200 transition-colors disabled:opacity-50"
          >
            <Clock className="w-3.5 h-3.5" />
            {isRequesting ? "Locating…" : "Use my location for an accurate estimate"}
          </button>
        ) : permission === "denied" ? (
          <p className="text-xs text-white/45">Showing default estimates — location access was denied.</p>
        ) : permission === "unavailable" ? (
          <p className="text-xs text-white/45">Showing default estimates — location is unavailable in this browser.</p>
        ) : null}
      </div>
    </motion.div>
  );
}

interface RowProps {
  icon: React.ReactNode;
  children: React.ReactNode;
}

function Row({ icon, children }: RowProps) {
  return (
    <div className="flex items-start gap-2.5 text-sm">
      <span className="flex-shrink-0 mt-0.5 text-sky-200">{icon}</span>
      <div className="leading-relaxed">{children}</div>
    </div>
  );
}

function SkeletonRow() {
  return (
    <div className="flex items-center gap-2.5">
      <div className="w-4 h-4 rounded bg-white/[0.05] animate-pulse" />
      <div className="h-4 flex-1 max-w-xs rounded bg-white/[0.05] animate-pulse" />
    </div>
  );
}

