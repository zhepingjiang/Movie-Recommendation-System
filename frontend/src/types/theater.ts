export interface Showtime {
  /** ISO-8601 instant. */
  startTime: string;
  /** e.g. "STANDARD", "IMAX", "3D", "ULTRAAVX". */
  format: string;
}

export interface NearbyTheater {
  id: number;
  name: string;
  brand: string | null;
  address: string | null;
  city: string | null;
  lat: number;
  lng: number;
  /** Straight-line distance from the search point, 1 decimal. */
  distanceKm: number;
  /** Upcoming showtimes, earliest first; never empty. */
  showtimes: Showtime[];
}

export interface GeocodeResult {
  displayName: string;
  lat: number;
  lng: number;
}

export type TheaterSort = 'distance' | 'nextShowtime';

/** The point theaters are searched around, plus how to describe it to the user. */
export interface SearchLocation {
  lat: number;
  lng: number;
  label: string;
}
