import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';

/**
 * Sign-in page at `/login`. On success, redirects back to wherever the user was headed before
 * being sent here (see RequireAuth), or to the home page. New accounts are created via the
 * `/register` onboarding wizard, not here — registration requires an initial genre pick that this
 * single-form page has no room for.
 */
export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [identifier, setIdentifier] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const from = (location.state as { from?: Location })?.from?.pathname ?? '/';

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      await login({ identifier, password });
      navigate(from, { replace: true });
    } catch {
      setError('Invalid username/email or password.');
    } finally {
      setLoading(false);
    }
  }

  const inputClass =
    'w-full rounded-md border border-white/15 bg-white/[0.08] px-3.5 py-2.5 text-sm text-[#f2f2f2] placeholder:text-[#888] focus:outline-none';

  return (
    <div className="mx-auto flex w-full max-w-[420px] flex-col px-6 py-20">
      <h1 className="mb-8 border-b border-white/10 pb-3 text-sm font-semibold text-[#f2f2f2]">Sign in</h1>

      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <input
          className={inputClass}
          placeholder="Username or email"
          value={identifier}
          onChange={(e) => setIdentifier(e.target.value)}
          required
        />
        <input
          className={inputClass}
          type="password"
          placeholder="Password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
          minLength={8}
        />

        {error && <div className="text-sm text-[#e50914]">{error}</div>}

        <button
          type="submit"
          disabled={loading}
          className="mt-2 cursor-pointer rounded-md bg-[#e50914] px-[26px] py-[11px] text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60"
        >
          {loading ? 'Please wait…' : 'Sign in'}
        </button>
      </form>

      <p className="mt-6 text-center text-sm text-[#888]">
        Don't have an account?{' '}
        <Link to="/register" className="text-[#f2f2f2] underline">
          Create one
        </Link>
      </p>
    </div>
  );
}
