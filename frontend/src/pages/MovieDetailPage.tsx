import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { fetchMovieById, fetchMovies } from '../api/movies';
import type { Movie } from '../types/movie';
import { formatRuntime, releaseYear } from '../utils/format';
import MovieRow from '../components/MovieRow';

const PLACEHOLDER_POSTER =
  'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="300" height="430"%3E%3Crect width="300" height="430" fill="%231c1c22"/%3E%3C/svg%3E';

export default function MovieDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [movie, setMovie] = useState<Movie | null | undefined>(undefined);
  const [similarMovies, setSimilarMovies] = useState<Movie[]>([]);

  useEffect(() => {
    let cancelled = false;
    const movieId = Number(id);

    async function load() {
      setMovie(undefined);
      setSimilarMovies([]);
      const found = await fetchMovieById(movieId);
      if (cancelled) return;
      setMovie(found);

      if (found && found.genres[0]) {
        const similarRes = await fetchMovies({ genre: found.genres[0], size: 9 });
        if (cancelled) return;
        setSimilarMovies(similarRes.items.filter((m) => m.id !== found.id).slice(0, 8));
      }
    }

    load();
    return () => {
      cancelled = true;
    };
  }, [id]);

  if (movie === undefined) {
    return <div className="py-24 text-center text-sm text-[#999]">Loading…</div>;
  }

  if (movie === null) {
    return (
      <div className="mx-auto w-full max-w-[1400px] px-[60px] py-24 text-center">
        <div className="mb-4 text-xl font-semibold">Movie not found</div>
        <Link to="/" className="text-sm text-[#e50914] no-underline">
          ‹ Back to home
        </Link>
      </div>
    );
  }

  const runtime = formatRuntime(movie.durationMin);

  return (
    <div className="mx-auto w-full max-w-[1400px] px-[60px] py-10 pb-[60px]">
      <div className="flex flex-col gap-10 md:flex-row">
        <img
          src={movie.posterUrl ?? PLACEHOLDER_POSTER}
          alt={movie.title}
          className="h-[450px] w-[300px] flex-none rounded-lg bg-[#1c1c22] object-cover"
        />
        <div className="flex-1">
          <div className="mb-3 text-[40px] leading-[1.15] font-bold">{movie.title}</div>
          <div className="mb-4 flex flex-wrap items-center gap-2.5 text-[13px] text-[#ccc]">
            <span className="rounded bg-white/[0.12] px-2 py-0.5 font-semibold text-[#f5c518]">
              ★ {movie.averageRating.toFixed(1)}
            </span>
            <span>{releaseYear(movie.releaseDate)}</span>
            {runtime && <span>· {runtime}</span>}
          </div>
          <div className="mb-6 flex flex-wrap gap-2">
            {movie.genres.map((genre) => (
              <span
                key={genre}
                className="rounded-full border border-white/15 bg-white/[0.06] px-3 py-1 text-xs text-[#d0d0d0]"
              >
                {genre}
              </span>
            ))}
          </div>
          <div className="mb-6 max-w-[720px] text-sm leading-[1.7] text-[#cfcfcf]">
            {movie.description ?? 'No synopsis available.'}
          </div>
        </div>
      </div>

      {similarMovies.length > 0 && (
        <div className="mt-16">
          <MovieRow title="Similar movies" movies={similarMovies} />
        </div>
      )}
    </div>
  );
}
