import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

const NAV_LINKS = [
  { label: 'Home', to: '/' },
  { label: 'Recommended', to: '/' },
  { label: 'Categories', to: '/search' },
  { label: 'My List', to: '/' },
];

export default function Navbar() {
  const [query, setQuery] = useState('');
  const navigate = useNavigate();

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
        <div className="flex h-8 w-8 items-center justify-center rounded-md bg-[#333] text-[13px] text-[#aaa]">
          Z
        </div>
      </div>
    </div>
  );
}
