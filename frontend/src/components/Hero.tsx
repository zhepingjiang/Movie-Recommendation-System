import type { Movie } from "../types/movie";
import { formatRuntime, releaseYear } from "../utils/format";

interface HeroProps {
  movie: Movie;
}

export default function Hero({ movie }: HeroProps) {
  const runtime = formatRuntime(movie.durationMin);

  return (
    <div className="relative -mt-[76px] flex h-[560px] items-end overflow-hidden pb-[60px]">
      <div
        className="absolute inset-0 z-0 bg-cover bg-[center_30%]"
        style={{ backgroundImage: movie.posterUrl ? `url('${movie.posterUrl}')` : undefined }}
      />
      <div className="absolute inset-0 z-[1] bg-[linear-gradient(90deg,rgba(11,11,15,0.97)_15%,rgba(11,11,15,0.55)_50%,rgba(11,11,15,0.1)_85%)]" />
      <div className="absolute inset-x-0 bottom-0 z-[1] h-[220px] bg-[linear-gradient(180deg,rgba(11,11,15,0)_0%,#0b0b0f_100%)]" />
      <div className="relative z-[2] mx-auto w-full max-w-[1400px] px-[60px]">
        <div className="max-w-[480px]">
          <div className="mb-2.5 text-[13px] font-semibold tracking-wide text-[#e50914]">
            Featured for you
          </div>
          <div className="mb-3.5 text-[44px] leading-[1.15] font-bold">
            {movie.title}
          </div>
          <div className="mb-4 flex items-center gap-2.5 text-[13px] text-[#ccc]">
            <span className="rounded bg-white/[0.12] px-2 py-0.5 font-semibold text-[#f5c518]">
              ★ {movie.averageRating.toFixed(1)}
            </span>
            <span>{releaseYear(movie.releaseDate)}</span>
            <span>· {movie.genres.join(', ') || 'Unknown'}</span>
            {runtime && <span>· {runtime}</span>}
          </div>
          <div className="mb-[22px] text-sm leading-[1.6] text-[#cfcfcf]">
            {movie.description}
          </div>
          <div className="flex gap-3">
            <button className="cursor-pointer rounded-md border-none bg-[#e50914] px-[26px] py-[11px] text-sm font-semibold text-white">
              Play now
            </button>
            <button className="cursor-pointer rounded-md border border-white/25 bg-white/10 px-[26px] py-[11px] text-sm font-semibold text-white">
              + Add to watchlist
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
