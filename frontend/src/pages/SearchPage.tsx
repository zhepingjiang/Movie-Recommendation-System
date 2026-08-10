import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { fetchGenres, fetchMovies } from '../api/movies';
import type { Movie } from '../types/movie';
import MovieCard from '../components/MovieCard';

const RATING_OPTIONS = [0, 6, 7, 8, 9];
const PAGE_SIZE = 24;
const DEBOUNCE_MS = 300;

export default function SearchPage() {
  const [searchParams] = useSearchParams();
  const [queryInput, setQueryInput] = useState(searchParams.get('q') ?? '');
  const [debouncedQuery, setDebouncedQuery] = useState(queryInput);
  const [genre, setGenre] = useState('All');
  const [minRating, setMinRating] = useState(0);
  const [page, setPage] = useState(0);

  const [genres, setGenres] = useState<string[]>([]);
  const [results, setResults] = useState<Movie[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchGenres()
      .then(setGenres)
      .catch(() => setGenres([]));
  }, []);

  useEffect(() => {
    const timeout = setTimeout(() => setDebouncedQuery(queryInput), DEBOUNCE_MS);
    return () => clearTimeout(timeout);
  }, [queryInput]);

  useEffect(() => {
    setPage(0);
  }, [debouncedQuery, genre, minRating]);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setLoading(true);
      setError(null);
      try {
        const res = await fetchMovies({
          page,
          size: PAGE_SIZE,
          query: debouncedQuery || undefined,
          genre: genre === 'All' ? undefined : genre,
          minRating: minRating || undefined,
        });
        if (cancelled) return;
        setResults(res.items);
        setTotalPages(res.totalPages);
        setTotalElements(res.totalElements);
      } catch {
        if (!cancelled) setError('Could not load movies. Is the backend running?');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    load();
    return () => {
      cancelled = true;
    };
  }, [debouncedQuery, genre, minRating, page]);

  return (
    <div className="mx-auto w-full max-w-[1400px] px-[60px] py-10 pb-[60px]">
      <div className="mb-8 text-[28px] font-bold">Search</div>

      <div className="mb-8 flex flex-col gap-5">
        <input
          className="w-full max-w-[420px] rounded-md border border-white/15 bg-white/[0.08] px-3.5 py-2.5 text-sm text-[#f2f2f2] placeholder:text-[#888] focus:outline-none"
          placeholder="Search movies..."
          value={queryInput}
          onChange={(e) => setQueryInput(e.target.value)}
        />

        <div className="flex flex-wrap items-center gap-6">
          <div className="flex items-center gap-2">
            <span className="text-xs text-[#999]">Genre</span>
            <select
              className="rounded-md border border-white/15 bg-[#16161b] px-2.5 py-1.5 text-sm text-[#f2f2f2] focus:outline-none"
              value={genre}
              onChange={(e) => setGenre(e.target.value)}
            >
              <option value="All">All</option>
              {genres.map((g) => (
                <option key={g} value={g}>
                  {g}
                </option>
              ))}
            </select>
          </div>

          <div className="flex items-center gap-2">
            <span className="text-xs text-[#999]">Min rating</span>
            <select
              className="rounded-md border border-white/15 bg-[#16161b] px-2.5 py-1.5 text-sm text-[#f2f2f2] focus:outline-none"
              value={minRating}
              onChange={(e) => setMinRating(Number(e.target.value))}
            >
              {RATING_OPTIONS.map((r) => (
                <option key={r} value={r}>
                  {r === 0 ? 'Any' : `${r}+`}
                </option>
              ))}
            </select>
          </div>
        </div>
      </div>

      {error && <div className="mb-4 text-sm text-[#e50914]">{error}</div>}
      {!error && <div className="mb-4 text-sm text-[#999]">{totalElements} results</div>}

      {loading ? (
        <div className="py-16 text-center text-sm text-[#999]">Loading…</div>
      ) : results.length > 0 ? (
        <>
          <div className="grid grid-cols-[repeat(auto-fill,160px)] gap-x-3.5 gap-y-8">
            {results.map((movie) => (
              <MovieCard key={movie.id} movie={movie} />
            ))}
          </div>

          {totalPages > 1 && (
            <div className="mt-10 flex items-center justify-center gap-4">
              <button
                type="button"
                disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}
                className="cursor-pointer rounded-md border border-white/15 bg-white/[0.06] px-4 py-2 text-sm text-[#f2f2f2] disabled:cursor-not-allowed disabled:opacity-40"
              >
                ‹ Prev
              </button>
              <span className="text-sm text-[#999]">
                Page {page + 1} of {totalPages}
              </span>
              <button
                type="button"
                disabled={page + 1 >= totalPages}
                onClick={() => setPage((p) => p + 1)}
                className="cursor-pointer rounded-md border border-white/15 bg-white/[0.06] px-4 py-2 text-sm text-[#f2f2f2] disabled:cursor-not-allowed disabled:opacity-40"
              >
                Next ›
              </button>
            </div>
          )}
        </>
      ) : (
        !error && (
          <div className="py-16 text-center text-sm text-[#999]">No movies match your filters.</div>
        )
      )}
    </div>
  );
}
