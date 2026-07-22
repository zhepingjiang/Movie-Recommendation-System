import Hero from '../components/Hero';
import MovieRow from '../components/MovieRow';
import { mockMovies } from '../data/mockMovies';

const recommended = mockMovies.slice(0, 8);
const trending = mockMovies.slice(5, 13);
const newReleases = mockMovies.slice(10, 18);

export default function HomePage() {
  return (
    <>
      <Hero movie={mockMovies[0]} />
      <div className="py-5 pb-[60px]">
        <div className="mx-auto w-full max-w-[1400px] px-[60px]">
          <MovieRow title="Recommended for you" movies={recommended} />
          <MovieRow title="Trending now" movies={trending} />
          <MovieRow title="New releases" movies={newReleases} />
        </div>
      </div>
    </>
  );
}
