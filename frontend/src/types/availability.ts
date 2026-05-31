export interface NearestSource {
  locationId: number;
  name: string;
  city: string | null;
  country: string | null;
  distanceKm: number | null;
}

export interface PickupOption {
  locationId: number;
  name: string;
  city: string | null;
  distanceKm: number | null;
  readyHours: number | null;
}

export interface AvailabilityEstimate {
  inStock: boolean;
  nearestSource: NearestSource | null;
  etaDaysMin: number | null;
  etaDaysMax: number | null;
  etaDateMin: string | null;
  etaDateMax: string | null;
  pickup: PickupOption | null;
}
