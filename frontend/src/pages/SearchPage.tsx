import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { allTags, genres, mockMovies } from '../data/mockMovies';
import MovieCard from '../components/MovieCard';

const RATING_OPTIONS = [0, 6, 7, 8, 9];

export default function SearchPage() {
  const [searchParams] = useSearchParams();
  const [query, setQuery] = useState(searchParams.get('q') ?? '');
  const [genre, setGenre] = useState('All');
  const [activeTags, setActiveTags] = useState<string[]>([]);
  const [minRating, setMinRating] = useState(0);

  function toggleTag(tag: string) {
    setActiveTags((prev) =>
      prev.includes(tag) ? prev.filter((t) => t !== tag) : [...prev, tag],
    );
  }

  const results = useMemo(() => {
    const q = query.trim().toLowerCase();
    return mockMovies.filter((movie) => {
      const matchesQuery = q === '' || movie.title.toLowerCase().includes(q);
      const matchesGenre = genre === 'All' || movie.genre === genre;
      const matchesTags = activeTags.every((tag) => movie.tags.includes(tag));
      const matchesRating = movie.rating >= minRating;
      return matchesQuery && matchesGenre && matchesTags && matchesRating;
    });
  }, [query, genre, activeTags, minRating]);

  return (
    <div className="mx-auto w-full max-w-[1400px] px-[60px] py-10 pb-[60px]">
      <div className="mb-8 text-[28px] font-bold">Search</div>

      <div className="mb-8 flex flex-col gap-5">
        <input
          className="w-full max-w-[420px] rounded-md border border-white/15 bg-white/[0.08] px-3.5 py-2.5 text-sm text-[#f2f2f2] placeholder:text-[#888] focus:outline-none"
          placeholder="Search movies..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
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

        <div className="flex flex-wrap gap-2">
          {allTags.map((tag) => {
            const active = activeTags.includes(tag);
            return (
              <button
                key={tag}
                type="button"
                onClick={() => toggleTag(tag)}
                className={
                  active
                    ? 'cursor-pointer rounded-full border border-[#e50914] bg-[#e50914]/20 px-3 py-1 text-xs text-white'
                    : 'cursor-pointer rounded-full border border-white/15 bg-white/[0.06] px-3 py-1 text-xs text-[#d0d0d0] hover:text-white'
                }
              >
                {tag}
              </button>
            );
          })}
        </div>
      </div>

      <div className="mb-4 text-sm text-[#999]">{results.length} results</div>

      {results.length > 0 ? (
        <div className="grid grid-cols-[repeat(auto-fill,160px)] gap-x-3.5 gap-y-8">
          {results.map((movie) => (
            <MovieCard key={movie.id} movie={movie} />
          ))}
        </div>
      ) : (
        <div className="py-16 text-center text-sm text-[#999]">
          No movies match your filters.
        </div>
      )}
    </div>
  );
}
