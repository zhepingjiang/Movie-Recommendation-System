import { useEffect, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import OnboardingHeader from '../components/OnboardingHeader';
import MovieRow from '../components/MovieRow';
import { fetchColdStartRecommendations } from '../api/movies';
import { useAuth } from '../hooks/useAuth';
import type { Movie } from '../types/movie';

const RECOMMENDATION_COUNT = 12;

/** Onboarding step 3: the account already exists (created at the end of step 2) and the user is
 * logged in, so this page just previews what their new account looks like before dropping them on
 * the real homepage. */
export default function RegisterExplorePage() {
  const { user, loading: authLoading } = useAuth();
  const navigate = useNavigate();

  const [recommended, setRecommended] = useState<Movie[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!user) return;
    let cancelled = false;
    fetchColdStartRecommendations(RECOMMENDATION_COUNT)
      .then((movies) => {
        if (!cancelled) setRecommended(movies);
      })
      .catch(() => {
        if (!cancelled) setRecommended([]);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [user]);

  if (!authLoading && !user) {
    return <Navigate to="/register" replace />;
  }

  const genreTags = user?.preferredGenres ?? [];

  return (
    <div className="flex min-h-screen flex-col bg-[#0a0a0a] text-[#f5f5f5]">
      <OnboardingHeader currentStep={3} />

      <main className="flex flex-1 flex-col items-center px-6 pt-12 pb-20">
        <div className="mb-6 flex h-16 w-16 items-center justify-center rounded-full bg-[#4ade80]/10">
          <svg viewBox="0 0 64 64" fill="none" stroke="#4ade80" strokeLinecap="round" strokeLinejoin="round" className="h-16 w-16">
            {/* Path (not <circle>) so the sweep flag can force counter-clockwise drawing */}
            <path
              className="checkmark-circle"
              d="M 63.25 32 A 31.25 31.25 0 1 0 0.75 32 A 31.25 31.25 0 1 0 63.25 32"
              strokeWidth={1.5}
              pathLength={1}
            />
            <polyline className="checkmark-tick" points="22.7 32 28.5 37.8 41.3 25" strokeWidth={3.5} pathLength={1} />
          </svg>
        </div>

        <div className="mb-11 max-w-[620px] text-center">
          <div className="mb-3.5 text-xs font-bold tracking-[2px] text-[#4ade80] uppercase">
            Step 3 of 3 — You're all set
          </div>
          <h1 className="mb-3.5 text-[32px] font-extrabold tracking-tight sm:text-[40px]">Start exploring!</h1>
          <p className="text-base leading-relaxed text-[#a3a3a3]">
            {genreTags.length > 0 ? (
              <>
                Your homepage is already taking shape around{' '}
                <span className="font-semibold text-[#f5f5f5]">{genreTags.join(', ')}</span>. Here's a first look:
              </>
            ) : (
              "Your homepage will get sharper the more you watch."
            )}
          </p>
        </div>

        <div className="w-full max-w-[1100px]">
          {loading ? (
            <div className="py-10 text-center text-sm text-[#a3a3a3]">Loading your recommendations…</div>
          ) : recommended.length > 0 ? (
            <MovieRow title="Recommended for you" movies={recommended} />
          ) : null}
        </div>

        <button
          type="button"
          onClick={() => navigate('/')}
          className="mt-4 cursor-pointer rounded-md bg-[#e50914] px-[52px] py-4 text-base font-bold tracking-wide text-white transition-colors hover:bg-[#ff0a16]"
        >
          Start exploring
        </button>
      </main>
    </div>
  );
}
