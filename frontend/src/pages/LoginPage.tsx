import { useState } from 'react';
import type { FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';

type Mode = 'signin' | 'register';

/**
 * Combined sign-in / registration page at `/login`. On success, redirects back to wherever the
 * user was headed before being sent here (see RequireAuth), or to the home page.
 */
export default function LoginPage() {
  const { login, register } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [mode, setMode] = useState<Mode>('signin');
  const [identifier, setIdentifier] = useState('');
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const from = (location.state as { from?: Location })?.from?.pathname ?? '/';

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      if (mode === 'signin') {
        await login({ identifier, password });
      } else {
        await register({ username, email, password });
      }
      navigate(from, { replace: true });
    } catch {
      setError(
        mode === 'signin'
          ? 'Invalid username/email or password.'
          : 'Could not create an account. The username or email may already be taken.',
      );
    } finally {
      setLoading(false);
    }
  }

  const inputClass =
    'w-full rounded-md border border-white/15 bg-white/[0.08] px-3.5 py-2.5 text-sm text-[#f2f2f2] placeholder:text-[#888] focus:outline-none';

  return (
    <div className="mx-auto flex w-full max-w-[420px] flex-col px-6 py-20">
      <div className="mb-8 flex gap-6 border-b border-white/10">
        <button
          type="button"
          onClick={() => setMode('signin')}
          className={`cursor-pointer pb-3 text-sm font-semibold ${
            mode === 'signin' ? 'border-b-2 border-[#e50914] text-[#f2f2f2]' : 'text-[#888]'
          }`}
        >
          Sign in
        </button>
        <button
          type="button"
          onClick={() => setMode('register')}
          className={`cursor-pointer pb-3 text-sm font-semibold ${
            mode === 'register' ? 'border-b-2 border-[#e50914] text-[#f2f2f2]' : 'text-[#888]'
          }`}
        >
          Create account
        </button>
      </div>

      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        {mode === 'signin' ? (
          <input
            className={inputClass}
            placeholder="Username or email"
            value={identifier}
            onChange={(e) => setIdentifier(e.target.value)}
            required
          />
        ) : (
          <>
            <input
              className={inputClass}
              placeholder="Username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
            />
            <input
              className={inputClass}
              type="email"
              placeholder="Email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </>
        )}
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
          {loading ? 'Please wait…' : mode === 'signin' ? 'Sign in' : 'Create account'}
        </button>
      </form>
    </div>
  );
}
