export interface Movie {
  id: number;
  title: string;
  posterUrl: string | null;
  description: string | null;
  releaseDate: string | null;
  durationMin: number | null;
  averageRating: number;
  genres: string[];
}

export interface PagedResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
