export interface Movie {
  id: number;
  title: string;
  posterUrl: string;
  backdropUrl: string;
  rating: number;
  genre: string;
  tags: string[];
  synopsis: string;
  releaseYear: number;
  runtimeMinutes: number;
  cast: string[];
}
