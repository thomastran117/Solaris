import { useState, useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import { MapPin, Clock, Loader2, AlertCircle } from "lucide-react";
import { locationsApi } from "../../api/locations";
import type { NearbyPickupLocation } from "../../types/order";

interface Props {
  companyId: string;
  selectedId: string | null;
  onSelect: (locationId: string) => void;
}

export default function PickupLocationPicker({
  companyId,
  selectedId,
  onSelect,
}: Props) {
  const [coords, setCoords] = useState<{ lat: number; lng: number } | null>(
    null
  );
  const [geoError, setGeoError] = useState(false);
  const [geoLoading, setGeoLoading] = useState(true);

  useEffect(() => {
    if (!navigator.geolocation) {
      setGeoError(true);
      setGeoLoading(false);
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setCoords({ lat: pos.coords.latitude, lng: pos.coords.longitude });
        setGeoLoading(false);
      },
      () => {
        setGeoError(true);
        setGeoLoading(false);
      }
    );
  }, []);

  const { data: locations, isLoading: queryLoading } = useQuery({
    queryKey: ["nearby-pickup", companyId, coords?.lat, coords?.lng],
    queryFn: () =>
      locationsApi
        .nearbyPickup(companyId, coords!.lat, coords!.lng)
        .then((r) => r.data),
    enabled: !!coords,
    staleTime: 5 * 60_000,
  });

  if (geoLoading) {
    return (
      <div className="flex items-center gap-2 text-sm text-muted-foreground py-4">
        <Loader2 className="h-4 w-4 animate-spin" /> Detecting your location…
      </div>
    );
  }

  if (geoError) {
    return (
      <div className="flex items-center gap-2 text-sm text-warning py-4 rounded-xl border border-warning/40 bg-warning-subtle px-4">
        <AlertCircle className="h-4 w-4 shrink-0" />
        Location permission denied. Please enable location access or enter your
        postcode to find nearby stores.
      </div>
    );
  }

  if (queryLoading) {
    return (
      <div className="flex items-center gap-2 text-sm text-muted-foreground py-4">
        <Loader2 className="h-4 w-4 animate-spin" /> Finding nearby stores…
      </div>
    );
  }

  if (!locations || locations.length === 0) {
    return (
      <p className="text-sm text-muted-foreground py-4">
        No pickup locations found near you.
      </p>
    );
  }

  return (
    <div className="space-y-3">
      {locations.map((loc: NearbyPickupLocation) => {
        const isSelected = loc.id === selectedId;
        return (
          <button
            key={loc.id}
            type="button"
            onClick={() => onSelect(loc.id)}
            className={`w-full text-left rounded-xl border p-4 transition ${
              isSelected
                ? "border-primary/40 bg-primary/10 shadow-md"
                : "border-border bg-surface hover:bg-muted"
            }`}
          >
            <div className="flex items-start justify-between gap-3">
              <div className="flex items-start gap-2">
                <MapPin className="h-4 w-4 text-link mt-0.5 shrink-0" />
                <div>
                  <p className="text-sm font-semibold text-foreground">
                    {loc.name}
                  </p>
                  {loc.address && (
                    <p className="text-xs text-muted-foreground">
                      {loc.address}, {loc.city}
                    </p>
                  )}
                </div>
              </div>
              <span className="text-xs font-medium text-muted-foreground shrink-0">
                {loc.distanceKm < 1
                  ? `${Math.round(loc.distanceKm * 1000)} m`
                  : `${loc.distanceKm.toFixed(1)} km`}
              </span>
            </div>
            {loc.pickupReadyHours != null && (
              <div className="flex items-center gap-1.5 mt-2 text-xs text-muted-foreground">
                <Clock className="h-3.5 w-3.5" />
                Ready in ~{loc.pickupReadyHours}h
              </div>
            )}
          </button>
        );
      })}
    </div>
  );
}
