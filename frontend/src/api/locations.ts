import api from "../api";
import type { NearbyPickupLocation } from "../types/order";

export const locationsApi = {
  nearbyPickup: (companyId: string, lat: number, lng: number, limit = 5) =>
    api.get<NearbyPickupLocation[]>(
      `/companies/${companyId}/inventory/locations/pickup/nearby`,
      { params: { lat, lng, limit } }
    ),
};
