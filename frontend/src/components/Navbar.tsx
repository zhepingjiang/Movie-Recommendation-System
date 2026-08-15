import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';

const NAV_LINKS = [
  { label: 'Home', to: '/' },
  { label: 'Recommended', to: '/' },
  { label: 'Categories', to: '/search' },
  { label: 'My List', to: '/' },
];

/** Sticky top navigation bar with site links and a search box that routes to /search. */
export default function Navbar() {
  const [query, setQuery] = useState('');
  const navigate = useNavigate();
  const { user, logout } = useAuth();

  async function handleLogout() {
    await logout();
    navigate('/');
  }

  // Navigate to the search page with the query as a URL param instead of managing search state here;
  // SearchPage reads it back out of the URL on mount.
  function handleSearchSubmit(e: React.FormEvent) {
    e.preventDefault();
    navigate(query.trim() ? `/search?q=${encodeURIComponent(query.trim())}` : '/search');
  }

  return (
    <div className="sticky top-0 z-10 flex items-center justify-between bg-gradient-to-b from-black/80 to-black/0 px-[60px] py-5">
      <Link to="/" className="text-[22px] font-bold tracking-wide text-[#e50914]">
        CINEMIND
      </Link>
      <div className="flex gap-7">
        {NAV_LINKS.map((link) => (
          <Link
            key={link.label}
            to={link.to}
            className="text-sm text-[#d0d0d0] no-underline hover:text-white"
          >
            {link.label}
          </Link>
        ))}
      </div>
      <div className="flex items-center gap-[18px]">
        <form onSubmit={handleSearchSubmit}>
          <input
            className="w-[180px] rounded-md border border-white/15 bg-white/[0.08] px-3 py-1.5 text-[13px] text-[#ccc] placeholder:text-[#ccc] focus:outline-none"
            placeholder="Search movies..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </form>
        {user ? (
          <div className="flex items-center gap-3">
            <Link to="/profile" title={user.displayName ?? user.username}>
              {user.avatarUrl ? (
                <img src={user.avatarUrl} alt="Avatar" className="h-8 w-8 rounded-md object-cover" />
              ) : (
                <div className="flex h-8 w-8 items-center justify-center rounded-md bg-[#333] text-[13px] text-[#aaa]">
                  {(user.displayName ?? user.username).charAt(0).toUpperCase()}
                </div>
              )}
            </Link>
            <button
              type="button"
              onClick={handleLogout}
              className="cursor-pointer text-sm text-[#d0d0d0] hover:text-white"
            >
              Log out
            </button>
          </div>
        ) : (
          <Link
            to="/login"
            className="cursor-pointer rounded-md border border-white/25 bg-white/10 px-4 py-1.5 text-sm text-[#f2f2f2] no-underline hover:bg-white/15"
          >
            Log in / Sign up
          </Link>
        )}
      </div>
    </div>
  );
}
