import type { PagedResponse } from '../types/movie';
import type { GeocodeResult, NearbyTheater, TheaterSort } from '../types/theater';
import { getJson } from './movies';

// The user's location ends up in request URLs, and so in access logs and traces. ~100 m is
// plenty for a theater search, so nothing more precise is ever sent.
function roundCoordinate(degrees: number): number {
  return Math.round(degrees * 1000) / 1000;
}

export interface NearbyTheaterQuery {
  lat: number;
  lng: number;
  radiusKm: number;
  movieId: number;
  sort: TheaterSort;
  page?: number;
  size?: number;
}

export function fetchNearbyTheaters(query: NearbyTheaterQuery): Promise<PagedResponse<NearbyTheater>> {
  return getJson<PagedResponse<NearbyTheater>>('/api/theatres/nearby', {
    ...query,
    lat: roundCoordinate(query.lat),
    lng: roundCoordinate(query.lng),
  });
}

// Backend proxies OpenStreetMap Nominatim (Ontario only). Call on submit, never per keystroke --
// Nominatim's usage policy forbids search-as-you-type.
export function geocodeAddress(address: string): Promise<GeocodeResult[]> {
  return getJson<GeocodeResult[]>('/api/geocode', { q: address });
}
