import { Link } from 'react-router-dom';
import type { Movie } from '../types/movie';
import { releaseYear } from '../utils/format';

interface MovieCardProps {
  movie: Movie;
}

const PLACEHOLDER_POSTER =
  'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="300" height="430"%3E%3Crect width="300" height="430" fill="%231c1c22"/%3E%3C/svg%3E';

export default function MovieCard({ movie }: MovieCardProps) {
  return (
    <Link to={`/movie/${movie.id}`} className="w-40 flex-none cursor-pointer no-underline">
      <div className="group relative h-[230px] w-40 overflow-hidden rounded-lg bg-[#1c1c22] transition-transform duration-150 ease-out hover:scale-[1.04]">
        <img
          src={movie.posterUrl ?? PLACEHOLDER_POSTER}
          alt={movie.title}
          className="block h-full w-full object-cover"
        />
        <div className="absolute top-2 right-2 rounded bg-black/70 px-1.5 py-0.5 text-[11px] font-semibold text-[#f5c518]">
          ★ {movie.averageRating.toFixed(1)}
        </div>
      </div>
      <div className="mt-2 overflow-hidden text-ellipsis whitespace-nowrap text-[13px] font-medium text-[#f2f2f2]">
        {movie.title}
      </div>
      <div className="mt-0.5 text-[11px] text-[#888]">
        {releaseYear(movie.releaseDate)} · {movie.genres[0] ?? 'Unknown'}
      </div>
    </Link>
  );
}
