import { Outlet } from 'react-router-dom';
import Navbar from './Navbar';
import Footer from './Footer';

/**
 * Shared page shell (Navbar + Footer) applied to every route via `<Outlet />`,
 * see the route tree in App.tsx.
 */
export default function Layout() {
  return (
    <div className="min-h-screen bg-[#0b0b0f] font-sans text-[#f2f2f2]">
      <Navbar />
      <Outlet />
      <Footer />
    </div>
  );
}
