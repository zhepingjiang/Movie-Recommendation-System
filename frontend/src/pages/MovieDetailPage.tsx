import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { fetchMovieById, fetchMovies } from '../api/movies';
import type { Movie } from '../types/movie';
import { formatRuntime, releaseYear } from '../utils/format';
import MovieRow from '../components/MovieRow';
import RatingModal from '../components/RatingModal';
import { useAuth } from '../hooks/useAuth';

const PLACEHOLDER_POSTER =
  'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="300" height="430"%3E%3Crect width="300" height="430" fill="%231c1c22"/%3E%3C/svg%3E';

/**
 * Movie detail page for route `/movie/:id`: shows full movie info plus a
 * "similar movies" row sourced from the movie's primary genre.
 */
export default function MovieDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  // `undefined` = loading, `null` = fetched but not found, otherwise the loaded movie.
  const [movie, setMovie] = useState<Movie | null | undefined>(undefined);
  const [similarMovies, setSimilarMovies] = useState<Movie[]>([]);
  const [ratingModalOpen, setRatingModalOpen] = useState(false);
  // Only reflects a rating submitted this session -- there's no endpoint yet to fetch a user's
  // existing rating, so a returning visitor sees "Rate this movie" even if they already rated it.
  const [myRating, setMyRating] = useState<number | null>(null);

  useEffect(() => {
    let cancelled = false;
    const movieId = Number(id);

    async function load() {
      setMovie(undefined);
      setSimilarMovies([]);
      setMyRating(null);
      const found = await fetchMovieById(movieId);
      if (cancelled) return;
      setMovie(found);

      // Only fetch similar movies once the primary movie resolves, using its first genre
      // as a simple similarity signal, then exclude itself and cap the row at 8 results.
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

  function handleRateClick() {
    if (!user) {
      navigate('/login', { state: { from: location } });
      return;
    }
    setRatingModalOpen(true);
  }

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
          <div className="flex flex-wrap gap-3">
            <button className="inline-flex cursor-pointer items-center gap-2 rounded-md border-none bg-[#e50914] px-[26px] py-[11px] text-sm font-semibold text-white">
              <svg viewBox="0 0 24 24" fill="currentColor" className="h-4 w-4 flex-none">
                <path d="M8 5v14l11-7z" />
              </svg>
              Play now
            </button>
            <button className="inline-flex cursor-pointer items-center gap-2 rounded-md border border-white/25 bg-white/10 px-[26px] py-[11px] text-sm font-semibold text-white">
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
                className="h-4 w-4 flex-none"
              >
                <path d="M3 9a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v1a2 2 0 0 0 0 4v1a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-1a2 2 0 0 0 0-4z" />
                <path d="M13 5v2" />
                <path d="M13 17v2" />
                <path d="M13 11v2" />
              </svg>
              Get tickets
            </button>
            <button className="cursor-pointer rounded-md border border-white/25 bg-white/10 px-[26px] py-[11px] text-sm font-semibold text-white">
              + Add to watchlist
            </button>
            <button
              type="button"
              onClick={handleRateClick}
              className="cursor-pointer rounded-md border border-white/25 bg-white/10 px-[26px] py-[11px] text-sm font-semibold text-white"
            >
              {myRating !== null ? `★ Rated ${myRating}/5` : '☆ Rate this movie'}
            </button>
          </div>
        </div>
      </div>

      {ratingModalOpen && (
        <RatingModal
          movieId={movie.id}
          movieTitle={movie.title}
          onClose={() => setRatingModalOpen(false)}
          onRated={(score) => {
            setMyRating(score);
            setRatingModalOpen(false);
          }}
        />
      )}

      {similarMovies.length > 0 && (
        <div className="mt-16">
          <MovieRow title="Similar movies" movies={similarMovies} />
        </div>
      )}
    </div>
  );
}
