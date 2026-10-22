import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { fetchMovieById } from '../api/movies';
import { fetchNearbyTheaters, geocodeAddress } from '../api/theaters';
import TheaterCard from '../components/TheaterCard';
import TheaterMap from '../components/TheaterMap';
import type { Movie } from '../types/movie';
import type { NearbyTheater, SearchLocation, TheaterSort } from '../types/theater';
import { formatRuntime } from '../utils/format';

const RADIUS_OPTIONS_KM = [2, 5, 10, 25];
const DEFAULT_RADIUS_KM = 5;
const PAGE_SIZE = 20;

const PLACEHOLDER_POSTER =
  'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="46" height="68"%3E%3Crect width="46" height="68" fill="%231c1c22"/%3E%3C/svg%3E';

type SearchStatus = 'idle' | 'loading' | 'ready' | 'error';

/**
 * "Find theaters" page for route `/movie/:id/theaters`: theaters near an address (geocoded by the
 * backend) or the browser's location that have upcoming showtimes for the movie, nearest first.
 * Showtimes are mock data for now (see MockShowtimeProvider on the backend).
 */
export default function NearbyTheatersPage() {
  const { id } = useParams<{ id: string }>();
  const movieId = Number(id);

  // `undefined` = loading, `null` = fetched but not found, otherwise the loaded movie.
  const [movie, setMovie] = useState<Movie | null | undefined>(undefined);
  const [addressInput, setAddressInput] = useState('');
  const [searchLocation, setSearchLocation] = useState<SearchLocation | null>(null);
  const [locationMessage, setLocationMessage] = useState<string | null>(null);
  const [isLocating, setIsLocating] = useState(false);
  const [radiusKm, setRadiusKm] = useState(DEFAULT_RADIUS_KM);
  const [sort, setSort] = useState<TheaterSort>('distance');

  const [searchStatus, setSearchStatus] = useState<SearchStatus>('idle');
  const [theaters, setTheaters] = useState<NearbyTheater[]>([]);
  const [totalTheaters, setTotalTheaters] = useState(0);
  const [loadedPage, setLoadedPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [loadMoreFailed, setLoadMoreFailed] = useState(false);
  // Bumped by every new search, so a "Load more" response from an earlier search can be ignored.
  const searchGenerationRef = useRef(0);
  const [selectedTheaterId, setSelectedTheaterId] = useState<number | null>(null);

  useEffect(() => {
    let cancelled = false;
    setMovie(undefined);
    fetchMovieById(movieId)
      .then((found) => {
        if (!cancelled) setMovie(found);
      })
      .catch(() => {
        if (!cancelled) setMovie(null);
      });
    return () => {
      cancelled = true;
    };
  }, [movieId]);

  // Re-run the search from page 0 whenever where/how far/how sorted changes.
  useEffect(() => {
    if (!searchLocation) return;
    let cancelled = false;
    searchGenerationRef.current += 1;
    setIsLoadingMore(false);
    setLoadMoreFailed(false);
    setSearchStatus('loading');
    setSelectedTheaterId(null);
    fetchNearbyTheaters({
      lat: searchLocation.lat,
      lng: searchLocation.lng,
      radiusKm,
      movieId,
      sort,
      page: 0,
      size: PAGE_SIZE,
    })
      .then((response) => {
        if (cancelled) return;
        setTheaters(response.items);
        setTotalTheaters(response.totalElements);
        setLoadedPage(0);
        setTotalPages(response.totalPages);
        setSearchStatus('ready');
      })
      .catch(() => {
        if (!cancelled) setSearchStatus('error');
      });
    return () => {
      cancelled = true;
    };
  }, [searchLocation, radiusKm, sort, movieId]);

  async function handleAddressSubmit(event: React.FormEvent) {
    event.preventDefault();
    const address = addressInput.trim();
    if (!address) return;
    setIsLocating(true);
    setLocationMessage(null);
    try {
      const matches = await geocodeAddress(address);
      if (matches.length === 0) {
        setLocationMessage("We couldn't find that address in Ontario. Try adding the city, e.g. \"King St W, Toronto\".");
        return;
      }
      setSearchLocation({ lat: matches[0].lat, lng: matches[0].lng, label: matches[0].displayName });
    } catch {
      setLocationMessage("Address lookup isn't available right now. Try again in a moment, or use your location.");
    } finally {
      setIsLocating(false);
    }
  }

  function handleUseMyLocation() {
    if (!navigator.geolocation) {
      setLocationMessage("Your browser can't share its location. Enter an address instead.");
      return;
    }
    setIsLocating(true);
    setLocationMessage(null);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setIsLocating(false);
        setAddressInput('');
        setSearchLocation({
          lat: position.coords.latitude,
          lng: position.coords.longitude,
          label: 'your current location',
        });
      },
      (positionError) => {
        setIsLocating(false);
        setLocationMessage(
          positionError.code === positionError.PERMISSION_DENIED
            ? 'Location access was denied. Enter an address instead.'
            : "Couldn't get your location. Enter an address instead.",
        );
      },
      { timeout: 10000, maximumAge: 5 * 60 * 1000 },
    );
  }

  async function handleLoadMore() {
    if (!searchLocation) return;
    const searchGeneration = searchGenerationRef.current;
    setIsLoadingMore(true);
    setLoadMoreFailed(false);
    try {
      const response = await fetchNearbyTheaters({
        lat: searchLocation.lat,
        lng: searchLocation.lng,
        radiusKm,
        movieId,
        sort,
        page: loadedPage + 1,
        size: PAGE_SIZE,
      });
      // FIX: the location, radius or sort changed while this page was loading; appending it would
      // mix results from two different searches.
      if (searchGeneration !== searchGenerationRef.current) return;
      setTheaters((previousTheaters) => [...previousTheaters, ...response.items]);
      setLoadedPage(response.page);
      setTotalPages(response.totalPages);
    } catch {
      // FIX: was setSearchStatus('error'), which hid the theaters already shown; now only the
      // "Show more" button reports the failure and offers a retry.
      if (searchGeneration === searchGenerationRef.current) setLoadMoreFailed(true);
    } finally {
      if (searchGeneration === searchGenerationRef.current) setIsLoadingMore(false);
    }
  }

  function handleSelectFromMap(theaterId: number) {
    setSelectedTheaterId(theaterId);
    document.getElementById(`theater-${theaterId}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  if (movie === null) {
    return (
      <div className="mx-auto w-full max-w-[1400px] px-[60px] py-24 text-center">
        <div className="mb-4 text-xl font-semibold">Movie not found</div>
        <Link to="/" className="text-sm text-[#e50914] no-underline">
          ‹ Back to home
        </Link>
      </div>
    );
  }

  const runtime = movie ? formatRuntime(movie.durationMin) : null;

  return (
    <div className="mx-auto w-full max-w-[1400px] px-[60px] py-10 pb-[60px]">
      <Link to={`/movie/${movieId}`} className="text-sm text-[#999] no-underline hover:text-white">
        ‹ Back to movie
      </Link>

      <header className="mt-4">
        <div className="text-xs font-bold tracking-[2px] text-[#e50914] uppercase">Now showing near you</div>
        <h1 className="mt-2 mb-1.5 text-[40px] leading-[1.15] font-bold">Find a theater nearby</h1>
        <p className="max-w-[620px] text-sm text-[#999]">
          Showtimes for the movie you're viewing, sorted by distance from your location.
        </p>
        {movie && (
          <div className="mt-5 flex max-w-[460px] items-center gap-3.5 rounded-lg border border-white/15 bg-white/[0.06] p-2.5">
            <img
              src={movie.posterUrl ?? PLACEHOLDER_POSTER}
              alt=""
              className="h-[68px] w-[46px] flex-none rounded bg-[#1c1c22] object-cover"
            />
            <div className="min-w-0">
              <div className="truncate font-semibold">{movie.title}</div>
              <div className="text-[13px] text-[#999]">
                <span className="font-semibold text-[#f5c518]">★ {movie.averageRating.toFixed(1)}</span>
                {runtime && <span> · {runtime}</span>}
              </div>
            </div>
          </div>
        )}
      </header>

      <section className="mt-8 flex flex-wrap items-center gap-2.5 border-y border-white/[0.08] py-4">
        <form
          onSubmit={handleAddressSubmit}
          className="flex min-w-0 flex-[1_1_320px] overflow-hidden rounded-md border border-white/15 bg-white/[0.08]"
        >
          <input
            value={addressInput}
            onChange={(event) => setAddressInput(event.target.value)}
            placeholder="Enter an address in Ontario, e.g. Union Station, Toronto"
            aria-label="Address"
            maxLength={200}
            className="min-w-0 flex-1 bg-transparent px-3.5 py-2.5 text-sm text-[#f2f2f2] placeholder:text-[#888] focus:outline-none"
          />
          <button
            type="submit"
            disabled={isLocating || !addressInput.trim()}
            className="cursor-pointer bg-[#e50914] px-4 text-sm font-semibold whitespace-nowrap text-white hover:bg-[#ff0a16] disabled:cursor-not-allowed disabled:opacity-50"
          >
            Search
          </button>
        </form>
        <button
          type="button"
          onClick={handleUseMyLocation}
          disabled={isLocating}
          className="inline-flex cursor-pointer items-center gap-2 rounded-md border border-white/25 bg-white/10 px-4 py-2.5 text-sm font-semibold whitespace-nowrap text-white disabled:cursor-not-allowed disabled:opacity-50"
        >
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
            className="h-4 w-4 flex-none"
          >
            <circle cx="12" cy="12" r="7" />
            <circle cx="12" cy="12" r="3" />
            <path d="M12 2v3" />
            <path d="M12 19v3" />
            <path d="M2 12h3" />
            <path d="M19 12h3" />
          </svg>
          Use my location
        </button>
        <div className="flex flex-wrap gap-1.5" role="group" aria-label="Search radius">
          {RADIUS_OPTIONS_KM.map((radiusOptionKm) => (
            <button
              key={radiusOptionKm}
              type="button"
              aria-pressed={radiusKm === radiusOptionKm}
              onClick={() => setRadiusKm(radiusOptionKm)}
              className={`cursor-pointer rounded-full border px-3.5 py-2 text-[13px] ${
                radiusKm === radiusOptionKm
                  ? 'border-[#e50914] bg-[#e50914] font-semibold text-white'
                  : 'border-white/15 bg-white/[0.06] text-[#d0d0d0] hover:text-white'
              }`}
            >
              {radiusOptionKm} km
            </button>
          ))}
        </div>
        <select
          value={sort}
          onChange={(event) => setSort(event.target.value as TheaterSort)}
          aria-label="Sort theaters"
          className="rounded-md border border-white/15 bg-[#16161b] px-2.5 py-2 text-sm text-[#f2f2f2] focus:outline-none"
        >
          <option value="distance">Nearest</option>
          <option value="nextShowtime">Next showtime</option>
        </select>
      </section>

      {(isLocating || locationMessage) && (
        <div className={`mt-3 text-sm ${locationMessage ? 'text-[#f87171]' : 'text-[#999]'}`} role="status">
          {locationMessage ?? 'Finding your location…'}
        </div>
      )}

      <main className="mt-5 grid grid-cols-1 gap-5 md:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)]">
        <section>
          {searchLocation && searchStatus !== 'idle' && (
            <div className="mb-2.5 text-[13px] text-[#999]">
              {searchStatus === 'ready'
                ? `${totalTheaters} theater${totalTheaters === 1 ? '' : 's'} within ${radiusKm} km of ${searchLocation.label}`
                : `Searching near ${searchLocation.label}…`}
            </div>
          )}

          {!searchLocation && (
            <div className="rounded-lg border border-dashed border-white/15 p-8 text-center text-sm text-[#999]">
              Enter an address or use your location to see theaters showing this movie.
            </div>
          )}

          {searchLocation && searchStatus === 'loading' && (
            <div className="py-12 text-center text-sm text-[#999]">Loading…</div>
          )}

          {searchStatus === 'error' && (
            <div className="rounded-lg border border-dashed border-white/15 p-8 text-center text-sm text-[#f87171]">
              Couldn't load theaters. Please try again.
            </div>
          )}

          {searchStatus === 'ready' && theaters.length === 0 && (
            <div className="rounded-lg border border-dashed border-white/15 p-8 text-center text-sm text-[#999]">
              No theaters within {radiusKm} km.{' '}
              {radiusKm < RADIUS_OPTIONS_KM[RADIUS_OPTIONS_KM.length - 1] && 'Try a larger radius.'}
            </div>
          )}

          {searchStatus === 'ready' && theaters.length > 0 && (
            <div className="flex flex-col gap-2.5">
              {theaters.map((theater, listIndex) => (
                <TheaterCard
                  key={theater.id}
                  theater={theater}
                  listNumber={listIndex + 1}
                  isSelected={theater.id === selectedTheaterId}
                  onSelect={() => setSelectedTheaterId(theater.id)}
                />
              ))}
              {loadedPage + 1 < totalPages && (
                <div className="mt-2 flex flex-col items-center gap-1.5">
                  {loadMoreFailed && (
                    <div className="text-[13px] text-[#f87171]" role="status">
                      Couldn't load more theaters.
                    </div>
                  )}
                  <button
                    type="button"
                    onClick={handleLoadMore}
                    disabled={isLoadingMore}
                    className="cursor-pointer rounded-md border border-white/15 bg-white/[0.06] px-4 py-2 text-sm text-[#f2f2f2] disabled:cursor-not-allowed disabled:opacity-40"
                  >
                    {isLoadingMore ? 'Loading…' : loadMoreFailed ? 'Try again' : 'Show more theaters'}
                  </button>
                </div>
              )}
            </div>
          )}
        </section>

        <aside className="order-first flex h-[260px] flex-col gap-2 md:sticky md:top-24 md:order-none md:h-[calc(100vh-140px)] md:min-h-[320px]">
          <div className="min-h-0 flex-1">
            {searchLocation ? (
              <TheaterMap
                center={searchLocation}
                radiusKm={radiusKm}
                theaters={searchStatus === 'ready' ? theaters : []}
                selectedTheaterId={selectedTheaterId}
                onSelectTheater={handleSelectFromMap}
              />
            ) : (
              <div className="flex h-full items-center justify-center rounded-lg border border-white/15 bg-[#16161b] text-sm text-[#666]">
                Map appears once you choose a location
              </div>
            )}
          </div>
          <div className="text-[11px] text-[#666]">
            Theater locations and address search ©{' '}
            <a
              href="https://www.openstreetmap.org/copyright"
              target="_blank"
              rel="noreferrer"
              className="text-[#999] hover:text-white"
            >
              OpenStreetMap contributors
            </a>
            . Showtimes are sample data.
          </div>
        </aside>
      </main>
    </div>
  );
}
