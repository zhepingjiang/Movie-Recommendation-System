import { useEffect, useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import OnboardingHeader from '../components/OnboardingHeader';
import { fetchGenres } from '../api/movies';
import { useAuth } from '../hooks/useAuth';
import type { RegisterAccountState } from './RegisterAccountPage';

// Static poster art per genre, purely decorative (the backend only returns genre names, not
// artwork). Any genre name not listed here falls back to a plain placeholder tile.
const GENRE_POSTERS: Record<string, string> = {
  Action: 'https://image.tmdb.org/t/p/w500/3sgnSfNT27Bx5O5ukr7B26mhEQq.jpg',
  Adventure: 'https://image.tmdb.org/t/p/w500/tHhxWxge06goXU6ZQH1hj7vK8Hd.jpg',
  Comedy: 'https://image.tmdb.org/t/p/w500/eJGWx219ZcEMVQJhAgMiqo8tYY.jpg',
  Drama: 'https://image.tmdb.org/t/p/w500/9cqNxx0GxF0bflZmeSMuL5tnGzr.jpg',
  'Science Fiction': 'https://image.tmdb.org/t/p/w500/yihdXomYb5kTeSivtFndMy5iDmf.jpg',
  'Sci-Fi': 'https://image.tmdb.org/t/p/w500/yihdXomYb5kTeSivtFndMy5iDmf.jpg',
  Horror: 'https://image.tmdb.org/t/p/w500/bRwnj8WEKBCvmfeUNOukJPwB43K.jpg',
  Romance: 'https://image.tmdb.org/t/p/w500/zqxIT48mWFsC4NSjGEHAcp1pjEo.jpg',
  Thriller: 'https://image.tmdb.org/t/p/w500/qJ2tW6WMUDux911r6m7haRef0WH.jpg',
  Animation: 'https://image.tmdb.org/t/p/w500/fWVSwgjpT2D78VUh6X8UBd2rorW.jpg',
  Fantasy: 'https://image.tmdb.org/t/p/w500/rCzpDGLbOoPwLjy3OAm5NUPOTrC.jpg',
  Crime: 'https://image.tmdb.org/t/p/w500/3bhkrj58Vtu7enYsRolD1fZdja1.jpg',
  Mystery: 'https://image.tmdb.org/t/p/w500/mGWOmj2jHFol3kOGNv1EhbSDDE1.jpg',
  Family: 'https://image.tmdb.org/t/p/w500/sKCr78MXSLixwmZ8DyJLrpMsd15.jpg',
  History: 'https://image.tmdb.org/t/p/w500/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg',
  Music: 'https://image.tmdb.org/t/p/w500/zm0KAbOjlt9eR5y7vDiL2dEOwMl.jpg',
  'TV Movie': 'https://image.tmdb.org/t/p/w500/xdhLAADGSse8KCrsDLBuM5b68Cg.jpg',
  War: 'https://image.tmdb.org/t/p/w500/kzRAd7mj39ZY3FGNrDdZjqx56tn.jpg',
  Western: 'https://image.tmdb.org/t/p/w500/gmmCh2BvTKp0YGT2FYG0eOQJELi.jpg',
};

const PLACEHOLDER_POSTER =
  'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="300" height="450"%3E%3Crect width="300" height="450" fill="%23141414"/%3E%3C/svg%3E';

/** Onboarding step 2: pick genres, then perform the actual account-creation call (full name +
 * email + password from step 1, plus the genres picked here) in one request. Requires the router
 * state left by RegisterAccountPage — landing here directly (e.g. a refresh) restarts step 1. */
export default function RegisterGenresPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const { register } = useAuth();

  const accountState = location.state as RegisterAccountState | null;

  const [genres, setGenres] = useState<string[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [loadingGenres, setLoadingGenres] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetchGenres()
      .then((names) => {
        if (!cancelled) setGenres(names);
      })
      .catch(() => {
        if (!cancelled) setError('Could not load genres. Is the backend running?');
      })
      .finally(() => {
        if (!cancelled) setLoadingGenres(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (!accountState) {
    return <Navigate to="/register" replace />;
  }

  function toggle(name: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(name)) next.delete(name);
      else next.add(name);
      return next;
    });
  }

  async function handleContinue() {
    if (selected.size === 0 || !accountState) return;
    setSubmitting(true);
    setError(null);
    try {
      await register({
        fullName: accountState.fullName,
        email: accountState.email,
        password: accountState.password,
        genres: Array.from(selected),
      });
      navigate('/register/explore', { state: { genres: Array.from(selected) } });
    } catch {
      setError('Could not create your account. The email may already be registered.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-screen flex-col bg-[#0a0a0a] text-[#f5f5f5]">
      <OnboardingHeader currentStep={2} />

      <main className="flex flex-1 flex-col items-center px-6 pt-6 pb-40">
        <div className="mb-10 max-w-[600px] text-center">
          <div className="mb-3.5 text-xs font-bold tracking-[2px] text-[#e50914] uppercase">Step 2 of 3</div>
          <h1 className="mb-3.5 text-[32px] font-extrabold tracking-tight sm:text-[38px]">
            What do you love to watch?
          </h1>
          <p className="text-base leading-relaxed text-[#a3a3a3]">
            Pick at least one genre to get started — the more you choose, the sharper your recommendations will be.
          </p>
        </div>

        {loadingGenres ? (
          <div className="text-sm text-[#a3a3a3]">Loading genres…</div>
        ) : (
          <div
            className="grid w-full max-w-[760px] grid-cols-2 gap-4 sm:grid-cols-3"
            role="group"
            aria-label="Genre selection"
          >
            {genres.map((name) => {
              const isSelected = selected.has(name);
              return (
                <button
                  type="button"
                  key={name}
                  onClick={() => toggle(name)}
                  aria-pressed={isSelected}
                  className={`group relative flex aspect-[2/3] cursor-pointer flex-col justify-end overflow-hidden rounded-[10px] border-2 bg-[#141414] text-left transition-transform hover:-translate-y-0.5 ${
                    isSelected ? 'border-[#e50914] shadow-[0_0_0_1px_#e50914,0_8px_24px_rgba(229,9,20,0.25)]' : 'border-transparent'
                  }`}
                >
                  <img
                    src={GENRE_POSTERS[name] ?? PLACEHOLDER_POSTER}
                    alt=""
                    loading="lazy"
                    className={`absolute inset-0 h-full w-full scale-105 object-cover object-top transition-[filter] ${
                      isSelected ? 'grayscale-0 brightness-100' : 'brightness-[0.8] grayscale-[15%] group-hover:grayscale-0 group-hover:brightness-100'
                    }`}
                  />
                  <div className="absolute inset-0 bg-gradient-to-b from-black/5 via-transparent to-black/90" />
                  <div
                    className={`absolute top-2.5 right-2.5 flex h-[26px] w-[26px] items-center justify-center rounded-full border-2 ${
                      isSelected ? 'border-[#e50914] bg-[#e50914]' : 'border-white/55 bg-black/35'
                    }`}
                  >
                    {isSelected && (
                      <svg viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth={3} strokeLinecap="round" strokeLinejoin="round" className="h-3.5 w-3.5">
                        <polyline points="20 6 9 17 4 12" />
                      </svg>
                    )}
                  </div>
                  <div className="relative z-[2] px-4 py-3.5 text-base font-bold">{name}</div>
                </button>
              );
            })}
          </div>
        )}
      </main>

      <div className="fixed inset-x-0 bottom-0 flex flex-col items-center gap-3.5 bg-gradient-to-t from-[#0a0a0a] from-60% to-transparent px-6 pt-9 pb-7">
        <div className="min-h-[18px] text-sm text-[#a3a3a3]">
          {error
            ? <span className="text-[#f87171]">{error}</span>
            : selected.size === 0
              ? 'Select at least 1 genre to continue'
              : selected.size < 3
                ? `${selected.size} selected — choosing a few more sharpens your recommendations`
                : <span className="text-[#4ade80]">{selected.size} selected — nice, that's a great start</span>}
        </div>
        <button
          type="button"
          onClick={handleContinue}
          disabled={selected.size === 0 || submitting}
          className={`rounded-md px-11 py-3.5 text-sm font-bold tracking-wide transition-colors ${
            selected.size > 0 && !submitting
              ? 'cursor-pointer bg-[#e50914] text-white hover:bg-[#ff0a16]'
              : 'cursor-not-allowed bg-[#7c0a0f] text-white/60'
          }`}
        >
          {submitting ? 'Creating your account…' : 'Continue'}
        </button>
      </div>
    </div>
  );
}
