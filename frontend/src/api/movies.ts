import type { Movie, PagedResponse, TrendingEntry } from '../types/movie';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

export interface MovieQueryParams {
  page?: number;
  size?: number;
  sort?: string;
  query?: string;
  genre?: string;
  minRating?: number;
  [key: string]: string | number | undefined;
}

async function getJson<T>(path: string, params: Record<string, string | number | undefined> = {}): Promise<T> {
  const searchParams = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') {
      searchParams.set(key, String(value));
    }
  }
  const query = searchParams.toString();
  const res = await fetch(`${API_BASE_URL}${path}${query ? `?${query}` : ''}`, { credentials: 'include' });
  if (!res.ok) {
    throw new Error(`Request to ${path} failed with status ${res.status}`);
  }
  return res.json();
}

export function fetchMovies(params: MovieQueryParams = {}): Promise<PagedResponse<Movie>> {
  return getJson<PagedResponse<Movie>>('/api/movies', params);
}

export async function fetchMovieById(id: number): Promise<Movie | null> {
  const res = await fetch(`${API_BASE_URL}/api/movies/${id}`, { credentials: 'include' });
  if (res.status === 404) {
    return null;
  }
  if (!res.ok) {
    throw new Error(`Request for movie ${id} failed with status ${res.status}`);
  }
  return res.json();
}

export function fetchGenres(): Promise<string[]> {
  return getJson<string[]>('/api/genres');
}

export function fetchTrending(limit: number): Promise<TrendingEntry[]> {
  return getJson<TrendingEntry[]>('/api/trending', { limit });
}

// Personalized recommendations for the current (authenticated) user, backfilled with trending
// movies server-side when there aren't enough. Field names already match Movie's camelCase shape
// (the backend returns MovieSummaryDto directly), unlike the cold-start endpoint below.
export function fetchRecommendations(limit: number): Promise<Movie[]> {
  return getJson<Movie[]>('/api/recommendations', { limit });
}

// Raw shape of a single entry from GET /api/recommendations/cold-start. Field names are
// snake_case because the backend DTO reuses its gRPC-facing @JsonProperty annotations for the
// HTTP response too (see MovieRecommendationDto on the backend).
interface ColdStartRecommendation {
  id: number;
  title: string;
  poster_url: string | null;
  average_rating: number;
  genres: string[];
  match_score: number;
}

export async function fetchColdStartRecommendations(limit: number): Promise<Movie[]> {
  const recommendations = await getJson<ColdStartRecommendation[]>('/api/recommendations/cold-start', { limit });
  return recommendations.map((rec) => ({
    id: rec.id,
    title: rec.title,
    posterUrl: rec.poster_url,
    description: null,
    releaseDate: null,
    durationMin: null,
    averageRating: rec.average_rating,
    genres: rec.genres,
  }));
}
