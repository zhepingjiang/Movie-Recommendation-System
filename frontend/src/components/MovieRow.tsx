import { Link } from 'react-router-dom';
import type { Movie } from '../types/movie';
import MovieCard from './MovieCard';

interface MovieRowProps {
  title: string;
  movies: Movie[];
  viewAllTo?: string;
}

export default function MovieRow({ title, movies, viewAllTo = '/search' }: MovieRowProps) {
  return (
    <div className="mb-10">
      <div className="mb-3.5 flex items-center justify-between">
        <div className="text-[19px] font-semibold">{title}</div>
        <Link to={viewAllTo} className="text-[13px] text-[#999] no-underline hover:text-[#ccc]">
          View all ›
        </Link>
      </div>
      <div className="scroll-fade-hover flex gap-3.5 overflow-x-auto pb-3">
        {movies.map((movie) => (
          <MovieCard key={movie.id} movie={movie} />
        ))}
      </div>
    </div>
  );
}
