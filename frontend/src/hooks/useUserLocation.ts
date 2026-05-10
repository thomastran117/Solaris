import { useCallback, useEffect, useRef, useState } from "react";

const STORAGE_KEY = "shopwave.userLocation";

export type GeoPermission = "unknown" | "prompt" | "granted" | "denied" | "unavailable";

export interface Coords {
  lat: number;
  lng: number;
}

interface StoredLocation {
  lat: number;
  lng: number;
  permission: GeoPermission;
  savedAt: number;
}

interface UseUserLocationResult {
  coords: Coords | null;
  permission: GeoPermission;
  isRequesting: boolean;
  error: string | null;
  request: () => void;
  clear: () => void;
}

/**
 * Round a coordinate to 2 decimals (~1.1 km at the equator). Coarser than the device
 * provides, but matches the backend's cache bucket and avoids storing precise location.
 */
function round2(value: number): number {
  return Math.round(value * 100) / 100;
}

function readStorage(): StoredLocation | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as StoredLocation;
    if (
      typeof parsed.lat !== "number" ||
      typeof parsed.lng !== "number" ||
      typeof parsed.permission !== "string"
    ) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

function writeStorage(value: StoredLocation): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(value));
  } catch {
    // localStorage may be disabled in private mode — degrade silently.
  }
}

function clearStorage(): void {
  try {
    window.localStorage.removeItem(STORAGE_KEY);
  } catch {
    // ignore
  }
}

/**
 * Buyer-location hook for delivery estimates.
 *
 * - Never auto-prompts. The user must call {@link request} (e.g. by clicking a CTA).
 * - Persists granted coords + permission state in localStorage so we don't reprompt.
 * - Coords are rounded to 2 decimals before storage (privacy).
 */
export function useUserLocation(): UseUserLocationResult {
  const [coords, setCoords] = useState<Coords | null>(null);
  const [permission, setPermission] = useState<GeoPermission>("unknown");
  const [isRequesting, setIsRequesting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const hasHydrated = useRef(false);

  useEffect(() => {
    if (hasHydrated.current) return;
    hasHydrated.current = true;

    if (typeof navigator === "undefined" || !navigator.geolocation) {
      setPermission("unavailable");
      return;
    }

    const stored = readStorage();
    if (stored) {
      setPermission(stored.permission);
      if (stored.permission === "granted") {
        setCoords({ lat: stored.lat, lng: stored.lng });
      }
    } else {
      setPermission("prompt");
    }
  }, []);

  const request = useCallback(() => {
    if (typeof navigator === "undefined" || !navigator.geolocation) {
      setPermission("unavailable");
      return;
    }
    setIsRequesting(true);
    setError(null);

    navigator.geolocation.getCurrentPosition(
      (position) => {
        const next: Coords = {
          lat: round2(position.coords.latitude),
          lng: round2(position.coords.longitude),
        };
        setCoords(next);
        setPermission("granted");
        setIsRequesting(false);
        writeStorage({ ...next, permission: "granted", savedAt: Date.now() });
      },
      (err) => {
        const denied = err.code === err.PERMISSION_DENIED;
        const nextPermission: GeoPermission = denied ? "denied" : "prompt";
        setPermission(nextPermission);
        setIsRequesting(false);
        setError(denied ? "Location permission denied" : "Could not read your location");
        // Persist denials so we don't reprompt; keep prompt state ephemeral.
        if (denied) {
          writeStorage({ lat: 0, lng: 0, permission: "denied", savedAt: Date.now() });
        }
      },
      { enableHighAccuracy: false, timeout: 10_000, maximumAge: 60 * 60 * 1000 }
    );
  }, []);

  const clear = useCallback(() => {
    setCoords(null);
    setPermission("prompt");
    setError(null);
    clearStorage();
  }, []);

  return { coords, permission, isRequesting, error, request, clear };
}
