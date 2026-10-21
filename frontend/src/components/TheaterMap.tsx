import type { NearbyTheater, SearchLocation } from '../types/theater';

const VIEWBOX_SIZE = 600;
const VIEWBOX_CENTER = VIEWBOX_SIZE / 2;
/** Radius circle stays inside the frame with a margin for pins. */
const RADIUS_CIRCLE_PX = 270;
const KM_PER_DEGREE_LAT = 110.574;
const KM_PER_DEGREE_LNG_AT_EQUATOR = 111.32;

interface TheaterMapProps {
  center: SearchLocation;
  radiusKm: number;
  theaters: NearbyTheater[];
  selectedTheaterId: number | null;
  onSelectTheater: (theaterId: number) => void;
}

/**
 * Placeholder map: an SVG with the search radius and a numbered pin per theater, positioned with
 * a flat-earth projection (fine at <= 50 km). No tiles and no API key. Kept behind this props
 * interface so a Leaflet map can replace it without touching the page.
 */
export default function TheaterMap({ center, radiusKm, theaters, selectedTheaterId, onSelectTheater }: TheaterMapProps) {
  const pixelsPerKm = RADIUS_CIRCLE_PX / radiusKm;
  const kmPerDegreeLng = KM_PER_DEGREE_LNG_AT_EQUATOR * Math.cos((center.lat * Math.PI) / 180);

  return (
    <div className="relative h-full w-full overflow-hidden rounded-lg border border-white/15 bg-[#16161b]">
      <svg
        viewBox={`0 0 ${VIEWBOX_SIZE} ${VIEWBOX_SIZE}`}
        preserveAspectRatio="xMidYMid meet"
        className="block h-full w-full"
        role="img"
        aria-label={`Map of ${theaters.length} theaters within ${radiusKm} km`}
      >
        <defs>
          <pattern id="theater-map-grid" width="40" height="40" patternUnits="userSpaceOnUse">
            <path d="M40 0H0V40" fill="none" className="stroke-white/[0.06]" />
          </pattern>
        </defs>
        <rect width={VIEWBOX_SIZE} height={VIEWBOX_SIZE} fill="url(#theater-map-grid)" />
        <circle
          cx={VIEWBOX_CENTER}
          cy={VIEWBOX_CENTER}
          r={RADIUS_CIRCLE_PX}
          strokeDasharray="6 6"
          className="fill-[#e50914]/[0.06] stroke-[#e50914]/50"
        />
        {theaters.map((theater, listIndex) => {
          const eastKm = (theater.lng - center.lng) * kmPerDegreeLng;
          const northKm = (theater.lat - center.lat) * KM_PER_DEGREE_LAT;
          const isSelected = theater.id === selectedTheaterId;
          return (
            <g
              key={theater.id}
              transform={`translate(${VIEWBOX_CENTER + eastKm * pixelsPerKm} ${VIEWBOX_CENTER - northKm * pixelsPerKm}) scale(${isSelected ? 1.3 : 1})`}
              onClick={() => onSelectTheater(theater.id)}
              className="cursor-pointer"
            >
              <title>{theater.name}</title>
              <path
                d="M0 0 C-12 -16 -12 -30 0 -30 C12 -30 12 -16 0 0Z"
                className={isSelected ? 'fill-[#e50914]' : 'fill-[#f2f2f2]'}
              />
              <text
                x="0"
                y="-15"
                textAnchor="middle"
                className={`text-[11px] font-bold ${isSelected ? 'fill-white' : 'fill-black'}`}
              >
                {listIndex + 1}
              </text>
            </g>
          );
        })}
        <circle cx={VIEWBOX_CENTER} cy={VIEWBOX_CENTER} r="16" className="fill-[#e50914]/25" />
        <circle cx={VIEWBOX_CENTER} cy={VIEWBOX_CENTER} r="7" strokeWidth="2" className="fill-[#e50914] stroke-white" />
      </svg>
      <div className="absolute bottom-3 left-3 rounded bg-black/70 px-2.5 py-1.5 text-[11px] text-[#a3a3a3]">
        Map preview · not to scale with streets
      </div>
    </div>
  );
}
