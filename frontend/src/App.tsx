import { BrowserRouter, Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import RequireAuth from './components/RequireAuth';
import HomePage from './pages/HomePage';
import LoginPage from './pages/LoginPage';
import MovieDetailPage from './pages/MovieDetailPage';
import ProfilePage from './pages/ProfilePage';
import RegisterAccountPage from './pages/RegisterAccountPage';
import RegisterExplorePage from './pages/RegisterExplorePage';
import RegisterGenresPage from './pages/RegisterGenresPage';
import SearchPage from './pages/SearchPage';

/** Root component: sets up client-side routing for the app's pages under the shared Layout. The
 * /register onboarding wizard is deliberately outside Layout — it's a full-bleed experience with
 * its own header, not the normal Navbar/Footer chrome. */
function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<HomePage />} />
          <Route path="movie/:id" element={<MovieDetailPage />} />
          <Route path="search" element={<SearchPage />} />
          <Route path="login" element={<LoginPage />} />
          <Route
            path="profile"
            element={
              <RequireAuth>
                <ProfilePage />
              </RequireAuth>
            }
          />
        </Route>
        <Route path="register" element={<RegisterAccountPage />} />
        <Route path="register/genres" element={<RegisterGenresPage />} />
        <Route path="register/explore" element={<RegisterExplorePage />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
