import { Link, useParams } from 'react-router-dom';
import { mockMovies } from '../data/mockMovies';
import MovieRow from '../components/MovieRow';

function formatRuntime(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  const mins = minutes % 60;
  return `${hours}h ${mins}m`;
}

export default function MovieDetailPage() {
  const { id } = useParams<{ id: string }>();
  const movie = mockMovies.find((m) => m.id === Number(id));

  if (!movie) {
    return (
      <div className="mx-auto w-full max-w-[1400px] px-[60px] py-24 text-center">
        <div className="mb-4 text-xl font-semibold">Movie not found</div>
        <Link to="/" className="text-sm text-[#e50914] no-underline">
          ‹ Back to home
        </Link>
      </div>
    );
  }

  const similarMovies = mockMovies
    .filter((m) => m.id !== movie.id && m.genre === movie.genre)
    .slice(0, 8);

  return (
    <div className="mx-auto w-full max-w-[1400px] px-[60px] py-10 pb-[60px]">
      <div className="flex flex-col gap-10 md:flex-row">
        <img
          src={movie.posterUrl}
          alt={movie.title}
          className="h-[450px] w-[300px] flex-none rounded-lg bg-[#1c1c22] object-cover"
        />
        <div className="flex-1">
          <div className="mb-3 text-[40px] leading-[1.15] font-bold">{movie.title}</div>
          <div className="mb-4 flex flex-wrap items-center gap-2.5 text-[13px] text-[#ccc]">
            <span className="rounded bg-white/[0.12] px-2 py-0.5 font-semibold text-[#f5c518]">
              ★ {movie.rating.toFixed(1)}
            </span>
            <span>{movie.releaseYear}</span>
            <span>· {movie.genre}</span>
            <span>· {formatRuntime(movie.runtimeMinutes)}</span>
          </div>
          <div className="mb-6 flex flex-wrap gap-2">
            {movie.tags.map((tag) => (
              <span
                key={tag}
                className="rounded-full border border-white/15 bg-white/[0.06] px-3 py-1 text-xs text-[#d0d0d0]"
              >
                {tag}
              </span>
            ))}
          </div>
          <div className="mb-6 max-w-[720px] text-sm leading-[1.7] text-[#cfcfcf]">
            {movie.synopsis}
          </div>
          <div>
            <div className="mb-2 text-sm font-semibold text-[#f2f2f2]">Cast</div>
            <div className="text-sm text-[#999]">{movie.cast.join(', ')}</div>
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
