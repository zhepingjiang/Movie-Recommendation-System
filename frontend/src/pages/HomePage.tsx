import { useEffect, useState } from 'react';
import Hero from '../components/Hero';
import MovieRow from '../components/MovieRow';
import { fetchMovies } from '../api/movies';
import { useAuth } from '../hooks/useAuth';
import type { Movie } from '../types/movie';

const ROW_SIZE = 8;

/**
 * Landing page: a hero banner followed by horizontally-scrolling movie rows. Logged-in users see
 * a personalized "Recommended for you" row plus a hero built from it; logged-out users skip that
 * fetch entirely and get a hero built from the top-rated movie instead.
 */
export default function HomePage() {
  const { user } = useAuth();
  const [recommended, setRecommended] = useState<Movie[]>([]);
  const [trending, setTrending] = useState<Movie[]>([]);
  const [newReleases, setNewReleases] = useState<Movie[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // Guards state updates after unmount (e.g. fast navigation away) since the fetches
    // below aren't otherwise cancellable.
    let cancelled = false;

    async function load() {
      setLoading(true);
      setError(null);
      try {
        // Fetch rows concurrently rather than sequentially to minimize load time. The
        // "recommended" row is only meaningful (and only fetched) for a logged-in user.
        const [recommendedRes, trendingRes, newReleasesRes] = await Promise.all([
          user ? fetchMovies({ size: ROW_SIZE, sort: 'id,asc' }) : Promise.resolve(null),
          fetchMovies({ size: ROW_SIZE, sort: 'averageRating,desc' }),
          fetchMovies({ size: ROW_SIZE, sort: 'releaseDate,desc' }),
        ]);
        if (cancelled) return;
        setRecommended(recommendedRes?.items ?? []);
        setTrending(trendingRes.items);
        setNewReleases(newReleasesRes.items);
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
  }, [user]);

  if (loading) {
    return <div className="py-24 text-center text-sm text-[#999]">Loading movies…</div>;
  }

  if (error) {
    return <div className="py-24 text-center text-sm text-[#e50914]">{error}</div>;
  }

  const heroMovie = user ? recommended[0] : trending[0];

  return (
    <>
      {heroMovie && <Hero movie={heroMovie} />}
      <div className="py-5 pb-[60px]">
        <div className="mx-auto w-full max-w-[1400px] px-[60px]">
          {user && <MovieRow title="Recommended for you" movies={recommended} />}
          <MovieRow title="Top rated" movies={trending} />
          <MovieRow title="New releases" movies={newReleases} />
        </div>
      </div>
    </>
  );
}
