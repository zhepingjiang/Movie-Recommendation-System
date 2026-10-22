import { useEffect } from 'react';
import L from 'leaflet';
import { Circle, CircleMarker, MapContainer, Marker, TileLayer, Tooltip, useMap } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import type { NearbyTheater, SearchLocation } from '../types/theater';

/**
 * Stadia Maps "Alidade Smooth Dark" raster tiles: OSM street data styled to match the dark UI.
 * No API key: localhost works as is; a deployed domain must be registered (free tier) in a Stadia
 * account. Tiles are fetched by the viewer's browser, so Stadia sees which area is being viewed.
 */
// FIX: was CARTO basemaps, which now serve an "API KEY REQUIRED" placeholder tile (with HTTP 200).
const TILE_URL = 'https://tiles.stadiamaps.com/tiles/alidade_smooth_dark/{z}/{x}/{y}{r}.png';
const TILE_ATTRIBUTION =
  '&copy; <a href="https://stadiamaps.com/">Stadia Maps</a> &copy; <a href="https://openmaptiles.org/">OpenMapTiles</a> &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';
const INITIAL_ZOOM = 12;
const BRAND_RED = '#e50914';

interface TheaterMapProps {
  center: SearchLocation;
  radiusKm: number;
  theaters: NearbyTheater[];
  selectedTheaterId: number | null;
  onSelectTheater: (theaterId: number) => void;
}

/**
 * Street map (Leaflet) with the search radius, the search point, and a numbered pin per theater
 * matching the list order. Clicking a pin selects that theater.
 */
export default function TheaterMap({ center, radiusKm, theaters, selectedTheaterId, onSelectTheater }: TheaterMapProps) {
  return (
    <div className="h-full w-full overflow-hidden rounded-lg border border-white/15">
      <MapContainer
        center={[center.lat, center.lng]}
        zoom={INITIAL_ZOOM}
        // Leaflet's own stylesheet sets a light grey background; ! so the dark one wins.
        className="h-full w-full bg-[#16161b]!"
      >
        <TileLayer url={TILE_URL} attribution={TILE_ATTRIBUTION} maxZoom={20} />
        <FitToRadius center={center} radiusKm={radiusKm} />
        <Circle
          center={[center.lat, center.lng]}
          radius={radiusKm * 1000}
          pathOptions={{ color: BRAND_RED, weight: 1.5, opacity: 0.6, dashArray: '6 6', fillOpacity: 0.06 }}
        />
        <CircleMarker
          center={[center.lat, center.lng]}
          radius={7}
          pathOptions={{ color: '#ffffff', weight: 2, fillColor: BRAND_RED, fillOpacity: 1 }}
        >
          <Tooltip>Searching near {center.label}</Tooltip>
        </CircleMarker>
        {theaters.map((theater, listIndex) => {
          const isSelected = theater.id === selectedTheaterId;
          return (
            <Marker
              key={theater.id}
              position={[theater.lat, theater.lng]}
              icon={numberedPinIcon(listIndex + 1, isSelected)}
              zIndexOffset={isSelected ? 1000 : 0}
              eventHandlers={{ click: () => onSelectTheater(theater.id) }}
            >
              <Tooltip direction="top" offset={[0, -30]}>
                {theater.name}
              </Tooltip>
            </Marker>
          );
        })}
      </MapContainer>
    </div>
  );
}

/**
 * Re-frames the map on the whole search circle whenever the search point or radius changes
 * (MapContainer's own center/zoom props only apply on first render).
 *
 * @param center the search point the circle is drawn around
 * @param radiusKm the search radius the view must fit
 */
function FitToRadius({ center, radiusKm }: { center: SearchLocation; radiusKm: number }) {
  const map = useMap();
  useEffect(() => {
    // toBounds takes the square's side length in metres, i.e. the circle's diameter.
    map.fitBounds(L.latLng(center.lat, center.lng).toBounds(radiusKm * 2000), { padding: [16, 16] });
  }, [map, center.lat, center.lng, radiusKm]);
  return null;
}

/**
 * Builds the numbered teardrop pin for one theater; its tip sits on the theater's coordinate.
 *
 * @param listNumber the theater's 1-based position in the result list, shown inside the pin
 * @param isSelected whether the theater is selected, which draws the pin larger and in red
 * @returns a Leaflet icon for the theater's marker
 */
function numberedPinIcon(listNumber: number, isSelected: boolean): L.DivIcon {
  const scale = isSelected ? 1.3 : 1;
  const width = Math.round(24 * scale);
  const height = Math.round(30 * scale);
  const pinColor = isSelected ? BRAND_RED : '#f2f2f2';
  const numberColor = isSelected ? '#ffffff' : '#000000';
  return L.divIcon({
    // Empty class drops Leaflet's default white square behind div icons.
    className: '',
    iconSize: [width, height],
    iconAnchor: [width / 2, height],
    html: `<svg viewBox="-12 -30 24 30" width="${width}" height="${height}" class="cursor-pointer drop-shadow-md">
      <path d="M0 0 C-12 -16 -12 -30 0 -30 C12 -30 12 -16 0 0Z" fill="${pinColor}" />
      <text x="0" y="-15" text-anchor="middle" font-size="11" font-weight="700" fill="${numberColor}">${listNumber}</text>
    </svg>`,
  });
}
