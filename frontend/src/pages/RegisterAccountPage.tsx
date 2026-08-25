import { useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import OnboardingHeader from '../components/OnboardingHeader';

export interface RegisterAccountState {
  fullName: string;
  email: string;
  password: string;
}

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Onboarding step 1: collects the new account's basic fields, then hands off to step 2 (genre
 * picking) via router state. No API call happens here — the backend only creates the account once
 * genres are picked too, see RegisterGenresPage. */
export default function RegisterAccountPage() {
  const navigate = useNavigate();

  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [touched, setTouched] = useState(false);

  const errors = useMemo(
    () => ({
      fullName: fullName.trim().length > 1 ? null : 'Enter your full name',
      email: EMAIL_RE.test(email.trim()) ? null : 'Enter a valid email address',
      password: password.length >= 8 ? null : 'Password must be at least 8 characters',
      confirmPassword:
        confirmPassword.length > 0 && confirmPassword === password ? null : "Passwords don't match",
    }),
    [fullName, email, password, confirmPassword],
  );
  const isValid = Object.values(errors).every((e) => e === null);

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setTouched(true);
    if (!isValid) return;
    const state: RegisterAccountState = { fullName: fullName.trim(), email: email.trim(), password };
    navigate('/register/genres', { state });
  }

  const inputClass =
    'w-full rounded-md border border-white/15 bg-white/[0.08] px-3.5 py-3 text-sm text-[#f5f5f5] placeholder:text-[#5c5c5c] focus:outline-none focus:border-[#e50914]';
  const labelClass = 'mb-1.5 block text-xs font-semibold tracking-wide text-[#a3a3a3]';
  const errorClass = 'mt-1.5 text-xs text-[#f87171]';

  return (
    <div className="flex min-h-screen flex-col bg-[#0a0a0a] text-[#f5f5f5]">
      <OnboardingHeader currentStep={1} />

      <main className="flex flex-1 items-center justify-center px-5 pb-16">
        <div className="w-full max-w-[420px] rounded-xl border border-[#262626] bg-[#0a0a0a] p-10 shadow-2xl">
          <h1 className="mb-2 text-[28px] font-extrabold tracking-tight">Create your account</h1>
          <p className="mb-7 text-sm leading-relaxed text-[#a3a3a3]">
            Join CINEMIND to get movie recommendations built around what you actually want to watch.
          </p>

          <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
            <div>
              <label className={labelClass} htmlFor="fullName">
                Full name
              </label>
              <input
                id="fullName"
                className={inputClass}
                placeholder="Jane Doe"
                autoComplete="name"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
              />
              {touched && errors.fullName && <div className={errorClass}>{errors.fullName}</div>}
            </div>

            <div>
              <label className={labelClass} htmlFor="email">
                Email
              </label>
              <input
                id="email"
                type="email"
                className={inputClass}
                placeholder="you@example.com"
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
              {touched && errors.email && <div className={errorClass}>{errors.email}</div>}
            </div>

            <div>
              <label className={labelClass} htmlFor="password">
                Password
              </label>
              <input
                id="password"
                type="password"
                className={inputClass}
                placeholder="At least 8 characters"
                autoComplete="new-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
              {touched && errors.password && <div className={errorClass}>{errors.password}</div>}
            </div>

            <div>
              <label className={labelClass} htmlFor="confirmPassword">
                Confirm password
              </label>
              <input
                id="confirmPassword"
                type="password"
                className={inputClass}
                placeholder="Re-enter your password"
                autoComplete="new-password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
              />
              {touched && errors.confirmPassword && <div className={errorClass}>{errors.confirmPassword}</div>}
            </div>

            <button
              type="submit"
              className={`mt-2 w-full cursor-pointer rounded-md px-4 py-3.5 text-sm font-bold tracking-wide transition-colors ${
                isValid || !touched
                  ? 'bg-[#e50914] text-white hover:bg-[#ff0a16]'
                  : 'cursor-not-allowed bg-[#7c0a0f] text-white/60'
              }`}
            >
              Continue
            </button>
          </form>

          <p className="mt-6 text-center text-sm text-[#a3a3a3]">
            Already have an account?{' '}
            <Link to="/login" className="text-[#f5f5f5] underline">
              Log in
            </Link>
          </p>
        </div>
      </main>
    </div>
  );
}
